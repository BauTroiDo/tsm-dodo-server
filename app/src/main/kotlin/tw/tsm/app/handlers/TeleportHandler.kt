package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.WarpDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/** Bản đồ cấm Thiên Hành Dị Năng (tương ứng SceneManager.sceneId == 10701 của client)*/
private const val FORBIDDEN_TELEPORT_MAP = 10701

/**
 * Handler Thiên Hành Dị Năng (mainKind=68)
 *
 * Nguồn: protocal.lua protocolTable[68], UITeleport.lua
 *
 * C:068-001 Thiên Hành Dị Năng bằng tinh thạch — dùng tinh thạch dịch chuyển tới điểm dịch chuyển đã mở khoá
 * C:068-002 Thiên Hành Dị Năng bằng cuộn — dùng cuộn để dịch chuyển (client chỉ là stub)
 */
class TeleportHandler(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val bitFlagSystem: BitFlagSystem,
    private val partySystem: PartySystem,
    private val warpDefs: Map<Int, WarpDef>,
    private val markDefs: Map<Int, MarkDef>
) : PacketHandler {

    override val mainKind: Int = Opcodes.TELEPORT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleCrystalWarp(session, data)
            2 -> handleScrollWarp(session, data)
            else -> logger.warn { "Sub-protocol Thiên Hành Dị Năng chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:068-001 Thiên Hành Dị Năng bằng tinh thạch
     *
     * Định dạng: ID cảnh(2B LE) + NO(1B)
     * NO = chỉ số 0-based trong warpDefs
     */
    private suspend fun handleCrystalWarp(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val sceneId = reader.readUShortLE()
        val no = reader.readUByte()

        // Kiểm tra hợp lệ trạng thái cơ bản
        if (!validateTeleport(session)) {
            sendWarpResult(session, 1, 1)
            return
        }

        // Tìm điểm dịch chuyển
        val warpDef = warpDefs[no]
        if (warpDef == null) {
            logger.warn { "${session.characterName} Thiên Hành Dị Năng thất bại: điểm dịch chuyển không tồn tại no=$no" }
            sendWarpResult(session, 1, 1)
            return
        }

        // Kiểm tra tính nhất quán của ID cảnh
        if (warpDef.scene != sceneId) {
            logger.warn { "${session.characterName} Thiên Hành Dị Năng thất bại: sceneID không khớp client=$sceneId server=${warpDef.scene}" }
            sendWarpResult(session, 1, 1)
            return
        }

        // Kiểm tra mốc đã mở khoá
        val charId = session.roleId.toLong()
        if (!validateWarpMark(charId, warpDef)) {
            logger.warn { "${session.characterName} Thiên Hành Dị Năng thất bại: mốc chưa mở khóa mark=${warpDef.mark}" }
            sendWarpResult(session, 1, 1)
            return
        }

        // Thực hiện dịch chuyển
        executeTeleport(session, warpDef)
        sendWarpResult(session, 1, 0)
        logger.info { "${session.characterName} Thiên Hành Dị Năng dịch chuyển: no=$no → scene=${warpDef.scene} (${warpDef.x},${warpDef.y})" }
    }

    /**
     * C:068-002 Thiên Hành Dị Năng bằng cuộn
     *
     * Định dạng: ID cảnh(2B LE) + NO(1B) + chỉ số túi đồ(1B)
     * Client hiện chỉ logError (stub), trước mắt giữ lại khung code.
     */
    private suspend fun handleScrollWarp(session: GameSession, data: ByteArray) {
        if (data.size < 4) return
        val reader = PacketReader(data)
        val sceneId = reader.readUShortLE()
        val no = reader.readUByte()
        val bagIndex = reader.readUByte()

        logger.debug { "${session.characterName} Thiên Hành Dị Năng bằng cuộn (chưa triển khai): scene=$sceneId no=$no bag=$bagIndex" }
        // TODO: client chưa dùng, chờ cài đặt logic tiêu hao cuộn sau
        sendWarpResult(session, 2, 1)
    }

    /**
     * Kiểm tra xem có được phép dịch chuyển không
     *
     * Tương ứng với UITeleport.CheckTeleport() của client:
     * - Không được đang trong chiến đấu
     * - Cấm ở bản đồ 10701
     * - Không được ở trong tổ đội
     */
    private fun validateTeleport(session: GameSession): Boolean {
        if (session.battleId != 0L) return false
        if (session.mapId == FORBIDDEN_TELEPORT_MAP) return false
        if (partySystem.isInParty(session.playerId)) return false
        return true
    }

    /**
     * Kiểm tra mốc của điểm dịch chuyển đã mở khoá hay chưa
     *
     * Tương ứng với UITeleport.SetupSkyPointData() của client:
     * `if warpDatas[i].mark ~= 0 and CheckFlag(MarkManager.flags, markDatas[warpDatas[i].mark].bitId)`
     */
    private suspend fun validateWarpMark(charId: Long, warpDef: WarpDef): Boolean {
        if (warpDef.mark == 0) return true
        val markDef = markDefs[warpDef.mark] ?: return false
        if (markDef.bitId == 0) return true
        val flags = bitFlagSystem.getSetFlagIds(charId)
        return flags.contains(markDef.bitId)
    }

    /**
     * Thực hiện dịch chuyển (theo mô hình MacroBoxHandler.handleReturnTown)
     */
    private suspend fun executeTeleport(session: GameSession, warpDef: WarpDef) {
        val charId = session.roleId.toLong()
        worldManager.leaveMap(session)
        worldManager.enterMap(session, warpDef.scene, warpDef.x, warpDef.y)
        characterRepository.updatePosition(charId, warpDef.scene, warpDef.x, warpDef.y)
        responseSender.sendPlayerTeleport(session, session.playerId, warpDef.scene, warpDef.x, warpDef.y)
        responseSender.sendSceneChange(
            session, session.playerId, warpDef.scene, warpDef.x, warpDef.y, session.equipItemIds, session.titleId
        )
    }

    /**
     * Gửi kết quả Thiên Hành Dị Năng
     *
     * S:068-001/002 định dạng: result(1B), 0=thành công
     */
    private fun sendWarpResult(session: GameSession, subKind: Int, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.TELEPORT).writeByte(subKind).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }
}

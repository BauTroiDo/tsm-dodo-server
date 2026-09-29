package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.MacroBoxSettings
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.MacroBoxSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler hộp cơ quan (treo máy nội bộ) — mainKind=65
 *
 * Nguồn: protocal.lua protocolTable[65]
 * xử lý 7 subKind C→S: khởi động/tạm dừng/xác nhận số giờ/xóa tinh thạch/bay về điểm ghi/xóa bình EXP/đặt thứ tự bình EXP
 */
class MacroBoxHandler(
    private val macroBoxSystem: MacroBoxSystem,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.MACHINE_BOX

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleActivate(session, data)
            2 -> handlePause(session)
            3 -> handleConfirmHours(session)
            4 -> handleRemoveSpar(session, data)
            5 -> handleReturnTown(session)
            6 -> handleRemoveExpBottle(session, data)
            7 -> handleSetExpPriority(session, data)
            else -> logger.warn { "Sub-protocol Hộp Cơ Quan chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:065-001 khởi động hộp cơ quan
     *
     * Định dạng: playerHp%(1B) playerSp%(1B) npcHp%(1B) npcSp%(1B)
     *       hpReturn(1B) spReturn(1B) playerDead(1B) npcDead(1B)
     *       autoWalk(1B) autoExp(1B) autoShotput(1B)
     */
    private suspend fun handleActivate(session: GameSession, data: ByteArray) {
        if (data.size < 11) return
        val reader = PacketReader(data)
        val playerHp = reader.readUByte()
        val playerSp = reader.readUByte()
        reader.readUByte()  // npcHp (cố định 1, bỏ qua)
        reader.readUByte()  // npcSp (cố định 1, bỏ qua)
        val hpReturn = reader.readBoolean()
        val spReturn = reader.readBoolean()
        val playerDead = reader.readBoolean()
        val npcDead = reader.readBoolean()
        reader.readBoolean()  // autoWalk (cố định false, bỏ qua)
        val autoExp = reader.readBoolean()
        val autoShotput = reader.readBoolean()

        val charId = session.roleId.toLong()
        val settings = MacroBoxSettings(
            playerHpPercent = playerHp,
            playerSpPercent = playerSp,
            hpReturnTown = hpReturn,
            spReturnTown = spReturn,
            playerDeadReturn = playerDead,
            npcDeadReturn = npcDead,
            autoExp = autoExp,
            autoShotput = autoShotput
        )

        macroBoxSystem.activate(charId, settings)
        responseSender.sendMacroBoxStart(session)
        logger.debug { "${session.characterName} khởi động Hộp Cơ Quan hp%=$playerHp sp%=$playerSp" }
    }

    /**
     * C:065-002 tạm dừng hộp cơ quan
     */
    private suspend fun handlePause(session: GameSession) {
        val charId = session.roleId.toLong()
        if (!macroBoxSystem.isActive(charId)) return
        macroBoxSystem.deactivate(charId)
        responseSender.sendMacroBoxPause(session)
        logger.debug { "${session.characterName} tạm dừng Hộp Cơ Quan" }
    }

    /**
     * C:065-003 xác nhận số giờ
     *
     * server riêng không tính phí, trả lời khởi động thành công ngay.
     */
    private suspend fun handleConfirmHours(session: GameSession) {
        val charId = session.roleId.toLong()
        if (macroBoxSystem.isActive(charId)) {
            responseSender.sendMacroBoxStart(session)
        }
    }

    /**
     * C:065-004 xóa tinh thạch
     *
     * Định dạng: sparId(2B LE) + bagSlot(1B)
     */
    private suspend fun handleRemoveSpar(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val sparId = reader.readUShortLE()
        val bagSlot = reader.readUByte()

        val charId = session.roleId.toLong()
        if (macroBoxSystem.removeSpar(charId, sparId, bagSlot)) {
            responseSender.sendMacroBoxSparRemoveMsg(session)
            // gửi xuống lại dữ liệu tinh thạch
            val state = macroBoxSystem.getState(charId)
            if (state != null) {
                responseSender.sendMacroBoxSparData(session, state.sparSlots)
            }
        }
    }

    /**
     * C:065-005 bay về điểm ghi
     *
     * tạm dừng hộp cơ quan → tra điểm ghi → dịch chuyển đến vị trí điểm ghi
     */
    private suspend fun handleReturnTown(session: GameSession) {
        val charId = session.roleId.toLong()
        macroBoxSystem.deactivate(charId)
        responseSender.sendMacroBoxPause(session)

        val record = characterRepository.getRecordPoint(charId)
        if (record == null) {
            logger.warn { "${session.characterName} bay về điểm kiểm tra thất bại: không có dữ liệu điểm kiểm tra" }
            return
        }

        val (mapId, x, y) = record
        worldManager.leaveMap(session)
        worldManager.enterMap(session, mapId, x, y)
        characterRepository.updatePosition(charId, mapId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, mapId, x, y)
        responseSender.sendSceneChange(session, session.playerId, mapId, x, y, session.equipItemIds, session.titleId)

        logger.info { "${session.characterName} bay về điểm kiểm tra: map=$mapId ($x,$y)" }
    }

    /**
     * C:065-006 xóa bình EXP
     *
     * Định dạng: index(1B)
     */
    private suspend fun handleRemoveExpBottle(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val index = reader.readUByte()

        val charId = session.roleId.toLong()
        if (macroBoxSystem.removeExpBottle(charId, index)) {
            // gửi xuống lại dữ liệu bình EXP
            val state = macroBoxSystem.getState(charId)
            if (state != null) {
                responseSender.sendMacroBoxExpBottleData(session, state.expPriority, state.expBottles)
            }
        }
    }

    /**
     * C:065-007 đặt thứ tự bình EXP
     *
     * Định dạng: index(1B)
     */
    private suspend fun handleSetExpPriority(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val index = reader.readUByte()

        val charId = session.roleId.toLong()
        if (macroBoxSystem.setExpPriority(charId, index)) {
            responseSender.sendMacroBoxSetExpBottle(session, index)
        }
    }
}

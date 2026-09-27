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

/** 禁止天行異能的地圖（對應客戶端 SceneManager.sceneId == 10701） */
private const val FORBIDDEN_TELEPORT_MAP = 10701

/**
 * 天行異能 Handler（mainKind=68）
 *
 * 來源：protocal.lua protocolTable[68]、UITeleport.lua
 *
 * C:068-001 晶石天行異能 — 使用晶石傳送到已解鎖的傳送點
 * C:068-002 卷軸天行異能 — 使用卷軸傳送（客戶端僅 stub）
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
            else -> logger.warn { "未處理的天行異能子協定：subKind=$subKind" }
        }
    }

    /**
     * C:068-001 晶石天行異能
     *
     * 格式：場景ID(2B LE) + NO(1B)
     * NO = warpDefs 的 0-based 索引
     */
    private suspend fun handleCrystalWarp(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val sceneId = reader.readUShortLE()
        val no = reader.readUByte()

        // 基本狀態驗證
        if (!validateTeleport(session)) {
            sendWarpResult(session, 1, 1)
            return
        }

        // 查找傳送點
        val warpDef = warpDefs[no]
        if (warpDef == null) {
            logger.warn { "${session.characterName} 天行異能失敗：傳送點不存在 no=$no" }
            sendWarpResult(session, 1, 1)
            return
        }

        // 校驗場景 ID 一致性
        if (warpDef.scene != sceneId) {
            logger.warn { "${session.characterName} 天行異能失敗：場景ID不一致 client=$sceneId server=${warpDef.scene}" }
            sendWarpResult(session, 1, 1)
            return
        }

        // 驗證標記解鎖
        val charId = session.roleId.toLong()
        if (!validateWarpMark(charId, warpDef)) {
            logger.warn { "${session.characterName} 天行異能失敗：標記未解鎖 mark=${warpDef.mark}" }
            sendWarpResult(session, 1, 1)
            return
        }

        // 執行傳送
        executeTeleport(session, warpDef)
        sendWarpResult(session, 1, 0)
        logger.info { "${session.characterName} 天行異能傳送：no=$no → scene=${warpDef.scene} (${warpDef.x},${warpDef.y})" }
    }

    /**
     * C:068-002 卷軸天行異能
     *
     * 格式：場景ID(2B LE) + NO(1B) + 背包索引(1B)
     * 客戶端目前僅 logError（stub），先留骨架。
     */
    private suspend fun handleScrollWarp(session: GameSession, data: ByteArray) {
        if (data.size < 4) return
        val reader = PacketReader(data)
        val sceneId = reader.readUShortLE()
        val no = reader.readUByte()
        val bagIndex = reader.readUByte()

        logger.debug { "${session.characterName} 卷軸天行異能（未實作）：scene=$sceneId no=$no bag=$bagIndex" }
        // TODO：客戶端未使用，待後續實作卷軸消耗邏輯
        sendWarpResult(session, 2, 1)
    }

    /**
     * 驗證是否允許傳送
     *
     * 對應客戶端 UITeleport.CheckTeleport()：
     * - 不能在戰鬥中
     * - 地圖 10701 禁止
     * - 不能組隊
     */
    private fun validateTeleport(session: GameSession): Boolean {
        if (session.battleId != 0L) return false
        if (session.mapId == FORBIDDEN_TELEPORT_MAP) return false
        if (partySystem.isInParty(session.playerId)) return false
        return true
    }

    /**
     * 驗證傳送點標記是否已解鎖
     *
     * 對應客戶端 UITeleport.SetupSkyPointData()：
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
     * 執行傳送（沿用 MacroBoxHandler.handleReturnTown 模式）
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
     * 發送天行異能結果
     *
     * S:068-001/002 格式：result(1B)，0=成功
     */
    private fun sendWarpResult(session: GameSession, subKind: Int, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.TELEPORT).writeByte(subKind).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }
}

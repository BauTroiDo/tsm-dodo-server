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
 * 機關盒（內掛）Handler — mainKind=65
 *
 * 來源：protocal.lua protocolTable[65]
 * 處理 7 個 C→S subKind：啟動/暫停/確認時數/移除晶石/飛回紀錄點/移除經驗瓶/設定經驗瓶順序
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
            else -> logger.warn { "未處理的機關盒子協定：subKind=$subKind" }
        }
    }

    /**
     * C:065-001 啟動機關盒
     *
     * 格式：playerHp%(1B) playerSp%(1B) npcHp%(1B) npcSp%(1B)
     *       hpReturn(1B) spReturn(1B) playerDead(1B) npcDead(1B)
     *       autoWalk(1B) autoExp(1B) autoShotput(1B)
     */
    private suspend fun handleActivate(session: GameSession, data: ByteArray) {
        if (data.size < 11) return
        val reader = PacketReader(data)
        val playerHp = reader.readUByte()
        val playerSp = reader.readUByte()
        reader.readUByte()  // npcHp（固定 1，略過）
        reader.readUByte()  // npcSp（固定 1，略過）
        val hpReturn = reader.readBoolean()
        val spReturn = reader.readBoolean()
        val playerDead = reader.readBoolean()
        val npcDead = reader.readBoolean()
        reader.readBoolean()  // autoWalk（固定 false，略過）
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
        logger.debug { "${session.characterName} 啟動機關盒 hp%=$playerHp sp%=$playerSp" }
    }

    /**
     * C:065-002 暫停機關盒
     */
    private suspend fun handlePause(session: GameSession) {
        val charId = session.roleId.toLong()
        if (!macroBoxSystem.isActive(charId)) return
        macroBoxSystem.deactivate(charId)
        responseSender.sendMacroBoxPause(session)
        logger.debug { "${session.characterName} 暫停機關盒" }
    }

    /**
     * C:065-003 確認時數
     *
     * 私服不計費，直接回應啟動成功。
     */
    private suspend fun handleConfirmHours(session: GameSession) {
        val charId = session.roleId.toLong()
        if (macroBoxSystem.isActive(charId)) {
            responseSender.sendMacroBoxStart(session)
        }
    }

    /**
     * C:065-004 移除晶石
     *
     * 格式：sparId(2B LE) + bagSlot(1B)
     */
    private suspend fun handleRemoveSpar(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val sparId = reader.readUShortLE()
        val bagSlot = reader.readUByte()

        val charId = session.roleId.toLong()
        if (macroBoxSystem.removeSpar(charId, sparId, bagSlot)) {
            responseSender.sendMacroBoxSparRemoveMsg(session)
            // 重新下發晶石資料
            val state = macroBoxSystem.getState(charId)
            if (state != null) {
                responseSender.sendMacroBoxSparData(session, state.sparSlots)
            }
        }
    }

    /**
     * C:065-005 飛回紀錄點
     *
     * 暫停機關盒 → 查詢記錄點 → 傳送到記錄點位置
     */
    private suspend fun handleReturnTown(session: GameSession) {
        val charId = session.roleId.toLong()
        macroBoxSystem.deactivate(charId)
        responseSender.sendMacroBoxPause(session)

        val record = characterRepository.getRecordPoint(charId)
        if (record == null) {
            logger.warn { "${session.characterName} 飛回紀錄點失敗：無記錄點資料" }
            return
        }

        val (mapId, x, y) = record
        worldManager.leaveMap(session)
        worldManager.enterMap(session, mapId, x, y)
        characterRepository.updatePosition(charId, mapId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, mapId, x, y)
        responseSender.sendSceneChange(session, session.playerId, mapId, x, y, session.equipItemIds, session.titleId)

        logger.info { "${session.characterName} 飛回紀錄點：map=$mapId ($x,$y)" }
    }

    /**
     * C:065-006 移除經驗瓶
     *
     * 格式：index(1B)
     */
    private suspend fun handleRemoveExpBottle(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val index = reader.readUByte()

        val charId = session.roleId.toLong()
        if (macroBoxSystem.removeExpBottle(charId, index)) {
            // 重新下發經驗瓶資料
            val state = macroBoxSystem.getState(charId)
            if (state != null) {
                responseSender.sendMacroBoxExpBottleData(session, state.expPriority, state.expBottles)
            }
        }
    }

    /**
     * C:065-007 設定經驗瓶順序
     *
     * 格式：index(1B)
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

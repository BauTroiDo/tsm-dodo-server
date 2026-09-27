package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EWar
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.constants.PkResultCode
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 戰鬥管理處理器（mainKind = 11）
 *
 * 來源：protocal.lua protocolTable[11]
 *
 * C→S 格式：
 * - subKind 1（結束觀戰）：無資料
 * - subKind 2（PK/參戰）：+warType(1) +playerId(8) +index(2)
 *   warType=2(PkPlayers) → 發起新 PK
 *   warType=5(Join) → 加入目標的戰鬥
 * - subKind 3（參戰）：+playerId(8) +index(2)
 * - subKind 6（求救）：無資料
 * - subKind 8（攻城戰PK）：+warType(1) +playerId(8) +index(1)
 * - subKind 9（離開城戰觀戰）：無資料
 * - subKind 10（生存戰場PK）：+warType(1) +playerId(8)
 */
class BattleManageHandler(
    private val battleManager: BattleManager,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.BATTLE_MANAGE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> {
                // C:011-001 結束觀戰 — 無資料
                logger.trace { "${session.characterName} 結束觀戰" }
                // TODO：觀戰系統
            }
            2 -> handleBattleRequest(session, data)
            3 -> {
                // C:011-003 參戰 — +playerId(8) +index(2)
                logger.trace { "${session.characterName} 申請參戰（舊協定） — TODO" }
            }
            6 -> {
                // C:011-006 求救 — 無資料
                logger.trace { "${session.characterName} 戰鬥求救 — TODO" }
            }
            8 -> {
                // C:011-008 攻城戰PK — TODO
                logger.trace { "${session.characterName} 攻城戰PK — TODO" }
            }
            9 -> {
                // C:011-009 離開城戰觀戰 — TODO
                logger.trace { "${session.characterName} 離開城戰觀戰 — TODO" }
            }
            10 -> {
                // C:011-010 生存戰場PK — TODO
                logger.trace { "${session.characterName} 生存戰場PK — TODO" }
            }
            else -> logger.warn { "未處理的戰鬥管理子協定：subKind=$subKind" }
        }
    }

    /**
     * C:011-002 — PK/參戰請求
     *
     * 格式：+warType(1) +targetPlayerId(8) +index(2)
     *
     * 客戶端根據目標狀態發送不同 warType：
     * - warType=2 (PkPlayers)：目標不在戰鬥中 → 發起新 PK
     * - warType=5 (Join)：目標在戰鬥中 → 參戰（加入他那一方）
     *
     * 來源：RoleController.lua:2760-2767（PK）/ 2826-2888（參戰）
     */
    private suspend fun handleBattleRequest(session: GameSession, data: ByteArray) {
        if (data.size < 9) return
        val reader = PacketReader(data)
        val warType = reader.readUByte()
        val targetPlayerId = reader.readULongLE()

        when (warType) {
            EWar.PK_PLAYERS -> handlePkPlayers(session, targetPlayerId)
            EWar.JOIN -> handleJoinBattle(session, targetPlayerId)
            EWar.PK_MAP_NPC -> {
                val npcId = targetPlayerId.toInt()
                val placementIdx = if (reader.remaining >= 2) reader.readUShortLE() else 0
                logger.info { "${session.characterName} 發起 PK MapNpc npcId=$npcId idx=$placementIdx" }
                battleManager.createMapNpcBattle(session, npcId, placementIdx)
            }
            else -> logger.warn { "${session.characterName} 未處理的 C:011-002 warType=$warType" }
        }
    }

    /**
     * 發起 PK — warType=2 (PkPlayers)
     *
     * 驗證流程：
     * 1. 發起者未在戰鬥中
     * 2. 目標存在且在同一地圖
     * 3. 目標未在戰鬥中
     * 4. 目標已開啟 PK 開關
     * 5. 通過後建立 PVP 戰鬥
     */
    private suspend fun handlePkPlayers(session: GameSession, targetPlayerId: Long) {
        logger.info { "${session.characterName} 發起 PK → 目標 $targetPlayerId" }

        // 驗證：發起者不在戰鬥中
        if (battleManager.isInBattle(session.playerId)) {
            logger.debug { "${session.characterName} 已在戰鬥中，無法發起 PK" }
            return
        }

        // 驗證：目標存在
        val target = worldManager.getPlayerById(targetPlayerId)
        if (target == null) {
            responseSender.sendPkResult(session, PkResultCode.SPECIAL_BATTLE)
            return
        }

        // 驗證：同一地圖
        if (session.mapId != target.mapId) {
            responseSender.sendPkResult(session, PkResultCode.SCENE_NO_PK)
            return
        }

        // 驗證：目標不在戰鬥中
        if (battleManager.isInBattle(target.playerId)) {
            val battle = battleManager.getPlayerBattle(target.playerId)
            val resultCode = when (battle?.warType) {
                EWar.BE_QUESTION -> PkResultCode.IN_QUESTION
                else -> PkResultCode.SPECIAL_BATTLE
            }
            responseSender.sendPkResult(session, resultCode)
            return
        }

        // 驗證：目標 PK 開關
        if (!target.pkEnabled) {
            responseSender.sendPkResult(session, PkResultCode.PK_OFF)
            return
        }

        // 建立 PVP 戰鬥
        battleManager.createPvpBattle(session, target)
    }

    /**
     * 參戰 — warType=5 (Join)
     *
     * 客戶端在目標 war != None 時使用 EInteractive.PlayOne 觸發。
     * 加入者會分配到與目標相同的陣營。
     *
     * 驗證流程：
     * 1. 加入者不在戰鬥中
     * 2. 目標存在
     * 3. 目標正在戰鬥中
     * 4. 戰鬥類型允許參戰
     * 5. 隊伍有空位
     * 6. 加入戰鬥
     */
    private suspend fun handleJoinBattle(session: GameSession, targetPlayerId: Long) {
        logger.info { "${session.characterName} 申請參戰 → 目標 $targetPlayerId" }

        // 驗證：加入者不在戰鬥中
        if (battleManager.isInBattle(session.playerId)) {
            logger.debug { "${session.characterName} 已在戰鬥中，無法參戰" }
            return
        }

        // 驗證：目標存在
        val target = worldManager.getPlayerById(targetPlayerId)
        if (target == null) {
            logger.debug { "參戰目標 $targetPlayerId 不在線" }
            return
        }

        // 驗證：目標在戰鬥中
        val battle = battleManager.getPlayerBattle(target.playerId)
        if (battle == null) {
            logger.debug { "目標 ${target.characterName} 未在戰鬥中" }
            return
        }

        // 驗證：戰鬥類型允許參戰
        when (battle.warType) {
            EWar.BE_QUESTION -> {
                responseSender.sendPkResult(session, PkResultCode.IN_QUESTION)
                return
            }
            EWar.PK_PLAYERS, EWar.EVENT, EWar.PK_MAP_NPC -> {
                // 允許參戰
            }
            else -> {
                responseSender.sendPkResult(session, PkResultCode.SPECIAL_BATTLE)
                return
            }
        }

        // 驗證：戰鬥狀態允許加入（已結束則不能）
        if (battle.state == tw.tsm.gamelogic.battle.BattleInstance.State.FINISHED) {
            logger.debug { "戰鬥 ${battle.battleId} 已結束，無法參戰" }
            return
        }

        // 加入戰鬥
        val success = battleManager.joinBattle(session, battle, target.playerId)
        if (!success) {
            // 人數已滿 → S:011-003 failCode=1
            responseSender.sendBattleError(session, 1)
        }
    }
}

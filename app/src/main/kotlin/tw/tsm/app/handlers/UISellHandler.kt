package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.UISellSystem
import tw.tsm.gamelogic.systems.UiSellQuote
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/** 禁止記憶傳送的地圖（對應客戶端 UITeleport.CheckTeleport 的 sceneId == 10701） */
private const val FORBIDDEN_TELEPORT_MAP = 10701

/**
 * 介面消費 Handler（mainKind=84）
 *
 * 來源：protocal.lua protocolTable[84]、Logic/UISell.lua
 *
 * C:084-001 查詢介消資料 — +介消編號(2) +args(2×N)
 * C:084-002 使用介消 — +總類(1) +介消編號(2) 總類1:物品 +背包索引(1)；總類2:點數/免費 +args(2×N)
 *
 * 已實作介消：
 * - sellId=1/2/3/56 錢莊/客棧/背包/寶庫格數擴充（RoleCount 遞增）
 * - sellId=7 記憶傳送使用（道具 46474 優先、元寶後備）
 * - sellId=8 記憶傳送點擴充（遞進價格、上限 5 格）
 */
class UISellHandler(
    private val responseSender: ResponseSender,
    private val uiSellSystem: UISellSystem,
    private val pointSystem: PointSystem,
    private val inventorySystem: InventorySystem,
    private val flyPointSystem: FlyPointSystem,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val partySystem: PartySystem,
    private val roleCountSystem: RoleCountSystem,
    private val skillRepository: SkillRepository,
    private val playerStateManager: PlayerStateManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.UI_SELL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleQuery(session, data)
            2 -> handleUse(session, data)
            else -> logger.warn { "未處理的介消子協定：subKind=$subKind" }
        }
    }

    /**
     * C:084-001 查詢介消資料
     *
     * 格式：+介消編號(2) +args(2×N)
     */
    private suspend fun handleQuery(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val sellId = reader.readUShortLE()
        val args = readArgs(reader)

        val charId = session.roleId.toLong()
        when (val quote = uiSellSystem.query(charId, sellId)) {
            is UiSellQuote.Fail -> {
                responseSender.sendUiSellFail(session, 1, sellId, quote.reason, quote.value)
                logger.debug { "${session.characterName} 介消查詢失敗：sellId=$sellId reason=${quote.reason}" }
            }
            is UiSellQuote.Money ->
                responseSender.sendUiSellMoneyQuery(session, sellId, quote.currencyKind, quote.cost)
            is UiSellQuote.Item ->
                responseSender.sendUiSellItemQuery(session, sellId, quote.itemId, quote.count)
        }

        logger.debug { "${session.characterName} 介消查詢：sellId=$sellId args=$args" }
    }

    /**
     * C:084-002 使用介消
     *
     * 格式：+總類(1) +介消編號(2)
     * 總類 1（物品）：+背包索引(1) +args(2×N)
     * 總類 2（點數/免費）：+args(2×N)
     *
     * TODO：sellId=11 轉轉樂的多格道具形式（+L(1) <<+背包引(1) +數量(2)>>）尚未支援
     */
    private suspend fun handleUse(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val useKind = reader.readUByte()
        val sellId = reader.readUShortLE()
        val bagIndex = if (useKind == USE_KIND_ITEM) {
            if (reader.remaining < 1) return
            reader.readUByte()
        } else {
            -1
        }
        val args = readArgs(reader)

        val charId = session.roleId.toLong()

        // 重新報價驗證（無狀態設計：以伺服器當下狀態為準，不信任客戶端）
        val quote = uiSellSystem.query(charId, sellId)
        if (quote is UiSellQuote.Fail) {
            responseSender.sendUiSellFail(session, 2, sellId, quote.reason, quote.value)
            return
        }

        // 效果前置驗證（先驗證再扣款，避免扣款後效果失敗）
        when (sellId) {
            UISellSystem.SELL_TURN3_UNLOCK_SKILL -> {
                val reason = validateTurn3Unlock(charId, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
            UISellSystem.SELL_TURN3_CHANGE_ELEMENT -> {
                val reason = validateTurn3ElementChange(charId, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
            UISellSystem.SELL_FLY_TELEPORT -> {
                val reason = validateFlyTeleport(session, args)
                if (reason != 0) {
                    responseSender.sendUiSellFail(session, 2, sellId, reason)
                    return
                }
            }
        }

        // 扣款
        if (!processPayment(session, sellId, useKind, bagIndex, quote)) return

        // 執行效果
        when (sellId) {
            UISellSystem.SELL_FLY_TELEPORT -> executeFlyTeleport(session, sellId, args)
            UISellSystem.SELL_FLY_EXTEND -> executeFlyExtend(session, sellId)
            UISellSystem.SELL_TURN3_UNLOCK_SKILL -> executeTurn3Unlock(session, sellId, args)
            UISellSystem.SELL_TURN3_CHANGE_ELEMENT -> executeTurn3ElementChange(session, sellId, args)
            else -> {
                val roleCountIndex = uiSellSystem.expansionRoleCountIndex(sellId)
                if (roleCountIndex != null) {
                    executeExpansion(session, sellId, roleCountIndex)
                }
            }
        }
    }

    /**
     * 讀取封包尾端的 args（每個 2B LE）
     */
    private fun readArgs(reader: PacketReader): List<Int> {
        val args = mutableListOf<Int>()
        while (reader.remaining >= 2) {
            args.add(reader.readUShortLE())
        }
        return args
    }

    /**
     * 執行扣款
     *
     * @return true=扣款成功；失敗時已回覆錯誤封包
     */
    private suspend fun processPayment(
        session: GameSession, sellId: Int, useKind: Int, bagIndex: Int, quote: UiSellQuote
    ): Boolean {
        val charId = session.roleId.toLong()

        when (quote) {
            is UiSellQuote.Item -> {
                // 客戶端選擇道具路徑：驗證背包格物品與報價一致
                if (useKind != USE_KIND_ITEM) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                    return false
                }
                val thing = inventorySystem.getBagItem(charId, bagIndex)
                if (thing == null || thing.itemId != quote.itemId || thing.quantity < quote.count) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                    return false
                }
                if (!inventorySystem.removeFromBag(charId, bagIndex, quote.count)) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_ITEM_DEDUCT_FAIL)
                    return false
                }
                responseSender.sendReduceBagItem(session, bagIndex, quote.count)
                logger.info { "${session.characterName} 介消扣道具：sellId=$sellId item=${quote.itemId}×${quote.count} bag=$bagIndex" }
            }
            is UiSellQuote.Money -> {
                if (useKind != USE_KIND_POINT) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_PRICE_ERROR)
                    return false
                }
                if (!pointSystem.subtractRealPoint(charId, quote.cost.toInt())) {
                    responseSender.sendUiSellFail(session, 2, sellId, UISellSystem.REASON_GOLD_NOT_ENOUGH)
                    return false
                }
                // 刷新客戶端元寶顯示
                val (real, virtual) = pointSystem.getPoints(charId)
                responseSender.sendPointBalance(session, real, virtual)
                logger.info { "${session.characterName} 介消扣元寶：sellId=$sellId cost=${quote.cost} 剩餘=$real" }
            }
            is UiSellQuote.Fail -> return false  // 已在呼叫端處理
        }
        return true
    }

    private suspend fun validateTurn3Unlock(charId: Long, args: List<Int>): Int {
        if (args.size != 1) return UISellSystem.REASON_NOT_USABLE
        val state = playerStateManager.getState(charId) ?: return UISellSystem.REASON_NOT_USABLE
        if (state.turn < 3) return UISellSystem.REASON_NOT_USABLE
        val skillId = args[0]
        if (skillId !in TURN3_LIGHT_SKILLS && skillId !in TURN3_DARK_SKILLS) return UISellSystem.REASON_NOT_USABLE
        val turn3 = skillRepository.findTurn3State(charId)
        val expected = if (skillId in TURN3_LIGHT_SKILLS) TURN3_LIGHT else TURN3_DARK
        if (turn3.element != expected || turn3.skills.containsKey(skillId)) return UISellSystem.REASON_NOT_USABLE
        return 0
    }

    private suspend fun validateTurn3ElementChange(charId: Long, args: List<Int>): Int {
        if (args.size != 1) return UISellSystem.REASON_NOT_USABLE
        val state = playerStateManager.getState(charId) ?: return UISellSystem.REASON_NOT_USABLE
        if (state.turn < 3) return UISellSystem.REASON_NOT_USABLE
        val turn3 = skillRepository.findTurn3State(charId)
        val target = args[0]
        if (target !in setOf(TURN3_LIGHT, TURN3_DARK) || turn3.element == 0 || target == turn3.element) {
            return UISellSystem.REASON_NOT_USABLE
        }
        return 0
    }

    private suspend fun executeTurn3Unlock(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val skillId = args.single()
        val state = skillRepository.findTurn3State(charId)
        skillRepository.saveTurn3State(charId, state.copy(skills = state.skills + (skillId to 1)))
        responseSender.sendUiSellSuccess(session, sellId)
        logger.info { "${session.characterName} unlocked Turn3 skill=$skillId" }
    }

    private suspend fun executeTurn3ElementChange(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val target = args.single()
        val state = skillRepository.findTurn3State(charId)
        // Skill khác hệ bị loại bỏ để client không giữ skill trái với Turn3Element.
        val allowed = if (target == TURN3_LIGHT) TURN3_LIGHT_SKILLS else TURN3_DARK_SKILLS
        skillRepository.saveTurn3State(charId, state.copy(
            element = target,
            skills = state.skills.filterKeys { it in allowed }
        ))
        responseSender.sendUiSellSuccess(session, sellId)
        logger.info { "${session.characterName} changed Turn3 element=$target" }
    }

    /**
     * sellId=7 記憶傳送前置驗證
     *
     * 對應客戶端 UITeleport.CheckTeleport()：戰鬥中/特定場景/組隊中不可傳送。
     * args = [類型(1=記憶傳送), 傳送點索引]
     *
     * @return 0=通過，其餘為失敗原因碼
     */
    private suspend fun validateFlyTeleport(session: GameSession, args: List<Int>): Int {
        if (args.size < 2 || args[0] != 1) {
            // TODO：args[0] != 1 的其他傳送類型尚未在客戶端發現使用案例
            return UISellSystem.REASON_NOT_USABLE
        }
        if (session.battleId != 0L) return UISellSystem.REASON_NOT_USABLE
        if (session.mapId == FORBIDDEN_TELEPORT_MAP) return UISellSystem.REASON_SCENE_FORBIDDEN
        if (partySystem.isInParty(session.playerId)) return UISellSystem.REASON_NOT_USABLE

        val charId = session.roleId.toLong()
        val point = flyPointSystem.loadAll(charId)[args[1]]
        if (point == null || point.sceneId <= 0) return UISellSystem.REASON_NOT_USABLE

        return 0
    }

    /**
     * sellId=7 執行記憶傳送
     */
    private suspend fun executeFlyTeleport(session: GameSession, sellId: Int, args: List<Int>) {
        val charId = session.roleId.toLong()
        val index = args[1]
        val point = flyPointSystem.loadAll(charId)[index] ?: return

        responseSender.sendUiSellSuccess(session, sellId)

        worldManager.leaveMap(session)
        worldManager.enterMap(session, point.sceneId, point.x, point.y)
        characterRepository.updatePosition(charId, point.sceneId, point.x, point.y)
        responseSender.sendPlayerTeleport(session, session.playerId, point.sceneId, point.x, point.y)
        responseSender.sendSceneChange(
            session, session.playerId, point.sceneId, point.x, point.y,
            session.equipItemIds, session.titleId
        )

        logger.info { "${session.characterName} 記憶傳送（介消）：index=$index → scene=${point.sceneId} (${point.x}, ${point.y})" }
    }

    /**
     * sellId=1/2/3/56 執行格數擴充
     *
     * RoleCount 遞增後以 S:085-002 下發，客戶端 Item.RefreshRoleCount /
     * Inn.RefreshRoleCount 會自動重算格數並刷新介面。
     */
    private suspend fun executeExpansion(session: GameSession, sellId: Int, roleCountIndex: Int) {
        val charId = session.roleId.toLong()
        val newValue = roleCountSystem.increment(charId, roleCountIndex)
        val maxValue = roleCountSystem.getMaxValue(charId, roleCountIndex)

        responseSender.sendUiSellSuccess(session, sellId)
        responseSender.sendRoleCountUpdate(session, mapOf(roleCountIndex to (newValue to maxValue)))

        logger.info { "${session.characterName} 介消格數擴充：sellId=$sellId roleCount[$roleCountIndex]=$newValue" }
    }

    /**
     * sellId=8 執行記憶傳送點擴充
     */
    private suspend fun executeFlyExtend(session: GameSession, sellId: Int) {
        val charId = session.roleId.toLong()
        val newCount = uiSellSystem.incrementPurchaseCount(charId, sellId)

        responseSender.sendUiSellSuccess(session, sellId)

        // 重新下發記憶傳送符資料，客戶端據此解鎖新格
        val points = flyPointSystem.loadAll(charId)
        responseSender.sendFlyPointData(session, newCount, points)

        logger.info { "${session.characterName} 擴充記憶傳送點：已擴充 $newCount 格" }
    }

    companion object {
        /** C:084-002 總類 1：物品消費 */
        private const val USE_KIND_ITEM = 1

        /** C:084-002 總類 2：點數/免費 */
        private const val USE_KIND_POINT = 2

        private const val TURN3_LIGHT = 7
        private const val TURN3_DARK = 8
        private val TURN3_LIGHT_SKILLS = setOf(22001,22002,22003,22004,22007,22010,22015,22016,22011)
        private val TURN3_DARK_SKILLS = setOf(23001,23002,23003,23004,23009,23010,23015,23016,23011)
    }
}

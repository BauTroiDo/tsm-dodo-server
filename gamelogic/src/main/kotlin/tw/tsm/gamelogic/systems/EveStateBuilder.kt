package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.domain.valueobjects.FollowNpcData

private val logger = KotlinLogging.logger {}

/**
 * Eve 事件狀態建構器
 *
 * 職責：
 * - 建構 PlayerEventState 快照（整合 QuestSystem / InventorySystem / PetSystem）
 * - Fallback Talk 查找
 * - 距離驗證
 *
 * 條件解析（resolveEvent）和 GroupData 過濾（applyGroupData）
 * 已移至 [EveConditionResolver]，本類只負責「狀態建構」。
 */
class EveStateBuilder(
    private val questSystem: QuestSystem,
    private val inventorySystem: InventorySystem,
    private val petSystem: PetSystem,
    private val stateMachine: EveEventStateMachine,
    private val playerStateManager: PlayerStateManager,
    private val markDefs: Map<Int, MarkDef>,
    private val roleCountSystem: RoleCountSystem,
    private val skillSystem: SkillSystem,
    private val moneySystem: MoneySystem,
    private val questRepository: QuestRepository,
    private val pointSystem: PointSystem? = null
) {

    /**
     * 建構玩家事件狀態快照
     *
     * 從各系統收集當前狀態，傳入 EveConditionEvaluator 進行純函數評估。
     * completedEveCounts 從 DB 載入 + 記憶體合併，確保重登不遺失。
     */
    suspend fun buildPlayerState(charId: Long, mapId: Int, session: GameSession? = null): PlayerEventState {
        val missionSteps = questSystem.getMissions(charId)
            .associateBy({ it.missionId }, { it.step })

        // 任務旗標（conditionClass=2 pStyle=3 條件用）
        val rawFlags = questSystem.getFlags(charId)
            .associateBy({ it.bitIndex }, { it.bitValue })
        val missionFlagValues = mutableMapOf<Int, Int>()
        for ((missionId, markDef) in markDefs) {
            val flagValue = rawFlags[markDef.bitId] ?: 0
            if (flagValue > 0) missionFlagValues[missionId] = flagValue
        }

        // 從 DB 載入已完成事件計數並合併到記憶體
        val dbCompleted = questRepository.getCompletedEventCounts(charId, mapId)
        if (dbCompleted.isNotEmpty()) {
            stateMachine.prePopulateCompleted(charId, mapId, dbCompleted)
        }

        val bag = inventorySystem.getBag(charId)
        val bagItems = mutableMapOf<Int, Int>()
        for ((_, thing) in bag) {
            bagItems[thing.itemId] = (bagItems[thing.itemId] ?: 0) + thing.quantity
        }

        val storedItems = mutableMapOf<Int, Int>()
        for (container in listOf(inventorySystem.getBank(charId), inventorySystem.getStorage(charId))) {
            for ((_, thing) in container) {
                storedItems[thing.itemId] = (storedItems[thing.itemId] ?: 0) + thing.quantity
            }
        }

        val equips = inventorySystem.getEquipment(charId)
        val equipItems = equips.values.map { it.itemId }.toSet()

        val followPets = petSystem.getFollowPets(charId)
        val followNpcIds = followPets.values.map { it.npcId }.toSet()
        val innNpcIds = petSystem.getInnPets(charId)
            .values.map { it.npcId }.toSet()
        val cartNpcIds = petSystem.getCartPets(charId)
            .values.map { it.npcId }.toSet()

        val roleCountValues = roleCountSystem.getAllCounts(charId)
            .mapValues { (_, valuePair) -> valuePair.first }
        val bagCapacity = calculateBagCapacity(roleCountValues)
        val skillLevels = skillSystem.getCache(charId)?.skills?.toMap() ?: emptyMap()
        val gold = moneySystem.getGold(charId)
        val realPoint = pointSystem?.getRealPoint(charId) ?: -1

        val eventSession = stateMachine.getSession(charId)

        return PlayerEventState(
            missionSteps = missionSteps,
            missionFlags = missionFlagValues,
            bagItems = bagItems,
            storedItems = storedItems,
            bagUsedSlots = bag.size,
            bagCapacity = bagCapacity,
            equipItems = equipItems,
            sex = session?.sex ?: 0,
            level = session?.level ?: 0,
            serverId = session?.serverId?.takeIf { it > 0 } ?: -1,
            realPoint = realPoint,
            rebornCount = playerStateManager.getState(charId)?.turn ?: 0,
            isMounted = (session?.mountNpcId ?: 0) != 0,
            lastSurfaceId = eventSession?.lastSurfaceId ?: -1,
            lastChoiceCode = eventSession?.lastChoiceCode ?: -1,
            battleResult = eventSession?.battleResult ?: 0,
            completedEveCounts = stateMachine.getCompletedEveCounts(charId, mapId),
            followNpcIds = followNpcIds,
            followPetCount = followPets.size,
            maxFollowPets = PetSystem.MAX_FOLLOW,
            innNpcIds = innNpcIds,
            cartNpcIds = cartNpcIds,
            roleCountValues = roleCountValues,
            skillLevels = skillLevels,
            gold = gold,
            npcSecondReincarnationStatus = calculateNpcSecondReincarnationStatus(charId, followPets.values)
        )
    }

    /**
     * 從 NPC 事件中找第一個 Talk result 作為 fallback 對話
     */
    fun findFallbackTalk(sceneData: SceneEveData, eveNos: List<Int>): EveResult? {
        for (eveNo in eveNos) {
            val eventData = sceneData.npcEvents[eveNo] ?: continue
            for (cond in eventData.conditions) {
                for (result in cond.results) {
                    if (result.resultType == 1 && result.resultMeanNo > 0) return result
                }
            }
        }
        return null
    }

    /**
     * 距離驗證：玩家 (0,0) 表示初始化中無位置，跳過驗證
     */
    fun isWithinRange(px: Int, py: Int, tx: Int, ty: Int): Boolean {
        if (px == 0 && py == 0) return true  // 玩家初始化中，無位置資料
        val dx = (px - tx).toLong()
        val dy = (py - ty).toLong()
        return dx * dx + dy * dy <= MAX_INTERACT_DISTANCE_SQ
    }

    companion object {
        /** 互動距離上限平方值（200 像素，比客戶端 130 寬鬆） */
        private const val MAX_INTERACT_DISTANCE_SQ = 200L * 200L
        private const val ROLE_COUNT_BAG_1 = 103
        private const val ROLE_COUNT_BAG_2 = 106
        private const val ROLE_COUNT_BAG_3 = 124
        private const val NPC_SECOND_REINCARNATION_MIN_LEVEL = 60
        private const val NPC_SECOND_REINCARNATION_MIN_LOYALTY = 100

        private fun calculateBagCapacity(roleCountValues: Map<Int, Int>): Int {
            val expandCount = (roleCountValues[ROLE_COUNT_BAG_1] ?: 0) +
                (roleCountValues[ROLE_COUNT_BAG_2] ?: 0) +
                (roleCountValues[ROLE_COUNT_BAG_3] ?: 0)
            return (ItemConstants.INIT_BAG_SLOTS + expandCount * ItemConstants.ADD_BAG_SLOTS)
                .coerceIn(0, ItemConstants.MAX_BAG_SLOTS)
        }
    }

    private fun calculateNpcSecondReincarnationStatus(
        charId: Long,
        followPets: Collection<FollowNpcData>
    ): Int {
        val canOpen = followPets.any { pet ->
            pet.level >= NPC_SECOND_REINCARNATION_MIN_LEVEL &&
                pet.loyalty >= NPC_SECOND_REINCARNATION_MIN_LOYALTY &&
                inventorySystem.hasNpcSecondReincarnationToken(charId, pet.npcId)
        }
        return if (canOpen) 0 else -1
    }
}

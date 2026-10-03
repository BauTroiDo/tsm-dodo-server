package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.data.SceneSetDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Bộ dựng trạng thái sự kiện Eve
 *
 * Nhiệm vụ:
 * - Dựng ảnh chụp PlayerEventState (tích hợp QuestSystem / InventorySystem / PetSystem)
 * - Tìm kiếm Talk fallback
 * - Kiểm tra khoảng cách
 *
 * Phân tích điều kiện (resolveEvent) và lọc GroupData (applyGroupData)
 * Đã chuyển sang [EveConditionResolver], lớp này chỉ chịu trách nhiệm "dựng trạng thái".
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
    private val pointSystem: PointSystem? = null,
    private val partySystem: PartySystem? = null,
    private val sceneSetDefs: Map<Int, SceneSetDef> = emptyMap(),
    private val activityTimeDefs: Map<Int, ActivityTimeDef> = emptyMap(),
) {

    /**
     * Dựng ảnh chụp nhanh trạng thái sự kiện người chơi
     *
     * Thu thập trạng thái hiện tại từ các hệ thống, truyền vào EveConditionEvaluator để đánh giá bằng hàm thuần.
     * completedEveCounts nạp từ DB + hợp nhất trong bộ nhớ, đảm bảo đăng nhập lại không mất.
     */
    suspend fun buildPlayerState(charId: Long, mapId: Int, session: GameSession? = null): PlayerEventState {
        val missionSteps = questSystem.getMissions(charId)
            .associateBy({ it.missionId }, { it.step })

        // Cờ nhiệm vụ (dùng cho điều kiện conditionClass=2 pStyle=3)
        val rawFlags = questSystem.getFlags(charId)
            .associateBy({ it.bitIndex }, { it.bitValue })
        val missionFlagValues = mutableMapOf<Int, Int>()
        for ((missionId, markDef) in markDefs) {
            val flagValue = rawFlags[markDef.bitId] ?: 0
            if (flagValue > 0) missionFlagValues[missionId] = flagValue
        }

        // Nạp số đếm sự kiện đã hoàn thành từ DB và hợp nhất vào bộ nhớ
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
        val followNpcLevels = followPets.values
            .groupBy { it.npcId }
            .mapValues { (_, pets) -> pets.maxOf { it.level } }
        val followNpcLoyalties = followPets.values
            .groupBy { it.npcId }
            .mapValues { (_, pets) -> pets.maxOf { it.loyalty } }
        val deployedNpcIds = followPets.values
            .filter { it.isDeployed }
            .map { it.npcId }
            .toSet()
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
        val moral = playerStateManager.getMoral(charId)
        val killNum = playerStateManager.getKillNum(charId)
        val runtimeState = playerStateManager.getState(charId)
        val activityOpen = resolveActivityOpen(mapId)
        val leaderId = partySystem?.getLeaderId(charId) ?: 0L
        val teamMembers = if (leaderId == 0L) {
            listOf(charId)
        } else {
            partySystem?.getTeamMembers(leaderId).orEmpty().ifEmpty { listOf(charId) }
        }
        val teamSize = teamMembers.size.coerceAtLeast(1)
        val teamState = partySystem?.getTeamState(charId)?.code ?: 0
        val romancePairValid = if (teamMembers.size == 2) {
            val sexes = teamMembers.map { memberId ->
                if (memberId == charId && session != null) session.sex
                else playerStateManager.getSex(memberId)
            }
            sexes.all { it in 1..2 } && sexes.toSet().size == 2
        } else {
            false
        }

        return PlayerEventState(
            missionSteps = missionSteps,
            dungeonMissionIds = markDefs.filterValues { it.kind == 4 || it.kind == 5 }.keys,
            missionFlags = missionFlagValues,
            bagItems = bagItems,
            storedItems = storedItems,
            bagUsedSlots = bag.size,
            bagCapacity = bagCapacity,
            equipItems = equipItems,
            sex = session?.sex ?: 0,
            // Offline fallback: schema hiện chưa có persistence phe cốt truyện.
            // DVT Eve/Talk xác nhận value=2 là nhánh Khăn Vàng/Hắc Sơn;
            // nhân vật bình thường dùng 1 cho đến khi tìm được nguồn server gốc.
            storyFaction = DEFAULT_STORY_FACTION,
            moral = moral,
            killNum = killNum,
            activityOpen = activityOpen,
            teamSize = teamSize,
            teamState = teamState,
            romancePairValid = romancePairValid,
            element = runtimeState?.element ?: 0,
            transformNpcId = runtimeState?.transformNpcId ?: 0,
            career = runtimeState?.career ?: 0,
            level = session?.level ?: runtimeState?.level ?: 0,
            serverId = session?.serverId?.takeIf { it > 0 } ?: -1,
            realPoint = realPoint,
            rebornCount = playerStateManager.getState(charId)?.turn ?: 0,
            activeTurn2Balls = skillSystem.getCache(charId)?.activeTurn2Balls?.toSet().orEmpty(),
            isMounted = (session?.mountNpcId ?: 0) != 0,
            lastSurfaceId = eventSession?.lastSurfaceId ?: -1,
            lastChoiceCode = eventSession?.lastChoiceCode ?: -1,
            battleResult = eventSession?.battleResult ?: 0,
            fightDataKey = eventSession?.fightDataKey ?: 0,
            completedEveCounts = stateMachine.getCompletedEveCounts(charId, mapId),
            followNpcIds = followNpcIds,
            followNpcLevels = followNpcLevels,
            followNpcLoyalties = followNpcLoyalties,
            deployedNpcIds = deployedNpcIds,
            followPetCount = followPets.size,
            maxFollowPets = petSystem.getUnlockedFollowCapacity(charId),
            innNpcIds = innNpcIds,
            cartNpcIds = cartNpcIds,
            roleCountValues = roleCountValues,
            skillLevels = skillLevels,
            gold = gold,
            npcSecondReincarnationStatus = calculateNpcSecondReincarnationStatus(charId, followPets.values)
        )
    }

    /**
     * Tìm result Talk đầu tiên trong sự kiện NPC làm hội thoại fallback
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
     * Kiểm tra khoảng cách: người chơi (0,0) nghĩa là đang khởi tạo chưa có vị trí, bỏ qua kiểm tra
     */
    fun isWithinRange(px: Int, py: Int, tx: Int, ty: Int): Boolean {
        if (px == 0 && py == 0) return true  // Người chơi đang khởi tạo, không có dữ liệu vị trí
        val dx = (px - tx).toLong()
        val dy = (py - ty).toLong()
        return dx * dx + dy * dy <= MAX_INTERACT_DISTANCE_SQ
    }

    companion object {
        /** Giá trị bình phương giới hạn khoảng cách tương tác (200 pixel, lỏng hơn client 130)*/
        private const val MAX_INTERACT_DISTANCE_SQ = 200L * 200L
        private const val ROLE_COUNT_BAG_1 = 103
        private const val ROLE_COUNT_BAG_2 = 106
        private const val ROLE_COUNT_BAG_3 = 124
        private const val NPC_SECOND_REINCARNATION_MIN_LEVEL = 60
        private const val NPC_SECOND_REINCARNATION_MIN_LOYALTY = 100
        private const val DEFAULT_STORY_FACTION = 1

        private fun calculateBagCapacity(roleCountValues: Map<Int, Int>): Int {
            val expandCount = (roleCountValues[ROLE_COUNT_BAG_1] ?: 0) +
                (roleCountValues[ROLE_COUNT_BAG_2] ?: 0) +
                (roleCountValues[ROLE_COUNT_BAG_3] ?: 0)
            return (ItemConstants.INIT_BAG_SLOTS + expandCount * ItemConstants.ADD_BAG_SLOTS)
                .coerceIn(0, ItemConstants.MAX_BAG_SLOTS)
        }
    }

    /**
     * class14 = trạng thái mở hoạt động của scene.
     *
     * DVT client data không có liên kết tĩnh scene -> ActivityTime trong SceneSet.
     * SceneSet.limit1 là cờ hạn chế thao tác của map, KHÔNG phải activityId.
     *
     * Với server offline, policy mặc định là mở nội dung activity để tránh hard-block
     * vĩnh viễn các cửa/NPC/Mine chỉ vì thiếu scheduler server gốc. Khi sau này tìm được
     * nguồn schedule authoritative, chỉ cần thay implementation này.
     */
    private fun resolveActivityOpen(mapId: Int): Boolean = true
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

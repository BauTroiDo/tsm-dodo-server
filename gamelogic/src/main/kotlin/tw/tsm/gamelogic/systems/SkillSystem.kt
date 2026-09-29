package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.Turn2SkillState
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống kỹ năng — Quản lý kỹ năng đã học và nâng cấp kỹ năng của người chơi
 *
 * Vòng đời: Tải khi đăng nhập → Thao tác trong bộ nhớ → Lưu khi ngắt kết nối
 */
class SkillSystem(
    private val skillRepository: SkillRepository,
    private val characterRepository: CharacterRepository,
    private val skillDefs: Map<Int, SkillDef>
) {
    /**
     * Bộ nhớ đệm (cache) kỹ năng nhân vật
     *
     * @property element Hệ/nguyên tố của nhân vật
     * @property skillPoint Điểm kỹ năng khả dụng
     * @property skills skillId → level
     * @property dirty Đánh dấu có thay đổi cần lưu hay không
     */
    data class SkillCache(
        val element: Int,
        var skillPoint: Int,
        val skills: MutableMap<Int, Int> = mutableMapOf(),
        var availableTurn2Balls: Int = 0,
        val activeTurn2Balls: MutableSet<Int> = mutableSetOf(),
        val heroSkills: MutableMap<Int, Int> = mutableMapOf(),
        var dirty: Boolean = false
    )

    private val cache = ConcurrentHashMap<Long, SkillCache>()

    /**
     * Tải kỹ năng nhân vật khi đăng nhập
     *
     * @return Đối tượng SkillCache gồm hệ nguyên tố, điểm kỹ năng, các kỹ năng đã học
     */
    suspend fun load(characterId: Long, element: Int, skillPoint: Int): SkillCache {
        val skills = skillRepository.findByCharacterId(characterId).toMutableMap()
        val turn2 = skillRepository.findTurn2State(characterId)

        val sc = SkillCache(
            element = element,
            skillPoint = skillPoint,
            skills = skills,
            availableTurn2Balls = turn2.availableBalls.coerceAtLeast(0),
            activeTurn2Balls = turn2.activeBalls.filter { it in 1..12 }.toMutableSet(),
            heroSkills = turn2.heroSkills.filterKeys { it in 6..13 }.toMutableMap()
        )
        cache[characterId] = sc
        logger.info { "Tải kỹ năng: charId=$characterId element=$element skillPoint=$skillPoint số kỹ năng=${skills.size}" }
        return sc
    }

    /**
     * Lấy bộ nhớ đệm kỹ năng nhân vật (null nghĩa là chưa đăng nhập)
     */
    fun getCache(characterId: Long): SkillCache? = cache[characterId]

    /**
     * Điều chỉnh điểm kỹ năng (dùng khi thăng cấp / lệnh GM)
     *
     * Đồng bộ cập nhật SkillCache.skillPoint, tránh bị lệch dữ liệu với PlayerState.skillPoint.
     * Bất kỳ nơi nào sửa đổi PlayerState.skillPoint đều phải đồng thời gọi phương thức này.
     *
     * @param characterId ID nhân vật
     * @param delta Lượng thay đổi (dương = tăng, âm = giảm)
     */
    fun adjustSkillPoint(characterId: Long, delta: Int) {
        val sc = cache[characterId] ?: return
        synchronized(sc) {
            sc.skillPoint += delta
            sc.dirty = true
        }
    }

    /**
     * Người chơi nâng cấp kỹ năng (C:028-001)
     *
     * Một lần nâng nhiều kỹ năng: xác minh cả batch và prerequisite trước khi trừ điểm.
     *
     * @param upgrades Danh sách các cặp (skillId, targetLevel) cần nâng cấp
     * @return Danh sách các cặp (skillId, newLevel) thực tế đã nâng cấp thành công
     */
    fun upgradeSkills(characterId: Long, upgrades: List<Pair<Int, Int>>, playerTurn: Int = 0, allowTurn2: Boolean = false): List<Pair<Int, Int>> {
        val sc = cache[characterId] ?: return emptyList()
        // UISkillTree.SendSkill uses pairs(): dependency order is not a contract.
        synchronized(sc) {
            if (upgrades.isEmpty() || upgrades.map { it.first }.distinct().size != upgrades.size) return emptyList()
            val proposed = sc.skills.toMutableMap()
            val pending = mutableSetOf<Int>()
            var cost = 0L
            val forbidden = setOf(14026, 14077, 10016, 11016, 12016, 13015, 22011, 23011)
            val opposite = when (sc.element) { 1 -> 3; 2 -> 4; 3 -> 1; 4 -> 2; else -> -1 }
            for ((id, target) in upgrades) {
                val def = skillDefs[id] ?: return emptyList()
                if (!allowTurn2 && (id in ALL_TURN2_BASE_SKILLS || id in HERO_SKILL_IDS)) return emptyList()
                // UISkillTree.AddSkillLv explicitly returns for the LightDark tab. These skills are
                // unlocked through UISell 57, never through C28:1. Reject forged upgrade packets
                // so Turn3 skills cannot leak into the ordinary character skill store.
                if (id in TURN3_LIGHT_DARK_SKILLS) return emptyList()
                val from = sc.skills[id] ?: 0
                if (id in forbidden || def.element == opposite || target !in 1..minOf(255, def.maxLv) ||
                    target < from || from < 0 || def.learnPoint < 0 || def.levelUpPoint < 0) return emptyList()

                // SkillData.turnKind: 0 = chưa chuyển sinh, 1 = kỹ năng chuyển sinh.
                // UISkillTree chỉ mở tab Turn1 từ Turn >= 1. Server phải tự kiểm tra vì client không đáng tin cậy.
                if (from == 0 && def.turnKind > 0 && playerTurn < def.turnKind) return emptyList()

                // UISkillTree.AddSkillLv có điều kiện riêng cho bốn kỹ năng gốc Turn1.
                // Lua dùng GetSkillLv(..., true), vì vậy chỉ kỹ năng đã học trước batch mới thỏa điều kiện này.
                if (from == 0) {
                    val turn1RootPrerequisites = when (id) {
                        10020 -> setOf(10013, 10012, 10011, 10015, 10014, 10009)
                        11020 -> setOf(11015, 11014, 11013, 11006)
                        12020 -> setOf(12015, 12014, 12013)
                        13019 -> setOf(13014, 13013, 13009)
                        else -> emptySet()
                    }
                    if (turn1RootPrerequisites.isNotEmpty() &&
                        turn1RootPrerequisites.none { (sc.skills[it] ?: 0) > 0 }) return emptyList()
                }
                if (target == from) continue
                val first = if (from == 0) def.learnPoint.toLong() *
                    (if (def.element in 1..4 && def.element != sc.element) 2 else 1) else 0L
                cost += first + (target - maxOf(from, 1)).toLong() * def.levelUpPoint
                proposed[id] = target
                if (from == 0) pending.add(id)
            }
            if (sc.skillPoint < 0 || cost > sc.skillPoint) return emptyList()
            val learned = sc.skills.filterValues { it > 0 }.keys.toMutableSet()
            while (pending.isNotEmpty()) {
                val ready = pending.filter { id ->
                    val prerequisites = skillDefs.getValue(id).preSkills.filter { it in skillDefs }
                    prerequisites.isEmpty() || prerequisites.any { it in learned }
                }
                if (ready.isEmpty()) return emptyList()
                learned.addAll(ready)
                pending.removeAll(ready.toSet())
            }
            val result = upgrades.filter { (id, target) -> target > (sc.skills[id] ?: 0) }
            if (result.isNotEmpty()) {
                sc.skills.putAll(proposed)
                sc.skillPoint -= cost.toInt()
                sc.dirty = true
            }
            return result
        }
    }

    /**
     * Đổi nghề nhị chuyển: xóa skill base của nghề cũ và hero slot đã chọn.
     * Thiên Châu đã kích hoạt được giữ nguyên vì Lua biểu diễn chúng độc lập với career.
     */
    fun resetTurn2CareerSkills(characterId: Long) {
        val sc = cache[characterId] ?: return
        synchronized(sc) {
            ALL_TURN2_BASE_SKILLS.forEach { sc.skills.remove(it) }
            HERO_SKILL_IDS.forEach { sc.skills.remove(it) }
            sc.heroSkills.clear()
            sc.dirty = true
        }
    }

    /**
     * C:028-005 nâng cấp kỹ năng nhị chuyển.
     * Chỉ mục thiên châu 1..12; chỉ mục khe anh hùng 6..13. Tất cả trạng thái kèm theo chỉ được commit sau khi kiểm tra hợp lệ batch kỹ năng thành công.
     */
    fun upgradeTurn2(
        characterId: Long,
        addBalls: List<Int>,
        upgrades: List<Pair<Int, Int>>,
        heroSlots: List<Pair<Int, Int>>,
        playerTurn: Int,
        career: Int
    ): Boolean {
        val sc = cache[characterId] ?: return false
        if (playerTurn < 2 || career !in 1..4) return false
        synchronized(sc) {
            if (addBalls.distinct().size != addBalls.size || addBalls.any { it !in 1..12 || it in sc.activeTurn2Balls }) return false
            if (heroSlots.map { it.first }.distinct().size != heroSlots.size ||
                heroSlots.any { (slot, id) -> slot !in 6..13 || id !in HERO_SKILL_IDS }) return false
            if (heroSlots.any { (slot, _) -> sc.heroSkills.containsKey(slot) }) return false
            if (heroSlots.map { it.second }.distinct().size != heroSlots.size ||
                heroSlots.any { (_, id) -> id in sc.heroSkills.values || (sc.skills[id] ?: 0) > 0 }) return false
            if (heroSlots.any { (_, id) -> upgrades.none { it.first == id && it.second == 1 } }) return false
            if (addBalls.size > sc.availableTurn2Balls) return false

            val base = TURN2_BASE_SKILLS[career] ?: return false
            fun requiredSkill(ball: Int): Int = when (ball) {
                in 1..4 -> base[0]
                in 5..6 -> base[1]
                in 7..8 -> base[2]
                in 9..10 -> base[3]
                else -> base[4]
            }
            val requested = upgrades.toMap()
            if (addBalls.any { ball ->
                    val id = requiredSkill(ball)
                    (sc.skills[id] ?: 0) <= 0 && (requested[id] ?: 0) <= 0
                }) return false

            // Client Turn2 page contains only these five career skills plus chosen hero slots.
            val allowedTurn2 = base.toSet() + heroSlots.map { it.second }.toSet()
            if (upgrades.any { (id, _) ->
                    id in ALL_TURN2_BASE_SKILLS && id !in base || id in HERO_SKILL_IDS && id !in allowedTurn2
                }) return false

            val needsSkillChange = upgrades.any { (id, target) -> target > (sc.skills[id] ?: 0) }
            val changed = if (upgrades.isEmpty()) emptyList() else upgradeSkills(characterId, upgrades, playerTurn, allowTurn2 = true)
            if (needsSkillChange && changed.isEmpty()) return false

            sc.availableTurn2Balls -= addBalls.size
            sc.activeTurn2Balls.addAll(addBalls)
            heroSlots.forEach { (slot, id) -> sc.heroSkills[slot] = id }
            if (addBalls.isNotEmpty() || heroSlots.isNotEmpty()) sc.dirty = true
            return true
        }
    }

    /**
     * Lưu kỹ năng nhân vật vào DB (không hủy nạp, dùng cho tự động lưu định kỳ)
     */
    suspend fun save(characterId: Long) {
        val sc = cache[characterId] ?: return
        if (sc.dirty) {
            skillRepository.saveAll(characterId, sc.skills)
            skillRepository.saveTurn2State(characterId, Turn2SkillState(sc.availableTurn2Balls, sc.activeTurn2Balls.toSet(), sc.heroSkills.toMap()))
            characterRepository.updateSkillPoint(characterId, sc.skillPoint)
            sc.dirty = false
        }
    }

    /**
     * Lưu kỹ năng nhân vật vào DB và xóa khỏi bộ nhớ đệm
     */
    suspend fun saveAndUnload(characterId: Long) {
        val sc = cache.remove(characterId) ?: return
        if (sc.dirty) {
            skillRepository.saveAll(characterId, sc.skills)
            skillRepository.saveTurn2State(characterId, Turn2SkillState(sc.availableTurn2Balls, sc.activeTurn2Balls.toSet(), sc.heroSkills.toMap()))
            characterRepository.updateSkillPoint(characterId, sc.skillPoint)
            logger.info { "Lưu kỹ năng: charId=$characterId số kỹ năng=${sc.skills.size} skillPoint=${sc.skillPoint}" }
        }
    }
    companion object {
        private val TURN2_BASE_SKILLS = mapOf(
            1 to listOf(14038, 14043, 14044, 14045, 14046),
            2 to listOf(14038, 14039, 14040, 14041, 14042),
            3 to listOf(14038, 14047, 14048, 14049, 14050),
            4 to listOf(14038, 14051, 14052, 14053, 14054)
        )
        private val ALL_TURN2_BASE_SKILLS = TURN2_BASE_SKILLS.values.flatten().toSet()
        private val HERO_SKILL_IDS =
            (10027..10034).toSet() + (11027..11034) + (12027..12034) + (13026..13033)
        private val TURN3_LIGHT_DARK_SKILLS = setOf(
            22001, 22002, 22003, 22004, 22007, 22010, 22015, 22016, 22011,
            23001, 23002, 23003, 23004, 23009, 23010, 23015, 23016, 23011
        )
    }
}

package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
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

        val sc = SkillCache(
            element = element,
            skillPoint = skillPoint,
            skills = skills
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
     * Một lần có thể nâng nhiều kỹ năng, xử lý lần lượt từng kỹ năng.
     *
     * @param upgrades Danh sách các cặp (skillId, targetLevel) cần nâng cấp
     * @return Danh sách các cặp (skillId, newLevel) thực tế đã nâng cấp thành công
     */
    fun upgradeSkills(characterId: Long, upgrades: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val sc = cache[characterId] ?: return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()

        // synchronized bảo vệ tính nguyên tử của việc trừ skillPoint và chỉnh sửa map skills
        synchronized(sc) {
            for ((skillId, targetLevel) in upgrades) {
                val def = skillDefs[skillId]
                if (def == null) {
                    logger.warn { "Nâng cấp kỹ năng thất bại: charId=$characterId skillId=$skillId kỹ năng không tồn tại" }
                    continue
                }

                val currentLevel = sc.skills[skillId] ?: 0
                if (targetLevel <= currentLevel) continue
                if (targetLevel > def.maxLv) {
                    logger.warn { "Nâng cấp kỹ năng thất bại: charId=$characterId skillId=$skillId vượt quá cấp tối đa ${def.maxLv}" }
                    continue
                }

                // Kiểm tra kỹ năng tiên quyết
                if (!checkPrerequisites(sc, def)) {
                    logger.warn { "Nâng cấp kỹ năng thất bại: charId=$characterId skillId=$skillId chưa thỏa mãn kỹ năng tiên quyết" }
                    continue
                }

                // Tính toán điểm cần thiết (bao gồm phạt khác hệ nguyên tố)
                val cost = calculateCost(def, currentLevel, targetLevel, sc.element)
                if (sc.skillPoint < cost) {
                    logger.warn { "Nâng cấp kỹ năng thất bại: charId=$characterId skillId=$skillId không đủ điểm (cần $cost, có ${sc.skillPoint})" }
                    continue
                }

                // Trừ điểm, thăng cấp
                sc.skillPoint -= cost
                sc.skills[skillId] = targetLevel
                sc.dirty = true
                result.add(skillId to targetLevel)

                logger.info { "Nâng cấp kỹ năng: charId=$characterId ${def.name} $currentLevel→$targetLevel tiêu hao $cost điểm, còn lại ${sc.skillPoint}" }
            }
        }

        return result
    }

    /**
     * Kiểm tra các kỹ năng tiên quyết đã học hay chưa
     */
    private fun checkPrerequisites(sc: SkillCache, def: SkillDef): Boolean {
        for (preSkillId in def.preSkills) {
            if (preSkillId > 0 && !sc.skills.containsKey(preSkillId)) {
                return false
            }
        }
        return true
    }

    /**
     * Tính toán điểm cần để nâng cấp
     *
     * Cấp đầu tiên cần learnPoint, các cấp sau mỗi cấp cần levelUpPoint.
     * Logic KERO: Khi hệ của kỹ năng khác với hệ của nhân vật, chi phí cơ bản ×2.
     *
     * @param playerElement Hệ nguyên tố của nhân vật (dùng để phán đoán phạt khác hệ)
     */
    private fun calculateCost(def: SkillDef, fromLevel: Int, toLevel: Int, playerElement: Int): Int {
        val elementPenalty = if (def.element > 0 && def.element != playerElement) 2 else 1
        var cost = 0
        for (lv in (fromLevel + 1)..toLevel) {
            val baseCost = if (lv == 1) def.learnPoint else def.levelUpPoint
            cost += baseCost * elementPenalty
        }
        return cost
    }

    /**
     * Lưu kỹ năng nhân vật vào DB (không hủy nạp, dùng cho tự động lưu định kỳ)
     */
    suspend fun save(characterId: Long) {
        val sc = cache[characterId] ?: return
        if (sc.dirty) {
            skillRepository.saveAll(characterId, sc.skills)
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
            characterRepository.updateSkillPoint(characterId, sc.skillPoint)
            logger.info { "Lưu kỹ năng: charId=$characterId số kỹ năng=${sc.skills.size} skillPoint=${sc.skillPoint}" }
        }
    }
}
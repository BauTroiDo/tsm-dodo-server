package tw.tsm.domain.interfaces.repositories

/**
 * 技能持久化介面
 */
data class Turn2SkillState(
    val availableBalls: Int = 0,
    val activeBalls: Set<Int> = emptySet(),
    val heroSkills: Map<Int, Int> = emptyMap()
)

interface SkillRepository {
    /**
     * 載入角色所有已學技能
     *
     * @return skillId → level 映射
     */
    suspend fun findByCharacterId(characterId: Long): Map<Int, Int>

    /**
     * 批量保存角色技能（全量覆蓋）
     *
     * @param skills skillId → level 映射
     */
    suspend fun saveAll(characterId: Long, skills: Map<Int, Int>)

    /** 載入二轉天珠與英雄技能槽。 */
    suspend fun findTurn2State(characterId: Long): Turn2SkillState = Turn2SkillState()

    /** 全量保存二轉天珠與英雄技能槽。 */
    suspend fun saveTurn2State(characterId: Long, state: Turn2SkillState) {}
}

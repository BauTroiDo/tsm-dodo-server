package tw.tsm.domain.interfaces.repositories

/**
 * 技能持久化介面
 */
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
}

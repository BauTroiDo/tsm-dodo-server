package tw.tsm.domain.interfaces.repositories

/**
 * 技能持久化介面
 */
data class Turn2SkillState(
    val availableBalls: Int = 0,
    val activeBalls: Set<Int> = emptySet(),
    val heroSkills: Map<Int, Int> = emptyMap()
)

data class Turn3State(
    val element: Int = 0,
    val exp: Long = 0,
    val intelligence: Int = 0,
    val atk: Int = 0,
    val def: Int = 0,
    val agi: Int = 0,
    val hpx: Int = 0,
    val spx: Int = 0,
    val skills: Map<Int, Int> = emptyMap()
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

    /** 載入三轉獨立成長資料。 */
    suspend fun findTurn3State(characterId: Long): Turn3State = Turn3State()

    /** 保存三轉獨立成長資料。 */
    suspend fun saveTurn3State(characterId: Long, state: Turn3State) {}
}

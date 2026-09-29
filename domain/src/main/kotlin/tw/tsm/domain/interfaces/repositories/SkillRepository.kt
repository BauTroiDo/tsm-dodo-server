package tw.tsm.domain.interfaces.repositories

/**
 * Giao diện persist kỹ năng
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
     * Tải toàn bộ kỹ năng đã học của nhân vật
     *
     * @return ánh xạ skillId → level
     */
    suspend fun findByCharacterId(characterId: Long): Map<Int, Int>

    /**
     * Lưu hàng loạt kỹ năng nhân vật (ghi đè toàn bộ)
     *
     * @param skills ánh xạ skillId → level
     */
    suspend fun saveAll(characterId: Long, skills: Map<Int, Int>)

    /** Tải thiên châu nhị chuyển và các ô kỹ năng anh hùng.*/
    suspend fun findTurn2State(characterId: Long): Turn2SkillState = Turn2SkillState()

    /** Lưu toàn bộ thiên châu nhị chuyển và các ô kỹ năng anh hùng.*/
    suspend fun saveTurn2State(characterId: Long, state: Turn2SkillState) {}

    /** Tải dữ liệu tăng trưởng độc lập của tam chuyển.*/
    suspend fun findTurn3State(characterId: Long): Turn3State = Turn3State()

    /** Lưu dữ liệu tăng trưởng độc lập của tam chuyển.*/
    suspend fun saveTurn3State(characterId: Long, state: Turn3State) {}
}

package tw.tsm.domain.valueobjects

data class SoulSkillData(
    val id: Int = 0,
    val level: Int = 0,
)

/**
 * Runtime/persistence form of DVT SoulThing (protocol 120).
 *
 * This is intentionally separate from FollowNpcData.soulId, which is the
 * protocol-15 魂玉/original-NPC id used by transformed warriors.
 */
data class SoulThingData(
    val sn: Long,
    val itemId: Int,
    val element: Int,
    val equipId: Int = 0,
    val level: Int = 1,
    val starLevel: Int = 0,
    val startAtt: List<Int> = List(6) { 0 },
    val nowAtt: List<Int> = List(6) { 0 },
    val skills: List<SoulSkillData> = List(4) { SoulSkillData() },
    val lockFlags: Int = 0,
) {
    init {
        require(startAtt.size == 6) { "Soul startAtt phải có đúng 6 phần tử" }
        require(nowAtt.size == 6) { "Soul nowAtt phải có đúng 6 phần tử" }
        require(skills.size == 4) { "Soul skills phải có đúng 4 phần tử" }
    }
}

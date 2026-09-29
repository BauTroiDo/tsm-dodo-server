package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Trạng thái bổ sung của kỹ năng nhị chuyển.
 * kind=1: index 0 = số Thiên Châu khả dụng, index 1..12 = Thiên Châu đã kích hoạt.
 * kind=2: index 6..13 = ô kỹ năng anh hùng nhị chuyển, value = skillId.
 */
object CharacterTurn2Skills : Table("character_turn2_skills") {
    val characterId = long("character_id")
    val kind = integer("kind")
    val slotIndex = integer("slot_index")
    val value = integer("value").default(0)

    override val primaryKey = PrimaryKey(characterId, kind, slotIndex)

    init { index(false, characterId) }
}

package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 二轉技能附加狀態。
 * kind=1: index 0 = 可用天珠數, index 1..12 = 已啟動天珠。
 * kind=2: index 6..13 = 二轉英雄技能槽，value = skillId。
 */
object CharacterTurn2Skills : Table("character_turn2_skills") {
    val characterId = long("character_id")
    val kind = integer("kind")
    val slotIndex = integer("slot_index")
    val value = integer("value").default(0)

    override val primaryKey = PrimaryKey(characterId, kind, slotIndex)

    init { index(false, characterId) }
}

package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** 三轉獨立成長資料；不與一/二轉 EXP、六圍混用。 */
object CharacterTurn3States : Table("character_turn3_states") {
    val characterId = long("character_id")
    val element = integer("element").default(0)
    val exp = long("exp").default(0)
    val int_ = integer("int_").default(0)
    val atk = integer("atk").default(0)
    val def = integer("def").default(0)
    val agi = integer("agi").default(0)
    val hpx = integer("hpx").default(0)
    val spx = integer("spx").default(0)
    override val primaryKey = PrimaryKey(characterId)
}

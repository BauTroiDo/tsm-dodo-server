package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Persistent DVT protocol-120 SoulThing state. */
object CharacterSouls : Table("character_souls") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    val sn = long("sn").uniqueIndex()
    val itemId = integer("item_id")
    val element = integer("element")
    val equipId = integer("equip_id").default(0)
    val level = integer("level").default(1)
    val starLevel = integer("star_level").default(0)

    val startAtk = integer("start_atk").default(0)
    val startDef = integer("start_def").default(0)
    val startInt = integer("start_int").default(0)
    val startAgi = integer("start_agi").default(0)
    val startHpx = integer("start_hpx").default(0)
    val startSpx = integer("start_spx").default(0)

    val nowAtk = integer("now_atk").default(0)
    val nowDef = integer("now_def").default(0)
    val nowInt = integer("now_int").default(0)
    val nowAgi = integer("now_agi").default(0)
    val nowHpx = integer("now_hpx").default(0)
    val nowSpx = integer("now_spx").default(0)

    val skill1Id = integer("skill1_id").default(0)
    val skill1Lv = integer("skill1_lv").default(0)
    val skill2Id = integer("skill2_id").default(0)
    val skill2Lv = integer("skill2_lv").default(0)
    val skill3Id = integer("skill3_id").default(0)
    val skill3Lv = integer("skill3_lv").default(0)
    val skill4Id = integer("skill4_id").default(0)
    val skill4Lv = integer("skill4_lv").default(0)

    val lockFlags = integer("lock_flags").default(0)

    override val primaryKey = PrimaryKey(id)
}

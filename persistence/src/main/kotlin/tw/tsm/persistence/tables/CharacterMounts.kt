package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色坐騎資料表
 *
 * 每個角色一筆，儲存坐騎等級、6 種屬性點數、當前坐騎 NPC ID。
 */
object CharacterMounts : Table("character_mounts") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").uniqueIndex()
    /** 坐騎等級 */
    val level = integer("level").default(1)
    /** 屬性點數 1（攻擊） */
    val attrPoint1 = integer("attr_point_1").default(0)
    /** 屬性點數 2（智力） */
    val attrPoint2 = integer("attr_point_2").default(0)
    /** 屬性點數 3（防禦） */
    val attrPoint3 = integer("attr_point_3").default(0)
    /** 屬性點數 4（額外HP） */
    val attrPoint4 = integer("attr_point_4").default(0)
    /** 屬性點數 5（額外SP） */
    val attrPoint5 = integer("attr_point_5").default(0)
    /** 屬性點數 6（預留） */
    val attrPoint6 = integer("attr_point_6").default(0)
    /** 當前坐騎 NPC ID（0 = 無） */
    val currentNpcId = integer("current_npc_id").default(0)

    override val primaryKey = PrimaryKey(id)
}

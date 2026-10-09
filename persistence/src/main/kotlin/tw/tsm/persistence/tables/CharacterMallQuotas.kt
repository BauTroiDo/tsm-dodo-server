package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** A dedicated calendar period avoids abusing the client-visible RoleCount maxValue. */
object CharacterMallQuotas : Table("character_mall_quotas") {
    val characterId = long("character_id")
    val flag = integer("quota_flag")
    val period = integer("quota_period")
    val count = integer("purchase_count")
    override val primaryKey = PrimaryKey(characterId, flag)
}

package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Global persisted jackpot pool for DVT warrior EXP slot (mainKind 76). */
object ExpSlotStates : Table("exp_slot_state") {
    val id = integer("id")
    val jackpotExp = long("jackpot_exp").default(0L)
    override val primaryKey = PrimaryKey(id)
}

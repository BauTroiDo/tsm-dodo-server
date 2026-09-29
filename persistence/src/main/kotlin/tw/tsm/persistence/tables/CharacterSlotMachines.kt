package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/** Trạng thái Vòng quay Thôn Thực (UISlotMachine/mainKind 72) theo nhân vật. */
object CharacterSlotMachines : Table("character_slot_machines") {
    val characterId = long("character_id")
    val coin = integer("coin").default(0)

    override val primaryKey = PrimaryKey(characterId)
}

/** 16 ô thưởng đang được giữ của Vòng quay Thôn Thực. */
object CharacterSlotMachineAwards : Table("character_slot_machine_awards") {
    val characterId = long("character_id").index()
    val awardIndex = integer("award_index")
    val itemId = integer("item_id")
    val quantity = integer("quantity")
    val slotIndex = integer("slot_index")

    override val primaryKey = PrimaryKey(characterId, awardIndex)
}

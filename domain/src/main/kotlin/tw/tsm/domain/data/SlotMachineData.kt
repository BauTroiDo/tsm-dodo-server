package tw.tsm.domain.data

/** Một candidate giải thưởng giải mã từ IOS_Slot.dat. */
data class SlotPoolEntry(
    val sourceIndex: Int,
    val itemId: Int,
    val count: Int,
    val weight: Int,
)

/** Bốn pool thưởng A→D của Vòng quay / Tiểu Mary. */
data class SlotMachinePools(
    val poolA: List<SlotPoolEntry>,
    val poolB: List<SlotPoolEntry>,
    val poolC: List<SlotPoolEntry>,
    val poolD: List<SlotPoolEntry>,
)

/** Một ô trong bàn 16 giải thưởng. */
data class SlotMachineAward(
    val awardIndex: Int,
    val itemId: Int,
    val count: Int,
    val slotIndex: Int,
)

/** State bền vững của Vòng quay. */
data class SlotMachineStateData(
    val coin: Int = 0,
    val awards: List<SlotMachineAward> = emptyList(),
)

/** Kết quả một request quay đã hoàn thành. */
data class SlotMachineSpinResult(
    val coin: Int,
    val resultSlotIndices: List<Int>,
    val changedBagSlots: List<Pair<Int, tw.tsm.domain.valueobjects.ThingData>>,
)

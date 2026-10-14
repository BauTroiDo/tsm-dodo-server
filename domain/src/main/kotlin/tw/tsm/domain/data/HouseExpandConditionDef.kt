package tw.tsm.domain.data

/** Exact DVT IOS_HouseExpandCondition_C.dat five-byte row, price in 10,000 gold. */
data class HouseExpandConditionDef(
    val level: Int,
    val building: Int,
    val floorLevel: Int,
    val furnitureKindsRequired: Int,
    val tenThousandGoldUnits: Int,
) {
    val goldCost: Int get() = tenThousandGoldUnits * 10_000
}

package tw.tsm.domain.data

data class SacredEquipSubDef(
    val no: Int,
    val attributeKind: Int,
    val randomValues: List<Int>,
) {
    init {
        require(no in 0..0xFFFF)
        require(attributeKind in 0..0xFF)
        require(randomValues.size == 10)
        require(randomValues.all { it in 0..0xFFFF })
    }
}

data class SacredEquipSubDomainDef(
    val threshold: Int,
    val attributeKind: Int,
) {
    init {
        require(threshold in 1..6)
        require(attributeKind in 0..0xFF)
    }
}

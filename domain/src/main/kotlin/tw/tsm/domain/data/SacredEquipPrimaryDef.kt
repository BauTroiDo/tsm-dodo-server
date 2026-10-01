package tw.tsm.domain.data

data class SacredEquipPrimaryDef(
    val no: Int,
    val attributeKind: Int,
    val upgradeLv: List<Int>
) {
    init {
        require(no in 0..0xFFFF)
        require(attributeKind in 0..0xFF)
        require(upgradeLv.size == 16)
        require(upgradeLv.all { it in 0..0xFFFF })
    }

    fun valueAt(level: Int): Int? = upgradeLv.getOrNull(level)
}

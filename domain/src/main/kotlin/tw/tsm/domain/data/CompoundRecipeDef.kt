package tw.tsm.domain.data

/**
 * DVT Mobile Compound recipe — Data/CompoundData.lua.
 *
 * resultKind == EItemKind của thành phẩm.
 * materialA/B/C là ItemDef.material; a/b/cDegree là tỷ lệ gốc của DVT (0..5).
 */
data class CompoundRecipeDef(
    val resultKind: Int,
    val materialA: Int,
    val materialB: Int,
    val materialC: Int,
    val degreeA: Int,
    val degreeB: Int,
    val degreeC: Int,
) {
    val materials: List<Int>
        get() = listOf(materialA, materialB, materialC).filter { it > 0 }

    val degrees: List<Int>
        get() = listOf(degreeA, degreeB, degreeC).take(materials.size)

    val arity: Int get() = materials.size

    /** Compound table enumerates combinations in sorted material order; input slot order is irrelevant. */
    fun matches(inputMaterials: List<Int>): Boolean =
        inputMaterials.size == arity && inputMaterials.sorted() == materials.sorted()
}

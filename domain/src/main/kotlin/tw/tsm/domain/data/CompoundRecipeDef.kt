package tw.tsm.domain.data

/**
 * DVT Mobile Compound recipe — Data/CompoundData.lua.
 *
 * resultKind == EItemKind của thành phẩm.
 * materialA/B/C là ItemDef.material; a/b/cDegree là tỷ lệ gốc của DVT (0..5).
 *
 * IOS_Compound.dat có một số record legacy không sạch: material > 0 nhưng degree == 0,
 * hoặc material == 0 nhưng degree > 0. Material byte là nguồn xác định ô có tham gia;
 * với ô có material thật, degree 0 được normalize thành 1 để không tạo tỷ lệ 0 bất khả thi.
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
    data class MaterialPart(
        val material: Int,
        val degree: Int,
    )

    val parts: List<MaterialPart>
        get() = listOf(
            materialA to degreeA,
            materialB to degreeB,
            materialC to degreeC,
        ).mapNotNull { (material, degree) ->
            if (material <= 0) null else MaterialPart(material, degree.coerceAtLeast(1))
        }

    val materials: List<Int>
        get() = parts.map { it.material }

    val degrees: List<Int>
        get() = parts.map { it.degree }

    val arity: Int get() = parts.size

    /** Compound table enumerates material combinations; input slot order is irrelevant. */
    fun matches(inputMaterials: List<Int>): Boolean =
        inputMaterials.size == arity && inputMaterials.sorted() == materials.sorted()
}

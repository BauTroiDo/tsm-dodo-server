package tw.tsm.domain.data

/** One DVT ExclusiveWeaponData attribute pair: kind(U16) + value(U32). */
data class ExclusiveWeaponAttributeDef(
    val kind: Int,
    val value: Long,
)

/**
 * DVT Data/ExclusiveWeaponData.lua + IOS_ExclusiveWeapon_C.dat.
 *
 * Keyed by itemId then enhanceLevel in GameDataManager.
 */
data class ExclusiveWeaponDef(
    val itemId: Int,
    val enhanceLevel: Int,
    val skillId: Int,
    val skillLevel: Int,
    val attributes: List<ExclusiveWeaponAttributeDef>,
    val element: Int,
    val elementValue: Long,
    val plusAttribute: ExclusiveWeaponAttributeDef,
) {
    init {
        require(attributes.size == 2) { "ExclusiveWeaponDef requires exactly two base attributes" }
    }
}

/** DVT Data/CollectWarriorSortData.lua + IOS_CollectWarriorSort_C.dat. */
data class CollectWarriorSortDef(
    val index: Int,
    val npcIds: List<Int>,
    val nft: Int,
) {
    init {
        require(npcIds.size == 3) { "CollectWarriorSortDef requires exactly three npcIds" }
    }
}

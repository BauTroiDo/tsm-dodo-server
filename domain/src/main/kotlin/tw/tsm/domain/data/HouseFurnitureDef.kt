package tw.tsm.domain.data

/** Native DVT IOS_Furniture_C.dat, server-verified decoration data. */
data class HouseFurnitureDef(
    val id: Int,
    val type: Int,
    val kind: Int,
    val occupyX: Int,
    val occupyY: Int,
    val decorateScore: Int,
)

package tw.tsm.domain.data

/**
 * DVT TitleData row used by the server.
 *
 * The client owns all presentation fields (HUD texture/colors/cape/effect). The
 * authoritative server-side fields are the title id and the permanent BitFlag
 * required to equip it.
 */
data class TitleDef(
    val id: Int,
    val nameStringId: Long,
    val flagId: Int,
)

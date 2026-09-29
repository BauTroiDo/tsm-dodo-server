package tw.tsm.gamelogic.systems

/**
 * Compatibility gate for the custom 9-pet extension.
 *
 * DVT legacy client/server contract carries four followers. The extension may
 * raise carried roster capacity to nine only for a client build that has been
 * audited/patched for slots 5..9. Progression/quest unlock is intentionally a
 * later phase; this policy only separates legacy capacity from extension max.
 */
data class PetRosterPolicy(
    val ninePetEnabled: Boolean = false,
    val extensionMaxCarried: Int = MAX_EXTENSION_CARRIED,
) {
    init {
        require(extensionMaxCarried in LEGACY_CARRIED..MAX_EXTENSION_CARRIED) {
            "extensionMaxCarried must be in $LEGACY_CARRIED..$MAX_EXTENSION_CARRIED"
        }
    }

    fun carriedCapacity(): Int =
        if (ninePetEnabled) extensionMaxCarried else LEGACY_CARRIED

    companion object {
        const val LEGACY_CARRIED = 4
        const val MAX_EXTENSION_CARRIED = 9
    }
}

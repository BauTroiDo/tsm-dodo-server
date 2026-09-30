package tw.tsm.gamelogic.systems

/**
 * Compatibility/progression gate for the custom 9-pet carried roster.
 *
 * Legacy DVT supports four carried followers. The extension can expose up to
 * nine logical carried slots, while progression controls how many NEW carried
 * slots may be filled. Existing records above the current unlock are preserved
 * so rollout/migration never deletes a pet.
 */
data class PetRosterPolicy(
    val ninePetEnabled: Boolean = false,
    val extensionMaxCarried: Int = MAX_EXTENSION_CARRIED,
    val progressionEnabled: Boolean = false,
    val unlockLevels: List<Int> = DEFAULT_UNLOCK_LEVELS,
    val rebirthUnlockAll: Boolean = true,
) {
    init {
        require(extensionMaxCarried in LEGACY_CARRIED..MAX_EXTENSION_CARRIED) {
            "extensionMaxCarried must be in $LEGACY_CARRIED..$MAX_EXTENSION_CARRIED"
        }
        require(unlockLevels.size >= extensionMaxCarried - LEGACY_CARRIED) {
            "unlockLevels must define at least ${extensionMaxCarried - LEGACY_CARRIED} extension slots"
        }
        require(unlockLevels.zipWithNext().all { (a, b) -> a < b }) {
            "unlockLevels must be strictly increasing"
        }
    }

    /** Hard protocol/UI ceiling supported by the selected client/server mode. */
    fun supportedCapacity(): Int =
        if (ninePetEnabled) extensionMaxCarried else LEGACY_CARRIED

    /**
     * Number of carried slots currently unlocked for NEW placement.
     *
     * Default behavior remains unchanged unless progression is explicitly enabled.
     * A rebirthed character can optionally unlock all extension slots immediately.
     *
     * questBonusSlots is a reserved hook for the later quest-chain phase; no
     * quest IDs are invented here.
     */
    fun unlockedCapacity(
        level: Int,
        turn: Int,
        questBonusSlots: Int = 0,
    ): Int {
        val supported = supportedCapacity()
        if (!ninePetEnabled || !progressionEnabled) return supported
        if (rebirthUnlockAll && turn > 0) return supported

        val levelUnlocked = unlockLevels
            .take(supported - LEGACY_CARRIED)
            .count { level >= it }

        return (LEGACY_CARRIED + levelUnlocked + questBonusSlots)
            .coerceIn(LEGACY_CARRIED, supported)
    }

    /** Backward-compatible alias used by older tests/reference code. */
    fun carriedCapacity(): Int = supportedCapacity()

    companion object {
        const val LEGACY_CARRIED = 4
        const val MAX_EXTENSION_CARRIED = 9

        /** Slot 5..9 unlock at Lv40/80/120/160/200 before rebirth. */
        val DEFAULT_UNLOCK_LEVELS = listOf(40, 80, 120, 160, 200)
    }
}

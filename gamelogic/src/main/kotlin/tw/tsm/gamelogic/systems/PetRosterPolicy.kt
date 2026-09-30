package tw.tsm.gamelogic.systems

/**
 * Compatibility policy for the custom 9-pet carried roster and battle deployment.
 *
 * The carried roster is always fully available when nine-pet mode is enabled.
 * Progression applies only to how many carried followers may enter battle at once.
 * This keeps ownership/storage independent from combat deployment.
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

    /** Carried roster capacity is never progression-gated. */
    fun unlockedCapacity(
        @Suppress("UNUSED_PARAMETER") level: Int,
        @Suppress("UNUSED_PARAMETER") turn: Int,
        @Suppress("UNUSED_PARAMETER") questBonusSlots: Int = 0,
    ): Int = supportedCapacity()

    /** No roster unlock milestone remains; progression is battle-only. */
    fun nextUnlockLevel(
        @Suppress("UNUSED_PARAMETER") level: Int,
        @Suppress("UNUSED_PARAMETER") turn: Int,
    ): Int? = null

    /**
     * Number of follow NPCs that may enter battle simultaneously.
     *
     * Legacy mode keeps the original single deployed follower. Nine-pet progression
     * starts at four battle followers, then unlocks slots 5..9 at the configured
     * level milestones. Rebirth may unlock all nine immediately.
     */
    fun battleDeploymentCapacity(
        level: Int,
        turn: Int,
        questBonusSlots: Int = 0,
    ): Int {
        val supported = supportedCapacity()
        if (!ninePetEnabled || !progressionEnabled) return LEGACY_BATTLE_DEPLOY
        if (rebirthUnlockAll && turn > 0) return supported

        val base = BASE_NINE_PET_BATTLE_DEPLOY.coerceAtMost(supported)
        val levelUnlocked = unlockLevels
            .take((supported - base).coerceAtLeast(0))
            .count { level >= it }

        return (base + levelUnlocked + questBonusSlots).coerceIn(base, supported)
    }

    /** Next battle-deployment milestone, or null when battle progression is not gating. */
    fun nextBattleUnlockLevel(level: Int, turn: Int): Int? {
        val supported = supportedCapacity()
        if (!ninePetEnabled || !progressionEnabled) return null
        if (rebirthUnlockAll && turn > 0) return null

        val deployed = battleDeploymentCapacity(level, turn)
        if (deployed >= supported) return null

        val base = BASE_NINE_PET_BATTLE_DEPLOY.coerceAtMost(supported)
        return unlockLevels.getOrNull(deployed - base)
    }

    /** Backward-compatible alias used by older tests/reference code. */
    fun carriedCapacity(): Int = supportedCapacity()

    companion object {
        const val LEGACY_CARRIED = 4
        const val MAX_EXTENSION_CARRIED = 9
        const val LEGACY_BATTLE_DEPLOY = 1
        const val BASE_NINE_PET_BATTLE_DEPLOY = 4

        /** Slot 5..9 unlock at Lv40/80/120/160/200 before rebirth. */
        val DEFAULT_UNLOCK_LEVELS = listOf(40, 80, 120, 160, 200)
    }
}

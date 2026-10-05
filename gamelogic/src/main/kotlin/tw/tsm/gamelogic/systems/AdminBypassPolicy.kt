package tw.tsm.gamelogic.systems

import tw.tsm.domain.interfaces.GameSession

/**
 * Central policy for GM/Admin testing bypasses.
 *
 * Normal players always follow authoritative DVT rules. GM accounts (gmLevel > 0)
 * receive the safe testing bypasses below. Destructive/economy-affecting bypasses
 * stay disabled by default and must be explicitly introduced by the subsystem that
 * needs them.
 */
class AdminBypassPolicy(
    val defaults: AdminBypassFlags = AdminBypassFlags(),
) {
    fun flags(session: GameSession): AdminBypassFlags =
        if (session.gmLevel > 0) defaults else AdminBypassFlags.NONE

    fun bypassSchedule(session: GameSession): Boolean = flags(session).bypassSchedule
    fun bypassLevel(session: GameSession): Boolean = flags(session).bypassLevel
    fun bypassEntryLimit(session: GameSession): Boolean = flags(session).bypassEntryLimit
    fun bypassQuestGate(session: GameSession): Boolean = flags(session).bypassQuestGate
    fun freeCost(session: GameSession): Boolean = flags(session).freeCost
    fun rewardRepeatTest(session: GameSession): Boolean = flags(session).rewardRepeatTest
}

data class AdminBypassFlags(
    val bypassSchedule: Boolean = true,
    val bypassLevel: Boolean = true,
    val bypassEntryLimit: Boolean = true,
    val bypassQuestGate: Boolean = true,
    val freeCost: Boolean = false,
    val rewardRepeatTest: Boolean = false,
) {
    companion object {
        val NONE = AdminBypassFlags(
            bypassSchedule = false,
            bypassLevel = false,
            bypassEntryLimit = false,
            bypassQuestGate = false,
            freeCost = false,
            rewardRepeatTest = false,
        )
    }
}

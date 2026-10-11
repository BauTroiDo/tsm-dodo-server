package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.MarkNavigationTarget
import tw.tsm.domain.data.MarkStepDef

/**
 * Server-side parity tool for the DVT client's MarkManager.GetMission and
 * MarkManager.Navigation. It does not teleport players or replace client A*.
 */
object QuestNavigationPolicy {
    data class Guidance(
        val displayedMissionId: Int,
        val activeMissionId: Int,
        val step: Int,
        val target: MarkNavigationTarget,
        val readyToTurnIn: Boolean,
        val description: String,
    )

    /**
     * DVT displays mission N+1 for an active N with bitId=0, but navigation
     * and progress still use N's MarkStep records. Example: 10075 -> 10074.
     */
    fun resolveActiveMissionId(
        displayedMissionId: Int,
        activeSteps: Map<Int, Int>,
        marks: Map<Int, MarkDef>,
    ): Int? {
        if (activeSteps.containsKey(displayedMissionId)) return displayedMissionId
        val base = displayedMissionId - 1
        return base.takeIf { base > 0 && activeSteps.containsKey(base) && marks[base]?.bitId == 0 }
    }

    /**
     * Matches DVT Lua precisely:
     * 1 capture requires owned pet; 2 kill remains unmet while on this step;
     * 3 item requires bag quantity. In DVT's two kill-step records (13221/2,
     * 13306/1), the battle's Eve result advances the mission to the next step.
     * The original Lua leaves kind=2 empty and incorrectly routes to turn-in.
     * First unmet condition selects its target; otherwise use endTarget.
     */
    fun resolve(
        displayedMissionId: Int,
        activeSteps: Map<Int, Int>,
        marks: Map<Int, MarkDef>,
        steps: Map<Int, Map<Int, MarkStepDef>>,
        itemCounts: Map<Int, Int>,
        followNpcIds: Set<Int> = emptySet(),
    ): Guidance? {
        val base = resolveActiveMissionId(displayedMissionId, activeSteps, marks) ?: return null
        val step = activeSteps[base] ?: return null
        val entry = steps[base]?.get(step) ?: return null
        val unmet = entry.conditions.firstOrNull {
            when (it.kind) {
                1 -> it.id !in followNpcIds
                2 -> true // Kill is pending until the battle Eve advances the mission step.
                3 -> (itemCounts[it.id] ?: 0) < it.requiredCount
                else -> false
            }
        }
        val target = unmet?.target ?: entry.endTarget
        return Guidance(displayedMissionId, base, step, target, unmet == null, entry.description)
    }
}

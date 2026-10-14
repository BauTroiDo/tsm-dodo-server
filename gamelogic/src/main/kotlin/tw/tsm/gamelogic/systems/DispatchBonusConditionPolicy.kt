package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DispatchBonusDef

/**
 * Original DVT DispatchBonusData:IsNpcMeetCondition (Lua).
 *
 * Only the condition predicate is source-confirmed. Award effectKind /
 * effectValue arithmetic belongs to the authoritative game server and must
 * NOT be guessed from a successful match.
 */
object DispatchBonusConditionPolicy {
    const val MORAL = 1
    const val ELEMENT = 2

    fun isMet(def: DispatchBonusDef?, npcMoral: Int, npcElement: Int): Boolean =
        when (def?.conditionKind) {
            MORAL -> def.conditionValue == npcMoral
            ELEMENT -> def.conditionValue == npcElement
            else -> false
        }

    fun matchingEffects(
        first: DispatchBonusDef?,
        second: DispatchBonusDef?,
        npcMoral: Int,
        npcElement: Int,
    ): Pair<Boolean, Boolean> =
        isMet(first, npcMoral, npcElement) to isMet(second, npcMoral, npcElement)
}

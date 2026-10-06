package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.EWar

/**
 * Whether a finished battle belongs to an Eve event chain.
 * Arena VS/Chaos/Mix use EWar.VS and must close cleanly without trying to resume
 * an unrelated NPC EventSession.
 */
object BattleLifecyclePolicy {
    fun shouldResumeEve(warType: Int): Boolean =
        warType !in setOf(EWar.PK_PLAYERS, EWar.PK_MAP_NPC, EWar.VS)
}

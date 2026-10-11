package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.EWar

/** Battle loot is handed out before Eve's battle-end continuation callback. */
object EveBattleRewardFencePolicy {
    fun needsReservation(warType: EWar, playerWin: Boolean, suppressGenericRewards: Boolean): Boolean =
        warType == EWar.EVENT && playerWin && !suppressGenericRewards
}

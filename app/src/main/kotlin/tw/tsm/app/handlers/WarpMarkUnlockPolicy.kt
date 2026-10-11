package tw.tsm.app.handlers

import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.WarpDef
import tw.tsm.gamelogic.systems.QuestSystem

/**
 * C:068 teleport unlock parity with UITeleport.SetupSkyPointData.
 *
 * DVT checks MarkManager.flags, NOT BitFlag.foreverFlags. Those are persisted
 * in different DB tables: character_mission_flags vs character_bit_flags.
 */
object WarpMarkUnlockPolicy {
    suspend fun isUnlocked(
        charId: Long,
        warpDef: WarpDef,
        markDefs: Map<Int, MarkDef>,
        questSystem: QuestSystem,
    ): Boolean {
        if (warpDef.mark == 0) return true
        val markDef = markDefs[warpDef.mark] ?: return false
        if (markDef.bitId == 0) return true // preserve existing no-flag marker handling
        return questSystem.hasMissionFlag(charId, warpDef.mark)
    }
}

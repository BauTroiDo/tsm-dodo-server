package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.interfaces.GameSession

/**
 * DVT Dungeon core (protocol 47).
 *
 * F1 intentionally implements the evidenced single-player branch (kind=2, maxPlayer=1).
 * Multiplayer rooms, weekly/soul criteria, skip/reward/reset are separate milestones.
 */
class DungeonSystem(
    private val defs: Map<Int, DungeonDef>,
    private val questSystem: QuestSystem,
    private val partySystem: PartySystem,
    private val specialTeleport: SpecialSceneTeleportService,
) {
    fun getDef(id: Int): DungeonDef? = defs[id]

    suspend fun validateSingleCreate(session: GameSession, dungeonId: Int): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        if (session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (def.kind != KIND_SINGLE || def.maxPlayer != 1) return RESULT_NO_SPACE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        if (def.dailyFlag != 0 && def.dailyCount > 0) {
            val used = questSystem.getMissions(session.roleId.toLong())
                .firstOrNull { it.missionId == def.dailyFlag }
                ?.step ?: 0
            if (used >= def.dailyCount) return RESULT_COUNT_EXHAUSTED
        }

        if (session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        return RESULT_OK
    }

    suspend fun enterSingle(session: GameSession, dungeonId: Int): Boolean {
        val def = defs[dungeonId] ?: return false
        return specialTeleport.enterSpecialScene(session, def.sceneId)
    }

    companion object {
        const val KIND_SINGLE = 2
        const val RESULT_OK = 0
        const val RESULT_NO_DUNGEON = 1
        const val RESULT_LEVEL_MISMATCH = 2
        const val RESULT_ALREADY_IN_ROOM = 3
        const val RESULT_COUNT_EXHAUSTED = 4
        const val RESULT_CANNOT_TEAM = 5
        const val RESULT_NO_SPACE = 7
        const val RESULT_IN_BATTLE = 8
    }
}

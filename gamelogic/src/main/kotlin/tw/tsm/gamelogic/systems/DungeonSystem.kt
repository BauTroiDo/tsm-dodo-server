package tw.tsm.gamelogic.systems

import java.util.concurrent.ConcurrentHashMap
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.interfaces.GameSession

data class ActiveDungeonRun(
    val dungeonId: Int,
    val startTime: Double,
    val instanceId: Int,
)

/**
 * DVT Dungeon core (protocol 47).
 *
 * F1 implements the evidenced single-player branch (kind=2, maxPlayer=1).
 * F2-A adds normal multiplayer room-entry validation (kind=1).
 * Weekly/soul criteria, skip/reward/reset and instance start remain separate milestones.
 */
class DungeonSystem(
    private val defs: Map<Int, DungeonDef>,
    private val questSystem: QuestSystem,
    private val partySystem: PartySystem,
    private val specialTeleport: SpecialSceneTeleportService,
    private val responseSender: tw.tsm.domain.interfaces.ResponseSender? = null,
) {
    private val activeRuns = ConcurrentHashMap<Long, ActiveDungeonRun>()

    fun getDef(id: Int): DungeonDef? = defs[id]

    fun getActiveRun(playerId: Long): ActiveDungeonRun? = activeRuns[playerId]

    fun markSingleStarted(session: GameSession, dungeonId: Int, startTime: Double) {
        activeRuns[session.playerId] = ActiveDungeonRun(
            dungeonId = dungeonId,
            startTime = startTime,
            instanceId = 0,
        )
    }

    fun markRoomStarted(room: DungeonRoomState, startTime: Double) {
        for (member in room.members.values) {
            activeRuns[member.session.playerId] = ActiveDungeonRun(
                dungeonId = room.dungeonId,
                startTime = startTime,
                instanceId = room.instanceId,
            )
        }
    }

    fun clearActiveRun(playerId: Long) {
        activeRuns.remove(playerId)
    }

    fun clearRoomRuns(room: DungeonRoomState) {
        for (member in room.members.values) {
            activeRuns.remove(member.session.playerId)
        }
    }

    suspend fun validateSingleCreate(session: GameSession, dungeonId: Int): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        if (session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (def.kind != KIND_SINGLE || def.maxPlayer != 1) return RESULT_NO_SPACE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        val countResult = validateDailyCount(session, def)
        if (countResult != RESULT_OK) return countResult

        if (session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        if (specialTeleport.deriveDungeonEntryPoint(def.sceneId) == null) return RESULT_NO_DUNGEON
        return RESULT_OK
    }

    suspend fun validateNormalRoomEntry(
        session: GameSession,
        dungeonId: Int,
        alreadyInRoom: Boolean,
    ): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        if (def.kind != KIND_NORMAL_MULTIPLAYER || def.maxPlayer <= 1) return RESULT_NO_SPACE
        if (session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
        if (alreadyInRoom || session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        return validateDailyCount(session, def)
    }

    suspend fun enterSingle(session: GameSession, dungeonId: Int): Boolean {
        val def = defs[dungeonId] ?: return false
        val point = specialTeleport.deriveDungeonEntryPoint(def.sceneId) ?: return false
        initializeMission(session, def)
        specialTeleport.teleport(session, def.sceneId, point.first, point.second)
        return true
    }

    suspend fun enterMultiplayer(room: DungeonRoomState): Boolean {
        val def = defs[room.dungeonId] ?: return false
        val point = specialTeleport.deriveDungeonEntryPoint(def.sceneId) ?: return false
        for (member in room.members.values) {
            initializeMission(member.session, def)
            specialTeleport.teleport(
                member.session,
                def.sceneId,
                point.first,
                point.second,
                room.instanceId,
            )
        }
        return true
    }

    private suspend fun initializeMission(session: GameSession, def: DungeonDef) {
        // Dungeon.CheckInDungeon + real Eve entry gates require this exact mission at step1.
        // Reset the active marker without granting its completion/skip flag or consuming quota.
        val charId = session.roleId.toLong()
        questSystem.setMissionStep(charId, def.missionFlag, 1)
        responseSender?.sendRemoveMission(session, def.missionFlag)
        responseSender?.sendAddMissionStep(session, def.missionFlag, 1)
    }

    private suspend fun validateDailyCount(session: GameSession, def: DungeonDef): Int {
        if (def.dailyFlag == 0 || def.dailyCount <= 0) return RESULT_OK
        for (missionId in questSystem.refreshDungeonDaily(session.roleId.toLong())) {
            responseSender?.sendRemoveMission(session, missionId)
        }
        val used = questSystem.getMissions(session.roleId.toLong())
            .firstOrNull { it.missionId == def.dailyFlag }
            ?.step ?: 0
        return if (used >= def.dailyCount) RESULT_COUNT_EXHAUSTED else RESULT_OK
    }

    companion object {
        const val KIND_NORMAL_MULTIPLAYER = 1
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

package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.data.AnotherWorldLvDef
import tw.tsm.domain.data.LimitTimeDungeonDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.ConcurrentHashMap

private val limitDungeonLogger = KotlinLogging.logger {}

/** Server timer cho UITeleport LimitFightArea / C:097-001. */
class LimitTimeDungeonSystem(
    private val defs: Map<Int, LimitTimeDungeonDef>,
    private val roleCountSystem: RoleCountSystem,
    private val responseSender: ResponseSender,
    private val specialTeleport: SpecialSceneTeleportService,
    private val scope: CoroutineScope,
    private val adminBypassPolicy: AdminBypassPolicy = AdminBypassPolicy(),
    private val questSystem: QuestSystem,
    private val anotherWorldLvDefs: Map<Int, AnotherWorldLvDef> = emptyMap(),
) {
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun getDef(index: Int): LimitTimeDungeonDef? = defs[index]

    /**
     * DVT UIMain.OnDropChange_AnotherWorldLv:
     * C097-002 contains one UInt8: selected id from IOS_AnotherWorldLv_C.dat.
     * For each rebirth turn the client lists 15 levels; id 4 means Lv55 for
     * turn0. MineData in scene49942 compares mission 13137 exactly with id.
     *
     * Only the currently authenticated character, in the matching scene and
     * rebirth group, can change its own stage; battle/active Eve events are
     * guarded by the packet handler before this method is reached.
     */
    suspend fun selectAnotherWorldLevel(session: GameSession, selectedId: Int): Boolean {
        if (session.roleId <= 0 || session.battleId != 0L) return false
        val def = defs.values.firstOrNull {
            it.sceneId == session.mapId && it.missionId > 0
        } ?: return false
        val selected = anotherWorldLvDefs[selectedId] ?: return false
        if (session.turn !in 0..3 || selected.turn != session.turn) return false
        if (!adminBypassPolicy.bypassEntryLimit(session) &&
            roleCountSystem.getValue(session.roleId.toLong(), def.limitIndex) >= def.limitTimeMinutes
        ) return false

        return try {
            val previous = questSystem.selectTrainingMissionStage(
                session.roleId.toLong(), def.missionId, selected.id
            )
            val delta = selected.id - previous
            when {
                delta > 0 -> responseSender.sendAddMissionStep(session, def.missionId, delta)
                delta < 0 -> responseSender.sendDecMissionStep(session, def.missionId, -delta)
            }
            limitDungeonLogger.info {
                "DVT training monster level: cid=${session.roleId} map=${def.sceneId} " +
                    "turn=${session.turn} level=${selected.level} mission=${def.missionId} " +
                    "step=$previous -> ${selected.id}"
            }
            true
        } catch (e: Exception) {
            limitDungeonLogger.error(e) {
                "Failed DVT training level change cid=${session.roleId} selected=$selectedId"
            }
            false
        }
    }

    /**
     * DVT scene 49942 has no static NPCs; its MineData fights require mission
     * 13137 step 1..60. C97 entrance previously ignored LimitTimeDungeonDef.missionId,
     * leaving fresh characters at step 0 where no fight condition can match.
     *
     * This is idempotent: reconnecting or entering again never resets existing
     * mission stages, and the client is informed using the normal S024-001 packet.
     */
    suspend fun ensureEncounterMission(session: GameSession, def: LimitTimeDungeonDef): Boolean {
        if (def.missionId <= 0) return true
        return try {
            val created = questSystem.ensureMissionStarted(session.roleId.toLong(), def.missionId)
            if (created) {
                responseSender.sendAddMissionStep(session, def.missionId, 1)
                limitDungeonLogger.info {
                    "Initialized DVT training encounter stage: cid=${session.roleId} " +
                        "scene=${def.sceneId} mission=${def.missionId} step=1"
                }
            }
            true
        } catch (e: Exception) {
            limitDungeonLogger.error(e) {
                "Cannot persist DVT training encounter stage cid=${session.roleId} scene=${def.sceneId}"
            }
            false
        }
    }

    /** Gọi sau C:012-001 để hỗ trợ cả vào mới lẫn relog/restart trong map giới hạn. */
    fun onSceneReady(session: GameSession) {
        val charId = session.roleId.toLong()
        stopTracking(charId)

        val def = defs.values.firstOrNull { it.sceneId == session.mapId } ?: return
        if (adminBypassPolicy.bypassEntryLimit(session)) {
            scope.launch { ensureEncounterMission(session, def) }
            return
        }
        val used = roleCountSystem.getValue(charId, def.limitIndex)
        if (used >= def.limitTimeMinutes) {
            scope.launch {
                specialTeleport.exitSpecialScene(session, def.sceneId)
            }
            return
        }

        val job = scope.launch {
            try {
                if (ensureEncounterMission(session, def)) track(session, def)
            } finally {
                val me = kotlin.coroutines.coroutineContext[Job]
                if (me != null) jobs.remove(charId, me)
            }
        }
        jobs[charId] = job
    }

    fun stopTracking(characterId: Long) {
        jobs.remove(characterId)?.cancel()
    }

    private suspend fun track(session: GameSession, def: LimitTimeDungeonDef) {
        val charId = session.roleId.toLong()
        while (session.state == SessionState.IN_GAME && session.mapId == def.sceneId) {
            delay(MINUTE_MS)
            if (session.state != SessionState.IN_GAME || session.mapId != def.sceneId) break

            val current = roleCountSystem.getValue(charId, def.limitIndex)
            if (current >= def.limitTimeMinutes) break

            val next = roleCountSystem.increment(charId, def.limitIndex, 1)
                .coerceAtMost(def.limitTimeMinutes)
            roleCountSystem.setValue(charId, def.limitIndex, next, def.limitTimeMinutes)
            responseSender.sendRoleCountUpdate(
                session,
                mapOf(def.limitIndex to (next to def.limitTimeMinutes)),
            )
            roleCountSystem.saveCounts(charId)

            if (next >= def.limitTimeMinutes) {
                specialTeleport.exitSpecialScene(session, def.sceneId)
                limitDungeonLogger.info {
                    session.characterName + " hết thời gian scene " + def.sceneId
                }
                break
            }
        }
    }

    companion object {
        const val MINUTE_MS = 60_000L
    }
}

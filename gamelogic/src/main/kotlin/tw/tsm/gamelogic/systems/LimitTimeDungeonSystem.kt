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
import tw.tsm.domain.interfaces.repositories.LimitedAreaDailyRepository
import java.time.Instant
import java.time.ZoneId
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
    /** Injectable monotonic-ish wall clock for deterministic expiry tests. */
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val minuteMillis: Long = MINUTE_MS,
    private val tickMillis: Long = 1_000L,
    private val dailyRepository: LimitedAreaDailyRepository? = null,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val jobSessions = ConcurrentHashMap<Long, GameSession>()

    fun getDef(index: Int): LimitTimeDungeonDef? = defs[index]

    /** Vietnamese calendar midnight; one entry is permanently claimed before teleport. */
    private fun dayKey(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(VIETNAM_TIME).toLocalDate().toEpochDay().toInt()

    /** C097-001 admission: one 120-minute visit per character per Vietnam calendar day.
     * An older pre-migration RoleCount 27 value counts as an already-used entry today.
     */
    suspend fun tryStartDailyVisit(session: GameSession, def: LimitTimeDungeonDef): Boolean {
        if (session.roleId <= 0) return false
        val charId = session.roleId.toLong()
        val used = roleCountSystem.getValue(charId, def.limitIndex)
        val repo = dailyRepository ?: return used < def.limitTimeMinutes // test-only legacy path
        return try {
            val accepted = repo.claim(
                charId, def.sceneId, dayKey(clockMillis()), clockMillis(),
                used, minuteMillis,
            )
            if (accepted) {
                roleCountSystem.setValue(charId, def.limitIndex, 0, def.limitTimeMinutes)
                roleCountSystem.saveCounts(charId)
                responseSender.sendRoleCountUpdate(
                    session, mapOf(def.limitIndex to (0 to def.limitTimeMinutes)),
                )
                limitDungeonLogger.info {
                    "DVT daily entry claimed: cid=$charId scene=${def.sceneId} day=${dayKey(clockMillis())}"
                }
            }
            accepted
        } catch (e: Exception) {
            limitDungeonLogger.error(e) { "Cannot persist daily otherworld admission cid=$charId" }
            false // fail closed: DB outage must never grant an extra daily entry
        }
    }

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

    /**
     * C012-001 can be sent repeatedly while the same character stays in a
     * limited-area scene. Re-arming a 60s interval on each packet resets the
     * last minute forever, so preserve the active job for the same session.
     *
     * A fresh session (relog) starts from the saved integer RoleCount minutes,
     * as the original DVT client does in Dungeon.StartLimitTimeDungeonTime.
     */
    fun onSceneReady(session: GameSession) {
        if (session.roleId <= 0) return
        val charId = session.roleId.toLong()
        val def = defs.values.firstOrNull { it.sceneId == session.mapId }
        if (def == null) {
            // A newly claimed entry may still be in the teleport handshake. Only
            // an already tracked timed scene can be closed as a voluntary exit.
            val wasTrackingInside = jobs[charId]?.isActive == true
            stopTracking(charId)
            // A voluntary scene exit consumes today's entire entry; reconnect while
            // still physically inside the map does NOT take this path.
            if (dailyRepository != null) scope.launch {
                for (dailyDef in defs.values) {
                    try {
                        if (session.mapId == dailyDef.sceneId ||
                            session.state != SessionState.IN_GAME
                        ) continue
                        val run = dailyRepository.find(charId, dailyDef.sceneId) ?: continue
                        if (wasTrackingInside && !run.closed) {
                            dailyRepository.close(charId, dailyDef.sceneId)
                        }
                        val minutes = when {
                            run.dayKey < dayKey(clockMillis()) -> 0 // new calendar day
                            wasTrackingInside || run.closed -> dailyDef.limitTimeMinutes
                            else -> continue // do not consume a pending C097 teleport
                        }
                        if (roleCountSystem.getValue(charId, dailyDef.limitIndex) != minutes) {
                            roleCountSystem.setValue(
                                charId, dailyDef.limitIndex, minutes, dailyDef.limitTimeMinutes,
                            )
                            roleCountSystem.saveCounts(charId)
                            responseSender.sendRoleCountUpdate(
                                session, mapOf(dailyDef.limitIndex to (minutes to dailyDef.limitTimeMinutes)),
                            )
                        }
                    } catch (e: Exception) {
                        limitDungeonLogger.error(e) { "Cannot reconcile daily visit cid=$charId" }
                    }
                }
            }
            return
        }
        if (dailyRepository == null && adminBypassPolicy.bypassEntryLimit(session)) {
            stopTracking(charId)
            scope.launch { ensureEncounterMission(session, def) }
            return
        }
        val old = jobs[charId]
        if (old?.isActive == true && jobSessions[charId] === session) return
        stopTracking(charId)

        jobSessions[charId] = session
        val job = scope.launch {
            try {
                val run = dailyRepository?.let { repo ->
                    repo.find(charId, def.sceneId) ?: repo.recover(
                        charId, def.sceneId, dayKey(clockMillis()), clockMillis(),
                        roleCountSystem.getValue(charId, def.limitIndex), minuteMillis,
                    )
                }
                if (run?.closed == true ||
                    roleCountSystem.getValue(charId, def.limitIndex) >= def.limitTimeMinutes
                ) {
                    exitExpired(session, def)
                } else if (ensureEncounterMission(session, def)) {
                    track(session, def, run?.startedAtMillis)
                }
            } finally {
                val me = kotlin.coroutines.coroutineContext[Job]
                if (me != null) jobs.remove(charId, me)
                jobSessions.remove(charId, session)
            }
        }
        jobs[charId] = job
    }

    fun stopTracking(characterId: Long) {
        jobs.remove(characterId)?.cancel()
        jobSessions.remove(characterId)
    }

    /**
     * DVT RoleCount index27 is an integer number of consumed training minutes.
     * Sample the elapsed clock each second rather than assuming coroutine
     * delay(MINUTE_MS) returns on the exact deadline. Both the count update and
     * kick must still happen if the coroutine was suspended for extra time.
     */
    private suspend fun track(session: GameSession, def: LimitTimeDungeonDef, startedAtMillis: Long?) {
        require(minuteMillis > 0 && tickMillis > 0)
        val charId = session.roleId.toLong()
        val startUsed = if (startedAtMillis == null) roleCountSystem.getValue(charId, def.limitIndex) else 0
        val started = startedAtMillis ?: clockMillis()
        while (session.state == SessionState.IN_GAME && session.mapId == def.sceneId) {
            val elapsed = (clockMillis() - started).coerceAtLeast(0L)
            val consumed = elapsed / minuteMillis
            val expected = (startUsed.toLong() + consumed)
                .coerceAtMost(def.limitTimeMinutes.toLong()).toInt()
            val recorded = roleCountSystem.getValue(charId, def.limitIndex)
            if (expected > recorded) {
                roleCountSystem.setValue(charId, def.limitIndex, expected, def.limitTimeMinutes)
                responseSender.sendRoleCountUpdate(
                    session,
                    mapOf(def.limitIndex to (expected to def.limitTimeMinutes)),
                )
                roleCountSystem.saveCounts(charId)
            }
            if (maxOf(expected, recorded) >= def.limitTimeMinutes) {
                exitExpired(session, def)
                return
            }
            delay(tickMillis)
        }
    }

    /** Only the authoritative server teleports players; client timer only hides itself.
     * Never send a warp during a battle. Retry failures on the same scene instead
     * of silently leaving a character trapped at 0 minutes forever.
     */
    private suspend fun exitExpired(session: GameSession, def: LimitTimeDungeonDef) {
        while (session.state == SessionState.IN_GAME && session.mapId == def.sceneId) {
            if (session.battleId == 0L) {
                try {
                    if (specialTeleport.exitSpecialScene(session, def.sceneId)) {
                        limitDungeonLogger.info {
                            session.characterName + " hết thời gian scene " + def.sceneId +
                                ", đã dịch chuyển ra ngoài"
                        }
                        return
                    }
                    limitDungeonLogger.error {
                        "Không thể xác định cửa thoát map giới hạn " + def.sceneId +
                            " cid=" + session.roleId + "; sẽ thử lại"
                    }
                } catch (e: Exception) {
                    limitDungeonLogger.error(e) {
                        "Lỗi dịch chuyển khỏi scene giới hạn " + def.sceneId +
                            " cid=" + session.roleId + "; sẽ thử lại"
                    }
                }
            }
            delay(tickMillis)
        }
    }

    companion object {
        const val MINUTE_MS = 60_000L
        private val VIETNAM_TIME: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}

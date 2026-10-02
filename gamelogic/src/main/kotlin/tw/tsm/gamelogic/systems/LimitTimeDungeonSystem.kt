package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
) {
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun getDef(index: Int): LimitTimeDungeonDef? = defs[index]

    /** Gọi sau C:012-001 để hỗ trợ cả vào mới lẫn relog/restart trong map giới hạn. */
    fun onSceneReady(session: GameSession) {
        val charId = session.roleId.toLong()
        stopTracking(charId)

        val def = defs.values.firstOrNull { it.sceneId == session.mapId } ?: return
        val used = roleCountSystem.getValue(charId, def.limitIndex)
        if (used >= def.limitTimeMinutes) {
            scope.launch {
                specialTeleport.exitSpecialScene(session, def.sceneId)
            }
            return
        }

        val job = scope.launch {
            try {
                track(session, def)
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

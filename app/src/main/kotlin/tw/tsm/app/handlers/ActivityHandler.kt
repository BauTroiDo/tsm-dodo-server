package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.ActivityScheduleSystem
import tw.tsm.gamelogic.systems.AdminBypassPolicy
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter
import java.time.LocalDateTime

private val activityLogger = KotlinLogging.logger {}

class ActivityHandler(
    private val scheduleSystem: ActivityScheduleSystem,
    private val adminBypassPolicy: AdminBypassPolicy,
    private val specialTeleport: SpecialSceneTeleportService,
    private val activityTimes: Map<Int, ActivityTimeDef>,
    private val eveData: Map<Int, SceneEveData>,
) : PacketHandler {
    override val mainKind: Int = Opcodes.ACTIVITY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            3 -> handleEnter(session, data)
            else -> activityLogger.warn { "Activity chưa hỗ trợ subKind=$subKind (${data.size} bytes)" }
        }
    }

    private suspend fun handleEnter(session: GameSession, data: ByteArray) {
        if (data.size != 2) return
        val activityId = PacketReader(data).readUShortLE()
        if (activityId != ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID) {
            activityLogger.warn { "C77-003 activityId=$activityId chưa hỗ trợ" }
            return
        }
        if (session.battleId != 0L) return

        val def = activityTimes[activityId] ?: return
        val bypassSchedule = adminBypassPolicy.bypassSchedule(session)
        val state = scheduleSystem.calculateState(def, LocalDateTime.now(), bypassSchedule)
        if (state != ActivityScheduleSystem.STATE_STARTED) {
            sendJudgmentMessage(session, 1)
            return
        }
        if (!adminBypassPolicy.bypassLevel(session) && session.level < JUDGMENT_TOWER_MIN_LEVEL) return

        val entry = deriveJudgmentTowerEntry(eveData) ?: run {
            activityLogger.error { "Không tìm được Eve reverse-link vào Tháp thí luyện 12921" }
            return
        }
        specialTeleport.teleport(session, JUDGMENT_TOWER_SCENE_ID, entry.first, entry.second)
        activityLogger.info {
            session.characterName + " vào Giải cứu Nhị Kiều activityId=" + activityId +
                " gmLevel=" + session.gmLevel + " scene=" + JUDGMENT_TOWER_SCENE_ID +
                " (" + entry.first + "," + entry.second + ")"
        }
    }

    private fun sendJudgmentMessage(session: GameSession, kind: Int) {
        session.send(PacketWriter().writeByte(Opcodes.NPC_EVENT).writeByte(41).writeByte(0).writeByte(kind).toByteArray())
    }

    companion object {
        const val JUDGMENT_TOWER_SCENE_ID = 12921
        const val JUDGMENT_TOWER_MIN_LEVEL = 15
        private const val GRID = 20

        fun deriveJudgmentTowerEntry(eveData: Map<Int, SceneEveData>): Pair<Int, Int>? {
            val inbound = eveData.entries.asSequence()
                .filter { (sceneId, _) -> sceneId !in JUDGMENT_TOWER_SCENE_ID..12999 }
                .flatMap { (_, scene) -> scene.sceneInfos.values.asSequence() }
                .firstOrNull { it.backgroundNo == JUDGMENT_TOWER_SCENE_ID }
                ?: return null
            return inbound.playerAppearX * GRID to inbound.playerAppearY * GRID
        }
    }
}

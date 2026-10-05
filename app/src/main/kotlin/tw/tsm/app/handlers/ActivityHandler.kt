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

    private val activityNavigationEntries: Map<Int, ActivityNavigationEntry> by lazy {
        deriveActivityNavigationEntries(eveData)
    }

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
        if (session.battleId != 0L) return

        val def = activityTimes[activityId] ?: run {
            activityLogger.warn { "C77-003 activityId=$activityId không có ActivityTime" }
            return
        }
        val bypassSchedule = adminBypassPolicy.bypassSchedule(session)
        val state = scheduleSystem.calculateState(def, LocalDateTime.now(), bypassSchedule)
        if (state != ActivityScheduleSystem.STATE_STARTED) {
            if (activityId == ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID) {
                sendJudgmentMessage(session, 1)
            }
            return
        }
        if (!adminBypassPolicy.bypassLevel(session) && session.level < ACTIVITY_MIN_LEVEL) return

        if (activityId == ActivityScheduleSystem.JUDGMENT_TOWER_ACTIVITY_ID) {
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
            return
        }

        val navigation = activityNavigationEntries[activityId] ?: run {
            activityLogger.warn { "C77-003 activityId=$activityId chưa khóa được entry authoritative" }
            return
        }
        sendActivityNavigation(session, navigation)
        activityLogger.info {
            session.characterName + " điều hướng vào activityId=" + activityId +
                " gmLevel=" + session.gmLevel + " scene=" + navigation.sceneId +
                " npcId=" + navigation.npcId + " (" + navigation.x + "," + navigation.y + ")"
        }
    }

    private fun sendActivityNavigation(session: GameSession, entry: ActivityNavigationEntry) {
        session.send(
            navigationPacket(entry)
        )
    }

    private fun sendJudgmentMessage(session: GameSession, kind: Int) {
        session.send(PacketWriter().writeByte(Opcodes.NPC_EVENT).writeByte(41).writeByte(0).writeByte(kind).toByteArray())
    }

    data class ActivityNavigationEntry(
        val sceneId: Int,
        val npcId: Int,
        val x: Int,
        val y: Int,
    )

    companion object {
        const val JUDGMENT_TOWER_SCENE_ID = 12921
        const val ACTIVITY_MIN_LEVEL = 15

        const val MIX_VS_ACTIVITY_ID = 2
        const val CHAOS_VS_ACTIVITY_ID = 3
        const val DARE_NPC_ACTIVITY_ID = 4
        const val WORLD_BOSS_ACTIVITY_ID = 5

        private const val MIX_VS_ENTRY_NPC_ID = 15163
        private const val CHAOS_VS_ENTRY_NPC_ID = 15226
        private const val DARE_NPC_ENTRY_NPC_ID = 15244
        private const val WORLD_BOSS_ENTRY_NPC_ID = 38177
        private const val GRID = 20

        fun deriveJudgmentTowerEntry(eveData: Map<Int, SceneEveData>): Pair<Int, Int>? {
            val inbound = eveData.entries.asSequence()
                .filter { (sceneId, _) -> sceneId !in JUDGMENT_TOWER_SCENE_ID..12999 }
                .flatMap { (_, scene) -> scene.sceneInfos.values.asSequence() }
                .firstOrNull { it.backgroundNo == JUDGMENT_TOWER_SCENE_ID }
                ?: return null
            return inbound.playerAppearX * GRID to inbound.playerAppearY * GRID
        }

        /**
         * DVT UIActivity does not receive a direct teleport for arena activities.
         * S:077-003 result=1 tells MarkManager to navigate to the authoritative entry NPC.
         *
         * Arena IDs 2/3/4 share the public arena scene that contains all three entry NPCs.
         * Requiring the trio avoids accidentally choosing old quest/copy placements of NPC 15163.
         */
        fun deriveActivityNavigationEntries(
            eveData: Map<Int, SceneEveData>
        ): Map<Int, ActivityNavigationEntry> {
            val arenaNpcIds = setOf(
                MIX_VS_ENTRY_NPC_ID,
                CHAOS_VS_ENTRY_NPC_ID,
                DARE_NPC_ENTRY_NPC_ID,
            )
            val arena = eveData.entries.firstOrNull { (_, scene) ->
                val present = scene.npcs.values.map { it.npcId }.toSet()
                arenaNpcIds.all { it in present }
            }

            val result = linkedMapOf<Int, ActivityNavigationEntry>()
            if (arena != null) {
                val (sceneId, scene) = arena
                fun add(activityId: Int, npcId: Int) {
                    val npc = scene.npcs.values.firstOrNull { it.npcId == npcId } ?: return
                    result[activityId] = ActivityNavigationEntry(sceneId, npcId, npc.x, npc.y)
                }
                add(MIX_VS_ACTIVITY_ID, MIX_VS_ENTRY_NPC_ID)
                add(CHAOS_VS_ACTIVITY_ID, CHAOS_VS_ENTRY_NPC_ID)
                add(DARE_NPC_ACTIVITY_ID, DARE_NPC_ENTRY_NPC_ID)
            }

            val worldBossPlacements = eveData.entries.asSequence()
                .flatMap { (sceneId, scene) ->
                    scene.npcs.values.asSequence()
                        .filter { it.npcId == WORLD_BOSS_ENTRY_NPC_ID }
                        .map { sceneId to it }
                }
                .toList()
            if (worldBossPlacements.size == 1) {
                val (sceneId, npc) = worldBossPlacements.single()
                result[WORLD_BOSS_ACTIVITY_ID] = ActivityNavigationEntry(
                    sceneId, WORLD_BOSS_ENTRY_NPC_ID, npc.x, npc.y
                )
            }
            return result
        }

        fun navigationPacket(entry: ActivityNavigationEntry): ByteArray =
            PacketWriter()
                .writeByte(Opcodes.ACTIVITY).writeByte(3).writeByte(0)
                .writeByte(1)
                .writeShortLE(entry.sceneId)
                .writeShortLE(entry.x)
                .writeShortLE(entry.y)
                .toByteArray()
    }
}

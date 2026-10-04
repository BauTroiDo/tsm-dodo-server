package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveSceneInfo
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.data.SceneSetDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository

private val specialSceneLogger = KotlinLogging.logger {}

/**
 * Chuyển vào các scene đặc biệt không có inbound SceneInfo trong Eve.emg.
 *
 * DVT lưu chính xác scene đích nhưng không lưu điểm spawn server-side cho C97/C170.
 * Vì vậy entry point là offline fallback suy từ hình học Eve thật, không phải contract gốc:
 * ưu tiên tâm cụm NPC (tọa độ chắc chắn hợp lệ), nếu không có NPC thì lệch 4 ô khỏi Door thoát.
 */
class SpecialSceneTeleportService(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val eveData: Map<Int, SceneEveData>,
    private val walkGrids: Map<Int, tw.tsm.domain.data.MapWalkGrid> = emptyMap(),
    private val sceneSetDefs: Map<Int, SceneSetDef> = emptyMap(),
) {
    /** Dungeon entry fallback: start beside the first Eve door, on a real walkable Ground cell.
     * A centroid may be inside a wall or a disconnected room. Never use it for dungeons.
     * Original backend C47 spawn is not present in the client; this policy is explicit.
     */
    fun deriveDungeonEntryPoint(sceneId: Int): Pair<Int, Int>? {
        val scene = eveData[sceneId] ?: return null
        val grid = walkGrids[sceneId] ?: return null
        val door = scene.doors.toSortedMap().values.firstOrNull() ?: return null
        return grid.nearestWalkable(door.x + DOOR_OFFSET, door.y + DOOR_OFFSET)
    }

    fun deriveEntryPoint(sceneId: Int): Pair<Int, Int>? {
        val scene = eveData[sceneId] ?: return null
        val npcs = scene.npcs.values
        if (npcs.isNotEmpty()) {
            val x = npcs.sumOf { it.x } / npcs.size
            val y = npcs.sumOf { it.y } / npcs.size
            return snap(x) to snap(y)
        }

        val door = scene.doors.toSortedMap().values.firstOrNull() ?: return null
        return snap(door.x + DOOR_OFFSET) to snap(door.y + DOOR_OFFSET)
    }

    suspend fun enterSpecialScene(session: GameSession, sceneId: Int, instanceId: Int = 0): Boolean {
        val (x, y) = deriveEntryPoint(sceneId) ?: return false
        teleport(session, sceneId, x, y, instanceId)
        specialSceneLogger.info {
            session.characterName + " vào scene đặc biệt " + sceneId +
                " tại fallback Eve (" + x + "," + y + ")"
        }
        return true
    }

    /**
     * Resolve a recovery/timeout exit from authoritative Eve door data.
     * Prefer a Door-linked resultType=2 whose target SceneSet is not a dynamic
     * dungeon (kind=2). This avoids treating an arbitrary SceneInfo or an
     * internal progression door as the dungeon exit. If no such Door-linked
     * target can be proven, fall back to the first SceneInfo as before.
     */
    fun deriveSpecialSceneExit(sceneId: Int): EveSceneInfo? {
        val scene = eveData[sceneId] ?: return null
        val doorLinked = scene.doors.toSortedMap().values
            .asSequence()
            .flatMap { door -> door.events.asSequence() }
            .mapNotNull { eveNo -> scene.npcEvents[eveNo] }
            .flatMap { event -> event.conditions.asSequence() }
            .flatMap { condition -> condition.results.asSequence() }
            .filter { it.resultType == DOOR_RESULT_TYPE }
            .mapNotNull { result -> scene.sceneInfos[result.parameter] }
            .distinctBy { it.eveNo }
            .toList()

        return doorLinked.firstOrNull { sceneSetDefs[it.backgroundNo]?.kind != DYNAMIC_DUNGEON_KIND }
            ?: doorLinked.firstOrNull()
            ?: scene.sceneInfos.toSortedMap().values.firstOrNull()
    }

    suspend fun exitSpecialScene(session: GameSession, sceneId: Int): Boolean {
        val sceneInfo = deriveSpecialSceneExit(sceneId)
        if (sceneInfo != null) {
            // EveDoorWarpService uses SceneInfo coordinates in tile units and converts
            // them to world pixels. Recovery leave must follow the same contract.
            teleport(
                session,
                sceneInfo.backgroundNo,
                sceneInfo.playerAppearX * GRID,
                sceneInfo.playerAppearY * GRID,
            )
            return true
        }

        // Legacy rescue only: some DVT dungeon scenes (notably 62003) contain no
        // SceneInfo/resultType=2 exit at all. A pre-persistence run has no captured
        // origin, so use the character's own durable record point rather than a
        // hard-coded map. New runs never need this path because origin is persisted.
        val recordPoint = characterRepository.getRecordPoint(session.roleId.toLong()) ?: return false
        val (recordMapId, recordX, recordY) = recordPoint
        if (
            recordMapId <= 0 ||
            recordMapId == sceneId ||
            sceneSetDefs[recordMapId]?.kind == DYNAMIC_DUNGEON_KIND
        ) {
            return false
        }

        specialSceneLogger.warn {
            session.characterName + " recovery rời scene=" + sceneId +
                " qua điểm ghi nhớ " + recordMapId + " (" + recordX + "," + recordY + ")"
        }
        teleport(session, recordMapId, recordX, recordY)
        return true
    }

    suspend fun teleport(session: GameSession, sceneId: Int, x: Int, y: Int, instanceId: Int = 0) {
        val charId = session.roleId.toLong()
        worldManager.leaveMap(session)
        worldManager.enterMap(session, sceneId, x, y, instanceId)
        characterRepository.updatePosition(charId, sceneId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, sceneId, x, y)
        responseSender.sendSceneChange(
            session, session.playerId, sceneId, x, y,
            session.equipItemIds, session.titleId, instanceId
        )
    }

    private fun snap(value: Int): Int = (value / GRID) * GRID

    companion object {
        private const val GRID = 20
        private const val DOOR_OFFSET = GRID * 4
        private const val DOOR_RESULT_TYPE = 2
        private const val DYNAMIC_DUNGEON_KIND = 2
    }
}

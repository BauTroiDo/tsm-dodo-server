package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.SceneEveData
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
) {
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

    suspend fun enterSpecialScene(session: GameSession, sceneId: Int): Boolean {
        val (x, y) = deriveEntryPoint(sceneId) ?: return false
        teleport(session, sceneId, x, y)
        specialSceneLogger.info {
            session.characterName + " vào scene đặc biệt " + sceneId +
                " tại fallback Eve (" + x + "," + y + ")"
        }
        return true
    }

    suspend fun exitSpecialScene(session: GameSession, sceneId: Int): Boolean {        val sceneInfo = eveData[sceneId]?.sceneInfos?.toSortedMap()?.values?.firstOrNull()
            ?: return false
        teleport(
            session,
            sceneInfo.backgroundNo,
            sceneInfo.playerAppearX,
            sceneInfo.playerAppearY,
        )
        return true
    }

    suspend fun teleport(session: GameSession, sceneId: Int, x: Int, y: Int) {
        val charId = session.roleId.toLong()
        worldManager.leaveMap(session)
        worldManager.enterMap(session, sceneId, x, y)
        characterRepository.updatePosition(charId, sceneId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, sceneId, x, y)
        responseSender.sendSceneChange(
            session, session.playerId, sceneId, x, y,
            session.equipItemIds, session.titleId
        )
    }

    private fun snap(value: Int): Int = (value / GRID) * GRID

    companion object {
        private const val GRID = 20
        private const val DOOR_OFFSET = GRID * 4
    }
}

package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.data.MapWalkGrid
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * World Atlas custom UI (mainKind=250).
 *
 * C:250-001 query GM capability.
 * S:250-001 isGm(1B).
 *
 * C:250-002 sceneId(U16 LE) + x(U16 LE) + y(U16 LE).
 * S:250-002 result(1B): 0=success, 1=not GM, 2=invalid target, 3=invalid state.
 *
 * Security: server never trusts the client-side GM flag.
 */
class WorldAtlasHandler(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val mapWalkGrids: Map<Int, MapWalkGrid>,
) : PacketHandler {

    override val mainKind: Int = Opcodes.WORLD_ATLAS

    private val worldMapSizes = mapOf(
        11000 to (3136 to 1920),
        12000 to (2688 to 3264),
        63000 to (2688 to 3264),
        13000 to (2432 to 1920),
        14000 to (1920 to 2688),
        15000 to (3264 to 2624),
        16000 to (2240 to 2560),
        17000 to (2496 to 1920),
        18000 to (3776 to 3200),
        19000 to (2560 to 2816),
        20000 to (1600 to 2944),
        21000 to (2624 to 1664),
        23000 to (2496 to 3072),
        24000 to (4992 to 4928),
        25000 to (3328 to 3136),
        26000 to (4928 to 3712),
        27000 to (4032 to 1984),
        28000 to (4416 to 3072),
        29000 to (4928 to 3072),
        30000 to (4672 to 3712),
        31000 to (3520 to 3264),
        32000 to (3968 to 2752),
    )

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            1 -> sendGmCapability(session)
            2 -> handleTeleport(session, data)
            else -> logger.warn { "WorldAtlas unknown subKind=" + subKind }
        }
    }

    private fun sendGmCapability(session: GameSession) {
        val isGm = session.state == SessionState.IN_GAME && session.gmLevel > 0
        val writer = PacketWriter(4)
            .writeByte(Opcodes.WORLD_ATLAS)
            .writeByte(1)
            .writeByte(0)
            .writeByte(if (isGm) 1 else 0)
        session.send(writer.toByteArray())
    }

    private suspend fun handleTeleport(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || session.battleId != 0L) {
            sendTeleportResult(session, 3)
            return
        }

        if (session.gmLevel <= 0) {
            logger.warn { "WorldAtlas teleport rejected for non-GM " + session.characterName }
            sendTeleportResult(session, 1)
            return
        }

        if (data.size < 6) {
            sendTeleportResult(session, 2)
            return
        }

        val reader = PacketReader(data)
        val sceneId = reader.readUShortLE()
        val x = reader.readUShortLE()
        val y = reader.readUShortLE()
        val walkGrid = mapWalkGrids[sceneId]
        if (walkGrid == null) {
            logger.warn {
                "WorldAtlas missing walk-grid for fixed GM target " + session.characterName +
                    ": scene=" + sceneId + " x=" + x + " y=" + y
            }
            sendTeleportResult(session, 2)
            return
        }

        val maxX = walkGrid.width * 20
        val maxY = walkGrid.height * 20
        if (x >= maxX || y >= maxY) {
            logger.warn {
                "WorldAtlas invalid fixed GM target " + session.characterName +
                    ": scene=" + sceneId + " x=" + x + " y=" + y +
                    " walkSize=(" + maxX + "," + maxY + ")"
            }
            sendTeleportResult(session, 2)
            return
        }

        val safeTarget = walkGrid.nearestWalkable(x, y)
        if (safeTarget == null) {
            logger.warn {
                "WorldAtlas no walkable GM target " + session.characterName +
                    ": scene=" + sceneId + " x=" + x + " y=" + y
            }
            sendTeleportResult(session, 2)
            return
        }
        val (safeX, safeY) = safeTarget

        val charId = session.roleId.toLong()
        worldManager.leaveMap(session)
        worldManager.enterMap(session, sceneId, safeX, safeY)
        characterRepository.updatePosition(charId, sceneId, safeX, safeY)

        responseSender.sendPlayerTeleport(session, session.playerId, sceneId, safeX, safeY)
        responseSender.sendSceneChange(
            session,
            session.playerId,
            sceneId,
            safeX,
            safeY,
            session.equipItemIds,
            session.titleId,
        )

        sendTeleportResult(session, 0)
        logger.info {
            "WorldAtlas GM teleport " + session.characterName +
                ": scene=" + sceneId + " requested=(" + x + "," + y + ")" +
                " safe=(" + safeX + "," + safeY + ")"
        }
    }

    private fun sendTeleportResult(session: GameSession, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(Opcodes.WORLD_ATLAS)
            .writeByte(2)
            .writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }
}

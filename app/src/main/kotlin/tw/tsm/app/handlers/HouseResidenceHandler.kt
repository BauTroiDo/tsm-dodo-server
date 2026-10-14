package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.HouseVisitSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val houseLogger = KotlinLogging.logger {}

/**
 * DVT protocol 106, phase 1: enter/leave own starter house and read-only metadata.
 * Never grant items, deduct gold, install warriors or accept furniture writes until
 * the owner inventory + durable house persistence model is implemented.
 */
class HouseResidenceHandler(
    private val visits: HouseVisitSystem,
    private val world: WorldManager,
    private val response: ResponseSender,
    private val characters: CharacterRepository,
    private val parties: PartySystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOUSE_RESIDENCE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> sendHouseData(session, data)
            2 -> enterHouse(session, data)
            7 -> if (data.isEmpty()) HouseResidencePackets.starterScore(session)
            8 -> if (data.isEmpty()) HouseResidencePackets.emptyFurnitureBag(session)
            12 -> leaveHouse(session, data)
            13 -> {
                val own = data.size == 8 &&
                    PacketReader(data).readULongLE() == session.playerId
                HouseResidencePackets.totalLevel(session, if (own) 1 else 0)
            }
            3, 4, 5, 6, 11 -> {
                // Explicit failure instead of silent packet drops or phantom payment.
                HouseResidencePackets.result(session, subKind, if (subKind == 11) 0 else 2)
                houseLogger.info { "Gia Vien write disabled: charId=${session.roleId} request=$subKind" }
            }
            else -> houseLogger.warn {
                "Gia Vien operation pending: charId=${session.roleId} kind=$subKind bytes=${data.size}"
            }
        }
    }

    private fun sendHouseData(session: GameSession, data: ByteArray) {
        if (data.size != 10) return
        val r = PacketReader(data)
        val owner = r.readULongLE()
        val building = r.readUByte()
        val floor = r.readUByte()
        if (owner != session.playerId ||
            building != HouseVisitSystem.STARTER_BUILDING ||
            floor != HouseVisitSystem.STARTER_FLOOR ||
            !visits.isInsideOwnHouse(session)
        ) return
        HouseResidencePackets.starterRoom(session, owner, building, floor)
    }

    private suspend fun enterHouse(session: GameSession, data: ByteArray) {
        if (data.size != 10) {
            HouseResidencePackets.entryResult(session, 2, 1, 1)
            return
        }
        val r = PacketReader(data)
        val owner = r.readULongLE()
        val building = r.readUByte()
        val floor = r.readUByte()
        if (owner != session.playerId ||
            building != HouseVisitSystem.STARTER_BUILDING ||
            floor != HouseVisitSystem.STARTER_FLOOR ||
            session.battleId != 0L
        ) {
            HouseResidencePackets.entryResult(session, 2, building, floor)
            return
        }

        if (parties.isInParty(session.playerId)) {
            HouseResidencePackets.entryResult(session, 10, building, floor)
            return
        }
        if (visits.isInsideOwnHouse(session)) {
            HouseResidencePackets.entryResult(session, 1, building, floor)
            return
        }
        val visit = visits.beginOwnHouse(session)
        if (visit == null) {
            HouseResidencePackets.entryResult(session, if (session.instanceId != 0) 9 else 2, building, floor)
            return
        }
        try {
            // Secure outdoor respawn on crash, relog or regular disconnect.
            characters.updatePosition(session.roleId.toLong(),
                visit.origin.mapId, visit.origin.x, visit.origin.y)
            HouseResidencePackets.entryResult(session, 1, building, floor)
            world.leaveMap(session)
            world.enterMap(session, HouseVisitSystem.STARTER_MAP,
                HouseVisitSystem.ENTRY_X, HouseVisitSystem.ENTRY_Y, visit.roomId)
            response.sendPlayerTeleport(session, session.playerId, session.mapId, session.x, session.y)
            response.sendSceneChange(session, session.playerId, session.mapId, session.x, session.y,
                session.equipItemIds, session.titleId, session.instanceId)
            houseLogger.info {
                "Gia Vien entry charId=${session.roleId} owner=$owner room=${visit.roomId} returnMap=${visit.origin.mapId}"
            }
        } catch (e: Exception) {
            houseLogger.error(e) { "Gia Vien entry failed charId=${session.roleId}" }
            visits.cleanup(session.playerId)
            world.leaveMap(session)
            world.enterMap(session, visit.origin.mapId, visit.origin.x, visit.origin.y,
                visit.origin.instanceId)
            HouseResidencePackets.entryResult(session, 2, building, floor)
        }
    }

    private suspend fun leaveHouse(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty()) {
            HouseResidencePackets.result(session, 12, 0)
            return
        }
        if (!visits.isInsideOwnHouse(session)) {
            HouseResidencePackets.result(session, 12, 0)
            return
        }
        val origin = visits.getVisit(session.playerId)?.origin ?: return
        // Keep the visit fence active during DB save and map transition so a
        // concurrent autosave cannot persist the transient home map.
        characters.updatePosition(session.roleId.toLong(), origin.mapId, origin.x, origin.y)
        HouseResidencePackets.result(session, 12, 1)
        world.leaveMap(session)
        world.enterMap(session, origin.mapId, origin.x, origin.y, origin.instanceId)
        visits.cleanup(session.playerId)
        response.sendPlayerTeleport(session, session.playerId, origin.mapId, origin.x, origin.y)
        response.sendSceneChange(session, session.playerId, origin.mapId, origin.x, origin.y,
            session.equipItemIds, session.titleId, origin.instanceId)
        houseLogger.info { "Gia Vien exit charId=${session.roleId} map=${origin.mapId}" }
    }
}

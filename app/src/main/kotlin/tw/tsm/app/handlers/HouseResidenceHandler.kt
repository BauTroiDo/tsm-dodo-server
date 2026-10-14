package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.HouseResidenceRepository
import tw.tsm.domain.interfaces.repositories.HouseResidenceSettings
import tw.tsm.domain.interfaces.repositories.HouseFurnitureRepository
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.HouseFurnitureDef
import tw.tsm.domain.interfaces.repositories.HouseWarriorRepository
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.InventorySystem
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
    private val houseSettings: HouseResidenceRepository,
    private val houseFurniture: HouseFurnitureRepository,
    private val inventory: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val houseWarriors: HouseWarriorRepository,
    private val pets: PetSystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val furnitureDefs: Map<Int, HouseFurnitureDef>,
    private val warriorThresholds: Map<Int, Int>,
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOUSE_RESIDENCE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> sendHouseData(session, data)
            3 -> assignHouseWarriors(session, data)
            7 -> if (data.isEmpty()) sendFloorScore(session)
            2 -> enterHouse(session, data)
            5 -> updateHouseSettings(session, data)
            6 -> saveFurniture(session, data)
            8 -> if (data.isEmpty()) sendFurnitureBag(session)
            12 -> leaveHouse(session, data)
            13 -> {
                val own = data.size == 8 &&
                    PacketReader(data).readULongLE() == session.playerId
                HouseResidencePackets.totalLevel(session, if (own) 1 else 0)
            }
            4, 11 -> {
                // Explicit failure instead of silent packet drops or phantom payment.
                HouseResidencePackets.result(session, subKind, if (subKind == 11) 0 else 2)
                houseLogger.info { "Gia Vien write disabled: charId=${session.roleId} request=$subKind" }
            }
            else -> houseLogger.warn {
                "Gia Vien operation pending: charId=${session.roleId} kind=$subKind bytes=${data.size}"
            }
        }
    }

    private suspend fun sendHouseData(session: GameSession, data: ByteArray) {
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
        val settings = houseSettings.load(session.roleId.toLong())
        val placed = HouseFurniturePolicy.visible(
            houseFurniture.load(session.roleId.toLong(), building, floor), ownedFurniture(session)
        )
        val score = HouseWarriorPolicy.floorScore(placed, furnitureDefs)
        val max = HouseWarriorPolicy.slots(score, warriorThresholds)
        val visible = HouseWarriorPolicy.visible(
            houseWarriors.load(session.roleId.toLong(), building, floor),
            max, eligibleWarriors(session)
        )
        HouseResidencePackets.starterRoom(session, owner, building, floor, settings, placed, visible)
    }

    private fun eligibleWarriors(session: GameSession): Set<Int> {
        val charId = session.roleId.toLong()
        val eligible = ArrayList<tw.tsm.domain.valueobjects.FollowNpcData>()
        eligible.addAll(pets.getFollowPets(charId).values.filter { it.canGrow != 0 && it.canGrow != 2 })
        eligible.addAll(pets.getCartPets(charId).values)
        eligible.addAll(pets.getInnPets(charId).values)
        return eligible.asSequence()
            .filter { it.level >= 120 && (npcDefs[it.npcId]?.turn ?: 0) >= 2 }
            .map { it.npcId }.toSet()
    }

    private suspend fun currentFloorData(session: GameSession): Pair<Int, List<Int>> {
        val charId = session.roleId.toLong()
        val placed = HouseFurniturePolicy.visible(
            houseFurniture.load(charId, 1, 1), ownedFurniture(session)
        )
        val score = HouseWarriorPolicy.floorScore(placed, furnitureDefs)
        val capacity = HouseWarriorPolicy.slots(score, warriorThresholds)
        val visible = HouseWarriorPolicy.visible(
            houseWarriors.load(charId, 1, 1), capacity, eligibleWarriors(session)
        )
        return score to visible
    }

    private suspend fun sendFloorScore(session: GameSession) {
        if (!visits.isInsideOwnHouse(session)) return
        val (score, warriors) = currentFloorData(session)
        HouseResidencePackets.floorScore(session, score, warriors)
    }

    private suspend fun assignHouseWarriors(session: GameSession, data: ByteArray) {
        if (!visits.isInsideOwnHouse(session) || data.size != 10) {
            HouseResidencePackets.result(session, 3, 2)
            return
        }
        val packet = PacketReader(data)
        val ids = List(4) { packet.readUShortLE() }
        val building = packet.readUByte()
        val floor = packet.readUByte()
        if (building != 1 || floor != 1) {
            HouseResidencePackets.result(session, 3, 2)
            return
        }
        val (score, _) = currentFloorData(session)
        val maxSlots = HouseWarriorPolicy.slots(score, warriorThresholds)
        if (!HouseWarriorPolicy.valid(ids, maxSlots, eligibleWarriors(session))) {
            HouseResidencePackets.result(session, 3, 5)
            return
        }
        houseWarriors.replace(session.roleId.toLong(), building, floor, ids)
        HouseResidencePackets.result(session, 3, 1)
        houseLogger.info {
            "Gia Vien warrior assignment charId=${session.roleId} score=$score slots=$maxSlots ids=$ids"
        }
    }

    private suspend fun ownedFurniture(session: GameSession): Map<Int, Int> =
        inventory.ownedHouseFurniture(session.roleId.toLong())

    private suspend fun sendFurnitureBag(session: GameSession) {
        if (!visits.isInsideOwnHouse(session)) return
        val owned = ownedFurniture(session)
        val placed = HouseFurniturePolicy.visible(
            houseFurniture.load(session.roleId.toLong(), 1, 1), owned
        )
        houseLogger.info {
            "DVT S106-008 furniture snapshot: charId=${session.roleId} kinds=${owned.size} total=${owned.values.sum()} placed=${placed.size}"
        }
        HouseResidencePackets.furnitureBag(session, owned, placed)
    }

    private suspend fun saveFurniture(session: GameSession, data: ByteArray) {
        if (!visits.isInsideOwnHouse(session) || data.size < 6) {
            HouseResidencePackets.result(session, 6, 2)
            return
        }
        val reader = PacketReader(data)
        val building = reader.readUByte()
        val floor = reader.readUByte()
        val count = reader.readUIntLE()
        if (building != 1 || floor != 1 || count > HouseFurniturePolicy.MAX_PLACEMENTS ||
            count * 6L != reader.remaining.toLong()) {
            HouseResidencePackets.result(session, 6, 4)
            return
        }
        val proposed = ArrayList<HouseFurniturePlacement>(count.toInt())
        repeat(count.toInt()) {
            proposed += HouseFurniturePlacement(
                reader.readUShortLE(), reader.readUByte(), reader.readUByte(),
                reader.readUByte(), reader.readUByte()
            )
        }
        val owned = ownedFurniture(session)
        if (!HouseFurniturePolicy.validate(proposed, owned)) {
            HouseResidencePackets.result(session, 6, 7)
            return
        }
        houseFurniture.replace(session.roleId.toLong(), building, floor, proposed)
        HouseResidencePackets.result(session, 6, 1)
        houseLogger.info { "Gia Vien saved furniture charId=${session.roleId} count=${proposed.size}" }
    }

    private suspend fun updateHouseSettings(session: GameSession, data: ByteArray) {
        if (!visits.isInsideOwnHouse(session)) {
            HouseResidencePackets.result(session, 5, 2)
            return
        }
        val next = try {
            val reader = PacketReader(data)
            if (data.size < 2) null else {
                val name = reader.readBig5WithByteLen()
                val signature = reader.readBig5WithByteLen()
                if (reader.remaining != 0 || !HouseSettingsPolicy.valid(name, signature)) null
                else HouseResidenceSettings(name, signature)
            }
        } catch (_: Exception) {
            null
        }
        if (next == null) {
            HouseResidencePackets.result(session, 5, 2)
            return
        }
        houseSettings.save(session.roleId.toLong(), next)
        HouseResidencePackets.result(session, 5, 1)
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

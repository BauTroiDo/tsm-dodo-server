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
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.HouseExpansionPolicy
import tw.tsm.gamelogic.systems.CharacterPersistenceGuard
import tw.tsm.domain.interfaces.repositories.HouseExpansionRepository
import tw.tsm.domain.data.HouseExpandConditionDef
import java.util.concurrent.ConcurrentHashMap
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
    private val houseExpansion: HouseExpansionRepository,
    private val money: MoneySystem,
    private val expansionDefs: Map<Int, HouseExpandConditionDef>,
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOUSE_RESIDENCE
    /** Prevent duplicated C106-004 packets before the client requests level sync. */
    private val upgradesAwaitingLevelAck = ConcurrentHashMap<Long, Boolean>()
    // C106-004 has no nonce and C106-013 automatically re-arms the client.
    // Reject replayed upgrade packets for a short window even AFTER level ACK.
    private val lastPaidExpansionNanos = ConcurrentHashMap<Long, Long>()
    private val minimumUpgradeIntervalNanos = 5_000_000_000L

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
                val level = if (own) houseExpansion.loadLevel(session.roleId.toLong()) else 0
                HouseResidencePackets.totalLevel(session, if (own) 1 else 0, level)
                if (own) upgradesAwaitingLevelAck.remove(session.playerId)
            }
            4 -> purchaseHouseExpansion(session, data)
            11 -> HouseResidencePackets.result(session, 11, 0)
            else -> houseLogger.warn {
                "Gia Vien operation pending: charId=${session.roleId} kind=$subKind bytes=${data.size}"
            }
        }
    }

    /**
     * C106-004 carries NO user-provided level or price. Only server DVT data
     * supplies the next consecutive upgrade requirements. The DB transaction
     * checks sufficient distinct collected furniture kinds and gold.
     */
    private suspend fun purchaseHouseExpansion(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty() || !visits.isInsideOwnHouse(session) ||
            expansionDefs.size != HouseExpansionPolicy.LAST_LEVEL) {
            houseLogger.warn {
                "Gia Vien expansion request rejected charId=${session.roleId} bytes=${data.size} " +
                    "insideOwnHouse=${visits.isInsideOwnHouse(session)} DvtTiers=${expansionDefs.size}"
            }
            HouseResidencePackets.result(session, 4, 2)
            return
        }
        if (upgradesAwaitingLevelAck.putIfAbsent(session.playerId, true) != null) {
            houseLogger.info { "Gia Vien expansion duplicate suppressed charId=${session.roleId} pendingLevelAck=true" }
            HouseResidencePackets.result(session, 4, 2)
            return
        }
        var committed = false
        try {
            val id = session.roleId.toLong()
            val current = houseExpansion.loadLevel(id)
            val next = HouseExpansionPolicy.next(current, expansionDefs)
            if (next != null) {
                committed = money.purchaseHouseExpansionGold(id, next.goldCost) { gold, auctionGold ->
                    // Executed under BOTH MoneySystem's character and gold
                    // mutation fences, so check+publication are serialized.
                    val last = lastPaidExpansionNanos[id]
                    val now = System.nanoTime()
                    if (last != null && now - last < minimumUpgradeIntervalNanos) {
                        false
                    } else {
                        houseExpansion.purchaseNextLevel(
                            id, current, gold, auctionGold,
                            next.furnitureKindsRequired, next.goldCost,
                        ).also { if (it) lastPaidExpansionNanos[id] = System.nanoTime() }
                    }
                }
                if (committed) {
                    response.sendSubtractMoney(session, next.goldCost)
                    houseLogger.info {
                        "Gia Vien expansion committed charId=$id level=$current->${next.level} " +
                            "goldCost=${next.goldCost} requiredKinds=${next.furnitureKindsRequired}"
                    }
                }
            }
            if (!committed) {
                val snapshot = money.getMoney(id)
                val actualKinds = ownedFurniture(session).count { it.value > 0 }
                houseLogger.info {
                    "Gia Vien expansion purchase denied charId=$id currentLevel=$current " +
                        "requiredCost=${next?.goldCost ?: -1} requiredKinds=${next?.furnitureKindsRequired ?: -1} " +
                        "cachedGold=${snapshot.first} actualKinds=$actualKinds"
                }
            }
            HouseResidencePackets.result(session, 4, if (committed) 1 else 2)
        } catch (e: Exception) {
            houseLogger.error(e) {
                "Gia Vien expansion response/transaction error charId=${session.roleId} committed=$committed; " +
                    "client must refresh authoritative level on reconnect"
            }
            // If payment committed but the socket fails while sending S026-002
            // or S106-004, NEVER emit a false failure response after debit.
            if (!committed) HouseResidencePackets.result(session, 4, 2)
        } finally {
            // A successful upgrade requires fresh C106-013 from the client to
            // re-arm; this also suppresses duplicate C106-004 on double click.
            if (!committed) upgradesAwaitingLevelAck.remove(session.playerId)
        }
    }

    private suspend fun sendHouseData(session: GameSession, data: ByteArray) {
        if (data.size != 10) return
        val r = PacketReader(data)
        val owner = r.readULongLE()
        val building = r.readUByte()
        val floor = r.readUByte()
        val visit = visits.getVisit(session.playerId) ?: return
        if (owner != session.playerId || !visits.isInsideOwnHouse(session) ||
            visit.building != building || visit.floor != floor) return
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
        val houseLevel = houseExpansion.loadLevel(session.roleId.toLong())
        HouseResidencePackets.starterRoom(session, owner, building, floor, settings, placed, visible, houseLevel)
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
        val visit = visits.getVisit(session.playerId) ?: return 0 to emptyList()
        val placed = HouseFurniturePolicy.visible(
            houseFurniture.load(charId, visit.building, visit.floor), ownedFurniture(session)
        )
        val score = HouseWarriorPolicy.floorScore(placed, furnitureDefs)
        val capacity = HouseWarriorPolicy.slots(score, warriorThresholds)
        val visible = HouseWarriorPolicy.visible(
            houseWarriors.load(charId, visit.building, visit.floor), capacity, eligibleWarriors(session)
        )
        return score to visible
    }

    private suspend fun sendFloorScore(session: GameSession) {
        if (!visits.isInsideOwnHouse(session)) return
        val (score, warriors) = currentFloorData(session)
        val room = visits.getVisit(session.playerId) ?: return
        HouseResidencePackets.floorScore(session, room.building, room.floor, score, warriors)
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
        val room = visits.getVisit(session.playerId)
        if (room == null || room.building != building || room.floor != floor) {
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

    private suspend fun allPlacedFurniture(characterId: Long): List<HouseFurniturePlacement> {
        val all = ArrayList<HouseFurniturePlacement>()
        for (building in 1..4) for (floor in 1..2) {
            all += houseFurniture.load(characterId, building, floor)
        }
        return all
    }

    private suspend fun sendFurnitureBag(session: GameSession) {
        if (!visits.isInsideOwnHouse(session)) return
        val owned = ownedFurniture(session)
        val all = allPlacedFurniture(session.roleId.toLong())
        houseLogger.info {
            "DVT S106-008 furniture snapshot: charId=${session.roleId} kinds=${owned.size} total=${owned.values.sum()} placed=${all.size}"
        }
        HouseResidencePackets.furnitureBag(session, owned, all)
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
        val visit = visits.getVisit(session.playerId)
        val level = houseExpansion.loadLevel(session.roleId.toLong())
        if (visit == null || visit.building != building || visit.floor != floor ||
            !HouseExpansionPolicy.unlocked(level, building, floor) ||
            count > HouseFurniturePolicy.MAX_PLACEMENTS ||
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
        val characterId = session.roleId.toLong()
        val saved = CharacterPersistenceGuard.withLock(characterId) {
            val owned = ownedFurniture(session)
            val otherCounts = mutableMapOf<Int, Int>()
            for (b in 1..4) for (f in 1..2) {
                if (b == building && f == floor) continue
                houseFurniture.load(characterId, b, f).forEach {
                    otherCounts[it.furnitureId] = otherCounts.getOrDefault(it.furnitureId, 0) + 1
                }
            }
            val available = owned.mapValues { (id, total) ->
                (total - otherCounts.getOrDefault(id, 0)).coerceAtLeast(0)
            }
            if (!HouseFurniturePolicy.validate(proposed, available)) return@withLock false
            houseFurniture.replace(characterId, building, floor, proposed)
            true
        }
        HouseResidencePackets.result(session, 6, if (saved) 1 else 7)
        if (saved) houseLogger.info {
            "Gia Vien saved furniture charId=$characterId building=$building floor=$floor count=${proposed.size}"
        }
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
        val packet = PacketReader(data)
        val owner = packet.readULongLE()
        val building = packet.readUByte()
        val floor = packet.readUByte()
        val map = HouseVisitSystem.mapForBuilding(building)
        if (owner != session.playerId || map == null || floor !in 1..2 ||
            session.battleId != 0L) {
            HouseResidencePackets.entryResult(session, 2, building, floor)
            return
        }

        val level = houseExpansion.loadLevel(session.roleId.toLong())
        if (!HouseExpansionPolicy.unlocked(level, building, floor)) {
            HouseResidencePackets.entryResult(session, 2, building, floor)
            return
        }

        val movingInside = visits.isInsideOwnHouse(session)
        if (!movingInside && parties.isInParty(session.playerId)) {
            HouseResidencePackets.entryResult(session, 10, building, floor)
            return
        }
        val visit = if (movingInside) visits.getVisit(session.playerId)
            else visits.beginOwnHouse(session)
        if (visit == null) {
            HouseResidencePackets.entryResult(session, if (session.instanceId != 0) 9 else 2, building, floor)
            return
        }
        if (movingInside && visit.building == building && visit.floor == floor) {
            HouseResidencePackets.entryResult(session, 1, building, floor)
            return
        }
        val previousBuilding = visit.building
        val previousFloor = visit.floor
        val previousMap = session.mapId
        val previousX = session.x
        val previousY = session.y
        if (movingInside && !visits.moveWithinHouse(session, building, floor)) {
            HouseResidencePackets.entryResult(session, 2, building, floor)
            return
        }
        try {
            if (!movingInside) {
                // An outdoor return point is always persisted; house scenes never are.
                characters.updatePosition(session.roleId.toLong(),
                    visit.origin.mapId, visit.origin.x, visit.origin.y)
            }
            HouseResidencePackets.entryResult(session, 1, building, floor)
            world.leaveMap(session)
            world.enterMap(session, map, HouseVisitSystem.ENTRY_X,
                HouseVisitSystem.ENTRY_Y, visit.roomId)
            response.sendPlayerTeleport(session, session.playerId, session.mapId, session.x, session.y)
            response.sendSceneChange(session, session.playerId, session.mapId, session.x, session.y,
                session.equipItemIds, session.titleId, session.instanceId)
            if (!movingInside) {
                // A previous session may have paid and disconnected before
                // C106-013 arrived. Re-entering a fresh house visit clears
                // that stale session ACK fence, not the paid replay cooldown.
                upgradesAwaitingLevelAck.remove(session.playerId)
            }
            houseLogger.info {
                "Gia Vien room charId=${session.roleId} owner=$owner building=$building floor=$floor room=${visit.roomId}"
            }
        } catch (e: Exception) {
            houseLogger.error(e) { "Gia Vien room transition failed charId=${session.roleId}" }
            visit.building = previousBuilding
            visit.floor = previousFloor
            if (!movingInside) visits.cleanup(session.playerId)
            world.leaveMap(session)
            if (movingInside) {
                world.enterMap(session, previousMap, previousX, previousY, visit.roomId)
            } else {
                world.enterMap(session, visit.origin.mapId, visit.origin.x, visit.origin.y,
                    visit.origin.instanceId)
            }
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

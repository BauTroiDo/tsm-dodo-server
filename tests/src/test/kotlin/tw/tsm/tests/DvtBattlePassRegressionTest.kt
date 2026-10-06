package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ActivityHandler
import tw.tsm.data.loaders.*
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.*
import tw.tsm.domain.util.OleDate
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class DvtBattlePassRegressionTest {
    private fun bytes(name: String): ByteArray {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_PassAward_C.dat")) }
        return Files.readAllBytes(root.resolve("File.dat/Data-ios/$name"))
    }
    private val awards = PassAwardDatLoader.load(bytes("IOS_PassAward_C.dat"))
    private val times = JiugonggeDatLoader.loadActivityTimes(bytes("IOS_ActivityTime_C.dat"))
    private val items = ItemDatLoader.load(bytes("IOS_Item_C.dat"))
    private val now = LocalDateTime.of(2026, 10, 6, 12, 0)

    @Test fun `real DVT seasons thresholds quantities and missing next schedule`() {
        assertEquals(mapOf(117 to 50, 118 to 50), awards.groupingBy { it.seasonId }.eachCount())
        assertTrue(awards.all { it.rewards.size == 3 && it.rewards.all { reward -> reward.itemId == 0 || reward.itemId in items } })
        assertFalse(118 in times)
        assertEquals(PassReward(0,5), awards.first { it.seasonId == 117 && it.level == 44 }.rewards[2])
        val expItems = items.filterValues { it.specialAbility == 218 && it.roleCountIndex == 791 }
        assertTrue(expItems.isNotEmpty(), "BattlePass EXP item source must be evidenced")
        println("BattlePass EXP source items=" + expItems.mapValues { it.value.roleCountValue })
        val rows = awards.filter { it.seasonId == 117 }
        for (row in rows) {
            assertEquals(row.level - 1, BattlePassSystem.levelForExp(rows, row.totalExp - 1))
            assertEquals(row.level, BattlePassSystem.levelForExp(rows, row.totalExp))
        }
        assertEquals(0, BattlePassSystem.levelForExp(rows, 0))
        assertEquals(50, BattlePassSystem.levelForExp(rows, Int.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) { PassAwardDatLoader.load(bytes("IOS_PassAward_C.dat").dropLast(1).toByteArray()) }
    }

    private inner class Store(exp: Int) {
        val progress = mutableMapOf(791 to (exp to 0))
        val bag = mutableMapOf<Int, ThingData>()
        var season: Int? = null
        var ledger: BattlePassProgress? = null
        var paid = false
        var fail = false
        var writes = 0
        val countRepo = mockk<RoleCountRepository>(relaxed = true).also {
            coEvery { it.loadAll(any()) } answers { progress.toMap() }
        }
        val repo = mockk<InventoryRepository>(relaxed = true).also { r ->
            coEvery { r.loadItems(any(), any()) } answers { if (secondArg<Int>() == EThings.BAG) bag.toMap() else emptyMap() }
            coEvery { r.loadBattlePassProgress(any()) } answers { ledger ?: season?.let { BattlePassProgress(it,0,0) } }
            coEvery { r.saveBattlePassClaimAtomic(any(), any(), any(), any(), any()) } coAnswers {
                if (fail) error("DB unavailable")
                check(arg<Map<Int, Int>>(3).all { (index, value) ->
                    if (ledger == null) (progress[index]?.first ?: 0) == value else (progress[index]?.first ?: 0) <= value
                })
                val changes = arg<Map<Int, ThingData?>>(2)
                changes.forEach { (slot, thing) -> if (thing == null) bag.remove(slot) else bag[slot] = thing }
                progress.putAll(arg<Map<Int, Pair<Int, Int>>>(4))
                season = secondArg()
                ledger = BattlePassProgress(season!!, progress.getValue(792).first, progress.getValue(793).first)
                writes++
            }
        }
        val flags = mockk<BitFlagSystem>().also { coEvery { it.getSetFlagIds(any()) } answers { if (paid) setOf(6060) else emptySet() } }
        suspend fun runtime(): Triple<BattlePassSystem, InventorySystem, RoleCountSystem> {
            val counts = RoleCountSystem(countRepo)
            counts.loadCounts(1)
            val inv = InventorySystem(repo, items, counts)
            inv.loadCharacterData(1)
            return Triple(BattlePassSystem(awards, times, items, inv, counts, flags), inv, counts)
        }
    }

    @Test fun `free claims once concurrent replay relog then paid catches up without free duplicates`() = runTest {
        val rows = awards.filter { it.seasonId == 117 }.take(3)
        val store = Store(rows.last().totalExp)
        val (system, _, _) = store.runtime()
        val results = List(5) { async { system.claim(1, now) } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(3, store.progress[792]?.first)
        assertEquals(0, store.progress[793]?.first)
        val expectedFree = rows.groupBy { it.rewards[0].itemId }.mapValues { (_, rs) -> rs.sumOf { it.rewards[0].quantity } }
        expectedFree.forEach { (id, qty) -> assertEquals(qty, store.bag.values.filter { it.itemId == id }.sumOf { it.quantity.toLong() }) }
        val (relogged, _, _) = store.runtime()
        assertFalse(relogged.claim(1, now))
        store.paid = true
        assertTrue(relogged.claim(1, now))
        assertEquals(3, store.progress[793]?.first)
        val all = rows.flatMap { it.rewards }.filter { it.itemId > 0 }.groupBy { it.itemId }.mapValues { (_, rs) -> rs.sumOf { it.quantity } }
        all.forEach { (id, qty) -> assertEquals(qty, store.bag.values.filter { it.itemId == id }.sumOf { it.quantity.toLong() }) }
        assertFalse(store.runtime().first.claim(1, now))
        assertEquals(2, store.writes)
        store.progress[792] = 0 to 0
        store.progress[793] = 0 to 0
        val (staleRelog, _, staleCounts) = store.runtime()
        assertFalse(staleRelog.claim(1,now))
        assertEquals(3,staleCounts.getValue(1,792))
        assertEquals(3,staleCounts.getValue(1,793))
        assertEquals(2,store.writes)
    }

    @Test fun `persistence failure full bag expiry and unproven rollover never consume progress`() = runTest {
        val store = Store(awards.first().totalExp)
        var (system, inv, counts) = store.runtime()
        store.fail = true
        assertFalse(system.claim(1, now))
        assertTrue(inv.getBag(1).isEmpty())
        assertEquals(0, counts.getValue(1, 792))
        assertEquals(null, store.season)
        store.fail = false
        store.bag.putAll((0 until counts.getBagCapacity(1)).associateWith { ThingData(1, 1) })
        system = store.runtime().first
        assertFalse(system.claim(1, now))
        assertFalse(system.claim(1, BattlePassSystem.end(times.getValue(117))))
        store.season = 116
        assertFalse(system.isOpen(1, now))
        assertFalse(system.claim(1, now))
        assertEquals(0, store.writes)
    }

    @Test fun `season window survives daily close and GM cannot fabricate another season`() {
        val session = mockk<GameSession>(relaxed = true)
        every { session.gmLevel } returns 99
        val packets = mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        ActivityScheduleSystem(times, AdminBypassPolicy(), setOf(117,118)).sendActivityState(session,117,now)
        val reader = PacketReader(packets[0])
        assertEquals(77,reader.readUByte()); assertEquals(1,reader.readUByte()); reader.readUByte()
        assertEquals(117,reader.readUShortLE())
        assertEquals(OleDate.fromLocalDateTime(BattlePassSystem.start(times.getValue(117))),reader.readDoubleLE())
        assertEquals(OleDate.fromLocalDateTime(BattlePassSystem.end(times.getValue(117))),reader.readDoubleLE())
        assertArrayEquals(byteArrayOf(77,11,0,128.toByte(),1,0),ActivityHandler.battlePassPacket(1,0))
        assertArrayEquals(byteArrayOf(77,11,0,128.toByte(),255.toByte(),1),ActivityHandler.battlePassPacket(255,1))
    }
    @Test fun `handler query opens UI and claims sync bag counters before success with malformed requests rejected`() = runTest {
        val bp = mockk<BattlePassSystem>()
        val counts = mockk<RoleCountSystem>(relaxed = true)
        val inventory = mockk<InventorySystem>(relaxed = true)
        val response = mockk<tw.tsm.domain.interfaces.ResponseSender>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)
        every { session.state } returns tw.tsm.domain.enums.SessionState.IN_GAME
        every { session.roleId } returns 1
        every { session.battleId } returns 0L
        coEvery { bp.isOpen(1, any()) } returns true
        coEvery { bp.claim(1, any()) } returns true
        val packets = mutableListOf<ByteArray>()
        every { session.send(capture(packets)) } just Runs
        val handler = ActivityHandler(mockk(), mockk(), mockk(), emptyMap(), emptyMap(), bp, counts, inventory, response)
        handler.handle(session,11,byteArrayOf(128.toByte(),0))
        assertArrayEquals(ActivityHandler.battlePassPacket(1,0),packets.last())
        coVerify(exactly = 0) { bp.claim(any(),any()) }
        handler.handle(session,11,byteArrayOf(128.toByte(),1))
        verifyOrder {
            response.sendAllBagItems(session,any(),true)
            response.sendRoleCountUpdate(session,any())
            session.send(ActivityHandler.battlePassPacket(1,1))
        }
        val packetCount = packets.size
        handler.handle(session,11,byteArrayOf(128.toByte(),1,1))
        handler.handle(session,11,byteArrayOf(128.toByte(),3))
        assertEquals(packetCount,packets.size)
        every { session.battleId } returns 10L
        handler.handle(session,11,byteArrayOf(128.toByte(),1))
        assertArrayEquals(ActivityHandler.battlePassPacket(0,1),packets.last())
        coVerify(exactly = 1) { bp.claim(any(),any()) }
    }

}

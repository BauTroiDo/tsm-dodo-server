package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.SlotMachineHandler
import tw.tsm.app.handlers.UISellHandler
import tw.tsm.app.response.SlotMachineResponseSender
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.data.loaders.SlotMachineDatLoader
import tw.tsm.domain.data.SlotMachineAward
import tw.tsm.domain.data.SlotMachineStateData
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.SlotMachineRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.PlayerCurrencySystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.gamelogic.systems.SlotMachineSpinOutcome
import tw.tsm.gamelogic.systems.SlotMachineSystem
import tw.tsm.gamelogic.systems.UISellSystem
import tw.tsm.gamelogic.systems.WorldManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class DvtSlotMachineTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }

    private val pools by lazy {
        SlotMachineDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Slot.dat"))
        )
    }

    private val itemDefs by lazy {
        ItemDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Item_C.dat"))
        )
    }

    @Test
    fun `IOS Slot decoder reproduces authoritative four pools`() {
        assertEquals(25, pools.poolA.size)
        assertEquals(19, pools.poolB.size)
        assertEquals(35, pools.poolC.size)
        assertEquals(39, pools.poolD.size)

        assertEquals(listOf(46037, 50, 1), pools.poolA.first().let { listOf(it.itemId, it.count, it.weight) })
        assertEquals(listOf(45607, 50, 18), pools.poolB.first().let { listOf(it.itemId, it.count, it.weight) })
        assertEquals(listOf(31171, 15, 20), pools.poolC.first().let { listOf(it.itemId, it.count, it.weight) })
        assertEquals(listOf(15090, 1, 60), pools.poolD.first().let { listOf(it.itemId, it.count, it.weight) })

        assertEquals(138, pools.poolA.sumOf { it.weight })
        assertEquals(172, pools.poolB.sumOf { it.weight })
        assertEquals(6575, pools.poolC.sumOf { it.weight })
        assertEquals(5005, pools.poolD.sumOf { it.weight })
    }

    @Test
    fun `generated board keeps DVT tier cardinality 1 2 3 10 and unique physical slots`() = runTest {
        var stored = SlotMachineStateData()
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)

        val state = system.open(42L)

        assertEquals(16, state.awards.size)
        assertEquals((1..16).toSet(), state.awards.map { it.awardIndex }.toSet())
        assertEquals((1..16).toSet(), state.awards.map { it.slotIndex }.toSet())

        val aIds = pools.poolA.map { it.itemId }.toSet()
        val bIds = pools.poolB.map { it.itemId }.toSet()
        val cIds = pools.poolC.map { it.itemId }.toSet()
        val dIds = pools.poolD.map { it.itemId }.toSet()

        assertTrue(state.awards.single { it.awardIndex == 1 }.itemId in aIds)
        assertTrue(state.awards.filter { it.awardIndex in 2..3 }.all { it.itemId in bIds })
        assertTrue(state.awards.filter { it.awardIndex in 4..6 }.all { it.itemId in cIds })
        assertTrue(state.awards.filter { it.awardIndex in 7..16 }.all { it.itemId in dIds })

        assertEquals(100, (1..16).sumOf { system.wireProbability(it) })
    }

    @Test
    fun `normal spin consumes one coin and grants one board reward transactionally`() = runTest {
        val rewardItemId = 15090
        assertEquals(0, itemDefs.getValue(rewardItemId).directUse)

        val board = (1..16).map {
            SlotMachineAward(
                awardIndex = it,
                itemId = rewardItemId,
                count = 1,
                slotIndex = it,
            )
        }
        var stored = SlotMachineStateData(coin = 10, awards = board)
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }

        val inventory = mockk<InventorySystem>()
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val thing = ThingData(itemId = rewardItemId, quantity = 1)

        every { inventory.canAddRewardsToBag(42L, listOf(rewardItemId to 1)) } returns true
        coEvery {
            inventory.addRewardsToBagExact(42L, listOf(rewardItemId to 1))
        } returns listOf(7 to thing)

        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val result = system.spin(42L, SlotMachineSystem.SUB_NORMAL)

        assertTrue(result is SlotMachineSpinOutcome.Success)
        result as SlotMachineSpinOutcome.Success
        assertEquals(9, result.state.coin)
        assertEquals(9, stored.coin)
        assertEquals(1, result.resultSlotIndices.size)
        assertEquals(listOf(7 to thing), result.changedBagSlots)

        coVerify(exactly = 1) {
            inventory.addRewardsToBagExact(42L, listOf(rewardItemId to 1))
        }
    }

    @Test
    fun `spin refuses insufficient coin without touching rewards`() = runTest {
        val board = (1..16).map {
            SlotMachineAward(it, 15090, 1, it)
        }
        var stored = SlotMachineStateData(coin = 1, awards = board)
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)

        val result = system.spin(42L, SlotMachineSystem.SUB_TRIPLE)

        assertEquals(SlotMachineSpinOutcome.CoinInsufficient, result)
        assertEquals(1, stored.coin)
        coVerify(exactly = 0) { inventory.addRewardsToBagExact(any(), any()) }
    }

    @Test
    fun `S72 wire matches DVT client contract`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender>(relaxed = true) {
            every { send(capture(packets)) } just Runs
        }
        val sender = SlotMachineResponseSender()

        val awards = (1..16).map {
            SlotMachineAward(
                awardIndex = it,
                itemId = 1000 + it,
                count = it,
                slotIndex = 17 - it,
            )
        }
        sender.sendAwards(session, awards)
        sender.sendSpinResult(session, listOf(3, 7, 12))
        sender.sendMessage(session, 6)
        sender.sendCoin(session, 513)

        assertEquals(4, packets.size)

        ByteBuffer.wrap(packets[0]).order(ByteOrder.LITTLE_ENDIAN).apply {
            assertEquals(72, get().toInt() and 0xFF)
            assertEquals(1, get().toInt() and 0xFF)
            assertEquals(0, get().toInt() and 0xFF)
            for (i in 1..16) {
                assertEquals(1000 + i, short.toInt() and 0xFFFF)
                assertEquals(i, get().toInt() and 0xFF)
                assertEquals(17 - i, get().toInt() and 0xFF)
                val expectedPr = when (i) {
                    1 -> 1
                    in 2..3 -> 2
                    in 4..6 -> 5
                    else -> 8
                }
                assertEquals(expectedPr, short.toInt() and 0xFFFF)
            }
            assertFalse(hasRemaining())
        }

        assertArrayEquals(
            byteArrayOf(72, 2, 0, 3, 3, 7, 12),
            packets[1]
        )
        assertArrayEquals(
            byteArrayOf(72, 3, 0, 6),
            packets[2]
        )
        assertArrayEquals(
            byteArrayOf(72, 6, 0, 0x01, 0x02),
            packets[3]
        )
    }

    @Test
    fun `unsupported direct reward finishes client spin without consuming coin`() = runTest {
        val rewardItemId = 31922
        val board = (1..16).map {
            SlotMachineAward(it, rewardItemId, 1, it)
        }
        var stored = SlotMachineStateData(coin = 10, awards = board)
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val response = mockk<ResponseSender>(relaxed = true)
        val session = inGameSession()
        val handler = SlotMachineHandler(system, response)

        handler.handle(session, SlotMachineSystem.SUB_NORMAL, byteArrayOf())

        assertEquals(10, stored.coin)
        verify(exactly = 1) { response.sendSlotMachineCoin(session, 10) }
        verify(exactly = 1) {
            response.sendSlotMachineSpinResult(
                session,
                match { it.size == 1 && it.single() in 1..16 }
            )
        }
        verify(exactly = 0) { response.sendSlotMachineMessage(session, any()) }
        coVerify(exactly = 0) { inventory.addRewardsToBagExact(any(), any()) }
    }

    @Test
    fun `direct-use Slot rewards identify proven currencies and keep Elf chest unsupported`() {
        val soul = itemDefs.getValue(65345)
        val colorSoul = itemDefs.getValue(65346)
        val warrior = itemDefs.getValue(65347)
        val weapon = itemDefs.getValue(65349)
        val elfChest = itemDefs.getValue(31922)

        for (def in listOf(soul, colorSoul, warrior, weapon)) {
            assertEquals(1, def.directUse)
            assertEquals(216, def.specialAbility)
        }
        assertEquals(18, soul.attr1Kind)
        assertEquals(19, colorSoul.attr1Kind)
        assertEquals(20, warrior.attr1Kind)
        assertEquals(22, weapon.attr1Kind)

        assertEquals(1, elfChest.directUse)
        assertEquals(239, elfChest.specialAbility)
        assertEquals("Dị Giới Rương Thần", elfChest.name)
    }

    @Test
    fun `double reward costs two coins and grants double quantity`() = runTest {
        val rewardItemId = 15090
        val board = (1..16).map {
            SlotMachineAward(it, rewardItemId, 1, it)
        }
        var stored = SlotMachineStateData(coin = 10, awards = board)
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>()
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val thing = ThingData(itemId = rewardItemId, quantity = 2)

        every { inventory.canAddRewardsToBag(42L, listOf(rewardItemId to 2)) } returns true
        coEvery {
            inventory.addRewardsToBagExact(42L, listOf(rewardItemId to 2))
        } returns listOf(7 to thing)

        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val result = system.spin(42L, SlotMachineSystem.SUB_DOUBLE)

        assertTrue(result is SlotMachineSpinOutcome.Success)
        result as SlotMachineSpinOutcome.Success
        assertEquals(8, result.state.coin)
        assertEquals(8, stored.coin)
        assertEquals(1, result.resultSlotIndices.size)
        coVerify(exactly = 1) {
            inventory.addRewardsToBagExact(42L, listOf(rewardItemId to 2))
        }
    }

    @Test
    fun `triple spin costs six coins and grants three independent results`() = runTest {
        val rewardItemId = 15090
        val board = (1..16).map {
            SlotMachineAward(it, rewardItemId, 1, it)
        }
        var stored = SlotMachineStateData(coin = 10, awards = board)
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>()
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val rewards = listOf(rewardItemId to 1, rewardItemId to 1, rewardItemId to 1)
        val thing = ThingData(itemId = rewardItemId, quantity = 3)

        every { inventory.canAddRewardsToBag(42L, rewards) } returns true
        coEvery { inventory.addRewardsToBagExact(42L, rewards) } returns listOf(7 to thing)

        val system = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val result = system.spin(42L, SlotMachineSystem.SUB_TRIPLE)

        assertTrue(result is SlotMachineSpinOutcome.Success)
        result as SlotMachineSpinOutcome.Success
        assertEquals(4, result.state.coin)
        assertEquals(4, stored.coin)
        assertEquals(3, result.resultSlotIndices.size)
        assertTrue(result.resultSlotIndices.all { it in 1..16 })
        coVerify(exactly = 1) { inventory.addRewardsToBagExact(42L, rewards) }
    }

    @Test
    fun `UISell11 query exposes exact item and 9 Point per coin routes`() = runTest {
        var stored = SlotMachineStateData()
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val slotSystem = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val response = mockk<ResponseSender>(relaxed = true)
        val point = mockk<PointSystem>(relaxed = true)
        val handler = newUiSellHandler(response, point, inventory, slotSystem)
        val session = inGameSession()

        // UISlotMachine item route: Launch(11, nil, amount)
        handler.handle(session, 1, byteArrayOf(11, 0, 5, 0))
        verify(exactly = 1) {
            response.sendUiSellItemQuery(session, 11, 31171, 5)
        }

        // UISlotMachine Point route: Launch(11, nil, 0, amount)
        handler.handle(session, 1, byteArrayOf(11, 0, 0, 0, 5, 0))
        verify(exactly = 1) {
            response.sendUiSellMoneyQuery(session, 11, 1, 45L)
        }
    }

    @Test
    fun `UISell11 multi-stack item payment deposits exact Slot coin amount`() = runTest {
        var stored = SlotMachineStateData()
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>()
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val slotSystem = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val response = mockk<ResponseSender>(relaxed = true)
        val point = mockk<PointSystem>(relaxed = true)
        val handler = newUiSellHandler(response, point, inventory, slotSystem)
        val session = inGameSession()

        every { inventory.getBagItem(42L, 300) } returns ThingData(itemId = 31171, quantity = 2)
        every { inventory.getBagItem(42L, 301) } returns ThingData(itemId = 31171, quantity = 3)
        coEvery {
            inventory.removeBagItemsExact(42L, listOf(300 to 2, 301 to 3))
        } returns true

        val packet = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .put(1)                    // useKind=item
            .putShort(11)              // sellId
            .put(2)                    // two stacks
            .putShort(300).putShort(2)
            .putShort(301).putShort(3)
            .putShort(5)               // args=[amount]
            .array()

        handler.handle(session, 2, packet)

        assertEquals(5, stored.coin)
        coVerify(exactly = 1) {
            inventory.removeBagItemsExact(42L, listOf(300 to 2, 301 to 3))
        }
        verify(exactly = 1) { response.sendReduceBagItem(session, 300, 2) }
        verify(exactly = 1) { response.sendReduceBagItem(session, 301, 3) }
        verify(exactly = 1) { response.sendSlotMachineCoin(session, 5) }
        verify(exactly = 1) { response.sendUiSellSuccess(session, 11) }
    }

    @Test
    fun `UISell11 Point payment deducts 9 Point per deposited coin`() = runTest {
        var stored = SlotMachineStateData()
        val repo = object : SlotMachineRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: SlotMachineStateData) {
                stored = state
            }
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val currencies = mockk<PlayerCurrencySystem>(relaxed = true)
        val slotSystem = SlotMachineSystem(repo, pools, inventory, currencies, itemDefs)
        val response = mockk<ResponseSender>(relaxed = true)
        val point = mockk<PointSystem>()
        val handler = newUiSellHandler(response, point, inventory, slotSystem)
        val session = inGameSession()

        every { point.subtractRealPoint(42L, 45) } returns true
        coEvery { point.savePoints(42L) } just Runs
        every { point.getPoints(42L) } returns (955 to 0)

        val packet = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
            .put(2)                    // useKind=Point
            .putShort(11)              // sellId
            .putShort(0)               // Point route marker
            .putShort(5)               // amount
            .array()

        handler.handle(session, 2, packet)

        assertEquals(5, stored.coin)
        verify(exactly = 1) { point.subtractRealPoint(42L, 45) }
        coVerify(exactly = 1) { point.savePoints(42L) }
        verify(exactly = 1) { response.sendPointBalance(session, 955, 0) }
        verify(exactly = 1) { response.sendSlotMachineCoin(session, 5) }
        verify(exactly = 1) { response.sendUiSellSuccess(session, 11) }
    }

    private fun inGameSession(): GameSession = mockk(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
        every { characterName } returns "Tester"
    }

    private fun newUiSellHandler(
        response: ResponseSender,
        point: PointSystem,
        inventory: InventorySystem,
        slotSystem: SlotMachineSystem,
    ): UISellHandler = UISellHandler(
        responseSender = response,
        uiSellSystem = mockk<UISellSystem>(relaxed = true),
        pointSystem = point,
        inventorySystem = inventory,
        flyPointSystem = mockk<FlyPointSystem>(relaxed = true),
        worldManager = mockk<WorldManager>(relaxed = true),
        characterRepository = mockk<CharacterRepository>(relaxed = true),
        partySystem = mockk<PartySystem>(relaxed = true),
        roleCountSystem = mockk<RoleCountSystem>(relaxed = true),
        skillRepository = mockk<SkillRepository>(relaxed = true),
        playerStateManager = mockk<PlayerStateManager>(relaxed = true),
        skillSystem = mockk<SkillSystem>(relaxed = true),
        slotMachineSystem = slotSystem,
    )
}

package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.MountHandler
import tw.tsm.app.response.MountResponseSender
import tw.tsm.data.loaders.MountDatLoader
import tw.tsm.data.loaders.MountGrowDatLoader
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MountRepository
import tw.tsm.domain.valueobjects.MountData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.LevelUpResult
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.gamelogic.systems.WorldManager
import java.nio.file.Files
import java.nio.file.Path

class DvtMountContractTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }

    @Test
    fun `Mobile DVT IOS mount catalogue contains the full client collection`() {
        val ios = MountDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Mounts_C.dat"))
        )
        assertEquals(224, ios.size)
    }

    @Test
    fun `Mobile DVT mount grow table is authoritative and max level is 10`() {
        val ios = MountGrowDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_MountsGrow_C.dat"))
        )

        assertEquals((1..10).toSet(), ios.keys)
        assertEquals(10, ios.size)

        val lv1 = ios.getValue(1)
        assertEquals(32101, lv1.upItemId)
        assertEquals(10, lv1.upItemCount)
        assertEquals(1000L, lv1.upMoney)

        val lv9 = ios.getValue(9)
        assertEquals(32101, lv9.upItemId)
        assertEquals(250, lv9.upItemCount)
        assertEquals(5_000_000L, lv9.upMoney)

        val lv10 = ios.getValue(10)
        assertEquals(0, lv10.upItemId)
        assertEquals(0, lv10.upItemCount)
        assertEquals(0L, lv10.upMoney)
        assertFalse(ios.containsKey(11))
    }

    @Test
    fun `S79-4 and S79-5 use UInt16 bag index`() {
        val packets = mutableListOf<ByteArray>()
        val session = mockk<SessionSender>(relaxed = true) {
            every { send(capture(packets)) } just Runs
        }
        val sender = MountResponseSender()

        sender.sendMountEquip(session, 300)
        sender.sendMountUnequip(session, 4, 300)

        assertArrayEquals(
            byteArrayOf(79, 4, 0, 0x2C, 0x01),
            packets[0]
        )
        assertArrayEquals(
            byteArrayOf(79, 5, 0, 4, 0x2C, 0x01),
            packets[1]
        )
    }

    @Test
    fun `C79-3 upgrade reads exact UInt16 bag index`() = runTest {
        val mountSystem = mockk<MountSystem>()
        val response = mockk<ResponseSender>(relaxed = true)
        val world = mockk<WorldManager>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)

        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 42
        every { session.characterName } returns "Tester"
        coEvery { mountSystem.levelUp(42L, 300) } returns LevelUpResult(2, 0, 0)

        val handler = MountHandler(mountSystem, response, world)
        handler.handle(session, 3, byteArrayOf(0x2C, 0x01))

        coVerify(exactly = 1) { mountSystem.levelUp(42L, 300) }
        verify(exactly = 1) { response.sendMountLevel(session, 2) }
    }

    @Test
    fun `C79-4 nourish consumes one item per click and adds one progress point`() = runTest {
        val grow = MountGrowDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_MountsGrow_C.dat"))
        )
        val repository = mockk<MountRepository>()
        val inventory = mockk<InventorySystem>()
        val money = mockk<MoneySystem>(relaxed = true)
        val flags = mockk<BitFlagSystem>(relaxed = true)

        coEvery { repository.findByCharacterId(42L) } returns MountData(level = 2)
        every { inventory.getBagItem(42L, 300) } returns ThingData(itemId = 32103, quantity = 9)
        coEvery { inventory.removeFromBag(42L, 300, 1) } returns true

        val system = MountSystem(
            repository,
            emptyMap(),
            grow,
            inventory,
            money,
            flags,
            emptyMap()
        )
        system.load(42L)

        val result = system.addAttributePoint(42L, 2, 300)

        assertNotNull(result)
        assertEquals(1, result!!.newPoints)
        assertEquals(1, result.itemConsumed)
        coVerify(exactly = 1) { inventory.removeFromBag(42L, 300, 1) }
        coVerify(exactly = 0) { inventory.removeFromBag(42L, 300, 10) }
    }

    @Test
    fun `C79-3 rejects legacy one-byte bag index`() = runTest {
        val mountSystem = mockk<MountSystem>(relaxed = true)
        val response = mockk<ResponseSender>(relaxed = true)
        val world = mockk<WorldManager>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true)

        every { session.state } returns SessionState.IN_GAME
        every { session.roleId } returns 42

        val handler = MountHandler(mountSystem, response, world)
        handler.handle(session, 3, byteArrayOf(5))

        coVerify(exactly = 0) { mountSystem.levelUp(any(), any()) }
    }
}

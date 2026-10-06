package tw.tsm.tests

import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.PokePackets
import tw.tsm.data.loaders.PokeDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.domain.data.PokeRewardDef
import tw.tsm.domain.data.PokeStateData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.PokeClaimOutcome
import tw.tsm.gamelogic.systems.PokeSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class DvtPokeTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent))
        return parent
    }

    @Test
    fun `DVT poke dat files decode exact rewards rates and bonus rows`() {
        val root = existing("File.dat", "Data-ios")
        val rewards = PokeDatLoader.loadRewards(Files.readAllBytes(root.resolve("IOS_PokeData_C.dat")))
        val rates = PokeDatLoader.loadRates(Files.readAllBytes(root.resolve("IOS_PokeRate_C.dat")))
        val bonus = PokeDatLoader.loadBonusRewards(Files.readAllBytes(root.resolve("IOS_PokeBonusReward_C.dat")))

        assertEquals(93, rewards.size)
        assertEquals(mapOf(1 to 18, 2 to 4, 3 to 2, 4 to 1), rates)
        assertEquals(53, bonus.size)
        assertEquals(PokeRewardDef(1, 1, 46116, 5), rewards.first())
        assertEquals(38, rewards.count { it.kind == 1 })
        assertEquals(18, rewards.count { it.kind == 2 })
        assertEquals(16, rewards.count { it.kind == 3 })
        assertEquals(21, rewards.count { it.kind == 4 })
    }

    @Test
    fun `all current Poke rewards are real bag items`() {
        val root = existing("File.dat", "Data-ios")
        val rewards = PokeDatLoader.loadRewards(Files.readAllBytes(root.resolve("IOS_PokeData_C.dat")))
        val items = ItemDatLoader.load(Files.readAllBytes(root.resolve("IOS_Item_C.dat")))
        val missing = rewards.filter { items[it.itemId] == null }
        assertTrue(missing.isEmpty(), "Missing Poke item defs: " + missing)
        val directUse = rewards.filter { items[it.itemId]?.directUse == 1 }
        assertTrue(directUse.isNotEmpty())
        assertTrue(directUse.all {
            val def = items.getValue(it.itemId)
            def.specialAbility == 216 && (def.attr1Kind - 8) in 1..14 && def.attr1Value > 0
        })
        assertEquals(setOf(6, 10, 11, 13, 14), directUse.map { items.getValue(it.itemId).attr1Kind - 8 }.toSet())
    }

    @Test
    fun `claim uses odd escalating coin cost and persists opened grid`() = runTest {
        var stored = PokeStateData(coin = 10, openedSlots = mapOf(1 to 1))
        val repo = object : PokeRepository {
            override suspend fun load(characterId: Long) = stored
            override suspend fun save(characterId: Long, state: PokeStateData) { stored = state }
        }
        val inventory = mockk<InventorySystem>()
        every { inventory.canAddItemToBag(any(), any(), any()) } returns true
        coEvery { inventory.addItemsToBagExact(any(), any(), any()) } returns emptyList()

        val rewards = listOf(
            PokeRewardDef(1, 1, 46116, 5),
            PokeRewardDef(2, 2, 46117, 1),
        )
        val itemDefs = ItemDatLoader.load(
            Files.readAllBytes(existing("File.dat", "Data-ios", "IOS_Item_C.dat"))
        )
        val system = PokeSystem(
            repo, rewards, mapOf(1 to 18, 2 to 4, 3 to 2, 4 to 1),
            inventory, itemDefs = itemDefs, random = Random(1)
        )

        val result = system.claim(7L, 2)
        assertTrue(result is PokeClaimOutcome.Success)
        assertEquals(7, stored.coin) // one existing open => next cost 3
        assertEquals(2, stored.openedSlots[2])
    }

    @Test
    fun `S92 snapshot matches Lua wire contract`() = runTest {
        val state = PokeStateData(coin = 12, openedSlots = mapOf(1 to 7))
        val repo = object : PokeRepository {
            override suspend fun load(characterId: Long) = state
            override suspend fun save(characterId: Long, state: PokeStateData) = Unit
        }
        val inventory = mockk<InventorySystem>(relaxed = true)
        val system = PokeSystem(
            repo,
            listOf(PokeRewardDef(1, 1, 46116, 5)),
            mapOf(1 to 18, 2 to 4, 3 to 2, 4 to 1),
            inventory,
        )
        val session = mockk<GameSession>()
        val sent = mutableListOf<ByteArray>()
        every { session.send(capture(sent)) } answers { Unit }

        PokePackets.sendSnapshot(session, system, state)

        assertEquals(2, sent.size)
        assertEquals(listOf(92, 1, 0, 12, 0), sent[0].map { it.toInt() and 255 })
        assertEquals(
            listOf(92, 2, 0, 1, 1, 1, 0x24, 0xB4, 5, 0, 0, 0, 7, 18, 0),
            sent[1].map { it.toInt() and 255 }
        )
        verify(exactly = 2) { session.send(any()) }
    }
}

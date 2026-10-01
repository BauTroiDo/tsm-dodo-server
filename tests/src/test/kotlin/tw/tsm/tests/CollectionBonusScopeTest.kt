package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.TradeHandler
import tw.tsm.domain.data.*
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CollectionBookRepository
import tw.tsm.gamelogic.systems.*

class CollectionBonusScopeTest {
    @Test
    fun nftShineBonusAppliesToPlayerButNotFollowNpc() {
        val system = CollectionSystem(
            bitFlagSystem = mockk(relaxed = true),
            collectWarriorDefs = emptyMap(),
            collectStyleDefs = emptyMap(),
            collectStyleValueDefs = listOf(
                // Normal collection threshold: shared by Player and FollowNpc.
                CollectStyleValueDef(
                    score = 0,
                    attributes = listOf(31 to 2),
                    nft = 0,
                ),
                // NFT Shine threshold: DVT Calculator.lua applies this to Player only.
                CollectStyleValueDef(
                    score = 0,
                    attributes = listOf(31 to 5),
                    nft = 1,
                ),
            ),
            collectCardDefs = emptyMap(),
            collectCardSortDefs = emptyMap(),
            collectNftDefs = emptyMap(),
            mountDefs = emptyMap(),
            inventorySystem = mockk(relaxed = true),
            roleCountSystem = mockk(relaxed = true),
            playerStateManager = mockk(relaxed = true),
            statSystem = mockk(relaxed = true),
            repository = mockk<CollectionBookRepository>(relaxed = true),
            responseSender = mockk<ResponseSender>(relaxed = true),
        )

        assertEquals(7, system.getPlayerCollectionAttribute(1L, 31))
        assertEquals(2, system.getFollowerCollectionAttribute(1L, 31))
    }

    private fun makeSystem(
        bitFlags: BitFlagSystem,
        warriors: Map<Int, CollectWarriorDef> = emptyMap(),
        nfts: Map<Int, CollectNftDef> = emptyMap(),
        mounts: Map<Int, MountDef> = emptyMap(),
        inventory: InventorySystem = mockk(relaxed = true),
    ) = CollectionSystem(
        bitFlagSystem = bitFlags,
        collectWarriorDefs = warriors,
        collectStyleDefs = emptyMap(),
        collectStyleValueDefs = emptyList(),
        collectCardDefs = emptyMap(),
        collectCardSortDefs = emptyMap(),
        collectNftDefs = nfts,
        mountDefs = mounts,
        inventorySystem = inventory,
        roleCountSystem = mockk(relaxed = true),
        playerStateManager = mockk(relaxed = true),
        statSystem = mockk(relaxed = true),
        repository = mockk<CollectionBookRepository>(relaxed = true),
        responseSender = mockk<ResponseSender>(relaxed = true),
    )

    @Test
    fun jackpotWarrior22113SetsGetFlag4353() = runTest {
        val bitFlags = mockk<BitFlagSystem>(relaxed = true)
        coEvery { bitFlags.getSetFlagIds(31L) } returns emptySet()
        val warrior = CollectWarriorDef(
            id = 22113, getFlag = 4353, rewardFlag = 4354,
            gainStr = 0L, awards = emptyList(), shineValue = 14,
        )
        val system = makeSystem(bitFlags, warriors = mapOf(22113 to warrior))

        assertEquals(4353, system.markWarriorOwned(31L, 22113))
        coVerify(exactly = 1) { bitFlags.setFlag(31L, 4353) }
    }

    @Test
    fun nftMountCanGetZeroCannotBeWithdrawn() = runTest {
        val bitFlags = mockk<BitFlagSystem>(relaxed = true)
        val inventory = mockk<InventorySystem>(relaxed = true)
        val nft = CollectNftDef(
            kind = 2, flag = 1075, warriorId = 18249,
            getId = 31383, canGet = 0, shineValue = 14,
        )
        val mount = MountDef(18249, 1075, 0L, 1.0)
        val system = makeSystem(
            bitFlags, nfts = mapOf(18249 to nft),
            mounts = mapOf(18249 to mount), inventory = inventory,
        )

        assertFalse(system.takeNftMount(31L, 18249).success)
        coVerify(exactly = 0) { bitFlags.clearFlag(any(), any()) }
        coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
    }

    @Test
    fun nftShineSumsWarriorStyleMountAndCard() = runTest {
        val bitFlags = mockk<BitFlagSystem>(relaxed = true)
        coEvery { bitFlags.getSetFlagIds(31L) } returns setOf(4353, 1075, 2000)

        val repository = mockk<CollectionBookRepository>(relaxed = true)
        coEvery { repository.loadStyleFlags(31L) } returns mapOf(18 to 0b1_1111)
        coEvery { repository.loadCardStates(31L) } returns emptyMap()

        val system = CollectionSystem(
            bitFlagSystem = bitFlags,
            collectWarriorDefs = mapOf(
                22113 to CollectWarriorDef(
                    id = 22113, getFlag = 4353, rewardFlag = 4354,
                    gainStr = 0L, awards = emptyList(), shineValue = 14,
                )
            ),
            collectStyleDefs = mapOf(
                18 to CollectStyleDef(
                    id = 18,
                    itemIds = listOf(24361, 24362, 24363, 24364, 24365),
                    itemScores = listOf(3, 3, 4, 2, 2, 0),
                    nft = 1,
                )
            ),
            collectStyleValueDefs = emptyList(),
            collectCardDefs = emptyMap(),
            collectCardSortDefs = emptyMap(),
            collectNftDefs = mapOf(
                18249 to CollectNftDef(
                    kind = 2, flag = 1075, warriorId = 18249,
                    getId = 31383, canGet = 0, shineValue = 14,
                ),
                777 to CollectNftDef(
                    kind = 1, flag = 2000, warriorId = 777,
                    getId = 30000, canGet = 1, shineValue = 7,
                ),
            ),
            mountDefs = mapOf(18249 to MountDef(18249, 1075, 0L, 1.0)),
            inventorySystem = mockk(relaxed = true),
            roleCountSystem = mockk(relaxed = true),
            playerStateManager = mockk(relaxed = true),
            statSystem = mockk(relaxed = true),
            repository = repository,
            responseSender = mockk(relaxed = true),
        )

        system.loadCharacter(31L)

        assertEquals(49, system.getNftShine(31L))
    }

    @Test
    fun petTradeGrantUpdatesCollectionRealtime() = runTest {
        val tradeSystem = mockk<TradeSystem>()
        val collection = mockk<CollectionSystem>(relaxed = true)
        val session = mockk<GameSession>(relaxed = true) {
            every { state } returns SessionState.IN_GAME
            every { playerId } returns 900L
            every { characterName } returns "Tester"
        }
        val recipient = mockk<GameSession>(relaxed = true)
        every {
            tradeSystem.confirmOrCancel(900L, 1, TradeSystem.MODE_PET)
        } returns listOf(TradeSystem.PetOwnershipGrant(31L, 22113, recipient))

        TradeHandler(tradeSystem, collection).handle(session, 12, byteArrayOf(1))

        coVerify(exactly = 1) {
            collection.markWarriorOwned(31L, 22113, recipient)
        }
    }
}

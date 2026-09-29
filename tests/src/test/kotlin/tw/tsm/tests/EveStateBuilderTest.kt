package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import tw.tsm.domain.data.EveCondition
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.EveConditionEvaluator
import tw.tsm.gamelogic.systems.EveEventStateMachine
import tw.tsm.gamelogic.systems.EveStateBuilder
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem

/**
 * EveStateBuilder 整合測試。
 */
class EveStateBuilderTest : DescribeSpec({

    fun cond(cls: Int, param: Int, ops: Int, value: Int) = EveCondition(
        conditionNo = 1,
        conditionClass = cls,
        conditionParameter = param,
        conditionParameterStyle = 0,
        conditionOps = ops,
        conditionValue = value,
        conditionSubItem = 0,
        toResult = 0,
        andNum = 1,
        results = emptyList()
    )

    describe("buildPlayerState") {
        it("會帶入 RoleCount 給 conditionClass=18 使用") {
            val charId = 1001L
            val mapId = 10801

            val roleCountRepository = object : RoleCountRepository {
                override suspend fun loadAll(charId: Long): Map<Int, Pair<Int, Int>> =
                    mapOf(
                        163 to (7 to 0),
                        822 to (5 to 10),
                        103 to (1 to 0),
                        106 to (2 to 0)
                    )

                override suspend fun upsert(charId: Long, countIndex: Int, value: Int, maxValue: Int) = Unit

                override suspend fun saveAll(charId: Long, counts: Map<Int, Pair<Int, Int>>) = Unit
            }
            val roleCountSystem = RoleCountSystem(roleCountRepository)
            roleCountSystem.loadCounts(charId)

            val skillRepository = object : SkillRepository {
                override suspend fun findByCharacterId(characterId: Long): Map<Int, Int> =
                    mapOf(14001 to 2)

                override suspend fun saveAll(characterId: Long, skills: Map<Int, Int>) = Unit
            }
            val characterRepository = mockk<CharacterRepository>(relaxed = true)
            coEvery { characterRepository.loadPoints(charId) } returns (125 to 7)
            val pointSystem = PointSystem(characterRepository)
            pointSystem.loadPoints(charId)
            val skillSystem = SkillSystem(skillRepository, characterRepository, emptyMap())
            skillSystem.load(charId, element = 1, skillPoint = 0)

            val inventoryRepository = object : InventoryRepository {
                override suspend fun loadItems(characterId: Long, storageType: Int): Map<Int, ThingData> = emptyMap()
                override suspend fun saveItem(characterId: Long, storageType: Int, slot: Int, thing: ThingData?) = Unit
                override suspend fun saveAllItems(characterId: Long, storageType: Int, items: Map<Int, ThingData>) = Unit
                override suspend fun loadMoney(characterId: Long): Pair<Int, Int> = 250 to 0
                override suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int) = Unit
            }
            val moneySystem = MoneySystem(inventoryRepository)
            moneySystem.loadMoney(charId)

            val questRepository = mockk<QuestRepository>()
            coEvery { questRepository.getMissions(charId) } returns emptyList()
            coEvery { questRepository.getMissionFlags(charId) } returns emptyList()
            coEvery { questRepository.getCompletedEventCounts(charId, mapId) } returns emptyMap()

            val inventorySystem = mockk<InventorySystem>()
            every { inventorySystem.getBag(charId) } returns mapOf(
                1 to ThingData(itemId = 1001, quantity = 1),
                2 to ThingData(itemId = 1002, quantity = 3)
            )
            every { inventorySystem.getBank(charId) } returns mapOf(
                1 to ThingData(itemId = 29348, quantity = 2)
            )
            every { inventorySystem.getStorage(charId) } returns mapOf(
                1 to ThingData(itemId = 29348, quantity = 3)
            )
            every { inventorySystem.getEquipment(charId) } returns emptyMap()

            val petSystem = mockk<PetSystem>()
            every { petSystem.getFollowPets(charId) } returns emptyMap()
            every { petSystem.getInnPets(charId) } returns emptyMap()
            every { petSystem.getCartPets(charId) } returns emptyMap()

            val session = mockk<GameSession>()
            every { session.sex } returns 2
            every { session.level } returns 30
            every { session.serverId } returns 97
            every { session.mountNpcId } returns 7001

            val builder = EveStateBuilder(
                questSystem = QuestSystem(questRepository, emptyMap()),
                inventorySystem = inventorySystem,
                petSystem = petSystem,
                stateMachine = EveEventStateMachine(),
                playerStateManager = PlayerStateManager(),
                markDefs = emptyMap(),
                roleCountSystem = roleCountSystem,
                skillSystem = skillSystem,
                moneySystem = moneySystem,
                questRepository = questRepository,
                pointSystem = pointSystem
            )

            val state = builder.buildPlayerState(charId, mapId, session)

            state.roleCountValues shouldBe mapOf(
                163 to 7,
                822 to 5,
                103 to 1,
                106 to 2
            )
            state.bagUsedSlots shouldBe 2
            state.storedItems shouldBe mapOf(29348 to 5)
            state.bagCapacity shouldBe 65
            state.isMounted shouldBe true
            state.sex shouldBe 2
            state.level shouldBe 30
            state.serverId shouldBe 97
            state.realPoint shouldBe 125
            state.skillLevels shouldBe mapOf(14001 to 2)
            state.gold shouldBe 250
            state.npcSecondReincarnationStatus shouldBe -1
            EveConditionEvaluator.evaluate(cond(cls = 18, param = 822, ops = 5, value = 5), state) shouldBe true
        }

        it("會依 Lua 武將二轉資格產生 class=15 param=1 狀態碼") {
            val charId = 1002L
            val mapId = 59102

            val roleCountRepository = object : RoleCountRepository {
                override suspend fun loadAll(charId: Long): Map<Int, Pair<Int, Int>> = emptyMap()
                override suspend fun upsert(charId: Long, countIndex: Int, value: Int, maxValue: Int) = Unit
                override suspend fun saveAll(charId: Long, counts: Map<Int, Pair<Int, Int>>) = Unit
            }
            val roleCountSystem = RoleCountSystem(roleCountRepository)
            roleCountSystem.loadCounts(charId)

            val skillRepository = object : SkillRepository {
                override suspend fun findByCharacterId(characterId: Long): Map<Int, Int> = emptyMap()
                override suspend fun saveAll(characterId: Long, skills: Map<Int, Int>) = Unit
            }
            val characterRepository = mockk<CharacterRepository>(relaxed = true)
            val skillSystem = SkillSystem(skillRepository, characterRepository, emptyMap())
            skillSystem.load(charId, element = 1, skillPoint = 0)

            val inventoryRepository = object : InventoryRepository {
                override suspend fun loadItems(characterId: Long, storageType: Int): Map<Int, ThingData> = emptyMap()
                override suspend fun saveItem(characterId: Long, storageType: Int, slot: Int, thing: ThingData?) = Unit
                override suspend fun saveAllItems(characterId: Long, storageType: Int, items: Map<Int, ThingData>) = Unit
                override suspend fun loadMoney(characterId: Long): Pair<Int, Int> = 0 to 0
                override suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int) = Unit
            }
            val moneySystem = MoneySystem(inventoryRepository)
            moneySystem.loadMoney(charId)

            val questRepository = mockk<QuestRepository>()
            coEvery { questRepository.getMissions(charId) } returns emptyList()
            coEvery { questRepository.getMissionFlags(charId) } returns emptyList()
            coEvery { questRepository.getCompletedEventCounts(charId, mapId) } returns emptyMap()

            val inventorySystem = mockk<InventorySystem>()
            every { inventorySystem.getBag(charId) } returns emptyMap()
            every { inventorySystem.getBank(charId) } returns emptyMap()
            every { inventorySystem.getStorage(charId) } returns emptyMap()
            every { inventorySystem.getEquipment(charId) } returns emptyMap()
            every { inventorySystem.hasNpcSecondReincarnationToken(charId, 22081) } returns true

            val petSystem = mockk<PetSystem>()
            every { petSystem.getFollowPets(charId) } returns mapOf(
                1 to FollowNpcData(slotIndex = 1, npcId = 22081, level = 60, loyalty = 100)
            )
            every { petSystem.getInnPets(charId) } returns emptyMap()
            every { petSystem.getCartPets(charId) } returns emptyMap()

            val builder = EveStateBuilder(
                questSystem = QuestSystem(questRepository, emptyMap()),
                inventorySystem = inventorySystem,
                petSystem = petSystem,
                stateMachine = EveEventStateMachine(),
                playerStateManager = PlayerStateManager(),
                markDefs = emptyMap(),
                roleCountSystem = roleCountSystem,
                skillSystem = skillSystem,
                moneySystem = moneySystem,
                questRepository = questRepository
            )

            val state = builder.buildPlayerState(charId, mapId)

            state.npcSecondReincarnationStatus shouldBe 0
            EveConditionEvaluator.evaluate(cond(cls = 15, param = 1, ops = 5, value = 0), state) shouldBe true
        }
    }
})

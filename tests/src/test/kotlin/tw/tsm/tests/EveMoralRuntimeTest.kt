package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.*

class EveMoralRuntimeTest : DescribeSpec({
    describe("DVT Eve Moral runtime") {
        it("Wu delta persists then updates runtime and S008-014") {
            val session = mockk<GameSession>()
            every { session.roleId } returns 31
            every { session.characterName } returns "MoralTester"

            val response = mockk<ResponseSender>(relaxed = true)
            val repository = mockk<CharacterRepository>()
            coEvery { repository.updateMoral(31L, 3, 320) } returns Unit
            val states = PlayerStateManager()
            states.initPlayer(
                characterId = 31L,
                hp = 100,
                sp = 50,
                maxHp = 100,
                maxSp = 50,
                level = 20,
                atk = 10,
                def = 10,
                agi = 10,
                intelligence = 10,
                hpx = 0,
                spx = 0,
                element = 1,
                moral = intArrayOf(10, 20, 200, 40, 50)
            )

            val executor = EveResultExecutor(
                responseSender = response,
                questSystem = mockk<QuestSystem>(relaxed = true),
                inventorySystem = mockk<InventorySystem>(relaxed = true),
                petSystem = mockk<PetSystem>(relaxed = true),
                skillSystem = mockk<SkillSystem>(relaxed = true),
                moneySystem = mockk<MoneySystem>(relaxed = true),
                npcDefs = emptyMap(),
                markDefs = emptyMap(),
                markFlagToGuideId = emptyMap(),
                eveData = emptyMap(),
                mountSystem = mockk<MountSystem>(relaxed = true),
                achievementSystem = mockk<AchievementSystem>(relaxed = true),
                bitFlagSystem = mockk<BitFlagSystem>(relaxed = true),
                roleCountSystem = mockk<RoleCountSystem>(relaxed = true),
                dailyProgressSystem = null,
                pointSystem = null,
                collectionSystem = null,
                playerStateManager = states,
                characterRepository = repository,
            )

            val result = executor.executeResultClass(
                session,
                EveResult(
                    resultGroupNo = 0,
                    resultNo = 1,
                    resultType = 0,
                    resultClass = 7,
                    parameter = 2,
                    parameterStyle = 3,
                    resultValue = 120,
                    resultMeanNo = 0,
                )
            )

            result shouldBe true
            states.getMoral(31L)[2] shouldBe 320
            coVerify(exactly = 1) { repository.updateMoral(31L, 3, 320) }
            verify(exactly = 1) {
                response.sendWorldAttribute(session, EAttribute.MORAL, 320L, true, 3)
            }
        }
    }
})

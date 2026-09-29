package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.AchievementSystem
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.EveResultExecutor
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SkillSystem

class EvePointPurchaseTest : DescribeSpec({
    describe("DVT Eve direct Point purchase") {
        it("scene12180 shape 131171/10 trừ 10 Point và trao item31171 x1") {
            val session = mockk<GameSession>()
            every { session.roleId } returns 77
            every { session.characterName } returns "Tester"

            val response = mockk<ResponseSender>(relaxed = true)
            val inventory = mockk<InventorySystem>()
            val points = mockk<PointSystem>()

            every { points.getRealPoint(77L) } returns 10
            every { points.subtractRealPoint(77L, 10) } returns true
            every { points.getPoints(77L) } returns (0 to 3)
            coEvery { points.savePoints(77L) } returns Unit

            every { inventory.canAddItemToBag(77L, 31171, 1) } returns true
            val thing = ThingData(itemId = 31171, quantity = 1)
            coEvery { inventory.addItemsToBagExact(77L, 31171, 1) } returns listOf(4 to thing)

            val executor = EveResultExecutor(
                responseSender = response,
                questSystem = mockk<QuestSystem>(relaxed = true),
                inventorySystem = inventory,
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
                pointSystem = points,
            )

            executor.executeResultClass(
                session,
                EveResult(
                    resultGroupNo = 0,
                    resultNo = 1,
                    resultType = 0,
                    resultClass = 7,
                    parameter = 0,
                    parameterStyle = 3,
                    resultValue = 131171,
                    resultMeanNo = 10,
                )
            )

            verify(exactly = 1) { points.subtractRealPoint(77L, 10) }
            coVerify(exactly = 1) { inventory.addItemsToBagExact(77L, 31171, 1) }
            coVerify(exactly = 1) { points.savePoints(77L) }
            verify(exactly = 1) { response.sendSetBagItem(session, 4, thing, true) }
            verify(exactly = 1) { response.sendPointBalance(session, 0, 3) }
        }

        it("không đủ Point thì không thêm vật phẩm") {
            val session = mockk<GameSession>()
            every { session.roleId } returns 77
            every { session.characterName } returns "Tester"

            val response = mockk<ResponseSender>(relaxed = true)
            val inventory = mockk<InventorySystem>(relaxed = true)
            val points = mockk<PointSystem>()
            every { points.getRealPoint(77L) } returns 9

            val executor = EveResultExecutor(
                responseSender = response,
                questSystem = mockk<QuestSystem>(relaxed = true),
                inventorySystem = inventory,
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
                pointSystem = points,
            )

            executor.executeResultClass(
                session,
                EveResult(
                    resultGroupNo = 0,
                    resultNo = 1,
                    resultType = 0,
                    resultClass = 7,
                    parameter = 0,
                    parameterStyle = 3,
                    resultValue = 131171,
                    resultMeanNo = 10,
                )
            )

            verify(exactly = 0) { points.subtractRealPoint(any(), any()) }
            coVerify(exactly = 0) { inventory.addItemsToBagExact(any(), any(), any()) }
            verify(exactly = 0) { response.sendPointBalance(any(), any(), any()) }
        }
    }
})

package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.StatSystem

class SacredEquipPrimaryD8A22PetBattleTest {
    private fun myth(primaryNo: Int): ItemDef = mockk {
        every { kind } returns EItemKind.MYTH
        every { attr1Kind } returns primaryNo
        every { attr1Value } returns 0
        every { attr2Kind } returns 0
        every { attr2Value } returns 0
    }

    @Test
    fun petBattleSnapshotAppliesMythHpAndIntPercent() {
        val stat = mockk<StatSystem>(relaxed = true) {
            every { calculateMaxHp(any(), any(), any(), any(), any(), any(), any()) } returns 1000L
            every { calculateMaxSp(any(), any(), any(), any(), any(), any(), any()) } returns 100
        }
        val npc = mockk<NpcDef>(relaxed = true) {
            every { turn } returns 0
            every { element } returns 1
            every { name } returns "D8Pet"
            every { skills } returns intArrayOf(0, 0, 0)
            every { specialSkill } returns 0
            every { upgradeSkill } returns 0
        }
        val sacred = mapOf(
            97 to SacredEquipPrimaryDef(97, EAttribute.HP_PERCENT, List(16) { 10 }),
            55 to SacredEquipPrimaryDef(55, EAttribute.INT_PERCENT, List(16) { 20 })
        )
        val items = mapOf(
            70001 to myth(97),
            70002 to myth(55)
        )
        val manager = BattleManager(
            responseSender = mockk<ResponseSender>(relaxed = true),
            skillDefs = emptyMap(),
            npcDefs = mapOf(100 to npc),
            playerStateManager = PlayerStateManager(),
            inventorySystem = mockk<InventorySystem>(relaxed = true),
            petSystem = mockk<PetSystem>(relaxed = true),
            moneySystem = mockk<MoneySystem>(relaxed = true),
            statSystem = stat,
            scope = mockk<CoroutineScope>(relaxed = true),
            itemDefs = items,
            sacredEquipPrimaryDefs = sacred
        )
        val pet = FollowNpcData(
            slotIndex = 1,
            npcId = 100,
            name = "D8Pet",
            level = 1,
            hp = 1000L,
            sp = 100,
            intelligence = 100,
            equips = mapOf(
                1 to ThingData(itemId = 70001, quantity = 1, mythLevel = 0),
                2 to ThingData(itemId = 70002, quantity = 1, mythLevel = 0)
            )
        )

        val method = BattleManager::class.java.getDeclaredMethod(
            "createFollowNpcFighter",
            NpcDef::class.java,
            FollowNpcData::class.java,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE,
            Integer.TYPE
        )
        method.isAccessible = true
        val fighter = method.invoke(manager, npc, pet, 900L, 77L, 100, 1, 1, 2) as BattleFighter

        assertEquals(1100L, fighter.maxHp)
        assertEquals(120, fighter.intelligence)
    }
}

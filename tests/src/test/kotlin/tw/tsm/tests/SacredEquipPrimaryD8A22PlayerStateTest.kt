package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.util.DvtPercentMath
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.EquipBonusCalculator
import tw.tsm.gamelogic.systems.PlayerStateManager

class SacredEquipPrimaryD8A22PlayerStateTest {
    private fun myth(primaryNo: Int): ItemDef = mockk {
        every { kind } returns EItemKind.MYTH
        every { attr1Kind } returns primaryNo
        every { attr1Value } returns 0
        every { attr2Kind } returns 0
        every { attr2Value } returns 0
    }

    @Test
    fun mythMainHpAndIntPercentAggregateDirectly() {
        val sacred = mapOf(
            97 to SacredEquipPrimaryDef(97, EAttribute.HP_PERCENT, List(16) { 10 }),
            55 to SacredEquipPrimaryDef(55, EAttribute.INT_PERCENT, List(16) { 7 })
        )
        val bonus = EquipBonusCalculator.calculateTotalBonus(
            equips = mapOf(
                1 to ThingData(itemId = 60001, quantity = 1, mythLevel = 4),
                2 to ThingData(itemId = 60002, quantity = 1, mythLevel = 9)
            ),
            itemDefs = mapOf(60001 to myth(97), 60002 to myth(55)),
            sacredEquipPrimaryDefs = sacred
        )

        assertEquals(10, bonus.hpPercent)
        assertEquals(7, bonus.intPercent)
    }

    @Test
    fun maxHpPercentUsesLuaFinalFloorAndEquipRefreshStoresPercentState() {
        assertEquals(1101L, DvtPercentMath.applyFinalPercentFloor(1001L, 10))
        assertEquals(116, DvtPercentMath.addBasePercentRound(105, 10))

        val stat = object : StatCalculator {
            override fun calculateMaxHp(
                level: Int, hpx: Int, equipMaxHp: Int, extraHp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Long = 1001L

            override fun calculateMaxSp(
                level: Int, spx: Int, equipMaxSp: Int, extraSp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Int = 100
        }

        val states = PlayerStateManager()
        states.initPlayer(
            characterId = 1L, hp = 500L, sp = 50, maxHp = 1001L, maxSp = 100,
            level = 1, atk = 10, def = 10, agi = 10, intelligence = 10,
            hpx = 0, spx = 0, element = 1
        )
        val result = states.updateEquipBonus(
            1L, EquipBonusData(hpPercent = 10, intPercent = 7), stat
        )!!

        assertEquals(1101L, result.newMaxHp)
        assertEquals(10, states.getState(1L)!!.hpPercent)
        assertEquals(7, states.getState(1L)!!.intPercent)
    }

    @Test
    fun equipRefreshKeepsAstrolabeAndCollectionInputsBeforeHpPercent() {
        val stat = object : StatCalculator {
            override fun calculateMaxHp(
                level: Int, hpx: Int, equipMaxHp: Int, extraHp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Long = 1000L + hpx + equipMaxHp

            override fun calculateMaxSp(
                level: Int, spx: Int, equipMaxSp: Int, extraSp: Int,
                turn: Int, career: Int, turn3Lv: Int
            ): Int = 100 + spx + equipMaxSp
        }

        val states = PlayerStateManager()
        states.initPlayer(
            characterId = 2L, hp = 500L, sp = 50, maxHp = 1000L, maxSp = 100,
            level = 1, atk = 10, def = 10, agi = 10, intelligence = 10,
            hpx = 0, spx = 0, element = 1,
            astrolabeBonus = tw.tsm.domain.valueobjects.AstrolabeBonusData(hpx = 5, spx = 4)
        )

        val result = states.updateEquipBonus(
            2L,
            EquipBonusData(equipHpx = 3, equipSpx = 2, hpPercent = 10),
            stat,
            collectionHpx = 7,
            collectionSpx = 6
        )!!

        // base HP = 1000 + (3 equip + 5 astrolabe + 7 collection) = 1015; floor(1015*1.10)=1116.
        assertEquals(1116L, result.newMaxHp)
        assertEquals(112, result.newMaxSp)
    }
}

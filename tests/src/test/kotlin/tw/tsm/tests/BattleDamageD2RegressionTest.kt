package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.enums.Element
import tw.tsm.gamelogic.battle.BattleDamageCalc
import tw.tsm.gamelogic.battle.ElementTable

class BattleDamageD2RegressionTest {
    @Test
    fun `classic element multipliers match PC baseline`() {
        assertEquals(1.2, ElementTable.getMultiplier(Element.EARTH, Element.WATER))
        assertEquals(0.8, ElementTable.getMultiplier(Element.EARTH, Element.WIND))
        assertEquals(1.2, ElementTable.getMultiplier(Element.WATER, Element.FIRE))
        assertEquals(0.8, ElementTable.getMultiplier(Element.WATER, Element.EARTH))
        assertEquals(1.2, ElementTable.getMultiplier(Element.FIRE, Element.WIND))
        assertEquals(0.8, ElementTable.getMultiplier(Element.FIRE, Element.WATER))
        assertEquals(1.2, ElementTable.getMultiplier(Element.WIND, Element.EARTH))
        assertEquals(0.8, ElementTable.getMultiplier(Element.WIND, Element.FIRE))
        assertEquals(1.0, ElementTable.getMultiplier(Element.EARTH, Element.FIRE))
    }
    private fun finalDamage(
        reduced: Double,
        element: Double = 1.0,
        reborn: Double = 1.0,
        variance: Int = 0,
        pvpOrPet: Boolean = false,
        defending: Boolean = false
    ): Int {
        val method = BattleDamageCalc::class.java.getDeclaredMethod(
            "calculateFinalDamage",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(
            BattleDamageCalc, reduced, element, reborn, variance,
            pvpOrPet, defending
        ) as Int
    }
    @Test
    fun `variance is absolute minus two through plus one after element and reborn`() {
        assertEquals(118, finalDamage(100.0, element = 1.2, variance = -2))
        assertEquals(120, finalDamage(100.0, element = 1.2, variance = 0))
        assertEquals(121, finalDamage(100.0, element = 1.2, variance = 1))
        assertEquals(78, finalDamage(100.0, element = 0.8, variance = -2))
        assertEquals(81, finalDamage(100.0, element = 0.8, variance = 1))
    }

    @Test
    fun `pvp division happens after absolute variance`() {
        assertEquals(60, finalDamage(
            reduced = 100.0,
            element = 1.2,
            variance = 1,
            pvpOrPet = true
        ))
    }
    @Test
    fun `defending while element disadvantaged follows PC one damage rule`() {
        assertEquals(1, finalDamage(
            reduced = 1000.0,
            element = 0.8,
            variance = 1,
            defending = true
        ))
    }

    @Test
    fun `damage never drops below one`() {
        assertEquals(1, finalDamage(
            reduced = -500.0,
            element = 1.2,
            variance = -2
        ))
    }
}

package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.WelfareItemExchangePlanner

class WelfareItemExchangePlannerTest {
    private val defs by lazy {
        val p = Path.of("File.dat", "Data-ios", "IOS_Item_C.dat")
        ItemDatLoader.load(Files.readAllBytes(if (Files.exists(p)) p else Path.of("..").resolve(p)))
    }
    @Test fun originalRecipe107ConsumesIngredientsAndCreatesOutput() {
        val bag = mapOf(0 to ThingData(itemId=55403,quantity=350))
        val change = WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to 150),55411 to 1,2,defs)
        assertNotNull(change)
        assertEquals(50,change!![0]!!.quantity)
        assertEquals(55411,change[1]!!.itemId)
        assertEquals(2,change[1]!!.quantity)
        assertEquals(350,bag[0]!!.quantity)
    }
    @Test fun insufficientIngredientLeavesBagUnchanged() {
        val bag=mapOf(0 to ThingData(itemId=55403,quantity=149))
        assertNull(WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to 150),55411 to 1,1,defs))
        assertEquals(149,bag[0]!!.quantity)
    }
    @Test fun lockedIngredientRejected() {
        val bag=mapOf(0 to ThingData(itemId=55403,quantity=150,isLocked=true))
        assertNull(WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to 150),55411 to 1,1,defs))
    }
    @Test fun fullyConsumedIngredientReleasesBagSlot() {
        val bag=mapOf(0 to ThingData(itemId=55403,quantity=150))
        val change=WelfareItemExchangePlanner.plan(bag,1,listOf(55403 to 150),55411 to 1,1,defs)
        assertNotNull(change)
        assertEquals(55411,change!![0]!!.itemId)
        assertEquals(55403,bag[0]!!.itemId)
    }
    @Test fun partialSpendCannotCreateSlotInFullBag() {
        val bag=mapOf(0 to ThingData(itemId=55403,quantity=200))
        assertNull(WelfareItemExchangePlanner.plan(bag,1,listOf(55403 to 150),55411 to 1,1,defs))
    }
    @Test fun overflowAndInvalidCountRejected() {
        val bag=mapOf(0 to ThingData(itemId=55403,quantity=999))
        assertNull(WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to Int.MAX_VALUE),55411 to 1,50,defs))
        assertNull(WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to 1),55411 to 1,51,defs))
        assertNull(WelfareItemExchangePlanner.plan(bag,20,listOf(55403 to 1),55411 to Int.MAX_VALUE,2,defs))
    }
}

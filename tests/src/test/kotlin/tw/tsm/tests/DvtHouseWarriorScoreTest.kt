package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.HouseWarriorPolicy
import tw.tsm.domain.data.HouseFurnitureDef
import tw.tsm.domain.interfaces.repositories.HouseFurniturePlacement
import tw.tsm.data.loaders.HouseFurnitureDatLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtHouseWarriorScoreTest {
    private val thresholds = mapOf(1 to 52, 2 to 90, 3 to 225, 4 to 630)
    @Test fun scoreLimitsWarriorSlotsFromOriginalDvt() {
        assertEquals(0, HouseWarriorPolicy.slots(51, thresholds))
        assertEquals(1, HouseWarriorPolicy.slots(52, thresholds))
        assertEquals(2, HouseWarriorPolicy.slots(90, thresholds))
        assertEquals(3, HouseWarriorPolicy.slots(225, thresholds))
        assertEquals(4, HouseWarriorPolicy.slots(630, thresholds))
        assertEquals(0, HouseWarriorPolicy.slots(65535, emptyMap()))
    }

    @Test fun rejectsUnownedUnderlevelDuplicateAndExcessAssignments() {
        val ownedEligible = setOf(123, 456)
        assertTrue(HouseWarriorPolicy.valid(listOf(123, 456, 0, 0), 2, ownedEligible))
        assertFalse(HouseWarriorPolicy.valid(listOf(123, 456, 999, 0), 2, ownedEligible))
        assertFalse(HouseWarriorPolicy.valid(listOf(123, 123, 0, 0), 2, ownedEligible))
        assertFalse(HouseWarriorPolicy.valid(listOf(123, 456, 0, 0), 1, ownedEligible))
        assertFalse(HouseWarriorPolicy.valid(listOf(123, 456, 0, 0), 2, setOf(123)))
    }

    @Test fun loadHidesFormerlyOwnedWarriorsAndLostScoreSlots() {
        assertEquals(listOf(123, 0, 0, 0), HouseWarriorPolicy.visible(
            listOf(123, 456, 0, 0), 1, setOf(123, 456)
        ))
        assertEquals(listOf(0, 0, 0, 0), HouseWarriorPolicy.visible(
            listOf(123, 456, 0, 0), 2, emptySet()
        ))
        val score = HouseWarriorPolicy.floorScore(
            listOf(HouseFurniturePlacement(7, 1, 1, 0, 0)),
            mapOf(7 to HouseFurnitureDef(7, 1, 2, 1, 1, 52))
        )
        assertEquals(52, score)
    }

    @Test fun thresholdFileMatchesExactThreeByteRows() {
        val bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(4)
        listOf(52, 90, 225, 630).forEachIndexed { idx, v ->
            bb.put((idx + 1).toByte())
            bb.putShort(v.toShort())
        }
        assertEquals(thresholds, HouseFurnitureDatLoader.loadWarriorThresholds(bb.array()))
    }
}

package tw.tsm.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import tw.tsm.gamelogic.systems.OfflineExpConversionPolicy as Policy
import tw.tsm.protocol.PacketReader

class OfflineExpConversionPolicyTest {
    private val verifiedTestFixture = Policy.Formula(5,50,70,100,1_000,1)
    @Test fun noUnverifiedDefaultIsActivated() {
        assertNull(Policy.fromEnvironment(emptyMap()))
        assertNull(Policy.fromEnvironment(mapOf("DVT_OFFLINE_EXP_ENABLED" to "true")))
        assertNull(Policy.fromEnvironment(mapOf(
            "DVT_OFFLINE_EXP_ENABLED" to "true",
            "DVT_OFFLINE_EXP_BP_PER_MIN" to "5",
            "DVT_OFFLINE_EXP_FREE_RATE" to "50"
        )))
    }

    @Test fun correctlyQuotesThreeClientTiers() {
        val offers=Policy.offers(480,verifiedTestFixture)
        assertEquals(listOf(30,28,29),offers.map { it.sellId })
        assertEquals(listOf(50,70,100),offers.map { it.ratePercent })
        assertEquals(listOf(0L,5_000L,5L),offers.map { it.cost })
        assertEquals(5,Policy.offers(401,verifiedTestFixture)[2].cost)
        assertEquals(1,Policy.offers(1,verifiedTestFixture)[2].cost)
    }

    @Test fun wirePacketMatchesDvtClientReader() {
        val packet=Policy.packetForOffers(Policy.offers(480,verifiedTestFixture))
        assertEquals(22,packet.size)
        assertEquals(87,packet[0].toInt() and 255)
        assertEquals(3,packet[1].toInt() and 255)
        assertEquals(0,packet[2].toInt() and 255)
        val reader=PacketReader(packet.copyOfRange(3,packet.size))
        assertEquals(3,reader.readUByte())
        assertEquals(50,reader.readUShortLE())
        assertEquals(0L,reader.readUIntLE())
        assertEquals(70,reader.readUShortLE())
        assertEquals(5_000L,reader.readUIntLE())
        assertEquals(100,reader.readUShortLE())
        assertEquals(5L,reader.readUIntLE())
        assertEquals(0,reader.remaining)
    }

    @Test fun earnedPercentMatchesTestFixtureWithoutFloatingError() {
        assertEquals(0,Policy.earnedWholePercent(19,verifiedTestFixture))
        assertEquals(1,Policy.earnedWholePercent(20,verifiedTestFixture))
        assertEquals(24,Policy.earnedWholePercent(480,verifiedTestFixture))
        assertEquals(72,Policy.earnedWholePercent(1440,verifiedTestFixture))
    }

    @Test fun rewardIsFractionOfLevelThresholdWithOverflowProtection() {
        val offers=Policy.offers(480,verifiedTestFixture)
        assertEquals(120_000L,Policy.rewardExp(1_000_000,480,offers[0],verifiedTestFixture))
        assertEquals(168_000L,Policy.rewardExp(1_000_000,480,offers[1],verifiedTestFixture))
        assertEquals(240_000L,Policy.rewardExp(1_000_000,480,offers[2],verifiedTestFixture))
        assertTrue(Policy.rewardExp(Long.MAX_VALUE,1440,offers[2],verifiedTestFixture)>0)
    }

    @Test fun invalidEconomicsAreRejectedBeforeQuoting() {
        assertThrows(IllegalArgumentException::class.java) {
            Policy.Formula(0,50,70,100,1000,1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Policy.Formula(5,70,50,100,1000,1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Policy.offers(0,verifiedTestFixture)
        }
    }
}

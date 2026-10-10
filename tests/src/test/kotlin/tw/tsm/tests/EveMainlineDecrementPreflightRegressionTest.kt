package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.EveMissionPreflightPolicy

class EveMainlineDecrementPreflightRegressionTest {
    private fun result(mission: Int, style: Int, value: Int) =
        EveResult(0, 1, 0, 2, mission, style, value, 0)

    @Test fun `missing mission decrement must be rejected before emitting client packet`() {
        val rejection = EveMissionPreflightPolicy.validate(
            listOf(result(12282, 2, 1), result(12283, 1, 1)), emptyMap()
        )
        assertEquals(EveMissionPreflightPolicy.Rejection(
            12282, EveMissionPreflightPolicy.Failure.MISSING_MISSION
        ), rejection)
    }

    @Test fun `ordered double decrement cannot underflow after the first consumes mission`() {
        val rejection = EveMissionPreflightPolicy.validate(
            listOf(result(12282, 2, 1), result(12282, 2, 1)), mapOf(12282 to 1)
        )
        assertEquals(EveMissionPreflightPolicy.Failure.MISSING_MISSION, rejection?.reason)
    }

    @Test fun `legitimate same chain add and decrement is valid`() {
        assertNull(EveMissionPreflightPolicy.validate(
            listOf(result(12282, 1, 12), result(12282, 2, 1)), emptyMap()
        ))
    }

    @Test fun `decrement of an active mission preserves normal completion behavior`() {
        assertNull(EveMissionPreflightPolicy.validate(
            listOf(result(12282, 2, 12)), mapOf(12282 to 12)
        ))
    }

    @Test fun `mission overflow rejected without applying following steps`() {
        assertEquals(EveMissionPreflightPolicy.Failure.INVALID_STEP,
            EveMissionPreflightPolicy.validate(
                listOf(result(12282, 1, 2), result(12283, 1, 1)),
                mapOf(12282 to 254)
            )?.reason
        )
    }

    @Test fun `completion followed by decrement may not reuse a removed step`() {
        assertEquals(EveMissionPreflightPolicy.Failure.MISSING_MISSION,
            EveMissionPreflightPolicy.validate(
                listOf(result(12282, 3, 0), result(12282, 2, 1)),
                mapOf(12282 to 12)
            )?.reason
        )
    }

    @Test fun `zero decrement is safely an idempotent no-op`() {
        assertNull(EveMissionPreflightPolicy.validate(
            listOf(result(12282, 2, 0)), emptyMap()
        ))
    }
}

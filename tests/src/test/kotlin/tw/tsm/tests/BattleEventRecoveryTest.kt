package tw.tsm.tests

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.BattleEventContinuation

class BattleEventRecoveryTest {
    @Test fun missingRuntimeClassReleasesEventExactlyOnce() = runTest {
        var aborted = 0
        BattleEventContinuation.run({ throw NoClassDefFoundError("EveMissionResultOperation") }, { aborted++ })
        assertEquals(1, aborted)
    }
    @Test fun ordinaryCallbackFailureAlsoReleasesEvent() = runTest {
        var aborted = 0
        BattleEventContinuation.run({ throw IllegalStateException("bad branch") }, { aborted++ })
        assertEquals(1, aborted)
    }
    @Test fun successfulContinuationKeepsItsDialogOrWarp() = runTest {
        var continued = false
        BattleEventContinuation.run({ continued = true }, { fail("must not abort successful branch") })
        assertTrue(continued)
    }
    @Test fun coroutineCancellationIsNotSwallowed() = runTest {
        var aborted = false
        try {
            BattleEventContinuation.run({ throw CancellationException("disconnect") }, { aborted = true })
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(aborted)
    }
    @Test fun fatalJvmErrorIsNotSwallowed() = runTest {
        var aborted = false
        try {
            BattleEventContinuation.run({ throw OutOfMemoryError("test only") }, { aborted = true })
            fail("fatal JVM error must propagate")
        } catch (_: OutOfMemoryError) { }
        assertFalse(aborted)
    }
}

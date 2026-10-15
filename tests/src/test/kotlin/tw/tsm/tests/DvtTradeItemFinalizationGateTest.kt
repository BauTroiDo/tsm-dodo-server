package tw.tsm.tests

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Abort
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Confirmation
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Phase
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Settlement
import java.util.concurrent.atomic.AtomicInteger

/** An isolated proposed C025 completion fence. Never touches live TradeSystem. */
class DvtTradeItemFinalizationGateTest {
    private val first = 991101L
    private val second = 991102L

    private fun ready(): TradeItemFinalizationGate {
        val gate = TradeItemFinalizationGate(first, second)
        assertEquals(Confirmation.WAITING_FOR_PARTNER, gate.confirm(first))
        assertEquals(Confirmation.START_SETTLEMENT, gate.confirm(second))
        return gate
    }

    @Test
    fun twoConcurrentConfirmationsElectExactlyOneCommitAndIgnoreReplays() = runBlocking {
        val gate = TradeItemFinalizationGate(first, second)
        assertEquals(Confirmation.IGNORED, gate.confirm(7L))
        val decisions = (0 until 20).map { i ->
            async(Dispatchers.Default) {
                gate.confirm(if (i % 2 == 0) first else second)
            }
        }.awaitAll()
        assertEquals(1, decisions.count { it == Confirmation.START_SETTLEMENT })
        assertEquals(1, decisions.count { it == Confirmation.WAITING_FOR_PARTNER })
        assertEquals(18, decisions.count { it == Confirmation.IGNORED })
        assertEquals(Confirmation.IGNORED, gate.confirm(first))
        assertEquals(Phase.SETTLING, gate.currentPhase())

        val commits = AtomicInteger()
        val success = AtomicInteger()
        val attempts = (0 until 12).map {
            async(Dispatchers.Default) {
                gate.settleOnce(
                    commitAndPublish = { commits.incrementAndGet(); true },
                    notifySuccess = { success.incrementAndGet() },
                )
            }
        }.awaitAll()
        assertEquals(1, attempts.count { it == Settlement.COMMITTED })
        assertEquals(11, attempts.count { it == Settlement.NOT_ELECTED })
        assertEquals(1, commits.get())
        assertEquals(1, success.get())
        assertEquals(Phase.COMMITTED, gate.currentPhase())
    }

    @Test
    fun cancelBeforeBothConfirmStopsSqlAndCannotBeReopened() = runBlocking {
        val gate = TradeItemFinalizationGate(first, second)
        assertEquals(Confirmation.WAITING_FOR_PARTNER, gate.confirm(first))
        assertEquals(Abort.NOT_PARTICIPANT, gate.cancelOrDisconnect(8L))
        assertEquals(Abort.CANCELLED, gate.cancelOrDisconnect(second))
        assertEquals(Confirmation.IGNORED, gate.confirm(second))
        assertEquals(Phase.CANCELLED, gate.currentPhase())
        var commits = 0
        var notifications = 0
        assertEquals(
            Settlement.NOT_ELECTED,
            gate.settleOnce({ commits++; true }, { notifications++ }),
        )
        assertEquals(0, commits)
        assertEquals(0, notifications)
    }

    @Test
    fun successIsNeverSignalledBeforeCommitAndBothCachesHaveBeenPublished() = runBlocking {
        val gate = ready()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val result = async(Dispatchers.Default) {
            gate.settleOnce(
                commitAndPublish = {
                    entered.complete(Unit)
                    release.await()
                    synchronized(events) { events += "sql-and-both-caches" }
                    true
                },
                notifySuccess = { synchronized(events) { events += "success-packet" } },
            )
        }
        withTimeout(3_000) { entered.await() }
        assertEquals(Abort.COMMIT_IN_FLIGHT, gate.cancelOrDisconnect(first))
        assertEquals(Abort.COMMIT_IN_FLIGHT, gate.cancelOrDisconnect(second))
        assertTrue(synchronized(events) { events.isEmpty() })
        release.complete(Unit)
        assertEquals(Settlement.COMMITTED, withTimeout(3_000) { result.await() })
        assertEquals(listOf("sql-and-both-caches", "success-packet"), events)
        assertEquals(Phase.COMMITTED, gate.currentPhase())
        assertEquals(Abort.ALREADY_TERMINAL, gate.cancelOrDisconnect(first))
    }

    @Test
    fun disconnectCancellationMidSqlStillFinishesCommitAndNotifiesOnce() = runBlocking {
        val gate = ready()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val committed = AtomicInteger()
        val notified = AtomicInteger()
        val work = async(Dispatchers.Default) {
            gate.settleOnce(
                commitAndPublish = {
                    entered.complete(Unit)
                    release.await()
                    committed.incrementAndGet()
                    true
                },
                notifySuccess = { notified.incrementAndGet() },
            )
        }
        withTimeout(3_000) { entered.await() }
        work.cancel()
        assertEquals(Abort.COMMIT_IN_FLIGHT, gate.cancelOrDisconnect(second))
        release.complete(Unit)
        withTimeout(3_000) { work.join() }
        assertEquals(1, committed.get())
        assertEquals(1, notified.get())
        assertEquals(Phase.COMMITTED, gate.currentPhase())
    }

    @Test
    fun sqlRejectionAndSqlExceptionNeverEmitSuccess() = runBlocking {
        val falseGate = ready()
        var falseSent = 0
        assertEquals(
            Settlement.REJECTED,
            falseGate.settleOnce({ false }, { falseSent++ }),
        )
        assertEquals(0, falseSent)
        assertEquals(Phase.REJECTED, falseGate.currentPhase())
        assertEquals(Abort.ALREADY_TERMINAL, falseGate.cancelOrDisconnect(second))

        val throwGate = ready()
        var throwSent = 0
        assertEquals(
            Settlement.INDETERMINATE,
            throwGate.settleOnce(
                commitAndPublish = { throw IllegalStateException("SQL outcome unknown") },
                notifySuccess = { throwSent++ },
            ),
        )
        assertEquals(0, throwSent)
        assertEquals(Phase.INDETERMINATE, throwGate.currentPhase())
        // A transport error is not evidence of SQL rollback. Never repeat.
        assertEquals(
            Settlement.NOT_ELECTED,
            throwGate.settleOnce({ true }, { throwSent++ }),
        )
        assertEquals(0, throwSent)
    }

    @Test
    fun packetFailureCannotUndoSqlOrStartAnotherCommit() = runBlocking {
        val gate = ready()
        var committed = 0
        var notifyAttempts = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                gate.settleOnce(
                    commitAndPublish = { committed++; true },
                    notifySuccess = {
                        notifyAttempts++
                        throw IllegalStateException("network socket gone")
                    },
                )
            }
        }
        assertEquals(Phase.COMMITTED, gate.currentPhase())
        assertEquals(
            Settlement.NOT_ELECTED,
            gate.settleOnce(
                commitAndPublish = { committed++; true },
                notifySuccess = { notifyAttempts++ },
            ),
        )
        assertEquals(1, committed)
        assertEquals(1, notifyAttempts)
    }

    @Test
    fun invalidPairIsRejectedBeforeAnyTradePacket() {
        assertThrows(IllegalArgumentException::class.java) {
            TradeItemFinalizationGate(first, first)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TradeItemFinalizationGate(0L, second)
        }
    }
}

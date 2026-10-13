package tw.tsm.gamelogic.systems

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * STAGED ONLY. No TradeHandler / TradeSystem / DI wiring.
 *
 * The two C025 item confirmations elect one SQL settler without suspending
 * inside a JVM synchronized block. Cancel/disconnect can revoke an unstarted
 * trade, but must not interrupt SQL + both RAM publications after settlement
 * begins. The success callback is reachable only after commitAndPublish=true.
 *
 * This is a state/notification boundary, NOT the BAG transaction planner.
 * Only TradeCacheSettlementCoordinator.stageCommit may be provided as the
 * future commitAndPublish implementation; NEVER pass the legacy RAM-only
 * TradeSystem.executeTrade into this gate.
 */
class TradeItemFinalizationGate(
    private val firstPlayerId: Long,
    private val secondPlayerId: Long,
) {
    init { require(firstPlayerId > 0 && secondPlayerId > 0 && firstPlayerId != secondPlayerId) }

    enum class Phase { AWAITING_CONFIRMATIONS, SETTLING, COMMITTED, REJECTED, INDETERMINATE, CANCELLED }
    enum class Confirmation { IGNORED, WAITING_FOR_PARTNER, START_SETTLEMENT }
    enum class Abort { CANCELLED, COMMIT_IN_FLIGHT, ALREADY_TERMINAL, NOT_PARTICIPANT }
    enum class Settlement { NOT_ELECTED, CANCELLED_BEFORE_SQL, REJECTED, INDETERMINATE, COMMITTED }

    private val monitor = Any()
    private var confirmedFirst = false
    private var confirmedSecond = false
    private var settlementStarted = false
    @Volatile private var phase = Phase.AWAITING_CONFIRMATIONS

    fun currentPhase(): Phase = phase

    fun confirm(playerId: Long): Confirmation = synchronized(monitor) {
        if (phase != Phase.AWAITING_CONFIRMATIONS) return@synchronized Confirmation.IGNORED
        when (playerId) {
            firstPlayerId -> {
                if (confirmedFirst) return@synchronized Confirmation.IGNORED
                confirmedFirst = true
            }
            secondPlayerId -> {
                if (confirmedSecond) return@synchronized Confirmation.IGNORED
                confirmedSecond = true
            }
            else -> return@synchronized Confirmation.IGNORED
        }
        if (confirmedFirst && confirmedSecond) {
            phase = Phase.SETTLING
            Confirmation.START_SETTLEMENT
        } else Confirmation.WAITING_FOR_PARTNER
    }

    /**
     * A disconnect during SQL is NOT a rollback command. The caller may
     * suppress outgoing packets to the offline player, but must finish the
     * durable settlement and cache publication.
     */
    fun cancelOrDisconnect(playerId: Long): Abort = synchronized(monitor) {
        if (playerId != firstPlayerId && playerId != secondPlayerId) {
            return@synchronized Abort.NOT_PARTICIPANT
        }
        when (phase) {
            Phase.AWAITING_CONFIRMATIONS -> {
                phase = Phase.CANCELLED
                Abort.CANCELLED
            }
            Phase.SETTLING -> Abort.COMMIT_IN_FLIGHT
            else -> Abort.ALREADY_TERMINAL
        }
    }

    /**
     * Called once by the winner of START_SETTLEMENT, OUTSIDE synchronized.
     * A cancelled request that did not enter SQL may not debit either side.
     * Once accepted, finish SQL and both online cache publications despite a
     * disconnect/cancellation. An SQL exception is INDETERMINATE until DB
     * reconciliation; never report rollback or retry an uncertain debit.
     * An exception from notifySuccess after commit MUST NOT undo the
     * committed status or trigger a second SQL attempt.
     */
    suspend fun settleOnce(
        commitAndPublish: suspend () -> Boolean,
        notifySuccess: () -> Unit,
    ): Settlement {
        val elected = synchronized(monitor) {
            if (phase != Phase.SETTLING || settlementStarted) false
            else {
                settlementStarted = true
                true
            }
        }
        if (!elected) return Settlement.NOT_ELECTED

        // Refuse cancellation that arrived while waiting to be dispatched.
        if (currentCoroutineContext()[Job]?.isActive == false) {
            synchronized(monitor) { phase = Phase.CANCELLED }
            return Settlement.CANCELLED_BEFORE_SQL
        }

        // Never return to a cancelled dispatcher between DB commit and cache
        // publication. The underlying coordinator has its own two-character
        // persistence and synchronous gold fences.
        return withContext(NonCancellable) {
            val committed = try {
                commitAndPublish()
            } catch (e: Exception) {
                // The DB might already have committed. Quarantine this trade
                // rather than announcing failure or initiating a second debit.
                synchronized(monitor) { phase = Phase.INDETERMINATE }
                return@withContext Settlement.INDETERMINATE
            }
            synchronized(monitor) {
                phase = if (committed) Phase.COMMITTED else Phase.REJECTED
            }
            if (!committed) return@withContext Settlement.REJECTED

            // This is the ONLY success path; durable DB and both caches are
            // already settled. Packet failures cannot cause another debit.
            notifySuccess()
            Settlement.COMMITTED
        }
    }
}

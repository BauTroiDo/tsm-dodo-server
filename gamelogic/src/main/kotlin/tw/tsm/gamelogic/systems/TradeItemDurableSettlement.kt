package tw.tsm.gamelogic.systems

import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Abort
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Confirmation
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Phase
import tw.tsm.gamelogic.systems.TradeItemFinalizationGate.Settlement
import java.util.UUID

/**
 * STAGED ONLY: full C025 item-confirmation path, deliberately NOT registered
 * in TradeSystem / TradeHandler / ServerModule.
 *
 * One immutable server UUID binds one frozen upload offer per participant,
 * then planner -> durable SQL -> BOTH online cache publications. If SQL's
 * outcome is ambiguous or already committed, reconcile the receipt and
 * current SQL/cache state; NEVER submit the transfer a second time.
 *
 * This object only receives frozen inputs. The future C025 adapter MUST obtain
 * full BAG+money snapshots and BAG capacity under both persistence locks and
 * verify session identity and frozen C025-002 offer ownership before creating
 * it. The coordinator revalidates every live BAG/gold snapshot under locks.
 */
class TradeItemDurableSettlement(
    val operationId: String,
    firstPlayerId: Long,
    secondPlayerId: Long,
    firstSide: TradeItemSnapshotPlanner.Side,
    secondSide: TradeItemSnapshotPlanner.Side,
    private val planner: TradeItemSnapshotPlanner,
    private val coordinator: TradeCacheSettlementCoordinator,
    private val reconciler: TradeReceiptCacheReconciler,
) {
    init {
        require(UUID.fromString(operationId).toString() == operationId) {
            "Item trade operation ID must be a canonical server UUID"
        }
        require(firstPlayerId > 0 && secondPlayerId > 0 && firstPlayerId != secondPlayerId)
        require(firstSide.characterId > 0 && secondSide.characterId > 0 &&
            firstSide.characterId != secondSide.characterId)
    }

    private fun freeze(source: TradeItemSnapshotPlanner.Side): TradeItemSnapshotPlanner.Side {
        fun deepCopy(input: Map<Int, tw.tsm.domain.valueobjects.ThingData>) =
            input.mapValues { (_, item) -> TradeItemOfferSnapshot.freeze(item) }
        return source.copy(
            beforeBag = deepCopy(source.beforeBag),
            expectedOfferedItems = deepCopy(source.expectedOfferedItems),
            offeredSlots = source.offeredSlots.toList(),
        )
    }

    private val first = freeze(firstSide)
    private val second = freeze(secondSide)
    private val gate = TradeItemFinalizationGate(firstPlayerId, secondPlayerId)

    enum class ConfirmationOutcome {
        IGNORED,
        WAITING_FOR_PARTNER,
        STARTED_AND_SETTLED,
    }

    data class Decision(
        val outcome: ConfirmationOutcome,
        val settlement: Settlement? = null,
    )

    @Volatile
    var lastPlanFailure: TradeItemSnapshotPlanner.Failure? = null
        private set

    fun currentPhase(): Phase = gate.currentPhase()
    fun cancelOrDisconnect(playerId: Long): Abort = gate.cancelOrDisconnect(playerId)

    /**
     * Called by confirmed players, outside JVM synchronized. The caller is
     * responsible for sending the original DVT S025 success=4 on callback.
     * Callback runs ONCE, and only after SQL plus both cache publications
     * are proven. A callback exception never restarts the SQL transaction.
     */
    suspend fun confirm(playerId: Long, notifySuccess: () -> Unit): Decision =
        when (gate.confirm(playerId)) {
            Confirmation.IGNORED -> Decision(ConfirmationOutcome.IGNORED)
            Confirmation.WAITING_FOR_PARTNER ->
                Decision(ConfirmationOutcome.WAITING_FOR_PARTNER)
            Confirmation.START_SETTLEMENT -> Decision(
                ConfirmationOutcome.STARTED_AND_SETTLED,
                gate.settleOnce(
                    commitAndPublish = {
                        when (val result = planner.plan(first, second)) {
                            is TradeItemSnapshotPlanner.Result.Rejected -> {
                                lastPlanFailure = result.reason
                                false
                            }
                            is TradeItemSnapshotPlanner.Result.Accepted -> {
                                // The coordinator must hold both persistence
                                // + gold fences until SQL and RAM complete.
                                // A thrown SQL result can mean COMMITTED.
                                val outcome = try {
                                    coordinator.stageCommitWithReceipt(
                                        operationId, result.first, result.second
                                    )
                                } catch (e: Exception) {
                                    TradeCacheSettlementCoordinator.ReceiptStage.INDETERMINATE
                                }
                                when (outcome) {
                                    TradeCacheSettlementCoordinator.ReceiptStage.COMMITTED_NOW ->
                                        true
                                    TradeCacheSettlementCoordinator.ReceiptStage.REJECTED ->
                                        false
                                    TradeCacheSettlementCoordinator.ReceiptStage.ALREADY_COMMITTED,
                                    TradeCacheSettlementCoordinator.ReceiptStage.INDETERMINATE -> {
                                        when (reconciler.reconcile(
                                            operationId, result.first, result.second
                                        )) {
                                            TradeReceiptCacheReconciler.Result.RESTORED,
                                            TradeReceiptCacheReconciler.Result.ALREADY_SYNCED -> true
                                            else -> throw IndeterminateTradeSettlement()
                                        }
                                    }
                                    TradeCacheSettlementCoordinator.ReceiptStage.TOKEN_CONFLICT ->
                                        throw IndeterminateTradeSettlement()
                                }
                            }
                        }
                    },
                    notifySuccess = notifySuccess,
                ),
            )
        }

    /** Quarantine; never interpreted as evidence that SQL rolled back. */
    private class IndeterminateTradeSettlement : IllegalStateException(
        "C025 SQL outcome unresolved; reconcile durable UUID before further actions"
    )
}

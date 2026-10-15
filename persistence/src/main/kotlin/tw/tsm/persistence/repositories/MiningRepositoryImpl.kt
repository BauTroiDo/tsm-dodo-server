package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.core.ResultRow
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.interfaces.repositories.MiningFuelCommit
import tw.tsm.domain.interfaces.repositories.MiningTsAwardCommit
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.Inventories
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.CustomMiningRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.jetbrains.exposed.v1.jdbc.insert

import org.jetbrains.exposed.v1.core.like
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.MiningOreReserves
import tw.tsm.persistence.tables.MiningTsAwardReceipts
import tw.tsm.persistence.tables.CharacterCurrencies

private class RejectedMiningStateAward : RuntimeException()

class MiningRepositoryImpl : MiningRepository {

    /**
     * CUSTOM TS DVT mining emulator (NOT official rates):
     *   each 360s tick uses one worker fullness unit (worker1..4 order);
     *   payout according to operator-configured rates;
     *   ore debit=1 per TS, shared reserve, capped per Vietnam calendar day.
     * The exact mine state, TS wallet, ore, and per-day receipts all commit in
     * ONE SQL transaction. Row locking/expected wallet fence protects races.
     */
    override suspend fun settleCustomMining(
        characterId: Long,
        nowMillis: Long,
        expectedTsBalance: Int,
        rules: CustomMiningRules,
    ): MiningTsAwardCommit? = suspendTransaction {
        if (!rules.enabled || characterId <= 0 || nowMillis <= 0L ||
            expectedTsBalance !in 0..999_999_999) return@suspendTransaction null

        val zone = ZoneId.of(rules.zoneId)
        val key = CharacterMining.characterId eq characterId
        var mine = CharacterMining.selectAll().where { key }.forUpdate().singleOrNull()
        if (mine == null) {
            // First visit starts an empty, non-paying mine. Do not mint TS.
            CharacterMining.insert {
                it[CharacterMining.characterId] = characterId
                it[mineType] = 1
                it[full1] = 0
                it[full2] = 0
                it[full3] = 0
                it[full4] = 0
                it[updatedAtMillis] = nowMillis
                it[dayLimit] = false
            }
            return@suspendTransaction MiningTsAwardCommit(false, expectedTsBalance, 600_000_000L)
        }

        // Peek wallet before any reserve initialization so obvious stale
        // requests fail without writes; recheck under the SQL row lock later.
        val walletKey = (CharacterCurrencies.characterId eq characterId) and
            (CharacterCurrencies.currencyKind eq 8)
        val previewTs = CharacterCurrencies.selectAll().where { walletKey }
            .singleOrNull()?.get(CharacterCurrencies.value) ?: 0
        if (previewTs != expectedTsBalance) return@suspendTransaction null

        // IMPORTANT: on the very FIRST mine payout, every character must
        // lock the SAME ore row, including when the reserve starts absent.
        // Otherwise two players can both debit from virtual 600M and mint
        // TS with only one ore debit (lost shared update).
        MiningOreReserves.insertIgnore {
            it[MiningOreReserves.mineType] = 1
            it[remainingOre] = 600_000_000L
        }
        val oreRow = MiningOreReserves.selectAll()
            .where { MiningOreReserves.mineType eq 1 }
            .forUpdate().single()
        var ore = oreRow[MiningOreReserves.remainingOre]
        val walletRow = CharacterCurrencies.selectAll().where { walletKey }
            .forUpdate().singleOrNull()
        val oldTs = walletRow?.get(CharacterCurrencies.value) ?: 0
        if (oldTs != expectedTsBalance) return@suspendTransaction null
        if (ore !in 0L..600_000_000L) return@suspendTransaction null
        val initialOre = ore
        val full = mutableListOf(
            mine[CharacterMining.full1], mine[CharacterMining.full2],
            mine[CharacterMining.full3], mine[CharacterMining.full4]
        )
        if (full.any { it !in 0..65535 }) return@suspendTransaction null
        val initTime = mine[CharacterMining.updatedAtMillis]
        var cursor = if (initTime in 1L..nowMillis) initTime else nowMillis
        var capped = mine[CharacterMining.dayLimit]
        val dayTotal = mutableMapOf<LocalDate,Int>()
        val payouts = linkedMapOf<LocalDate,Int>()
        fun dayAt(ts: Long): LocalDate =
            Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        fun nextMidnight(day: LocalDate): Long =
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun used(day:LocalDate):Int = dayTotal.getOrPut(day) {
            val tag = "custom1:" + day.toString().replace("-","") + ":" + characterId + ":%"
            MiningTsAwardReceipts.selectAll().where {
                (MiningTsAwardReceipts.characterId eq characterId) and
                (MiningTsAwardReceipts.receiptId like tag)
            }.sumOf { it[MiningTsAwardReceipts.tsCredit].toLong() }
                .coerceIn(0L,Int.MAX_VALUE.toLong()).toInt()
        }

        var totalCredit=0L
        var iterations=0
        // Bound one request's work; the next poll continues from persisted cursor.
        while (iterations++ < 20000 && cursor <= nowMillis) {
            if (full.all { it == 0 } || ore == 0L) {
                // No retroactive earnings if the worker has been empty.
                cursor = nowMillis
                capped = false
                break
            }
            if (capped) {
                val midnight = nextMidnight(dayAt(cursor))
                if (nowMillis < midnight) break
                cursor = midnight
                capped = false
                continue
            }
            val tick = cursor + 360_000L
            if (tick > nowMillis) break
            val day = dayAt(tick)
            val already=used(day)
            if (already >= rules.dailyTsCap) {
                capped = true
                // Do not eat a worker fullness unit for an unpaid tick.
                cursor = minOf(cursor,tick - 1L)
                continue
            }
            val i=full.indexOfFirst { it > 0 }
            val rate=rules.workerTsPerTick[i]
            val gain=minOf(rate.toLong(), (rules.dailyTsCap-already).toLong(),
                ore, (999_999_999L-expectedTsBalance-totalCredit))
            if (gain<=0L) {
                // Wallet/reserve is exhausted; do not burn worker fullness.
                if (ore==0L) cursor=nowMillis
                break
            }
            full[i] -= 1
            cursor=tick
            ore -= gain
            totalCredit += gain
            val credited=gain.toInt()
            dayTotal[day] = already + credited
            payouts[day] = (payouts[day] ?: 0) + credited
            if (dayTotal.getValue(day)>=rules.dailyTsCap) capped=true
        }
        val updated = initTime != cursor ||
            capped != mine[CharacterMining.dayLimit] ||
            full != listOf(mine[CharacterMining.full1],mine[CharacterMining.full2],
                mine[CharacterMining.full3],mine[CharacterMining.full4])
        if (!updated && totalCredit == 0L)
            return@suspendTransaction MiningTsAwardCommit(false,oldTs,initialOre)

        if (totalCredit > 0L) {
            CharacterCurrencies.upsert {
                it[CharacterCurrencies.characterId] = characterId
                it[CharacterCurrencies.currencyKind] = 8
                it[CharacterCurrencies.value] = oldTs + totalCredit.toInt()
            }
            check(MiningOreReserves.update({ MiningOreReserves.mineType eq 1 }) {
                it[remainingOre] = ore
            } == 1) { "Shared ore debit update failed" }
            var creditedSoFar=0L
            val firstTimestamp = initTime.coerceAtLeast(1)
            for ((day, credits) in payouts) {
                creditedSoFar+=credits
                val receiptId = "custom1:" + day.toString().replace("-","") +
                    ":" + characterId + ":" + firstTimestamp
                MiningTsAwardReceipts.insert {
                    it[MiningTsAwardReceipts.receiptId] = receiptId
                    it[MiningTsAwardReceipts.characterId] = characterId
                    it[MiningTsAwardReceipts.mineType] = 1
                    it[MiningTsAwardReceipts.oreDebit] = credits.toLong()
                    it[MiningTsAwardReceipts.tsCredit] = credits
                    it[MiningTsAwardReceipts.tsBalanceAfter] = oldTs + creditedSoFar.toInt()
                    it[MiningTsAwardReceipts.oreRemainingAfter] =
                        initialOre - creditedSoFar
                }
            }
        }

        check(CharacterMining.update({ key }) {
            it[CharacterMining.full1] = full[0]
            it[CharacterMining.full2] = full[1]
            it[CharacterMining.full3] = full[2]
            it[CharacterMining.full4] = full[3]
            it[CharacterMining.updatedAtMillis] = cursor
            it[CharacterMining.dayLimit] = capped
        } == 1) { "Character mine update failed" }
        MiningTsAwardCommit(totalCredit>0L,oldTs+totalCredit.toInt(),ore)
    }

    /**
     * Atomic TS currency credit + shared reserve debit + unique durable receipt.
     * Does not choose rates, run on fuel ticks, or publish any client cache.
     * Must be integrated under the currency writer fence once rates are sourced.
     */
    override suspend fun commitVerifiedTsAwardAtomic(
        receiptId: String,
        characterId: Long,
        mineType: Int,
        oreDebit: Long,
        tsCredit: Int,
        maxReserve: Long,
        expectedTsBalance: Int,
    ): MiningTsAwardCommit? = suspendTransaction {
        if (receiptId.length !in 1..128 || !receiptId.all {
                it.isLetterOrDigit() || it == ':' || it == '-' || it == '_'
            } || characterId <= 0 || mineType != 1 || oreDebit <= 0 ||
            maxReserve <= 0 || oreDebit > maxReserve || tsCredit <= 0 ||
            expectedTsBalance !in 0..999_999_999
        ) return@suspendTransaction null
        MiningOreReserves.insertIgnore {
            it[MiningOreReserves.mineType] = mineType
            it[remainingOre] = maxReserve
        }
        // A shared row lock serializes concurrent awards and repeat receipt IDs.
        val reserve = MiningOreReserves.selectAll()
            .where { MiningOreReserves.mineType eq mineType }
            .forUpdate().singleOrNull() ?: error("Missing mine reserve after initialize")
        val beforeOre = reserve[MiningOreReserves.remainingOre]
        if (beforeOre !in 0L..maxReserve) return@suspendTransaction null
        val existing = MiningTsAwardReceipts.selectAll()
            .where { MiningTsAwardReceipts.receiptId eq receiptId }
            .singleOrNull()
        if (existing != null) {
            if (existing[MiningTsAwardReceipts.characterId] != characterId ||
                existing[MiningTsAwardReceipts.mineType] != mineType ||
                existing[MiningTsAwardReceipts.oreDebit] != oreDebit ||
                existing[MiningTsAwardReceipts.tsCredit] != tsCredit
            ) return@suspendTransaction null
            return@suspendTransaction MiningTsAwardCommit(
                false, existing[MiningTsAwardReceipts.tsBalanceAfter],
                existing[MiningTsAwardReceipts.oreRemainingAfter],
            )
        }
        if (beforeOre < oreDebit) return@suspendTransaction null
        val currencyKey = (CharacterCurrencies.characterId eq characterId) and
            (CharacterCurrencies.currencyKind eq 8)
        val wallet = CharacterCurrencies.selectAll().where { currencyKey }
            .forUpdate().singleOrNull()
        val oldTs = wallet?.get(CharacterCurrencies.value) ?: 0
        // Refuse stale online cache; direct SQL without this check can
        // overwrite a simultaneous TS purchase or credit.
        if (oldTs != expectedTsBalance || oldTs < 0 ||
            oldTs.toLong() + tsCredit > 999_999_999L) {
            return@suspendTransaction null
        }
        val nextTs = oldTs + tsCredit
        val nextOre = beforeOre - oreDebit
        CharacterCurrencies.upsert {
            it[CharacterCurrencies.characterId] = characterId
            it[CharacterCurrencies.currencyKind] = 8
            it[CharacterCurrencies.value] = nextTs
        }
        check(MiningOreReserves.update({ MiningOreReserves.mineType eq mineType }) {
            it[remainingOre] = nextOre
        } == 1) { "Mining award reserve update lost" }
        MiningTsAwardReceipts.insert {
            it[MiningTsAwardReceipts.receiptId] = receiptId
            it[MiningTsAwardReceipts.characterId] = characterId
            it[MiningTsAwardReceipts.mineType] = mineType
            it[MiningTsAwardReceipts.oreDebit] = oreDebit
            it[MiningTsAwardReceipts.tsCredit] = tsCredit
            it[MiningTsAwardReceipts.tsBalanceAfter] = nextTs
            it[MiningTsAwardReceipts.oreRemainingAfter] = nextOre
        }
        MiningTsAwardCommit(true, nextTs, nextOre)
    }

    /**
     * Source-neutral settlement boundary. This method does NOT decide when a
     * reward is due nor how much TS to pay. Those are original DVT backend rules
     * absent from IL2CPP and Lua. Call only after those rules are established,
     * under CharacterPersistenceGuard AND PlayerCurrencySystem writer fence.
     */
    override suspend fun commitVerifiedTsAwardWithMineStateAtomic(
        receiptId: String,
        characterId: Long,
        mineType: Int,
        oreDebit: Long,
        tsCredit: Int,
        maxReserve: Long,
        expectedTsBalance: Int,
        expectedMine: MiningRecord,
        updatedMine: MiningRecord,
    ): MiningTsAwardCommit? = try {
        suspendTransaction {
        if (receiptId.length !in 1..128 || !receiptId.all {
                it.isLetterOrDigit() || it == ':' || it == '-' || it == '_'
            } || characterId <= 0 || mineType != 1 || oreDebit <= 0 ||
            maxReserve <= 0 || oreDebit > maxReserve || tsCredit <= 0 ||
            expectedTsBalance !in 0..999_999_999 ||
            expectedMine.mineType != mineType || updatedMine.mineType != mineType ||
            expectedMine.dayLimit || expectedMine.machineFull.size != 4 ||
            updatedMine.machineFull.size != 4 ||
            expectedMine.machineFull.any { it !in 0..65535 } ||
            updatedMine.machineFull.any { it !in 0..65535 } ||
            expectedMine.machineFull.none { it > 0 } ||
            expectedMine.updatedAtMillis <= 0 ||
            updatedMine.updatedAtMillis < expectedMine.updatedAtMillis ||
            expectedMine.machineFull.indices.any {
                updatedMine.machineFull[it] > expectedMine.machineFull[it]
            }
        ) return@suspendTransaction null

        // Lock the mine FIRST. A simultaneous C170-010 refill is BAG->mine
        // and a background source-backed reward must never overwrite it.
        val mine = CharacterMining.selectAll()
            .where { CharacterMining.characterId eq characterId }
            .forUpdate().singleOrNull() ?: return@suspendTransaction null
        // Idempotent replay is checked BEFORE mine-state CAS: the original
        // worker has already advanced on the first successful commit.
        val replay = MiningTsAwardReceipts.selectAll()
            .where { MiningTsAwardReceipts.receiptId eq receiptId }
            .singleOrNull()
        if (replay != null) {
            if (replay[MiningTsAwardReceipts.characterId] != characterId ||
                replay[MiningTsAwardReceipts.mineType] != mineType ||
                replay[MiningTsAwardReceipts.oreDebit] != oreDebit ||
                replay[MiningTsAwardReceipts.tsCredit] != tsCredit
            ) return@suspendTransaction null
            return@suspendTransaction MiningTsAwardCommit(
                false, replay[MiningTsAwardReceipts.tsBalanceAfter],
                replay[MiningTsAwardReceipts.oreRemainingAfter],
            )
        }
        if (mine.asMiningRecord() != expectedMine) return@suspendTransaction null

        // Shared ore lock serializes with the existing receipt-only award API.
        MiningOreReserves.insertIgnore {
            it[MiningOreReserves.mineType] = mineType
            it[remainingOre] = maxReserve
        }
        val reserve = MiningOreReserves.selectAll()
            .where { MiningOreReserves.mineType eq mineType }
            .forUpdate().singleOrNull() ?: error("Missing reserved mining ore row")
        val beforeOre = reserve[MiningOreReserves.remainingOre]
        if (beforeOre !in 0L..maxReserve) throw RejectedMiningStateAward()

        val existing = MiningTsAwardReceipts.selectAll()
            .where { MiningTsAwardReceipts.receiptId eq receiptId }
            .singleOrNull()
        if (existing != null) {
            if (existing[MiningTsAwardReceipts.characterId] != characterId ||
                existing[MiningTsAwardReceipts.mineType] != mineType ||
                existing[MiningTsAwardReceipts.oreDebit] != oreDebit ||
                existing[MiningTsAwardReceipts.tsCredit] != tsCredit
            ) throw RejectedMiningStateAward()
            // Replays must never reapply the old miner state, or overwrite a
            // wallet balance changed by another purchase or TS reward.
            return@suspendTransaction MiningTsAwardCommit(
                false, existing[MiningTsAwardReceipts.tsBalanceAfter],
                existing[MiningTsAwardReceipts.oreRemainingAfter],
            )
        }
        if (beforeOre < oreDebit) throw RejectedMiningStateAward()

        val currencyKey = (CharacterCurrencies.characterId eq characterId) and
            (CharacterCurrencies.currencyKind eq 8)
        val wallet = CharacterCurrencies.selectAll().where { currencyKey }
            .forUpdate().singleOrNull()
        val oldTs = wallet?.get(CharacterCurrencies.value) ?: 0
        if (oldTs != expectedTsBalance || oldTs < 0 ||
            oldTs.toLong() + tsCredit > 999_999_999L
        ) throw RejectedMiningStateAward()

        val nextTs = oldTs + tsCredit
        val nextOre = beforeOre - oreDebit
        check(CharacterMining.update({ CharacterMining.characterId eq characterId }) {
            it[CharacterMining.mineType] = updatedMine.mineType
            it[CharacterMining.full1] = updatedMine.machineFull[0]
            it[CharacterMining.full2] = updatedMine.machineFull[1]
            it[CharacterMining.full3] = updatedMine.machineFull[2]
            it[CharacterMining.full4] = updatedMine.machineFull[3]
            it[CharacterMining.updatedAtMillis] = updatedMine.updatedAtMillis
            it[CharacterMining.dayLimit] = updatedMine.dayLimit
        } == 1) { "Mining state CAS update affected wrong number of rows" }
        CharacterCurrencies.upsert {
            it[CharacterCurrencies.characterId] = characterId
            it[CharacterCurrencies.currencyKind] = 8
            it[CharacterCurrencies.value] = nextTs
        }
        check(MiningOreReserves.update({ MiningOreReserves.mineType eq mineType }) {
            it[remainingOre] = nextOre
        } == 1) { "Mining reserve debit affected wrong number of rows" }
        MiningTsAwardReceipts.insert {
            it[MiningTsAwardReceipts.receiptId] = receiptId
            it[MiningTsAwardReceipts.characterId] = characterId
            it[MiningTsAwardReceipts.mineType] = mineType
            it[MiningTsAwardReceipts.oreDebit] = oreDebit
            it[MiningTsAwardReceipts.tsCredit] = tsCredit
            it[MiningTsAwardReceipts.tsBalanceAfter] = nextTs
            it[MiningTsAwardReceipts.oreRemainingAfter] = nextOre
        }
        MiningTsAwardCommit(true, nextTs, nextOre)
        }
    } catch (_: RejectedMiningStateAward) { null }

    override suspend fun loadRemainingOre(mineType: Int): Long? = suspendTransaction {
        MiningOreReserves.selectAll()
            .where { MiningOreReserves.mineType eq mineType }
            .singleOrNull()?.get(MiningOreReserves.remainingOre)
    }

    /**
     * In one row-locked SQL transaction, debit a VERIFIED awarded ore amount.
     * The original client does not specify ore rates; do not call from
     * durability settlement or without a real reward receipt.
     */
    override suspend fun debitVerifiedOre(
        mineType: Int, quantity: Long, maxReserve: Long,
    ): Boolean = suspendTransaction {
        if (mineType < 1 || quantity <= 0 || maxReserve <= 0 || quantity > maxReserve) {
            return@suspendTransaction false
        }
        MiningOreReserves.insertIgnore {
            it[MiningOreReserves.mineType] = mineType
            it[remainingOre] = maxReserve
        }
        val locked = MiningOreReserves.selectAll()
            .where { MiningOreReserves.mineType eq mineType }
            .forUpdate().singleOrNull() ?: error("Missing shared mine reserve after initialization")
        val before = locked[MiningOreReserves.remainingOre]
        if (before < quantity || before > maxReserve) return@suspendTransaction false
        val rows = MiningOreReserves.update({ MiningOreReserves.mineType eq mineType }) {
            it[remainingOre] = before - quantity
        }
        check(rows == 1) { "Mining reserve deduction changed $rows rows" }
        true
    }


    /**
     * ONE SQL transaction. Lock the BAG row before reading mining state;
     * never debit food separately from persisting machine durability.
     */
    override suspend fun commitFuelRefillAtomic(
        charId: Long, bagSlot: Int, expectedBag: ThingData, quantity: Int,
        expectedMine: MiningRecord?, updatedMine: MiningRecord,
    ): MiningFuelCommit? = suspendTransaction {
        if (charId <= 0 || bagSlot < 0 || quantity <= 0 ||
            updatedMine.machineFull.size != 4 ||
            updatedMine.machineFull.any { it !in 0..65535 }) return@suspendTransaction null

        val bagKey = (Inventories.characterId eq charId) and
            (Inventories.storageType eq EThings.BAG) and
            (Inventories.slot eq bagSlot)
        val bagRow = Inventories.selectAll().where { bagKey }.forUpdate().singleOrNull()
            ?: return@suspendTransaction null
        val before = bagRow[Inventories.quantity]
        if (bagRow[Inventories.itemId] != expectedBag.itemId ||
            before != expectedBag.quantity || before < quantity ||
            bagRow[Inventories.isLocked]) return@suspendTransaction null

        val mineRow = CharacterMining.selectAll()
            .where { CharacterMining.characterId eq charId }
            .forUpdate().singleOrNull()
        val persistedMine = mineRow?.asMiningRecord()
        if (persistedMine != expectedMine) return@suspendTransaction null

        val remaining = before - quantity
        val changed = if (remaining == 0) Inventories.deleteWhere { bagKey }
        else Inventories.update({ bagKey }) { it[Inventories.quantity] = remaining }
        check(changed == 1) { "Mining fuel debit did not affect exactly one BAG row" }

        CharacterMining.upsert {
            it[characterId] = charId
            it[mineType] = updatedMine.mineType
            it[full1] = updatedMine.machineFull[0]
            it[full2] = updatedMine.machineFull[1]
            it[full3] = updatedMine.machineFull[2]
            it[full4] = updatedMine.machineFull[3]
            it[updatedAtMillis] = updatedMine.updatedAtMillis
            it[dayLimit] = updatedMine.dayLimit
        }
        MiningFuelCommit(if (remaining == 0) null else expectedBag.copy(quantity = remaining))
    }

    private fun ResultRow.asMiningRecord(): MiningRecord = MiningRecord(
        mineType = this[CharacterMining.mineType],
        machineFull = listOf(
            this[CharacterMining.full1], this[CharacterMining.full2],
            this[CharacterMining.full3], this[CharacterMining.full4],
        ),
        updatedAtMillis = this[CharacterMining.updatedAtMillis],
        dayLimit = this[CharacterMining.dayLimit],
    )

    override suspend fun load(charId: Long): MiningRecord? = suspendTransaction {
        CharacterMining.selectAll()
            .where { CharacterMining.characterId eq charId }
            .firstOrNull()
            ?.let { row ->
                MiningRecord(
                    mineType = row[CharacterMining.mineType],
                    machineFull = listOf(
                        row[CharacterMining.full1], row[CharacterMining.full2],
                        row[CharacterMining.full3], row[CharacterMining.full4],
                    ),
                    updatedAtMillis = row[CharacterMining.updatedAtMillis],
                    dayLimit = row[CharacterMining.dayLimit],
                )
            }
    }

    override suspend fun save(charId: Long, record: MiningRecord) {
        val full = (record.machineFull + listOf(0, 0, 0, 0)).take(4)
        suspendTransaction {
            CharacterMining.upsert {
                it[characterId] = charId
                it[mineType] = record.mineType
                it[full1] = full[0]
                it[full2] = full[1]
                it[full3] = full[2]
                it[full4] = full[3]
                it[updatedAtMillis] = record.updatedAtMillis
                it[dayLimit] = record.dayLimit
            }
        }
    }
}

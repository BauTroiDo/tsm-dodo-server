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
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.MiningOreReserves
import tw.tsm.persistence.tables.MiningTsAwardReceipts
import tw.tsm.persistence.tables.CharacterCurrencies

private class RejectedMiningStateAward : RuntimeException()

class MiningRepositoryImpl : MiningRepository {
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

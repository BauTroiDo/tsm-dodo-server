package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.core.ResultRow
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.interfaces.repositories.MiningFuelCommit
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.Inventories
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.MiningOreReserves

class MiningRepositoryImpl : MiningRepository {
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

package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.interfaces.repositories.TradeReceiptCommit
import tw.tsm.domain.interfaces.repositories.TradeReceiptLookup
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.TradeSettlementReceipts
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterBattlePassSeasons
import tw.tsm.domain.constants.EThings

/**
 * Triển khai InventoryRepository bằng Exposed ORM
 *
 * Dùng bảng đơn inventories chứa túi đồ/trang bị/Tiền Trang, phân biệt bằng storageType.
 */
class InventoryRepositoryImpl : InventoryRepository {

    override suspend fun loadItems(characterId: Long, storageType: Int): Map<Int, ThingData> =
        suspendTransaction {
            Inventories.selectAll()
                .where {
                    (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq storageType)
                }
                .associate { it[Inventories.slot] to it.toThingData() }
        }

    override suspend fun saveItem(characterId: Long, storageType: Int, slot: Int, thing: ThingData?) =
        suspendTransaction {
            if (thing == null || thing.isEmpty) {
                Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq storageType) and
                    (Inventories.slot eq slot)
                }
            } else {
                Inventories.upsert {
                    it[Inventories.characterId] = characterId
                    it[Inventories.storageType] = storageType
                    it[Inventories.slot] = slot
                    it.setThingData(thing)
                }
            }
            Unit
        }

    override suspend fun saveItemsAtomic(
        characterId: Long,
        storageType: Int,
        changes: Map<Int, ThingData?>
    ) = suspendTransaction {
        for ((slot, thing) in changes) {
            if (thing == null || thing.isEmpty) {
                Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq storageType) and
                    (Inventories.slot eq slot)
                }
            } else {
                Inventories.upsert {
                    it[Inventories.characterId] = characterId
                    it[Inventories.storageType] = storageType
                    it[Inventories.slot] = slot
                    it.setThingData(thing)
                }
            }
        }
        Unit
    }

    override suspend fun loadBattlePassProgress(characterId: Long): tw.tsm.domain.data.BattlePassProgress? = suspendTransaction {
        CharacterBattlePassSeasons.selectAll().where { CharacterBattlePassSeasons.charId eq characterId }
            .singleOrNull()?.let {
                tw.tsm.domain.data.BattlePassProgress(it[CharacterBattlePassSeasons.seasonId],
                    it[CharacterBattlePassSeasons.freeLevel], it[CharacterBattlePassSeasons.paidLevel])
            }
    }

    override suspend fun saveBattlePassClaimAtomic(
        characterId: Long, seasonId: Int, changes: Map<Int, ThingData?>,
        expectedProgress: Map<Int, Int>, nextProgress: Map<Int, Pair<Int, Int>>,
    ) = suspendTransaction {
        val bound = CharacterBattlePassSeasons.selectAll()
            .where { CharacterBattlePassSeasons.charId eq characterId }.forUpdate().singleOrNull()
        check(bound == null || bound[CharacterBattlePassSeasons.seasonId] == seasonId) {
            "BattlePass rollover requires evidenced reset policy"
        }
        if (bound != null) {
            check(bound[CharacterBattlePassSeasons.freeLevel] <= expectedProgress.getValue(792) &&
                bound[CharacterBattlePassSeasons.paidLevel] <= expectedProgress.getValue(793)) {
                "BattlePass claim ledger already advanced"
            }
        }
        val persisted = CharacterRoleCounts.selectAll().where { CharacterRoleCounts.charId eq characterId }
            .associate { it[CharacterRoleCounts.countIndex] to it[CharacterRoleCounts.value] }
        check(expectedProgress.all { (index, value) ->
            if (bound == null) (persisted[index] ?: 0) == value
            else (persisted[index] ?: 0) <= value
        })
        val storageType = EThings.BAG
        for ((slot, thing) in changes) {
            if (thing == null || thing.isEmpty) {
                Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq storageType) and
                    (Inventories.slot eq slot)
                }
            } else {
                Inventories.upsert {
                    it[Inventories.characterId] = characterId
                    it[Inventories.storageType] = storageType
                    it[Inventories.slot] = slot
                    it.setThingData(thing)
                }
            }
        }
        nextProgress.forEach { (index, pair) ->
            CharacterRoleCounts.upsert {
                it[CharacterRoleCounts.charId] = characterId
                it[CharacterRoleCounts.countIndex] = index
                it[CharacterRoleCounts.value] = pair.first
                it[CharacterRoleCounts.maxValue] = pair.second
            }
        }
        CharacterBattlePassSeasons.upsert {
            it[CharacterBattlePassSeasons.charId] = characterId
            it[CharacterBattlePassSeasons.seasonId] = seasonId
            it[CharacterBattlePassSeasons.freeLevel] = nextProgress.getValue(792).first
            it[CharacterBattlePassSeasons.paidLevel] = nextProgress.getValue(793).first
        }
        Unit
    }

    override suspend fun saveAllItems(characterId: Long, storageType: Int, items: Map<Int, ThingData>) =
        suspendTransaction {
            // Xoá sạch toàn bộ dữ liệu loại đó trước
            Inventories.deleteWhere {
                (Inventories.characterId eq characterId) and
                (Inventories.storageType eq storageType)
            }
            // Ghi hàng loạt (giảm round-trip DB)
            val nonEmpty = items.filter { !it.value.isEmpty }
            if (nonEmpty.isNotEmpty()) {
                Inventories.batchInsert(nonEmpty.entries.toList()) { (slot, thing) ->
                    this[Inventories.characterId] = characterId
                    this[Inventories.storageType] = storageType
                    this[Inventories.slot] = slot
                    this.setThingData(thing)
                }
            }
            Unit
        }

    /**
     * STAGED ONLY: optimistic full-snapshot two-player BAG + GOLD transaction.
     *
     * The real TradeSystem currently uses synchronous RAM changes; enabling
     * this method there without atomic cache publication would resurrect old
     * objects at autosave/logout. Remain unwired until that is implemented.
     */
    override suspend fun commitTwoCharacterTradeAtomic(
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): Boolean = commitTwoCharacterTradeInternal(null, first, second) == TradeReceiptCommit.COMMITTED_NOW

    /** STAGED / UNWIRED: receipt and BAG+gold settlement share ONE SQL commit. */
    override suspend fun commitTwoCharacterTradeWithReceiptAtomic(
        operationId: String,
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): TradeReceiptCommit =
        commitTwoCharacterTradeInternal(operationId, first, second)

    override suspend fun findTwoCharacterTradeReceipt(
        operationId: String,
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): TradeReceiptLookup {
        if (!validOperationId(operationId) ||
            first.characterId <= 0 || second.characterId <= 0 ||
            first.characterId == second.characterId
        ) return TradeReceiptLookup.TOKEN_CONFLICT
        val digest = TradeSnapshotDigest.of(first, second)
        return suspendTransaction {
            val row = TradeSettlementReceipts.selectAll()
                .where { TradeSettlementReceipts.operationId eq operationId }
                .singleOrNull() ?: return@suspendTransaction TradeReceiptLookup.NOT_FOUND
            if (row[TradeSettlementReceipts.firstCharacterId] == minOf(first.characterId, second.characterId) &&
                row[TradeSettlementReceipts.secondCharacterId] == maxOf(first.characterId, second.characterId) &&
                row[TradeSettlementReceipts.snapshotDigest] == digest
            ) TradeReceiptLookup.MATCHED else TradeReceiptLookup.TOKEN_CONFLICT
        }
    }

    private fun validOperationId(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value }
            .getOrDefault(false)

    private suspend fun commitTwoCharacterTradeInternal(
        operationId: String?,
        first: TradeAccountSnapshot,
        second: TradeAccountSnapshot,
    ): TradeReceiptCommit {
        if (operationId != null && !validOperationId(operationId)) return TradeReceiptCommit.REJECTED
        if (first.characterId <= 0 || second.characterId <= 0 ||
            first.characterId == second.characterId
        ) return TradeReceiptCommit.REJECTED
        val sides = listOf(first, second).sortedBy { it.characterId }
        if (sides.any { side ->
                side.beforeGold < 0 || side.afterGold < 0 ||
                    side.beforeGold > ItemConstants.MAX_GOLD ||
                    side.afterGold > ItemConstants.MAX_GOLD ||
                    side.auctionGold < 0 ||
                    listOf(side.beforeBag, side.afterBag).any { bag ->
                        bag.any { (slot, item) ->
                            slot !in 0..65535 || item.isEmpty ||
                                item.itemId !in 1..65535 || item.quantity <= 0
                        }
                    }
            }
        ) return TradeReceiptCommit.REJECTED

        // A cross-player trade cannot mint/destroy cards or gold. Compare
        // entire ThingData, including all Myth, affix and expiration fields.
        fun identities(bags: List<Map<Int, ThingData>>): Map<ThingData, Long> =
            bags.flatMap { it.values }.groupingBy { it.copy(quantity = 1) }
                .fold(0L) { acc, item -> acc + item.quantity.toLong() }
        if (identities(sides.map { it.beforeBag }) != identities(sides.map { it.afterBag })) {
            return TradeReceiptCommit.REJECTED
        }
        if (sides.sumOf { it.beforeGold.toLong() } !=
            sides.sumOf { it.afterGold.toLong() }
        ) return TradeReceiptCommit.REJECTED

        return suspendTransaction {
            // Lock both money rows in canonical order BEFORE consulting the
            // receipt. A concurrent replay waits for the first SQL commit.
            val lockedMoney = sides.map { side ->
                CharacterMoney.selectAll()
                    .where { CharacterMoney.characterId eq side.characterId }
                    .forUpdate().singleOrNull()
                    ?: return@suspendTransaction TradeReceiptCommit.REJECTED
            }

            if (operationId != null) {
                val receipt = TradeSettlementReceipts.selectAll()
                    .where { TradeSettlementReceipts.operationId eq operationId }
                    .forUpdate().singleOrNull()
                if (receipt != null) {
                    val same = receipt[TradeSettlementReceipts.firstCharacterId] == sides[0].characterId &&
                        receipt[TradeSettlementReceipts.secondCharacterId] == sides[1].characterId &&
                        receipt[TradeSettlementReceipts.snapshotDigest] == TradeSnapshotDigest.of(first, second)
                    return@suspendTransaction if (same) TradeReceiptCommit.ALREADY_COMMITTED
                        else TradeReceiptCommit.TOKEN_CONFLICT
                }
            }

            // Re-validate both stored snapshots after serialized row locks.
            for ((index, side) in sides.withIndex()) {
                val persistedMoney = lockedMoney[index]
                if (persistedMoney[CharacterMoney.gold] != side.beforeGold ||
                    persistedMoney[CharacterMoney.auctionGold] != side.auctionGold
                ) return@suspendTransaction TradeReceiptCommit.REJECTED
                val persistedBag = Inventories.selectAll().where {
                    (Inventories.characterId eq side.characterId) and
                        (Inventories.storageType eq EThings.BAG)
                }.forUpdate().associate { row ->
                    row[Inventories.slot] to row.toThingData()
                }
                if (persistedBag != side.beforeBag) return@suspendTransaction TradeReceiptCommit.REJECTED
            }
            for (side in sides) {
                Inventories.deleteWhere {
                    (Inventories.characterId eq side.characterId) and
                        (Inventories.storageType eq EThings.BAG)
                }
                if (side.afterBag.isNotEmpty()) {
                    Inventories.batchInsert(side.afterBag.entries.toList()) { (slot, thing) ->
                        this[Inventories.characterId] = side.characterId
                        this[Inventories.storageType] = EThings.BAG
                        this[Inventories.slot] = slot
                        this.setThingData(thing)
                    }
                }
                check(CharacterMoney.update({
                    CharacterMoney.characterId eq side.characterId
                }) { it[CharacterMoney.gold] = side.afterGold } == 1) {
                    "Missing trade-money row during atomic settlement"
                }
            }
            // Receipt insert belongs to this SAME transaction. A duplicate
            // UUID insert rolls back the entire BAG+gold transfer.
            if (operationId != null) {
                TradeSettlementReceipts.insert {
                    it[TradeSettlementReceipts.operationId] = operationId
                    it[TradeSettlementReceipts.firstCharacterId] = sides[0].characterId
                    it[TradeSettlementReceipts.secondCharacterId] = sides[1].characterId
                    it[TradeSettlementReceipts.snapshotDigest] = TradeSnapshotDigest.of(first, second)
                    it[TradeSettlementReceipts.committedAtEpochMs] = System.currentTimeMillis()
                }
            }
            TradeReceiptCommit.COMMITTED_NOW
        }
    }

    override suspend fun loadMoney(characterId: Long): Pair<Int, Int> =
        suspendTransaction {
            CharacterMoney.selectAll()
                .where { CharacterMoney.characterId eq characterId }
                .singleOrNull()
                ?.let { Pair(it[CharacterMoney.gold], it[CharacterMoney.auctionGold]) }
                ?: Pair(0, 0)
        }

    override suspend fun saveMoney(characterId: Long, gold: Int, auctionGold: Int) =
        suspendTransaction {
            CharacterMoney.upsert {
                it[CharacterMoney.characterId] = characterId
                it[CharacterMoney.gold] = gold
                it[CharacterMoney.auctionGold] = auctionGold
            }
            Unit
        }

    // ── Helper riêng ──

    /**
     * Map ResultRow → ThingData
     */
    private fun ResultRow.toThingData() = ThingData(
        itemId = this[Inventories.itemId],
        quantity = this[Inventories.quantity],
        damage = this[Inventories.damage],
        element = this[Inventories.element],
        elementValue = this[Inventories.elementValue],
        proofKind = this[Inventories.proofKind],
        growLevel = this[Inventories.growLevel],
        growExp = this[Inventories.growExp],
        specialKind = this[Inventories.specialKind],
        stoneAttr = this[Inventories.stoneAttr],
        stoneLevel = this[Inventories.stoneLevel],
        enhanceLevel = this[Inventories.enhanceLevel],
        deleteTime = this[Inventories.deleteTime],
        damagedItemId = this[Inventories.damagedItemId],
        isLocked = this[Inventories.isLocked],
        reinforced = this[Inventories.reinforced],
        affix1 = this[Inventories.affix1],
        affix2 = this[Inventories.affix2],
        affix3 = this[Inventories.affix3],
        styleLevel = this[Inventories.styleLevel],
        mythLevel = this[Inventories.mythLevel],
        myth = listOf(
            MythAttribute(this[Inventories.myth1Kind], this[Inventories.myth1Value], this[Inventories.myth1Extra]),
            MythAttribute(this[Inventories.myth2Kind], this[Inventories.myth2Value], this[Inventories.myth2Extra]),
            MythAttribute(this[Inventories.myth3Kind], this[Inventories.myth3Value], this[Inventories.myth3Extra]),
            MythAttribute(this[Inventories.myth4Kind], this[Inventories.myth4Value], this[Inventories.myth4Extra])
        )
    )

    /**
     * Thiết lập trường cho ThingData → INSERT/UPSERT
     */
    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.setThingData(thing: ThingData) {
        this[Inventories.itemId] = thing.itemId
        this[Inventories.quantity] = thing.quantity
        this[Inventories.damage] = thing.damage
        this[Inventories.element] = thing.element
        this[Inventories.elementValue] = thing.elementValue
        this[Inventories.proofKind] = thing.proofKind
        this[Inventories.growLevel] = thing.growLevel
        this[Inventories.growExp] = thing.growExp
        this[Inventories.specialKind] = thing.specialKind
        this[Inventories.stoneAttr] = thing.stoneAttr
        this[Inventories.stoneLevel] = thing.stoneLevel
        this[Inventories.enhanceLevel] = thing.enhanceLevel
        this[Inventories.deleteTime] = thing.deleteTime
        this[Inventories.damagedItemId] = thing.damagedItemId
        this[Inventories.isLocked] = thing.isLocked
        this[Inventories.reinforced] = thing.reinforced
        this[Inventories.affix1] = thing.affix1
        this[Inventories.affix2] = thing.affix2
        this[Inventories.affix3] = thing.affix3
        this[Inventories.styleLevel] = thing.styleLevel
        this[Inventories.mythLevel] = thing.mythLevel
        this[Inventories.myth1Kind] = thing.myth[0].kind
        this[Inventories.myth1Value] = thing.myth[0].value
        this[Inventories.myth1Extra] = thing.myth[0].extra
        this[Inventories.myth2Kind] = thing.myth[1].kind
        this[Inventories.myth2Value] = thing.myth[1].value
        this[Inventories.myth2Extra] = thing.myth[1].extra
        this[Inventories.myth3Kind] = thing.myth[2].kind
        this[Inventories.myth3Value] = thing.myth[2].value
        this[Inventories.myth3Extra] = thing.myth[2].extra
        this[Inventories.myth4Kind] = thing.myth[3].kind
        this[Inventories.myth4Value] = thing.myth[3].value
        this[Inventories.myth4Extra] = thing.myth[3].extra
    }
}

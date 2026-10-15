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
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.CharacterHouseFurnitureOwned
import tw.tsm.domain.interfaces.repositories.HouseFurnitureRedemption
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterBattlePassSeasons
import tw.tsm.persistence.tables.CharacterWelfareExchangeReceipts
import tw.tsm.domain.constants.EThings

/**
 * Triển khai InventoryRepository bằng Exposed ORM
 *
 * Dùng bảng đơn inventories chứa túi đồ/trang bị/Tiền Trang, phân biệt bằng storageType.
 */
class InventoryRepositoryImpl : InventoryRepository {

    override suspend fun loadOwnedHouseFurniture(characterId: Long): Map<Int, Int> =
        suspendTransaction {
            CharacterHouseFurnitureOwned.selectAll()
                .where { CharacterHouseFurnitureOwned.characterId eq characterId }
                .associate {
                    it[CharacterHouseFurnitureOwned.furnitureId] to
                        it[CharacterHouseFurnitureOwned.quantity]
                }
        }

    /** Source BAG debit and House furniture credit MUST commit in one SQL transaction. */
    override suspend fun redeemHouseFurnitureAtomic(
        characterId: Long, slot: Int, itemId: Int, furnitureId: Int, quantity: Int,
    ): HouseFurnitureRedemption? = suspendTransaction {
        if (quantity !in 1..255 || furnitureId !in 1..65535) return@suspendTransaction null
        val key = (Inventories.characterId eq characterId) and
            (Inventories.storageType eq EThings.BAG) and (Inventories.slot eq slot)
        val row = Inventories.selectAll().where { key }.forUpdate().firstOrNull()
            ?: return@suspendTransaction null
        val actualItemId = row[Inventories.itemId]
        val available = row[Inventories.quantity]
        if (actualItemId != itemId || available < quantity || available <= 0 ||
            row[Inventories.isLocked]) return@suspendTransaction null

        val ownerKey = (CharacterHouseFurnitureOwned.characterId eq characterId) and
            (CharacterHouseFurnitureOwned.furnitureId eq furnitureId)
        val current = CharacterHouseFurnitureOwned.selectAll().where { ownerKey }
            .forUpdate().firstOrNull()?.get(CharacterHouseFurnitureOwned.quantity) ?: 0
        if (current !in 0..255 || quantity > 255 - current) return@suspendTransaction null

        val after = available - quantity
        if (after == 0) Inventories.deleteWhere { key }
        else Inventories.update({ key }) { it[Inventories.quantity] = after }

        CharacterHouseFurnitureOwned.upsert {
            it[CharacterHouseFurnitureOwned.characterId] = characterId
            it[CharacterHouseFurnitureOwned.furnitureId] = furnitureId
            it[CharacterHouseFurnitureOwned.quantity] = current + quantity
        }
        HouseFurnitureRedemption(
            bagAfter = if (after == 0) null else row.toThingData().copy(quantity = after),
            ownedTotal = current + quantity,
        )
    }


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

    /**
     * Recorded gift exchange and BAG update share the same transaction.
     * No receipt can be committed if any BAG write fails.
     */
    override suspend fun saveWelfareExchangeAtomic(
        characterId: Long, changes: Map<Int, ThingData?>,
        recipeDay: Int, exchangeCount: Int, resultItemId: Int, resultQuantity: Int,
    ) = suspendTransaction {
        require(characterId > 0L && recipeDay >= 0 && exchangeCount in 1..50 &&
            resultItemId in 1..65535 && resultQuantity in 1..Int.MAX_VALUE &&
            changes.isNotEmpty()) { "Invalid welfare exchange receipt metadata" }
        for ((slot, thing) in changes) {
            if (thing == null || thing.isEmpty) {
                Inventories.deleteWhere {
                    (Inventories.characterId eq characterId) and
                    (Inventories.storageType eq EThings.BAG) and
                    (Inventories.slot eq slot)
                }
            } else {
                Inventories.upsert {
                    it[Inventories.characterId] = characterId
                    it[Inventories.storageType] = EThings.BAG
                    it[Inventories.slot] = slot
                    it.setThingData(thing)
                }
            }
        }
        CharacterWelfareExchangeReceipts.insert {
            it[CharacterWelfareExchangeReceipts.characterId] = characterId
            it[CharacterWelfareExchangeReceipts.recipeDay] = recipeDay
            it[CharacterWelfareExchangeReceipts.exchangeCount] = exchangeCount
            it[CharacterWelfareExchangeReceipts.resultItemId] = resultItemId
            it[CharacterWelfareExchangeReceipts.resultQuantity] = resultQuantity
            it[CharacterWelfareExchangeReceipts.claimedAtEpochMs] =
                System.currentTimeMillis()
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

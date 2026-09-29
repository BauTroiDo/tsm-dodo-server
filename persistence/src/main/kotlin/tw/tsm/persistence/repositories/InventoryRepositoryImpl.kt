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
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.Inventories

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
        styleLevel = this[Inventories.styleLevel]
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
    }
}

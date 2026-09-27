package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.MountRepository
import tw.tsm.domain.valueobjects.MountData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.CharacterMountEquips
import tw.tsm.persistence.tables.CharacterMounts

/**
 * 坐騎資料存取實作（Exposed ORM）
 *
 * 主表用 upsert（characterId 唯一），裝備表用 delete + batchInsert。
 */
class MountRepositoryImpl : MountRepository {

    override suspend fun findByCharacterId(characterId: Long): MountData? =
        suspendTransaction {
            val row = CharacterMounts.selectAll()
                .where { CharacterMounts.characterId eq characterId }
                .firstOrNull() ?: return@suspendTransaction null

            val data = MountData(
                level = row[CharacterMounts.level],
                currentNpcId = row[CharacterMounts.currentNpcId]
            )
            data.attributePoints[1] = row[CharacterMounts.attrPoint1]
            data.attributePoints[2] = row[CharacterMounts.attrPoint2]
            data.attributePoints[3] = row[CharacterMounts.attrPoint3]
            data.attributePoints[4] = row[CharacterMounts.attrPoint4]
            data.attributePoints[5] = row[CharacterMounts.attrPoint5]
            data.attributePoints[6] = row[CharacterMounts.attrPoint6]

            // 載入裝備
            CharacterMountEquips.selectAll()
                .where { CharacterMountEquips.characterId eq characterId }
                .forEach { eqRow ->
                    val thing = ThingData(
                        itemId = eqRow[CharacterMountEquips.itemId],
                        quantity = eqRow[CharacterMountEquips.quantity],
                        damage = eqRow[CharacterMountEquips.damage],
                        element = eqRow[CharacterMountEquips.element],
                        elementValue = eqRow[CharacterMountEquips.elementValue],
                        proofKind = eqRow[CharacterMountEquips.proofKind],
                        growLevel = eqRow[CharacterMountEquips.growLevel],
                        growExp = eqRow[CharacterMountEquips.growExp],
                        specialKind = eqRow[CharacterMountEquips.specialKind],
                        stoneAttr = eqRow[CharacterMountEquips.stoneAttr],
                        stoneLevel = eqRow[CharacterMountEquips.stoneLevel],
                        enhanceLevel = eqRow[CharacterMountEquips.enhanceLevel],
                        deleteTime = eqRow[CharacterMountEquips.deleteTime],
                        damagedItemId = eqRow[CharacterMountEquips.damagedItemId],
                        isLocked = eqRow[CharacterMountEquips.isLocked],
                        reinforced = eqRow[CharacterMountEquips.reinforced],
                        affix1 = eqRow[CharacterMountEquips.affix1],
                        affix2 = eqRow[CharacterMountEquips.affix2],
                        affix3 = eqRow[CharacterMountEquips.affix3],
                        styleLevel = eqRow[CharacterMountEquips.styleLevel]
                    )
                    data.equips[eqRow[CharacterMountEquips.fitType]] = thing
                }

            data
        }

    override suspend fun save(characterId: Long, data: MountData) {
        suspendTransaction {
            // 主表 upsert
            val existing = CharacterMounts.selectAll()
                .where { CharacterMounts.characterId eq characterId }
                .firstOrNull()

            if (existing != null) {
                CharacterMounts.update({ CharacterMounts.characterId eq characterId }) {
                    it[level] = data.level
                    it[attrPoint1] = data.attributePoints[1]
                    it[attrPoint2] = data.attributePoints[2]
                    it[attrPoint3] = data.attributePoints[3]
                    it[attrPoint4] = data.attributePoints[4]
                    it[attrPoint5] = data.attributePoints[5]
                    it[attrPoint6] = data.attributePoints[6]
                    it[currentNpcId] = data.currentNpcId
                }
            } else {
                CharacterMounts.insert {
                    it[CharacterMounts.characterId] = characterId
                    it[level] = data.level
                    it[attrPoint1] = data.attributePoints[1]
                    it[attrPoint2] = data.attributePoints[2]
                    it[attrPoint3] = data.attributePoints[3]
                    it[attrPoint4] = data.attributePoints[4]
                    it[attrPoint5] = data.attributePoints[5]
                    it[attrPoint6] = data.attributePoints[6]
                    it[currentNpcId] = data.currentNpcId
                }
            }

            // 裝備表 delete + batchInsert
            CharacterMountEquips.deleteWhere { CharacterMountEquips.characterId eq characterId }
            val equipList = data.equips.entries.filter { !it.value.isEmpty }
            if (equipList.isNotEmpty()) {
                CharacterMountEquips.batchInsert(equipList) { (fitType, thing) ->
                    this[CharacterMountEquips.characterId] = characterId
                    this[CharacterMountEquips.fitType] = fitType
                    this[CharacterMountEquips.itemId] = thing.itemId
                    this[CharacterMountEquips.quantity] = thing.quantity
                    this[CharacterMountEquips.damage] = thing.damage
                    this[CharacterMountEquips.element] = thing.element
                    this[CharacterMountEquips.elementValue] = thing.elementValue
                    this[CharacterMountEquips.proofKind] = thing.proofKind
                    this[CharacterMountEquips.growLevel] = thing.growLevel
                    this[CharacterMountEquips.growExp] = thing.growExp
                    this[CharacterMountEquips.specialKind] = thing.specialKind
                    this[CharacterMountEquips.stoneAttr] = thing.stoneAttr
                    this[CharacterMountEquips.stoneLevel] = thing.stoneLevel
                    this[CharacterMountEquips.enhanceLevel] = thing.enhanceLevel
                    this[CharacterMountEquips.deleteTime] = thing.deleteTime
                    this[CharacterMountEquips.damagedItemId] = thing.damagedItemId
                    this[CharacterMountEquips.isLocked] = thing.isLocked
                    this[CharacterMountEquips.reinforced] = thing.reinforced
                    this[CharacterMountEquips.affix1] = thing.affix1
                    this[CharacterMountEquips.affix2] = thing.affix2
                    this[CharacterMountEquips.affix3] = thing.affix3
                    this[CharacterMountEquips.styleLevel] = thing.styleLevel
                }
            }
        }
    }
}

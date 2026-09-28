package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.CharacterPetEquips
import tw.tsm.persistence.tables.CharacterPets

/**
 * 武將資料存取實作（Exposed ORM）
 *
 * 武將本體存 CharacterPets；身上裝備存 CharacterPetEquips，
 * 以 (storageType, slotIndex) 對應本體，載入時合併進 FollowNpcData.equips。
 */
class PetRepositoryImpl : PetRepository {

    override suspend fun findByCharacterId(characterId: Long): List<FollowNpcData> =
        suspendTransaction {
            // 先載入全部武將裝備，依 (storageType, slotIndex) 分組
            val equipsByPet = mutableMapOf<Pair<Int, Int>, MutableMap<Int, ThingData>>()
            CharacterPetEquips.selectAll()
                .where { CharacterPetEquips.characterId eq characterId }
                .forEach { eqRow ->
                    val key = eqRow[CharacterPetEquips.storageType] to eqRow[CharacterPetEquips.slotIndex]
                    equipsByPet.getOrPut(key) { mutableMapOf() }[eqRow[CharacterPetEquips.fitType]] = ThingData(
                        itemId = eqRow[CharacterPetEquips.itemId],
                        quantity = eqRow[CharacterPetEquips.quantity],
                        damage = eqRow[CharacterPetEquips.damage],
                        element = eqRow[CharacterPetEquips.element],
                        elementValue = eqRow[CharacterPetEquips.elementValue],
                        proofKind = eqRow[CharacterPetEquips.proofKind],
                        growLevel = eqRow[CharacterPetEquips.growLevel],
                        growExp = eqRow[CharacterPetEquips.growExp],
                        specialKind = eqRow[CharacterPetEquips.specialKind],
                        stoneAttr = eqRow[CharacterPetEquips.stoneAttr],
                        stoneLevel = eqRow[CharacterPetEquips.stoneLevel],
                        enhanceLevel = eqRow[CharacterPetEquips.enhanceLevel],
                        deleteTime = eqRow[CharacterPetEquips.deleteTime],
                        damagedItemId = eqRow[CharacterPetEquips.damagedItemId],
                        isLocked = eqRow[CharacterPetEquips.isLocked],
                        reinforced = eqRow[CharacterPetEquips.reinforced],
                        affix1 = eqRow[CharacterPetEquips.affix1],
                        affix2 = eqRow[CharacterPetEquips.affix2],
                        affix3 = eqRow[CharacterPetEquips.affix3],
                        styleLevel = eqRow[CharacterPetEquips.styleLevel]
                    )
                }

            CharacterPets.selectAll()
                .where { CharacterPets.characterId eq characterId }
                .map { row ->
                    FollowNpcData(
                        slotIndex = row[CharacterPets.slotIndex],
                        npcId = row[CharacterPets.npcId],
                        storageType = row[CharacterPets.storageType],
                        name = row[CharacterPets.name],
                        level = row[CharacterPets.level],
                        exp = row[CharacterPets.exp],
                        hp = row[CharacterPets.hp],
                        sp = row[CharacterPets.sp],
                        atk = row[CharacterPets.atk],
                        def = row[CharacterPets.def],
                        agi = row[CharacterPets.agi],
                        intelligence = row[CharacterPets.intelligence],
                        hpx = row[CharacterPets.hpx],
                        spx = row[CharacterPets.spx],
                        deathCount = row[CharacterPets.deathCount],
                        loyalty = row[CharacterPets.loyalty],
                        skillPoint = row[CharacterPets.skillPoint],
                        skill1Lv = row[CharacterPets.skill1Lv],
                        skill2Lv = row[CharacterPets.skill2Lv],
                        skill3Lv = row[CharacterPets.skill3Lv],
                        canGrow = row[CharacterPets.canGrow],
                        soulId = row[CharacterPets.soulId],
                        upgradeLv = row[CharacterPets.upgradeLv],
                        ascendPillCount = row[CharacterPets.ascendPillCount],
                        canUseSpecialSkill = row[CharacterPets.canUseSpecialSkill],
                        hpPillCount = row[CharacterPets.hpPillCount],
                        spPillCount = row[CharacterPets.spPillCount],
                        skill4Lv = row[CharacterPets.skill4Lv],
                        conjureGrow = row[CharacterPets.conjureGrow],
                        turnKind = row[CharacterPets.turnKind],
                        surplus = row[CharacterPets.surplus],
                        lvUpTime = row[CharacterPets.lvUpTime],
                        seedFixed = row[CharacterPets.seedFixed],
                        equips = equipsByPet[row[CharacterPets.storageType] to row[CharacterPets.slotIndex]] ?: emptyMap()
                    )
                }
        }

    override suspend fun saveAll(characterId: Long, pets: List<FollowNpcData>) {
        suspendTransaction {
            CharacterPets.deleteWhere { CharacterPets.characterId eq characterId }
            if (pets.isNotEmpty()) {
                CharacterPets.batchInsert(pets) { pet ->
                    this[CharacterPets.characterId] = characterId
                    this[CharacterPets.storageType] = pet.storageType
                    this[CharacterPets.slotIndex] = pet.slotIndex
                    this[CharacterPets.npcId] = pet.npcId
                    this[CharacterPets.name] = pet.name
                    this[CharacterPets.level] = pet.level
                    this[CharacterPets.exp] = pet.exp
                    this[CharacterPets.hp] = pet.hp
                    this[CharacterPets.sp] = pet.sp
                    this[CharacterPets.atk] = pet.atk
                    this[CharacterPets.def] = pet.def
                    this[CharacterPets.agi] = pet.agi
                    this[CharacterPets.intelligence] = pet.intelligence
                    this[CharacterPets.hpx] = pet.hpx
                    this[CharacterPets.spx] = pet.spx
                    this[CharacterPets.deathCount] = pet.deathCount
                    this[CharacterPets.loyalty] = pet.loyalty
                    this[CharacterPets.skillPoint] = pet.skillPoint
                    this[CharacterPets.skill1Lv] = pet.skill1Lv
                    this[CharacterPets.skill2Lv] = pet.skill2Lv
                    this[CharacterPets.skill3Lv] = pet.skill3Lv
                    this[CharacterPets.canGrow] = pet.canGrow
                    this[CharacterPets.soulId] = pet.soulId
                    this[CharacterPets.upgradeLv] = pet.upgradeLv
                    this[CharacterPets.ascendPillCount] = pet.ascendPillCount
                    this[CharacterPets.canUseSpecialSkill] = pet.canUseSpecialSkill
                    this[CharacterPets.hpPillCount] = pet.hpPillCount
                    this[CharacterPets.spPillCount] = pet.spPillCount
                    this[CharacterPets.skill4Lv] = pet.skill4Lv
                    this[CharacterPets.conjureGrow] = pet.conjureGrow
                    this[CharacterPets.turnKind] = pet.turnKind
                    this[CharacterPets.surplus] = pet.surplus
                    this[CharacterPets.lvUpTime] = pet.lvUpTime
                    this[CharacterPets.seedFixed] = pet.seedFixed
                }
            }

            // 武將裝備：整批重寫（與本體同交易，確保一致）
            CharacterPetEquips.deleteWhere { CharacterPetEquips.characterId eq characterId }
            val equipEntries = pets.flatMap { pet ->
                pet.equips.entries
                    .filter { !it.value.isEmpty }
                    .map { Triple(pet, it.key, it.value) }
            }
            if (equipEntries.isNotEmpty()) {
                CharacterPetEquips.batchInsert(equipEntries) { (pet, fitType, thing) ->
                    this[CharacterPetEquips.characterId] = characterId
                    this[CharacterPetEquips.storageType] = pet.storageType
                    this[CharacterPetEquips.slotIndex] = pet.slotIndex
                    this[CharacterPetEquips.fitType] = fitType
                    this[CharacterPetEquips.itemId] = thing.itemId
                    this[CharacterPetEquips.quantity] = thing.quantity
                    this[CharacterPetEquips.damage] = thing.damage
                    this[CharacterPetEquips.element] = thing.element
                    this[CharacterPetEquips.elementValue] = thing.elementValue
                    this[CharacterPetEquips.proofKind] = thing.proofKind
                    this[CharacterPetEquips.growLevel] = thing.growLevel
                    this[CharacterPetEquips.growExp] = thing.growExp
                    this[CharacterPetEquips.specialKind] = thing.specialKind
                    this[CharacterPetEquips.stoneAttr] = thing.stoneAttr
                    this[CharacterPetEquips.stoneLevel] = thing.stoneLevel
                    this[CharacterPetEquips.enhanceLevel] = thing.enhanceLevel
                    this[CharacterPetEquips.deleteTime] = thing.deleteTime
                    this[CharacterPetEquips.damagedItemId] = thing.damagedItemId
                    this[CharacterPetEquips.isLocked] = thing.isLocked
                    this[CharacterPetEquips.reinforced] = thing.reinforced
                    this[CharacterPetEquips.affix1] = thing.affix1
                    this[CharacterPetEquips.affix2] = thing.affix2
                    this[CharacterPetEquips.affix3] = thing.affix3
                    this[CharacterPetEquips.styleLevel] = thing.styleLevel
                }
            }
        }
    }

    override suspend fun delete(characterId: Long, storageType: Int, slotIndex: Int) {
        suspendTransaction {
            CharacterPets.deleteWhere {
                (CharacterPets.characterId eq characterId) and
                    (CharacterPets.storageType eq storageType) and
                    (CharacterPets.slotIndex eq slotIndex)
            }
            CharacterPetEquips.deleteWhere {
                (CharacterPetEquips.characterId eq characterId) and
                    (CharacterPetEquips.storageType eq storageType) and
                    (CharacterPetEquips.slotIndex eq slotIndex)
            }
        }
    }
}

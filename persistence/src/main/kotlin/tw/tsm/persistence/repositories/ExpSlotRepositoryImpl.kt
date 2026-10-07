package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import tw.tsm.persistence.tables.ExpSlotStates

class ExpSlotRepositoryImpl : ExpSlotRepository {
    override suspend fun loadJackpotExp(): Long = suspendTransaction {
        ExpSlotStates.selectAll()
            .where { ExpSlotStates.id eq STATE_ID }
            .singleOrNull()
            ?.get(ExpSlotStates.jackpotExp)
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    override suspend fun saveJackpotExp(value: Long) {
        suspendTransaction {
            ExpSlotStates.upsert {
                it[id] = STATE_ID
                it[jackpotExp] = value.coerceAtLeast(0L)
            }
        }
    }

    override suspend fun settleSpin(
        characterId: Long, bagSlot: Int, coin: ThingData, count: Int,
        before: FollowNpcData, after: FollowNpcData,
        expectedJackpot: Long?, nextJackpot: Long,
    ) {
        suspendTransaction {
            val bagKey = (Inventories.characterId eq characterId) and
                (Inventories.storageType eq EThings.BAG) and (Inventories.slot eq bagSlot)
            val row = Inventories.selectAll().where { bagKey }.forUpdate().singleOrNull()
                ?: error("EXP slot coin missing")
            check(count > 0 && row[Inventories.itemId] == coin.itemId &&
                row[Inventories.quantity] == coin.quantity && coin.quantity >= count) {
                "EXP slot coin changed"
            }
            val petKey = (CharacterPets.characterId eq characterId) and
                (CharacterPets.storageType eq before.storageType) and
                (CharacterPets.slotIndex eq before.slotIndex)
            val pet = CharacterPets.selectAll().where { petKey }.forUpdate().singleOrNull()
                ?: error("EXP slot pet missing")
            check(pet[CharacterPets.npcId] == before.npcId &&
                pet[CharacterPets.exp] <= before.exp && pet[CharacterPets.level] <= before.level &&
                pet[CharacterPets.turnKind] == before.turnKind && pet[CharacterPets.canGrow] == before.canGrow &&
                pet[CharacterPets.skillPoint] <= before.skillPoint) {
                "EXP slot pet changed; retry after save"
            }
            if (expectedJackpot != null) {
                val state = ExpSlotStates.selectAll().where { ExpSlotStates.id eq STATE_ID }
                    .forUpdate().singleOrNull()
                check((state?.get(ExpSlotStates.jackpotExp) ?: 0L) == expectedJackpot) {
                    "EXP slot jackpot changed"
                }
                ExpSlotStates.upsert {
                    it[id] = STATE_ID
                    it[jackpotExp] = nextJackpot
                }
            }
            if (coin.quantity == count) {
                Inventories.deleteWhere { bagKey }
            } else {
                Inventories.update({ bagKey }) { it[quantity] = coin.quantity - count }
            }
            CharacterPets.update({ petKey }) {
                it[level] = after.level
                it[exp] = after.exp
                it[skillPoint] = after.skillPoint
                if (after.level > before.level) {
                    it[hp] = after.hp
                    it[sp] = after.sp
                }
            }
        }
    }

    companion object {
        private const val STATE_ID = 1
    }
}

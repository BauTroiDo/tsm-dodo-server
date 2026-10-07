package tw.tsm.tests

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.repositories.ExpSlotRepositoryImpl
import tw.tsm.persistence.tables.*
import java.util.UUID

class ExpSlotPersistenceTest {
    private fun database(): Database {
        val db = Database.connect("jdbc:h2:mem:exp-slot-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL", driver="org.h2.Driver")
        transaction(db) {
            SchemaUtils.create(Inventories, CharacterPets, ExpSlotStates)
            Inventories.insert {
                it[characterId]=42; it[storageType]=EThings.BAG; it[slot]=7
                it[itemId]=31207; it[quantity]=10
            }
            CharacterPets.insert {
                it[characterId]=42; it[storageType]=1; it[slotIndex]=1; it[npcId]=123
                it[level]=20; it[exp]=1000; it[hp]=100; it[sp]=10
            }
            ExpSlotStates.insert { it[id]=1; it[jackpotExp]=12345 }
        }
        return db
    }
    private val before = FollowNpcData(1,123,level=20,exp=1000,hp=100,sp=10)
    private val after = before.copy(level=21,exp=2000,skillPoint=1,hp=200,sp=20)
    private val coin = ThingData(itemId=31207,quantity=10)

    @Test fun `coin pet and jackpot commit together and stale replay fails`() = runBlocking {
        val db = database()
        val repo = ExpSlotRepositoryImpl()
        repo.settleSpin(42,7,coin,10,before,after,12345,0)
        transaction(db) {
            assertEquals(0L,Inventories.selectAll().count())
            val pet = CharacterPets.selectAll().single()
            assertEquals(2000L,pet[CharacterPets.exp])
            assertEquals(21,pet[CharacterPets.level])
            assertEquals(200L,pet[CharacterPets.hp])
            assertEquals(0L,ExpSlotStates.selectAll().single()[ExpSlotStates.jackpotExp])
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repo.settleSpin(42,7,coin,10,before,after,12345,0) }
        }
        Unit
    }

    @Test fun `SQL failure after coin debit and jackpot reset rolls all tables back`() = runBlocking {
        val db = database()
        transaction(db) { exec("""ALTER TABLE character_pets ADD CONSTRAINT test_level_limit CHECK ("level" <= 20)""") }
        assertThrows(Exception::class.java) {
            runBlocking { ExpSlotRepositoryImpl().settleSpin(42,7,coin,10,before,after,12345,0) }
        }
        transaction(db) {
            assertEquals(10,Inventories.selectAll().single()[Inventories.quantity])
            val pet = CharacterPets.selectAll().single()
            assertEquals(1000L,pet[CharacterPets.exp])
            assertEquals(20,pet[CharacterPets.level])
            assertEquals(12345L,ExpSlotStates.selectAll().single()[ExpSlotStates.jackpotExp])
        }
    }

    @Test fun `stale jackpot rejects without payment or pet changes`() = runBlocking {
        val db = database()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { ExpSlotRepositoryImpl().settleSpin(42,7,coin,1,before,after,999,0) }
        }
        transaction(db) {
            assertEquals(10,Inventories.selectAll().single()[Inventories.quantity])
            assertEquals(1000L,CharacterPets.selectAll().single()[CharacterPets.exp])
            assertEquals(12345L,ExpSlotStates.selectAll().single()[ExpSlotStates.jackpotExp])
        }
    }
}

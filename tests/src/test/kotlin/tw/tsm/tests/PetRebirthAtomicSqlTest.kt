package tw.tsm.tests

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EThings
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.persistence.tables.CharacterPetEquips
import tw.tsm.persistence.tables.Inventories
import java.util.UUID

/** Exercises the REAL SQL repository against private in-memory H2; never DEV MySQL. */
class PetRebirthAtomicSqlTest {
    private val character = 987654321L
    private val old = FollowNpcData(
        slotIndex = 1, npcId = 41003, turnKind = 1,
        level = 60, loyalty = 100,
        instanceId = "1bcb81eb-4e48-4f29-ad0d-486d761e96a6",
    )
    private val next = old.copy(npcId = 45001, turnKind = 2, level = 1, loyalty = 60)

    private fun fixture(block: suspend (InventoryRepositoryImpl, Database) -> Unit) {
        val prior = TransactionManager.defaultDatabase
        val db = Database.connect(
            "jdbc:h2:mem:pet_rebirth_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver",
        )
        TransactionManager.defaultDatabase = db
        try {
            transaction(db) {
                SchemaUtils.create(Inventories, CharacterPets, CharacterPetEquips)
                Inventories.insert {
                    it[characterId] = character
                    it[storageType] = EThings.BAG
                    it[slot] = 7
                    it[itemId] = 51001
                    it[quantity] = 2
                }
                CharacterPets.insert {
                    it[characterId] = character
                    it[instanceId] = old.instanceId
                    it[storageType] = 1
                    it[slotIndex] = 1
                    it[npcId] = old.npcId
                    it[turnKind] = old.turnKind
                    it[level] = old.level
                }
            }
            runBlocking { block(InventoryRepositoryImpl(), db) }
        } finally {
            TransactionManager.defaultDatabase = prior
        }
    }

    private fun assertData(db: Database, npc: Int, turn: Int, tokenQty: Int) {
        transaction(db) {
            val pet = CharacterPets.selectAll().single()
            assertEquals(npc, pet[CharacterPets.npcId])
            assertEquals(turn, pet[CharacterPets.turnKind])
            assertEquals(old.instanceId, pet[CharacterPets.instanceId])
            assertEquals(tokenQty, Inventories.selectAll().single()[Inventories.quantity])
        }
    }

    @Test fun commitsPetAndTokenTogetherAndRejectsReplay() = fixture { repo, db ->
        val result = repo.commitPetRebirthAtomic(character, 7, 51001, old, next)
        assertNotNull(result)
        assertEquals(1, result!!.bagAfter!!.quantity)
        assertData(db, 45001, 2, 1)

        assertNull(repo.commitPetRebirthAtomic(character, 7, 51001, old, next))
        assertData(db, 45001, 2, 1)
    }

    @Test fun refusesWrongTokenWithoutTouchingPet() = fixture { repo, db ->
        assertNull(repo.commitPetRebirthAtomic(character, 7, 99999, old, next))
        assertData(db, 41003, 1, 2)
    }

    @Test fun sqlFailureDuringTokenDebitRollsPetUpdateBack() = fixture { repo, db ->
        transaction(db) {
            exec("ALTER TABLE inventories ADD CONSTRAINT token_min_two CHECK (quantity >= 2)")
        }
        assertThrows(Exception::class.java) {
            runBlocking { repo.commitPetRebirthAtomic(character, 7, 51001, old, next) }
        }
        assertData(db, 41003, 1, 2)
    }

    @Test fun refusesStalePetInstanceWithoutDebitingToken() = fixture { repo, db ->
        val other = old.copy(instanceId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        assertNull(repo.commitPetRebirthAtomic(character, 7, 51001, other, next))
        assertData(db, 41003, 1, 2)
    }

    @Test fun lastTokenIsDeletedAndPetRemainsDurable() = fixture { repo, db ->
        transaction(db) {
            Inventories.update({ Inventories.characterId eq character }) {
                it[quantity] = 1
            }
        }
        val result = repo.commitPetRebirthAtomic(character, 7, 51001, old, next)
        assertNotNull(result)
        assertNull(result!!.bagAfter)
        transaction(db) {
            assertEquals(0L, Inventories.selectAll().count())
            val saved = CharacterPets.selectAll().single()
            assertEquals(45001, saved[CharacterPets.npcId])
            assertEquals(2, saved[CharacterPets.turnKind])
            assertEquals(old.instanceId, saved[CharacterPets.instanceId])
        }
    }

    @Test fun refusesPetWithEquipmentWithoutDebitingToken() = fixture { repo, db ->
        transaction(db) {
            CharacterPetEquips.insert {
                it[characterId] = character
                it[storageType] = 1
                it[slotIndex] = 1
                it[fitType] = 1
                it[itemId] = 204
                it[quantity] = 1
            }
        }
        assertNull(repo.commitPetRebirthAtomic(character, 7, 51001, old, next))
        assertData(db, 41003, 1, 2)
    }
}

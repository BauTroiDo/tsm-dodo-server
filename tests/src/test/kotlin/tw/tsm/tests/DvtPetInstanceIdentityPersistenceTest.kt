package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.persistence.repositories.PetRepositoryImpl
import tw.tsm.persistence.tables.CharacterPetEquips
import tw.tsm.persistence.tables.CharacterPets

/** Child binding must use immutable individual identity, never NPC template or slot. */
class DvtPetInstanceIdentityPersistenceTest {
    private fun db(): Database {
        val db = Database.connect(
            "jdbc:h2:mem:dvt-pet-identity-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(CharacterPets, CharacterPetEquips)
            CharacterPets.insert {
                it[characterId] = 101L
                it[storageType] = 1
                it[slotIndex] = 1
                it[npcId] = 15049
                it[seedFixed] = true
            }
            // Same template, another individual, in the cart.
            CharacterPets.insert {
                it[characterId] = 101L
                it[storageType] = 2
                it[slotIndex] = 1
                it[npcId] = 15049
                it[seedFixed] = true
            }
            assertEquals(listOf("", ""), CharacterPets.selectAll()
                .where { CharacterPets.characterId eq 101L }
                .map { it[CharacterPets.instanceId] })
        }
        return db
    }

    @Test fun legacyRosterIsAssignedTwoDurableUuidsImmediatelyOnFirstRead() = runBlocking {
        val db = db()
        val repo = PetRepositoryImpl()
        val initial = repo.findByCharacterId(101L)
        assertEquals(2, initial.size)
        val keys = initial.map { it.instanceId }
        assertEquals(2, keys.toSet().size)
        assertTrue(keys.all { UUID.fromString(it).toString() == it })
        transaction(db) {
            assertEquals(keys.toSet(), CharacterPets.selectAll()
                .where { CharacterPets.characterId eq 101L }
                .map { it[CharacterPets.instanceId] }.toSet())
        }
        // No saveAll/autosave was requested: repeated login must keep IDs.
        assertEquals(keys, repo.findByCharacterId(101L).map { it.instanceId })
    }

    @Test fun movingAndReSavingSameNpcTemplatePreservesUniqueInstanceIdentity() = runBlocking {
        val db = db()
        val repo = PetRepositoryImpl()
        val before = repo.findByCharacterId(101L)
        val originalRowIds = transaction(db) {
            CharacterPets.selectAll().where { CharacterPets.characterId eq 101L }
                .map { it[CharacterPets.id] }.toSet()
        }
        val first = before[0].copy(storageType = 3, slotIndex = 17, level = 37)
        val second = before[1].copy(storageType = 1, slotIndex = 2)
        repo.saveAll(101L, listOf(first, second))
        val after = repo.findByCharacterId(101L)
        assertEquals(before.map { it.instanceId }, after.map { it.instanceId })
        assertEquals(3, after[0].storageType)
        assertEquals(17, after[0].slotIndex)
        assertEquals(37, after[0].level)
        assertEquals(2, after[1].slotIndex)
        assertEquals(15049, after[0].npcId)
        assertEquals(15049, after[1].npcId)
        transaction(db) {
            val nextRowIds = CharacterPets.selectAll()
                .where { CharacterPets.characterId eq 101L }
                .map { it[CharacterPets.id] }.toSet()
            assertNotEquals(originalRowIds, nextRowIds,
                "saveAll recreates row IDs while pet instanceId stays fixed")
        }
        assertNotEquals(first.instanceId, FollowNpcData(1, 15049).instanceId,
            "Newly captured general must have separate instance identity")
    }

    @Test fun refuseDuplicateOrMissingIdentityBeforeDeletingExistingRoster() = runBlocking {
        val db = db()
        val repo = PetRepositoryImpl()
        val before = repo.findByCharacterId(101L)
        val oldKeys = before.map { it.instanceId }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveAll(101L, listOf(before[0], before[0].copy(slotIndex = 2))) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.saveAll(101L, listOf(before[0].copy(instanceId = ""))) }
        }
        assertEquals(oldKeys, repo.findByCharacterId(101L).map { it.instanceId })
        transaction(db) {
            assertEquals(2L, CharacterPets.selectAll()
                .where { CharacterPets.characterId eq 101L }.count())
        }
    }

    @Test fun sameGeneralRetainsIdentityDuringRebirthAndPlacementCopies() {
        val general = FollowNpcData(slotIndex = 1, npcId = 15049)
        assertEquals(general.instanceId, general.copy(slotIndex = 8, storageType = 2).instanceId)
        assertEquals(general.instanceId,
            general.copy(slotIndex = 3, npcId = 15050, level = 1).instanceId)
        val file = listOf(
            Path.of("gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/PetRebornSystem.kt"),
            Path.of("../gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/PetRebornSystem.kt"),
        ).first { Files.isRegularFile(it) }
        assertTrue(Files.readString(file).contains("instanceId = pet.instanceId"),
            "Rebirth must carry over the preexisting instance identity")
    }
}

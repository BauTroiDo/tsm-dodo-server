package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.ChildFollowerBindingPolicy
import tw.tsm.app.handlers.ChildPackets
import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.persistence.repositories.ChildRepositoryImpl
import tw.tsm.persistence.tables.CharacterChildStates
import tw.tsm.persistence.tables.CharacterPets

/** Genuine DVT child to durable pet UUID pairing; no inferred adoption. */
class DvtChildPetBindingContractTest {
    private fun snapshot(kind: Int): ChildDataSnapshot {
        val raw = ByteArray(93)
        raw[0] = kind.toByte()
        raw[1] = 1
        raw[3] = 1
        raw[72] = 0x90.toByte()
        raw[73] = 1
        return ChildDataSnapshot.decode(raw)
    }

    private fun database(): Database {
        val db = Database.connect(
            "jdbc:h2:mem:dvt-child-pet-binding-" + UUID.randomUUID() +
                ";DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
        transaction(db) { SchemaUtils.create(CharacterChildStates, CharacterPets) }
        return db
    }

    private fun insertPet(
        db: Database, character: Long, id: String,
        storage: Int = 1, slot: Int = 1, npc: Int = 15049,
    ) {
        transaction(db) {
            CharacterPets.insert {
                it[characterId] = character
                it[instanceId] = id
                it[storageType] = storage
                it[slotIndex] = slot
                it[npcId] = npc
                it[seedFixed] = true
            }
        }
    }

    @Test fun adultCanBindOnlyOneActuallyOwnedUniquePetAndBindingSurvivesChildUpdates() = runBlocking {
        val db = database()
        val repo = ChildRepositoryImpl()
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        insertPet(db, 101L, first)
        insertPet(db, 101L, second, storage = 2)
        val adult = snapshot(3)
        repo.save(101L, adult)
        assertNull(repo.loadState(101L)?.petInstanceId)
        assertFalse(repo.bindAdultToOwnedPet(101L, UUID.randomUUID().toString()))
        assertFalse(repo.bindAdultToOwnedPet(202L, first))
        assertFalse(repo.bindAdultToOwnedPet(101L, "15049"))
        assertTrue(repo.bindAdultToOwnedPet(101L, first))
        assertTrue(repo.bindAdultToOwnedPet(101L, first))
        assertFalse(repo.bindAdultToOwnedPet(101L, second))
        val saved = repo.loadState(101L)!!
        assertEquals(first, saved.petInstanceId)
        assertArrayEquals(adult.toBytes(), saved.snapshot.toBytes())
        assertEquals(400, repo.setItemUseTotal(101L, 400)?.childItemCount)
        assertEquals(first, repo.loadState(101L)?.petInstanceId)
        assertEquals(32, repo.updateCare(101L, stodge = 32, satiation = null)?.stodge)
        assertEquals(first, repo.loadState(101L)?.petInstanceId)
        transaction(db) {
            assertEquals(1L, CharacterChildStates.selectAll().count())
            assertEquals(first, CharacterChildStates.selectAll().single()[CharacterChildStates.petInstanceId])
        }
    }

    @Test fun rejectFosterDeadAndDuplicateInstanceAcrossOwnersBeforeBinding() = runBlocking {
        val db = database()
        val repo = ChildRepositoryImpl()
        val pet = UUID.randomUUID().toString()
        insertPet(db, 101L, pet)
        repo.save(101L, snapshot(1))
        assertFalse(repo.bindAdultToOwnedPet(101L, pet))
        repo.save(101L, snapshot(2))
        assertFalse(repo.bindAdultToOwnedPet(101L, pet))
        repo.save(101L, snapshot(3))
        insertPet(db, 202L, pet, npc = 16000)
        assertFalse(repo.bindAdultToOwnedPet(101L, pet))
        assertNull(repo.loadState(101L)?.petInstanceId)
        assertNull(repo.loadState(202L))
    }

    @Test fun onlyExactBoundAdultCarriedGeneralCanResolveS069009Slot() {
        val adult = snapshot(3)
        val foster = snapshot(1)
        val dead = snapshot(2)
        val childId = UUID.randomUUID().toString()
        val otherId = UUID.randomUUID().toString()
        val childPet = FollowNpcData(slotIndex = 3, npcId = 15049, instanceId = childId)
        val sameNpcNormal = FollowNpcData(slotIndex = 1, npcId = 15049, instanceId = otherId)
        val following = mapOf(3 to childPet, 1 to sameNpcNormal)
        val roster = following.values.toList()
        fun choose(
            c: ChildDataSnapshot = adult,
            id: String? = childId,
            follow: Map<Int, FollowNpcData> = following,
            owned: Collection<FollowNpcData> = roster,
            maxSlot: Int = 9,
        ) = ChildFollowerBindingPolicy.followIndex(c, id, follow, owned, maxSlot)

        assertEquals(3, choose())
        assertArrayEquals(byteArrayOf(69, 9, 0, 3, 93, 0),
            ChildPackets.deployed(choose()!!, adult).copyOfRange(0, 6))
        assertNull(choose(c = foster))
        assertNull(choose(c = dead))
        assertNull(choose(id = null))
        assertNull(choose(id = ""))
        assertNull(choose(id = UUID.randomUUID().toString()))
        assertNull(choose(maxSlot = 2))
        assertNull(choose(follow = mapOf(3 to childPet.copy(slotIndex = 2))))
        assertNull(choose(follow = mapOf(3 to childPet.copy(storageType = 2))))
        assertNull(choose(follow = mapOf(1 to sameNpcNormal)))
        assertNull(choose(owned = roster + childPet.copy(storageType = 2, slotIndex = 7)))
        assertNull(choose(follow = emptyMap(), owned = listOf(childPet.copy(storageType = 3))))
        assertArrayEquals(byteArrayOf(69, 7, 0, 93, 0),
            ChildPackets.snapshot(adult).copyOfRange(0, 5))
    }

    @Test fun loginMustSendPetRosterBeforeAnyBoundChildFollowerPacket() {
        val location = listOf(
            Path.of("app/src/main/kotlin/tw/tsm/app/handlers/SelectCharHandler.kt"),
            Path.of("../app/src/main/kotlin/tw/tsm/app/handlers/SelectCharHandler.kt")
        ).first { Files.isRegularFile(it) }
        val code = Files.readString(location)
        assertTrue(code.indexOf("loginService.executeCharacterEnterSequence(") <
            code.indexOf("childRepository?.loadState(record.id)"))
        assertTrue(code.contains("ChildFollowerBindingPolicy.followIndex("))
        assertTrue(code.contains("ChildPackets.snapshot(child)"))
        assertTrue(code.contains("ChildPackets.deployed(followSlot, child)"))
    }
}

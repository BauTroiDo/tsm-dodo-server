package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import java.util.UUID
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.ChildRepository
import tw.tsm.domain.interfaces.repositories.ChildProfileState
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.persistence.tables.CharacterChildStates

class ChildRepositoryImpl : ChildRepository {
    override suspend fun load(characterId: Long): ChildDataSnapshot? = suspendTransaction {
        val row = CharacterChildStates.selectAll()
            .where { CharacterChildStates.characterId eq characterId }
            .firstOrNull() ?: return@suspendTransaction null
        ChildDataSnapshot.decode(row[CharacterChildStates.payload])
    }

    override suspend fun loadState(characterId: Long): ChildProfileState? = suspendTransaction {
        val row = CharacterChildStates.selectAll()
            .where { CharacterChildStates.characterId eq characterId }
            .firstOrNull() ?: return@suspendTransaction null
        val raw = row[CharacterChildStates.petInstanceId]
        // Invalid legacy/corrupt binding is never trusted for S069-009.
        val validId = raw.takeIf { it.isNotBlank() && runCatching {
            UUID.fromString(it).toString() == it
        }.getOrDefault(false) }
        ChildProfileState(
            ChildDataSnapshot.decode(row[CharacterChildStates.payload]),
            validId,
        )
    }

    override suspend fun bindAdultToOwnedPet(
        characterId: Long, petInstanceId: String,
    ): Boolean {
        if (characterId <= 0 || !runCatching {
            UUID.fromString(petInstanceId).toString() == petInstanceId
        }.getOrDefault(false)) return false
        return suspendTransaction {
            // Lock the child profile so two racing binders cannot replace each
            // other. Never create one or turn a normal NPC into a child.
            val row = CharacterChildStates.selectAll()
                .where { CharacterChildStates.characterId eq characterId }
                .forUpdate()
                .firstOrNull() ?: return@suspendTransaction false
            val child = ChildDataSnapshot.decode(row[CharacterChildStates.payload])
            if (child.kind != ChildDataSnapshot.ADULT_KIND) return@suspendTransaction false
            val currentId = row[CharacterChildStates.petInstanceId]
            if (currentId.isNotEmpty() && currentId != petInstanceId) {
                return@suspendTransaction false
            }
            // Require exactly one physical pet instance GLOBALLY and ownership
            // by this character. A duplicated UUID must not grant two users
            // child access. Storage slot can change without changing identity.
            val owners = CharacterPets.selectAll()
                .where { CharacterPets.instanceId eq petInstanceId }
                .forUpdate()
                .toList()
            if (owners.size != 1 || owners.single()[CharacterPets.characterId] != characterId) {
                return@suspendTransaction false
            }
            if (currentId.isEmpty()) {
                CharacterChildStates.update({ CharacterChildStates.characterId eq characterId }) {
                    it[CharacterChildStates.petInstanceId] = petInstanceId
                }
            }
            true
        }
    }

    override suspend fun setItemUseTotal(characterId: Long, newTotal: Int): ChildDataSnapshot? {
        if (characterId <= 0L || newTotal !in 0..65535) return null
        return suspendTransaction {
            // Per-character row lock prevents concurrent item use from silently
            // regressing ChildData or losing the 399->400 adulthood transition.
            val row = CharacterChildStates.selectAll()
                .where { CharacterChildStates.characterId eq characterId }
                .forUpdate()
                .firstOrNull() ?: return@suspendTransaction null
            val current = ChildDataSnapshot.decode(row[CharacterChildStates.payload])
            if (current.kind == 0 || newTotal < current.childItemCount) {
                return@suspendTransaction null
            }
            val updated = current.withItemCount(newTotal)
            if (updated.childItemCount != current.childItemCount ||
                updated.kind != current.kind
            ) {
                CharacterChildStates.update({ CharacterChildStates.characterId eq characterId }) {
                    it[CharacterChildStates.payload] = updated.toBytes()
                }
            }
            updated
        }
    }

    override suspend fun updateCare(
        characterId: Long,
        stodge: Int?,
        satiation: Int?,
    ): ChildDataSnapshot? {
        if (characterId <= 0 || (stodge == null && satiation == null) ||
            (stodge != null && stodge !in 0..65535) ||
            (satiation != null && satiation !in 0..65535)
        ) return null
        return suspendTransaction {
            val row = CharacterChildStates.selectAll()
                .where { CharacterChildStates.characterId eq characterId }
                .forUpdate()
                .firstOrNull() ?: return@suspendTransaction null
            val old = ChildDataSnapshot.decode(row[CharacterChildStates.payload])
            if (old.kind == 0) return@suspendTransaction null
            val updated = old.withCare(stodge = stodge, satiation = satiation)
            if (!updated.toBytes().contentEquals(old.toBytes())) {
                CharacterChildStates.update({ CharacterChildStates.characterId eq characterId }) {
                    it[CharacterChildStates.payload] = updated.toBytes()
                }
            }
            updated
        }
    }

    override suspend fun save(characterId: Long, snapshot: ChildDataSnapshot) {
        require(characterId > 0L)
        val bytes = snapshot.toBytes()
        // No client opcode populates this repository. Only trusted child creation/gameplay may save.
        suspendTransaction {
            val exists = CharacterChildStates.selectAll()
                .where { CharacterChildStates.characterId eq characterId }
                .firstOrNull() != null
            if (exists) {
                CharacterChildStates.update({ CharacterChildStates.characterId eq characterId }) {
                    it[CharacterChildStates.payload] = bytes
                }
            } else {
                CharacterChildStates.insert {
                    it[CharacterChildStates.characterId] = characterId
                    it[CharacterChildStates.payload] = bytes
                }
            }
        }
    }
}

package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.ChildRepository
import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.persistence.tables.CharacterChildStates

class ChildRepositoryImpl : ChildRepository {
    override suspend fun load(characterId: Long): ChildDataSnapshot? = suspendTransaction {
        val row = CharacterChildStates.selectAll()
            .where { CharacterChildStates.characterId eq characterId }
            .firstOrNull() ?: return@suspendTransaction null
        ChildDataSnapshot.decode(row[CharacterChildStates.payload])
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

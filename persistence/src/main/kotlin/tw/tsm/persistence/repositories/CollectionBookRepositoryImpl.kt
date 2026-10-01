package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.data.CollectionCardState
import tw.tsm.domain.interfaces.repositories.CollectionBookRepository
import tw.tsm.persistence.tables.CharacterCollectionCards
import tw.tsm.persistence.tables.CharacterCollectionStyles

class CollectionBookRepositoryImpl : CollectionBookRepository {
    override suspend fun loadStyleFlags(characterId: Long): Map<Int, Int> =
        suspendTransaction {
            CharacterCollectionStyles.selectAll()
                .where { CharacterCollectionStyles.characterId eq characterId }
                .associate {
                    it[CharacterCollectionStyles.styleId] to it[CharacterCollectionStyles.flag]
                }
        }

    override suspend fun saveStyleFlag(characterId: Long, styleId: Int, flag: Int) {
        suspendTransaction {
            val exists = CharacterCollectionStyles.selectAll()
                .where {
                    (CharacterCollectionStyles.characterId eq characterId) and
                        (CharacterCollectionStyles.styleId eq styleId)
                }
                .firstOrNull() != null

            if (exists) {
                CharacterCollectionStyles.update({
                    (CharacterCollectionStyles.characterId eq characterId) and
                        (CharacterCollectionStyles.styleId eq styleId)
                }) {
                    it[CharacterCollectionStyles.flag] = flag
                }
            } else {
                CharacterCollectionStyles.insert {
                    it[CharacterCollectionStyles.characterId] = characterId
                    it[CharacterCollectionStyles.styleId] = styleId
                    it[CharacterCollectionStyles.flag] = flag
                }
            }
        }
    }

    override suspend fun loadCardStates(characterId: Long): Map<Int, CollectionCardState> =
        suspendTransaction {
            CharacterCollectionCards.selectAll()
                .where { CharacterCollectionCards.characterId eq characterId }
                .associate {
                    val cardId = it[CharacterCollectionCards.cardId]
                    cardId to CollectionCardState(
                        cardId = cardId,
                        level = it[CharacterCollectionCards.level],
                        equipSlot = it[CharacterCollectionCards.equipSlot],
                    )
                }
        }

    override suspend fun saveCardState(characterId: Long, state: CollectionCardState) {
        suspendTransaction {
            val exists = CharacterCollectionCards.selectAll()
                .where {
                    (CharacterCollectionCards.characterId eq characterId) and
                        (CharacterCollectionCards.cardId eq state.cardId)
                }
                .firstOrNull() != null

            if (exists) {
                CharacterCollectionCards.update({
                    (CharacterCollectionCards.characterId eq characterId) and
                        (CharacterCollectionCards.cardId eq state.cardId)
                }) {
                    it[CharacterCollectionCards.level] = state.level
                    it[CharacterCollectionCards.equipSlot] = state.equipSlot
                }
            } else {
                CharacterCollectionCards.insert {
                    it[CharacterCollectionCards.characterId] = characterId
                    it[CharacterCollectionCards.cardId] = state.cardId
                    it[CharacterCollectionCards.level] = state.level
                    it[CharacterCollectionCards.equipSlot] = state.equipSlot
                }
            }
        }
    }
}

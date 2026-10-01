package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.AstrolabeRepository
import tw.tsm.domain.valueobjects.AstrolabeData
import tw.tsm.persistence.tables.CharacterAstrolabes

class AstrolabeRepositoryImpl : AstrolabeRepository {
    override suspend fun findByCharacterId(characterId: Long): AstrolabeData? =
        suspendTransaction {
            val row = CharacterAstrolabes.selectAll()
                .where { CharacterAstrolabes.characterId eq characterId }
                .firstOrNull() ?: return@suspendTransaction null
            AstrolabeData(
                natalPoint = row[CharacterAstrolabes.natalPoint],
                starLevels = intArrayOf(
                    row[CharacterAstrolabes.star1],
                    row[CharacterAstrolabes.star2],
                    row[CharacterAstrolabes.star3],
                    row[CharacterAstrolabes.star4],
                    row[CharacterAstrolabes.star5],
                    row[CharacterAstrolabes.star6],
                    row[CharacterAstrolabes.star7]
                )
            )
        }

    override suspend fun save(characterId: Long, data: AstrolabeData) {
        val levels = data.starLevels
        require(levels.size == 7)
        suspendTransaction {
            val exists = CharacterAstrolabes.selectAll()
                .where { CharacterAstrolabes.characterId eq characterId }
                .firstOrNull() != null
            if (exists) {
                CharacterAstrolabes.update({ CharacterAstrolabes.characterId eq characterId }) {
                    it[natalPoint] = data.natalPoint
                    it[star1] = levels[0]
                    it[star2] = levels[1]
                    it[star3] = levels[2]
                    it[star4] = levels[3]
                    it[star5] = levels[4]
                    it[star6] = levels[5]
                    it[star7] = levels[6]
                }
            } else {
                CharacterAstrolabes.insert {
                    it[CharacterAstrolabes.characterId] = characterId
                    it[natalPoint] = data.natalPoint
                    it[star1] = levels[0]
                    it[star2] = levels[1]
                    it[star3] = levels[2]
                    it[star4] = levels[3]
                    it[star5] = levels[4]
                    it[star6] = levels[5]
                    it[star7] = levels[6]
                }
            }
        }
    }
}

package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.MessageBoardRecord
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.persistence.tables.MessageBoardMessages

class MessageBoardRepositoryImpl : MessageBoardRepository {
    override suspend fun findRecent(limit: Int): List<MessageBoardRecord> = suspendTransaction {
        MessageBoardMessages.selectAll()
            .orderBy(MessageBoardMessages.id to SortOrder.DESC)
            .limit(limit.coerceAtLeast(0))
            .map(::toRecord)
            .reversed()
    }

    override suspend fun create(
        characterId: Long, playerId: Long, name: String, sex: Int, headStyle: Int,
        color1: Int, color2: Int, message: String, createdAt: Long, maxRows: Int,
    ): MessageBoardRecord = suspendTransaction {
        val id = MessageBoardMessages.insert { row ->
            row[MessageBoardMessages.characterId] = characterId
            row[MessageBoardMessages.playerId] = playerId
            row[MessageBoardMessages.name] = name
            row[MessageBoardMessages.sex] = sex
            row[MessageBoardMessages.headStyle] = headStyle
            row[MessageBoardMessages.color1] = color1
            row[MessageBoardMessages.color2] = color2
            row[MessageBoardMessages.createdAt] = createdAt
            row[MessageBoardMessages.message] = message
        } get MessageBoardMessages.id

        val keep = maxRows.coerceAtLeast(1)
        val stale = MessageBoardMessages.selectAll()
            .orderBy(MessageBoardMessages.id to SortOrder.DESC)
            .map { it[MessageBoardMessages.id] }
            .drop(keep)
        if (stale.isNotEmpty()) {
            MessageBoardMessages.deleteWhere { MessageBoardMessages.id inList stale }
        }

        MessageBoardRecord(id, characterId, playerId, name, sex, headStyle, color1, color2, createdAt, message)
    }

    private fun toRecord(row: org.jetbrains.exposed.v1.core.ResultRow) = MessageBoardRecord(
        id = row[MessageBoardMessages.id],
        characterId = row[MessageBoardMessages.characterId],
        playerId = row[MessageBoardMessages.playerId],
        name = row[MessageBoardMessages.name],
        sex = row[MessageBoardMessages.sex],
        headStyle = row[MessageBoardMessages.headStyle],
        color1 = row[MessageBoardMessages.color1],
        color2 = row[MessageBoardMessages.color2],
        createdAt = row[MessageBoardMessages.createdAt],
        message = row[MessageBoardMessages.message],
    )
}

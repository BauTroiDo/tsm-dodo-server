package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.util.OleDate

data class MessageBoardRecord(
    val id: Long,
    val characterId: Long,
    val playerId: Long,
    val name: String,
    val sex: Int,
    val headStyle: Int,
    val color1: Int,
    val color2: Int,
    val createdAt: Long,
    val message: String,
) {
    val oaDate: Double
        get() = OleDate.fromMillis(createdAt)
}

interface MessageBoardRepository {
    /** Oldest -> newest, so the DVT client inserting each row at index 1 ends newest-first. */
    suspend fun findRecent(limit: Int = 500): List<MessageBoardRecord>

    /** Persists one message and trims storage to maxRows newest records. */
    suspend fun create(
        characterId: Long,
        playerId: Long,
        name: String,
        sex: Int,
        headStyle: Int,
        color1: Int,
        color2: Int,
        message: String,
        createdAt: Long = System.currentTimeMillis(),
        maxRows: Int = 500,
    ): MessageBoardRecord
}

package tw.tsm.domain.interfaces.repositories

data class HouseProfile(
    val characterId: Long,
    val sign: Int = 0,
    val birthMonth: Int = 0,
    val birthDay: Int = 0,
    val charm: Int = 0,
    val signature: String = "",
    val impression: String = "",
    val favorite: String = "",
    val area: String = "",
    val hobby: String = "",
)

data class GiftRecord(
    val id: Long = 0,
    val senderId: Long,
    val targetId: Long,
    val itemId: Int,
    val quantity: Int,
    val time: Double,
)

interface HouseBoardRepository {
    suspend fun getProfile(characterId: Long): HouseProfile
    suspend fun saveProfile(profile: HouseProfile)
    suspend fun addCharm(characterId: Long, delta: Int): HouseProfile
    suspend fun applyGift(record: GiftRecord, charmDelta: Int): HouseProfile

    suspend fun addGift(record: GiftRecord): GiftRecord
    suspend fun recentGifts(targetId: Long, limit: Int = 100): List<GiftRecord>
    suspend fun allGifts(targetId: Long): List<GiftRecord>
}

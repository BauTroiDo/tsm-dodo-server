package tw.tsm.domain.interfaces.repositories

data class HouseResidenceSettings(
    val name: String = "Gia Vien",
    val signature: String = "",
)

/** House label/signature are independent of protocol 105's social profile. */
interface HouseResidenceRepository {
    suspend fun load(characterId: Long): HouseResidenceSettings
    suspend fun save(characterId: Long, settings: HouseResidenceSettings)
}

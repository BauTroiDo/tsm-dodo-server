package tw.tsm.domain.interfaces.repositories

data class HouseFurniturePlacement(
    val furnitureId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val direction: Int,
)

interface HouseFurnitureRepository {
    suspend fun load(characterId: Long, building: Int, floor: Int): List<HouseFurniturePlacement>
    suspend fun replace(characterId: Long, building: Int, floor: Int, furniture: List<HouseFurniturePlacement>)
}

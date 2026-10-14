package tw.tsm.domain.interfaces.repositories

/** Four owner-owned eligible NPCs assigned to one DVT home floor; 0=empty. */
interface HouseWarriorRepository {
    suspend fun load(characterId: Long, building: Int, floor: Int): List<Int>
    suspend fun replace(characterId: Long, building: Int, floor: Int, npcIds: List<Int>)
}

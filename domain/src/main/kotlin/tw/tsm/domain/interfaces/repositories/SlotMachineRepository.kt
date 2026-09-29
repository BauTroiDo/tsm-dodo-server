package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.data.SlotMachineStateData

interface SlotMachineRepository {
    suspend fun load(characterId: Long): SlotMachineStateData
    suspend fun save(characterId: Long, state: SlotMachineStateData)
}

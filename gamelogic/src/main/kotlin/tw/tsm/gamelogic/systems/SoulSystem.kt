package tw.tsm.gamelogic.systems

import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.SoulThingData
import java.util.concurrent.ConcurrentHashMap

class SoulSystem(
    private val repository: SoulRepository,
) {
    private val cache = ConcurrentHashMap<Long, LinkedHashMap<Long, SoulThingData>>()

    suspend fun load(characterId: Long): List<SoulThingData> {
        val loaded = repository.findByCharacterId(characterId)
        loaded.forEach(::validate)
        require(loaded.map { it.sn }.distinct().size == loaded.size) {
            "Soul SN bi trung cho characterId=$characterId"
        }
        val state = LinkedHashMap<Long, SoulThingData>(loaded.size)
        loaded.forEach { state[it.sn] = it }
        cache[characterId] = state
        return state.values.toList()
    }

    fun snapshot(characterId: Long): List<SoulThingData> =
        cache[characterId]?.values?.toList() ?: emptyList()

    fun find(characterId: Long, sn: Long): SoulThingData? = cache[characterId]?.get(sn)

    fun equippedForNpc(characterId: Long, npcId: Int): SoulThingData? =
        cache[characterId]?.values?.firstOrNull { it.equipId == npcId }

    fun unload(characterId: Long) {
        cache.remove(characterId)
    }

    private fun validate(soul: SoulThingData) {
        require(soul.itemId in 0..0xFFFF)
        require(soul.element in 0..0xFF)
        require(soul.equipId in 0..0xFFFF)
        require(soul.level in 0..0xFFFF)
        require(soul.starLevel in 0..0xFF)
        require(soul.startAtt.all { it in 0..0xFFFF })
        require(soul.nowAtt.all { it in 0..0xFFFF })
        require(soul.skills.all { it.id in 0..0xFFFF && it.level in 0..0xFF })
        require(soul.lockFlags in 0..0xFF)
    }
}

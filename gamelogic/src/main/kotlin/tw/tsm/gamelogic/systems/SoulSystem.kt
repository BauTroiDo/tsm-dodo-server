package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData
import java.util.concurrent.ConcurrentHashMap

private val soulLogger = KotlinLogging.logger {}

sealed interface SoulEquipResult {
    data class Ok(val soul: SoulThingData) : SoulEquipResult
    data class Fail(val errorCode: Int) : SoulEquipResult
}

class SoulSystem(
    private val repository: SoulRepository,
) {
    private val cache = ConcurrentHashMap<Long, LinkedHashMap<Long, SoulThingData>>()
    private val locks = ConcurrentHashMap<Long, Mutex>()

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

    suspend fun create(
        characterId: Long,
        template: SoulThingData,
    ): SoulThingData? = lockFor(characterId).withLock {
        val state = cache[characterId] ?: return@withLock null
        if (state.size >= MAX_SOUL_BAG) return@withLock null

        val nextFromClock = System.currentTimeMillis().coerceAtLeast(1L)
        val nextFromState = ((state.keys.maxOrNull() ?: 0L) + 1L).coerceAtLeast(1L)
        var sn = maxOf(nextFromClock, nextFromState)
        while (state.containsKey(sn)) sn++

        val created = template.copy(sn = sn)
        validate(created)

        try {
            repository.save(characterId, created)
            state[sn] = created
            created
        } catch (e: Exception) {
            soulLogger.error(e) {
                "Persist Soul create failed: characterId=" + characterId + " itemId=" + template.itemId
            }
            null
        }
    }

    suspend fun delete(
        characterId: Long,
        sn: Long,
    ): Boolean = lockFor(characterId).withLock {
        val state = cache[characterId] ?: return@withLock false
        if (!state.containsKey(sn)) return@withLock false
        try {
            repository.delete(characterId, sn)
            state.remove(sn)
            true
        } catch (e: Exception) {
            soulLogger.error(e) {
                "Persist Soul delete failed: characterId=" + characterId + " sn=" + sn
            }
            false
        }
    }

    suspend fun equip(
        characterId: Long,
        npcId: Int,
        sn: Long,
        npcElement: Int,
    ): SoulEquipResult = lockFor(characterId).withLock {
        val state = cache[characterId] ?: return@withLock SoulEquipResult.Fail(ERR_NO_PLAYER)
        val soul = state[sn] ?: return@withLock SoulEquipResult.Fail(ERR_NO_SOUL)

        if (soul.isMining) return@withLock SoulEquipResult.Fail(ERR_MINING)
        if (soul.equipId != 0) return@withLock SoulEquipResult.Fail(ERR_EQUIP_TAKEOFF)
        if (soul.element != npcElement) return@withLock SoulEquipResult.Fail(ERR_ELEMENT_MISMATCH)
        if (state.values.any { it.equipId == npcId }) {
            return@withLock SoulEquipResult.Fail(ERR_WARRIOR_HAS_SOUL)
        }

        persistThenPublish(characterId, state, soul.copy(equipId = npcId))
    }

    suspend fun takeOff(
        characterId: Long,
        npcId: Int,
        sn: Long,
    ): SoulEquipResult = lockFor(characterId).withLock {
        val state = cache[characterId] ?: return@withLock SoulEquipResult.Fail(ERR_NO_PLAYER)
        val soul = state[sn] ?: return@withLock SoulEquipResult.Fail(ERR_NO_SOUL)

        if (soul.isMining) return@withLock SoulEquipResult.Fail(ERR_MINING)
        if (soul.equipId != npcId || npcId == 0) {
            return@withLock SoulEquipResult.Fail(ERR_EQUIP_TAKEOFF)
        }

        persistThenPublish(characterId, state, soul.copy(equipId = 0))
    }

    fun unload(characterId: Long) {
        cache.remove(characterId)
        locks.remove(characterId)
    }

    private suspend fun persistThenPublish(
        characterId: Long,
        state: LinkedHashMap<Long, SoulThingData>,
        updated: SoulThingData,
    ): SoulEquipResult {
        return try {
            repository.save(characterId, updated)
            state[updated.sn] = updated
            SoulEquipResult.Ok(updated)
        } catch (e: Exception) {
            soulLogger.error(e) {
                "Persist Soul equip/takeoff failed: characterId=$characterId sn=${updated.sn}"
            }
            SoulEquipResult.Fail(ERR_GENERIC)
        }
    }

    private fun lockFor(characterId: Long): Mutex =
        locks.computeIfAbsent(characterId) { Mutex() }

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

    companion object {
        const val MAX_SOUL_BAG = 100
        const val ERR_NO_PLAYER = 1
        const val ERR_NO_SOUL = 2
        const val ERR_MINING = 4
        const val ERR_WARRIOR_ID = 7
        const val ERR_WARRIOR_HAS_SOUL = 8
        const val ERR_EQUIP_TAKEOFF = 9
        const val ERR_ELEMENT_MISMATCH = 10
        const val ERR_NO_WARRIOR = 12
        const val ERR_IN_BATTLE = 26
        const val ERR_GENERIC = 255
    }
}

object SoulEquipTargetValidator {
    fun validate(pet: FollowNpcData?, npcId: Int, npcElement: Int?): Int? {
        if (pet == null) return SoulSystem.ERR_NO_WARRIOR
        if (pet.npcId != npcId) return SoulSystem.ERR_WARRIOR_ID
        if (pet.canGrow == 0 || pet.canGrow == 2) return SoulSystem.ERR_NO_WARRIOR
        if (npcElement == null) return SoulSystem.ERR_NO_WARRIOR
        return null
    }
}

/**
 * DVT RoleController:GetSkillLv(showHigher=true): a Soul-only skill is exposed
 * at its Soul level; if the warrior already knows the same skill, the higher
 * of the warrior and Soul levels wins.
 */
object SoulSkillLevelMerge {
    fun merge(base: Map<Int, Int>, soul: SoulThingData?): Map<Int, Int> {
        if (soul == null) return base
        val result = base.toMutableMap()
        for (skill in soul.skills) {
            if (skill.id <= 0 || skill.level <= 0) continue
            result[skill.id] = maxOf(result[skill.id] ?: 0, skill.level)
        }
        return result
    }
}

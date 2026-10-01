package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.AstrolabeDef
import tw.tsm.domain.interfaces.repositories.AstrolabeRepository
import tw.tsm.domain.valueobjects.AstrolabeBonusData
import tw.tsm.domain.valueobjects.AstrolabeData
import tw.tsm.domain.util.DvtPercentMath
import java.util.concurrent.ConcurrentHashMap

data class AstrolabeUpgradeResult(
    val success: Boolean,
    val state: AstrolabeData,
    val goldCost: Int = 0,
    val pointCost: Int = 0
)

data class AstrolabePointResult(
    val success: Boolean,
    val state: AstrolabeData,
    val added: Int = 0
)

/**
 * DVT Thất Tinh Mệnh Bàn.
 *
 * Contract:
 * - 7 sao, mỗi sao tối đa Lv10.
 * - tăng theo vòng đều bằng Astrolabe.FindNextStar().
 * - tiêu natalPoint từ IOS_Astrolabe.dat.
 * - vàng = nextLv^2 * 10000.
 * - sao 1..6 là otherAdd cho HPX/DEF/INT/SPX/AGI/ATK.
 * - sao 7 được lưu/đồng bộ nhưng chưa áp Crit vì Lua DVT không hề cộng nó vào EAttribute.Crit.
 */
class AstrolabeSystem(
    private val repository: AstrolabeRepository,
    private val defs: Map<Int, AstrolabeDef>,
    private val moneySystem: MoneySystem,
    private val playerStateManager: PlayerStateManager,
    private val statSystem: StatSystem
) {
    private data class MutableState(
        var natalPoint: Int,
        val levels: IntArray
    )

    private val cache = ConcurrentHashMap<Long, MutableState>()
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun load(characterId: Long): AstrolabeData {
        val loaded = repository.findByCharacterId(characterId) ?: AstrolabeData()
        validateState(loaded)
        cache[characterId] = MutableState(loaded.natalPoint, loaded.starLevels.copyOf())
        return loaded.copySafe()
    }

    fun snapshot(characterId: Long): AstrolabeData =
        cache[characterId]?.let { AstrolabeData(it.natalPoint, it.levels.copyOf()) }
            ?: AstrolabeData()

    fun bonuses(characterId: Long): AstrolabeBonusData =
        bonuses(snapshot(characterId).starLevels)

    fun bonuses(levels: IntArray): AstrolabeBonusData {
        require(levels.size == STAR_COUNT)
        fun sum(kind: Int): Int {
            val def = defs[kind] ?: return 0
            val level = levels[kind - 1].coerceIn(0, MAX_LEVEL)
            return def.levels.take(level).sumOf { it.value }
        }
        return AstrolabeBonusData(
            hpx = sum(1),
            def = sum(2),
            intelligence = sum(3),
            spx = sum(4),
            agi = sum(5),
            atk = sum(6),
            criticle = sum(7)
        )
    }

    /** 1-based star index, exact DVT Astrolabe.FindNextStar(). */
    fun findNextStar(levels: IntArray): Int {
        require(levels.size == STAR_COUNT)
        if (levels[0] == levels[6]) return 1
        for (i in 1 until STAR_COUNT) {
            if (levels[i] < levels[i - 1]) return i + 1
        }
        return 1
    }

    fun maxUsableItemQuantity(characterId: Long, pointPerItem: Int): Int {
        if (pointPerItem <= 0) return 0
        val current = snapshot(characterId).natalPoint
        return ((MAX_NATAL_POINT - current) / pointPerItem).coerceAtLeast(0)
    }

    suspend fun addNatalPoint(characterId: Long, amount: Int): AstrolabePointResult {
        if (amount <= 0) return AstrolabePointResult(false, snapshot(characterId))
        val mutex = locks.computeIfAbsent(characterId) { Mutex() }
        return mutex.withLock {
            val state = cache[characterId] ?: return@withLock AstrolabePointResult(false, AstrolabeData())
            val next = state.natalPoint.toLong() + amount.toLong()
            if (next > MAX_NATAL_POINT) {
                return@withLock AstrolabePointResult(false, AstrolabeData(state.natalPoint, state.levels.copyOf()))
            }

            val previous = state.natalPoint
            state.natalPoint = next.toInt()
            try {
                repository.save(characterId, AstrolabeData(state.natalPoint, state.levels.copyOf()))
            } catch (e: Exception) {
                state.natalPoint = previous
                throw e
            }
            AstrolabePointResult(
                success = true,
                state = AstrolabeData(state.natalPoint, state.levels.copyOf()),
                added = amount
            )
        }
    }

    /**
     * Compensation hook cho direct-use item: khôi phục snapshot đã persist nếu
     * mutation inventory sau đó thất bại. Không dùng cho gameplay thường.
     */
    suspend fun restore(characterId: Long, data: AstrolabeData) {
        validateState(data)
        val mutex = locks.computeIfAbsent(characterId) { Mutex() }
        mutex.withLock {
            val state = cache[characterId]
                ?: throw IllegalStateException("Astrolabe chưa load: $characterId")
            repository.save(characterId, data)
            state.natalPoint = data.natalPoint
            data.starLevels.copyInto(state.levels)
        }
    }

    suspend fun upgrade(characterId: Long, requestedStar: Int): AstrolabeUpgradeResult {
        val mutex = locks.computeIfAbsent(characterId) { Mutex() }
        return mutex.withLock {
            val state = cache[characterId]
                ?: return@withLock AstrolabeUpgradeResult(false, AstrolabeData())
            val before = AstrolabeData(state.natalPoint, state.levels.copyOf())

            if (requestedStar !in 1..STAR_COUNT) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }
            if (state.levels.all { it >= MAX_LEVEL }) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }
            if (requestedStar != findNextStar(state.levels)) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }

            val currentLevel = state.levels[requestedStar - 1]
            if (currentLevel >= MAX_LEVEL) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }
            val nextLevel = currentLevel + 1
            val levelDef = defs[requestedStar]?.level(nextLevel)
                ?: return@withLock AstrolabeUpgradeResult(false, before)
            val pointCost = levelDef.needPoint
            val goldCost = nextLevel * nextLevel * 10_000

            if (state.natalPoint < pointCost || moneySystem.getGold(characterId) < goldCost) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }
            if (!moneySystem.subtractGold(characterId, goldCost)) {
                return@withLock AstrolabeUpgradeResult(false, before)
            }

            val oldBonus = bonuses(state.levels)
            state.natalPoint -= pointCost
            state.levels[requestedStar - 1] = nextLevel
            val after = AstrolabeData(state.natalPoint, state.levels.copyOf())

            try {
                repository.save(characterId, after)
                moneySystem.saveMoney(characterId)
            } catch (e: Exception) {
                state.natalPoint = before.natalPoint
                before.starLevels.copyInto(state.levels)
                runCatching { repository.save(characterId, before) }
                moneySystem.addGold(characterId, goldCost)
                runCatching { moneySystem.saveMoney(characterId) }
                throw e
            }

            updateRuntimeAfterUpgrade(characterId, oldBonus, bonuses(state.levels))
            AstrolabeUpgradeResult(true, after, goldCost, pointCost)
        }
    }

    private fun updateRuntimeAfterUpgrade(
        characterId: Long,
        oldBonus: AstrolabeBonusData,
        newBonus: AstrolabeBonusData
    ) {
        val state = playerStateManager.getState(characterId) ?: return
        synchronized(state) {
            val turn3Lv = if (state.turn >= 3) (state.level - 200).coerceAtLeast(0) else 0
            val calcLevel = if (state.turn >= 3) 200 else state.level
            val turn3Hpx = if (state.turn >= 3) turn3Lv.coerceAtMost(70) + state.turn3Hpx else 0
            val turn3Spx = if (state.turn >= 3) turn3Lv + state.turn3Spx else 0

            val oldBaseMaxHp = statSystem.calculateMaxHp(
                calcLevel,
                state.hpx + state.equipHpx + turn3Hpx + oldBonus.hpx,
                state.equipMaxHp,
                0,
                state.turn,
                state.career,
                turn3Lv
            )
            val newBaseMaxHp = statSystem.calculateMaxHp(
                calcLevel,
                state.hpx + state.equipHpx + turn3Hpx + newBonus.hpx,
                state.equipMaxHp,
                0,
                state.turn,
                state.career,
                turn3Lv
            )
            val oldMaxHp = DvtPercentMath.applyFinalPercentFloor(oldBaseMaxHp, state.hpPercent)
                .coerceAtLeast(1L)
            val newMaxHp = DvtPercentMath.applyFinalPercentFloor(newBaseMaxHp, state.hpPercent)
                .coerceAtLeast(1L)
            val oldMaxSp = statSystem.calculateMaxSp(
                calcLevel,
                state.spx + state.equipSpx + turn3Spx + oldBonus.spx,
                state.equipMaxSp,
                0,
                state.turn,
                state.career,
                turn3Lv
            )
            val newMaxSp = statSystem.calculateMaxSp(
                calcLevel,
                state.spx + state.equipSpx + turn3Spx + newBonus.spx,
                state.equipMaxSp,
                0,
                state.turn,
                state.career,
                turn3Lv
            )

            state.maxHp = (state.maxHp + (newMaxHp - oldMaxHp)).coerceAtLeast(1L)
            state.maxSp = (state.maxSp + (newMaxSp - oldMaxSp)).coerceAtLeast(1)
            state.hp = state.hp.coerceAtMost(state.maxHp)
            state.sp = state.sp.coerceAtMost(state.maxSp)
            state.astrolabeHpx = newBonus.hpx
            state.astrolabeDef = newBonus.def
            state.astrolabeInt = newBonus.intelligence
            state.astrolabeSpx = newBonus.spx
            state.astrolabeAgi = newBonus.agi
            state.astrolabeAtk = newBonus.atk
        }
    }

    fun unload(characterId: Long) {
        cache.remove(characterId)
        locks.remove(characterId)
    }

    private fun validateState(data: AstrolabeData) {
        require(data.natalPoint in 0..MAX_NATAL_POINT)
        require(data.starLevels.size == STAR_COUNT)
        require(data.starLevels.all { it in 0..MAX_LEVEL })
        for (i in 1 until STAR_COUNT) {
            require(data.starLevels[i] <= data.starLevels[i - 1])
            require(data.starLevels[i - 1] - data.starLevels[i] <= 1)
        }
        require(data.starLevels[0] - data.starLevels[6] in 0..1)
    }

    companion object {
        const val STAR_COUNT = 7
        const val MAX_LEVEL = 10
        const val MAX_NATAL_POINT = 65_535
    }
}

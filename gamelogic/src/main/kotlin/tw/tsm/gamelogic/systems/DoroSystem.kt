package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.BlissBagItem
import tw.tsm.domain.data.GoodsSaleDef
import tw.tsm.domain.data.DoroReward
import java.time.LocalDateTime
import kotlin.random.Random

class DoroSystem(
    goodsSaleDefs: List<GoodsSaleDef>,
    private val blissBagDefs: Map<Int, BlissBagDef>,
    private val roll: (Int) -> Int = { total -> Random.nextInt(1, total + 1) },
) {
    private val doroGoods = goodsSaleDefs.filter { it.storeKind == STORE_DORO }

    fun findPackage(
        storeKind: Int,
        mainPageIndex: Int,
        showKind: Int,
        pageItemIndex: Int,
        itemId: Int,
        saleCredit: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): GoodsSaleDef? {
        if (storeKind != STORE_DORO || pageItemIndex <= 0) return null
        return doroGoods.firstOrNull { def ->
            def.storeKind == storeKind &&
                def.mainKind == mainPageIndex &&
                def.subKind == showKind &&
                def.pageItemIndex == pageItemIndex &&
                def.itemId == itemId &&
                def.saleCredit == saleCredit &&
                def.open &&
                (def.bitFunction and NORMAL_CLIENT_BIT) == NORMAL_CLIENT_BIT &&
                isInSaleTime(def, now)
        }
    }

    /**
     * Doro draws reuse BlissBag_C weights. One package [GoodsSaleDef.value] means
     * exactly that many eggs/results; each egg independently selects one reward.
     *
     * Current DVT Doro package groups use kind=1. If a future package contains
     * multiple kind groups, fail conservatively rather than inventing how one egg
     * should serialize multiple rewards into S:023-150.
     */
    fun draw(packageDef: GoodsSaleDef): List<DoroReward>? {
        val pool = blissBagDefs[packageDef.itemId] ?: return null
        if (packageDef.value <= 0 || pool.kindCount != 1) return null
        val candidates = pool.items.filter { it.kind == 1 && it.pr > 0 && it.itemId > 0 && it.count > 0 }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.pr }
        if (totalWeight <= 0) return null

        return List(packageDef.value) {
            choose(candidates, totalWeight).let { DoroReward(it.itemId, it.count) }
        }
    }

    fun poolWeight(packageItemId: Int): Int =
        blissBagDefs[packageItemId]?.items?.filter { it.kind == 1 }?.sumOf { it.pr } ?: 0

    private fun choose(candidates: List<BlissBagItem>, totalWeight: Int): BlissBagItem {
        var value = roll(totalWeight).coerceIn(1, totalWeight)
        for (candidate in candidates) {
            value -= candidate.pr
            if (value <= 0) return candidate
        }
        return candidates.last()
    }

    private fun isInSaleTime(def: GoodsSaleDef, now: LocalDateTime): Boolean = when (def.timeKind) {
        0 -> true
        1 -> {
            val start = now.withHour(def.startHour).withMinute(0).withSecond(0).withNano(0)
            val end = now.withHour(def.endHour).withMinute(0).withSecond(0).withNano(0)
            !now.isBefore(start) && now.isBefore(end)
        }
        2 -> runCatching {
            val start = LocalDateTime.of(now.year, now.monthValue, def.startDay, def.startHour, 0)
            val end = LocalDateTime.of(now.year, now.monthValue, def.endDay, def.endHour, 0)
            !now.isBefore(start) && now.isBefore(end)
        }.getOrDefault(false)
        3 -> runCatching {
            val start = LocalDateTime.of(def.startYear, def.startMonth, def.startDay, def.startHour, 0)
            val end = LocalDateTime.of(def.endYear, def.endMonth, def.endDay, def.endHour, 0)
            !now.isBefore(start) && now.isBefore(end)
        }.getOrDefault(false)
        else -> false
    }

    companion object {
        const val STORE_DORO = 5
        private const val NORMAL_CLIENT_BIT = 2
    }
}

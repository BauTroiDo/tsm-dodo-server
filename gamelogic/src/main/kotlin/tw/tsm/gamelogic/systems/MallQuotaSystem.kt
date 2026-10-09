package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.GoodsSaleDef
import tw.tsm.domain.interfaces.repositories.MallQuotaRecord
import tw.tsm.domain.interfaces.repositories.MallQuotaRepository
import java.time.LocalDate
import java.time.temporal.WeekFields

private val logger = KotlinLogging.logger {}

/**
 * PC DVT GS_C: saleKind 1=daily, 2=weekly, 3=monthly, 4=lifetime, 5=event.
 * The client reads RoleCount[offer.flag]; keep it synchronized with persisted quota.
 * Do not implement event kind 5 without the authoritative event reset contract.
 */
class MallQuotaSystem(
    private val repository: MallQuotaRepository,
    private val counts: RoleCountSystem,
    private val offers: List<GoodsSaleDef>,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    data class Reservation(
        val characterId: Long, val flag: Int, val previous: MallQuotaRecord?,
        val currentPeriod: Int, val nextCount: Int,
    )

    fun period(kind: Int, date: LocalDate = clock()): Int = when (kind) {
        1 -> date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        2 -> {
            val w = WeekFields.ISO
            date.get(w.weekBasedYear()) * 100 + date.get(w.weekOfWeekBasedYear())
        }
        3 -> date.year * 100 + date.monthValue
        4 -> -1
        else -> error("Unsupported GS_C quota kind=$kind")
    }

    fun supported(offer: GoodsSaleDef): Boolean =
        offer.saleKind in 1..4 && offer.flag > 0 && offer.dayCount > 0

    /**
     * Called AFTER RoleCount.loadCounts and BEFORE S85-1 login snapshot.
     * Reset expired counters before the PC client enables its purchase buttons.
     */
    suspend fun refreshOnLogin(characterId: Long) {
        val eligible = offers.filter { supported(it) }.distinctBy { it.flag }
        for (offer in eligible) {
            val row = repository.load(characterId, offer.flag) ?: continue
            val wanted = period(offer.saleKind)
            val value = if (row.period == wanted) row.count else 0
            if (counts.getValue(characterId, offer.flag) != value) {
                counts.setValue(characterId, offer.flag, value,
                    counts.getMaxValue(characterId, offer.flag))
            }
        }
    }

    /**
     * Reserve before charging money, so a crash can't grant unlimited retries.
     * Caller serializes requests per character and compensates on charge/grant failure.
     */
    suspend fun reserve(characterId: Long, offer: GoodsSaleDef, quantity: Int): Reservation? {
        if (!supported(offer) || quantity <= 0) return null
        val key = period(offer.saleKind)
        val previous = repository.load(characterId, offer.flag)
        val value = if (previous == null) {
            // Preserve any existing RoleCount limits from pre-migration characters.
            counts.getValue(characterId, offer.flag)
        } else if (previous.period == key) {
            previous.count
        } else 0
        if (value < 0 || value.toLong() + quantity > offer.dayCount.toLong()) return null
        val next = value + quantity
        repository.save(characterId, MallQuotaRecord(offer.flag, key, next))
        return Reservation(characterId, offer.flag, previous, key, next)
    }

    suspend fun cancel(reservation: Reservation) {
        if (reservation.previous == null) repository.remove(reservation.characterId, reservation.flag)
        else repository.save(reservation.characterId, reservation.previous)
    }

    suspend fun complete(reservation: Reservation) {
        val previousMax = counts.getMaxValue(reservation.characterId, reservation.flag)
        counts.setValue(reservation.characterId, reservation.flag, reservation.nextCount, previousMax)
        try { counts.saveCounts(reservation.characterId) }
        catch (e: Exception) {
            // Quota record is already durable, so deny duplicate charge on the next session.
            logger.error(e) { "Mall quota RoleCount persist failed cid=${reservation.characterId} flag=${reservation.flag}" }
        }
    }
}

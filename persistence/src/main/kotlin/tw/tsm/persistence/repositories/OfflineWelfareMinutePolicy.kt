package tw.tsm.persistence.repositories

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** DVT client RoleCount[172] uses 480 minutes/day and [171] renders up to 1440. */
data class OfflineMinuteAccrual(
    val unclaimedMinutes: Int,
    val todayMinutes: Int,
    val newMinutes: Int,
)

object OfflineWelfareMinutePolicy {
    const val DAILY_CAP = 480
    const val UNCLAIMED_CAP = 1440
    private val ZONE = ZoneId.of("Asia/Taipei")

    fun dayKey(millis: Long): Int = Instant.ofEpochMilli(millis)
        .atZone(ZONE).toLocalDate().let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }

    /**
     * All arithmetic is based on local DVT calendar days; a long absence earns
     * at most 8h per day and the total unclaimed bar is bounded at 24h.
     *
     * No speculative conversion of minutes into RoleCount[169] EXP or [170] fee.
     */
    fun calculate(
        offlineSince: Long,
        nowMillis: Long,
        storedDay: Int,
        alreadyTodayMinutes: Int,
        alreadyUnclaimedMinutes: Int,
    ): OfflineMinuteAccrual {
        val today = dayKey(nowMillis)
        val carry = if (storedDay == today) alreadyTodayMinutes.coerceIn(0, DAILY_CAP) else 0
        val existing = alreadyUnclaimedMinutes.coerceIn(0, UNCLAIMED_CAP)
        if (offlineSince <= 0L || offlineSince >= nowMillis)
            return OfflineMinuteAccrual(existing, carry, 0)

        val startDay = Instant.ofEpochMilli(offlineSince).atZone(ZONE).toLocalDate()
        val endDay = Instant.ofEpochMilli(nowMillis).atZone(ZONE).toLocalDate()
        if (startDay.isAfter(endDay))
            return OfflineMinuteAccrual(existing, carry, 0)

        val daysBetween = ChronoUnit.DAYS.between(startDay, endDay)
        val earned: Long
        val updatedToday: Int
        if (daysBetween == 0L) {
            val duration = ((nowMillis - offlineSince) / 60_000L).coerceAtLeast(0L)
            val added = duration.coerceAtMost((DAILY_CAP - carry).toLong()).toInt()
            earned = added.toLong()
            updatedToday = carry + added
        } else {
            val nextMidnight = startDay.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()
            val firstMinutes = ((nextMidnight - offlineSince) / 60_000L).coerceAtLeast(0L)
            val priorFirstDay = if (storedDay == dayKey(offlineSince)) alreadyTodayMinutes.coerceIn(0, DAILY_CAP) else 0
            val firstAdded = firstMinutes.coerceAtMost((DAILY_CAP - priorFirstDay).toLong())
            val fullDays = (daysBetween - 1L).coerceAtLeast(0L)
            val middleAdded = fullDays.coerceAtMost(UNCLAIMED_CAP.toLong() / DAILY_CAP + 1) * DAILY_CAP
            val todayMidnight = endDay.atStartOfDay(ZONE).toInstant().toEpochMilli()
            val lastMinutes = ((nowMillis - todayMidnight) / 60_000L).coerceAtLeast(0L)
            val lastAdded = lastMinutes.coerceAtMost((DAILY_CAP - carry).toLong())
            earned = firstAdded + middleAdded + lastAdded
            updatedToday = (carry + lastAdded).toInt()
        }
        val accepted = earned.coerceAtMost((UNCLAIMED_CAP - existing).toLong()).toInt()
        return OfflineMinuteAccrual(existing + accepted, updatedToday, accepted)
    }
}

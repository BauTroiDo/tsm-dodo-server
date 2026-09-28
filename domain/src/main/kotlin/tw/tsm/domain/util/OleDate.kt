package tw.tsm.domain.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * OLE Automation Date 轉換工具
 *
 * OADate 是 Windows COM 的日期格式：以 1899-12-30 00:00:00 為基準，
 * 整數部分代表天數，小數部分代表當天已過的時間比例。
 * 例如 1.5 = 1899-12-31 12:00:00
 */
object OleDate {

    /** OADate 基準點與 Unix epoch (1970-01-01) 的天數差 */
    private const val OA_EPOCH_OFFSET_DAYS = 25569.0

    /** 每天的毫秒數 */
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** OLE epoch as a wall-clock calendar date. */
    private val OA_EPOCH_DATE: LocalDate = LocalDate.of(1899, 12, 30)

    /** Unix 毫秒 → OADate */
    fun fromMillis(millis: Long): Double =
        millis / MILLIS_PER_DAY + OA_EPOCH_OFFSET_DAYS

    /** OADate → Unix 毫秒 */
    fun toMillis(oaDate: Double): Long =
        ((oaDate - OA_EPOCH_OFFSET_DAYS) * MILLIS_PER_DAY).toLong()

    /**
     * Convert a local wall-clock DateTime to OADate without applying a timezone.
     *
     * DVT client does DateTime.FromOADate(value) then compares it with DateTime.Now,
     * so protocol timestamps must represent server wall-clock time, not UTC epoch time.
     */
    fun fromLocalDateTime(value: LocalDateTime): Double {
        val days = value.toLocalDate().toEpochDay() - OA_EPOCH_DATE.toEpochDay()
        val seconds = value.toLocalTime().toSecondOfDay() + value.nano / 1_000_000_000.0
        return days.toDouble() + seconds / 86_400.0
    }

    /** 取得當前 UTC-based OADate (persistence durations). */
    fun now(): Double = fromMillis(System.currentTimeMillis())

    /** DVT protocol server clock: local wall-clock OADate. */
    fun nowLocal(): Double = fromLocalDateTime(LocalDateTime.now())

    /** 從現在起算 N 天後的 OADate（用於信件過期時間等） */
    fun daysFromNow(days: Int): Double = now() + days.toDouble()
}

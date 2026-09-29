package tw.tsm.domain.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Công cụ chuyển đổi OLE Automation Date
 *
 * OADate là định dạng ngày của Windows COM: lấy 1899-12-30 00:00:00 làm mốc,
 * Phần nguyên biểu thị số ngày, phần thập phân biểu thị tỷ lệ thời gian đã trôi qua trong ngày.
 * Ví dụ 1.5 = 1899-12-31 12:00:00
 */
object OleDate {

    /** Hiệu số ngày giữa mốc OADate và Unix epoch (1970-01-01)*/
    private const val OA_EPOCH_OFFSET_DAYS = 25569.0

    /** Số mili giây trong một ngày*/
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** OLE epoch as a wall-clock calendar date. */
    private val OA_EPOCH_DATE: LocalDate = LocalDate.of(1899, 12, 30)

    /** Unix mili giây → OADate*/
    fun fromMillis(millis: Long): Double =
        millis / MILLIS_PER_DAY + OA_EPOCH_OFFSET_DAYS

    /** OADate → Unix mili giây*/
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

    /** Lấy OADate hiện tại theo UTC (persistence durations).*/
    fun now(): Double = fromMillis(System.currentTimeMillis())

    /** DVT protocol server clock: local wall-clock OADate. */
    fun nowLocal(): Double = fromLocalDateTime(LocalDateTime.now())

    /** OADate của N ngày kể từ hiện tại (dùng cho thời hạn thư v.v.)*/
    fun daysFromNow(days: Int): Double = now() + days.toDouble()
}

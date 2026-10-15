package tw.tsm.gamelogic.systems

/** Verified login days + previously claimed distinct daily login prizes only. */
object WelfareLoginDayPolicy {
    fun visibleDay(recordedDays: Int, previouslyClaimedDays: Int): Int =
        maxOf(1, recordedDays, previouslyClaimedDays).coerceAtMost(65535)
}

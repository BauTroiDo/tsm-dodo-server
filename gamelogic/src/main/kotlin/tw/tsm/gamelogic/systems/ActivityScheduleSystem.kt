package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.util.OleDate
import tw.tsm.protocol.PacketWriter
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * DVT protocol 77 schedule/state bridge.
 *
 * Lua UIActivity reads static IOS_ActivityTime_C.dat locally, but runtime
 * serverStartTime/serverCloseTime/state only change when S:077-001/002 arrive.
 */
class ActivityScheduleSystem(
    private val activityTimes: Map<Int, ActivityTimeDef>,
    private val adminBypassPolicy: AdminBypassPolicy = AdminBypassPolicy(),
    private val battlePassSeasonIds: Set<Int> = emptySet(),
) {
    fun sendAllActivityStates(
        session: GameSession,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        activityTimes.keys.sorted().forEach { activityId ->
            sendActivityState(session, activityId, now)
        }
    }

    fun sendActivityState(
        session: GameSession,
        activityId: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val def = activityTimes[activityId] ?: return
        if (def.switchKind < 1) return

        if (activityId in battlePassSeasonIds) {
            val start = BattlePassSystem.start(def)
            val close = BattlePassSystem.end(def)
            val state = when {
                now.isBefore(start) -> STATE_NONE
                !now.isBefore(close) -> STATE_ENDED
                !isOpenDay(def, now.toLocalDate()) -> STATE_NONE
                else -> STATE_STARTED
            }
            session.send(PacketWriter().writeByte(MAIN_KIND).writeByte(1).writeByte(0)
                .writeShortLE(activityId).writeDoubleLE(OleDate.fromLocalDateTime(start))
                .writeDoubleLE(OleDate.fromLocalDateTime(close)).toByteArray())
            session.send(PacketWriter().writeByte(MAIN_KIND).writeByte(2).writeByte(0)
                .writeShortLE(activityId).writeByte(state).toByteArray())
            return
        }
        val bypassSchedule = adminBypassPolicy.bypassSchedule(session)
        val today = now.toLocalDate()
        val start = if (bypassSchedule) now.minusMinutes(1)
        else today.atTime(def.openHour, def.openMinute)
        var close = if (bypassSchedule) now.plusDays(1)
        else today.atTime(def.closeHour, def.closeMinute)
        if (!close.isAfter(start)) close = close.plusDays(1)

        val state = calculateState(def, now, bypassSchedule)

        session.send(
            PacketWriter()
                .writeByte(MAIN_KIND).writeByte(1).writeByte(0)
                .writeShortLE(activityId)
                .writeDoubleLE(OleDate.fromLocalDateTime(start))
                .writeDoubleLE(OleDate.fromLocalDateTime(close))
                .toByteArray()
        )
        session.send(
            PacketWriter()
                .writeByte(MAIN_KIND).writeByte(2).writeByte(0)
                .writeShortLE(activityId)
                .writeByte(state)
                .toByteArray()
        )
    }

    fun calculateState(
        def: ActivityTimeDef,
        now: LocalDateTime,
        bypassSchedule: Boolean = false,
    ): Int {
        if (bypassSchedule) return STATE_STARTED

        val staticStart = LocalDateTime.of(
            def.startYear, def.startMonth, def.startDay, def.startHour, def.startMinute
        )
        val staticEnd = LocalDateTime.of(
            def.endYear, def.endMonth, def.endDay, def.endHour, def.endMinute
        )
        if (now.isBefore(staticStart)) return STATE_NONE
        if (now.isAfter(staticEnd)) return STATE_ENDED

        if (!isOpenDay(def, now.toLocalDate())) return STATE_NONE

        val open = now.toLocalDate().atTime(def.openHour, def.openMinute)
        var close = now.toLocalDate().atTime(def.closeHour, def.closeMinute)
        if (!close.isAfter(open)) close = close.plusDays(1)

        return when {
            now.isBefore(open) -> STATE_NONE
            now.isBefore(close) -> STATE_STARTED
            else -> STATE_ENDED
        }
    }

    private fun isOpenDay(def: ActivityTimeDef, date: LocalDate): Boolean {
        if (def.openDayMask == 0) return true
        val bit = 1 shl (date.dayOfWeek.value - 1) // Monday bit0 ... Sunday bit6
        return def.openDayMask and bit != 0
    }

    companion object {
        const val MAIN_KIND = 77
        const val JUDGMENT_TOWER_ACTIVITY_ID = 1

        const val STATE_NONE = 0
        const val STATE_STARTED = 1
        const val STATE_ENDED = 2
    }
}

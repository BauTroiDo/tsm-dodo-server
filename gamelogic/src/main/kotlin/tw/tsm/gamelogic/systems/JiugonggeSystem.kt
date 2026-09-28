package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.data.ActivityTimeDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.JiugonggeInfoDef
import tw.tsm.domain.data.JiugonggeMissionDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.util.OleDate
import tw.tsm.protocol.PacketWriter
import java.time.LocalDateTime

private val jiugongLogger = KotlinLogging.logger {}

/**
 * DVT 九宮格 (Daily) system.
 *
 * Authoritative client contracts:
 * - UIJiugongge.lua: daily button opens EUIJiugonggeOpenWay.Jiugongge.
 * - Jiugongge.lua: S:091-001 layout and mission completion semantics.
 * - CheckCondition.lua: ECondition values.
 *
 * This first server slice intentionally enables only the regular daily board
 * (JiugonggeInfo kind=1). Open-server/festival board rotation is separate and
 * is not guessed here.
 */
class JiugonggeSystem(
    private val infos: Map<Int, JiugonggeInfoDef>,
    private val missions: Map<Int, JiugonggeMissionDef>,
    private val activityTimes: Map<Int, ActivityTimeDef>,
    private val roleCountSystem: RoleCountSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val inventorySystem: InventorySystem,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
) {
    private fun dailyInfo(): JiugonggeInfoDef? =
        infos.values.filter { it.kind == KIND_DAILY }.minByOrNull { it.id }

    fun dailyMissions(): List<JiugonggeMissionDef> {
        val info = dailyInfo() ?: return emptyList()
        return missions.values
            .filter { it.boardNumber == info.id.toLong() }
            .sortedBy { it.id }
            .take(9)
    }

    suspend fun sendDailyInit(session: GameSession) {
        refreshDailyActivityTime(session)
        sendDailySnapshot(session)
    }

    /**
     * Push lại S:091-001. UIJiugongge.SetJiugongge() sẽ gọi ChangeGrid khi UI đang mở,
     * nhờ đó đăng ký lại UpdateTime nếu lần render đầu đã tự InvaildSet trước khi
     * phản hồi C:001-016 từ server quay về.
     */
    suspend fun sendDailySnapshot(session: GameSession) {
        val info = dailyInfo() ?: run {
            jiugongLogger.warn { "Không có Jiugongge kind=1 trong data" }
            return
        }
        val boardMissions = dailyMissions()
        if (boardMissions.size != 9) {
            jiugongLogger.warn { "Jiugongge daily id=${info.id} cần 9 mission, hiện có ${boardMissions.size}" }
            return
        }

        val values = boardMissions.map { mission ->
            val current = currentValue(session, mission)
            Triple(mission, current, compare(current, mission.conditionOperator, mission.conditionValue))
        }

        val writer = PacketWriter()
            .writeByte(MAIN_KIND)
            .writeByte(1)
            .writeByte(0)
            .writeByte(1)
            .writeShortLE(info.id)
            .writeByte(1)

        for ((mission, current, completed) in values) {
            writer.writeShortLE(mission.id)
            writer.writeIntLE(current.coerceIn(0L, 0xFFFF_FFFFL).toInt())
            writer.writeByte(if (completed) 1 else 0)
        }
        session.send(writer.toByteArray())
        jiugongLogger.info { "Đồng bộ Hằng ngày: charId=${session.roleId} grid=${info.id}" }
    }

    suspend fun checkMission(
        session: GameSession,
        gridId: Int,
        index: Int,
        missionId: Int,
    ): Boolean {
        val info = dailyInfo() ?: return false
        if (gridId != info.id || index !in 1..9) return false
        val boardMissions = dailyMissions()
        val mission = boardMissions.getOrNull(index - 1) ?: return false
        if (mission.id != missionId) return false
        if (!isComplete(session, mission)) return false

        session.send(
            PacketWriter()
                .writeByte(MAIN_KIND).writeByte(2).writeByte(0)
                .writeByte(1)
                .writeShortLE(gridId)
                .writeByte(index)
                .toByteArray()
        )
        return true
    }

    suspend fun claimReward(session: GameSession, gridId: Int, index: Int, missionId: Int): Boolean {
        val info = dailyInfo() ?: return false
        if (gridId != info.id || index !in 1..7) return false
        val boardMissions = dailyMissions()
        if (boardMissions.size != 9) return false
        if (boardMissions.getOrNull(index - 1)?.id != missionId) return false

        val reward = info.awards.getOrNull(index - 1) ?: return false
        val charId = session.roleId.toLong()
        val flags = bitFlagSystem.getSetFlagIds(charId)
        if (reward.getFlag in flags) return false

        val eligible = if (index == 7) {
            info.awards.take(6).all { it.getFlag in flags }
        } else {
            rewardMissionIndexes(index).all { pos -> isComplete(session, boardMissions[pos]) }
        }
        if (!eligible) return false

        val quantity = reward.quantity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (reward.itemId > 0 && quantity > 0) {
            if (!canFitReward(charId, reward.itemId, quantity)) return false
            val added = inventorySystem.addItemsToBag(charId, reward.itemId, quantity)
            if (added.isEmpty()) return false
            for ((slot, thing) in added) {
                responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
            }
        }

        bitFlagSystem.setFlag(charId, reward.getFlag)
        responseSender.sendBitFlagChanges(session, listOf(reward.getFlag to true))

        session.send(
            PacketWriter()
                .writeByte(MAIN_KIND).writeByte(3).writeByte(0)
                .writeByte(1)
                .writeShortLE(gridId)
                .writeByte(index)
                .toByteArray()
        )
        jiugongLogger.info {
            "Nhận thưởng Hằng ngày: charId=$charId grid=$gridId index=$index item=${reward.itemId} x$quantity"
        }
        return true
    }

    private suspend fun isComplete(session: GameSession, mission: JiugonggeMissionDef): Boolean {
        val current = currentValue(session, mission)
        return compare(current, mission.conditionOperator, mission.conditionValue)
    }

    private suspend fun currentValue(session: GameSession, mission: JiugonggeMissionDef): Long {
        val charId = session.roleId.toLong()
        return when (mission.conditionKind) {
            14 -> roleCountSystem.getValue(charId, mission.conditionKindValue.toInt()).toLong()
            17 -> if (mission.conditionKindValue.toInt() in bitFlagSystem.getSetFlagIds(charId)) 1L else 0L
            18 -> roleCountSystem.getValue(charId, RoleCountSystem.FRIEND_COUNT).toLong()
            19 -> {
                val requiredTurn = mission.conditionKindValue.toInt()
                if (session.turn >= requiredTurn) session.level.toLong() + session.turn * PLAYER_MAX_LEVEL else 0L
            }
            else -> 0L
        }
    }

    private fun compare(actual: Long, operator: Int, expected: Long): Boolean = when (operator) {
        1 -> actual == expected
        2 -> actual > expected
        3 -> actual >= expected
        4 -> actual < expected
        5 -> actual <= expected
        6 -> actual != expected
        else -> false
    }

    private fun rewardMissionIndexes(index: Int): IntArray = when (index) {
        1 -> intArrayOf(0, 1, 2)
        2 -> intArrayOf(3, 4, 5)
        3 -> intArrayOf(6, 7, 8)
        4 -> intArrayOf(0, 3, 6)
        5 -> intArrayOf(1, 4, 7)
        6 -> intArrayOf(2, 5, 8)
        else -> intArrayOf()
    }

    private fun canFitReward(charId: Long, itemId: Int, quantity: Int): Boolean {
        val bag = inventorySystem.getBag(charId)
        val capacity = roleCountSystem.getBagCapacity(charId)
        val def = itemDefs[itemId]
        val stackLimit = if (def != null && def.fitType == 0) ItemConstants.MAX_STACK else 1
        var room = if (stackLimit > 1) {
            bag.values.filter { it.itemId == itemId }
                .sumOf { (stackLimit - it.quantity).coerceAtLeast(0) }
        } else 0
        room += (capacity - bag.size).coerceAtLeast(0) * stackLimit
        return room >= quantity
    }

    /**
     * Refresh the active daily window on demand.
     *
     * Time-sensitive client UIs issue C:001-016 when opened. Re-sending S:077
     * here keeps activityTimes[activityId][1].serverStartTime/serverCloseTime
     * valid even if the player stayed online across a day boundary.
     */
    fun refreshDailyActivityTime(session: GameSession) {
        val info = dailyInfo() ?: return
        sendActivityWindow(session, info.activityId)
    }

    private fun sendActivityWindow(session: GameSession, activityId: Int) {
        val def = activityTimes[activityId] ?: return

        // DVT S:077-001 carries the runtime opening window, not the static
        // lifetime from ActivityTime_C.start*/end*. UIActivity compares
        // serverStartTime.DayOfWeek with CGTimer.serverTime and UIJiugongge
        // treats "before start" / "after close" as inactive.
        //
        // Activity 12 (daily nine-grid) is open every day 00:00-23:59, so
        // construct today's wall-clock window. This must use the same local
        // wall clock as S:001-016 / CGTimer.SetServerTime().
        val now = LocalDateTime.now()
        val startDateTime = now.toLocalDate().atTime(def.openHour, def.openMinute)
        var closeDateTime = now.toLocalDate().atTime(def.closeHour, def.closeMinute)
        if (!closeDateTime.isAfter(startDateTime)) {
            closeDateTime = closeDateTime.plusDays(1)
        }

        val start = OleDate.fromLocalDateTime(startDateTime)
        val close = OleDate.fromLocalDateTime(closeDateTime)

        session.send(
            PacketWriter()
                .writeByte(ACTIVITY_MAIN_KIND).writeByte(1).writeByte(0)
                .writeShortLE(activityId)
                .writeDoubleLE(start)
                .writeDoubleLE(close)
                .toByteArray()
        )
        session.send(
            PacketWriter()
                .writeByte(ACTIVITY_MAIN_KIND).writeByte(2).writeByte(0)
                .writeShortLE(activityId)
                .writeByte(1)
                .toByteArray()
        )
    }

    companion object {
        const val MAIN_KIND = 91
        const val ACTIVITY_MAIN_KIND = 77
        const val KIND_DAILY = 1
        const val PLAYER_MAX_LEVEL = 200
    }
}

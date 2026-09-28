package tw.tsm.gamelogic.systems

import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender

/**
 * Bridge between authoritative gameplay events and DVT CheckCondition/Jiugongge.
 *
 * Client contracts:
 * - RoleCount.Set() (S:085-002) emits CheckCondition(ECondition.RoleCount).
 * - BitFlag.Set() (S:081-001) emits CheckCondition(ECondition.Flag) for flags 1490..1499.
 * - Jiugongge listens to those notifications and sends C:091-002 when a mission completes.
 *
 * The old server loaded RoleCount but did not advance the counters for normal gameplay,
 * so the Daily UI stayed at 0/x even after the player had performed the action.
 */
class DailyProgressSystem(
    private val roleCountSystem: RoleCountSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val responseSender: ResponseSender,
) {
    fun incrementRoleCount(
        session: GameSession,
        countIndex: Int,
        delta: Int = 1,
    ): Int {
        if (delta <= 0 || session.roleId <= 0) return roleCountSystem.getValue(session.roleId.toLong(), countIndex)
        val charId = session.roleId.toLong()
        val value = roleCountSystem.increment(charId, countIndex, delta)
        val max = roleCountSystem.getMaxValue(charId, countIndex)
        responseSender.sendRoleCountUpdate(session, mapOf(countIndex to (value to max)))
        return value
    }

    fun setRoleCountAtLeast(
        session: GameSession,
        countIndex: Int,
        value: Int,
    ): Int {
        val charId = session.roleId.toLong()
        if (charId <= 0) return 0
        val old = roleCountSystem.getValue(charId, countIndex)
        if (value <= old) return old
        val max = roleCountSystem.getMaxValue(charId, countIndex)
        roleCountSystem.setValue(charId, countIndex, value, max)
        responseSender.sendRoleCountUpdate(session, mapOf(countIndex to (value to max)))
        return value
    }

    suspend fun setConditionFlag(session: GameSession, flagId: Int) {
        val charId = session.roleId.toLong()
        if (charId <= 0) return
        if (flagId in bitFlagSystem.getSetFlagIds(charId)) return
        bitFlagSystem.setFlag(charId, flagId)
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))
    }

    companion object {
        // ERoleCount from client lua/Logic/RoleCount.lua.
        const val CATCH_SUCCESS = 151
        const val MOUNTS = 160
        const val FIGHT = 163
        const val SKILL_POINT = RoleCountSystem.SKILL_POINT
        const val ATTRIBUTE_POINT = RoleCountSystem.ATTRIBUTE_POINT
        const val FRIEND_MAX = RoleCountSystem.FRIEND_COUNT

        // JiugonggeMission_C.dat:
        // mission 4 = "Xuất chiến võ tướng", mission 9 = "Lần đầu tổ đội",
        // mission 10 = "Gia nhập quân đoàn".
        const val FLAG_DEPLOY_FOLLOW_NPC = 1499
        const val FLAG_FIRST_PARTY = 1498
        const val FLAG_JOIN_ORGANIZATION = 1497
    }
}

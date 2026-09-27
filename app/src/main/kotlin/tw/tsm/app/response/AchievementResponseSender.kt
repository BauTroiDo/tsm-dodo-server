package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * 成就/RoleCount 封包序列化子模組 — mainKind=82, 85
 */
class AchievementResponseSender {

    /**
     * S:082-001 完成成就回應
     *
     * 客戶端 ReciveCompleteAchievement 無論結果都讀 result + id。
     */
    fun sendAchievementComplete(session: SessionSender, result: Int, achievementId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.ACHIEVEMENT).writeByte(1).writeByte(0)
                .writeByte(result)
                .writeShortLE(achievementId)
                .toByteArray()
        )
    }

    /**
     * S:082-002 成就領獎回應
     *
     * 客戶端 ReciveGetAchievement 只在 result=0 時讀取 id。
     */
    fun sendAchievementReward(session: SessionSender, result: Int, achievementId: Int) {
        val writer = PacketWriter()
            .writeByte(Opcodes.ACHIEVEMENT).writeByte(2).writeByte(0)
            .writeByte(result)
        if (result == 0) {
            writer.writeShortLE(achievementId)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:085-002 更新角色計數器（多筆）
     *
     * 對應客戶端 protocolTable[85][2]：
     * +數量(2 LE) <<+索引(2 LE) +數值(4 LE, unsigned) +上限(4 LE, signed)>>
     */
    fun sendRoleCountUpdate(session: SessionSender, counts: Map<Int, Pair<Int, Int>>) {
        if (counts.isEmpty()) return
        val writer = PacketWriter()
            .writeByte(85).writeByte(2).writeByte(0)
            .writeShortLE(counts.size)
        for ((index, valuePair) in counts) {
            writer.writeShortLE(index)
            writer.writeIntLE(valuePair.first)   // value（unsigned 但用 Int 傳）
            writer.writeIntLE(valuePair.second)  // maxValue（signed）
        }
        session.send(writer.toByteArray())
    }
}

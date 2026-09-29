package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.DvtRoleCountWire
import tw.tsm.protocol.PacketWriter

/**
 * Submodule tuần tự hoá gói tin thành tựu/RoleCount — mainKind=82, 85
 */
class AchievementResponseSender {

    /**
     * S:082-001 phản hồi hoàn thành thành tựu
     *
     * ReciveCompleteAchievement của client dù kết quả thế nào đều đọc result + id.
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
     * S:082-002 phản hồi nhận thưởng thành tựu
     *
     * ReciveGetAchievement của client chỉ đọc id khi result=0.
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
     * S:085-002 cập nhật bộ đếm nhân vật (nhiều bản ghi)
     *
     * Tương ứng protocolTable[85][2] của client:
     * +số lượng(2 LE) <<+chỉ số(2 LE) +giá trị(4 LE, unsigned) +giới hạn trên(4 LE, signed)>>
     */
    fun sendRoleCountUpdate(session: SessionSender, counts: Map<Int, Pair<Int, Int>>) {
        if (counts.isEmpty()) return
        val writer = PacketWriter()
            .writeByte(85).writeByte(2).writeByte(0)
            .writeShortLE(counts.size)
        for ((index, valuePair) in counts) {
            writer.writeShortLE(index)
            writer.writeIntLE(DvtRoleCountWire.encodeValue(index, valuePair.first))
            writer.writeIntLE(valuePair.second)  // maxValue（signed）
        }
        session.send(writer.toByteArray())
    }
}

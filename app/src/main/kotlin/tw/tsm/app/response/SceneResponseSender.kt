package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * Tuần tự hoá gói tin liên quan cảnh
 *
 * mainKind = 7（cảnh）, 12（chuyển cảnh）, 22（MapNpc）
 */
class SceneResponseSender {

    /**
     * S:007-001 Danh sách phân khu
     *
     * Định dạng: +count(1) + [index(2) + currentPlayers(2) + maxPlayers(2)]...
     */
    fun sendAreaList(session: SessionSender, onlineCount: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SCENE).writeByte(1).writeByte(0)
                .writeByte(1)              // Số lượng phân khu = 1
                .writeShortLE(0)           // index phân khu = 0
                .writeShortLE(onlineCount)
                .writeShortLE(500)         // Số người tối đa
                .toByteArray()
        )
    }

    /** S:007-002 Kết quả chuyển phân khu*/
    fun sendAreaSwitchResult(session: SessionSender, resultCode: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SCENE).writeByte(2).writeByte(0)
                .writeByte(resultCode)
                .toByteArray()
        )
    }

    /**
     * S:007-000 Người chơi dịch chuyển tức thời
     *
     * Định dạng: +roleId(8) +sceneId(2) +x(2) +y(2)
     */
    fun sendPlayerTeleport(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SCENE).writeByte(0).writeByte(0)
                .writeLongLE(playerId)
                .writeShortLE(sceneId)
                .writeShortLE(x)
                .writeShortLE(y)
                .toByteArray()
        )
    }

    /**
     * S:012-000 Thông báo chuyển cảnh
     *
     * Định dạng: +roleId(8) +sceneId(2) +x(2) +y(2) +tag(2) +instanceId(2)
     *       +equipCount(1) +[itemId(2)]... +outfitCount(1) +[outfitId(2)]... +titleId(2)
     */
    fun sendSceneChange(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int, equipItemIds: IntArray = intArrayOf(), titleId: Int = 0, instanceId: Int = 0) {
        val writer = PacketWriter()
            .writeByte(12).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(sceneId)
            .writeShortLE(x)
            .writeShortLE(y)
            .writeShortLE(0)   // sceneTag
            .writeShortLE(instanceId)   // instanceId
        // Ngoại hình trang bị
        writer.writeByte(equipItemIds.size)
        for (itemId in equipItemIds) {
            writer.writeShortLE(itemId)
        }
        // Trang bị hiển thị ngoài（hiện chưa có）
        writer.writeByte(0)
        // Danh hiệu
        writer.writeShortLE(titleId)
        session.send(writer.toByteArray())
    }

    /**
     * S:022-004 Khởi tạo MapNpc
     *
     * Định dạng: +số lượng(2) <<+chỉ mục(2) +thứ tự hình(1) +loại chiến đấu(1) +vị trí x(2) +vị trí y(2) +loại hiển thị(1) +thời gian ẩn(4)>>
     */
    fun sendMapNpcInit(session: SessionSender, npcs: List<EveNpcPlacement>) {
        val activeNpcs = npcs.filter { !it.close }
        val writer = PacketWriter(3 + 2 + activeNpcs.size * 12)
            .writeByte(Opcodes.MAP_NPC).writeByte(4).writeByte(0)
            .writeShortLE(activeNpcs.size)
        for (npc in activeNpcs) {
            writer.writeShortLE(npc.id)
            writer.writeByte(0)                        // Thứ tự hình ảnh
            writer.writeByte(0)                        // Loại chiến đấu
            writer.writeShortLE(npc.x)
            writer.writeShortLE(npc.y)
            writer.writeByte(1)                        // Loại hiển thị
            writer.writeUIntLE(0)                      // Thời gian ẩn
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:022-002 MapNpc di chuyển
     *
     * Định dạng: +chỉ số(2) +toạ độ X(2) +toạ độ Y(2)
     */
    fun buildMapNpcMovePacket(npcIndex: Int, x: Int, y: Int): ByteArray {
        return PacketWriter(9)
            .writeByte(Opcodes.MAP_NPC).writeByte(2).writeByte(0)
            .writeShortLE(npcIndex)
            .writeShortLE(x)
            .writeShortLE(y)
            .toByteArray()
    }
}

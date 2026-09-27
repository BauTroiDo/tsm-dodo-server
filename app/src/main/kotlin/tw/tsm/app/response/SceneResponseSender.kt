package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * 場景相關封包序列化
 *
 * mainKind = 7（場景）, 12（場景切換）, 22（MapNpc）
 */
class SceneResponseSender {

    /**
     * S:007-001 分區列表
     *
     * 格式：+count(1) + [index(2) + currentPlayers(2) + maxPlayers(2)]...
     */
    fun sendAreaList(session: SessionSender, onlineCount: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SCENE).writeByte(1).writeByte(0)
                .writeByte(1)              // 分區數量 = 1
                .writeShortLE(0)           // 分區 index = 0
                .writeShortLE(onlineCount)
                .writeShortLE(500)         // 最大人數
                .toByteArray()
        )
    }

    /** S:007-002 分區切換結果 */
    fun sendAreaSwitchResult(session: SessionSender, resultCode: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.SCENE).writeByte(2).writeByte(0)
                .writeByte(resultCode)
                .toByteArray()
        )
    }

    /**
     * S:007-000 玩家瞬移
     *
     * 格式：+roleId(8) +sceneId(2) +x(2) +y(2)
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
     * S:012-000 場景切換通知
     *
     * 格式：+roleId(8) +sceneId(2) +x(2) +y(2) +tag(2) +instanceId(2)
     *       +equipCount(1) +[itemId(2)]... +outfitCount(1) +[outfitId(2)]... +titleId(2)
     */
    fun sendSceneChange(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int, equipItemIds: IntArray = intArrayOf(), titleId: Int = 0) {
        val writer = PacketWriter()
            .writeByte(12).writeByte(0).writeByte(0)
            .writeLongLE(playerId)
            .writeShortLE(sceneId)
            .writeShortLE(x)
            .writeShortLE(y)
            .writeShortLE(0)   // sceneTag
            .writeShortLE(0)   // instanceId
        // 裝備外觀
        writer.writeByte(equipItemIds.size)
        for (itemId in equipItemIds) {
            writer.writeShortLE(itemId)
        }
        // 外顯裝備（目前暫無）
        writer.writeByte(0)
        // 稱號
        writer.writeShortLE(titleId)
        session.send(writer.toByteArray())
    }

    /**
     * S:022-004 MapNpc 初始化
     *
     * 格式：+數量(2) <<+索引(2) +圖序(1) +戰鬥類型(1) +位置x(2) +位置y(2) +顯示種類(1) +隱藏時間(4)>>
     */
    fun sendMapNpcInit(session: SessionSender, npcs: List<EveNpcPlacement>) {
        val activeNpcs = npcs.filter { !it.close }
        val writer = PacketWriter(3 + 2 + activeNpcs.size * 12)
            .writeByte(Opcodes.MAP_NPC).writeByte(4).writeByte(0)
            .writeShortLE(activeNpcs.size)
        for (npc in activeNpcs) {
            writer.writeShortLE(npc.id)
            writer.writeByte(0)                        // 圖序
            writer.writeByte(0)                        // 戰鬥類型
            writer.writeShortLE(npc.x)
            writer.writeShortLE(npc.y)
            writer.writeByte(1)                        // 顯示種類
            writer.writeUIntLE(0)                      // 隱藏時間
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:022-002 MapNpc 移動
     *
     * 格式：+索引(2) +座標X(2) +座標Y(2)
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

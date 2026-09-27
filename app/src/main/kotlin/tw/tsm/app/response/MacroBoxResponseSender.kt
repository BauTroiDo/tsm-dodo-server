package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.SparSlot
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * 機關盒封包序列化 — mainKind=65 的 14 個 S→C 封包
 *
 * 來源：protocal.lua protocolTable[65]
 */
class MacroBoxResponseSender {

    private val mainKind = Opcodes.MACHINE_BOX

    /**
     * S:065-001 機關盒啟動成功
     */
    fun sendMacroBoxStart(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(1).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-002 機關盒暫停
     */
    fun sendMacroBoxPause(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(2).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-003 場景新增玩家小寵物
     *
     * 格式：+roleId(8B LE) +kind(1B)
     */
    fun sendMiniPetAppear(session: SessionSender, roleId: Long, kind: Int) {
        val writer = PacketWriter(3 + 8 + 1)
            .writeByte(mainKind).writeByte(3).writeByte(0)
            .writeLongLE(roleId)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-004 舊玩家小寵物列表
     *
     * 格式：迴圈{roleId(8B LE) kind(1B)}
     */
    fun sendMiniPetList(session: SessionSender, pets: Map<Long, Int>) {
        val writer = PacketWriter(3 + pets.size * 9)
            .writeByte(mainKind).writeByte(4).writeByte(0)
        for ((roleId, kind) in pets) {
            writer.writeLongLE(roleId)
            writer.writeByte(kind)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:065-005 開啟機關盒介面（空封包）
     */
    fun sendMacroBoxOpen(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(5).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-006 機關盒晶石資料
     *
     * 格式：迴圈{index(1B) sparId(2B LE) count(2B LE)}
     */
    fun sendMacroBoxSparData(session: SessionSender, sparSlots: Map<Int, SparSlot>) {
        val writer = PacketWriter(3 + sparSlots.size * 5)
            .writeByte(mainKind).writeByte(6).writeByte(0)
        for ((index, slot) in sparSlots) {
            writer.writeByte(index)
            writer.writeShortLE(slot.sparId)
            writer.writeShortLE(slot.count)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:065-007 機關盒訊息
     *
     * 格式：+kind(1B)
     */
    fun sendMacroBoxMessage(session: SessionSender, kind: Int) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(7).writeByte(0)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-008 機關盒晶石擴充數量
     *
     * 格式：+count(1B) +showMsg(1B)
     */
    fun sendMacroBoxSparExtend(session: SessionSender, count: Int, showMsg: Boolean) {
        val writer = PacketWriter(3 + 2)
            .writeByte(mainKind).writeByte(8).writeByte(0)
            .writeByte(count)
            .writeByte(if (showMsg) 1 else 0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-009 機關盒晶石移除訊息（空封包）
     */
    fun sendMacroBoxSparRemoveMsg(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(9).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-010 機關盒進行補給
     *
     * 格式：+checkItem(1B bool)
     */
    fun sendMacroBoxSupply(session: SessionSender, checkItem: Boolean) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(10).writeByte(0)
            .writeByte(if (checkItem) 1 else 0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-011 經驗瓶資料
     *
     * 格式：+priority(1B) + 迴圈{index(1B) bottleId(2B LE) exp(4B LE)}
     */
    fun sendMacroBoxExpBottleData(session: SessionSender, priority: Int, bottles: Map<Int, ExpBottleSlot>) {
        val writer = PacketWriter(3 + 1 + bottles.size * 7)
            .writeByte(mainKind).writeByte(11).writeByte(0)
            .writeByte(priority)
        for ((index, bottle) in bottles) {
            writer.writeByte(index)
            writer.writeShortLE(bottle.bottleId)
            writer.writeIntLE(bottle.exp)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:065-012 更新經驗瓶
     *
     * 格式：+index(1B) +exp(4B LE)
     */
    fun sendMacroBoxUpdateExpBottle(session: SessionSender, index: Int, exp: Int) {
        val writer = PacketWriter(3 + 1 + 4)
            .writeByte(mainKind).writeByte(12).writeByte(0)
            .writeByte(index)
            .writeIntLE(exp)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-013 設定經驗瓶成功
     *
     * 格式：+index(1B)
     */
    fun sendMacroBoxSetExpBottle(session: SessionSender, index: Int) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(13).writeByte(0)
            .writeByte(index)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-014 機關盒經驗瓶擴充數量
     *
     * 格式：+count(1B) +showMsg(1B)
     */
    fun sendMacroBoxExpExtend(session: SessionSender, count: Int, showMsg: Boolean) {
        val writer = PacketWriter(3 + 2)
            .writeByte(mainKind).writeByte(14).writeByte(0)
            .writeByte(count)
            .writeByte(if (showMsg) 1 else 0)
        session.send(writer.toByteArray())
    }
}

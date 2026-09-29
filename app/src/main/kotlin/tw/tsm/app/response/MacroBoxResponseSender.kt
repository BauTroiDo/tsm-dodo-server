package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.SparSlot
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * Tuần tự hoá gói tin hộp cơ quan — 14 gói S→C của mainKind=65
 *
 * Nguồn: protocal.lua protocolTable[65]
 */
class MacroBoxResponseSender {

    private val mainKind = Opcodes.MACHINE_BOX

    /**
     * S:065-001 Hộp cơ quan khởi động thành công
     */
    fun sendMacroBoxStart(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(1).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-002 Hộp cơ quan tạm dừng
     */
    fun sendMacroBoxPause(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(2).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-003 Thêm thú cưng nhỏ của người chơi trong cảnh
     *
     * Định dạng: +roleId(8B LE) +kind(1B)
     */
    fun sendMiniPetAppear(session: SessionSender, roleId: Long, kind: Int) {
        val writer = PacketWriter(3 + 8 + 1)
            .writeByte(mainKind).writeByte(3).writeByte(0)
            .writeLongLE(roleId)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-004 Danh sách thú cưng nhỏ của người chơi cũ
     *
     * Định dạng: vòng lặp{roleId(8B LE) kind(1B)}
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
     * S:065-005 Mở giao diện hộp cơ quan（gói tin trống）
     */
    fun sendMacroBoxOpen(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(5).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-006 Dữ liệu tinh thạch hộp cơ quan
     *
     * Định dạng: vòng lặp {index(1B) sparId(2B LE) count(2B LE)}
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
     * S:065-007 Thông báo hộp cơ quan
     *
     * Định dạng: +kind(1B)
     */
    fun sendMacroBoxMessage(session: SessionSender, kind: Int) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(7).writeByte(0)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-008 Mở rộng số lượng tinh thạch hộp cơ quan
     *
     * Định dạng: +count(1B) +showMsg(1B)
     */
    fun sendMacroBoxSparExtend(session: SessionSender, count: Int, showMsg: Boolean) {
        val writer = PacketWriter(3 + 2)
            .writeByte(mainKind).writeByte(8).writeByte(0)
            .writeByte(count)
            .writeByte(if (showMsg) 1 else 0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-009 Thông báo xoá tinh thạch hộp cơ quan（gói tin trống）
     */
    fun sendMacroBoxSparRemoveMsg(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(mainKind).writeByte(9).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-010 Hộp cơ quan tiến hành tiếp tế
     *
     * Định dạng: +checkItem(1B bool)
     */
    fun sendMacroBoxSupply(session: SessionSender, checkItem: Boolean) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(10).writeByte(0)
            .writeByte(if (checkItem) 1 else 0)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-011 Dữ liệu bình kinh nghiệm
     *
     * Định dạng: +priority(1B) + vòng lặp {index(1B) bottleId(2B LE) exp(4B LE)}
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
     * S:065-012 Cập nhật bình kinh nghiệm
     *
     * Định dạng: +index(1B) +exp(4B LE)
     */
    fun sendMacroBoxUpdateExpBottle(session: SessionSender, index: Int, exp: Int) {
        val writer = PacketWriter(3 + 1 + 4)
            .writeByte(mainKind).writeByte(12).writeByte(0)
            .writeByte(index)
            .writeIntLE(exp)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-013 thiết lập bình kinh nghiệm thành công
     *
     * Định dạng: +index(1B)
     */
    fun sendMacroBoxSetExpBottle(session: SessionSender, index: Int) {
        val writer = PacketWriter(3 + 1)
            .writeByte(mainKind).writeByte(13).writeByte(0)
            .writeByte(index)
        session.send(writer.toByteArray())
    }

    /**
     * S:065-014 số lượng mở rộng bình kinh nghiệm của hộp cơ quan
     *
     * Định dạng: +count(1B) +showMsg(1B)
     */
    fun sendMacroBoxExpExtend(session: SessionSender, count: Int, showMsg: Boolean) {
        val writer = PacketWriter(3 + 2)
            .writeByte(mainKind).writeByte(14).writeByte(0)
            .writeByte(count)
            .writeByte(if (showMsg) 1 else 0)
        session.send(writer.toByteArray())
    }
}

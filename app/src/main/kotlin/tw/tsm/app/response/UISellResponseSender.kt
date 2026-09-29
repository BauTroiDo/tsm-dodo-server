package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * Phân mô-đun con tuần tự hoá gói tin 介消 — mainKind=84, 35
 *
 * Nguồn: protocal.lua protocolTable[84], Logic/UISell.lua
 */
class UISellResponseSender {

    /**
     * S:084-001 / S:084-002 介消 thất bại (kết quả=0)
     *
     * Định dạng: +mã số 介消(2) +kết quả(1)=0 +nguyên nhân(1) +giá trị(4)
     * Client string.GetUiSellFail(sellId, reason, value) chuyển thành thông báo thất bại.
     */
    fun sendUiSellFail(session: SessionSender, subKind: Int, sellId: Int, reason: Int, value: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.UI_SELL).writeByte(subKind).writeByte(0)
                .writeShortLE(sellId)
                .writeByte(0)
                .writeByte(reason)
                .writeIntLE(value.toInt())
                .toByteArray()
        )
    }

    /**
     * S:084-001 介消 báo giá tiền (kết quả 1=nguyên bảo / 2=nguyên bảo khoá / 3=tiền game)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1) +số tiền(4)
     */
    fun sendUiSellMoneyQuery(session: SessionSender, sellId: Int, currencyKind: Int, cost: Long) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.UI_SELL).writeByte(1).writeByte(0)
                .writeShortLE(sellId)
                .writeByte(currencyKind)
                .writeIntLE(cost.toInt())
                .toByteArray()
        )
    }

    /**
     * S:084-001 介消 báo giá vật phẩm (kết quả=255)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=255 +ID vật phẩm(2) +số lượng(2)
     * ⚠ Số lượng là 2 bytes: client UISell.ReceiveQuery dùng ReadUInt16 để đọc
     */
    fun sendUiSellItemQuery(session: SessionSender, sellId: Int, itemId: Int, count: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.UI_SELL).writeByte(1).writeByte(0)
                .writeShortLE(sellId)
                .writeByte(255)
                .writeShortLE(itemId)
                .writeShortLE(count)
                .toByteArray()
        )
    }

    /**
     * S:084-001 介消 báo giá miễn phí (kết quả=254)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=254
     */
    fun sendUiSellFreeQuery(session: SessionSender, sellId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.UI_SELL).writeByte(1).writeByte(0)
                .writeShortLE(sellId)
                .writeByte(254)
                .toByteArray()
        )
    }

    /**
     * S:084-002 Kết quả 介消 thành công
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=1
     * Client UISell.UISellResult kích hoạt callback khi kết quả != 0.
     */
    fun sendUiSellSuccess(session: SessionSender, sellId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.UI_SELL).writeByte(2).writeByte(0)
                .writeShortLE(sellId)
                .writeByte(1)
                .toByteArray()
        )
    }

    /**
     * S:035-005 Hiển thị điểm còn lại
     *
     * Định dạng: +điểm thực(4) +điểm ảo(4)
     * Client Role.SetPoint(real) / Role.SetLockPoint(virtual).
     */
    fun sendPointBalance(session: SessionSender, pointReal: Int, pointVirtual: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CROSS_SERVER).writeByte(5).writeByte(0)
                .writeIntLE(pointReal)
                .writeIntLE(pointVirtual)
                .toByteArray()
        )
    }
}

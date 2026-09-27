package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/**
 * 介面消費封包序列化子模組 — mainKind=84, 35
 *
 * 來源：protocal.lua protocolTable[84]、Logic/UISell.lua
 */
class UISellResponseSender {

    /**
     * S:084-001 / S:084-002 介消失敗（結果=0）
     *
     * 格式：+介消編號(2) +結果(1)=0 +原因(1) +數值(4)
     * 客戶端 string.GetUiSellFail(sellId, reason, value) 轉為失敗訊息。
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
     * S:084-001 介消金錢報價（結果 1=元寶 / 2=綁元 / 3=遊戲幣）
     *
     * 格式：+介消編號(2) +結果(1) +金額(4)
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
     * S:084-001 介消道具報價（結果=255）
     *
     * 格式：+介消編號(2) +結果(1)=255 +物品ID(2) +數量(2)
     * ⚠ 數量為 2 bytes：客戶端 UISell.ReceiveQuery 用 ReadUInt16 讀取
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
     * S:084-001 介消免費報價（結果=254）
     *
     * 格式：+介消編號(2) +結果(1)=254
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
     * S:084-002 介消成功結果
     *
     * 格式：+介消編號(2) +結果(1)=1
     * 客戶端 UISell.UISellResult 在結果 != 0 時觸發 callback。
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
     * S:035-005 顯示剩餘點數
     *
     * 格式：+實點(4) +虛點(4)
     * 客戶端 Role.SetPoint(real) / Role.SetLockPoint(virtual)。
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

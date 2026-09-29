package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.DoroReward
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.protocol.PacketWriter

/** DVT Mall/Doro response wire. */
class MallResponseSender {
    /** S:023-150 — gacha egg/result list. Must precede S:066-003 success. */
    fun sendDoroResult(session: SessionSender, rewards: List<DoroReward>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.SHOP).writeByte(150).writeByte(0)
            .writeShortLE(rewards.size)
        for (reward in rewards) {
            writer.writeShortLE(reward.itemId)
            writer.writeIntLE(reward.count)
        }
        session.send(writer.toByteArray())
    }

    /** S:066-003 +itemId(UInt16) +result(Byte). result=1 success, 2 failure. */
    fun sendMallPurchaseResult(session: SessionSender, itemId: Int, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MALL).writeByte(3).writeByte(0)
                .writeShortLE(itemId)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:035-050 — Role.SetPlayerCurrency(kind, UInt32 value). */
    fun sendPlayerCurrency(session: SessionSender, currencyKind: Int, value: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.CROSS_SERVER).writeByte(50).writeByte(0)
                .writeByte(currencyKind)
                .writeIntLE(value.coerceAtLeast(0))
                .toByteArray()
        )
    }
}

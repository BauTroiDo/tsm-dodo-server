package tw.tsm.app.handlers

import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.protocol.PacketWriter

/** DVT Common/protocal.lua protocolTable[69][7] and [69][9].
 * Wire envelope: mainKind=69, subKind=7/9, 0, followed by the Lua-defined fields.
 * C069-001/002 are child skill deletion commands, NOT profile upload requests.
 */
object ChildPackets {
    fun snapshot(child: ChildDataSnapshot): ByteArray =
        PacketWriter(98).writeByte(69).writeByte(7).writeByte(0)
            .writeShortLE(ChildDataSnapshot.SIZE).writeBytes(child.toBytes()).toByteArray()

    /** Only emit after the server has assigned a genuine follow NPC slot. */
    fun deployed(followIndex: Int, child: ChildDataSnapshot): ByteArray {
        require(followIndex in 1..255) { "Only a real deployed child followIndex can be sent" }
        return PacketWriter(99).writeByte(69).writeByte(9).writeByte(0)
            .writeByte(followIndex).writeShortLE(ChildDataSnapshot.SIZE)
            .writeBytes(child.toBytes()).toByteArray()
    }
}

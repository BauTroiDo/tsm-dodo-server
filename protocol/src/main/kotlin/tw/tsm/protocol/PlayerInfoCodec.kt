package tw.tsm.protocol

/**
 * Công cụ tuần tự hóa danh thiếp (thông tin) người chơi
 *
 * Nguồn: PlayerInfo.lua readBaseInfo / readFriendData
 *
 * Định dạng danh thiếp:
 * ```
 * nameLen(1B) + name(Big5) + level(1B) + element(1B) + turn3Element(1B)
 * + turn(1B) + career(1B) + sex(1B) + headStyle(1B)
 * + colorTint1(4B LE) + colorTint2(4B LE)
 * ```
 */
object PlayerInfoCodec {

    /**
     * Ghi danh thiếp cơ bản (15B + độ dài tên)
     */
    fun PacketWriter.writePlayerCard(
        name: String,
        level: Int,
        element: Int,
        turn3Element: Int,
        turn: Int,
        career: Int,
        sex: Int,
        hair: Int,
        color1: Int,
        color2: Int
    ): PacketWriter {
        writeBig5WithByteLen(name)
        writeByte(level)
        writeByte(element)
        writeByte(turn3Element)
        writeByte(turn)
        writeByte(career)
        writeByte(sex)
        writeByte(hair)
        writeIntLE(color1)
        writeIntLE(color2)
        return this
    }

    /**
     * Ghi dữ liệu bổ sung của bạn bè (20B)
     *
     * Đặt ngay sau writePlayerCard.
     */
    fun PacketWriter.writeFriendExtra(
        online: Boolean,
        friendly: Int,
        functionFlag: Int,
        addTime: Double,
        offlineTime: Double
    ): PacketWriter {
        writeByte(if (online) 1 else 0)
        writeShortLE(friendly)
        writeByte(functionFlag)
        writeDoubleLE(addTime)
        writeDoubleLE(offlineTime)
        return this
    }
}
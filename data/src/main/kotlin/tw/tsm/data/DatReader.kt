package tw.tsm.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Trình đọc nhị phân .dat
 *
 * Đối chiếu 1:1 với `DatReader.lua` của client game mobile.
 * Hỗ trợ giải mã XOR (.dat bản PC) và đọc trực tiếp không mã hoá (`_C.dat` bản game mobile).
 *
 * @param data toàn bộ nội dung tệp
 * @param numberOffset lượng offset giá trị（dùng để giải mã）
 * @param xor1 khoá XOR 1-byte
 * @param xor2 khoá XOR 2-byte
 * @param xor4 khoá XOR 4-byte
 */
class DatReader(
    private val data: ByteArray,
    private val numberOffset: Int = 0,
    private val xor1: Int = 0,
    private val xor2: Int = 0,
    private val xor4: Int = 0
) {
    private var position: Int = 0

    /** Còn dữ liệu để đọc hay không*/
    fun canRead(): Boolean = position < data.size

    /** Chuyển đến vị trí chỉ định*/
    fun seek(pos: Int) {
        position = pos.coerceIn(0, data.size)
    }

    /** Vị trí đọc hiện tại*/
    fun position(): Int = position

    // ── Đọc giá trị số (đối chiếu DatReader:ReadNumber) ──

    /**
     * Đọc 1 byte không dấu
     *
     * Đối chiếu Lua `ReadByte(computeXOR, computeOffset)`
     */
    fun readByte(): Int {
        return readNumber(1, signed = false)
    }

    /**
     * Đọc 1 byte có dấu
     *
     * Đối chiếu Lua `ReadSByte(computeXOR, computeOffset)`
     */
    fun readSByte(): Int {
        return readNumber(1, signed = true)
    }

    /**
     * Đọc 2 byte không dấu (little-endian)
     *
     * Đối chiếu Lua `ReadUInt16(computeXOR, computeOffset)`
     */
    fun readUInt16(): Int {
        return readNumber(2, signed = false)
    }

    /**
     * Đọc 2 byte có dấu (little-endian)
     *
     * Đối chiếu Lua `ReadInt16(computeXOR, computeOffset)`
     */
    fun readInt16(): Int {
        return readNumber(2, signed = true)
    }

    /**
     * Đọc 4 byte không dấu (little-endian)
     *
     * Đối chiếu Lua `ReadUInt32(computeXOR, computeOffset)`
     */
    fun readUInt32(): Long {
        return readNumber(4, signed = false).toLong() and 0xFFFFFFFFL
    }

    /**
     * Đọc 4 byte có dấu (little-endian)
     *
     * Đối chiếu Lua `ReadInt32(computeXOR, computeOffset)`
     */
    fun readInt32(): Int {
        return readNumber(4, signed = true)
    }

    /**
     * Đọc số thực dấu phẩy động độ chính xác kép IEEE 754 (8 byte, little-endian)
     *
     * Đối chiếu Lua `ReadDouble()`
     */
    fun readDouble(): Double {
        if (position + 8 > data.size) return 0.0

        val buf = ByteBuffer.wrap(data, position, 8).order(ByteOrder.LITTLE_ENDIAN)
        position += 8
        return buf.double
    }

    /**
     * Đọc giá trị boolean (1 byte == 1)
     *
     * Đối chiếu Lua `ReadBoolean(computeXOR, computeOffset)`
     */
    fun readBoolean(): Boolean {
        return readByte() == 1
    }

    // ── Đọc byte/chuỗi ──

    /**
     * Đọc byte thô
     *
     * Đối chiếu Lua `ReadBytes(size)`
     */
    fun readBytes(size: Int): ByteArray {
        if (position + size > data.size) return ByteArray(0)
        val result = data.copyOfRange(position, position + size)
        position += size
        return result
    }

    /**
     * Đọc chuỗi bản PC (1B độ dài + bytes đảo ngược + Big5)
     *
     * Đối chiếu Lua `ReadString(size, computeRevert, xor, offset)`
     * Định dạng: [count: 1B] [padding...] [chuỗi Big5 đã đảo ngược]
     * Tổng cộng tiêu tốn 1 + size bytes
     */
    fun readString(size: Int): String {
        if (position + 1 + size > data.size) return ""

        val count = data[position].toInt() and 0xFF
        position += 1

        val result = if (count > 0) {
            // Đọc đảo ngược: đọc count bytes từ position + size - count, sau đó đảo ngược
            val start = position + size - count
            val raw = data.copyOfRange(start, start + count)
            raw.reverse()
            String(raw, BIG5)
        } else {
            ""
        }

        position += size
        return result
    }

    /**
     * Đọc chuỗi ASCII độ dài cố định (không mã hoá, không đảo ngược)
     *
     * Định dạng: [count: 1B] [data: size bytes]
     * Tổng cộng tiêu tốn 1 + size bytes. Dùng cho các file không mã hoá như chỉ mục Ground.mmg.
     */
    fun readFixedAscii(size: Int): String {
        if (position + 1 + size > data.size) return ""
        val count = data[position].toInt() and 0xFF
        position += 1
        val result = if (count in 1..size) {
            String(data, position, count, Charsets.US_ASCII)
        } else ""
        position += size
        return result
    }

    /**
     * Đọc chuỗi Unicode bản game mobile (tiền tố độ dài 2B LE + UTF-16LE bytes)
     *
     * Đối chiếu Lua `LuaHelper.BytesToString(encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()))`
     */
    fun readUnicodeString(): String {
        val byteLen = readUInt16()
        if (byteLen <= 0) return ""
        val raw = readBytes(byteLen)
        return String(raw, UTF16LE)
    }

    // ── Cài đặt nội bộ ──

    /**
     * Đọc giá trị số chung (đối chiếu Lua `ReadNumber`)
     *
     * Ghép little-endian → giải mã XOR → trừ offset → chuyển đổi có dấu
     */
    private fun readNumber(size: Int, signed: Boolean): Int {
        if (position + size > data.size) return 0

        // Ghép little-endian
        var result = 0L
        for (i in 0 until size) {
            result = result or ((data[position + i].toLong() and 0xFF) shl (i * 8))
        }
        position += size

        // Giải mã XOR
        when (size) {
            1 -> if (xor1 != 0) result = result xor xor1.toLong()
            2 -> if (xor2 != 0) result = result xor xor2.toLong()
            4 -> if (xor4 != 0) result = result xor xor4.toLong()
        }

        // offset
        if (numberOffset != 0) {
            result -= numberOffset
        }

        // Chuyển đổi có dấu
        if (signed) {
            when (size) {
                1 -> result = result.toByte().toLong()
                2 -> result = result.toShort().toLong()
                4 -> result = result.toInt().toLong()
            }
        } else {
            // Không dấu: đảm bảo giá trị dương
            when (size) {
                1 -> result = result and 0xFF
                2 -> result = result and 0xFFFF
                4 -> result = result and 0xFFFFFFFFL
            }
        }

        return result.toInt()
    }

    companion object {
        private val BIG5: Charset = Charset.forName("Big5")
        private val UTF16LE: Charset = Charset.forName("UTF-16LE")
    }
}

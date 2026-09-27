package tw.tsm.protocol

import java.nio.charset.Charset

/**
 * Trình đọc gói tin phiên bản game mobile (Little Endian)
 *
 * Đối chiếu với ReadNumber / ReadString của ByteBuffer.lua
 * Tất cả các giá trị số đều là Little Endian
 */
class PacketReader(private val data: ByteArray) {
    /** Vị trí đọc hiện tại */
    var position: Int = 0
        private set

    /** Số byte còn lại có thể đọc */
    val remaining: Int get() = data.size - position

    /** Còn dữ liệu để đọc hay không */
    val isReadable: Boolean get() = remaining > 0

    // ── Giá trị số không dấu (Unsigned) ──

    /** Đọc số nguyên không dấu 1 byte (0-255) */
    fun readUByte(): Int {
        check(remaining >= 1) { "Đọc UByte thất bại: còn lại $remaining bytes" }
        return data[position++].toInt() and 0xFF
    }

    /** Đọc số nguyên không dấu 2 bytes (LE) */
    fun readUShortLE(): Int {
        check(remaining >= 2) { "Đọc UShort thất bại: còn lại $remaining bytes" }
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    /** Đọc số nguyên không dấu 4 bytes (LE), trả về Long để tránh tràn số (overflow) */
    fun readUIntLE(): Long {
        check(remaining >= 4) { "Đọc UInt thất bại: còn lại $remaining bytes" }
        val b0 = data[position++].toLong() and 0xFF
        val b1 = data[position++].toLong() and 0xFF
        val b2 = data[position++].toLong() and 0xFF
        val b3 = data[position++].toLong() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** Đọc số nguyên không dấu 8 bytes (LE) */
    fun readULongLE(): Long {
        check(remaining >= 8) { "Đọc ULong thất bại: còn lại $remaining bytes" }
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((data[position++].toLong() and 0xFF) shl (i * 8))
        }
        return result
    }

    // ── Giá trị số có dấu (Signed) ──

    /** Đọc số nguyên có dấu 1 byte (-128~127) */
    fun readByte(): Int {
        check(remaining >= 1) { "Đọc Byte thất bại: còn lại $remaining bytes" }
        return data[position++].toInt()
    }

    /** Đọc số nguyên có dấu 2 bytes (LE) */
    fun readShortLE(): Int {
        val unsigned = readUShortLE()
        return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
    }

    /** Đọc số nguyên có dấu 4 bytes (LE) */
    fun readIntLE(): Int {
        check(remaining >= 4) { "Đọc Int thất bại: còn lại $remaining bytes" }
        val b0 = data[position++].toInt() and 0xFF
        val b1 = data[position++].toInt() and 0xFF
        val b2 = data[position++].toInt() and 0xFF
        val b3 = data[position++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    /** Đọc Double 8 bytes (LE) */
    fun readDoubleLE(): Double {
        val bits = readULongLE()
        return Double.fromBits(bits)
    }

    // ── Boolean ──

    /** Đọc giá trị Boolean 1 byte */
    fun readBoolean(): Boolean = readUByte() == 1

    // ── Chuỗi ký tự (String) ──

    /** Đọc chuỗi Big5 độ dài cố định (Bao gồm fallback UTF-8) */
    fun readBig5(length: Int): String {
        check(remaining >= length) { "Đọc Big5($length) thất bại: còn lại $remaining bytes" }
        val bytes = data.copyOfRange(position, position + length)
        position += length
        return decodeBig5OrUtf8(bytes).trimEnd('\u0000')
    }

    /** Đọc 1 byte tiền tố độ dài + chuỗi Big5 */
    fun readBig5WithByteLen(): String {
        val len = readUByte()
        return if (len > 0) readBig5(len) else ""
    }

    /** Đọc 1 byte tiền tố độ dài + chuỗi UTF-8. */
    fun readUtf8WithByteLen(): String {
        val len = readUByte()
        if (len == 0) return ""
        check(remaining >= len) { "Đọc UTF-8($len) thất bại: còn lại $remaining bytes" }
        val bytes = data.copyOfRange(position, position + len)
        position += len
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()) 3 else 0
        return String(bytes, offset, bytes.size - offset, UTF8).trimEnd('\u0000')
    }

    /** Đọc 2 bytes tiền tố độ dài (LE) + chuỗi Big5 */
    fun readBig5WithShortLen(): String {
        val len = readUShortLE()
        return if (len > 0) readBig5(len) else ""
    }

    /** Đọc chuỗi UTF-16LE độ dài cố định */
    fun readUnicode(length: Int): String {
        check(remaining >= length) { "Đọc Unicode($length) thất bại: còn lại $remaining bytes" }
        val bytes = data.copyOfRange(position, position + length)
        position += length
        return String(bytes, UTF16LE).trimEnd('\u0000')
    }

    /** Đọc 1 byte tiền tố độ dài + chuỗi UTF-16LE (Dùng cho tin nhắn chat) */
    fun readUnicodeWithByteLen(): String {
        val len = readUByte()
        if (len <= 0) return ""
        check(len % 2 == 0) { "Độ dài UTF-16LE phải là số chẵn: len=$len" }
        return readUnicode(len)
    }

    // ── Dữ liệu thô (Raw data) ──

    /** Đọc mảng byte thô với độ dài chỉ định */
    fun readBytes(length: Int): ByteArray {
        check(remaining >= length) { "Đọc bytes($length) thất bại: còn lại $remaining bytes" }
        val result = data.copyOfRange(position, position + length)
        position += length
        return result
    }

    /** Bỏ qua độ dài chỉ định */
    fun skip(length: Int) {
        check(remaining >= length) { "Bỏ qua $length bytes thất bại: còn lại $remaining bytes" }
        position += length
    }

    /**
     * Thử giải mã bằng Big5, nếu kết quả chứa ký tự thay thế thì chuyển sang dùng UTF-8.
     *
     * Dùng để tương thích với tên tiếng Anh được mã hóa UTF-8 của Client (Big5 không thể giải mã chính xác các ký tự không phải ASCII).
     */
    private fun decodeBig5OrUtf8(bytes: ByteArray): String {
        val big5Result = String(bytes, BIG5)
        // Giải mã Big5 thất bại sẽ tạo ra U+FFFD (Ký tự thay thế), lúc này chuyển sang dùng UTF-8
        if (big5Result.contains('\uFFFD')) {
            val utf8Result = String(bytes, UTF8)
            if (!utf8Result.contains('\uFFFD')) {
                return utf8Result
            }
        }
        return big5Result
    }

    companion object {
        val BIG5: Charset = GameCharsets.BIG5
        val UTF16LE: Charset = GameCharsets.UTF16LE
        val UTF8: Charset = GameCharsets.UTF8
    }
}
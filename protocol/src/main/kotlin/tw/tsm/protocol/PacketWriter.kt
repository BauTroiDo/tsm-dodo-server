package tw.tsm.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Bộ ghi gói tin bản mobile (little endian)
 *
 * Đối chiếu WriteNumber / WriteString của ByteBuffer.lua
 * Mọi giá trị số đều là Little Endian
 */
class PacketWriter(initialCapacity: Int = 64) {
    private val buffer = ByteArrayOutputStream(initialCapacity)

    /** Số byte đã ghi tính đến hiện tại*/
    val size: Int get() = buffer.size()

    /** Lấy kết quả cuối cùng*/
    fun toByteArray(): ByteArray = buffer.toByteArray()

    // ── Ghi giá trị số ──

    /** Ghi 1 byte*/
    fun writeByte(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        return this
    }

    /** Ghi 2 bytes (LE)*/
    fun writeShortLE(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        return this
    }

    /** Ghi 4 bytes (LE)*/
    fun writeIntLE(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        buffer.write((value ushr 16) and 0xFF)
        buffer.write((value ushr 24) and 0xFF)
        return this
    }

    /** Ghi 4 bytes unsigned (LE)*/
    fun writeUIntLE(value: Long): PacketWriter {
        buffer.write((value and 0xFF).toInt())
        buffer.write(((value ushr 8) and 0xFF).toInt())
        buffer.write(((value ushr 16) and 0xFF).toInt())
        buffer.write(((value ushr 24) and 0xFF).toInt())
        return this
    }

    /** Ghi 8 bytes (LE)*/
    fun writeLongLE(value: Long): PacketWriter {
        for (i in 0 until 8) {
            buffer.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
        return this
    }

    /** Ghi 8 bytes Double (LE)*/
    fun writeDoubleLE(value: Double): PacketWriter {
        writeLongLE(value.toBits())
        return this
    }

    // ── Boolean ──

    /** Ghi giá trị boolean (1 byte)*/
    fun writeBoolean(value: Boolean): PacketWriter {
        buffer.write(if (value) 1 else 0)
        return this
    }

    // ── Chuỗi ──

    /** Ghi chuỗi Big5 độ dài cố định (thiếu thì bù 0)*/
    fun writeBig5(value: String, length: Int): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        val actual = minOf(bytes.size, length)
        buffer.write(bytes, 0, actual)
        repeat(length - actual) { buffer.write(0) }
        return this
    }

    /** Ghi tiền tố độ dài 1 byte + chuỗi Big5*/
    fun writeBig5WithByteLen(value: String): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** Ghi tiền tố độ dài 2 bytes + chuỗi Big5 (LE)*/
    fun writeBig5WithShortLen(value: String): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        writeShortLE(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** Ghi tiền tố độ dài 1 byte + chuỗi UTF-8 (tự thêm BOM UTF-8 để tương thích ByteBuffer.lua phía mobile)*/
    fun writeUtf8WithByteLen(value: String): PacketWriter {
        val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
        val bytes = clean.toByteArray(UTF8)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** Ghi tiền tố độ dài 2 bytes + chuỗi UTF-8 (LE, tự thêm BOM UTF-8)*/
    fun writeUtf8WithShortLen(value: String): PacketWriter {
        val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
        val bytes = clean.toByteArray(UTF8)
        writeShortLE(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** Ghi tiền tố độ dài 1 byte + chuỗi UTF-16LE (dùng cho tin chat)*/
    fun writeUnicodeWithByteLen(value: String): PacketWriter {
        val bytes = value.toByteArray(UTF16LE)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    // ── Dữ liệu gốc ──

    /** Ghi các byte gốc*/
    fun writeBytes(data: ByteArray): PacketWriter {
        buffer.write(data)
        return this
    }

    /**
     * Thử mã hoá bằng Big5, nếu thất bại (chứa ký tự ngoài Big5) thì chuyển sang UTF-8 và thêm BOM UTF-8.
     *
     * Dùng để tương thích tên tiếng Anh và ký tự đa ngữ (như tiếng Việt có dấu thanh).
     * ByteBuffer.lua:ReadString phía mobile sẽ dựa vào 3 bytes đầu (0xEF, 0xBB, 0xBF)
     * Xác định có phải UTF-8 không, nếu không có BOM sẽ cưỡng chế dùng Big5 giải mã khiến tiếng Việt bị loạn mã.
     */
    private fun encodeBig5OrUtf8(value: String): ByteArray {
        return try {
            val big5Bytes = value.toByteArray(BIG5)
            // Kiểm tra có ký tự thay thế hay không (nghĩa là Big5 không mã hoá được)
            val decoded = String(big5Bytes, BIG5)
            if (decoded == value) {
                big5Bytes
            } else {
                val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
                clean.toByteArray(UTF8)
            }
        } catch (_: Exception) {
            val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
            clean.toByteArray(UTF8)
        }
    }

    companion object {
        val BIG5: Charset = GameCharsets.BIG5
        val UTF16LE: Charset = GameCharsets.UTF16LE
        val UTF8: Charset = GameCharsets.UTF8
    }
}

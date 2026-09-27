package tw.tsm.protocol

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * 手遊版封包寫入器（小端序）
 *
 * 對照 ByteBuffer.lua 的 WriteNumber / WriteString
 * 所有數值皆為 Little Endian
 */
class PacketWriter(initialCapacity: Int = 64) {
    private val buffer = ByteArrayOutputStream(initialCapacity)

    /** 目前已寫入的位元組數 */
    val size: Int get() = buffer.size()

    /** 取得最終結果 */
    fun toByteArray(): ByteArray = buffer.toByteArray()

    // ── 數值寫入 ──

    /** 寫入 1 byte */
    fun writeByte(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        return this
    }

    /** 寫入 2 bytes（LE） */
    fun writeShortLE(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        return this
    }

    /** 寫入 4 bytes（LE） */
    fun writeIntLE(value: Int): PacketWriter {
        buffer.write(value and 0xFF)
        buffer.write((value ushr 8) and 0xFF)
        buffer.write((value ushr 16) and 0xFF)
        buffer.write((value ushr 24) and 0xFF)
        return this
    }

    /** 寫入 4 bytes 無號（LE） */
    fun writeUIntLE(value: Long): PacketWriter {
        buffer.write((value and 0xFF).toInt())
        buffer.write(((value ushr 8) and 0xFF).toInt())
        buffer.write(((value ushr 16) and 0xFF).toInt())
        buffer.write(((value ushr 24) and 0xFF).toInt())
        return this
    }

    /** 寫入 8 bytes（LE） */
    fun writeLongLE(value: Long): PacketWriter {
        for (i in 0 until 8) {
            buffer.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
        return this
    }

    /** 寫入 8 bytes Double（LE） */
    fun writeDoubleLE(value: Double): PacketWriter {
        writeLongLE(value.toBits())
        return this
    }

    // ── 布林 ──

    /** 寫入布林值（1 byte） */
    fun writeBoolean(value: Boolean): PacketWriter {
        buffer.write(if (value) 1 else 0)
        return this
    }

    // ── 字串 ──

    /** 寫入固定長度 Big5 字串（不足補零） */
    fun writeBig5(value: String, length: Int): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        val actual = minOf(bytes.size, length)
        buffer.write(bytes, 0, actual)
        repeat(length - actual) { buffer.write(0) }
        return this
    }

    /** 寫入 1 byte 長度前綴 + Big5 字串 */
    fun writeBig5WithByteLen(value: String): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** 寫入 2 bytes 長度前綴 + Big5 字串（LE） */
    fun writeBig5WithShortLen(value: String): PacketWriter {
        val bytes = encodeBig5OrUtf8(value)
        writeShortLE(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** 寫入 1 byte 長度前綴 + UTF-8 字串（自動補 UTF-8 BOM 以兼容手機端 ByteBuffer.lua） */
    fun writeUtf8WithByteLen(value: String): PacketWriter {
        val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
        val bytes = clean.toByteArray(UTF8)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** 寫入 2 bytes 長度前綴 + UTF-8 字串（LE，自動補 UTF-8 BOM） */
    fun writeUtf8WithShortLen(value: String): PacketWriter {
        val clean = if (value.startsWith("\uFEFF")) value else "\uFEFF$value"
        val bytes = clean.toByteArray(UTF8)
        writeShortLE(bytes.size)
        buffer.write(bytes)
        return this
    }

    /** 寫入 1 byte 長度前綴 + UTF-16LE 字串（聊天訊息用） */
    fun writeUnicodeWithByteLen(value: String): PacketWriter {
        val bytes = value.toByteArray(UTF16LE)
        writeByte(bytes.size)
        buffer.write(bytes)
        return this
    }

    // ── 原始資料 ──

    /** 寫入原始位元組 */
    fun writeBytes(data: ByteArray): PacketWriter {
        buffer.write(data)
        return this
    }

    /**
     * 嘗試用 Big5 編碼，若失敗（含非 Big5 字元）則改用 UTF-8 並加上 UTF-8 BOM。
     *
     * 用於兼容英文名稱及多語系字元（如越南文帶調符號）。
     * 手機端 ByteBuffer.lua:ReadString 會根據前 3 bytes (0xEF, 0xBB, 0xBF)
     * 判斷是否為 UTF-8，若無 BOM 則會強制使用 Big5 解碼導致越南文變亂碼。
     */
    private fun encodeBig5OrUtf8(value: String): ByteArray {
        return try {
            val big5Bytes = value.toByteArray(BIG5)
            // 檢查是否有替換字元（表示 Big5 無法編碼）
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

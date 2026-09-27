package tw.tsm.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * .dat 二進位讀取器
 *
 * 1:1 對照手遊客戶端 `DatReader.lua`。
 * 支援 XOR 解密（PC 版 .dat）和無加密直讀（手遊版 `_C.dat`）。
 *
 * @param data 檔案完整內容
 * @param numberOffset 數值偏移量（解密用）
 * @param xor1 1-byte XOR 金鑰
 * @param xor2 2-byte XOR 金鑰
 * @param xor4 4-byte XOR 金鑰
 */
class DatReader(
    private val data: ByteArray,
    private val numberOffset: Int = 0,
    private val xor1: Int = 0,
    private val xor2: Int = 0,
    private val xor4: Int = 0
) {
    private var position: Int = 0

    /** 是否還有資料可讀 */
    fun canRead(): Boolean = position < data.size

    /** 跳到指定位置 */
    fun seek(pos: Int) {
        position = pos.coerceIn(0, data.size)
    }

    /** 目前讀取位置 */
    fun position(): Int = position

    // ── 數值讀取（對照 DatReader:ReadNumber） ──

    /**
     * 讀取無號 1 位元組
     *
     * 對照 Lua `ReadByte(computeXOR, computeOffset)`
     */
    fun readByte(): Int {
        return readNumber(1, signed = false)
    }

    /**
     * 讀取有號 1 位元組
     *
     * 對照 Lua `ReadSByte(computeXOR, computeOffset)`
     */
    fun readSByte(): Int {
        return readNumber(1, signed = true)
    }

    /**
     * 讀取無號 2 位元組（小端序）
     *
     * 對照 Lua `ReadUInt16(computeXOR, computeOffset)`
     */
    fun readUInt16(): Int {
        return readNumber(2, signed = false)
    }

    /**
     * 讀取有號 2 位元組（小端序）
     *
     * 對照 Lua `ReadInt16(computeXOR, computeOffset)`
     */
    fun readInt16(): Int {
        return readNumber(2, signed = true)
    }

    /**
     * 讀取無號 4 位元組（小端序）
     *
     * 對照 Lua `ReadUInt32(computeXOR, computeOffset)`
     */
    fun readUInt32(): Long {
        return readNumber(4, signed = false).toLong() and 0xFFFFFFFFL
    }

    /**
     * 讀取有號 4 位元組（小端序）
     *
     * 對照 Lua `ReadInt32(computeXOR, computeOffset)`
     */
    fun readInt32(): Int {
        return readNumber(4, signed = true)
    }

    /**
     * 讀取 IEEE 754 雙精度浮點數（8 位元組，小端序）
     *
     * 對照 Lua `ReadDouble()`
     */
    fun readDouble(): Double {
        if (position + 8 > data.size) return 0.0

        val buf = ByteBuffer.wrap(data, position, 8).order(ByteOrder.LITTLE_ENDIAN)
        position += 8
        return buf.double
    }

    /**
     * 讀取布林值（1 位元組 == 1）
     *
     * 對照 Lua `ReadBoolean(computeXOR, computeOffset)`
     */
    fun readBoolean(): Boolean {
        return readByte() == 1
    }

    // ── 位元組/字串讀取 ──

    /**
     * 讀取原始位元組
     *
     * 對照 Lua `ReadBytes(size)`
     */
    fun readBytes(size: Int): ByteArray {
        if (position + size > data.size) return ByteArray(0)
        val result = data.copyOfRange(position, position + size)
        position += size
        return result
    }

    /**
     * 讀取 PC 版字串（1B 長度 + 反轉 bytes + Big5）
     *
     * 對照 Lua `ReadString(size, computeRevert, xor, offset)`
     * 格式：[count: 1B] [padding...] [反轉的 Big5 字串]
     * 總共消耗 1 + size 個 bytes
     */
    fun readString(size: Int): String {
        if (position + 1 + size > data.size) return ""

        val count = data[position].toInt() and 0xFF
        position += 1

        val result = if (count > 0) {
            // 反轉讀取：從 position + size - count 開始讀 count 個 bytes，再反轉
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
     * 讀取固定長度 ASCII 字串（無加密、無反轉）
     *
     * 格式：[count: 1B] [data: size bytes]
     * 總共消耗 1 + size 個 bytes。用於 Ground.mmg 索引等無加密檔案。
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
     * 讀取手遊版 Unicode 字串（2B LE 長度前綴 + UTF-16LE bytes）
     *
     * 對照 Lua `LuaHelper.BytesToString(encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()))`
     */
    fun readUnicodeString(): String {
        val byteLen = readUInt16()
        if (byteLen <= 0) return ""
        val raw = readBytes(byteLen)
        return String(raw, UTF16LE)
    }

    // ── 內部實作 ──

    /**
     * 通用數值讀取（對照 Lua `ReadNumber`）
     *
     * 小端序組裝 → XOR 解密 → 減偏移 → 有號轉換
     */
    private fun readNumber(size: Int, signed: Boolean): Int {
        if (position + size > data.size) return 0

        // 小端序組裝
        var result = 0L
        for (i in 0 until size) {
            result = result or ((data[position + i].toLong() and 0xFF) shl (i * 8))
        }
        position += size

        // XOR 解密
        when (size) {
            1 -> if (xor1 != 0) result = result xor xor1.toLong()
            2 -> if (xor2 != 0) result = result xor xor2.toLong()
            4 -> if (xor4 != 0) result = result xor xor4.toLong()
        }

        // 偏移
        if (numberOffset != 0) {
            result -= numberOffset
        }

        // 有號轉換
        if (signed) {
            when (size) {
                1 -> result = result.toByte().toLong()
                2 -> result = result.toShort().toLong()
                4 -> result = result.toInt().toLong()
            }
        } else {
            // 無號：確保正值
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

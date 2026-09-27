package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.BlissBagItem

/**
 * 福袋載入器 — 對應 `DataManager.OnLoadBlissBagData`
 *
 * 來源：`BlissBag_C.dat`
 * 格式：count(Int32) + count 筆 BlissBagData.New(reader)
 *
 * 每筆格式：
 * - id: UInt16       ← 福袋物品 ID（key，同 id 歸為同一個福袋）
 * - itemId: UInt16   ← 獎勵物品 ID
 * - count: UInt32    ← 獎勵數量
 * - pr: UInt32       ← 機率權重
 * - kind: Byte       ← 種類分組（開袋時按 kind 各抽一個）
 *
 * Key = 福袋物品 ID → BlissBagDef（含該 id 下所有獎勵項目）
 * kindCount = 同 id 中最大的 kind 值
 */
object BlissBagDatLoader {

    /**
     * 載入福袋資料
     *
     * @return 福袋物品 ID → BlissBagDef 映射
     */
    fun load(data: ByteArray): Map<Int, BlissBagDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()

        // 暫存：按 id 分組
        val grouped = LinkedHashMap<Int, MutableList<BlissBagItem>>()

        for (i in 0 until count) {
            val id = reader.readUInt16()       // 福袋物品 ID
            val itemId = reader.readUInt16()   // 獎勵物品 ID
            val itemCount = reader.readUInt32().toInt() // 獎勵數量
            val pr = reader.readUInt32().toInt()       // 機率權重
            val kind = reader.readByte()       // 種類分組

            val item = BlissBagItem(
                itemId = itemId,
                count = itemCount,
                pr = pr,
                kind = kind
            )
            grouped.getOrPut(id) { mutableListOf() }.add(item)
        }

        // 轉換為 BlissBagDef
        val result = LinkedHashMap<Int, BlissBagDef>(grouped.size)
        for ((bagItemId, items) in grouped) {
            val kindCount = items.maxOfOrNull { it.kind } ?: 1
            result[bagItemId] = BlissBagDef(
                bagItemId = bagItemId,
                kindCount = kindCount,
                items = items
            )
        }

        return result
    }
}

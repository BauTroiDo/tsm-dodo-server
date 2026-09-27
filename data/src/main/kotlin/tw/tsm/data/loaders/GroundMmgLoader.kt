package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader

private val logger = KotlinLogging.logger {}

/**
 * Ground.mmg 地圖地質資料載入器 — 對應 `DataManager.OnLoadMapData`
 *
 * 來源：`CompreseData/Ground.mmg`（無加密，keys 全 0）
 * 提取每張地圖的 `geolBaseAtt`（地質基礎屬性），用於戰鬥背景材質。
 *
 * 索引格式（檔尾）：
 * - 最後 2 bytes = count (uint16)
 * - 往前 count × 29 bytes 的索引條目
 * - 每筆：name(1+20 bytes) + position(int32) + size(int32)
 *
 * 地圖 body 格式（MapData.New）：
 * ```
 * width(4) + height(4)
 * + mapPicCount(1) + mapPics[count × 6]
 * + blockWidth(2) + blockHeight(2) + blocks[w × h]
 * + waveCount(2) + waves[count × 6]
 * + elementCount(2) + elements[count × 8]
 * + npcCount(1)
 * + geolBaseAtt(1)  ← 目標值
 * ```
 */
object GroundMmgLoader {

    /** 每筆索引條目大小：name(21) + position(4) + size(4) = 29 */
    private const val INDEX_ENTRY_SIZE = 29

    /** name 欄位固定大小（readFixedAscii 的參數） */
    private const val NAME_FIELD_SIZE = 20

    /**
     * 載入 Ground.mmg，回傳 mapId → geolBaseAtt 映射
     */
    fun load(data: ByteArray): Map<Int, Int> {
        val reader = DatReader(data)
        val fileSize = data.size

        if (fileSize < 2) {
            logger.warn { "Ground.mmg 檔案過小：${fileSize} bytes" }
            return emptyMap()
        }

        // 讀取索引（檔尾）
        reader.seek(fileSize - 2)
        val count = reader.readUInt16()

        val indexStart = fileSize - 2 - count * INDEX_ENTRY_SIZE
        if (indexStart < 0) {
            logger.warn { "Ground.mmg 索引起始位置異常：count=$count indexStart=$indexStart" }
            return emptyMap()
        }

        reader.seek(indexStart)

        // 解析索引條目
        data class MapEntry(val mapId: Int, val position: Int, val size: Int)

        val entries = mutableListOf<MapEntry>()
        for (i in 0 until count) {
            val name = reader.readFixedAscii(NAME_FIELD_SIZE)
            val position = reader.readInt32()
            val size = reader.readInt32()

            // 從 "12001.map" 提取 mapId
            val dotIndex = name.indexOf(".map")
            if (dotIndex > 0) {
                val mapId = name.substring(0, dotIndex).toIntOrNull()
                if (mapId != null && size > 0) {
                    entries.add(MapEntry(mapId, position, size))
                }
            }
        }

        // 逐筆讀取 geolBaseAtt
        val result = LinkedHashMap<Int, Int>(entries.size)
        var errors = 0

        for (entry in entries) {
            try {
                val geolBaseAtt = readGeolBaseAtt(reader, entry.position, entry.size)
                if (geolBaseAtt > 0) {
                    result[entry.mapId] = geolBaseAtt
                }
            } catch (e: Exception) {
                errors++
                if (errors <= 5) {
                    logger.debug { "Ground.mmg mapId=${entry.mapId} 解析失敗：${e.message}" }
                }
            }
        }

        if (errors > 0) {
            logger.debug { "Ground.mmg 共 $errors 筆地圖解析跳過" }
        }

        return result
    }

    /**
     * 從地圖 body 中提取 geolBaseAtt
     *
     * 按 MapData.New 的讀取順序跳過各變長段，
     * 到達 npcCount 之後的 geolBaseAtt 欄位。
     */
    private fun readGeolBaseAtt(reader: DatReader, bodyPosition: Int, bodySize: Int): Int {
        reader.seek(bodyPosition)

        // width(4) + height(4) = 8
        reader.readInt32() // width
        reader.readInt32() // height

        // mapPicCount(1) + mapPics[count × 6]
        val mapPicCount = reader.readByte()
        // 每筆 mapPic: picId(2) + posX(2) + posY(2) = 6
        repeat(mapPicCount) {
            reader.readUInt16() // picId
            reader.readUInt16() // posX
            reader.readUInt16() // posY
        }

        // blockWidth(2) + blockHeight(2) + blocks[w × h]
        val blockWidth = reader.readUInt16()
        val blockHeight = reader.readUInt16()
        val blockSize = blockWidth * blockHeight
        if (blockSize > 0) {
            reader.seek(reader.position() + blockSize) // 跳過 blocks 陣列
        }

        // waveCount(2) + waves[count × 6]
        val waveCount = reader.readUInt16()
        // 每筆 wave: blockX(2) + blockY(2) + soundId(1) + dist(1) = 6
        if (waveCount > 0) {
            reader.seek(reader.position() + waveCount * 6)
        }

        // elementCount(2) + elements[count × 8]
        val elementCount = reader.readUInt16()
        // 每筆 element: picId(4) + posX(2) + posY(2) = 8
        if (elementCount > 0) {
            reader.seek(reader.position() + elementCount * 8)
        }

        // npcCount(1)
        reader.readByte()

        // geolBaseAtt(1) ← 目標！
        return reader.readByte()
    }
}

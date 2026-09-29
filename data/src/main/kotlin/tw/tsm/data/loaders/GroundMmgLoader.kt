package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader

private val logger = KotlinLogging.logger {}

/**
 * Loader dữ liệu địa chất bản đồ Ground.mmg — tương ứng `DataManager.OnLoadMapData`
 *
 * Nguồn: `CompreseData/Ground.mmg` (không mã hoá, keys toàn 0)
 * Trích `geolBaseAtt` của từng bản đồ (thuộc tính địa chất cơ sở), dùng cho chất liệu nền chiến đấu.
 *
 * Định dạng chỉ mục (cuối file):
 * - 2 bytes cuối = count (uint16)
 * - Các mục chỉ số count × 29 bytes tính lùi về trước
 * - Mỗi bản ghi: name(1+20 bytes) + position(int32) + size(int32)
 *
 * Định dạng body của bản đồ (MapData.New):
 * ```
 * width(4) + height(4)
 * + mapPicCount(1) + mapPics[count × 6]
 * + blockWidth(2) + blockHeight(2) + blocks[w × h]
 * + waveCount(2) + waves[count × 6]
 * + elementCount(2) + elements[count × 8]
 * + npcCount(1)
 * + geolBaseAtt(1)  ← giá trị đích
 * ```
 */
object GroundMmgLoader {

    /** Kích thước mỗi mục chỉ mục: name(21) + position(4) + size(4) = 29*/
    private const val INDEX_ENTRY_SIZE = 29

    /** Kích thước cố định của trường name (tham số của readFixedAscii)*/
    private const val NAME_FIELD_SIZE = 20

    /**
     * Đọc Ground.mmg, trả về ánh xạ mapId → geolBaseAtt
     */
    fun load(data: ByteArray): Map<Int, Int> {
        val reader = DatReader(data)
        val fileSize = data.size

        if (fileSize < 2) {
            logger.warn { "Ground.mmg quá nhỏ: ${fileSize} bytes" }
            return emptyMap()
        }

        // Đọc chỉ mục (cuối file)
        reader.seek(fileSize - 2)
        val count = reader.readUInt16()

        val indexStart = fileSize - 2 - count * INDEX_ENTRY_SIZE
        if (indexStart < 0) {
            logger.warn { "Ground.mmg vị trí bắt đầu index bất thường: count=$count indexStart=$indexStart" }
            return emptyMap()
        }

        reader.seek(indexStart)

        // Parse các mục chỉ mục
        data class MapEntry(val mapId: Int, val position: Int, val size: Int)

        val entries = mutableListOf<MapEntry>()
        for (i in 0 until count) {
            val name = reader.readFixedAscii(NAME_FIELD_SIZE)
            val position = reader.readInt32()
            val size = reader.readInt32()

            // Trích mapId từ "12001.map"
            val dotIndex = name.indexOf(".map")
            if (dotIndex > 0) {
                val mapId = name.substring(0, dotIndex).toIntOrNull()
                if (mapId != null && size > 0) {
                    entries.add(MapEntry(mapId, position, size))
                }
            }
        }

        // Đọc geolBaseAtt từng bản ghi
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
                    logger.debug { "Ground.mmg mapId=${entry.mapId} parse thất bại: ${e.message}" }
                }
            }
        }

        if (errors > 0) {
            logger.debug { "Ground.mmg bỏ qua $errors bản đồ vì parse lỗi" }
        }

        return result
    }

    /**
     * Trích geolBaseAtt từ body của bản đồ
     *
     * Bỏ qua các đoạn có độ dài biến thiên theo thứ tự đọc của MapData.New,
     * Trường geolBaseAtt sau khi đọc hết npcCount.
     */
    private fun readGeolBaseAtt(reader: DatReader, bodyPosition: Int, bodySize: Int): Int {
        reader.seek(bodyPosition)

        // width(4) + height(4) = 8
        reader.readInt32() // width
        reader.readInt32() // height

        // mapPicCount(1) + mapPics[count × 6]
        val mapPicCount = reader.readByte()
        // Mỗi bản ghi mapPic: picId(2) + posX(2) + posY(2) = 6
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
            reader.seek(reader.position() + blockSize) // Bỏ qua mảng blocks
        }

        // waveCount(2) + waves[count × 6]
        val waveCount = reader.readUInt16()
        // Mỗi bản ghi wave: blockX(2) + blockY(2) + soundId(1) + dist(1) = 6
        if (waveCount > 0) {
            reader.seek(reader.position() + waveCount * 6)
        }

        // elementCount(2) + elements[count × 8]
        val elementCount = reader.readUInt16()
        // Mỗi bản ghi element: picId(4) + posX(2) + posY(2) = 8
        if (elementCount > 0) {
            reader.seek(reader.position() + elementCount * 8)
        }

        // npcCount(1)
        reader.readByte()

        // geolBaseAtt(1) ← đích!
        return reader.readByte()
    }
}

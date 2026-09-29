package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader
import tw.tsm.domain.data.LeaderboardDef

private val logger = KotlinLogging.logger {}

/**
 * Loader LeaderbordInfo_C.dat
 *
 * Đối chiếu `LeaderboardData.New(reader)` của `LeaderboardData.lua` và
 * `DataManager.OnLoadLeaderboardInfo()`。
 *
 * Định dạng (mỗi bản ghi):
 * - id: Byte
 * - mainTagText: UInt32
 * - subTagText: UInt32
 * - scoreText: UInt32
 * - nameText: UInt32
 * - awardId: Byte
 * - scoreFormat: UnicodeString (prefix độ dài 2B LE + UTF-16LE bytes)
 * - stateEnable: Boolean
 * - rankAwards: 3 × UInt16 (flagId)
 * - bitFunction: Byte
 */
object LeaderboardDatLoader {

    /**
     * Đọc định nghĩa bảng xếp hạng
     *
     * @param data dữ liệu nhị phân .dat đã giải mã
     * @return key = ID bảng xếp hạng
     */
    fun load(data: ByteArray): Map<Int, LeaderboardDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = mutableMapOf<Int, LeaderboardDef>()

        for (i in 0 until count) {
            val id = reader.readByte()
            val mainTagText = reader.readUInt32()
            val subTagText = reader.readUInt32()
            val scoreText = reader.readUInt32()
            val nameText = reader.readUInt32()
            val awardId = reader.readByte()
            val scoreFormat = reader.readUnicodeString()
            val stateEnable = reader.readBoolean()
            val rankAwardFlagIds = (0 until 3).map { reader.readUInt16() }
            val bitFunction = reader.readByte()

            result[id] = LeaderboardDef(
                id = id,
                mainTagText = mainTagText,
                subTagText = subTagText,
                scoreText = scoreText,
                nameText = nameText,
                awardId = awardId,
                scoreFormat = scoreFormat,
                stateEnable = stateEnable,
                rankAwardFlagIds = rankAwardFlagIds,
                bitFunction = bitFunction
            )
        }

        logger.info { "Nạp LeaderbordInfo xong: ${result.size} định nghĩa bảng xếp hạng" }
        return result
    }
}

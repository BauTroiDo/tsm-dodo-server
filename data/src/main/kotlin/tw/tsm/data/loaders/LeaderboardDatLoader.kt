package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader
import tw.tsm.domain.data.LeaderboardDef

private val logger = KotlinLogging.logger {}

/**
 * LeaderbordInfo_C.dat 載入器
 *
 * 對照 `LeaderboardData.lua` 的 `LeaderboardData.New(reader)` 和
 * `DataManager.OnLoadLeaderboardInfo()`。
 *
 * 格式（每筆）：
 * - id: Byte
 * - mainTagText: UInt32
 * - subTagText: UInt32
 * - scoreText: UInt32
 * - nameText: UInt32
 * - awardId: Byte
 * - scoreFormat: UnicodeString (2B LE 長度前綴 + UTF-16LE bytes)
 * - stateEnable: Boolean
 * - rankAwards: 3 × UInt16 (flagId)
 * - bitFunction: Byte
 */
object LeaderboardDatLoader {

    /**
     * 載入排行榜定義
     *
     * @param data 解密後的 .dat 二進位資料
     * @return key = 排行榜 ID
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

        logger.info { "LeaderbordInfo 載入完成：${result.size} 筆排行榜定義" }
        return result
    }
}

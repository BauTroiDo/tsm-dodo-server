package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader

private val logger = KotlinLogging.logger {}

/**
 * 教學引導資料載入結果
 *
 * @property guideLastBitFlagId guideId → 最後一步的 bitFlagId（C:005-008 教學完成用）
 * @property guideMarkFlagIds guideId → 第一步的 markFlagId（S:024-005 觸發教學用）
 */
data class TeachInfoResult(
    val guideLastBitFlagId: Map<Int, Int>,
    val guideMarkFlagIds: Map<Int, Int>
)

/**
 * 教學引導資料載入器 — 對應 `DataManager.OnLoadGuideData`
 *
 * 來源：`TeachInfo_C.dat`
 * 格式：count(Int32) + count 筆 GuideData.New(reader)
 *
 * 提取兩組映射：
 * 1. guideId → lastStep.bitFlagId — C:005-008 教學完成時查詢永標
 * 2. guideId → step1.markFlagId — S:024-005 觸發教學 UI 用
 */
object TeachInfoDatLoader {

    /** 步驟暫存結構 */
    private data class StepInfo(val step: Int, val markFlagId: Int, val bitFlagId: Int)

    /**
     * 載入教學引導資料
     *
     * 格式對照 `TutorialData.lua GuideData.New(reader)`：
     * ```
     * id(1B) step(1B) title(4B) picId(2B)
     * descLen(2B) desc(N) style(1B) sizeOffset(2B)
     * tagLen(2B) tag(N) rootLen(2B) root(N) objLen(2B) obj(N)
     * param(4B) markFlagId(2B) bitFlagId(2B) closeUI(1B)
     * ```
     *
     * @return [TeachInfoResult] 包含 bitFlagId 和 markFlagId 兩組映射
     */
    fun load(data: ByteArray): TeachInfoResult {
        val reader = DatReader(data)
        val count = reader.readInt32()

        // 暫存所有 guideId 的步驟資料
        val guideSteps = LinkedHashMap<Int, MutableList<StepInfo>>()

        for (i in 0 until count) {
            val id = reader.readByte()          // 教學 ID（guideId）
            val step = reader.readByte()        // 步驟序號

            // title(4B) + picId(2B)
            reader.readUInt32()                 // title — 跳過
            reader.readUInt16()                 // picId — 跳過

            // focus.description = Unicode string
            reader.readUnicodeString()          // 跳過
            // focus.style(1B) + focus.sizeOffset(2B signed)
            reader.readByte()                   // 跳過
            reader.readInt16()                  // 跳過
            // focus.anchoredTag = Unicode string
            reader.readUnicodeString()          // 跳過
            // focus.anchoredRoot = Unicode string
            reader.readUnicodeString()          // 跳過
            // focus.anchoredObject = Unicode string
            reader.readUnicodeString()          // 跳過
            // focus.anchoredParam(4B)
            reader.readUInt32()                 // 跳過

            val markFlagId = reader.readUInt16()
            val bitFlagId = reader.readUInt16()
            val closeUI = reader.readBoolean()

            guideSteps.getOrPut(id) { mutableListOf() }.add(StepInfo(step, markFlagId, bitFlagId))
        }

        // 提取每個 guideId 最後一步的 bitFlagId
        val bitFlagResult = LinkedHashMap<Int, Int>()
        for ((guideId, steps) in guideSteps) {
            val lastStep = steps.maxByOrNull { it.step }
            if (lastStep != null && lastStep.bitFlagId != 0) {
                bitFlagResult[guideId] = lastStep.bitFlagId
            }
        }

        // 提取每個 guideId 第一步的 markFlagId（客戶端用 step=1 的 markFlagId 觸發教學）
        val markFlagResult = LinkedHashMap<Int, Int>()
        for ((guideId, steps) in guideSteps) {
            val firstStep = steps.minByOrNull { it.step }
            if (firstStep != null && firstStep.markFlagId != 0) {
                markFlagResult[guideId] = firstStep.markFlagId
            }
        }

        logger.debug { "TeachInfo 解析完成：${guideSteps.size} 個教學，${bitFlagResult.size} 個 bitFlagId，${markFlagResult.size} 個 markFlagId" }
        return TeachInfoResult(bitFlagResult, markFlagResult)
    }
}

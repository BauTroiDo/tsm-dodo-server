package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader

private val logger = KotlinLogging.logger {}

/**
 * Kết quả đọc dữ liệu hướng dẫn
 *
 * @property guideLastBitFlagId guideId → bitFlagId của bước cuối (dùng cho C:005-008 kết thúc hướng dẫn)
 * @property guideMarkFlagIds guideId → markFlagId của bước đầu (dùng cho S:024-005 kích hoạt hướng dẫn)
 */
data class TeachInfoResult(
    val guideLastBitFlagId: Map<Int, Int>,
    val guideMarkFlagIds: Map<Int, Int>
)

/**
 * Loader dữ liệu hướng dẫn — tương ứng `DataManager.OnLoadGuideData`
 *
 * Nguồn: `TeachInfo_C.dat`
 * Định dạng: count(Int32) + count bản ghi GuideData.New(reader)
 *
 * Trích hai ánh xạ:
 * 1. guideId → lastStep.bitFlagId — C:005-008 truy vấn cờ vĩnh viễn khi kết thúc hướng dẫn
 * 2. guideId → step1.markFlagId — S:024-005 dùng để kích hoạt UI hướng dẫn
 */
object TeachInfoDatLoader {

    /** Cấu trúc tạm của bước*/
    private data class StepInfo(val step: Int, val markFlagId: Int, val bitFlagId: Int)

    /**
     * Đọc dữ liệu hướng dẫn
     *
     * Định dạng đối chiếu `TutorialData.lua GuideData.New(reader)`:
     * ```
     * id(1B) step(1B) title(4B) picId(2B)
     * descLen(2B) desc(N) style(1B) sizeOffset(2B)
     * tagLen(2B) tag(N) rootLen(2B) root(N) objLen(2B) obj(N)
     * param(4B) markFlagId(2B) bitFlagId(2B) closeUI(1B)
     * ```
     *
     * @return [TeachInfoResult] gồm hai ánh xạ bitFlagId và markFlagId
     */
    fun load(data: ByteArray): TeachInfoResult {
        val reader = DatReader(data)
        val count = reader.readInt32()

        // Tạm lưu dữ liệu bước của tất cả guideId
        val guideSteps = LinkedHashMap<Int, MutableList<StepInfo>>()

        for (i in 0 until count) {
            val id = reader.readByte()          // ID hướng dẫn (guideId)
            val step = reader.readByte()        // Số thứ tự bước

            // title(4B) + picId(2B)
            reader.readUInt32()                 // title — bỏ qua
            reader.readUInt16()                 // picId — bỏ qua

            // focus.description = Unicode string
            reader.readUnicodeString()          // Bỏ qua
            // focus.style(1B) + focus.sizeOffset(2B signed)
            reader.readByte()                   // Bỏ qua
            reader.readInt16()                  // Bỏ qua
            // focus.anchoredTag = Unicode string
            reader.readUnicodeString()          // Bỏ qua
            // focus.anchoredRoot = Unicode string
            reader.readUnicodeString()          // Bỏ qua
            // focus.anchoredObject = Unicode string
            reader.readUnicodeString()          // Bỏ qua
            // focus.anchoredParam(4B)
            reader.readUInt32()                 // Bỏ qua

            val markFlagId = reader.readUInt16()
            val bitFlagId = reader.readUInt16()
            val closeUI = reader.readBoolean()

            guideSteps.getOrPut(id) { mutableListOf() }.add(StepInfo(step, markFlagId, bitFlagId))
        }

        // Trích bitFlagId của bước cuối mỗi guideId
        val bitFlagResult = LinkedHashMap<Int, Int>()
        for ((guideId, steps) in guideSteps) {
            val lastStep = steps.maxByOrNull { it.step }
            if (lastStep != null && lastStep.bitFlagId != 0) {
                bitFlagResult[guideId] = lastStep.bitFlagId
            }
        }

        // Trích markFlagId của bước đầu mỗi guideId (client dùng markFlagId của step=1 để kích hoạt hướng dẫn)
        val markFlagResult = LinkedHashMap<Int, Int>()
        for ((guideId, steps) in guideSteps) {
            val firstStep = steps.minByOrNull { it.step }
            if (firstStep != null && firstStep.markFlagId != 0) {
                markFlagResult[guideId] = firstStep.markFlagId
            }
        }

        logger.debug { "Parse TeachInfo xong: ${guideSteps.size} bước hướng dẫn, ${bitFlagResult.size} bitFlagId, ${markFlagResult.size} markFlagId" }
        return TeachInfoResult(bitFlagResult, markFlagResult)
    }
}

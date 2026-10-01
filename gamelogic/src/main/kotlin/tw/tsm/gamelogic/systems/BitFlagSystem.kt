package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.BitFlagRepository

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống cờ vĩnh viễn (BitFlag) — Tương ứng với BitFlag.lua ở client
 *
 * Quản lý các cờ vĩnh viễn (foreverFlags), dùng cho các trạng thái vĩnh viễn như hoàn thành hướng dẫn (tutorial), đạt mốc thu thập, v.v.
 *
 * Quy tắc đóng gói bit (Tham chiếu CheckFlag/SetFlag trong functions.lua):
 * - `byteIndex = (flagId - 1) / 8` (hệ cơ số 0 / 0-based)
 * - `bitPosition = (flagId - 1) % 8`
 * - `mask = 1 shl bitPosition`
 *
 * @param repo                 Tầng truy cập cơ sở dữ liệu cờ vĩnh viễn
 * @param guideLastBitFlagId   guideId → bitFlagId của bước cuối cùng (TeachInfo_C.dat)
 */
class BitFlagSystem(
    private val repo: BitFlagRepository,
    private val guideLastBitFlagId: Map<Int, Int>
) {

    /**
     * Đóng gói cờ vĩnh viễn thành mảng byte (Dùng cho S:081-002)
     *
     * Tương ứng với định dạng đọc của BitFlag.Init() ở client:
     * gồm count byte, mỗi byte lưu 8 cờ bit.
     *
     * @param flagIds Tập hợp các ID cờ vĩnh viễn đã được thiết lập
     * @return Mảng byte sau khi đóng gói
     */
    fun packFlags(flagIds: Set<Int>): ByteArray {
        if (flagIds.isEmpty()) return ByteArray(0)
        val maxId = flagIds.max()
        val byteCount = (maxId - 1) / 8 + 1
        val bytes = ByteArray(byteCount)
        for (id in flagIds) {
            val idx = (id - 1) / 8
            val bit = (id - 1) % 8
            bytes[idx] = (bytes[idx].toInt() or (1 shl bit)).toByte()
        }
        return bytes
    }

    /**
     * Lấy tất cả các ID cờ vĩnh viễn đã thiết lập của nhân vật
     *
     * @param charId ID nhân vật
     * @return Tập hợp các flagId đã được thiết lập
     */
    suspend fun getSetFlagIds(charId: Long): Set<Int> = repo.getSetFlagIds(charId)

    /**
     * Thiết lập cờ vĩnh viễn
     *
     * @param charId ID nhân vật
     * @param flagId ID cờ vĩnh viễn
     */
    suspend fun setFlag(charId: Long, flagId: Int) {
        repo.setFlag(charId, flagId)
        logger.debug { "Thiết lập cờ vĩnh viễn: charId=$charId flagId=$flagId" }
    }

    /** Xoá một cờ vĩnh viễn. DVT NFT collection dùng thao tác này khi lấy card/mount ra. */
    suspend fun clearFlag(charId: Long, flagId: Int) {
        repo.clearFlag(charId, flagId)
        logger.debug { "Xoá cờ vĩnh viễn: charId=$charId flagId=$flagId" }
    }

    /**
     * Xử lý hoàn thành hướng dẫn (C:005-008)
     *
     * Tra cứu bitFlagId của bước cuối cùng tương ứng với guideId, sau đó lưu vào cơ sở dữ liệu.
     *
     * @param charId  ID nhân vật
     * @param guideId ID hướng dẫn (currentId do client gửi lên)
     * @return bitFlagId tương ứng (null = không tìm thấy ánh xạ)
     */
    suspend fun completeTeach(charId: Long, guideId: Int): Int? {
        val bitFlagId = guideLastBitFlagId[guideId]
        if (bitFlagId == null) {
            logger.warn { "Không tìm thấy ánh xạ hướng dẫn: guideId=$guideId (Không có hướng dẫn này trong TeachInfo_C.dat)" }
            return null
        }

        repo.setFlag(charId, bitFlagId)
        logger.info { "Hoàn thành hướng dẫn: charId=$charId guideId=$guideId bitFlagId=$bitFlagId" }
        return bitFlagId
    }
}
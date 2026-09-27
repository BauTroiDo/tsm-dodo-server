package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.interfaces.repositories.FlyPointRepository
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository

private val logger = KotlinLogging.logger {}

/** Số ô bùa truyền tống ghi nhớ ban đầu (Tương ứng với UITeleport.MaxFlyScroll) */
private const val MAX_FLY_SCROLL = 5

/** Số lần mở rộng bùa truyền tống ghi nhớ tối đa (Tương ứng với UITeleport.MaxFlyScrollExtend) */
private const val MAX_FLY_SCROLL_EXTEND = 5

/**
 * Hệ thống bùa truyền tống ghi nhớ — Quản lý việc đọc và lưu các điểm truyền tống ghi nhớ
 *
 * Tương ứng với thẻ Memory của UITeleport.lua ở client.
 */
class FlyPointSystem(
    private val repo: FlyPointRepository,
    private val uiSellCountRepo: UiSellCountRepository
) {

    /**
     * Tải tất cả các điểm truyền tống ghi nhớ của nhân vật
     *
     * @param charId ID nhân vật
     * @return Map<Chỉ số (hệ cơ số 1 / 1-based), FlyPointRecord>
     */
    suspend fun loadAll(charId: Long): Map<Int, FlyPointRecord> = repo.getAll(charId)

    /**
     * Lấy số ô truyền tống ghi nhớ đã được mở rộng
     *
     * Số lượng mở rộng = Tổng số lần mua tại giao diện tiêu hao (UI) sellId=8 (Tối đa [MAX_FLY_SCROLL_EXTEND]).
     * Tương ứng với trường "Số lượng mở" của gói tin S:023-118.
     */
    suspend fun getExtendCount(charId: Long): Int =
        minOf(uiSellCountRepo.getCount(charId, UISellSystem.SELL_FLY_EXTEND), MAX_FLY_SCROLL_EXTEND)

    /**
     * Ghi nhớ vị trí hiện tại vào chỉ số (index) chỉ định
     *
     * @param charId ID nhân vật
     * @param index Chỉ số điểm truyền tống (hệ cơ số 1 / 1-based)
     * @param sceneId ID cảnh hiện tại
     * @param x Tọa độ X hiện tại
     * @param y Tọa độ Y hiện tại
     * @param extendCount Số lượng mở rộng thêm mà người chơi đã mua
     * @return Trả về true nếu thành công
     */
    suspend fun memorize(charId: Long, index: Int, sceneId: Int, x: Int, y: Int, extendCount: Int): Boolean {
        val maxSlots = MAX_FLY_SCROLL + extendCount
        if (index < 1 || index > maxSlots) {
            logger.warn { "Chỉ số truyền tống ghi nhớ vượt quá giới hạn: charId=$charId index=$index max=$maxSlots" }
            return false
        }

        repo.save(charId, index, sceneId, x, y)
        logger.debug { "Ghi nhớ truyền tống: charId=$charId index=$index → scene=$sceneId ($x, $y)" }
        return true
    }
}
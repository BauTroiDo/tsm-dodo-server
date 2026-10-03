package tw.tsm.gamelogic.systems

import kotlin.math.abs

/**
 * Chiến lược phân luồng resultClass của Eve.
 *
 * resultClass=10 xuất hiện rất nhiều trong Eve.emg, nhưng các mẫu đều là type=0, param=0, value=0, meanNo=0,
 * và EventHandler[0] của client không có nhánh class10. Dựa vào phân bố dữ liệu, xác định đây là marker (dấu mốc) của luồng sự kiện,
 * phía server nên xác định rõ là bỏ qua (no-op) và để luồng tiếp tục tiến hành.
 *
 * pStyle=0 của resultClass=1 trong Eve.emg dùng dấu âm/dương của resultValue để biểu thị việc thêm/bớt vật phẩm,
 * ví dụ trong cùng một sự kiện xuất hiện đồng thời quy trình trao đổi vật phẩm `itemA -1` và `itemB +1`.
 *
 * Tất cả các mẫu resultValue của pStyle=4 thuộc resultClass=2 đều rơi vào phạm vi bitId của Mark_C.dat,
 * do đó được xem là thiết lập trực tiếp cờ nhiệm vụ chỉ định; các mẫu pStyle=5 đều có resultValue bằng 0,
 * và các nhiệm vụ tham số phần lớn là nhiệm vụ hoạt động/phó bản có bitId=0, mang ý nghĩa là xóa bỏ nhiệm vụ nhưng không thiết lập cờ hoàn thành.
 */
enum class EveMissionResultKind {
    ADD_STEP,
    DEC_STEP,
    COMPLETE_MISSION,
    SET_FLAG,
    REMOVE_MISSION
}

data class EveMissionResultOperation(
    val kind: EveMissionResultKind,
    val value: Int = 0
)

enum class EvePlayerResultKind {
    OPEN_STORAGE,
    OPEN_BANK,
    OPEN_NPC_STORE,
    OPEN_INN,
    INN_REST_CONFIRM,
    ADD_MONEY,
    OPEN_MESSAGE_BOARD,
    OPEN_GALAXY_LEADERBOARD,
    OPEN_PLAYER_REINCARNATION,
    OPEN_KILL_POINT_SHOP,
    OPEN_WATER_WAR_ORG_MANAGE,
    OPEN_NPC_SECOND_REINCARNATION,
    SEND_BADO_ERROR_MESSAGE,
    SEND_BADO_START_MESSAGE,
    SEND_CHRISTMAS_EVENT_MESSAGE,
    SEND_ARENA_ERROR_MESSAGE,
    SEND_LANTERN_ERROR_MESSAGE,
    SEND_UNKNOWN_ACTIVITY_MESSAGE
}

data class EvePlayerResultOperation(
    val kind: EvePlayerResultKind
)

enum class EveMoneyResultKind {
    ADD,
    SUBTRACT
}

data class EveMoneyResultOperation(
    val kind: EveMoneyResultKind,
    val amount: Int
)

data class EvePointPurchase(
    val itemId: Int,
    val quantity: Int,
    val price: Int
)

data class EveMoralDelta(
    /** 1 Ngụy, 2 Thục, 3 Ngô, 4 Khăn Vàng, 5 Du Hiệp. */
    val faction: Int,
    val delta: Int
)

object EveResultClassPolicy {
    fun isExplicitNoOp(resultClass: Int, parameterStyle: Int): Boolean =
        resultClass == 10 && parameterStyle in setOf(1, 2)

    /**
     * resultClass=7 pStyle 1..5 normally denotes the repeatable service-NPC flow.
     *
     * Eve.emg also overloads pStyle=1 and pStyle=3 when resultValue != 0:
     * - pStyle=1 + value: bộ chọn teleport arena đấu trận không giới hạn
     * - pStyle=3 + value: premium-currency direct purchase
     *
     * Those value-bearing variants must never be routed through bank/storage/store
     * service handling, otherwise the wrong UI is opened before their own subsystem
     * gets a chance to process them.
     */
    fun isRepeatableServicePlayerOperation(parameterStyle: Int, resultValue: Int): Boolean =
        when (parameterStyle) {
            1, 3 -> resultValue == 0
            2, 4, 5 -> true
            else -> false
        }

    /**
     * DVT direct Point purchase encoded in resultClass=7 / pStyle=3.
     *
     * Các mẫu Eve.emg đã xác minh là sáu nhánh sòng bạc chính trên cảnh 12180:
     * value = quantity * 100000 + itemId, parameter=0, meanNo=Point price.
     * Other pStyle=3 samples use different semantics and must not be decoded here.
     */
    fun directPointPurchase(
        parameterStyle: Int,
        parameter: Int,
        resultValue: Int,
        resultMeanNo: Int
    ): EvePointPurchase? {
        if (parameterStyle != 3 || parameter != 0 || resultValue < 100000 || resultMeanNo <= 0) {
            return null
        }
        val quantity = resultValue / 100000
        val itemId = resultValue % 100000
        if (quantity <= 0 || itemId <= 0) return null
        return EvePointPurchase(itemId = itemId, quantity = quantity, price = resultMeanNo)
    }

    /**
     * DVT Eve resultType=0/resultClass=7/parameter=2 thay đổi Moral.
     * pStyle dùng mapping đảo: 5 Ngụy, 4 Thục, 3 Ngô, 2 Khăn Vàng, 1 Du Hiệp.
     * resultValue là delta có dấu. Shape class7 khác không được diễn giải ở đây.
     */
    fun moralDelta(
        resultType: Int,
        parameter: Int,
        parameterStyle: Int,
        resultValue: Int
    ): EveMoralDelta? {
        if (resultType != 0 || parameter != 2 || parameterStyle !in 1..5 || resultValue == 0) return null
        return EveMoralDelta(faction = 6 - parameterStyle, delta = resultValue)
    }

    fun itemDelta(parameterStyle: Int, resultValue: Int): Int? =
        when (parameterStyle) {
            0 -> resultValue
            1 -> resultValue
            2 -> -abs(resultValue)
            else -> null
        }

    /** Staged dungeon Eve encodes absolute step2..n as 1000+step (Mark kinds4/5).
     * Keep ordinary quest direct-bit semantics separate.
     */
    fun dungeonStepAssignment(markKind: Int, value: Int): Int? =
        if (markKind in 4..5 && value in 1001..1255) value - 1000 else null

    fun missionOperation(parameterStyle: Int, resultValue: Int): EveMissionResultOperation? =
        when (parameterStyle) {
            1 -> EveMissionResultOperation(EveMissionResultKind.ADD_STEP, resultValue)
            2 -> EveMissionResultOperation(EveMissionResultKind.DEC_STEP, abs(resultValue))
            3 -> EveMissionResultOperation(EveMissionResultKind.COMPLETE_MISSION)
            4 -> EveMissionResultOperation(EveMissionResultKind.SET_FLAG, resultValue)
            5 -> EveMissionResultOperation(EveMissionResultKind.REMOVE_MISSION)
            else -> null
        }

    fun moneyOperation(parameterStyle: Int, resultValue: Int): EveMoneyResultOperation? {
        if (resultValue <= 0) return null
        return when (parameterStyle) {
            1 -> EveMoneyResultOperation(EveMoneyResultKind.ADD, resultValue)
            2 -> EveMoneyResultOperation(EveMoneyResultKind.SUBTRACT, resultValue)
            else -> null
        }
    }

    fun playerOperation(resultType: Int, parameterStyle: Int): EvePlayerResultOperation? =
        when {
            parameterStyle == 6 -> EvePlayerResultOperation(EvePlayerResultKind.ADD_MONEY)
            resultType != 7 -> null
            parameterStyle == 1 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_STORAGE)
            parameterStyle == 2 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_BANK)
            parameterStyle == 3 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_NPC_STORE)
            parameterStyle == 4 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_INN)
            parameterStyle == 5 -> EvePlayerResultOperation(EvePlayerResultKind.INN_REST_CONFIRM)
            parameterStyle in setOf(7, 8) -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_MESSAGE_BOARD)
            parameterStyle == 10 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_GALAXY_LEADERBOARD)
            parameterStyle == 12 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_PLAYER_REINCARNATION)
            parameterStyle == 13 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_KILL_POINT_SHOP)
            parameterStyle == 15 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_WATER_WAR_ORG_MANAGE)
            parameterStyle == 21 -> EvePlayerResultOperation(EvePlayerResultKind.OPEN_NPC_SECOND_REINCARNATION)
            parameterStyle == 27 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_BADO_ERROR_MESSAGE)
            parameterStyle == 29 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_BADO_START_MESSAGE)
            parameterStyle == 30 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_CHRISTMAS_EVENT_MESSAGE)
            parameterStyle == 32 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_ARENA_ERROR_MESSAGE)
            parameterStyle == 35 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_LANTERN_ERROR_MESSAGE)
            parameterStyle == 39 -> EvePlayerResultOperation(EvePlayerResultKind.SEND_UNKNOWN_ACTIVITY_MESSAGE)
            else -> null
        }
}
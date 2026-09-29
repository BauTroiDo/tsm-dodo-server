package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.UISellDef
import tw.tsm.domain.data.UISellFormulaDef
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository

private val logger = KotlinLogging.logger {}

/**
 * Kết quả báo giá tiêu phí qua UI (UI Sell) — Biểu diễn phía server của nội dung phản hồi S:084-001
 */
sealed class UiSellQuote {
    /**
     * Không thể sử dụng
     *
     * @property reason Mã nguyên nhân thất bại (Đối chiếu string.GetUiSellFail)
     * @property value Giá trị đính kèm
     */
    data class Fail(val reason: Int, val value: Long = 0) : UiSellQuote()

    /**
     * Tiêu phí tiền tệ
     *
     * @property currencyKind 1=KNB, 2=KNB khóa, 3=Tiền game
     * @property cost Số tiền
     */
    data class Money(val currencyKind: Int, val cost: Long) : UiSellQuote()

    /**
     * Tiêu phí đạo cụ
     *
     * @property itemId ID vật phẩm tiêu hao
     * @property count Số lượng tiêu hao
     */
    data class Item(val itemId: Int, val count: Int) : UiSellQuote()
}

/**
 * Hệ thống tiêu phí giao diện (UI Sell) — Tương ứng Client Logic/UISell.lua (mainKind=84)
 *
 * Xử lý báo giá tiêu phí UI (C:084-001) và bộ đếm số lần mua.
 * Việc trừ tiền thực tế và thực thi hiệu ứng do UISellHandler điều phối các hệ thống hoàn thành.
 *
 * Nguồn chi phí: `UISell_Formula_C.dat` (Bảng chi phí server, Client không đọc).
 *
 * @param uiSellDefs Định nghĩa tiêu phí UI (UISell_C.dat)
 * @param formulaDefs Công thức chi phí (UISell_Formula_C.dat)
 * @param countRepo Lưu trữ cố định số lần mua
 * @param inventorySystem Truy vấn túi đồ (Đường dẫn tiêu phí đạo cụ)
 * @param roleCountSystem Đếm số lượng và phán đoán sức chứa cho loại tiêu phí UI mở rộng ô trống
 */
class UISellSystem(
    private val uiSellDefs: Map<Int, UISellDef>,
    private val formulaDefs: Map<Int, UISellFormulaDef>,
    private val countRepo: UiSellCountRepository,
    private val inventorySystem: InventorySystem,
    private val roleCountSystem: RoleCountSystem
) {

    /**
     * Quy cách tiêu phí UI loại mở rộng ô trống
     *
     * @property roleCountIndex Chỉ số ERoleCount tăng lên sau khi mua
     * @property maxPurchases Số lần mua tối đa (Nguồn xem ở các chú thích trong [EXPANSIONS])
     */
    private data class ExpansionSpec(val roleCountIndex: Int, val maxPurchases: Int)

    /**
     * Ánh xạ sellId → Quy cách mở rộng
     *
     * Số lần mua được tính trực tiếp bằng giá trị RoleCount tương ứng — Đạo cụ thành tựu/sự kiện đi theo Bag_2/Bank_2, v.v.
     * Khác chỉ số, không can thiệp vào chỉ số dành riêng cho việc mua.
     */
    private val expansions = mapOf(
        // Mở rộng tiền trang 5 ô: 50 KNB/lần, tối đa 10 lần (formula kind=14 params[0]=10), 25 → 75 ô
        SELL_BANK_EXPAND to ExpansionSpec(RoleCountSystem.BANK_1, 10),
        // Mở rộng khách sạn 1 ô: 50 KNB/lần, tối đa 10 lần (formula kind=14 params[0]=10), 6 → 16 ô
        SELL_INN_EXPAND to ExpansionSpec(RoleCountSystem.INN, 10),
        // Mở rộng túi đồ 5 ô: 199 KNB/lần, tối đa 2 lần (formula kind=6 bảng giá [199,199]), 50 → 60 ô
        SELL_BAG_EXPAND to ExpansionSpec(RoleCountSystem.BAG_1, 2),
        // Mở rộng bảo khố 5 ô: tối đa 14 lần = MaxStorageGrid(70)/5 (Server lậu mặc định Storage_1=14 đã đầy)
        SELL_STORAGE_EXPAND to ExpansionSpec(RoleCountSystem.STORAGE_1, 14),
        // Kho võ tướng: DVT bắt đầu 5 ô, tối đa 150, mỗi lần +1 ô.
        SELL_WARRIOR_REPOSITORY_EXPAND to ExpansionSpec(
            RoleCountSystem.WARRIOR_REPOSITORY,
            RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS
        )
    )

    /**
     * Truy vấn báo giá tiêu phí UI (Xác thực chung cho C:084-001 / C:084-002)
     *
     * @param charId ID nhân vật
     * @param sellId ID tiêu phí UI (sellId)
     * @return Kết quả báo giá
     */
    suspend fun query(charId: Long, sellId: Int): UiSellQuote {
        if (uiSellDefs[sellId] == null) {
            return UiSellQuote.Fail(REASON_NO_DATA)
        }

        // DVT nút kho tướng gọi UISell.Launch(69), nhưng các bản
        // UISell_Formula_C.dat đi kèm client/server đều không có record 69.
        // TextData gốc 24906 ghi rõ mỗi ô mở rộng tốn 150 nguyên bảo.
        if (sellId == SELL_WARRIOR_REPOSITORY_EXPAND) {
            return queryWarriorRepositoryExpansion(charId)
        }

        val formula = formulaDefs[sellId] ?: return UiSellQuote.Fail(REASON_NO_DATA)

        return when (sellId) {
            SELL_FLY_TELEPORT -> queryFlyTeleport(charId, formula)
            SELL_FLY_EXTEND -> queryFlyExtend(charId, formula)
            SELL_TURN3_UNLOCK_SKILL, SELL_TURN3_CHANGE_ELEMENT -> queryFormulaPrice(formula)
            in expansions -> queryExpansion(charId, sellId, formula)
            // TODO: Các sellId còn lại (Tăng tốc phái đi, Vòng quay may mắn, Đặt lại thuộc tính...) chờ các hệ thống thực thi xong sẽ bổ sung dần
            else -> UiSellQuote.Fail(REASON_NOT_USABLE)
        }
    }

    /**
     * sellId=1/2/3/56 Loại tiêu phí UI mở rộng ô trống
     *
     * Số lần mua = Giá trị RoleCount tương ứng hiện tại; Giá lấy theo mức cố định hoặc bảng giá lũy tiến tùy vào formula kind.
     */
    private fun queryExpansion(charId: Long, sellId: Int, formula: UISellFormulaDef): UiSellQuote {
        val spec = expansions.getValue(sellId)
        val purchased = roleCountSystem.getValue(charId, spec.roleCountIndex)
        if (purchased >= spec.maxPurchases) {
            return UiSellQuote.Fail(REASON_LIMIT_REACHED)
        }
        return UiSellQuote.Money(CURRENCY_GOLD, priceAt(formula, purchased))
    }

    private fun queryWarriorRepositoryExpansion(charId: Long): UiSellQuote {
        val purchased = roleCountSystem.getValue(charId, RoleCountSystem.WARRIOR_REPOSITORY)
        if (purchased >= RoleCountSystem.WARRIOR_REPOSITORY_MAX_EXPANSIONS) {
            return UiSellQuote.Fail(REASON_LIMIT_REACHED)
        }
        return UiSellQuote.Money(CURRENCY_GOLD, WARRIOR_REPOSITORY_SLOT_PRICE)
    }

    /**
     * Lấy giá của lần mua thứ N+1 (N = Số lần đã mua)
     *
     * kind=6 là bảng giá lũy tiến (value=giá lần 1, params=các lần sau); các kind còn lại xem như giá cố định value.
     */
    private fun priceAt(formula: UISellFormulaDef, purchased: Int): Long {
        if (formula.kind != FORMULA_KIND_PRICE_TABLE || purchased == 0) {
            return formula.value
        }
        return formula.params.getOrElse(purchased - 1) { formula.value.toInt() }.toLong()
    }

    /**
     * Lấy chỉ số RoleCount tương ứng của loại mở rộng (Dùng để thực thi hiệu ứng)
     *
     * @return Trả về null nếu không phải tiêu phí UI loại mở rộng
     */
    private fun queryFormulaPrice(formula: UISellFormulaDef): UiSellQuote =
        UiSellQuote.Money(CURRENCY_GOLD, formula.value)

    fun expansionRoleCountIndex(sellId: Int): Int? = expansions[sellId]?.roleCountIndex

    /**
     * sellId=7 Sử dụng dịch chuyển ghi nhớ (Truyền tống)
     *
     * Ưu tiên đạo cụ: Trong túi có Bùa truyền tống ghi nhớ (46474) → Tiêu hao 1 lá;
     * Không có bùa → Tiêu phí KNB (Công thức value=2).
     */
    private fun queryFlyTeleport(charId: Long, formula: UISellFormulaDef): UiSellQuote {
        val hasScroll = inventorySystem.getBag(charId).values
            .any { it.itemId == FLY_SCROLL_ITEM_ID && it.quantity > 0 }
        return if (hasScroll) {
            UiSellQuote.Item(FLY_SCROLL_ITEM_ID, 1)
        } else {
            UiSellQuote.Money(CURRENCY_GOLD, formula.value)
        }
    }

    /**
     * sellId=8 Mở rộng điểm dịch chuyển ghi nhớ
     *
     * Giá lũy tiến: Ô thứ 1 = formula.value, Ô thứ N = formula.params[N-2] (Kiểm tra thực tế mỗi ô 59 KNB).
     * Tối đa 5 ô (UITeleport.MaxFlyScrollExtend).
     */
    private suspend fun queryFlyExtend(charId: Long, formula: UISellFormulaDef): UiSellQuote {
        val purchased = countRepo.getCount(charId, SELL_FLY_EXTEND)
        if (purchased >= MAX_FLY_EXTEND) {
            return UiSellQuote.Fail(REASON_LIMIT_REACHED)
        }
        return UiSellQuote.Money(CURRENCY_GOLD, priceAt(formula, purchased))
    }

    /**
     * Lấy tổng số lần đã mua tiêu phí UI
     */
    suspend fun getPurchaseCount(charId: Long, sellId: Int): Int =
        countRepo.getCount(charId, sellId)

    /**
     * Cộng dồn số lần +1 sau khi mua thành công
     *
     * @return Số lần mới sau khi tăng
     */
    suspend fun incrementPurchaseCount(charId: Long, sellId: Int): Int {
        val newCount = countRepo.increment(charId, sellId)
        logger.debug { "Tăng bộ đếm tiêu phí UI: charId=$charId sellId=$sellId count=$newCount" }
        return newCount
    }

    companion object {
        // ── Các sellId đã được thực thi ──

        /** Tiêu phí UI 1: Mở rộng tiền trang 5 ô (UIBank.OnClick_Unlock → Launch(1)) */
        const val SELL_BANK_EXPAND = 1

        /** Tiêu phí UI 2: Mở rộng khách sạn 1 ô */
        const val SELL_INN_EXPAND = 2

        /** Tiêu phí UI 3: Mở rộng túi đồ 5 ô (UIBag → Launch(3)) */
        const val SELL_BAG_EXPAND = 3

        /** Tiêu phí UI 56: Mở rộng bảo khố 5 ô (UIBank.OnClick_Unlock → Launch(56)) */
        const val SELL_STORAGE_EXPAND = 56

        /** UINpcInn.OnClick_AddWarriorRepositorySpace → UISell.Launch(69). */
        const val SELL_WARRIOR_REPOSITORY_EXPAND = 69

        /** TextData gốc 24906: mỗi ô kho võ tướng cần 150 nguyên bảo. */
        const val WARRIOR_REPOSITORY_SLOT_PRICE = 150L

        /** Tiêu phí UI 7: Sử dụng dịch chuyển ghi nhớ (UITeleport.OnClick_Teleport → Launch(7, _, 1, index)) */
        const val SELL_FLY_TELEPORT = 7

        /** Tiêu phí UI 8: Mở rộng điểm dịch chuyển ghi nhớ (UITeleport giải khóa ô → Launch(8)) */
        const val SELL_FLY_EXTEND = 8

        /** UISlotMachine: nạp xu Thôn Thực (UISlotMachine.OnClick_InsertCoin → Launch(11,...)). */
        const val SELL_SLOT_COIN = 11

        /** UISlotMachine: đổi bàn thưởng (Launch(41)); payment formula kind19 còn UNKNOWN. */
        const val SELL_SLOT_CHANGE = 41

        /** Item tiền Thôn Thực hoan lạc. */
        const val SLOT_COIN_ITEM_ID = 31171

        /** UISlotMachine.lua hardcode floor(Role.GetPoint()/9): 9 real Point / 1 xu. */
        const val SLOT_COIN_POINT_PRICE = 9

        /** Tam chuyển: mở khóa skill Quang/Ám (UISkillTree.UnLockSkill). */
        const val SELL_TURN3_UNLOCK_SKILL = 57

        /** Tam chuyển: đổi Quang <-> Ám sau lần chọn đầu. */
        const val SELL_TURN3_CHANGE_ELEMENT = 58

        /** Công thức kind=6: Bảng giá lũy tiến (value=Giá lần đầu, params=Giá các lần sau) */
        const val FORMULA_KIND_PRICE_TABLE = 6

        /** ID vật phẩm bùa truyền tống ghi nhớ (Item_C.dat: id=46474 kind=38) */
        const val FLY_SCROLL_ITEM_ID = 46474

        /** Số ô mở rộng tối đa của điểm dịch chuyển ghi nhớ (UITeleport.MaxFlyScrollExtend) */
        const val MAX_FLY_EXTEND = 5

        // ── Loại tiền tệ (Mã kết quả S:084-001) ──

        /** KNB */
        const val CURRENCY_GOLD = 1

        // ── Mã nguyên nhân thất bại (Đối chiếu string.GetUiSellFail) ──

        /** 1: Không có dữ liệu tiêu phí UI */
        const val REASON_NO_DATA = 1

        /** 4: Lỗi giá */
        const val REASON_PRICE_ERROR = 4

        /** 7: Trừ vật phẩm thất bại */
        const val REASON_ITEM_DEDUCT_FAIL = 7

        /** 11: Đã đạt giới hạn tối đa */
        const val REASON_LIMIT_REACHED = 11

        /** 12: Không đủ KNB */
        const val REASON_GOLD_NOT_ENOUGH = 12

        /** 15: Không thể sử dụng */
        const val REASON_NOT_USABLE = 15

        /** 17: Cảnh hiện tại không thể dịch chuyển */
        const val REASON_SCENE_FORBIDDEN = 17
    }
}
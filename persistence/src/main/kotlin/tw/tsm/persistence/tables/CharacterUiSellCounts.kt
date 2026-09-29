package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Bảng đếm số lần mua qua UI của nhân vật
 *
 * Ghi lại số lần mua luỹ kế của từng sellId theo mỗi nhân vật,
 * Dùng cho giá tăng bậc (giá lần mua thứ N) và xét giới hạn mua.
 * Ví dụ: sellId=8 mở rộng điểm dịch chuyển ký ức → count chính là số ô đã mở rộng.
 */
object CharacterUiSellCounts : Table("character_ui_sell_counts") {
    /** ID nhân vật*/
    val charId = long("char_id").index()

    /** Mã 介消 (sellId trong UISell_C.dat), mua bán qua bảng*/
    val sellId = integer("sell_id")

    /** Số lần mua luỹ kế*/
    val count = integer("count").default(0)

    override val primaryKey = PrimaryKey(charId, sellId)
}

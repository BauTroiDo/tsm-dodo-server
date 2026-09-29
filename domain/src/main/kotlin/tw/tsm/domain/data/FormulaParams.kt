package tw.tsm.domain.data

/**
 * Tham số công thức — tương ứng kết quả đọc của `Calc.Initialize(reader)` trong `Calculator.lua`
 *
 * Nguồn: `Formula.dat`
 * Thứ tự đọc đối chiếu chính xác dòng 42-75 của Calculator.lua.
 */
data class FormulaParams(
    val w1: Double,
    val w2: Double,
    val w3: Double,
    val w4: Double,
    val w5: Double,
    val w6: Double,
    val w7: Double,
    val w9: Double,
    val w10: Double,
    val w13: Double,
    val w14: Double,
    val w15: Double,
    val w16: Double,
    val w20: Double,
    val w21: Double,
    val w25: Double,
    val w28: Double,
    val w30: Double,
    val w31: Double,
    val w35: Double,
    val w36: Double,
    val extraExp: Int,
    val baseHp: Int,
    val baseSp: Int,
    val w37: Double,
    val w40: Double,
    val w38: Double,
    val w39: Double,
    val w45: Double,
    val w50: Double,
    val w51: Double,
    val attAgiScope: Int,
    val ran: Int
)

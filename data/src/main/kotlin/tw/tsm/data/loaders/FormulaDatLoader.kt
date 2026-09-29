package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.FormulaParams

/**
 * Loader tham số công thức — tương ứng `DataManager.OnLoadFormulaData` → `Calc.Initialize(reader)`
 *
 * Nguồn: `Formula.dat` (không có prefix count, đọc liên tiếp trực tiếp)
 * Thứ tự đọc đối chiếu chính xác dòng 42-75 của Calculator.lua.
 */
object FormulaDatLoader {

    /**
     * Đọc tham số công thức
     */
    fun load(data: ByteArray): FormulaParams {
        val reader = DatReader(data)
        return FormulaParams(
            w1 = reader.readDouble(),
            w2 = reader.readDouble(),
            w3 = reader.readDouble(),
            w4 = reader.readDouble(),
            w5 = reader.readDouble(),
            w6 = reader.readDouble(),
            w7 = reader.readDouble(),
            w9 = reader.readDouble(),
            w10 = reader.readDouble(),
            w13 = reader.readDouble(),
            w14 = reader.readDouble(),
            w15 = reader.readDouble(),
            w16 = reader.readDouble(),
            w20 = reader.readDouble(),
            w21 = reader.readDouble(),
            w25 = reader.readDouble(),
            w28 = reader.readDouble(),
            w30 = reader.readDouble(),
            w31 = reader.readDouble(),
            w35 = reader.readDouble(),
            w36 = reader.readDouble(),
            extraExp = reader.readInt32(),
            baseHp = reader.readUInt16(),
            baseSp = reader.readUInt16(),
            w37 = reader.readDouble(),
            w40 = reader.readDouble(),
            w38 = reader.readDouble(),
            w39 = reader.readDouble(),
            w45 = reader.readDouble(),
            w50 = reader.readDouble(),
            w51 = reader.readDouble(),
            attAgiScope = reader.readUInt16(),
            ran = reader.readByte()
        )
    }
}

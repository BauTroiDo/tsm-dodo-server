package tw.tsm.domain.data

/**
 * 傳送門定義 — 對應 `WarpData.New(reader)`
 *
 * 來源：`Warp_C.dat`
 * 每筆 14 bytes：name(4) + scene(2) + mark(2) + x(4) + y(4) = 16 bytes（含 ReadUInt32）
 */
data class WarpDef(
    /** 名稱 ID（string.Get 對照用） */
    val name: Long,
    /** 傳送目標場景 ID */
    val scene: Int,
    /** 身上標記 */
    val mark: Int,
    /** 目標 X 座標 */
    val x: Int,
    /** 目標 Y 座標 */
    val y: Int
)

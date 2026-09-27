package tw.tsm.domain.data

/**
 * 坐騎定義 — 對應 `MountsData.lua`
 *
 * 來源：`Mounts_C.dat`
 * 格式：npcId(UInt16) + flagId(UInt16) + source(UInt32) + scale(Byte) * 0.01
 */
data class MountDef(
    /** NPC ID（也是坐騎辨識 key） */
    val npcId: Int,
    /** 收集用 BitFlag ID */
    val flagId: Int,
    /** 來源描述字串 ID */
    val source: Long,
    /** 縮放比例（原始值 * 0.01） */
    val scale: Double
)

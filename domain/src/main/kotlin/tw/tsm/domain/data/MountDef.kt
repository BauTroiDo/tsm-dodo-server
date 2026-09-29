package tw.tsm.domain.data

/**
 * Định nghĩa vật cưỡi — tương ứng `MountsData.lua`
 *
 * Nguồn: `Mounts_C.dat`
 * Định dạng: npcId(UInt16) + flagId(UInt16) + source(UInt32) + scale(Byte) * 0.01
 */
data class MountDef(
    /** NPC ID (cũng là key nhận dạng vật cưỡi)*/
    val npcId: Int,
    /** BitFlag ID dùng để thu thập*/
    val flagId: Int,
    /** ID chuỗi mô tả nguồn*/
    val source: Long,
    /** Tỷ lệ thu phóng (giá trị gốc * 0.01)*/
    val scale: Double
)

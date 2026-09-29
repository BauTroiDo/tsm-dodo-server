package tw.tsm.domain.valueobjects

/**
 * Dữ liệu runtime vật cưỡi của nhân vật — tương ứng trạng thái module Mounts.lua
 *
 * Nạp khi đăng nhập/lưu khi mất kết nối. Đối tượng lưu trữ bền vững trong DB.
 */
class MountData(
    /** Cấp độ vật cưỡi (1-based)*/
    var level: Int = 1,
    /** 6 loại điểm thuộc tính (index 1-6, Lua truy cập theo 1-based, không dùng index 0)*/
    val attributePoints: IntArray = IntArray(7),
    /** Trang bị vật cưỡi (fitType → ThingData)*/
    val equips: MutableMap<Int, ThingData> = mutableMapOf(),
    /** ID NPC vật cưỡi đang chọn (0 = chưa chọn)*/
    var currentNpcId: Int = 0,
    /** Có đang cưỡi hay không (không lưu bền vững, khi đăng nhập là false)*/
    var isRiding: Boolean = false
)

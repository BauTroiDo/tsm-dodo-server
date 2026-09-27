package tw.tsm.domain.valueobjects

/**
 * 角色坐騎運行時資料 — 對應 Mounts.lua 的模組狀態
 *
 * 登入載入/斷線保存。DB 持久化對象。
 */
class MountData(
    /** 坐騎等級（1-based） */
    var level: Int = 1,
    /** 6 種屬性點數（index 1-6，Lua 以 1-based 存取，index 0 不用） */
    val attributePoints: IntArray = IntArray(7),
    /** 坐騎裝備（fitType → ThingData） */
    val equips: MutableMap<Int, ThingData> = mutableMapOf(),
    /** 當前選擇的坐騎 NPC ID（0 = 未選擇） */
    var currentNpcId: Int = 0,
    /** 是否正在騎乘中（不持久化，登入時為 false） */
    var isRiding: Boolean = false
)

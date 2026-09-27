package tw.tsm.domain.constants

/**
 * 坐騎系統常數 — 對應 `Mounts.lua`
 */
object MountConstants {
    /** 屬性種類數（atk/int/def/hp/sp，kind 1-5） */
    const val MAX_ATTRIBUTE_KINDS = 5

    /** attributePoints 陣列大小（index 0 不用，1-6） */
    const val ATTRIBUTE_ARRAY_SIZE = 7

    /** 坐騎裝備最大欄位數 */
    const val MAX_EQUIP_SLOTS = 5

    /** EAttribute.MountsLv — 坐騎等級屬性代碼（RoleController.lua L116） */
    const val ATTR_MOUNTS_LV = 227

    /** 騎寵教學任務 ID（MarkManager mission） */
    const val MOUNT_TUTORIAL_MISSION_ID = 10024

    /** 教學預設坐騎 NPC ID（白馬） */
    const val DEFAULT_MOUNT_NPC_ID = 18005
}

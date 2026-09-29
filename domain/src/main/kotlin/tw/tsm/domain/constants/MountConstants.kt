package tw.tsm.domain.constants

/**
 * Hằng số hệ thống vật cưỡi — tương ứng `Mounts.lua`
 */
object MountConstants {
    /** Số loại thuộc tính (atk/int/def/hp/sp, kind 1-5)*/
    const val MAX_ATTRIBUTE_KINDS = 5

    /** Kích thước mảng attributePoints (không dùng index 0, 1-6)*/
    const val ATTRIBUTE_ARRAY_SIZE = 7

    /** Số ô trang bị vật cưỡi tối đa*/
    const val MAX_EQUIP_SLOTS = 5

    /** EAttribute.MountsLv — mã thuộc tính cấp độ vật cưỡi (RoleController.lua L116)*/
    const val ATTR_MOUNTS_LV = 227

    /** ID nhiệm vụ hướng dẫn cưỡi thú (MarkManager mission)*/
    const val MOUNT_TUTORIAL_MISSION_ID = 10024

    /** ID NPC vật cưỡi mặc định khi hướng dẫn (ngựa trắng)*/
    const val DEFAULT_MOUNT_NPC_ID = 18005
}

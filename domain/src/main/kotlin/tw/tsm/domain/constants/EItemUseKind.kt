package tw.tsm.domain.constants

/**
 * Loại hiệu ứng sử dụng vật phẩm — tương ứng `ItemData.lua` EItemUseKind
 *
 * Giá trị trường `specialAbility` của định nghĩa vật phẩm, quyết định hiệu ứng khi dùng vật phẩm.
 * Chỉ liệt kê các loại phía server cần xử lý.
 */
object EItemUseKind {
    const val RETURN_SAVE_POINT = 2         // Về điểm lưu
    const val NO_ENCOUNTER = 4              // Không gặp quái 20 giây
    const val TRANSFORM_DOLL = 5            // Búp bê biến hình (EItemKind.Transform=48)
    const val HP_FULL = 25                  // Hồi đầy thể lực
    const val EXP_EQUIP_150 = 40            // Kinh nghiệm x1.5
    const val GIFT_BOX = 48                 // Quà có thể mở (túi phúc)
    const val EXP_EQUIP_200 = 50            // Kinh nghiệm x2
    const val HP_SP_FULL = 54               // HP + SP đầy
    const val EXP_EQUIP_ARG = 60            // Hệ số phúc thần
    const val NPC_ATTR_PILL = 65            // Đơn năng lực tướng
    const val EXP_PILL = 92                 // Đơn kinh nghiệm
    const val EXP_GOD = 93                  // Đạo cụ phúc thần
    const val SP_FULL = 165                 // Hồi đầy ma lực
    const val NPC_PICTURE = 195              // Võ tướng họa sách/token (spare3 = NPC ID)
    const val BLISS_BAG_VIEW = 223          // Mở rộng xem trước túi phúc
    const val LOCK_BLISS_BAG = 228          // Túi phúc rương báu cần chìa khoá
    const val BLISS_BAG_KEY = 229           // Chìa khoá rương báu
    const val SOUL = 240                    // Vật phẩm Hồn (ĐVT EItemUseKind.Soul)
    const val CAREER_CHANGE = 208           // Phù chuyển nghề
    const val ASTROLABE = 211              // Vật phẩm tăng điểm Thất Tinh Mệnh Bàn
    const val DIRECT_CURRENCY = 216         // ItemUse_216: cộng currency theo attribute[1]
    const val ROLE_COUNT = 218              // Mã RoleCount
    const val BATTLE_ATTR_PILL = 200        // BUFF chiến đấu
    const val MOUNTS = 217                  // Tuyển tập tranh vật cưỡi (spare3 = NPC ID)
    const val GODSKIN_STONE = 246           // Đá nâng cấp Thần Trang/Myth
}

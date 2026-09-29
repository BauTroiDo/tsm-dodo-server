package tw.tsm.domain.constants

/**
 * hằng số loại nhân vật — tương ứng EHuman trong `define.lua`
 *
 * dùng cho trường kind trong dữ liệu nhân vật chiến đấu của S:011-005 / S:011-250.
 * giá trị phải khớp hoàn toàn với RoleController.lua của client Lua.
 */
object EHuman {
    const val NONE = 0                // không
    const val PLAYER = 1              // người chơi là nhân vật chính
    const val PLAYERS = 2             // người chơi khác
    const val MAP_NPC = 3             // NPC bản đồ lớn (không dùng trong chiến đấu)
    const val FOLLOW_NPC = 4          // NPC / tướng đi theo (hiển thị tên trong chiến đấu)
    const val SCENE_ELM = 5           // phần tử cảnh
    const val GUARD_NPC = 6           // NPC đóng trại
    const val MINE_NPC = 7            // NPC mìn
    const val OWNER_NPC = 8           // NPC thuộc quyền sở hữu
    const val DIVIDE = 9              // phân thân
    const val SOLDIER = 11            // lính
    const val MACHINE = 12            // máy móc
    const val CTRL_MACH = 13          // điều khiển máy móc
    const val AUTO_FOLLOW = 14        // tự động đi theo
    const val CTRL_SOLD = 15          // điều khiển lính
    const val FOREIGN_NPC = 16        // NPC ngoại lai
    const val CART_NPC = 17           // NPC xe ngựa
    const val CHAOS_GOD = 18          // thần loạn đả
    const val PET_NPC = 19            // thú cưng nhỏ
    const val QUESTION = 20           // Hỏi đáp chiến đấu
    const val WORLD_BOSS = 21         // World Boss
    const val AUTOMANUAL_PLAYER = 28  // người chơi đấu bán tự động
    const val AUTOMANUAL_NPC = 29     // NPC đấu bán tự động
    const val HOUSE_WARRIOR = 30      // tướng trong nhà nhỏ
    const val BATTLE_ROYALE_NPC = 31  // NPC sinh tồn (battle royale)
    const val DUPLICATE = 255         // nhân vật sao chép (dùng để render trong chiến đấu)

    /** @deprecated chuyển sang dùng MAP_NPC*/
    @Deprecated("Dùng MAP_NPC thay thế", replaceWith = ReplaceWith("MAP_NPC"))
    const val NPC = MAP_NPC
}

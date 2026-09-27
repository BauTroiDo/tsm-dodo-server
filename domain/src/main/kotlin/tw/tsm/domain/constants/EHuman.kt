package tw.tsm.domain.constants

/**
 * 角色種類常數 — 對應 `define.lua` EHuman
 *
 * 用於 S:011-005 / S:011-250 戰鬥角色資料的 kind 欄位。
 * 數值必須與 Lua 客戶端 RoleController.lua 完全一致。
 */
object EHuman {
    const val NONE = 0                // 無
    const val PLAYER = 1              // 主角玩家
    const val PLAYERS = 2             // 其他玩家
    const val MAP_NPC = 3             // 大地圖 NPC（不用於戰鬥）
    const val FOLLOW_NPC = 4          // 跟隨 NPC / 武將（戰鬥中顯示名字）
    const val SCENE_ELM = 5           // 場景元件
    const val GUARD_NPC = 6           // 駐紮 NPC
    const val MINE_NPC = 7            // 地雷 NPC
    const val OWNER_NPC = 8           // 擁有的 NPC
    const val DIVIDE = 9              // 分身
    const val SOLDIER = 11            // 兵
    const val MACHINE = 12            // 械
    const val CTRL_MACH = 13          // 控制器械
    const val AUTO_FOLLOW = 14        // 自動跟隨
    const val CTRL_SOLD = 15          // 控制兵
    const val FOREIGN_NPC = 16        // 外來 NPC
    const val CART_NPC = 17           // 馬車 NPC
    const val CHAOS_GOD = 18          // 亂鬥天神
    const val PET_NPC = 19            // 小寵物
    const val QUESTION = 20           // 戰鬥問答
    const val WORLD_BOSS = 21         // 世界 Boss
    const val AUTOMANUAL_PLAYER = 28  // 半自動對戰玩家
    const val AUTOMANUAL_NPC = 29     // 半自動對戰 NPC
    const val HOUSE_WARRIOR = 30      // 小屋武將
    const val BATTLE_ROYALE_NPC = 31  // 大逃殺 NPC
    const val DUPLICATE = 255         // 複製角色（戰鬥渲染用）

    /** @deprecated 改用 MAP_NPC */
    @Deprecated("改用 MAP_NPC", replaceWith = ReplaceWith("MAP_NPC"))
    const val NPC = MAP_NPC
}

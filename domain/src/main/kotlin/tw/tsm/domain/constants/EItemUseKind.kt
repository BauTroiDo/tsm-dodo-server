package tw.tsm.domain.constants

/**
 * 物品使用效果種類 — 對應 `ItemData.lua` EItemUseKind
 *
 * 物品定義的 `specialAbility` 欄位值，決定使用物品時的效果。
 * 只列出伺服器端需要處理的種類。
 */
object EItemUseKind {
    const val RETURN_SAVE_POINT = 2         // 回儲存點
    const val NO_ENCOUNTER = 4              // 不遇敵 20 秒
    const val HP_FULL = 25                  // 體力全部回復
    const val EXP_EQUIP_150 = 40            // 經驗 1.5 倍
    const val GIFT_BOX = 48                 // 可開啟得禮物（福袋）
    const val EXP_EQUIP_200 = 50            // 經驗 2 倍
    const val HP_SP_FULL = 54               // HP + SP 全滿
    const val EXP_EQUIP_ARG = 60            // 福神倍率
    const val NPC_ATTR_PILL = 65            // 武將能力丹
    const val EXP_PILL = 92                 // 經驗值丹
    const val EXP_GOD = 93                  // 福神道具
    const val SP_FULL = 165                 // 魔力全部回復
    const val BLISS_BAG_VIEW = 223          // 展開預覽福袋
    const val LOCK_BLISS_BAG = 228          // 需鑰匙寶箱福袋
    const val BLISS_BAG_KEY = 229           // 寶箱鑰匙
    const val CAREER_CHANGE = 208           // 職業轉換符
    const val ROLE_COUNT = 218              // RoleCount 代碼
    const val BATTLE_ATTR_PILL = 200        // 戰鬥 BUFF
    const val MOUNTS = 217                  // 坐騎畫冊（spare3 = NPC ID）
}

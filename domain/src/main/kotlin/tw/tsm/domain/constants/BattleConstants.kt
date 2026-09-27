package tw.tsm.domain.constants

/**
 * 戰鬥系統常數 — 對應 `FightManager.lua` / `FightField.lua`
 */
object BattleConstants {
    // ── 棋盤 ──
    /** 最大列數（0-3） */
    const val MAX_COL = 4
    /** 最大行數（0-4） */
    const val MAX_ROW = 5
    /** 玩家方前排列（右側，面朝左） */
    const val PLAYER_FRONT_COL = 2
    /** 玩家方後排列（右側） */
    const val PLAYER_BACK_COL = 3
    /** 敵方前排列（左側，面朝右） */
    const val ENEMY_FRONT_COL = 0
    /** 敵方後排列（左側） */
    const val ENEMY_BACK_COL = 1
    /** 棋盤中心行（MaxChipRow / 2） */
    const val CENTER_ROW = 2

    // ── 倒數計時 ──
    /** 一般戰鬥倒數（秒） */
    const val COUNTDOWN_NORMAL = 20
    /** 團 P 倒數（秒） */
    const val COUNTDOWN_5V5 = 30
    /** 天空擂台倒數（秒） */
    const val COUNTDOWN_CHAOS_GOD = 10

    // ── 世界 Boss ──
    /** 世界 Boss 最大回合數 */
    const val WORLD_BOSS_LIMIT_ROUND = 20

    // ── 普攻技能 ID ──
    /** 肉搏攻擊 */
    const val HAND_FIGHT_SKILL_ID = 10000

    // ── 捕捉技能 ID ──
    /** 手捕 */
    const val CATCH_HAND = 15001
    /** 捕網失敗 */
    const val CATCH_NET_FAIL = 15002
    /** 捕網成功 */
    const val CATCH_NET_SUCCESS = 15003
    /** 使用輔助物品演出（客戶端 UseItem.lua，戰鬥道具用） */
    const val USE_ITEM_SKILL_ID = 19001
    /** 捕網失敗（變體） */
    const val CATCH_NET_FAIL_2 = 15004
    /** 捕網成功（變體） */
    const val CATCH_NET_SUCCESS_2 = 15005

    // ── 動畫時序（毫秒）──
    /** 戰鬥場景載入等待（進場動畫 + 網路延遲） */
    const val SCENE_LOAD_DELAY_MS = 1200L
    /** 回合結束後等待最後一個動畫播完 */
    const val ROUND_END_DELAY_MS = 1200L
    /** 打飛動畫等待 */
    const val FLY_OUT_ANIM_DELAY_MS = 500L

    /** 桃太郎飯糰 25085 的捕捉等級差限制；不是手動捕捉 15002 的 +5 規則。 */
    const val CATCH_LEVEL_DIFF = 40
    /** DVT FightRoleController.DoClick: 15002 只允許目標最多高施放者 5 級。 */
    const val MANUAL_CATCH_LEVEL_DIFF = 5

    /** 捕捉技能 ID 集合 */
    val CATCH_SKILL_IDS = setOf(CATCH_HAND, CATCH_NET_FAIL, CATCH_NET_SUCCESS, CATCH_NET_FAIL_2, CATCH_NET_SUCCESS_2)

    // ── 防禦技能 ID ──
    /** 防禦（FightField.lua: 17001） */
    const val DEFEND_SKILL_ID = 17001
    /** 防禦技能 ID 集合 */
    val DEFEND_SKILL_IDS = setOf(DEFEND_SKILL_ID)

    // ── 逃跑技能 ID ──
    /** 一般逃跑 */
    const val ESCAPE_SKILL_ID = 18001
    /** 逃跑失敗動畫 */
    const val ESCAPE_FAIL_SKILL_ID = 18002
    /** 逃跑技能 ID 集合 */
    val ESCAPE_SKILL_IDS = setOf(ESCAPE_SKILL_ID)

    // ── 打飛（超殺） ──
    /** 打飛倍率：單擊傷害 >= maxHp × 此倍率時觸發打飛 */
    const val FLY_OUT_DAMAGE_RATIO = 1.0
}

/**
 * 戰鬥類型 — 對應 `FightManager.lua` EWar
 */
object EWar {
    const val NONE = 0
    const val EVENT = 1             // 事件戰鬥（NPC 觸發）
    const val PK_PLAYERS = 2        // 玩家 PK
    const val PK_MAP_NPC = 3        // 地圖遇敵
    const val GUEST = 4             // 觀戰
    const val JOIN = 5              // 加入戰鬥
    const val SERVER = 6            // 伺服器觸發
    const val BE_QUESTION = 7       // 問答戰鬥
    const val VS = 8                // 擂台 PK
    const val CITY_PK = 9           // 攻城戰
    const val WORLD_BOSS = 26       // 世界 Boss
    const val DUNGEON = 29          // 副本
    const val ONE_BRING_FOUR = 32   // 一帶四
    const val ORG_BOSS = 33         // 軍團 Boss
}

/**
 * 戰場模式 — 對應 `FightManager.lua` EFFWar_Style
 */
object EWarStyle {
    const val NORMAL = 1            // 一般戰鬥
    const val BEQ = 2               // 戰鬥問答
    const val ARENA_PK = 3          // 擂台 PK
    const val W5TO5 = 4             // 團 P
    const val CITY_PK = 5           // 攻城
    const val LIFE_PK = 11          // 生存戰 PK
    const val WORLD_BOSS = 16       // 世界 Boss
    const val NEW_CHAOS_5V5 = 17    // 新亂鬥
    const val NEW_MIX_5V5 = 26      // 無界混 P
    const val NEW_CHAOS_5V5_100 = 27 // 無界亂鬥 100LV
    const val DUNGEON = 29          // 副本
}

/**
 * 攻擊結果 — 對應 `FightManager.lua` EAttackResult
 */
object EAttackResult {
    const val MISS = 0              // 迴避
    const val HIT = 1               // 命中
    const val THUNDER = 2           // 天雷（暴擊）
    const val HEART_EYE = 3         // 心眼亦敵
}

/**
 * 被擊動作 — 對應 `FightManager.lua` beHitAct
 */
object EBeHitAction {
    const val HIT = 0               // 被擊
    const val BLOCK = 1             // 格擋
    const val DODGE = 2             // 閃避
    const val NONE = 3              // 不變
}

/**
 * 戰鬥行為 — 對應 `FightManager.lua` EFightBeh
 */
object EFightBeh {
    const val NONE = 0
    const val ROTATE = 1            // 轉身
    const val QUAKE = 2             // 震動
    const val DODGE = 6             // 閃躲
    const val FLY_OUT = 7           // 打飛
    const val DEAD = 8              // 死亡
    const val MAGICAL_EYE = 10      // 心眼奕敵
}

/**
 * 技能範圍 — 對應 `SkillData.lua` fightArea
 */
object EFightArea {
    const val SINGLE = 1            // 單體
    const val COL_TWO = 2           // 同列二人
    const val ROW_THREE = 3         // 同行三人
    const val JUMP_THREE = 4        // 跳三人
    const val CROSS_FIVE = 5        // 十字五人
    const val SIX = 6               // 六人（整排）
    const val ROW_FIVE = 7          // 同行五人
    const val ALL = 8               // 全體
}

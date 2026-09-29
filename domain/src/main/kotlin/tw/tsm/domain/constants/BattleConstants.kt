package tw.tsm.domain.constants

/**
 * Hằng số hệ thống chiến đấu — tương ứng `FightManager.lua` / `FightField.lua`
 */
object BattleConstants {
    // ── Bàn cờ ──
    /** Số cột tối đa (0-3)*/
    const val MAX_COL = 4
    /** Số hàng tối đa (0-4)*/
    const val MAX_ROW = 5
    /** Hàng trước phe người chơi (bên phải, quay mặt sang trái)*/
    const val PLAYER_FRONT_COL = 2
    /** Hàng sau phe người chơi (bên phải)*/
    const val PLAYER_BACK_COL = 3
    /** Hàng trước phe địch (bên trái, quay mặt sang phải)*/
    const val ENEMY_FRONT_COL = 0
    /** Hàng sau phe địch (bên trái)*/
    const val ENEMY_BACK_COL = 1
    /** Hàng giữa bàn cờ (MaxChipRow / 2)*/
    const val CENTER_ROW = 2

    // ── Đếm ngược ──
    /** Đếm ngược chiến đấu thường (giây)*/
    const val COUNTDOWN_NORMAL = 20
    /** Đếm ngược PvP tổ đội (giây)*/
    const val COUNTDOWN_5V5 = 30
    /** Đếm ngược đấu trường bầu trời (giây)*/
    const val COUNTDOWN_CHAOS_GOD = 10

    // ── World Boss ──
    /** Số hiệp tối đa của World Boss*/
    const val WORLD_BOSS_LIMIT_ROUND = 20

    // ── ID kỹ năng tấn công thường ──
    /** Tấn công cận chiến*/
    const val HAND_FIGHT_SKILL_ID = 10000

    // ── ID kỹ năng bắt ──
    /** Bắt bằng tay*/
    const val CATCH_HAND = 15001
    /** Bắt bằng lưới thất bại*/
    const val CATCH_NET_FAIL = 15002
    /** Bắt bằng lưới thành công*/
    const val CATCH_NET_SUCCESS = 15003
    /** Hiệu ứng trình diễn khi dùng vật phẩm hỗ trợ (client UseItem.lua, dùng cho vật phẩm chiến đấu)*/
    const val USE_ITEM_SKILL_ID = 19001
    /** Bắt bằng lưới thất bại (biến thể)*/
    const val CATCH_NET_FAIL_2 = 15004
    /** Bắt bằng lưới thành công (biến thể)*/
    const val CATCH_NET_SUCCESS_2 = 15005

    // ── Mốc thời gian hoạt ảnh (ms)──
    /** Đợi tải cảnh chiến đấu (hoạt ảnh vào trận + độ trễ mạng)*/
    const val SCENE_LOAD_DELAY_MS = 1200L
    /** Sau khi kết thúc hiệp, đợi hoạt ảnh cuối cùng phát xong*/
    const val ROUND_END_DELAY_MS = 1200L
    /**
     * Spacing between independent S:050-001 commands.
     * DVT Lua animation scripts commonly use sub-second internal intervals and do not ACK completion.
     * Keep this on the original-server pacing scale; do not derive it from SkillData.spendSecond.
     */
    const val ACTION_COMMAND_PACING_MS = 500L
    /** Đợi hoạt ảnh đánh bay*/
    const val FLY_OUT_ANIM_DELAY_MS = 500L

    /** Giới hạn chênh lệch cấp khi bắt bằng cơm nắm Momotaro 25085; không phải quy tắc +5 của bắt thủ công 15002.*/
    const val CATCH_LEVEL_DIFF = 40
    /** DVT FightRoleController.DoClick: 15002 chỉ cho phép mục tiêu cao hơn người thi triển tối đa 5 cấp.*/
    const val MANUAL_CATCH_LEVEL_DIFF = 5

    /** C→S manual catch command: DVT FightRoleController.DoClick only sends CatchNet=15002. */
    val CATCH_COMMAND_SKILL_IDS = setOf(CATCH_NET_FAIL)

    // ── ID kỹ năng phòng thủ ──
    /** Phòng thủ (FightField.lua: 17001)*/
    const val DEFEND_SKILL_ID = 17001
    /** Tập hợp ID kỹ năng phòng thủ*/
    val DEFEND_SKILL_IDS = setOf(DEFEND_SKILL_ID)

    // ── ID kỹ năng bỏ chạy ──
    /** Bỏ chạy thông thường*/
    const val ESCAPE_SKILL_ID = 18001
    /** Hoạt ảnh bỏ chạy thất bại*/
    const val ESCAPE_FAIL_SKILL_ID = 18002
    /** Tập hợp ID kỹ năng bỏ chạy*/
    val ESCAPE_SKILL_IDS = setOf(ESCAPE_SKILL_ID)

    // ── Đánh bay (super kill) ──
    /** Hệ số đánh bay: sát thương một đòn >= maxHp × hệ số này thì kích hoạt đánh bay*/
    const val FLY_OUT_DAMAGE_RATIO = 1.0
}

/**
 * Loại hình chiến đấu — tương ứng `FightManager.lua` EWar
 */
object EWar {
    const val NONE = 0
    const val EVENT = 1             // Chiến đấu sự kiện (NPC kích hoạt)
    const val PK_PLAYERS = 2        // Người chơi PK
    const val PK_MAP_NPC = 3        // Gặp địch trên bản đồ
    const val GUEST = 4             // Theo dõi trận đấu
    const val JOIN = 5              // Tham chiến
    const val SERVER = 6            // Server kích hoạt
    const val BE_QUESTION = 7       // Chiến đấu hỏi đáp
    const val VS = 8                // PK đấu trường
    const val CITY_PK = 9           // Chiến công thành
    const val WORLD_BOSS = 26       // World Boss
    const val DUNGEON = 29          // Dungeon
    const val ONE_BRING_FOUR = 32   // Một dẫn bốn
    const val ORG_BOSS = 33         // Boss quân đoàn
}

/**
 * Chế độ chiến trường — tương ứng `FightManager.lua` EFFWar_Style
 */
object EWarStyle {
    const val NORMAL = 1            // Chiến đấu thông thường
    const val BEQ = 2               // Hỏi đáp chiến đấu
    const val ARENA_PK = 3          // PK đấu trường
    const val W5TO5 = 4             // PvP tổ đội
    const val CITY_PK = 5           // Công thành
    const val LIFE_PK = 11          // PK sinh tồn
    const val WORLD_BOSS = 16       // World Boss
    const val NEW_CHAOS_5V5 = 17    // Hỗn chiến mới
    const val NEW_MIX_5V5 = 26      // Hỗn PvP không giới hạn
    const val NEW_CHAOS_5V5_100 = 27 // Hỗn chiến không giới hạn 100LV
    const val DUNGEON = 29          // Dungeon
}

/**
 * Kết quả tấn công — tương ứng `FightManager.lua` EAttackResult
 */
object EAttackResult {
    const val MISS = 0              // Né tránh
    const val HIT = 1               // Đánh trúng
    const val THUNDER = 2           // Thiên lôi (chí mạng)
    const val HEART_EYE = 3         // Tâm nhãn diệc địch
}

/**
 * Hành động khi bị đánh — tương ứng `FightManager.lua` beHitAct
 */
object EBeHitAction {
    const val HIT = 0               // Bị đánh
    const val BLOCK = 1             // Chặn đỡ
    const val DODGE = 2             // Lánh
    const val NONE = 3              // Không đổi
}

/**
 * Hành vi chiến đấu — tương ứng `FightManager.lua` EFightBeh
 */
object EFightBeh {
    const val NONE = 0
    const val ROTATE = 1            // Quay người
    const val QUAKE = 2             // Rung
    const val DODGE = 6             // Lánh
    const val FLY_OUT = 7           // Đánh bay
    const val DEAD = 8              // Tử trận
    const val MAGICAL_EYE = 10      // Tâm nhãn diệc địch
}

/**
 * Phạm vi kỹ năng — tương ứng `SkillData.lua` fightArea
 */
object EFightArea {
    const val SINGLE = 1            // Đơn thể
    const val COL_TWO = 2           // Hai kẻ cùng cột
    const val ROW_THREE = 3         // Ba kẻ cùng hàng
    const val JUMP_THREE = 4        // Nhảy ba kẻ
    const val CROSS_FIVE = 5        // Chữ thập 5 kẻ
    const val SIX = 6               // 6 kẻ (cả hàng)
    const val ROW_FIVE = 7          // Năm kẻ cùng hàng
    const val ALL = 8               // Toàn thể
}

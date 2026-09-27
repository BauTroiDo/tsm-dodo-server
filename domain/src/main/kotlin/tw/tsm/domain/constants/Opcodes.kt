package tw.tsm.domain.constants

/**
 * 手遊版主 Opcode 定義
 *
 * 來源：protocal.lua 的 protocolTable[mainKind][subKind] 結構
 * 每個 mainKind 下有多個 subKind，以二級路由分發
 */
object Opcodes {
    /** 連線/斷線通知 */
    const val DISCONNECT = 0

    /** 登入認證系統 */
    const val LOGIN = 1

    /** 聊天系統 */
    const val CHAT = 2

    /** 主角系統（選角/玩家出現/登入天數） */
    const val CHARACTER = 3

    /** 玩家屬性/狀態 */
    const val PLAYER_STATUS = 5

    /** 移動系統 */
    const val MOVE = 6

    /** 地圖/場景 */
    const val SCENE = 7

    /** 戰鬥屬性系統（S→C 屬性修改） */
    const val BATTLE = 8

    /** 戰鬥管理（C→S: PK/參戰/觀戰, S→C: 初始化/結束） */
    const val BATTLE_MANAGE = 11

    /** 戰鬥技能指令（C→S: 出招/道具, S→C: 攻擊結果） */
    const val BATTLE_COMMAND = 50

    /** 戰鬥屬性變化（S→C only） */
    const val BATTLE_ATTR_CHANGE = 51

    /** 戰鬥倒數/回魔（S→C only） */
    const val BATTLE_COUNTDOWN = 52

    /** 戰鬥狀態/特效（S→C only） */
    const val BATTLE_STATUS = 53

    /** 創角系統（手遊版 protocolTable[9]） */
    const val CREATE_CHAR = 9

    /** 心跳包 */
    const val KEEPALIVE = 10

    /** 場景切換完畢（C→S 12-001） */
    const val RELOCATE = 12

    /** 隊伍系統（protocolTable[13]） */
    const val PARTY = 13

    /** 好友系統（protocolTable[14]） */
    const val FRIEND = 14

    /** 跟隨武將管理（protocolTable[15]：新增/放逐/改名/馬車） */
    const val FOLLOW_NPC = 15

    /** GM/戰鬥問答（protocolTable[16]） */
    const val GM_QUIZ = 16

    /** 跟隨武將出戰控制（protocolTable[19]） */
    const val FOLLOW_NPC_DEPLOY = 19

    /** NPC 事件系統（對話/碰撞觸發/傳送門） */
    const val NPC_EVENT = 20

    /** MapNpc 系列（S:022-001~010 NPC 巡邏/移動/隱形/初始化） */
    const val MAP_NPC = 22

    /** NPC 商店系統 */
    const val SHOP = 23

    /** 任務/標記系統（MarkManager） */
    const val MARK = 24

    /** 玩家交易系統（物品/武將/快遞） */
    const val TRADE = 25

    /** 成就系統（protocolTable[82]） */
    const val ACHIEVEMENT = 82

    /** 信件系統（protocolTable[83]） */
    const val MAIL = 83

    /** 排行榜系統（protocolTable[88]） */
    const val LEADERBOARD = 88

    /** 金錢系統（增減/初始化） */
    const val MONEY = 26

    /** NPC 買賣 */
    const val NPC_TRADE = 27

    /** 技能升級（C→S 028-001 玩家, 028-002 武將） */
    const val SKILL_UPGRADE = 28

    /** 錢莊/銀行（mainKind=29 為控制封包、mainKind=30 為物品操作） */
    const val BANK_CONTROL = 29
    /** 錢莊物品操作 */
    const val BANK = 30

    /** 客棧系統（protocolTable[31]） */
    const val INN = 31

    /** 快捷鍵系統（C:040-002 設定, S:040-001 資料, S:040-002 結果） */
    const val HOTKEY = 40

    /** 武將轉生系統（protocolTable[44]） */
    const val NPC_REINCARNATION = 44

    /** 表情/動作系統（C:032-001 表情, C:032-002 動作, C:032-003 停止動作） */
    const val EMOTE = 32

    /** PK 開關設定（C:033-001 PK開關, C:033-002 參戰開關） */
    const val PK_SETTING = 33

    /** 跨服系統 */
    const val CROSS_SERVER = 35

    /** 副本/挑戰 */
    const val CHALLENGE = 36

    /** 任務（主副線） */
    const val MISSION = 47

    /** 坐騎系統（protocolTable[79]） */
    const val MOUNT = 79

    /** 武將強化（C:074-001 發起, C:074-002 確認, C:074-003 取消） */
    const val NPC_UPGRADE = 74

    /** 場景對象 */
    const val SCENE_OBJECT = 63

    /** 機關盒（內掛）系統 */
    const val MACHINE_BOX = 65

    /** 天行異能傳送（protocolTable[68]） */
    const val TELEPORT = 68

    /** 天梯賽季 */
    const val ARENA = 66

    /** 永標系統（BitFlag） */
    const val FOREVER_FLAG = 81

    /** 介面消費系統（C:084-001 查詢介消, C:084-002 使用介消） */
    const val UI_SELL = 84

    /** 玩家計數資料系統（背包格數、錢莊格數等），使用 S:085-001/002 */
    const val ROLE_COUNT_SYSTEM = 85

    /** 派遣系統（C:086-001 查詢, C:086-002 出發, C:086-005 完成） */
    const val DISPATCH = 86

    /** 角色計數資料（背包格數等） */
    const val ROLE_COUNT = 100

    /** 寶庫（與錢莊平行的第二倉庫） */
    const val STORAGE = 102

    /** 武將倉庫（protocolTable[122]） */
    const val WARRIOR_REPO = 122

    /** 精靈系統（DVT UIElf.lua / protocolTable[107]） */
    const val ELF = 107

    /** 除錯/測試 */
    const val DEBUG = 199
}

/** 協定事件碼（Socket 層） */
object ProtocolEvent {
    /** TCP 連線建立 */
    const val CONNECT = 101
    /** 異常斷線 */
    const val EXCEPTION = 102
    /** 正常斷線 */
    const val DISCONNECT = 103
    /** 收到訊息 */
    const val MESSAGE = 104
    /** 網路不可用 */
    const val INTERNET_UNAVAILABLE = 105
}

/** 新角色起始場景（新手村，地圖 1600×1152 px） */
const val DEFAULT_START_MAP: Int = 10801
/** 新角色起始 X 像素座標 */
const val DEFAULT_START_X: Int = 570
/** 新角色起始 Y 像素座標 */
const val DEFAULT_START_Y: Int = 770

/** 客戶端版本號 */
const val CLIENT_VERSION: Int = 258

/** 預設伺服器埠 */
const val DEFAULT_PORT: Int = 6613

/** 心跳間隔（秒） */
const val HEARTBEAT_INTERVAL: Int = 20

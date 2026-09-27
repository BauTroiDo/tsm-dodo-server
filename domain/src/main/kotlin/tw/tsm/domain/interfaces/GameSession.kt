package tw.tsm.domain.interfaces

import tw.tsm.domain.enums.LoginKind
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.repositories.CharacterRecord

/**
 * 連線身份識別 — 帳號、伺服器、登入方式
 */
interface SessionIdentity {
    /** 帳號名稱 */
    var account: String
    /** 登入方式 */
    var loginKind: LoginKind
    /** 帳號資料庫 ID */
    var accountId: Long
    /** 選擇的伺服器 ID */
    var serverId: Int
    /** 資料庫 ID（DBID） */
    var dbId: Int
    /** Game Server ID */
    var gsId: Int
    /** 連線序號（重連用） */
    var connectCode: Long
    /** GM 等級（0=普通玩家, 1+=GM） */
    var gmLevel: Int
}

/**
 * 連線狀態管理
 */
interface SessionStatus {
    /** 目前連線狀態 */
    var state: SessionState
    /** 是否已完成登入序列 */
    var loginFinished: Boolean
}

/**
 * 角色基本資訊
 */
interface SessionCharacter {
    /** 玩家 ID（8 bytes） */
    var playerId: Long
    /** 角色 ID */
    var roleId: Int
    /** 角色名稱 */
    var characterName: String
}

/**
 * 角色在世界中的位置
 */
interface SessionWorld {
    /** 目前所在地圖 ID */
    var mapId: Int
    /** 目前 X 座標 */
    var x: Int
    /** 目前 Y 座標 */
    var y: Int
    /** 目前朝向（0-7） */
    var direction: Int
    /** 上次位置儲存時間戳（防抖用，毫秒） */
    var lastPositionSave: Long
    /** 最後一次 Door 傳送時間戳（MeetDoor 冷卻用，毫秒） */
    var lastDoorWarpTime: Long
    /** 最後一次 Door 傳送的來源地圖（MeetDoor 冷卻只在同地圖生效） */
    var lastDoorWarpMapId: Int
}

/**
 * 角色外觀資料 — 用於 S:003-000 玩家出現封包
 */
interface SessionAppearance {
    /** 性別（0=男, 1=女） */
    var sex: Int
    /** 元素屬性 */
    var element: Int
    /** 等級 */
    var level: Int
    /** 轉生次數 */
    var turn: Int
    /** 二/三轉職業 */
    var career: Int
    /** 三轉光暗元素 */
    var turn3Element: Int
    /** 頭型 */
    var hair: Int
    /** 臉圖 */
    var face: Int
    /** 色盤一（帽/髮/膚） */
    var color1: Int
    /** 色盤二（衣/配一/鞋） */
    var color2: Int
    /** 裝備 item ID 列表（供 sendOtherPlayerAppear 外觀用） */
    var equipItemIds: IntArray
    /** 當前穿戴稱號 ID（0 = 未穿戴） */
    var titleId: Int
}

/**
 * 封包發送能力
 */
interface SessionSender {
    /** 發送原始封包資料到客戶端（含 mainKind+subKind+compress header） */
    fun send(data: ByteArray)
    /** 關閉連線 */
    fun close()
}

/**
 * 戰鬥狀態
 */
interface SessionBattle {
    /** 目前所在戰鬥 ID（0=不在戰鬥中） */
    var battleId: Long
}

/**
 * PK 設定
 */
interface SessionPk {
    /** PK 開關（true=允許被 PK） */
    var pkEnabled: Boolean
    /** 參戰開關（true=允許參戰） */
    var joinEnabled: Boolean
}

/**
 * 坐騎狀態 — 用於 S:003-000 玩家出現封包和 S:079-007 廣播
 */
interface SessionMount {
    /** 當前騎乘的坐騎 NPC ID（0 = 未騎乘） */
    var mountNpcId: Int
    /** 坐騎等級（用於其他玩家出現封包的 MountsLv 屬性） */
    var mountLevel: Int
}

/**
 * 遊戲連線階段 — 組合所有子介面
 *
 * 每個 TCP 連線對應一個 GameSession 實例。
 * 拆分為多個小介面，讓各層只依賴需要的部分。
 */
interface GameSession : SessionIdentity, SessionStatus, SessionCharacter, SessionWorld, SessionAppearance, SessionBattle, SessionPk, SessionMount, SessionSender

/**
 * 從角色記錄載入基本資訊到 session（角色 ID、名稱、外觀）
 *
 * LoginHandler 和 SelectCharHandler 共用。
 */
fun GameSession.loadFromRecord(record: CharacterRecord) {
    roleId = record.id.toInt()
    characterName = record.name
    state = SessionState.IN_GAME
    sex = record.sex
    element = record.element
    level = record.level
    turn = record.turn
    career = record.career
    hair = record.hair
    color1 = record.color1
    color2 = record.color2
    titleId = record.titleId
}

package tw.tsm.domain.constants

/**
 * 好友系統常數
 *
 * 來源：Social.lua 常數定義 + protocal.lua protocolTable[14]
 */
object FriendConstants {
    /** 最大好友數量 */
    const val MAX_FRIENDS = 50
    /** 最大信任度 */
    const val MAX_FRIENDLY = 9999
    /** 每日最大可收禮數量 */
    const val MAX_GIFT_RECEIVE = 10
    /** 每日最大可送禮數量 */
    const val MAX_GIFT_SEND = 20

    // ── S:014-007 訊息種類 ──
    /** 該場景人數過多 */
    const val MSG_SCENE_TOO_CROWDED = 1
    /** 邀請成功 */
    const val MSG_INVITE_SENT = 2
    /** 重複邀請 */
    const val MSG_DUPLICATE_INVITE = 3
    /** 對方邀請名單已滿 */
    const val MSG_TARGET_INVITE_FULL = 4
    /** 對方好友名單已滿 */
    const val MSG_TARGET_FRIEND_FULL = 5
    /** 自己好友名單已滿 */
    const val MSG_MY_FRIEND_FULL = 6
    /** 查無此玩家 */
    const val MSG_PLAYER_NOT_FOUND = 7

    // ── S:014-015 收禮結果 ──
    /** 收禮成功 */
    const val GIFT_SUCCESS = 1
    /** 未獲得禮物 */
    const val GIFT_NOT_RECEIVED = 2
    /** 已領取 */
    const val GIFT_ALREADY_TAKEN = 3
    /** 已達領取上限 */
    const val GIFT_LIMIT_REACHED = 4
    /** 物品 ID 錯誤 */
    const val GIFT_ITEM_ERROR = 5
    /** 背包空間不足 */
    const val GIFT_BAG_FULL = 6
}

package tw.tsm.domain.interfaces

import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SparSlot
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.interfaces.repositories.FriendRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.domain.valueobjects.ThingData

/**
 * 回應發送介面 — gamelogic 只發意圖，不碰封包格式
 *
 * gamelogic 層透過此介面發送回應，
 * app 層的 ResponseSenderImpl 負責將意圖序列化為實際封包。
 */
interface ResponseSender {

    // ── 登入相關 ──

    /**
     * 發送登入結果 (S:001-002)
     *
     * @param serverTime OLE Automation Date（Double）
     */
    fun sendLoginResult(
        session: SessionSender,
        resultCode: Int,
        dbId: Int = 0,
        gsId: Int = 0,
        playerId: Long = 0,
        serverTime: Double = 0.0,
        account: String = "",
        password: String = "",
        accountKind: Int = 0
    )

    /**
     * 發送登入成功 (S:001-003)
     */
    fun sendLoginSuccess(session: SessionSender, needChangePassword: Boolean = false)

    /**
     * 發送登入序列完成 (S:001-013)
     */
    fun sendLoginFinished(session: SessionSender, connectCode: Long)

    // ── 玩家資料 ──

    /**
     * 發送玩家資料大包 (S:005-003)
     */
    fun sendPlayerData(session: SessionSender, data: PlayerDataDto)

    /**
     * 發送丹藥次數 (S:005-011)
     */
    fun sendPillCounts(
        session: SessionSender,
        attrPill: Int,
        hpPill: Int,
        spPill: Int,
        skillPill: Int
    )

    // ── 社交/時間 ──

    /**
     * 發送登入天數 (S:003-001)
     */
    fun sendLoginDay(session: SessionSender, loginDay: Int, continueLogin: Int)

    /**
     * 發送每日在線時間 (S:003-002)
     */
    fun sendOnlineTime(session: SessionSender, time: Double)

    /**
     * 發送半月卡時間 (S:003-003)
     */
    fun sendHalfMonthCardTime(session: SessionSender, time: Double)

    // ── 角色進入 ──

    /**
     * 發送玩家出現 (S:003-000)
     *
     * 來源：protocal.lua protocolTable[3][0] → Role.PlayerAppear
     *
     * @param playerId 玩家 ID（與 S:001-002 一致）
     * @param record 角色資料
     * @param serverId 伺服器 ID
     */
    fun sendPlayerAppear(session: SessionSender, playerId: Long, record: CharacterRecord, serverId: Int = 1, turn3Element: Int = 0)

    // ── 創角相關 ──

    /**
     * 發送創角成功 (S:009-001)
     *
     * 客戶端收到後關閉 UICreateRole，自動發送 C:003 選擇角色
     */
    fun sendCreateCharSuccess(session: SessionSender)

    /**
     * 發送檢查名字結果 (S:009-003)
     *
     * @param result 0=可用, 1=重複, 2=非法, 3=其他錯誤
     */
    fun sendCheckNameResult(session: SessionSender, result: Int)

    // ── 通用 ──

    /**
     * 發送心跳回應 (S:010-000)
     */
    fun sendKeepalive(session: SessionSender)

    /**
     * 發送斷線通知 (S:000-000)
     */
    fun sendDisconnect(session: SessionSender, reason: Int)

    // ── 移動相關 ──

    /**
     * 廣播玩家移動給同地圖其他玩家 (S:006-001)
     *
     * 來源：protocal.lua protocolTable[6][1]
     * 格式：+roleId(8) +direction(1) +destX(2) +destY(2)
     *
     * @param sender 發起移動的 session（用於取得 mapId 做廣播，自身不發）
     */
    fun sendPlayerMove(sender: GameSession, playerId: Long, direction: Int, x: Int, y: Int)

    // ── 聊天相關 ──

    /**
     * 廣播聊天訊息給所有在線玩家 (S:002-{channel})
     *
     * 來源：protocal.lua protocolTable[2][0~7]
     * 格式：+senderId(8) +titleId(2) +senderNameLen(1) +senderName(Big5)
     *       +messageLen(1) +message(Big5) +itemCount(1) +npcCount(1)
     */
    fun sendChatMessage(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String
    )

    /**
     * 廣播聊天訊息給指定地圖玩家（輕頻） (S:002-002)
     */
    fun sendChatMessageToMap(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String,
        mapId: Int
    )

    /**
     * 發送密語給指定玩家 (S:002-003)
     */
    fun sendWhisperMessage(
        target: SessionSender, senderId: Long, senderName: String, content: String,
        titleId: Int = 0
    )

    // ── 系統訊息 ──

    /**
     * 發送系統訊息到單一玩家 (S:002-000)
     *
     * 用途：GM 指令回饋、系統通知。
     * 使用 ChatChannel.SYSTEM (subKind=0)，只發給指定 session，不廣播。
     */
    fun sendSystemMessage(session: SessionSender, content: String)

    // ── 場景相關 ──

    /**
     * 發送分區列表 (S:007-001)
     *
     * 格式：+count(1) + [index(2) + currentPlayers(2) + maxPlayers(2)]...
     */
    fun sendAreaList(session: SessionSender, onlineCount: Int)

    /**
     * 發送分區切換結果 (S:007-002)
     *
     * @param resultCode 1=同分區 2=找不到 3=組隊中 4=已滿 其他=成功
     */
    fun sendAreaSwitchResult(session: SessionSender, resultCode: Int)

    /**
     * 發送玩家瞬移 (S:007-000)
     *
     * 格式：+roleId(8) +sceneId(2) +x(2) +y(2)
     */
    fun sendPlayerTeleport(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int)

    /**
     * 發送場景切換 (S:012-000)
     *
     * 格式：+roleId(8) +sceneId(2) +x(2) +y(2) +tag(2) +instanceId(2)
     *       +equipCount(1) +[itemId(2)]... +outfitCount(1) +[outfitId(2)]... +titleId(2)
     */
    fun sendSceneChange(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int, equipItemIds: IntArray = intArrayOf(), titleId: Int = 0)

    /**
     * 發送其他玩家出現 (S:003-000)
     *
     * 用已知的 session 資料組裝簡易版玩家出現封包
     */
    fun sendOtherPlayerAppear(target: SessionSender, other: GameSession)

    /**
     * 廣播玩家更換稱號 (S:005-012)
     *
     * 格式：+roleId(8) +titleId(2)
     */
    fun sendTitleChange(target: SessionSender, roleId: Long, titleId: Int)

    // ── NPC 事件相關 ──

    /**
     * 發送事件換場景通知 (S:020-007)
     *
     * 通知客戶端即將切換場景（EventManager.ReceiveChangeSceneEvent）。
     * 應在 S:012-000 之前發送，客戶端收到後設定 isChangingScene=true 並準備換場景。
     */
    fun sendEventChangeScene(session: SessionSender)

    /**
     * 發送一般事件結果 (S:020-001)
     *
     * 來源：protocal.lua protocolTable[20][1] → EventManager.ReceiveCommonEvent
     * 格式（15 bytes LE）：
     *   +resultDiagnosis(1) +resultGroupNo(2) +resultNo(1) +resultType(1)
     *   +resultClass(1) +parameter(2) +parameterStyle(1) +resultValue(4) +resultMeanNo(2)
     *
     * @param result Eve_NpcEventData.lua 的 ResultArray 單筆
     */
    fun sendCommonEvent(session: SessionSender, result: EveResult)

    /**
     * 發送事件結束 (S:020-008)
     *
     * 通知客戶端清除 EventManager 的 Running 狀態。
     * 來源：protocal.lua protocolTable[20][8] → EventManager.ReceiveEndEvent
     *
     * @param endKind 結束種類（0=正常結束, 45=死亡停止導航, 46=死亡）
     */
    fun sendEventEnd(session: SessionSender, endKind: Int = 0)

    /**
     * 開啟玩家第一次轉生介面 (S:036-011)
     *
     * 來源：protocal.lua protocolTable[36][11] → UI.Open(UITurn, ETrunMode.Reincarnation, 1)。
     * 由 Eve resultType=7/resultClass=7/pStyle=12 觸發。
     */
    fun sendOpenPlayerReincarnation(session: SessionSender)

    /**
     * 開啟殺敵點兌換商店 (S:036-013)
     *
     * 來源：protocal.lua protocolTable[36][13] → UI.Open(UIShop, EUIShop.KillPoint, options)。
     * 由 Eve resultType=7/resultClass=7/pStyle=13 觸發。
     */
    fun sendOpenKillPointShop(session: SessionSender)

    /**
     * 開啟水戰奉邑介面或續行事件 (S:036-015)
     *
     * 來源：protocal.lua protocolTable[36][15]，目前客戶端水戰 UI 已停用，只呼叫
     * EventManager.SetConduct(true) 讓事件流程繼續。
     * 由 Eve resultType=7/resultClass=7/pStyle=15 觸發。
     */
    fun sendOpenWaterWarOrgManage(session: SessionSender)

    /**
     * 開啟武將二轉介面 (S:044-003)
     *
     * 來源：protocal.lua protocolTable[44][3] → UI.Open(UINpcTurn, 2)。
     * 由 Eve resultType=7/resultClass=7/pStyle=21 觸發。
     */
    fun sendOpenNpcSecondReincarnation(session: SessionSender)

    /**
     * 開啟武將轉生（一轉）介面 (S:044-002)
     *
     * 來源：protocal.lua protocolTable[44][2] → UI.Open(UINpcTurn, 1)。
     * Eve.emg 全量掃描確認無對應入口（docs/eve-gap-analysis.md §5），
     * 推測由任務系統觸發；目前由 GM 指令 /武將轉生 觸發。
     */
    fun sendOpenNpcReincarnation(session: SessionSender)

    /**
     * 發送武將轉生訊息 (S:044-001)
     *
     * 格式：+種類(1)
     *
     * @param msgKind 0=轉生成功, 1=無信物, 2=無可轉生武將, 3=等級<30, 4/9=轉生後武將已在客棧,
     *   5=轉生後武將已在身上, 6=不可成長, 7=忠誠<60, 8=裝備欄未清空,
     *   10=等級<60, 11=忠誠<100, 255=例外錯誤
     */
    fun sendPetRebornMessage(session: SessionSender, msgKind: Int)

    /**
     * 發送尋找惡魔巴豆妖錯誤訊息 (S:020-027)
     *
     * 來源：protocal.lua protocolTable[20][27]，客戶端顯示 string.Get(30099)。
     * 由 Eve resultType=7/resultClass=7/pStyle=27 觸發。
     */
    fun sendBadoErrorMessage(session: SessionSender)

    /**
     * 發送尋找惡魔巴豆妖開始訊息 (S:020-029)
     *
     * 來源：protocal.lua protocolTable[20][29]，客戶端顯示系統聊天與跑馬燈 string.Get(30101)。
     * 由 Eve resultType=7/resultClass=7/pStyle=29 觸發。
     */
    fun sendBadoStartMessage(session: SessionSender)

    /**
     * 開啟無界排行榜 (S:036-010)
     *
     * 來源：protocal.lua protocolTable[36][10]。
     * 由 Eve resultType=7/resultClass=7/pStyle=10 觸發。
     */
    fun sendOpenGalaxyLeaderboard(session: SessionSender)

    /**
     * 發送聖誕節活動訊息 (S:020-030)
     *
     * 來源：protocal.lua protocolTable[20][30]，客戶端顯示 string.Get(30107)。
     * 由 Eve resultType=7/resultClass=7/pStyle=30 觸發。
     */
    fun sendChristmasEventMessage(session: SessionSender)

    /**
     * 發送擂台賽錯誤訊息 (S:020-032)
     *
     * 來源：protocal.lua protocolTable[20][32]，客戶端顯示 string.Get(30110)。
     * 由 Eve resultType=7/resultClass=7/pStyle=32 觸發。
     */
    fun sendArenaErrorMessage(session: SessionSender)

    /**
     * 發送元宵節錯誤訊息 (S:020-035)
     *
     * 來源：protocal.lua protocolTable[20][35]，客戶端顯示 string.Get(30117)。
     * 由 Eve resultType=7/resultClass=7/pStyle=35 觸發。
     */
    fun sendLanternErrorMessage(session: SessionSender)

    /**
     * 發送未知活動訊息 (S:020-039)
     *
     * 來源：protocal.lua protocolTable[20][39]，客戶端顯示 string.Get(30125)。
     * 由 Eve resultType=7/resultClass=7/pStyle=39 觸發。
     */
    fun sendUnknownActivityMessage(session: SessionSender)

    // ── 任務系統 (mainKind=24) ──

    /**
     * 發送新增任務步驟 (S:024-001)
     *
     * 對應 MarkManager.AddMissionStep()。
     * 格式：+missionId(2B LE) +step(1B)
     *
     * @param missionId 任務 ID
     * @param step      步驟增加量
     */
    fun sendAddMissionStep(session: SessionSender, missionId: Int, step: Int)

    /**
     * 發送減少任務步驟 (S:024-002)
     *
     * 對應 MarkManager.DecMissionStep()。
     * 格式：+missionId(2B LE) +step(1B)
     *
     * @param missionId 任務 ID
     * @param step      步驟減少量
     */
    fun sendDecMissionStep(session: SessionSender, missionId: Int, step: Int)

    /**
     * 發送刪除任務 (S:024-004)
     *
     * 對應 MarkManager.RemoveMission()。
     * 格式：+missionId(2B LE)
     *
     * @param missionId 任務 ID
     */
    fun sendRemoveMission(session: SessionSender, missionId: Int)

    /**
     * 發送初始化任務步驟 (S:024-006)
     *
     * 登入時下發所有進行中任務，對應 MarkManager.InitMissionStep()。
     * 格式：+count(4B LE) +<<index(1B) +missionId(2B LE) +step(1B)>>
     *
     * @param missions 進行中任務列表（可為空）
     */
    fun sendInitMissionSteps(session: SessionSender, missions: List<MissionRecord>)

    /**
     * 發送初始化任務標記 (S:024-007)
     *
     * 登入時下發所有已完成任務標記，對應 MarkManager.InitMissionFlag()。
     * 格式：+count(2B LE) +<<bitIndex(2B LE) +bitValue(1B)>>
     *
     * @param flags 已完成任務標記列表（可為空）
     */
    fun sendInitMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>)

    /**
     * 發送設定任務標記 (S:024-005)
     *
     * 任務完成時下發，對應 MarkManager.SetMissionFlag()。
     * 格式：+count(4B LE UInt32) <<+bitIndex(2B LE UInt16) +bitValue(1B Boolean)>>
     *
     * 注意：與 S:024-007 格式不同！
     * - S:024-005: count=UInt32(4B), bitValue=Boolean(1B)
     * - S:024-007: count=UInt16(2B), bitValue=Byte(1B)
     *
     * @param flags 要設定的標記列表
     */
    fun sendSetMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>)

    // ── 永標系統 (mainKind=81) ──

    /**
     * 發送初始化永標 (S:081-002)
     *
     * 登入時下發所有永標，對應 BitFlag.Init()。
     * 格式：+size(2B LE UInt16) +永標字節陣列(size×1B)
     *
     * @param packedFlags 打包後的永標 byte 陣列
     */
    fun sendInitBitFlags(session: SessionSender, packedFlags: ByteArray)

    /**
     * 發送永標變更 (S:081-001)
     *
     * 運行時更新永標，對應 BitFlag.Set()。
     * 格式：+count(4B LE UInt32) <<+flagId(2B LE UInt16) +value(1B Boolean)>>
     *
     * @param changes (flagId, value) 列表
     */
    fun sendBitFlagChanges(session: SessionSender, changes: List<Pair<Int, Boolean>>)

    // ── 背包/物品相關 (mainKind=23) ──

    /**
     * 發送背包所有物品 (S:023-005)
     *
     * 格式：+refresh(1) +count(2) +<<slot(1) +ThingData(35)>>
     */
    fun sendAllBagItems(session: SessionSender, items: Map<Int, ThingData>, refresh: Boolean = false)

    /**
     * 發送背包設定物品 (S:023-008)
     *
     * 格式：+slot(1) +ThingData(35) +showMsg(1)
     */
    fun sendSetBagItem(session: SessionSender, slot: Int, thing: ThingData, showMsg: Boolean = true)

    /**
     * 發送背包減少物品 (S:023-009)
     *
     * 格式：+slot(1) +amount(4)
     */
    fun sendReduceBagItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * 發送背包移動物品 (S:023-010)
     *
     * 格式：+fromSlot(1) +amount(4) +toSlot(1)
     */
    fun sendMoveBagItem(session: SessionSender, fromSlot: Int, amount: Int, toSlot: Int)

    /**
     * 發送所有裝備 (S:023-011)
     *
     * 格式：+count(1) +<<ThingData(35)>>
     * 客戶端用 ItemDef.fitType 決定槽位。
     */
    fun sendAllEquipment(session: SessionSender, equips: Map<Int, ThingData>)

    /**
     * 發送刪除裝備 (S:023-012)
     *
     * 格式：+fitType(1)
     */
    fun sendDeleteEquip(session: SessionSender, fitType: Int)

    /**
     * 發送使用物品成功 (S:023-015)
     *
     * 格式：+itemId(2) +count(4) +showMsg(1)
     */
    fun sendUseItemSuccess(session: SessionSender, itemId: Int, count: Int, showMsg: Boolean = true)

    /**
     * 發送卸下裝備 (S:023-016)
     *
     * 格式：+fitType(1) +bagSlot(1)
     */
    fun sendUnequip(session: SessionSender, fitType: Int, bagSlot: Int)

    /**
     * 發送穿上裝備 (S:023-017)
     *
     * 格式：+bagSlot(1)
     */
    fun sendEquip(session: SessionSender, bagSlot: Int)

    /**
     * 發送武將卸下裝備（直接刪除，丟棄用） (S:023-021)
     *
     * 格式：+followSlot(1) +fitType(1)
     */
    fun sendPetEquipDelete(session: SessionSender, followSlot: Int, fitType: Int)

    /**
     * 發送武將卸下裝備到背包 (S:023-022)
     *
     * 格式：+followSlot(1) +fitType(1) +bagSlot(1)
     */
    fun sendPetUnequipToBag(session: SessionSender, followSlot: Int, fitType: Int, bagSlot: Int)

    /**
     * 發送武將穿上裝備 (S:023-023)
     *
     * 格式：+followSlot(1) +bagSlot(1)
     * 客戶端收到後自行做背包格 ↔ 裝備格互換。
     */
    fun sendPetEquip(session: SessionSender, followSlot: Int, bagSlot: Int)

    /**
     * 發送拾取結果 (S:023-025)
     *
     * 格式：+result(1) [0=成功:+kind(2)+value(2)+x(2)+y(2), 1=無法拾取, 2=距離太遠]
     */
    fun sendPickupResult(session: SessionSender, result: Int, kind: Int = 0, value: Int = 0, x: Int = 0, y: Int = 0)

    /**
     * 廣播新增場景物品 (S:023-003)
     *
     * 格式：+itemId(2) +x(2) +y(2) +isMe(1) +canPickPlayerId(8)
     *
     * @param canPickPlayerId 可撿玩家 ID（0 = 所有人均可撿）
     */
    fun sendAddGroundItem(session: SessionSender, itemId: Int, x: Int, y: Int, isMe: Int, canPickPlayerId: Long = 0L)

    /**
     * 廣播刪除場景物品 (S:023-002)
     *
     * 格式：+index(2) +isMe(1)
     */
    fun sendRemoveGroundItem(session: SessionSender, index: Int, isMe: Boolean)

    /**
     * 廣播丟棄金錢到場景 (S:023-018)
     *
     * 格式：+amount(4) +x(2) +y(2) +isMe(1)
     */
    fun sendDropMoneyToGround(session: SessionSender, amount: Int, x: Int, y: Int, isMe: Boolean)

    // ── 玩家交易 (mainKind=25) ──

    /**
     * 發送交易物品開始 (S:025-001)
     *
     * 向被邀請方發送，客戶端收到後開啟 UITrade。
     * 格式：+partnerId(8)
     */
    fun sendTradeStart(session: SessionSender, partnerId: Long)

    /**
     * 發送交易物品取消/完成 (S:025-002)
     *
     * reason 嚴格對照 protocal.lua:6189-6212（TextData 40010~40019）：
     * 0=系統忙碌, 1=對方斷線, 2=對方忙碌中, 3=對方取消,
     * 4=雙方交易完成, 5=不同場景, 6=金幣不足, 7=物品過多, 8=戰鬥中, 其他=系統中斷
     * 格式：+reason(1)
     */
    fun sendTradeCancel(session: SessionSender, reason: Int)

    /**
     * 發送交易物品確認 — 展示對方出價 (S:025-003)
     *
     * 雙方上傳後，伺服器向各方發送對方的出價內容。
     * 格式：+money(4) +<<ThingData(35)>>
     */
    fun sendTradeConfirm(session: SessionSender, money: Int, items: List<ThingData>)

    /**
     * 發送交易武將開始 (S:025-010)
     *
     * 向被邀請方發送，客戶端收到後開啟 UITrade 武將模式。
     * 格式：+partnerId(8)
     */
    fun sendPetTradeStart(session: SessionSender, partnerId: Long)

    /**
     * 發送交易武將取消/完成 (S:025-011)
     *
     * reason 0~8 同 S:025-002；武將專屬：9=資料比對有異, 10=身上滿四將,
     * 11=客棧重複, 12=忠誠為零, 13=馬車重複, 14=身上有不可轉讓物品
     * 格式：+reason(1)
     */
    fun sendPetTradeCancel(session: SessionSender, reason: Int)

    /**
     * 發送交易武將確認 — 展示對方出價 (S:025-012)
     *
     * 格式：+money(4) +npcId(2) [+等級(1) +MaxHp(2) +Hp(2) +MaxSp(2) +Sp(2)
     * +L(1)+名字(L) +忠誠(1) +昇華丹(1) +SP丹(1) +HP丹(1) +裝備數量(1) <<+ThingData(35)>>]
     * pet 為 null 時只發 money + npcId=0（對方不出武將）。
     */
    fun sendPetTradeConfirm(session: SessionSender, money: Int, pet: FollowNpcData?, maxHp: Int, maxSp: Int)

    // ── 金錢相關 (mainKind=26) ──

    /**
     * 發送初始化金錢 (S:026-004)
     *
     * 格式：+gold(4) +auctionGold(4)
     */
    fun sendInitMoney(session: SessionSender, gold: Int, auctionGold: Int)

    /**
     * 發送增加金錢 (S:026-001)
     *
     * 格式：+amount(4)
     */
    fun sendAddMoney(session: SessionSender, amount: Int)

    /**
     * 發送減少金錢 (S:026-002)
     *
     * 格式：+amount(4)
     */
    fun sendSubtractMoney(session: SessionSender, amount: Int)

    /**
     * 發送金錢容量已滿 (S:026-003)
     *
     * 格式：+kind(1) [1=金錢滿, 2=拍賣金滿]
     */
    fun sendMoneyFull(session: SessionSender, kind: Int)

    // ── NPC 買賣 (mainKind=27) ──

    /**
     * 發送買東西結果 (S:027-001)
     *
     * 格式：+result(1)
     */
    fun sendBuyResult(session: SessionSender, result: Int)

    /**
     * 發送賣東西結果 (S:027-002)
     *
     * 格式：+result(1) +count(1) +<<bagIndex(1)>>
     */
    fun sendSellResult(session: SessionSender, result: Int, bagIndices: List<Int>)

    /**
     * S:027-003 開啟 NPC 賣東西介面
     *
     * ```
     * [mainKind 1B] [subKind 1B] [compress 1B] [kind 1B]
     * ```
     * kind: 1=事件商店 2=隨身雜貨商
     */
    fun sendOpenNpcStore(session: SessionSender, kind: Int)

    /**
     * 發送記憶傳送符資料 (S:023-118)
     *
     * 登入時或記憶操作後發送，客戶端更新 UITeleport.flyPoints。
     *
     * ```
     * [mainKind=23 1B] [subKind=118 1B] [compress=0 1B]
     * [flyPointCount 1B] [count 1B]
     * <<[index 1B] [sceneId 2B LE] [x 2B LE] [y 2B LE]>>
     * ```
     *
     * @param session 玩家連線
     * @param flyPointCount 已開啟的額外記憶傳送符數量
     * @param points 已記憶的傳送點 Map<索引(1-based), FlyPointRecord>
     */
    fun sendFlyPointData(session: SessionSender, flyPointCount: Int, points: Map<Int, FlyPointRecord>)

    // ── 武將強化 (mainKind=74, 8, 15) ──

    /**
     * 發送武將操作暫存資料 (S:008-011)
     *
     * 強化/脫胎換骨的屬性預覽，客戶端存入 Role.rebornNpcId/rebornAttributes。
     * 格式：+操作種類(1) +武將ID(2) +武將屬性(1)×6
     * 屬性線序：int, atk, def, agi, hpx, spx
     *
     * @param kind 1=強化, 2=脫胎換骨
     * @param gains 六圍增加值 [int, atk, def, agi, hpx, spx]
     */
    fun sendPetTempUpgradeData(session: SessionSender, kind: Int, npcId: Int, gains: IntArray)

    /**
     * 發送武將強化等級 (S:015-020)
     *
     * 格式：+武將索引(1) +強化等級(1)
     */
    fun sendPetUpgradeLv(session: SessionSender, followIndex: Int, upgradeLv: Int)

    /**
     * 發送武將強化結果 (S:074-002 成功 / S:074-003 取消)
     *
     * 兩者皆為空 payload，客戶端清除強化暫存狀態。
     *
     * @param success true=S:074-002 強化成功, false=S:074-003 取消
     */
    fun sendPetUpgradeResult(session: SessionSender, success: Boolean)

    // ── 介面消費 (mainKind=84) ──

    /**
     * 發送介消失敗 (S:084-001 / S:084-002 結果=0)
     *
     * 格式：+介消編號(2) +結果(1)=0 +原因(1) +數值(4)
     * 原因碼對照 string.GetUiSellFail：1=無介消資料, 5=金錢不足, 7=扣物品失敗,
     * 11=已達最大上限, 12=元寶不足, 15=不可使用, 17=所在場景不可傳送
     *
     * @param subKind 1=查詢回覆, 2=結果回覆
     * @param reason 失敗原因碼
     * @param value 附帶數值（原因 2 等級不足時為需求等級，其餘 0）
     */
    fun sendUiSellFail(session: SessionSender, subKind: Int, sellId: Int, reason: Int, value: Long = 0)

    /**
     * 發送介消金錢報價 (S:084-001 結果 1/2/3)
     *
     * 格式：+介消編號(2) +結果(1) +金額(4)
     *
     * @param currencyKind 1=元寶, 2=綁元, 3=遊戲幣
     * @param cost 消費金額
     */
    fun sendUiSellMoneyQuery(session: SessionSender, sellId: Int, currencyKind: Int, cost: Long)

    /**
     * 發送介消道具報價 (S:084-001 結果=255)
     *
     * 格式：+介消編號(2) +結果(1)=255 +物品ID(2) +數量(2)
     * ⚠ protocal.lua 註解寫數量(1)，但客戶端實際 ReadUInt16 — 以 Lua 讀取程式碼為準
     */
    fun sendUiSellItemQuery(session: SessionSender, sellId: Int, itemId: Int, count: Int)

    /**
     * 發送介消免費報價 (S:084-001 結果=254)
     *
     * 格式：+介消編號(2) +結果(1)=254
     */
    fun sendUiSellFreeQuery(session: SessionSender, sellId: Int)

    /**
     * 發送介消成功結果 (S:084-002)
     *
     * 格式：+介消編號(2) +結果(1)=1
     */
    fun sendUiSellSuccess(session: SessionSender, sellId: Int)

    // ── 點數 (mainKind=35) ──

    /**
     * 發送剩餘元寶點數 (S:035-005)
     *
     * 格式：+實點(4) +虛點(4)
     * 客戶端 Role.SetPoint(real) / Role.SetLockPoint(virtual) 刷新顯示。
     */
    fun sendPointBalance(session: SessionSender, pointReal: Int, pointVirtual: Int)

    /** S:035-050 DVT generic player currency. */
    fun sendPlayerCurrency(session: SessionSender, currencyKind: Int, value: Int)

    /** S:023-150 Doro draw results; one entry per egg/draw. */
    fun sendDoroResult(session: SessionSender, rewards: List<tw.tsm.domain.data.DoroReward>)

    /** S:066-003 Mall purchase result. */
    fun sendMallPurchaseResult(session: SessionSender, itemId: Int, result: Int)

    // ── 錢莊 (mainKind=30) ──

    /**
     * 發送錢莊所有物品 (S:030-001)
     *
     * 格式：<<+slot(1) +ThingData(35)>>
     */
    fun sendBankAllItems(session: SessionSender, items: Map<Int, ThingData>)

    /**
     * 發送錢莊設定物品 (S:030-004)
     *
     * 格式：+slot(1) +ThingData(35)
     */
    fun sendBankSetItem(session: SessionSender, slot: Int, thing: ThingData)

    /**
     * 發送錢莊減少物品 (S:030-005)
     *
     * 格式：+slot(1) +amount(4)
     */
    fun sendBankReduceItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * 發送錢莊操作失敗 (S:030-007)
     *
     * 格式：+failCode(1) [3=存入失敗, 13=錢莊已滿]
     */
    fun sendBankFail(session: SessionSender, failCode: Int)

    /**
     * 發送清空錢莊物品 (S:030-008)
     */
    fun sendBankClear(session: SessionSender)

    /**
     * 開啟錢莊 UI (S:029-006)
     *
     * 伺服器命令客戶端打開 UIBank。
     * 對應 protocal.lua protocolTable[29][6]
     */
    fun sendOpenBank(session: SessionSender)

    /**
     * 發送錢莊等級 (S:029-009)
     *
     * 對應 protocal.lua protocolTable[29][9] — Role.SetBankLv(level)
     *
     * @param level 錢莊等級
     */
    fun sendBankLevel(session: SessionSender, level: Int)

    // ── 客棧 (mainKind=31) ──

    /**
     * S:031-002 住宿費用確認
     *
     * 客戶端收到後顯示費用對話框，玩家確認後發送 C:031-001（InnHandler.handleRest）。
     * 事件系統 resultType=7 cls=7 pStyle=5 觸發。
     */
    fun sendInnRestConfirm(session: SessionSender, cost: Int)

    /**
     * 開啟 UINpcInn UI（S:031-015 等級 + S:031-006 武將資料 + S:031-007 開啟 UI）
     *
     * 事件系統 resultType=7 cls=7 pStyle=4 觸發。
     * UINpcInn 為武將倉庫 + 客棧儲存的共用雙頁籤 UI。
     */
    fun sendOpenInn(session: SessionSender, innLevel: Int, innPets: Map<Int, FollowNpcData>)

    /**
     * S:122-001 武將倉庫資訊
     *
     * 回應客戶端 C:122-001 查詢（UINpcInn OnOpen 自動發送）。
     */
    fun sendOpenWarehouse(session: SessionSender, warehousePets: List<FollowNpcData>)

    // ── 寶庫 (mainKind=102) ──

    /**
     * 發送寶庫所有物品 (S:102-001)
     *
     * 格式：<<+slot(1) +ThingData(35)>>
     */
    fun sendStorageAllItems(session: SessionSender, items: Map<Int, ThingData>)

    /**
     * 發送寶庫設定物品 (S:102-004)
     *
     * 格式：+slot(1) +ThingData(35)
     */
    fun sendStorageSetItem(session: SessionSender, slot: Int, thing: ThingData)

    /**
     * 發送寶庫減少物品 (S:102-005)
     *
     * 格式：+slot(1) +amount(4)
     */
    fun sendStorageReduceItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * 發送寶庫操作失敗 (S:102-007)
     *
     * 格式：+failCode(1) [3=存入失敗, 13=寶庫已滿, 14=非可堆疊物品]
     */
    fun sendStorageFail(session: SessionSender, failCode: Int)

    /**
     * 發送清空寶庫物品 (S:102-008)
     */
    fun sendStorageClear(session: SessionSender)

    /**
     * 發送角色計數資料 (S:100-017)
     *
     * 用於告知客戶端背包格數（Bag_1/2/3）等計數值。
     * 客戶端公式：currentThingGrid = InitBagGrid(50) + Bag_1*5 + Bag_2*5 + Bag_3*5
     *
     * @param session 目標 session
     * @param counts 計數索引 → 數值映射（例如 103→0, 106→0, 124→0）
     */
    fun sendRoleCountData(session: SessionSender, counts: Map<Int, Int>)

    // ── 技能相關 (mainKind=8) ──

    /**
     * 發送主角技能資料 (S:008-013)
     *
     * 來源：protocal.lua protocolTable[8][13] → Role.ReceivePlayerSkillData
     * 格式：+element(1) +skillPoint(2LE) +skillCount(2LE) <<+skillId(2LE) +level(1)>>
     *
     * @param element 角色元素
     * @param skillPoint 可用技能點
     * @param skills skillId → level 映射
     */
    fun sendPlayerSkillData(session: SessionSender, element: Int, skillPoint: Int, skills: Map<Int, Int>)

    /** S:008-012 設定三轉光暗元素結果。 */
    fun sendTurn3ElementResult(session: SessionSender, result: Int)

    /** S:023-077 天珠資訊：可用數 + 已啟動索引。 */
    fun sendTurn2Balls(session: SessionSender, available: Int, active: Set<Int>)

    /** S:023-078 二轉英雄技能槽。Map key 使用 UI 槽 6..13。 */
    fun sendTurn2HeroSkills(session: SessionSender, heroSkills: Map<Int, Int>)

    /**
     * 發送熱鍵資料 (S:040-001)
     *
     * 來源：protocal.lua protocolTable[40][1] → Hotkey.ReceiveHotKeyData
     * 格式：PlayerMain 段 [頁數(1)+鍵數(1)+<<ID(2)>>] + PlayerSub 段 [頁數(1)+鍵數(1)+<<ID(2)>>]
     *
     * @param mainKeys 技能快捷鍵 ID 列表（PlayerMain）
     * @param subKeys 物品快捷鍵 ID 列表（PlayerSub）
     */
    fun sendHotkeyData(session: SessionSender, mainKeys: List<Int>, subKeys: List<Int>)

    /**
     * S:040-002 快捷鍵設定結果
     *
     * ```
     * [mainKind=40 1B] [subKind=2 1B] [compress=0 1B] [result 1B]
     * [若 result=0] dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
     * ```
     * result: 0=成功
     */
    fun sendHotkeySetResult(session: SessionSender, result: Int,
                            dataIndex: Int = 0, pageIndex: Int = 0,
                            keyIndex: Int = 0, useId: Int = 0)

    // ── 隊伍系統 (mainKind=13) ──

    /**
     * 發送組隊要求（S:013-001）
     *
     * 通知隊長有玩家要求加入隊伍。
     * 格式：+playerId(8)
     */
    fun sendPartyJoinRequest(session: SessionSender, requesterId: Long)

    /**
     * 發送組隊要求結果（S:013-003）
     *
     * 通知要求方隊長的回覆。
     * 格式：+result(1) +nameLen(1) +name(Big5)
     */
    fun sendPartyJoinResult(session: SessionSender, result: Int, leaderName: String)

    /**
     * 發送離隊通知（S:013-004）
     *
     * 格式：+playerId(8) +x(2) +y(2)
     */
    fun sendPartyLeave(session: SessionSender, playerId: Long, x: Int, y: Int)

    /**
     * 發送加入隊伍通知（S:013-005）
     *
     * 格式：+leaderId(8) +playerId(8)
     */
    fun sendPartyMemberJoin(session: SessionSender, leaderId: Long, newMemberId: Long)

    /**
     * 發送隊伍完整資料（S:013-006）
     *
     * 登入或加入時同步。
     * 格式：<<leaderId(8) +count(1) <<playerId(8)>>>>
     */
    fun sendPartyData(session: SessionSender, parties: Map<Long, List<Long>>)

    /**
     * 發送設定軍師通知（S:013-007）
     *
     * 格式：+playerId(8)
     */
    fun sendPartySetAdviser(session: SessionSender, adviserId: Long)

    /**
     * 發送取消軍師通知（S:013-008）
     *
     * 格式：空封包
     */
    fun sendPartyRemoveAdviser(session: SessionSender)

    /**
     * 發送邀請組隊（S:013-009）
     *
     * 格式：+playerId(8)
     */
    fun sendPartyInvite(session: SessionSender, inviterId: Long)

    /**
     * 發送邀請組隊結果（S:013-010）
     *
     * 格式：+result(1) +nameLen(1) +name(Big5)
     */
    fun sendPartyInviteResult(session: SessionSender, result: Int, targetName: String)

    /**
     * 發送軍師資料（S:013-011）
     *
     * 登入時同步所有軍師。
     * 格式：<<playerId(8)>>
     */
    fun sendPartyAdviserData(session: SessionSender, adviserIds: List<Long>)

    /**
     * 發送組隊訊息（S:013-013）
     *
     * 格式：+kind(1)
     * 0=成功, 1=該隊滿, 2=本隊滿, 3=事件中, 6=解散
     */
    fun sendPartyMessage(session: SessionSender, kind: Int)

    /**
     * 發送換隊長通知（S:013-014）
     *
     * 格式：+leaderId(8) <<playerId(8)>>
     */
    fun sendPartyChangeLeader(session: SessionSender, newLeaderId: Long, memberIds: List<Long>)

    /**
     * 發送組隊換移動狀態（S:013-015）
     *
     * 格式：+playerId(8) +status(1)
     */
    fun sendPartyMoveStatus(session: SessionSender, playerId: Long, status: Int)

    // ── 武將系統 (mainKind=15, 19) ──

    /**
     * 發送主角隨身武將完整資料（S:015-008）
     *
     * 來源：protocal.lua protocolTable[15][8]
     * 登入時下發所有隨身武將的完整資料。
     */
    fun sendFollowNpcFullData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>)

    /**
     * 發送新增隨身武將（S:015-001）
     *
     * 格式：+playerId(8) +followIndex(1) +npcId(4) +canGrow(1) +showMsg(1)
     */
    fun sendFollowNpcAdd(session: SessionSender, playerId: Long, slotIndex: Int, npcId: Int, canGrow: Int, showMsg: Boolean)

    /**
     * 發送刪除隨身武將（S:015-002）
     *
     * 格式：+playerId(8) +followIndex(1) +showMsg(1)
     */
    fun sendFollowNpcRemove(session: SessionSender, playerId: Long, slotIndex: Int, showMsg: Boolean)

    /**
     * 發送馬車武將資料（S:015-010）
     *
     * 格式：<<+index(1) +npcId(2) +level(1) +hp(4) +nameLen(1) +name(Big5)>>
     */
    fun sendCartData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>)

    /**
     * 發送刪除馬車武將（S:015-011）
     *
     * 格式：+index(1)
     */
    fun sendCartRemove(session: SessionSender, slotIndex: Int)

    /**
     * Đăng nhập/khởi tạo võ tướng xuất chiến (S:019-001).
     * DVT client protocolTable[19][1] gọi Role.SetFightNpc(UInt16).
     */
    fun sendFollowNpcDeployInit(session: SessionSender, npcId: Int)

    /**
     * Võ tướng xuất chiến runtime (S:019-004).
     * DVT client protocolTable[19][4] cũng gọi Role.SetFightNpc(UInt16).
     */
    fun sendFollowNpcDeploy(session: SessionSender, npcId: Int)

    /**
     * 發送武將收回（S:019-002）
     *
     * 空封包
     */
    fun sendFollowNpcRecall(session: SessionSender)

    // ── 屬性修改 (mainKind=8) ──

    /**
     * 發送主角屬性修改 (S:008-001)
     *
     * 來源：protocal.lua protocolTable[8][1]
     * 格式：+attrKind(1) +sign(1) +value(4LE) +arg(4LE)
     *
     * @param attrKind 屬性種類（EAttribute）
     * @param value 屬性數值（絕對值）
     * @param isPositive true=正號(1) false=負號(2)
     * @param arg 附加參數（通常為 0）
     */
    fun sendSetAttribute(session: SessionSender, attrKind: Int, value: Int, isPositive: Boolean, arg: Int = 0)

    /** S:008-002: FollowNpc(4), index UInt16, attribute Byte, sign Byte, value/arg Int32. */
    fun sendFollowNpcAttribute(session: SessionSender, slot: Int, attrKind: Int, value: Int, arg: Int = 0)

    /**
     * 發送裝備加成更新（8 筆 S:008-001 + MaxHP/HP/MaxSP/SP）
     *
     * 穿脫裝備後呼叫。依序發送 8 個裝備屬性的新值，
     * 客戶端收到 EquipMaxHp/EquipHpx 等時會自動重算 MaxHP。
     * 最後補發 HP/SP 絕對值確保同步。
     *
     * @param session 目標連線
     * @param bonus 重新計算後的裝備加成總值
     * @param maxHp 重算後的 MaxHP
     * @param maxSp 重算後的 MaxSP
     * @param hp clamp 後的 HP
     * @param sp clamp 後的 SP
     */
    fun sendEquipBonusUpdate(
        session: SessionSender,
        bonus: EquipBonusData,
        maxHp: Long,
        maxSp: Int,
        hp: Long,
        sp: Int
    )

    // ── 戰鬥系統 (mainKind=11, 50, 51, 52, 53) ──

    /**
     * 發送戰鬥初始化 (S:011-250)
     *
     * 來源：protocal.lua protocolTable[11][250]
     * 格式：+terrain(1) +eventNo(2LE) +<<RoleAppear 資料>>
     */
    fun sendBattleInit(session: SessionSender, terrain: Int, eventNo: Int, roleData: ByteArray)

    /**
     * S:011-004 場景玩家戰鬥類型更新
     *
     * 來源：protocal.lua protocolTable[11][4]
     * 格式：<<+角色種類(1) +玩家ID(8 LE) +NPCID(2 LE) +戰鬥類型(1)>>
     */
    fun sendSceneWarTypeUpdate(session: SessionSender, entries: ByteArray)

    /**
     * S:011-005 參戰角色資訊（戰場舊玩家收到新參戰角色）
     *
     * 來源：protocal.lua protocolTable[11][5]
     * 格式：<<+戰鬥角色資料(?)>> — 同 BattleRoleData 格式
     */
    fun sendBattleJoinRoleAppear(session: SessionSender, roleData: ByteArray)

    /**
     * 發送戰鬥開始倒數 (S:052-001)
     *
     * 來源：protocal.lua protocolTable[52][1]
     * 格式：空封包
     */
    fun sendBattleCountdown(session: SessionSender)

    /**
     * 發送技能攻擊結果 (S:050-001)
     *
     * 來源：protocal.lua protocolTable[50][1]
     * 格式：已序列化的攻擊結果資料
     */
    fun sendBattleSkillResult(session: SessionSender, data: ByteArray)

    /**
     * 發送戰鬥中即時屬性修改 (S:051-001)
     *
     * 來源：protocal.lua protocolTable[51][1] → FightManager.RevChangeAttrib
     * 格式：+isRevive(1) +<<col(1) +row(1) +attrKind(1) +value(4LE)>>
     * attrKind: EAttribute.HP=25, SP=26 等
     */
    fun sendBattleAttrChange(session: SessionSender, data: ByteArray)

    /**
     * 發送結束戰鬥 (S:011-000)
     *
     * 來源：protocal.lua protocolTable[11][0]
     * 格式：+playerId(8LE) +npcIndex(2LE)
     */
    fun sendBattleEnd(session: SessionSender, playerId: Long, npcIndex: Int)

    /**
     * 發送角色離開戰鬥 (S:011-001)
     *
     * 來源：protocal.lua protocolTable[11][1]
     * 格式：+col(1) +row(1)
     */
    fun sendBattleExitFight(session: SessionSender, col: Int, row: Int)

    /**
     * 發送戰鬥狀態回復 (S:053-001)
     *
     * 格式：<<+col(1) +row(1) +statusKind(1) +skillId(2LE)>>
     */
    fun sendBattleRestoreStatus(session: SessionSender, data: ByteArray)

    /**
     * 發送戰鬥打飛 (S:053-003)
     *
     * 單擊傷害 >= maxHp 時觸發。客戶端播放飛出動畫並從棋盤移除。
     *
     * 格式：+col(1) +row(1)
     */
    fun sendBattleFlyOut(session: SessionSender, col: Int, row: Int)

    /**
     * 發送戰鬥使用物品結果 (S:050-002)
     *
     * 格式：+col(1) +row(1) +itemId(2LE) +count(1) +tgtCol(1) +tgtRow(1)
     */
    fun sendBattleUseItem(session: SessionSender, col: Int, row: Int, itemId: Int, count: Int, tgtCol: Int, tgtRow: Int)

    /**
     * 發送戰場類型 (S:011-010)
     *
     * 格式：+warStyle(1) +rounds(2LE) +limitType(1) +limitSwitch(4LE)
     */
    fun sendBattleFieldType(session: SessionSender, warStyle: Int, rounds: Int, limitType: Int, limitSwitch: Int)

    // ── PK 設定 (mainKind=33) ──

    /**
     * 發送要求 PK 回應 (S:033-001)
     *
     * 來源：protocal.lua protocolTable[33][1]
     * 格式：+結果(1)
     *
     * 結果碼：
     * 1=對方未開PK開關, 2=對方未開參戰開關, 3=對方戰鬥問答中,
     * 4=特殊戰鬥不能參戰, 5=城門戰不同盟, 6=城門戰不同軍團,
     * 7=對方住宿中, 8=此場景不可參戰, 9=同盟不可PK, 10=無敵狀態
     */
    fun sendPkResult(session: SessionSender, resultCode: Int)

    /**
     * 發送設定 PVP 開關 (S:033-002)
     *
     * 來源：protocal.lua protocolTable[33][2]
     * 格式：+PK開關(1) +參戰開關(1)
     */
    fun sendPkSwitch(session: SessionSender, pkEnabled: Boolean, joinEnabled: Boolean)

    /**
     * 發送戰鬥錯誤訊息 (S:011-003)
     *
     * 來源：protocal.lua protocolTable[11][3]
     * 格式：+種類(1)
     */
    fun sendBattleError(session: SessionSender, errorKind: Int)

    // ── MapNpc 系統 (mainKind=22) ──

    /**
     * 發送 MapNpc 初始化 (S:022-004)
     *
     * 來源：protocal.lua protocolTable[22][4]
     * 格式：+數量(2) <<+索引(2) +圖序(1) +戰鬥類型(1) +位置x(2) +位置y(2) +顯示種類(1) +隱藏時間(4)>>
     *
     * 客戶端收到後呼叫 MarkManager.ContinueNavigation()，場景載入必須此封包。
     *
     * @param npcs 該場景的 NPC 擺放列表
     */
    fun sendMapNpcInit(session: SessionSender, npcs: List<EveNpcPlacement>)

    /**
     * 組裝 MapNpc 移動封包 (S:022-002)
     *
     * 來源：protocal.lua protocolTable[22][2]
     * 格式：+索引(2 UInt16) +座標X(2 UInt16) +座標Y(2 UInt16)
     *
     * 客戶端收到後呼叫 `Role.mapNpcs[index]:Move(Vector2.New(x, y))`
     *
     * @return 組裝好的封包 ByteArray，供 broadcastToMap 使用
     */
    fun buildMapNpcMovePacket(npcIndex: Int, x: Int, y: Int): ByteArray

    // ── 戰鬥掉寶 (mainKind=53) ──

    /**
     * 發送戰鬥掉寶 (S:053-004)
     *
     * 來源：protocal.lua protocolTable[53][4] → FightManager.RevFlyTreasure
     * 格式：+物品ID(2) +掉寶列(1) +掉寶行(1) +得寶列(1) +得寶行(1)
     *
     * @param itemId 掉落物品 ID
     * @param fromCol 掉寶來源列（敵人位置）
     * @param fromRow 掉寶來源行
     * @param toCol 得寶目標列（玩家位置）
     * @param toRow 得寶目標行
     */
    fun sendBattleDrop(session: SessionSender, itemId: Int, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int)

    // ── 坐騎系統 (mainKind=79) ──

    /**
     * S:079-001 座騎資料（登入下發）
     *
     * 格式：+等級(1) +點數(2LE)*6 +裝備數量(1) <<+ThingData(35)>> +當前NPC ID(2LE)
     */
    fun sendMountData(session: SessionSender, data: tw.tsm.domain.valueobjects.MountData)

    /**
     * S:079-002 設定座騎等級
     *
     * 格式：+等級(1)
     */
    fun sendMountLevel(session: SessionSender, level: Int)

    /**
     * S:079-003 設定座騎點數
     *
     * 格式：+種類(1) +點數(2LE)
     */
    fun sendMountAttributePoint(session: SessionSender, kind: Int, points: Int)

    /**
     * S:079-004 穿上座騎裝備
     *
     * 格式：+背包索引(1)
     */
    fun sendMountEquip(session: SessionSender, bagIndex: Int)

    /**
     * S:079-005 卸下座騎裝備
     *
     * 格式：+裝備部位(1) +背包索引(1)
     */
    fun sendMountUnequip(session: SessionSender, fitType: Int, bagIndex: Int)

    /**
     * S:079-006 切換座騎
     *
     * 格式：+NPC ID(2LE)
     */
    fun sendMountSwitch(session: SessionSender, npcId: Int)

    /**
     * S:079-007 設定玩家座騎（廣播用）
     *
     * 格式：+玩家ID(8LE) +NPC ID(2LE)
     */
    fun sendPlayerMount(session: SessionSender, playerId: Long, npcId: Int)

    /**
     * S:079-008 坐騎數值
     *
     * 格式：+數量(1) <<+種類(1) +正負號(1) +數值(4LE) +參數(4LE)>>
     */
    fun sendMountAttributes(session: SessionSender, attrs: List<MountAttribute>)

    // ── 機關盒系統 (mainKind=65) ──

    /**
     * S:065-001 機關盒啟動成功
     *
     * 空封包。客戶端收到後 MachineBox.SetAutoFight(true)。
     */
    fun sendMacroBoxStart(session: SessionSender)

    /**
     * S:065-002 機關盒暫停
     *
     * 空封包。客戶端收到後 MachineBox.SetAutoFight(false) + 顯示字串 20730。
     */
    fun sendMacroBoxPause(session: SessionSender)

    /**
     * S:065-003 場景新增玩家小寵物
     *
     * 格式：+roleId(8B LE) +kind(1B)
     */
    fun sendMiniPetAppear(session: SessionSender, roleId: Long, kind: Int)

    /**
     * S:065-004 舊玩家小寵物列表
     *
     * 格式：迴圈{roleId(8B LE) kind(1B)}，直到資料結束
     */
    fun sendMiniPetList(session: SessionSender, pets: Map<Long, Int>)

    /**
     * S:065-005 開啟機關盒介面
     *
     * 空封包（客戶端已注解此功能）。
     */
    fun sendMacroBoxOpen(session: SessionSender)

    /**
     * S:065-006 機關盒晶石資料
     *
     * 格式：迴圈{index(1B) sparId(2B LE) count(2B LE)}
     */
    fun sendMacroBoxSparData(session: SessionSender, sparSlots: Map<Int, SparSlot>)

    /**
     * S:065-007 機關盒訊息
     *
     * 格式：+kind(1B) [1=字串80112, 2=字串80113]
     */
    fun sendMacroBoxMessage(session: SessionSender, kind: Int)

    /**
     * S:065-008 機關盒晶石擴充數量
     *
     * 格式：+count(1B) +showMsg(1B)
     */
    fun sendMacroBoxSparExtend(session: SessionSender, count: Int, showMsg: Boolean)

    /**
     * S:065-009 機關盒晶石移除訊息
     *
     * 空封包。客戶端顯示字串 80292。
     */
    fun sendMacroBoxSparRemoveMsg(session: SessionSender)

    /**
     * S:065-010 機關盒進行補給
     *
     * 戰鬥結束後發送，通知客戶端執行補品/HP/SP 補給。
     * 格式：+checkItem(1B bool) [0=不檢查補品不足回城, 1=需要檢查]
     */
    fun sendMacroBoxSupply(session: SessionSender, checkItem: Boolean)

    /**
     * S:065-011 經驗瓶資料
     *
     * 格式：+priority(1B) + 迴圈{index(1B) bottleId(2B LE) exp(4B LE)}
     */
    fun sendMacroBoxExpBottleData(session: SessionSender, priority: Int, bottles: Map<Int, ExpBottleSlot>)

    /**
     * S:065-012 更新經驗瓶
     *
     * 格式：+index(1B) +exp(4B LE)
     */
    fun sendMacroBoxUpdateExpBottle(session: SessionSender, index: Int, exp: Int)

    /**
     * S:065-013 設定經驗瓶成功
     *
     * 格式：+index(1B)
     */
    fun sendMacroBoxSetExpBottle(session: SessionSender, index: Int)

    /**
     * S:065-014 機關盒經驗瓶擴充數量
     *
     * 格式：+count(1B) +showMsg(1B)
     */
    fun sendMacroBoxExpExtend(session: SessionSender, count: Int, showMsg: Boolean)

    // ──── 成就系統（mainKind=82）────

    /**
     * S:082-001 完成成就回應
     *
     * 格式：+結果(1B) +成就ID(2B LE)
     * 結果：0=成功, 1=無資料, 2=已完成, 3=條件不通過, 4=資料錯誤
     * 注意：客戶端無論結果碼為何，都會讀取 result + id
     */
    fun sendAchievementComplete(session: SessionSender, result: Int, achievementId: Int)

    /**
     * S:082-002 成就領獎回應
     *
     * 格式：+結果(1B) [+成就ID(2B LE)（僅結果=0）]
     * 結果：0=成功, 1=失敗
     */
    fun sendAchievementReward(session: SessionSender, result: Int, achievementId: Int)

    // ── 信件系統 (mainKind=83) ──

    /**
     * S:083-001 新增信件
     *
     * 格式：+count(4B LE) <<+mailId(4B) +time(8B Double) +state(1B)
     *       +contentLen(2B LE) +content(Big5) +attachCount(1B)
     *       <<+kind(1B) +data(ThingData/NpcInfo)>>>>
     */
    fun sendMails(session: SessionSender, mails: List<MailRecord>)

    /**
     * S:083-002 領取信件確認
     *
     * 格式：+count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailTakeConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-003 刪除信件確認
     *
     * 格式：+count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailDeleteConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-004 已讀信件確認
     *
     * 格式：+count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailReadConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-005 信件錯誤碼
     *
     * 格式：+kind(1B)
     * 4=背包滿, 5=已領, 6=已刪, 7=過期, 8=金錢滿, 9=背包關閉
     */
    fun sendMailError(session: SessionSender, errorKind: Int)

    // ── 排行榜系統 (mainKind=88) ──

    /**
     * S:088-001 排行榜資料
     *
     * 格式：+leaderboardId(1B) +page(1B) +maxPage(1B) +myRank(1B)
     *       +infoCount(1B) <<+scoreParam(4B LE)>>
     *       +count(1B) <<+rank(1B) +state(1B) +roleId(8B LE) +nameLen(1B) +name(Big5)
     *       +level(1B) +element(1B) +turn3Element(1B) +turn(1B) +career(1B)
     *       +scoreCount(1B) <<+score(4B LE)>>>>
     */
    fun sendLeaderboardData(
        session: SessionSender,
        leaderboardId: Int, page: Int, maxPage: Int, myRank: Int,
        myScoreParams: List<Int>,
        entries: List<LeaderboardEntryDto>
    )

    // ── 好友系統 (mainKind=14) ──

    /**
     * S:014-003 好友邀請結果
     *
     * 格式：+nameLen(1B) +name(Big5) +result(1B)
     */
    fun sendFriendInviteResult(session: SessionSender, name: String, result: Int)

    /**
     * S:014-004 刪除好友通知
     *
     * 格式：+playerId(8B LE)
     */
    fun sendFriendRemove(session: SessionSender, playerId: Long)

    /**
     * S:014-005 新增好友（登入下發 + 即時新增）
     *
     * 格式：+count(2B LE) <<+playerId(8B LE) +名片 +在線(1B) +信任度(2B LE) +標記(1B) +結交時間(8B) +離線時間(8B)>>
     */
    fun sendFriendAdd(session: SessionSender, friends: List<FriendDataDto>)

    /**
     * S:014-006 好友信箱已滿
     */
    fun sendFriendMailFull(session: SessionSender)

    /**
     * S:014-007 好友訊息
     *
     * 格式：+kind(1B) [1-7 各種結果碼]
     */
    fun sendFriendMessage(session: SessionSender, kind: Int)

    /**
     * S:014-008 好友邀請
     *
     * 格式：+count(2B LE) <<+playerId(8B LE) +名片 +msg(1B)>>
     */
    fun sendFriendInvites(session: SessionSender, invites: List<FriendInviteDto>)

    /**
     * S:014-009 刪除好友邀請
     *
     * 格式：+playerId(8B LE)
     */
    fun sendFriendRemoveInvite(session: SessionSender, playerId: Long)

    /**
     * S:014-012 更新好友功能標記
     *
     * 格式：+count(1B) <<+playerId(8B LE) +flag(1B)>>
     */
    fun sendFriendFunctionFlags(session: SessionSender, updates: List<Pair<Long, Int>>)

    /**
     * S:014-013 更新好友信任度
     *
     * 格式：+playerId(8B LE) +friendly(2B LE)
     */
    fun sendFriendFriendly(session: SessionSender, playerId: Long, friendly: Int)

    /**
     * S:014-014 玩家資訊
     *
     * 格式：+playerId(8B) +名片 +maxHp(4B) +maxSp(2B) +六圍(4B)×6
     *       +equipCount(1B) <<+ThingData>> +outfitCount(1B) <<+itemId(2B)>>
     *       +titleId(2B) +marriage(1B)
     */
    fun sendPlayerInfo(session: SessionSender, data: PlayerInfoDto)

    /**
     * S:014-015 收禮結果
     *
     * 格式：+result(1B) [1=成功, 2-6 各種失敗]
     */
    fun sendGiftReceiveResult(session: SessionSender, result: Int)

    /**
     * S:014-016 更新好友在線狀態
     *
     * 格式：+playerId(8B LE) +online(1B)
     */
    fun sendFriendOnlineStatus(session: SessionSender, playerId: Long, online: Boolean)

    /**
     * S:014-017 更新歷史好友最大數量
     *
     * 格式：+count(4B LE)
     */
    fun sendMaxRecordFriendCount(session: SessionSender, count: Int)

    // ──── RoleCount（mainKind=85）────

    /**
     * S:085-002 更新角色計數器（多筆）
     *
     * 格式：+數量(2B LE) <<+索引(2B LE) +數值(4B LE unsigned) +上限(4B LE signed)>>
     */
    fun sendRoleCountUpdate(session: SessionSender, counts: Map<Int, Pair<Int, Int>>)
    /** S:023-069 玩家轉職/轉生外觀與 turn/career 同步。 */
    fun sendPlayerCareerChange(
        session: SessionSender, playerId: Long, hair: Int,
        color1: Int, color2: Int, turn: Int, career: Int
    )

}

/**
 * 坐騎屬性值 — 用於 S:079-008
 */
/**
 * 排行榜條目 DTO — 用於 S:088-001
 */
data class LeaderboardEntryDto(
    val rank: Int,
    /** 0=不變, 1=上升, 2=下降 */
    val state: Int,
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val scoreParams: List<Int>
)

/**
 * 好友資料 DTO — 用於 S:014-005
 */
data class FriendDataDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    val online: Boolean,
    val friendly: Int,
    val functionFlag: Int,
    val addTime: Double,
    val offlineTime: Double
)

/**
 * 好友邀請 DTO — 用於 S:014-008
 */
data class FriendInviteDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int
)

/**
 * 玩家資訊 DTO — 用於 S:014-014
 */
data class PlayerInfoDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    val maxHp: Long,
    val maxSp: Int,
    val intelligence: Int,
    val atk: Int,
    val def: Int,
    val hpx: Int,
    val spx: Int,
    val agi: Int,
    val equips: List<ThingData>,
    val outfitItemIds: List<Int> = emptyList(),
    val titleId: Int,
    val marriage: Int = 0
)

data class MountAttribute(
    /** EAttribute 種類 */
    val kind: Int,
    /** 1=正, 2=負 */
    val sign: Int,
    /** 數值 */
    val value: Int,
    /** 參數 */
    val arg: Int = 0
)

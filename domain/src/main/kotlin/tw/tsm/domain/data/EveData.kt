package tw.tsm.domain.data

/**
 * NPC 事件系統值物件 — 對應 Eve.emg 資料結構
 *
 * 來源：客戶端 Lua 路徑 `Data/Eve/`
 *   - Eve_NpcData.lua        → [EveNpcPlacement]
 *   - Eve_NpcEventData.lua   → [NpcEventData]、[EveCondition]、[EveResult]
 */

/**
 * 事件結果 — 對應 Eve_NpcEventData.lua ResultArray 單筆
 *
 * 欄位與 S:020-001 封包 1:1 對應（15 bytes）。
 *
 * @param resultGroupNo  S:020-001 resultGroupNo (2B)
 * @param resultNo       S:020-001 resultNo (1B)
 * @param resultType     S:020-001 resultType (1B)：0=物品效果 1=對話 2=門 3=戰鬥 4=NPC移動 5=動畫 6=互動
 * @param resultClass    S:020-001 resultClass (1B)：3=NPC 7=玩家
 * @param parameter      S:020-001 parameter (2B)：NPC clickId（用於面向旋轉）
 * @param parameterStyle S:020-001 parameterStyle (1B)
 * @param resultValue    S:020-001 resultValue (4B)
 * @param resultMeanNo   S:020-001 resultMeanNo (2B)：對話 ID（客戶端查 talkDatas[]）
 */
data class EveResult(
    val resultGroupNo: Int,
    val resultNo: Int,
    val resultType: Int,
    val resultClass: Int,
    val parameter: Int,
    val parameterStyle: Int,
    val resultValue: Int,
    val resultMeanNo: Int
)

/**
 * 事件條件（含 ResultArray）— 對應 Eve_NpcEventData.lua ConditionArray 單筆
 *
 * @param conditionNo        條件序號
 * @param conditionClass     條件分類（0=無條件；2=任務步驟；其他=待確認語義）
 * @param conditionParameter 條件參數（conditionClass=2 時為任務 ID）
 * @param conditionParameterStyle 條件參數樣式（語義依 conditionClass 而異）
 * @param conditionOps       條件運算子（0/1=等於 2=小於 3=小等 4=大於 5=大等 6=不等）
 * @param conditionValue     條件比較值
 * @param conditionSubItem   條件子項目（UInt16，語義待確認）
 * @param toResult           若此條件符合，跳到哪個結果位置（AND 鏈結構用）
 * @param andNum             與後 N 個條件 AND（>0 表示此條件是 AND 鏈的前置）
 * @param results            符合此條件時要執行的結果列表
 */
data class EveCondition(
    val conditionNo: Int,
    val conditionClass: Int,
    val conditionParameter: Int,
    val conditionParameterStyle: Int,
    val conditionOps: Int,
    val conditionValue: Int,
    val conditionSubItem: Int,
    val toResult: Int,
    val andNum: Int,
    val results: List<EveResult>
)

/**
 * NPC 事件資料 — 對應 Eve_NpcEventData.lua
 *
 * @param eveNo      事件編號（NpcData.events[] 中的 EveNo）
 * @param whenHappen 觸發時機旗標（4 個 boolean）：
 *                   [0]=OnClickObject（ClickNpc）、[1]=OnStrokeObject（BumpNpc）、
 *                   [2]=OnIntoObjectArea（ScanNpc）、[3]=OnSerStrokeObject（伺服器碰撞）
 * @param conditions 條件列表（依序評估，第一個符合的條件會被執行）
 */
data class NpcEventData(
    val eveNo: Int,
    val whenHappen: List<Boolean>,
    val conditions: List<EveCondition>
)

/**
 * NPC 擺放資料 — 對應 Eve_NpcData.lua
 *
 * @param id     clickId（客戶端發送觸發封包時附帶的索引）
 * @param npcId  對應 Npc_C.dat 的 NPC 種類 ID
 * @param events 此 NPC 關聯的事件 EveNo 列表
 * @param x      NPC 座標 X（用於 S:022-004 初始化和伺服器端距離驗證）
 * @param y      NPC 座標 Y
 * @param close  NPC 是否關閉（不顯示）
 * @param motionType    巡邏類型：1=站立 2=隨機走 3=範圍走 4=路徑走
 * @param motionBack    motionType=4 時：1=停止 2=循環
 * @param motionCycleNum 路徑循環次數（0=無限）
 * @param direction     初始朝向（0-7，逆時鐘）
 * @param motionSuspendMS 每個路點停留時間（毫秒）
 * @param motionSpeedLv 移動速度等級（1=慢 2=中 3=快）
 * @param motionNodes   巡邏路點座標列表 (x, y)
 * @param traceRadius   追擊半徑（>0 時 NPC 會追擊玩家）
 */
data class EveNpcPlacement(
    val id: Int,
    val npcId: Int,
    val events: List<Int>,
    val x: Int = 0,
    val y: Int = 0,
    val close: Boolean = false,
    val motionType: Int = 1,
    val motionBack: Int = 1,
    val motionCycleNum: Int = 0,
    val direction: Int = 0,
    val motionSuspendMS: Int = 0,
    val motionSpeedLv: Int = 1,
    val motionNodes: List<Pair<Int, Int>> = emptyList(),
    val traceRadius: Int = 0,
    /** NPC 成長性：0=不可成長（橘色名字）、1=可成長（藍色名字） */
    val canGrow: Int = 0
)

/**
 * 傳送點擺放資料 — 對應 Eve_DoorData.lua
 *
 * Door 和 NPC 共享同一個 NpcEventData 表，但各自有獨立的 ID 空間。
 * 客戶端觸發 Door 時送 C:020-004（ClickDoor）或 C:020-008（MeetDoor），
 * triggerID = Door.id（非 NPC clickId）。
 *
 * @param id     傳送點 ID（客戶端 DoorData.id）
 * @param events 此傳送點關聯的事件 EveNo 列表
 * @param x      傳送點像素座標 X（UInt16，用於伺服器端距離驗證）
 * @param y      傳送點像素座標 Y（UInt16，用於伺服器端距離驗證）
 */
data class EveDoorPlacement(
    val id: Int,
    val events: List<Int>,
    val x: Int = 0,
    val y: Int = 0
)

/**
 * 場景資訊資料 — 對應 Eve_SceneInfoData.lua
 *
 * resultType=2（Door 傳送）時，result.parameter = 此表的 eveNo 鍵值。
 * 伺服器查詢此表取得目標場景 ID 和出現座標。
 *
 * @param eveNo          事件編號（SceneInfo 表的 key）
 * @param backgroundNo   目標場景 ID
 * @param playerAppearX  玩家出現 X 座標
 * @param playerAppearY  玩家出現 Y 座標
 * @param direction      面向方向
 */
data class EveSceneInfo(
    val eveNo: Int,
    val backgroundNo: Int,
    val playerAppearX: Int,
    val playerAppearY: Int,
    val direction: Int
)

/**
 * 戰鬥敵人配置 — 對應 Eve_FightData.lua ReadEnemy()
 *
 * @param no          敵人編號
 * @param npcId       NPC 種類 ID（對應 Npc_C.dat）
 * @param locationPos 棋盤位置（相對於敵方側：0-4=前排 col2 row0-4，5-9=後排 col3 row0-4）
 * @param ai          AI 行為模式
 */
data class EveFightEnemy(
    val no: Int,
    val npcId: Int,
    val locationPos: Int,
    val ai: Int
) {
    /** 棋盤列（敵方前排=0, 後排=1；左側面朝右） */
    val col: Int get() = locationPos / 5
    /** 棋盤行（0-4） */
    val row: Int get() = locationPos % 5
}

/**
 * 事件戰鬥資料 — 對應 Eve_FightData.lua FightData.New()
 *
 * resultType=3 時，EveResult.resultMeanNo = 此表的 eveNo 鍵值。
 * 伺服器查詢此表取得敵方陣容和戰鬥設定。
 *
 * @param eveNo        事件編號（FightData 表的 key）
 * @param fightType    戰鬥型態
 * @param linkToResult 連接結果（戰鬥結束後跳轉用）
 * @param leftEnemies  左側敵人陣列（主要敵方群組）
 * @param rightEnemies 右側敵人陣列（次要群組，通常為空）
 * @param fightLimit   戰鬥限制旗標
 */
data class EveFightData(
    val eveNo: Int,
    val fightType: Int,
    val linkToResult: Int,
    val leftEnemies: List<EveFightEnemy>,
    val rightEnemies: List<EveFightEnemy>,
    val fightLimit: Long
)

/**
 * Surface 句子 — 對應 Eve_SurfaceData.lua sentence
 *
 * @param dataId    DB 編號（Talk.dat / Item.dat / Npc.dat 索引）
 * @param dataKind  資料來源（1=Talk.dat 2=Item.dat 3=Npc.dat）
 * @param style     顯示方式（1=Label/文字 2=Button/按鈕 3=ListItem/選單選項 4=CloseButton/關閉按鈕）
 * @param canCut    是否換行（僅 Button 使用）
 */
data class EveSentence(
    val dataId: Int,
    val dataKind: Int,
    val style: Int,
    val canCut: Boolean
)

/**
 * Surface 互動資料 — 對應 Eve_SurfaceData.lua
 *
 * resultType=6 時，EveResult.resultMeanNo = 此表的 id 鍵值。
 * 客戶端查詢本地 surfaceDatas[resultMeanNo] 顯示 UI。
 *
 * @param id            互動編號
 * @param sentenceCount Sentence 數量（已扣除被忽略的 dataId=20393）
 * @param optionIndex   第一個選項在 sentences[] 中的 index（1-based）
 * @param optionCount   選項個數
 * @param optionMode    選單觸發模式（0=一般對話 1=買賣視窗 2=招兵生產 3=預留）
 * @param sentences     句子列表（1-based index，跳過 dataId=20393 的洞）
 */
data class EveSurfaceData(
    val id: Int,
    val sentenceCount: Int,
    val optionIndex: Int,
    val optionCount: Int,
    val optionMode: Int,
    val sentences: Map<Int, EveSentence>
)

/**
 * GroupData — 對應 Eve_GroupData.lua
 *
 * 結果分組機制：`EveResult.resultGroupNo > 0` 時引用此表。
 * 伺服器依 `ProbabilityRateAy` 從 `MemberNoAy` 中選一個 `resultNo`，
 * 只保留被選中的結果。
 *
 * 客戶端 `EventData.lua` 儲存 `self.Group[group.EveNo] = group`，
 * 但 EventManager/EventHandler 從不使用 → 純伺服器端邏輯。
 *
 * @param eveNo            GroupData 自身 ID（= 結果的 resultGroupNo 引用值）
 * @param eventNo          所屬 NpcEvent 事件編號
 * @param conditionNo      所屬條件編號
 * @param useMode          選擇模式
 * @param memberNoAy       成員的 resultNo 列表
 * @param probabilityRateAy 每個成員的機率權重
 * @param pickMember       預選成員（0=無）
 */
data class EveGroupData(
    val eveNo: Int,
    val eventNo: Int,
    val conditionNo: Int,
    val useMode: Int,
    val memberNoAy: List<Int>,
    val probabilityRateAy: List<Int>,
    val pickMember: Int
)

/**
 * 場景事件資料（NPC 擺放 + 傳送點擺放 + 事件定義 + 場景資訊 + 戰鬥資料 + 互動資料 + 分組資料）
 *
 * @param npcs         clickId → EveNpcPlacement
 * @param doors        doorId  → EveDoorPlacement
 * @param npcEvents    eveNo   → NpcEventData
 * @param sceneInfos   eveNo   → EveSceneInfo（Door 傳送目的地）
 * @param fightDatas   eveNo   → EveFightData（事件戰鬥敵方配置）
 * @param surfaceDatas id      → EveSurfaceData（Surface 互動定義）
 * @param groupDatas   eveNo   → EveGroupData（結果分組選擇）
 */
data class SceneEveData(
    val npcs: Map<Int, EveNpcPlacement>,
    val doors: Map<Int, EveDoorPlacement>,
    val npcEvents: Map<Int, NpcEventData>,
    val sceneInfos: Map<Int, EveSceneInfo>,
    val fightDatas: Map<Int, EveFightData> = emptyMap(),
    val surfaceDatas: Map<Int, EveSurfaceData> = emptyMap(),
    val groupDatas: Map<Int, EveGroupData> = emptyMap()
)

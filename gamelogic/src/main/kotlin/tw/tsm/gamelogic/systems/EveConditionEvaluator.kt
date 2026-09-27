package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition

private val logger = KotlinLogging.logger {}

/**
 * 玩家事件狀態快照 — 條件評估所需的所有資料
 *
 * 設計為不可變值物件，由呼叫者（NpcEventHandler）建構後傳入。
 * 評估器本身不查詢任何外部系統。
 *
 * @param missionSteps   任務 ID → 當前步驟（pStyle=1/2 條件用）
 * @param missionFlags   旗標 ID → 值（pStyle=3 條件用：已完成任務的 bitId）
 * @param bagItems       物品 ID → 持有數量
 * @param storedItems    錢莊/寶庫物品 ID → 持有數量（conditionClass=16 用）
 * @param bagUsedSlots   背包已使用格數（conditionClass=7 param=2 用）
 * @param bagCapacity    目前背包容量（含 RoleCount 擴充格）
 * @param equipItems     已裝備的物品 ID 集合
 * @param sex            角色性別（conditionClass=7 param=5 用，對應 pStyle=1/2）
 * @param level          角色等級
 * @param rebornCount    轉生次數
 * @param isMounted      是否正在騎乘坐騎（conditionClass=7 param=16 用）
 * @param lastSurfaceId  最後顯示的 Surface ID（conditionClass=10 比對用）
 * @param lastChoiceCode 最後選擇碼（conditionClass=10 比對用）
 * @param battleResult   戰鬥結果（0=無 1=勝 2=敗 3=逃，conditionClass=8 比對用）
 * @param completedEveCounts 此場景已完成事件計數（eveNo → 完成次數，conditionClass=12 比對用）
 * @param currentEveNo  當前正在評估的 eveNo（conditionClass=12 param=0 用）
 * @param followNpcIds   隨身武將 NPC ID 集合（conditionClass=9 武將持有檢查用）
 * @param innNpcIds      客棧武將 NPC ID 集合（conditionClass=7 param=6 pStyle=3 用）
 * @param cartNpcIds     馬車武將 NPC ID 集合（conditionClass=7 param=6 pStyle=2 用）
 * @param roleCountValues RoleCount 角色統計計數（conditionClass=18 部分索引用）
 * @param skillLevels    技能 ID → 技能等級（conditionClass=4 比對用）
 * @param gold           身上金幣（conditionClass=5 金錢條件用）
 * @param npcSecondReincarnationStatus 武將二轉入口狀態碼（0=可開啟，-1=無符合資格武將）
 */
data class PlayerEventState(
    val missionSteps: Map<Int, Int> = emptyMap(),
    val missionFlags: Map<Int, Int> = emptyMap(),
    val bagItems: Map<Int, Int> = emptyMap(),
    val storedItems: Map<Int, Int> = emptyMap(),
    val bagUsedSlots: Int = 0,
    val bagCapacity: Int = 0,
    val equipItems: Set<Int> = emptySet(),
    val sex: Int = 0,
    val level: Int = 0,
    val rebornCount: Int = 0,
    val isMounted: Boolean = false,
    val lastSurfaceId: Int = -1,
    val lastChoiceCode: Int = -1,
    val battleResult: Int = 0,
    val completedEveCounts: Map<Int, Int> = emptyMap(),
    val currentEveNo: Int = 0,
    val followNpcIds: Set<Int> = emptySet(),
    val followPetCount: Int = 0,
    val maxFollowPets: Int = 4,
    val innNpcIds: Set<Int> = emptySet(),
    val cartNpcIds: Set<Int> = emptySet(),
    val roleCountValues: Map<Int, Int> = emptyMap(),
    val skillLevels: Map<Int, Int> = emptyMap(),
    val gold: Int = 0,
    val npcSecondReincarnationStatus: Int = -1,
)

/**
 * Eve 條件評估器 — 純函數，不持有任何狀態
 *
 * 來源：客戶端不做條件評估（全部由伺服器判斷），
 * 條件語義從 Eve_NpcEventData.lua 欄位命名 + 遊戲行為推導。
 *
 * 已驗證 conditionClass：0, 1, 2, 3(場景物件), 4, 5(金錢), 7, 8, 9(武將持有), 10, 11(收驚), 12, 15(玩家/武將轉生入口), 18(部分 RoleCount)
 * 未實作（保守 false）：6, 13, 14, 17
 */
object EveConditionEvaluator {

    /**
     * 評估單一條件是否成立
     *
     * @param cond  Eve.emg 條件資料
     * @param state 玩家當前狀態快照
     * @return true = 條件成立
     */
    fun evaluate(cond: EveCondition, state: PlayerEventState): Boolean {
        return when (cond.conditionClass) {
            0 -> true  // 無條件，永遠匹配

            1 -> {
                // 背包物品持有檢查：param=itemId
                // ops=2, val=N → qty > N（有物品，例如 val=0 → qty > 0，val=2 → qty > 2）
                // ops=1, val=N → qty < N（缺少物品，例如 val=1 → qty < 1 即 qty == 0）
                // ops=0, val=N → qty == N
                // ops=5, val=0 → qty == 0（無物品）
                val qty = state.bagItems[cond.conditionParameter] ?: 0
                val result = when (cond.conditionOps) {
                    0 -> qty == cond.conditionValue
                    1 -> qty < cond.conditionValue
                    2 -> qty > cond.conditionValue
                    5 -> if (cond.conditionValue == 0) qty == 0 else qty >= cond.conditionValue
                    6 -> qty != cond.conditionValue
                    else -> compareStep(qty, cond.conditionOps, cond.conditionValue)
                }
                logger.debug { "class=1 itemId=${cond.conditionParameter} qty=$qty ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            2 -> {
                // 任務條件：param=missionId, ops=比較, val=目標值
                // pStyle=3 → 同時檢查任務步驟與旗標，取較大值
                //   進行中任務：step=1, flag=0 → actual=1
                //   已完成任務：step=0, flag=1 → actual=1
                //   未開始任務：step=0, flag=0 → actual=0
                // pStyle=1/2 → 僅檢查任務步驟
                val step = state.missionSteps[cond.conditionParameter] ?: 0
                val actual = if (cond.conditionParameterStyle == 3) {
                    val flag = state.missionFlags[cond.conditionParameter] ?: 0
                    maxOf(step, flag)
                } else {
                    step
                }
                val checkType = if (cond.conditionParameterStyle == 3) "step|flag" else "step"
                val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=2 missionId=${cond.conditionParameter} $checkType=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            3 -> {
                // 場景物件可互動狀態：Eve 樣本固定為 pStyle=4/ops=5/value=0/subItem=0。
                val result = cond.conditionParameter > 0 &&
                    cond.conditionParameterStyle == 4 &&
                    cond.conditionOps == 5 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=3 非已知場景物件條件 param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            4 -> {
                val skillLevel = state.skillLevels[cond.conditionParameter] ?: 0
                val result = compareStep(skillLevel, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=4 skillId=${cond.conditionParameter} level=$skillLevel ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            5 -> {
                // Eve 金錢條件：常見樣本為 ops=2,value=金額-1，表示金幣達到扣款門檻。
                val result = evaluateMoneyCondition(state.gold, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=5 gold=${state.gold} ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            7 -> {
                // 通用屬性：param 決定子類型
                val actual: Int? = when (cond.conditionParameter) {
                    0 -> state.level               // 等級
                    2 -> {
                        // 背包空間分支：pStyle=1 背包已滿；pStyle=2 背包有空位；pStyle=4 比較剩餘格數。
                        evaluateBagSpaceCondition(cond, state)
                    }
                    3 -> {
                        // 裝備物品檢查：val=itemId，有裝備回傳 itemId，無回傳 0
                        if (cond.conditionValue in state.equipItems) cond.conditionValue else 0
                    }
                    5 -> {
                        // 武將空間分支：pStyle=1 隨身武將已滿(>=4)；pStyle=2 隨身武將有空位(<4)；pStyle=4 剩餘武將格數
                        evaluatePetSpaceCondition(cond, state)
                    }
                    6 -> {
                        // 武將儲存位置檢查：pStyle=1 隨身 / 2 馬車 / 3 客棧，val=npcId
                        val npcId = cond.conditionValue
                        val npcSet = when (cond.conditionParameterStyle) {
                            1 -> state.followNpcIds
                            2 -> state.cartNpcIds
                            3 -> state.innNpcIds
                            else -> emptySet()
                        }
                        if (npcId in npcSet) npcId else 0
                    }
                    16 -> {
                        // 坐騎狀態：Eve 樣本固定 subItem=1 時接「請你下馬」對話。
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 1 &&
                            state.isMounted
                        ) 0 else null
                    }
                    else -> null
                }
                if (actual == null) {
                    logger.warn {
                        "conditionClass=7 未實作 param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    return false
                }
                // class=7 採正向比較：actual [ops] conditionValue。
                val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=7 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            8 -> {
                // 戰鬥結果：pStyle=1 勝利 / 2 敗北 / 3 逃跑
                val result = cond.conditionParameterStyle == state.battleResult
                logger.debug { "class=8 pStyle=${cond.conditionParameterStyle} battleResult=${state.battleResult} → $result" }
                result
            }

            9 -> {
                // 武將持有檢查：conditionValue=npcId
                // 模式同 class=7 param=3（裝備檢查）：有 → 回傳 npcId，無 → 回傳 0
                // 例：cls=9 v=22081 ops=5 → 有武將: 22081>=22081=true / 無: 0>=22081=false
                if (cond.conditionParameter != 0) {
                    logger.warn {
                        "conditionClass=9 進階武將條件未實作 param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    return false
                }
                val npcId = cond.conditionValue
                val actual = if (npcId in state.followNpcIds) npcId else 0
                val result = compareStep(actual, cond.conditionOps, npcId)
                logger.debug { "class=9 npcId=$npcId hasPet=${npcId in state.followNpcIds} ops=${cond.conditionOps} → $result" }
                result
            }

            10 -> {
                // 對話選項選擇：param=surfaceId, pStyle=choiceCode
                val result = state.lastSurfaceId == cond.conditionParameter &&
                             state.lastChoiceCode == cond.conditionParameterStyle
                logger.debug { "class=10 surfaceId=${cond.conditionParameter} code=${cond.conditionParameterStyle} lastSurface=${state.lastSurfaceId} lastCode=${state.lastChoiceCode} → $result" }
                result
            }

            11 -> {
                // 收驚婆婆流程確認：Eve 樣本固定為 param=0/pStyle=1/ops=0/value=0。
                val result = cond.conditionParameter == 0 &&
                    cond.conditionParameterStyle == 1 &&
                    cond.conditionOps == 0 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=11 非已知收驚條件 param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            12 -> {
                // 場景已完成事件數（param=0 為場景總完成事件數，否則為特定 eveNo 完成數）
                val value = if (cond.conditionParameter == 0) {
                    state.completedEveCounts.values.count { it > 0 }
                } else {
                    state.completedEveCounts[cond.conditionParameter] ?: 0
                }
                // 正向比較（與 class=2 一致）：compareStep(actual, ops, expected)
                val result = compareStep(value, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=12 param=${cond.conditionParameter} eveNo=${state.currentEveNo} count=$value ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            14 -> {
                logger.warn { "conditionClass=14 語意待驗證 param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                false
            }

            15 -> evaluateReincarnationCondition(cond, state)

            16 -> {
                if (cond.conditionParameterStyle != 1 || cond.conditionSubItem != 0) {
                    logger.warn {
                        "conditionClass=16 未知形狀 param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                    return false
                }
                val qty = state.storedItems[cond.conditionParameter] ?: 0
                val result = compareStep(cond.conditionValue, cond.conditionOps, qty)
                logger.debug {
                    "class=16 storedItemId=${cond.conditionParameter} qty=$qty " +
                        "ops=${cond.conditionOps} val=${cond.conditionValue} → $result"
                }
                result
            }

            18 -> {
                if (!isKnownRoleCountIndex(cond.conditionParameter)) {
                    logger.warn { "conditionClass=18 非已知 RoleCount 索引 param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                    false
                } else {
                    val actual = state.roleCountValues[cond.conditionParameter] ?: 0
                    val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                    logger.debug { "class=18 roleCount=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                    result
                }
            }

            else -> {
                // 未實作 conditionClass — WARN 級別方便診斷
                logger.warn { "未實作 conditionClass=${cond.conditionClass} param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                false
            }
        }
    }

    private fun isKnownRoleCountIndex(index: Int): Boolean =
        // 1=桃園結義對話計數、189=暑假作業活動計數（resultClass=14 寫入端已鑑定，見 docs/eve-gap-analysis.md）
        index in setOf(1, 100, 101, 102, 108, 109, 189, 192, 668, 822)

    private fun evaluateBagSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        if (state.bagCapacity <= 0) return null

        val freeSlots = (state.bagCapacity - state.bagUsedSlots).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    private fun evaluatePetSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        val maxPets = if (state.maxFollowPets > 0) state.maxFollowPets else PetSystem.MAX_FOLLOW
        val currentPets = if (state.followPetCount > 0) state.followPetCount else state.followNpcIds.size
        val freeSlots = (maxPets - currentPets).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    private fun evaluateMoneyCondition(gold: Int, ops: Int, value: Int): Boolean =
        when (ops) {
            1 -> gold == value
            2 -> gold > value
            4 -> gold >= value
            5 -> gold <= value
            6 -> gold != value
            else -> compareStep(gold, ops, value)
        }

    private fun evaluateReincarnationCondition(cond: EveCondition, state: PlayerEventState): Boolean {
        if (cond.conditionParameterStyle != 0 || cond.conditionSubItem != 0) {
            logger.warn {
                "conditionClass=15 非已確認轉生條件 param=${cond.conditionParameter} " +
                    "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                    "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
            }
            return false
        }

        val actual = when (cond.conditionParameter) {
            0 -> state.rebornCount
            1 -> state.npcSecondReincarnationStatus
            else -> null
        }
        if (actual == null) {
            logger.warn {
                "conditionClass=15 未實作 param=${cond.conditionParameter} " +
                    "ops=${cond.conditionOps} val=${cond.conditionValue} → false"
            }
            return false
        }

        val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
        logger.debug { "class=15 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
        return result
    }

    /**
     * 比較運算
     *
     * Eve 二進制條件 ops 對照：
     * 0→== / 1→< / 2→> / 3→<= / 4→>= / 5→>= / 6→!=
     */
    fun compareStep(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0 -> actual == expected
        1 -> actual < expected
        2 -> actual > expected
        3 -> actual <= expected
        4 -> actual >= expected
        5 -> actual >= expected
        6 -> actual != expected
        else -> {
            logger.warn { "未知 ops=$ops actual=$actual expected=$expected → true" }
            true
        }
    }
}

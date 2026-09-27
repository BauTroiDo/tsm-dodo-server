package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.enums.BattleStatus

/**
 * 戰鬥參戰角色 — 對應 `FightField.lua` fightHum[]
 *
 * 代表棋盤上的一個戰鬥單位（玩家、武將、或怪物）。
 * 使用普通 class 而非 data class，因為：
 * - IntArray 在 data class 中破壞 equals/hashCode 語義
 * - @Volatile 在 data class.copy() 後失效
 * - 此類別以 roleId 作為唯一身份標識
 */
class BattleFighter(
    /** 角色 ID（玩家=characterId, 怪物=自動生成負數 ID） */
    val roleId: Long,
    /** 角色種類（EHuman） */
    val kind: Int,
    /** 主人 ID（玩家自己=0，武將=玩家 ID） */
    val masterId: Long = 0,
    /** NPC 地圖索引（怪物用） */
    val mapNpcIdx: Int = 0,

    // ── 名稱/外觀 ──
    val name: String,
    val npcId: Int = 0,

    // ── 棋盤位置 ──
    var col: Int,
    var row: Int,

    // ── 戰鬥屬性 ──
    var maxHp: Long,
    var maxSp: Long,
    var hp: Long,
    var sp: Long,
    var level: Int,
    var upgradeLv: Int = 0,
    var element: Int,
    var atk: Int,
    var def: Int,
    var agi: Int,
    var intelligence: Int,

    // ── 技能（最多 3 個） ──
    val skills: IntArray = intArrayOf(),
    /** Skill level snapshot; needed by SkillData:GetFightArea(skillLv). */
    val skillLevels: Map<Int, Int> = emptyMap(),

    // ── 玩家專屬外觀資料（kind=Player 時使用） ──
    var sex: Int = 0,
    var face: Int = 0,
    var hair: Int = 0,
    var color1: Int = 0,
    var color2: Int = 0,
    var turn: Int = 0,
    var career: Int = 0,
    var turn3Element: Int = 0,
    var specialEquipItemId: Int = 0,
    var equipItemIds: IntArray = intArrayOf(),
    var outfitItemIds: IntArray = intArrayOf(),

    // ── 戰鬥狀態 ──
    var isDead: Boolean = false,
    /** 是否被打飛（超殺死亡，無法戰場復活） */
    var isFlyOut: Boolean = false,
    /** 本回合是否處於防禦狀態（KERO: dest.def → 傷害減半） */
    var isDefending: Boolean = false,
    var statusEffect: BattleStatus = BattleStatus.NONE,
    var statusRounds: Int = 0,
    /** Skill that applied the current status; echoed when the status is restored. */
    var statusSkillId: Int = 0,
    /** Battle round in which the current status was inflicted. */
    var statusAppliedRound: Int = -1,
    /** Independent client status groups (Seal/Vary/Faint/Spec/Strong). */
    val groupedStatuses: MutableMap<Int, GroupedBattleStatus> = mutableMapOf(),

    /** 本回合是否已行動（跨執行緒：Netty 寫入 / 協程讀取） */
    @Volatile var hasActed: Boolean = false,

    /** 本回合選擇的行動（跨執行緒：Netty 寫入 / 協程讀取） */
    @Volatile var pendingAction: BattleAction? = null
) {
    /** 是否為玩家方（列 2-3，右側） */
    val isPlayerSide: Boolean get() = col >= 2

    /** 是否為敵方（列 0-1，左側） */
    val isEnemySide: Boolean get() = col < 2

    /** 是否為真人玩家（PLAYER 或 PLAYERS，用於 PVP 區分人與 NPC） */
    val isHumanPlayer: Boolean get() = kind == EHuman.PLAYER || kind == EHuman.PLAYERS

    /** 是否由玩家操控（包含武將等 masterID 綁定角色，客戶端 FindMasterID 判斷用） */
    val isPlayerControlled: Boolean get() = isHumanPlayer || kind == EHuman.FOLLOW_NPC

    /** 是否存活 */
    val isAlive: Boolean get() = !isDead && hp > 0

    /** 是否被控場（無法行動） */
    val isStunned: Boolean get() = groupedStatuses.values.any { BattleStatus.isStunStatus(it.status) } || BattleStatus.isStunStatus(statusEffect)

    fun setGroupedStatus(group: Int, status: BattleStatus, rounds: Int, skillId: Int, appliedRound: Int) {
        if (group <= 0 || status == BattleStatus.NONE) return
        groupedStatuses[group] = GroupedBattleStatus(status, rounds, skillId, appliedRound)
        syncLegacyStatusView()
    }

    fun clearGroupedStatus(group: Int) {
        groupedStatuses.remove(group)
        syncLegacyStatusView()
    }

    fun clearAllGroupedStatuses() {
        groupedStatuses.clear()
        syncLegacyStatusView()
    }

    fun syncLegacyStatusView() {
        val primary = groupedStatuses.toSortedMap().values.firstOrNull()
        statusEffect = primary?.status ?: BattleStatus.NONE
        statusRounds = primary?.rounds ?: 0
        statusSkillId = primary?.skillId ?: 0
        statusAppliedRound = primary?.appliedRound ?: -1
    }

    /** 棋盤索引 (col * MAX_ROW + row) */
    val gridIndex: Int get() = col * BattleConstants.MAX_ROW + row

    /**
     * Combo 傷害倍率（每次連擊累計 ×1.2，每回合結束重置為 1.0）
     *
     * 由 BattleInstance.resolveRound() 在 actingFighters 排序後計算並注入。
     */
    var comboMultiplier: Double = 1.0

    companion object {
        /**
         * 判斷 [prev] → [curr] 是否構成 Combo
         *
         * Combo 條件（對應 PC 版 C# 原版邏輯）：
         * 1. 兩者的行動都是技能（不是物品/逃跑等）
         * 2. 兩個技能都是物理技能（SkillDef.fightWay == 1）
         * 3. 兩個技能的目標格子相同（targetCol + targetRow 均相等）
         *
         * @param prev 前一個行動者
         * @param curr 當前行動者
         * @param getSkillDef 查詢技能定義的 lambda（可傳入 BattleSkillResolver::getSkillDef）
         */
        fun checkCombo(
            prev: BattleFighter,
            curr: BattleFighter,
            getSkillDef: (Int) -> tw.tsm.domain.data.SkillDef?
        ): Boolean {
            val prevAction = prev.pendingAction ?: return false
            val currAction = curr.pendingAction ?: return false

            // 條件 1：兩者都是技能行動
            if (prevAction.actionKind != BattleAction.KIND_SKILL) return false
            if (currAction.actionKind != BattleAction.KIND_SKILL) return false

            // 條件 2：兩個技能都是物理技能（fightWay == 1 或普攻 10000）
            val prevIsPhysical = prevAction.id == BattleConstants.HAND_FIGHT_SKILL_ID ||
                (getSkillDef(prevAction.id)?.fightWay == 1)
            val currIsPhysical = currAction.id == BattleConstants.HAND_FIGHT_SKILL_ID ||
                (getSkillDef(currAction.id)?.fightWay == 1)
            if (!prevIsPhysical || !currIsPhysical) return false

            // 條件 3：目標格子相同
            if (prevAction.targetCol != currAction.targetCol) return false
            if (prevAction.targetRow != currAction.targetRow) return false

            return true
        }
    }
}

/**
 * 戰鬥行動（玩家選擇或 AI 決定）
 */
data class BattleAction(
    /** 行動種類：1=技能, 2=物品, 3=捕捉, 4=逃跑 */
    val actionKind: Int,
    /** 技能/物品 ID */
    val id: Int,
    /** 目標列 */
    val targetCol: Int,
    /** 目標行 */
    val targetRow: Int
) {
    companion object {
        const val KIND_SKILL = 1
        const val KIND_ITEM = 2
        const val KIND_CATCH = 3
        const val KIND_ESCAPE = 4
        const val KIND_DEFEND = 5
    }
}


data class GroupedBattleStatus(
    val status: BattleStatus,
    var rounds: Int,
    val skillId: Int,
    val appliedRound: Int
)

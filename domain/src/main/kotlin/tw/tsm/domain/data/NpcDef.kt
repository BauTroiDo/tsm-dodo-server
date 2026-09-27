package tw.tsm.domain.data

/**
 * NPC 定義 — 對應 `NpcData.New(reader)`
 *
 * 來源：`Npc_C.dat`
 * 欄位順序嚴格對照 NpcData.lua 第 236-305 行。
 */
data class NpcDef(
    /** [1] 名稱（UTF-16LE） */
    val name: String,
    /** [2] 類別（ENpc 列舉） */
    val kind: Int,
    /** [4] 角色圖 */
    val picId: Int,
    /** [5] 遮罩圖 */
    val maskId: Int,
    /** [6-9] 色盤（帽髮膚 / 衣配1配2 / 配3配4鞋 / 武底武配1腕） */
    val colorTints: IntArray,
    /** [10] 可否捕捉（0=可捉, 1=不可捉） */
    val canBeCatch: Boolean,
    /** [11] 體型 */
    val bodyKind: Int,
    /** [12] 武器種類 */
    val weaponKind: Int,
    /** [13] 等級 */
    val level: Int,
    /** [14] 基礎 HP */
    val hpBase: Int,
    /** [15] 基礎 SP */
    val spBase: Int,
    /** [16] HP 上升幅度 */
    val hpx: Int,
    /** [17] SP 上升幅度 */
    val spx: Int,
    /** [18] 知力 */
    val intelligence: Int,
    /** [19] 攻擊 */
    val atk: Int,
    /** [20] 防禦 */
    val def: Int,
    /** [21] 敏捷 */
    val agi: Int,
    /** [22] 陣營（0=無, 1=魏, 2=蜀, 3=吳, 4=黃巾, 5=遊俠） */
    val moral: Int,
    /** [23] 陣營值 */
    val moralValue: Int,
    /** [24] 元素（1=地, 2=水, 3=火, 4=風） */
    val element: Int,
    /** [25-27] 技能 ID（最多 3 個，Role.maxNpcSkill = 3） */
    val skills: IntArray,
    /** [35] 武將特有技 */
    val specialSkill: Int,
    /** [36] 轉生限制（0=未轉生, 1=轉生後） */
    val turn: Int,
    /** [38] 被動技 ID */
    val passiveSkill: Int,
    /** [39] 被動技等級 */
    val passiveSkillLv: Int,
    /** [40] 馬鞍 */
    val saddleKind: Int,
    /** [41] 強化物品編號 */
    val upgradeItemId: Int,
    /** [42] 強化技能 */
    val upgradeSkill: Int,
    /** [43] 限制條件（拆 low/high byte） */
    val limits: IntArray,
    /** [44] 座騎位移 */
    val rideOffsetH: Int,
    /** [45] 圖片位移 X */
    val picOffsetX: Int,
    /** [46] 圖片位移 Y */
    val picOffsetY: Int,
    /** [47] HUD 位移 */
    val hudOffsetH: Int,
    /** [48] 影子類型（0=不使用, 1=小, 2=長, 3=大） */
    val shadowKind: Int,
    /** [49] 稀有階級（1-3=銅, 4=銀, 5=金） */
    val rare: Int
)

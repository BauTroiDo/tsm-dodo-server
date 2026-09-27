package tw.tsm.domain.data

/**
 * 技能定義 — 對應 `SkillData.New(reader)`
 *
 * 來源：`Skill_C.dat`
 * 欄位順序嚴格對照 SkillData.lua 第 4-63 行。
 */
data class SkillDef(
    /** 技能名稱（UTF-16LE） */
    val name: String,
    /** 種類 */
    val kind: Int,
    /** 消耗 SP */
    val requireSp: Int,
    /** 元素（1=地, 2=水, 3=火, 4=風） */
    val element: Int,
    /** 數值（影響屬性） */
    val numerical: Long,
    /** 屬性種類 */
    val attribute: Int,
    /** 等級 */
    val level: Int,
    /** 打法（0=單體無位移, 1=打多位, 2=有位移） */
    val fightWay: Int,
    /** 範圍（1=一人, 2=同列二人, 3=行三人, 4=跳三人, 5=十字五人, 6=六人, 7=同行五人, 8=全體） */
    val fightArea: Int,
    /** 回合（0=none, 1-3=定住回合, 4=持續三回合） */
    val round: Int,
    /** 秒數 */
    val spendSecond: Int,
    /** 狀態（0=正常, 1=冰封, 2=旋風, 3=樹精） */
    val hitStatus: Int,
    /** 攻擊分幾下 */
    val howMuchTimes: Int,
    /** 限制等級（含以上才可學會） */
    val limitLv: Int,
    /** 初學所需點數 */
    val learnPoint: Int,
    /** 每級所需點數 */
    val levelUpPoint: Int,
    /** 最高等級 */
    val maxLv: Int,
    /** 前置技能 ID 列表 */
    val preSkills: List<Int>,
    /** 攻擊種類 */
    val atkKind: Int,
    /** 轉生類型（0=未轉生, 1=已轉生） */
    val turnKind: Int,
    /** 學習限制 */
    val learnLimit: Int,
    /** 使用限制 */
    val useLimit: Int,
    /** 打法成長類型 */
    val fightWayGrowType: Int,
    /** 技能說明（UTF-16LE） */
    val description: String
)

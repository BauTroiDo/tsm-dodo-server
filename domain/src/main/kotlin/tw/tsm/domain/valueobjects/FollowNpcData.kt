package tw.tsm.domain.valueobjects

/**
 * 武將資料值物件
 *
 * 對應 S:015-008 登入下發格式。
 * 來源：Role.lua FollowNpcAppear()
 */
data class FollowNpcData(
    /** 跟隨索引（1-4） */
    val slotIndex: Int,
    /** NPC ID */
    val npcId: Int,
    /** 儲存類型：1=隨身, 2=馬車, 3=客棧, 4=武將倉庫 */
    val storageType: Int = 1,
    /** 名稱（Big5） */
    val name: String = "",
    /** 等級 */
    val level: Int = 1,
    /** 經驗值 */
    val exp: Long = 0,
    /** 血量 */
    val hp: Long = 0,
    /** 魔力 */
    val sp: Int = 0,
    /** 攻擊 */
    val atk: Int = 0,
    /** 防禦 */
    val def: Int = 0,
    /** 敏捷 */
    val agi: Int = 0,
    /** 智力 */
    val intelligence: Int = 0,
    /** HP 增幅 */
    val hpx: Int = 0,
    /** SP 增幅 */
    val spx: Int = 0,
    /** 陣亡次數 */
    val deathCount: Int = 0,
    /** 忠誠度 */
    val loyalty: Int = 60,
    /** 技能點數 */
    val skillPoint: Int = 0,
    /** 技能 1 等級 */
    val skill1Lv: Int = 0,
    /** 技能 2 等級 */
    val skill2Lv: Int = 0,
    /** 技能 3 等級 */
    val skill3Lv: Int = 0,
    /** 成長類型：0=不可成長, 1=可成長 */
    val canGrow: Int = 1,
    /** 魂玉 ID */
    val soulId: Int = 0,
    /** 強化等級 */
    val upgradeLv: Int = 0,
    /** 昇華丹次數 */
    val ascendPillCount: Int = 0,
    /** 是否可使用特有技 */
    val canUseSpecialSkill: Boolean = false,
    /** HP 丹數量 */
    val hpPillCount: Int = 0,
    /** SP 丹數量 */
    val spPillCount: Int = 0,
    /** 技能 4 等級（武將倉庫序列化用） */
    val skill4Lv: Int = 0,
    /** 法力成長（武將倉庫序列化用） */
    val conjureGrow: Int = 0,
    /** 轉生次數 */
    val turnKind: Int = 0,
    /** 存活狀態（0=正常, 1=陣亡） */
    val surplus: Int = 0,
    /** 升級時間戳（OLE Date） */
    val lvUpTime: Double = 0.0,
    /**
     * 武將裝備（fitType 1帽/2衣/3武器/4護脕/5鞋/6特殊 → ThingData）
     *
     * 與玩家共用 6 格裝備模型（Item.lua MaxEquip=6, EItemFitType）。
     * 裝備跟著武將本體走：進馬車/客棧/倉庫不卸裝。
     */
    val equips: Map<Int, ThingData> = emptyMap()
)

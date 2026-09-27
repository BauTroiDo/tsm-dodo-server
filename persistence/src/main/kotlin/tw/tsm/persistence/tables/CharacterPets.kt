package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * 角色武將資料表
 *
 * 儲存隨身、馬車、客棧、武將倉庫中的武將資料。
 * storageType: 1=隨身, 2=馬車, 3=客棧, 4=武將倉庫
 */
object CharacterPets : Table("character_pets") {
    val id = long("id").autoIncrement()
    val characterId = long("character_id").index()
    /** 儲存類型：1=隨身, 2=馬車, 3=客棧, 4=武將倉庫 */
    val storageType = integer("storage_type").default(1)
    /** 槽位索引 */
    val slotIndex = integer("slot_index")
    /** NPC ID */
    val npcId = integer("npc_id")
    /** 名稱 */
    val name = varchar("name", 64).default("")
    /** 等級 */
    val level = integer("level").default(1)
    /** 經驗值 */
    val exp = long("exp").default(0)
    /** 血量 */
    val hp = long("hp").default(0)
    /** 魔力 */
    val sp = integer("sp").default(0)
    /** 攻擊 */
    val atk = integer("atk").default(0)
    /** 防禦 */
    val def = integer("def").default(0)
    /** 敏捷 */
    val agi = integer("agi").default(0)
    /** 智力 */
    val intelligence = integer("intelligence").default(0)
    /** HP 增幅 */
    val hpx = integer("hpx").default(0)
    /** SP 增幅 */
    val spx = integer("spx").default(0)
    /** 陣亡次數 */
    val deathCount = integer("death_count").default(0)
    /** 忠誠度 */
    val loyalty = integer("loyalty").default(60)
    /** 技能點數 */
    val skillPoint = integer("skill_point").default(0)
    /** 技能 1 等級 */
    val skill1Lv = integer("skill1_lv").default(0)
    /** 技能 2 等級 */
    val skill2Lv = integer("skill2_lv").default(0)
    /** 技能 3 等級 */
    val skill3Lv = integer("skill3_lv").default(0)
    /** 成長類型：0=不可成長, 1=可成長 */
    val canGrow = integer("can_grow").default(1)
    /** 魂玉 ID */
    val soulId = integer("soul_id").default(0)
    /** 強化等級 */
    val upgradeLv = integer("upgrade_lv").default(0)
    /** 昇華丹次數 */
    val ascendPillCount = integer("ascend_pill_count").default(0)
    /** 是否可使用特有技 */
    val canUseSpecialSkill = bool("can_use_special_skill").default(false)
    /** HP 丹數量 */
    val hpPillCount = integer("hp_pill_count").default(0)
    /** SP 丹數量 */
    val spPillCount = integer("sp_pill_count").default(0)
    /** 技能 4 等級 */
    val skill4Lv = integer("skill4_lv").default(0)
    /** 法力成長 */
    val conjureGrow = integer("conjure_grow").default(0)
    /** 轉生次數 */
    val turnKind = integer("turn_kind").default(0)
    /** 存活狀態 */
    val surplus = integer("surplus").default(0)
    /** 升級時間戳 */
    val lvUpTime = double("lv_up_time").default(0.0)

    override val primaryKey = PrimaryKey(id)
}

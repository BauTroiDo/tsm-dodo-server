package tw.tsm.persistence.tables

import org.jetbrains.exposed.v1.core.Table
import tw.tsm.domain.constants.DEFAULT_START_MAP
import tw.tsm.domain.constants.DEFAULT_START_X
import tw.tsm.domain.constants.DEFAULT_START_Y

/**
 * 角色資料表
 *
 * 儲存角色基本資訊、外觀、屬性配點。
 * 欄位對應手遊 C:009-001 創角封包格式。
 */
object Characters : Table("characters") {
    /** 自動遞增 ID */
    val id = long("id").autoIncrement()

    /** 所屬帳號 ID（外鍵） */
    val accountId = long("account_id").index()

    /** 角色名稱（Big5 最長 14 bytes，UTF-8 最長 21） */
    val name = varchar("name", 32).uniqueIndex()

    /** 性別（1=男, 2=女） */
    val sex = integer("sex").default(1)

    /** 髮型 */
    val hair = integer("hair").default(0)

    /** 色盤一（帽/髮/膚 RGB，十進位編碼） */
    val color1 = integer("color1").default(0)

    /** 色盤二（衣/配/鞋 RGB，十進位編碼） */
    val color2 = integer("color2").default(0)

    /** 五行屬性（1=金, 2=木, 3=水, 4=火, 5=土） */
    val element = integer("element").default(1)

    /** 等級 */
    val level = integer("level").default(1)

    /** 地圖 ID（預設新手村 10801） */
    val mapId = integer("map_id").default(DEFAULT_START_MAP)

    /** 地圖 X 座標 */
    val x = integer("x").default(DEFAULT_START_X)

    /** 地圖 Y 座標 */
    val y = integer("y").default(DEFAULT_START_Y)

    // ── 六圍初始配點 ──

    /** 力量（STR） */
    val str = integer("str").default(0)

    /** 敏捷（DEX） */
    val dex = integer("dex").default(0)

    /** 體力（VIT） */
    val vit = integer("vit").default(0)

    /** 智力（INT） */
    val int_ = integer("int_").default(0)

    /** 精神（MEN） */
    val men = integer("men").default(0)

    /** 靈力（SPR） */
    val spr = integer("spr").default(0)

    /** 可用技能點數 */
    val skillPoint = integer("skill_point").default(0)

    /** 可用屬性點數（升級獲得，用於六圍配點） */
    val attrPoint = integer("attr_point").default(0)

    /** 當前經驗值 */
    val exp = long("exp").default(0)

    /** 轉生次數（0/1/2/3） */
    val turn = integer("turn").default(0)

    /** 二轉職業（0=無, 1=俠客, 2=霸王, 3=賢士, 4=嫡仙） */
    val career = integer("career").default(0)

    // ── 記錄點（重生位置）──

    /** 記錄點地圖 ID（住宿後設定，飛回紀錄點/戰敗時傳送至此） */
    val recordMapId = integer("record_map_id").default(DEFAULT_START_MAP)

    /** 記錄點 X 座標 */
    val recordX = integer("record_x").default(DEFAULT_START_X)

    /** 記錄點 Y 座標 */
    val recordY = integer("record_y").default(DEFAULT_START_Y)

    /** 出戰武將 NPC ID（0 = 無出戰） */
    val fightNpcId = integer("fight_npc_id").default(0)

    /** 當前穿戴稱號 ID（0 = 未穿戴） */
    val titleId = integer("title_id").default(0)

    // ── 元寶點數（S:035-005 實點/虛點）──

    /** 元寶實點（儲值取得，TODO：原廠為帳號層級共享，私服簡化為角色層級） */
    val pointReal = integer("point_real").default(0)

    /** 元寶虛點（綁元，台版未開放消費，僅保留欄位） */
    val pointVirtual = integer("point_virtual").default(0)

    /** 建立時間（Unix millis） */
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

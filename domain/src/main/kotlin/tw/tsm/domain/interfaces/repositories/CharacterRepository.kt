package tw.tsm.domain.interfaces.repositories

/**
 * 角色資料存取介面
 */
interface CharacterRepository {
    /**
     * 查詢帳號下所有角色
     */
    suspend fun findByAccountId(accountId: Long): List<CharacterRecord>

    /**
     * 根據角色 ID 查詢
     */
    suspend fun findById(characterId: Long): CharacterRecord?

    /**
     * 檢查角色名稱是否已存在
     */
    suspend fun isNameTaken(name: String): Boolean

    /**
     * 建立新角色
     *
     * @return 新角色 ID
     */
    suspend fun create(accountId: Long, data: CharacterCreateData): Long

    /**
     * 更新角色等級
     */
    suspend fun updateLevel(characterId: Long, level: Int)

    /** 更新轉生次數與二轉職業。 */
    suspend fun updateTurnCareer(characterId: Long, turn: Int, career: Int)

    /** 更新轉生後外觀。 */
    suspend fun updateTurnAppearance(characterId: Long, hair: Int, color1: Int, color2: Int)

    /**
     * 更新角色位置（斷線/登出時儲存）
     */
    suspend fun updatePosition(characterId: Long, mapId: Int, x: Int, y: Int)

    /**
     * 更新角色技能點數
     */
    suspend fun updateSkillPoint(characterId: Long, skillPoint: Int)

    /**
     * 屬性配點 — 增加指定六圍欄位並扣減 attrPoint
     *
     * @param characterId 角色 ID
     * @param column 要增加的 DB 欄位名（str/dex/vit/int_/men/spr）
     * @param addValue 增加的點數
     * @param newAttrPoint 扣減後的剩餘屬性點數
     */
    suspend fun addStatPoint(characterId: Long, column: String, addValue: Int, newAttrPoint: Int)

    /**
     * 更新角色經驗和等級（戰鬥獎勵/斷線儲存時呼叫）
     */
    suspend fun updateExpAndLevel(characterId: Long, exp: Long, level: Int)

    /**
     * 更新記錄點（住宿後設定，飛回紀錄點/戰敗時傳送至此）
     */
    suspend fun updateRecordPoint(characterId: Long, mapId: Int, x: Int, y: Int)

    /**
     * 查詢記錄點
     *
     * @return (mapId, x, y) 或 null（角色不存在時）
     */
    suspend fun getRecordPoint(characterId: Long): Triple<Int, Int, Int>?

    /**
     * 更新角色完整運行時狀態（斷線/登出儲存）
     *
     * 保存 exp/level + attrPoint/skillPoint + 六圍，確保升級/配點後的狀態不丟失。
     * ⚠️ Characters 表沒有 hp/sp 欄位，HP/SP 在登入時從 maxHp/maxSp 初始化。
     */
    suspend fun updatePlayerState(
        characterId: Long, exp: Long, level: Int,
        attrPoint: Int, skillPoint: Int,
        intelligence: Int, atk: Int, def: Int, agi: Int, hpx: Int, spx: Int
    )

    /**
     * 更新出戰武將 NPC ID（斷線/登出時保存）
     *
     * @param fightNpcId 出戰武將 NPC ID，0 表示無出戰
     */
    suspend fun updateFightNpcId(characterId: Long, fightNpcId: Int)

    /**
     * 更新當前穿戴稱號 ID
     *
     * @param titleId 稱號 ID，0 表示未穿戴
     */
    suspend fun updateTitleId(characterId: Long, titleId: Int)

    /**
     * 查詢所有角色的排行榜基礎資料
     *
     * 用於排行榜定時快取刷新，只取必要欄位。
     */
    suspend fun findAllForLeaderboard(): List<LeaderboardCharRecord>

    /**
     * 查詢元寶點數
     *
     * @return (實點, 虛點)，角色不存在回 (0, 0)
     */
    suspend fun loadPoints(characterId: Long): Pair<Int, Int>

    /**
     * 儲存元寶點數
     *
     * @param pointReal 實點（儲值元寶）
     * @param pointVirtual 虛點（綁元）
     */
    suspend fun savePoints(characterId: Long, pointReal: Int, pointVirtual: Int)
}

/**
 * 角色記錄值物件
 */
data class CharacterRecord(
    val id: Long,
    val accountId: Long,
    val name: String,
    val sex: Int = 1,
    val hair: Int = 0,
    val color1: Int = 0,
    val color2: Int = 0,
    val element: Int = 1,
    val level: Int = 1,
    val mapId: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val str: Int = 0,
    val dex: Int = 0,
    val vit: Int = 0,
    val int_: Int = 0,
    val men: Int = 0,
    val spr: Int = 0,
    val skillPoint: Int = 0,
    val attrPoint: Int = 0,
    val exp: Long = 0,
    val turn: Int = 0,
    val fightNpcId: Int = 0,
    val titleId: Int = 0
)

/**
 * 建立角色所需資料
 *
 * 對應 C:009-001 封包格式：
 * +姓名(?) +性別(1) +0(1) +髮型(1) +0(1)
 * +色盤一(4) +色盤二(4) +屬性(1) +六圍(1)*6 +隱私(1)
 */
/**
 * 排行榜用精簡角色資料
 */
data class LeaderboardCharRecord(
    val id: Long,
    val name: String,
    val level: Int,
    val exp: Long,
    val element: Int,
    val turn: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    /** 金錢（來自 CharacterMoney） */
    val gold: Int = 0,
    /** 拍賣金（來自 CharacterMoney） */
    val auctionGold: Int = 0,
    /** 武將最高等級（來自 CharacterPets MAX(level)） */
    val petMaxLevel: Int = 0
)

data class CharacterCreateData(
    /** 角色名稱（Big5 編碼，最長 14 bytes） */
    val name: String,
    /** 性別（1=男, 2=女） */
    val sex: Int,
    /** 髮型索引 */
    val hair: Int,
    /** 色盤一（帽/髮/膚 RGB 十進位編碼） */
    val color1: Int,
    /** 色盤二（衣/配/鞋 RGB 十進位編碼） */
    val color2: Int,
    /** 五行屬性（1-5） */
    val element: Int,
    /** 力量配點 */
    val str: Int,
    /** 敏捷配點 */
    val dex: Int,
    /** 體力配點 */
    val vit: Int,
    /** 智力配點 */
    val int_: Int,
    /** 精神配點 */
    val men: Int,
    /** 靈力配點 */
    val spr: Int
)

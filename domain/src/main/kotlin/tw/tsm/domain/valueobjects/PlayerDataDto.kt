package tw.tsm.domain.valueobjects

/**
 * 玩家資料 DTO — 對應 S:005-003 封包
 *
 * 來源：protocal.lua protocolTable[5][3] → Role.ReceivePlayerData(data)
 */
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.StatCalculator

data class PlayerDataDto(
    // ── 基礎屬性 ──
    val element: Int = 1,
    val hp: Long = 100,
    val sp: Int = 50,
    val intelligence: Int = 10,
    val atk: Int = 10,
    val def: Int = 10,
    val agi: Int = 10,
    val hpx: Int = 0,
    val spx: Int = 0,
    val level: Int = 1,
    val exp: Int = 0,
    val skillPoint: Int = 0,
    val attrPoint: Int = 6,
    val killNum: Int = 0,
    val battleHonor: Int = 0,
    val govRequireValue: Int = 0,
    val astrolabePoint: Int = 0,
    val maxHp: Long = 100,
    val maxSp: Int = 50,

    // ── 裝備加值 ──
    val equipAtk: Int = 0,
    val equipDef: Int = 0,
    val equipInt: Int = 0,
    val equipAgi: Int = 0,
    val equipMaxHp: Int = 0,
    val equipMaxSp: Int = 0,
    val equipHpx: Int = 0,
    val equipSpx: Int = 0,

    // ── 陣營好感度（魏、蜀、吳、黃巾、遊俠） ──
    val moral: IntArray = intArrayOf(0, 0, 0, 0, 0),

    // ── 其他 ──
    val pracCount: Long = 0,
    val organization: Long = 0,
    val autoEatCount: Int = 0,

    // ── 初轉技能 (skillId, skillLv) ──
    val skills: List<Pair<Int, Int>> = emptyList(),

    // ── 三轉資料 ──
    val turn3Element: Int = 0,
    val turn3Exp: Long = 0,
    val turn3Int: Int = 0,
    val turn3Atk: Int = 0,
    val turn3Def: Int = 0,
    val turn3Agi: Int = 0,
    val turn3Hpx: Int = 0,
    val turn3Spx: Int = 0,
    val turn3Skills: List<Pair<Int, Int>> = emptyList(),

    // ── 新增動態屬性 (attrType, attrValue) ──
    val newAttrs: List<Pair<Int, Int>> = emptyList()
) {
    companion object {
        /** 新角色預設值 */
        fun defaultNewPlayer() = PlayerDataDto()

        /**
         * 從角色記錄建立 DTO
         *
         * 使用角色的六圍配點 + StatSystem 計算真實屬性。
         * 六圍映射：int_→intelligence, str→atk, vit→def, dex→agi, spr→hpx, men→spx
         *
         * @param record 資料庫角色記錄
         * @param statCalc 屬性計算器（StatSystem 實作）
         * @param equipBonus 裝備加成（穿戴裝備的 8 項屬性加成總值）
         */
        fun fromCharacterRecord(
            record: CharacterRecord,
            statCalc: StatCalculator,
            equipBonus: EquipBonusData = EquipBonusData.EMPTY
        ): PlayerDataDto {
            // 六圍 → 戰鬥屬性映射
            val intelligence = record.int_
            val atk = record.str
            val def = record.vit
            val agi = record.dex
            val hpx = record.spr
            val spx = record.men

            // 計算 MaxHP/MaxSP（含裝備加成）
            val totalHpx = hpx + equipBonus.equipHpx
            val maxHp = statCalc.calculateMaxHp(
                level = record.level, hpx = totalHpx,
                equipMaxHp = equipBonus.equipMaxHp, extraHp = 0,
                turn = record.turn, career = record.career, turn3Lv = 0
            )
            val totalSpx = spx + equipBonus.equipSpx
            val maxSp = statCalc.calculateMaxSp(
                level = record.level, spx = totalSpx,
                equipMaxSp = equipBonus.equipMaxSp, extraSp = 0,
                turn = record.turn, career = record.career, turn3Lv = 0
            )

            return PlayerDataDto(
                element = record.element,
                hp = maxHp,
                sp = maxSp,
                intelligence = intelligence,
                atk = atk,
                def = def,
                agi = agi,
                hpx = hpx,
                spx = spx,
                level = record.level,
                skillPoint = record.skillPoint,
                attrPoint = record.attrPoint,
                maxHp = maxHp,
                maxSp = maxSp,
                equipAtk = equipBonus.equipAtk,
                equipDef = equipBonus.equipDef,
                equipInt = equipBonus.equipInt,
                equipAgi = equipBonus.equipAgi,
                equipMaxHp = equipBonus.equipMaxHp,
                equipMaxSp = equipBonus.equipMaxSp,
                equipHpx = equipBonus.equipHpx,
                equipSpx = equipBonus.equipSpx
            )
        }
    }
}

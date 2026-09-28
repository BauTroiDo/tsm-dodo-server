package tw.tsm.domain.valueobjects

/**
 * 玩家資料 DTO — 對應 S:005-003 封包
 *
 * 來源：protocal.lua protocolTable[5][3] → Role.ReceivePlayerData(data)
 */
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.Turn3State
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
            equipBonus: EquipBonusData = EquipBonusData.EMPTY,
            turn3: Turn3State = Turn3State()
        ): PlayerDataDto {
            // 六圍 → 戰鬥屬性映射
            val intelligence = record.int_
            val atk = record.str
            val def = record.vit
            val agi = record.dex
            val hpx = record.spr
            val spx = record.men

            // Calculator.lua: Turn3 uses base Lv=200, turn3Lv=Lv-200 and adds
            // automatic Turn3 HP/SP growth plus persisted Turn3Hpx/Turn3Spx.
            val turn3Lv = if (record.turn >= 3) (record.level - 200).coerceAtLeast(0) else 0
            val calcLevel = if (record.turn >= 3) 200 else record.level
            val totalHpx = hpx + equipBonus.equipHpx +
                (if (record.turn >= 3) turn3Lv.coerceAtMost(70) + turn3.hpx else 0)
            val maxHp = statCalc.calculateMaxHp(
                level = calcLevel, hpx = totalHpx,
                equipMaxHp = equipBonus.equipMaxHp, extraHp = 0,
                turn = record.turn, career = record.career, turn3Lv = turn3Lv
            )
            val totalSpx = spx + equipBonus.equipSpx +
                (if (record.turn >= 3) turn3Lv + turn3.spx else 0)
            val maxSp = statCalc.calculateMaxSp(
                level = calcLevel, spx = totalSpx,
                equipMaxSp = equipBonus.equipMaxSp, extraSp = 0,
                turn = record.turn, career = record.career, turn3Lv = turn3Lv
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
                // DVT Role.ReceivePlayerData đọc EXP trực tiếp từ S:005-003.
                // Nếu bỏ trường này, PlayerDataDto mặc định exp=0 và thanh EXP luôn
                // trở về 0% sau mỗi lần đăng nhập dù DB đã lưu đúng EXP.
                exp = record.exp.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
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
                equipSpx = equipBonus.equipSpx,
                turn3Element = turn3.element,
                turn3Exp = turn3.exp,
                turn3Int = turn3.intelligence,
                turn3Atk = turn3.atk,
                turn3Def = turn3.def,
                turn3Agi = turn3.agi,
                turn3Hpx = turn3.hpx,
                turn3Spx = turn3.spx,
                turn3Skills = turn3.skills.toList()
            )
        }
    }
}

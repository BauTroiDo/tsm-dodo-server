package tw.tsm.domain.valueobjects

/**
 * DTO dữ liệu người chơi — tương ứng gói tin S:005-003
 *
 * Nguồn: protocal.lua protocolTable[5][3] → Role.ReceivePlayerData(data)
 */
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.Turn3State
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.util.DvtPercentMath

data class PlayerDataDto(
    // ── Thuộc tính cơ bản ──
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

    // ── Bonus trang bị ──
    val equipAtk: Int = 0,
    val equipDef: Int = 0,
    val equipInt: Int = 0,
    val equipAgi: Int = 0,
    val equipMaxHp: Int = 0,
    val equipMaxSp: Int = 0,
    val equipHpx: Int = 0,
    val equipSpx: Int = 0,

    // ── Độ thân thiện phe đảng (Ngụy, Thục, Ngô, Khăn Vàng, Du Hiệp) ──
    val moral: IntArray = intArrayOf(0, 0, 0, 0, 0),

    // ── Khác ──
    val pracCount: Long = 0,
    val organization: Long = 0,
    val autoEatCount: Int = 0,

    // ── Kỹ năng chuyển sinh lần đầu (skillId, skillLv) ──
    val skills: List<Pair<Int, Int>> = emptyList(),

    // ── Dữ liệu chuyển sinh lần 3 ──
    val turn3Element: Int = 0,
    val turn3Exp: Long = 0,
    val turn3Int: Int = 0,
    val turn3Atk: Int = 0,
    val turn3Def: Int = 0,
    val turn3Agi: Int = 0,
    val turn3Hpx: Int = 0,
    val turn3Spx: Int = 0,
    val turn3Skills: List<Pair<Int, Int>> = emptyList(),

    // ── Thuộc tính động mới thêm (attrType, attrValue) ──
    val newAttrs: List<Pair<Int, Int>> = emptyList()
) {
    companion object {
        /** Giá trị mặc định của nhân vật mới*/
        fun defaultNewPlayer() = PlayerDataDto()

        /**
         * Tạo DTO từ bản ghi nhân vật
         *
         * Dùng điểm phân bổ sáu chỉ số của nhân vật + StatSystem để tính thuộc tính thực tế.
         * Ánh xạ sáu chỉ số: int_→intelligence, str→atk, vit→def, dex→agi, spr→hpx, men→spx
         *
         * @param record bản ghi nhân vật trong cơ sở dữ liệu
         * @param statCalc bộ tính thuộc tính (do StatSystem triển khai)
         * @param equipBonus bonus trang bị (tổng chỉ số cộng thêm 8 thuộc tính của trang bị đang mặc)
         */
        fun fromCharacterRecord(
            record: CharacterRecord,
            statCalc: StatCalculator,
            equipBonus: EquipBonusData = EquipBonusData.EMPTY,
            turn3: Turn3State = Turn3State(),
            astrolabePoint: Int = 0,
            astrolabeBonus: AstrolabeBonusData = AstrolabeBonusData.EMPTY
        ): PlayerDataDto {
            // Ánh xạ sáu chỉ số → thuộc tính chiến đấu
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
                (if (record.turn >= 3) turn3Lv.coerceAtMost(70) + turn3.hpx else 0) +
                astrolabeBonus.hpx
            val baseMaxHp = statCalc.calculateMaxHp(
                level = calcLevel, hpx = totalHpx,
                equipMaxHp = equipBonus.equipMaxHp, extraHp = 0,
                turn = record.turn, career = record.career, turn3Lv = turn3Lv
            )
            val maxHp = DvtPercentMath.applyFinalPercentFloor(baseMaxHp, equipBonus.hpPercent)
                .coerceAtLeast(1L)
            val totalSpx = spx + equipBonus.equipSpx +
                (if (record.turn >= 3) turn3Lv + turn3.spx else 0) +
                astrolabeBonus.spx
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
                killNum = record.killNum,
                astrolabePoint = astrolabePoint.coerceIn(0, 65_535),
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
                moral = record.moral.copyOf(5),
                turn3Element = turn3.element,
                turn3Exp = turn3.exp,
                turn3Int = turn3.intelligence,
                turn3Atk = turn3.atk,
                turn3Def = turn3.def,
                turn3Agi = turn3.agi,
                turn3Hpx = turn3.hpx,
                turn3Spx = turn3.spx,
                turn3Skills = turn3.skills.toList(),
                // DVT Role.ReceivePlayerData đọc các thuộc tính Mobile mở rộng ở cuối S:005-003.
                newAttrs = listOf(
                    EAttribute.ATK_PERCENT to equipBonus.atkPercent,
                    EAttribute.DEF_PERCENT to equipBonus.defPercent,
                    EAttribute.HP_PERCENT to equipBonus.hpPercent,
                    EAttribute.INT_PERCENT to equipBonus.intPercent,
                    EAttribute.CRITICAL to equipBonus.crit,
                    EAttribute.CRIT_DAMAGE to equipBonus.critDamage,
                    EAttribute.DODGE to equipBonus.dodge,
                    EAttribute.HIT to equipBonus.hit,
                    EAttribute.INCREASE_TOTAL_DAMAGE to equipBonus.increaseTotalDamage,
                    EAttribute.DAMAGE_REDUCTION to equipBonus.damageReduction,
                    EAttribute.REDUCE_TOTAL_DAMAGE to equipBonus.reduceTotalDamage,
                    EAttribute.INCREASE_DAMAGE_TAKEN_SELF to equipBonus.increaseDamageTakenSelf
                )
            )
        }
    }
}

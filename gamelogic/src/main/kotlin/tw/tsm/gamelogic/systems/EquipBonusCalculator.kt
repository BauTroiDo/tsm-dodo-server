package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.valueobjects.ThingData

/**
 * Trình tính toán chỉ số cộng thêm từ trang bị — Object hàm thuần túy (pure function)
 *
 * Tham khảo tskero `TSCharacter.addEquipBonus()` + `refreshBonus()`.
 * Duyệt qua tất cả trang bị nhân vật đang mặc, sử dụng [ItemDef.attr1Kind]/[ItemDef.attr1Value]
 * và [ItemDef.attr2Kind]/[ItemDef.attr2Value] để cộng dồn 8 thuộc tính cộng thêm.
 *
 * Giá trị của attr1Kind/attr2Kind tương ứng trực tiếp với hằng số [EAttribute]:
 * 207=EquipMaxHp, 208=EquipMaxSp, 210=EquipAtk,
 * 211=EquipDef, 212=EquipInt, 214=EquipAgi,
 * 218=EquipHpx, 219=EquipSpx
 */
object EquipBonusCalculator {

    /**
     * Tính toán tổng giá trị của 8 thuộc tính cộng thêm từ trang bị trên toàn thân
     *
     * Duyệt qua từng [ThingData] trong ô trang bị, truy vấn [itemDefs] để lấy
     * attr1Kind/attr1Value và attr2Kind/attr2Value,
     * đối chiếu với hằng số [EAttribute] và cộng dồn vào các trường tương ứng.
     *
     * @param equips Trang bị đang mặc hiện tại (fitType → ThingData)
     * @param itemDefs Bảng định nghĩa vật phẩm (itemId → ItemDef)
     * @return Kết quả cộng dồn của 8 chỉ số cộng thêm từ trang bị
     */
    fun calculateTotalBonus(
        equips: Map<Int, ThingData>,
        itemDefs: Map<Int, ItemDef>,
        sacredEquipPrimaryDefs: Map<Int, SacredEquipPrimaryDef> = emptyMap()
    ): EquipBonusData {
        var maxHp = 0
        var maxSp = 0
        var atk = 0
        var def = 0
        var int_ = 0
        var agi = 0
        var hpx = 0
        var spx = 0
        var atkPercent = 0
        var defPercent = 0
        var hpPercent = 0
        var intPercent = 0
        var crit = 0
        var critDamage = 0
        var dodge = 0
        var hit = 0
        var increaseTotalDamage = 0
        var damageReduction = 0
        var reduceTotalDamage = 0
        var increaseDamageTakenSelf = 0

        for ((_, thing) in equips) {
            if (thing.itemId == 0) continue
            val itemDef = itemDefs[thing.itemId] ?: continue

            // 8 stat trang bị cổ điển dùng mã hóa 100-based. DVT stat Mobile 103..106
            // lại lưu trực tiếp phần trăm (IOS_Item_C: Dodge/Hit rune raw=1..4 => 1..4%).
            val eff1 = decodeAttributeValue(itemDef.attr1Kind, itemDef.attr1Value)
            val eff2 = decodeAttributeValue(itemDef.attr2Kind, itemDef.attr2Value)

            // Myth attr1Kind là key SacredEquipPrimaryDatas, không phải EAttribute trực tiếp.
            // Lua ItemData:GetGodMainAttrText lấy attNo + upgradeLv[mythLv] từ bảng này.
            if (itemDef.kind != EItemKind.MYTH) {
                accumulateBonus(itemDef.attr1Kind, eff1) { kind, value ->
                when (kind) {
                    EAttribute.EQUIP_MAX_HP -> maxHp += value
                    EAttribute.EQUIP_MAX_SP -> maxSp += value
                    EAttribute.EQUIP_ATK -> atk += value
                    EAttribute.EQUIP_DEF -> def += value
                    EAttribute.EQUIP_INT -> int_ += value
                    EAttribute.EQUIP_AGI -> agi += value
                    EAttribute.EQUIP_HPX -> hpx += value
                    EAttribute.EQUIP_SPX -> spx += value
                    EAttribute.ATK_PERCENT -> atkPercent += value
                    EAttribute.DEF_PERCENT -> defPercent += value
                    EAttribute.HP_PERCENT -> hpPercent += value
                    EAttribute.INT_PERCENT -> intPercent += value
                    EAttribute.CRITICAL -> crit += value
                    EAttribute.CRIT_DAMAGE -> critDamage += value
                    EAttribute.DODGE -> dodge += value
                    EAttribute.HIT -> hit += value
                    EAttribute.INCREASE_TOTAL_DAMAGE -> increaseTotalDamage += value
                    EAttribute.DAMAGE_REDUCTION -> damageReduction += value
                    EAttribute.REDUCE_TOTAL_DAMAGE -> reduceTotalDamage += value
                        EAttribute.INCREASE_DAMAGE_TAKEN_SELF -> increaseDamageTakenSelf += value
                    }
                }
            }

            // Cộng thêm từ attr2 (Tương ứng với prop2/prop2_val trong tskero)
            accumulateBonus(itemDef.attr2Kind, eff2) { kind, value ->
                when (kind) {
                    EAttribute.EQUIP_MAX_HP -> maxHp += value
                    EAttribute.EQUIP_MAX_SP -> maxSp += value
                    EAttribute.EQUIP_ATK -> atk += value
                    EAttribute.EQUIP_DEF -> def += value
                    EAttribute.EQUIP_INT -> int_ += value
                    EAttribute.EQUIP_AGI -> agi += value
                    EAttribute.EQUIP_HPX -> hpx += value
                    EAttribute.EQUIP_SPX -> spx += value
                    EAttribute.ATK_PERCENT -> atkPercent += value
                    EAttribute.DEF_PERCENT -> defPercent += value
                    EAttribute.HP_PERCENT -> hpPercent += value
                    EAttribute.INT_PERCENT -> intPercent += value
                    EAttribute.CRITICAL -> crit += value
                    EAttribute.CRIT_DAMAGE -> critDamage += value
                    EAttribute.DODGE -> dodge += value
                    EAttribute.HIT -> hit += value
                    EAttribute.INCREASE_TOTAL_DAMAGE -> increaseTotalDamage += value
                    EAttribute.DAMAGE_REDUCTION -> damageReduction += value
                    EAttribute.REDUCE_TOTAL_DAMAGE -> reduceTotalDamage += value
                    EAttribute.INCREASE_DAMAGE_TAKEN_SELF -> increaseDamageTakenSelf += value
                }
            }

            // DVT Myth/Godskin sub-attributes are already real values:
            // ItemData:GetGodSubAttrText displays myth[i].value + myth[i].extra directly.
            // Do not apply legacy raw-100 decoding here.
            if (itemDef.kind == EItemKind.MYTH) {
                val primary = sacredEquipPrimaryDefs[itemDef.attr1Kind]
                val mainValue = primary?.valueAt(thing.mythLevel)
                if (primary != null && mainValue != null) {
                    accumulateBonus(primary.attributeKind, mainValue) { kind, value ->
                        when (kind) {
                            EAttribute.EQUIP_MAX_HP -> maxHp += value
                            EAttribute.EQUIP_MAX_SP -> maxSp += value
                            EAttribute.EQUIP_ATK -> atk += value
                            EAttribute.EQUIP_DEF -> def += value
                            EAttribute.EQUIP_INT -> int_ += value
                            EAttribute.EQUIP_AGI -> agi += value
                            EAttribute.EQUIP_HPX -> hpx += value
                            EAttribute.EQUIP_SPX -> spx += value
                            EAttribute.ATK_PERCENT -> atkPercent += value
                            EAttribute.DEF_PERCENT -> defPercent += value
                            EAttribute.HP_PERCENT -> hpPercent += value
                            EAttribute.INT_PERCENT -> intPercent += value
                            EAttribute.CRITICAL -> crit += value
                            EAttribute.CRIT_DAMAGE -> critDamage += value
                            EAttribute.DODGE -> dodge += value
                            EAttribute.HIT -> hit += value
                            EAttribute.INCREASE_TOTAL_DAMAGE -> increaseTotalDamage += value
                            EAttribute.DAMAGE_REDUCTION -> damageReduction += value
                            EAttribute.REDUCE_TOTAL_DAMAGE -> reduceTotalDamage += value
                            EAttribute.INCREASE_DAMAGE_TAKEN_SELF -> increaseDamageTakenSelf += value
                        }
                    }
                }

                for (mythAttr in thing.myth) {
                    val mythValue = mythAttr.value + mythAttr.extra
                    accumulateBonus(mythAttr.kind, mythValue) { kind, value ->
                        when (kind) {
                            EAttribute.EQUIP_MAX_HP -> maxHp += value
                            EAttribute.EQUIP_MAX_SP -> maxSp += value
                            EAttribute.EQUIP_ATK -> atk += value
                            EAttribute.EQUIP_DEF -> def += value
                            EAttribute.EQUIP_INT -> int_ += value
                            EAttribute.EQUIP_AGI -> agi += value
                            EAttribute.EQUIP_HPX -> hpx += value
                            EAttribute.EQUIP_SPX -> spx += value
                            EAttribute.ATK_PERCENT -> atkPercent += value
                            EAttribute.DEF_PERCENT -> defPercent += value
                            EAttribute.HP_PERCENT -> hpPercent += value
                            EAttribute.INT_PERCENT -> intPercent += value
                            EAttribute.CRITICAL -> crit += value
                            EAttribute.CRIT_DAMAGE -> critDamage += value
                            EAttribute.DODGE -> dodge += value
                            EAttribute.HIT -> hit += value
                            EAttribute.INCREASE_TOTAL_DAMAGE -> increaseTotalDamage += value
                            EAttribute.DAMAGE_REDUCTION -> damageReduction += value
                            EAttribute.REDUCE_TOTAL_DAMAGE -> reduceTotalDamage += value
                            EAttribute.INCREASE_DAMAGE_TAKEN_SELF -> increaseDamageTakenSelf += value
                        }
                    }
                }
            }
        }

        return EquipBonusData(
            equipMaxHp = maxHp, equipMaxSp = maxSp,
            equipAtk = atk, equipDef = def,
            equipInt = int_, equipAgi = agi,
            equipHpx = hpx, equipSpx = spx,
            atkPercent = atkPercent, defPercent = defPercent,
            hpPercent = hpPercent, intPercent = intPercent,
            crit = crit, critDamage = critDamage,
            dodge = dodge, hit = hit,
            increaseTotalDamage = increaseTotalDamage,
            damageReduction = damageReduction,
            reduceTotalDamage = reduceTotalDamage,
            increaseDamageTakenSelf = increaseDamageTakenSelf
        )
    }

    /**
     * Giải mã ItemData attr value theo contract DVT.
     * Nhóm Mobile percentage 101..118 lưu trực tiếp giá trị phần trăm; nhóm equip legacy 2xx dùng offset 100.
     * IOS_Item_C xác nhận ATK%/DEF% rune raw=1..5 và Crit/Dodge/Hit rune raw trực tiếp.
     */
    private fun decodeAttributeValue(attrKind: Int, rawValue: Int): Int {
        if (rawValue == 0) return 0
        return when (attrKind) {
            EAttribute.RESISTANCE_PERCENT -> rawValue
            in EAttribute.ATK_PERCENT..EAttribute.INCREASE_DAMAGE_TAKEN_SELF -> rawValue
            else -> rawValue - 100
        }
    }

    /**
     * Cộng dồn giá trị cộng thêm của một thuộc tính đơn lẻ
     *
     * @param attrKind Hằng số EAttribute (0 nghĩa là không có hiệu ứng)
     * @param value Giá trị cộng thêm (0 nghĩa là không có hiệu ứng)
     * @param accumulator Callback cộng dồn (Accumulator callback)
     */
    private inline fun accumulateBonus(
        attrKind: Int,
        value: Int,
        accumulator: (Int, Int) -> Unit
    ) {
        if (attrKind != 0 && value != 0) {
            accumulator(attrKind, value)
        }
    }
}
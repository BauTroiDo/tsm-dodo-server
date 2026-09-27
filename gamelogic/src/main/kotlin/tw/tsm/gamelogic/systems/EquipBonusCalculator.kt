package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.data.ItemDef
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
        itemDefs: Map<Int, ItemDef>
    ): EquipBonusData {
        var maxHp = 0
        var maxSp = 0
        var atk = 0
        var def = 0
        var int_ = 0
        var agi = 0
        var hpx = 0
        var spx = 0

        for ((_, thing) in equips) {
            if (thing.itemId == 0) continue
            val itemDef = itemDefs[thing.itemId] ?: continue

            // Giá trị thuộc tính trang bị sử dụng mã hóa hệ cơ số 100 (100-based) (Item.lua:3049 effectValue = attribute.value - 100)
            // raw=0 nghĩa là chưa thiết lập → giữ nguyên là 0; raw!=0 → trừ đi 100 để lấy giá trị cộng thêm thực tế
            val eff1 = if (itemDef.attr1Value != 0) itemDef.attr1Value - 100 else 0
            val eff2 = if (itemDef.attr2Value != 0) itemDef.attr2Value - 100 else 0

            // Cộng thêm từ attr1 (Tương ứng với prop1/prop1_val trong tskero)
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
                }
            }
        }

        return EquipBonusData(
            equipMaxHp = maxHp, equipMaxSp = maxSp,
            equipAtk = atk, equipDef = def,
            equipInt = int_, equipAgi = agi,
            equipHpx = hpx, equipSpx = spx
        )
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
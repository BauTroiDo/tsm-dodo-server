package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.NpcUpgradeDef

/**
 * 武將強化載入器 — 對應 `NpcData.lua` NpcUpgradeData.New
 *
 * 來源：`NpcUpgrade_C.dat`
 * 格式：count(Int32) + 每筆 lv(B) + requireMoney(U32) + requireDebris(U16)
 *       + requirePicture(B) + attributeAdd(B) + skillLv(B) + upgradeLvIcon(B)
 */
object NpcUpgradeDatLoader {

    /**
     * 載入武將強化資料
     *
     * @return Map<目標星等, NpcUpgradeDef>
     */
    fun load(data: ByteArray): Map<Int, NpcUpgradeDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, NpcUpgradeDef>(count)

        for (i in 0 until count) {
            val lv = reader.readByte()                    // 星等（key）
            val requireMoney = reader.readUInt32()        // 需求遊戲幣
            val requireDebris = reader.readUInt16()       // 需求碎片數量
            val requirePicture = reader.readByte()        // 需求畫冊數量
            val attributeAdd = reader.readByte()          // 強化隨機數值
            val skillLv = reader.readByte()               // 強化技等級
            val upgradeLvIcon = reader.readByte()         // 星等圖示

            result[lv] = NpcUpgradeDef(
                requireMoney = requireMoney,
                requireDebris = requireDebris,
                requirePicture = requirePicture,
                attributeAdd = attributeAdd,
                skillLv = skillLv,
                upgradeLvIcon = upgradeLvIcon
            )
        }

        return result
    }
}

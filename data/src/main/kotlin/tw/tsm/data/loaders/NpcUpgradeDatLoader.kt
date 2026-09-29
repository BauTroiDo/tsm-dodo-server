package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.NpcUpgradeDef

/**
 * Loader cường hoá tướng — tương ứng `NpcData.lua` NpcUpgradeData.New
 *
 * Nguồn: `NpcUpgrade_C.dat`
 * Định dạng: count(Int32) + mỗi bản ghi lv(B) + requireMoney(U32) + requireDebris(U16)
 *       + requirePicture(B) + attributeAdd(B) + skillLv(B) + upgradeLvIcon(B)
 */
object NpcUpgradeDatLoader {

    /**
     * Đọc dữ liệu cường hoá tướng
     *
     * @return Map<mức sao đích, NpcUpgradeDef>
     */
    fun load(data: ByteArray): Map<Int, NpcUpgradeDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, NpcUpgradeDef>(count)

        for (i in 0 until count) {
            val lv = reader.readByte()                    // Mức sao (key)
            val requireMoney = reader.readUInt32()        // Số game tệ yêu cầu
            val requireDebris = reader.readUInt16()       // Số lượng mảnh vụn yêu cầu
            val requirePicture = reader.readByte()        // Số lượng hoạ sách yêu cầu
            val attributeAdd = reader.readByte()          // Giá trị ngẫu nhiên cường hoá
            val skillLv = reader.readByte()               // Cấp độ kỹ năng cường hoá
            val upgradeLvIcon = reader.readByte()         // Icon sao

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

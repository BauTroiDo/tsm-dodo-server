package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.ItemDef

/**
 * Loader vật phẩm — tương ứng `DataManager.OnLoadItemData`
 *
 * Nguồn: `Item_C.dat`
 * Định dạng: count(Int32) + count bản ghi ItemData.New(reader)
 * Key = ID vật phẩm (chỉ lưu khi id != 0)
 *
 * Thứ tự trường đối chiếu nghiêm ngặt dòng 300-385 của ItemData.lua.
 */
object ItemDatLoader {

    /**
     * Đọc dữ liệu vật phẩm
     */
    fun load(data: ByteArray): Map<Int, ItemDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        val result = LinkedHashMap<Int, ItemDef>(count)

        for (i in 0 until count) {
            val name = reader.readUnicodeString()          // [1]
            val kind = reader.readByte()                    // [2]
            val id = reader.readUInt16()                    // [3]
            val iconId = reader.readUInt16()                // [4]
            val malePicId = reader.readUInt16()             // [5]
            val femalePicId = reader.readUInt16()           // [6]

            // [7-12] Thuộc tính 1, 2
            val attr1Kind = reader.readUInt16()
            val attr1Item = reader.readByte()
            val attr1Value = reader.readInt32()
            val attr2Kind = reader.readUInt16()
            val attr2Item = reader.readByte()
            val attr2Value = reader.readInt32()

            val material = reader.readByte()                // [13]
            val level = reader.readByte()                   // [14]
            val fitType = reader.readByte()                 // [15]
            val specialAbility = reader.readUInt16()        // [16]

            // [17-24] Bảng màu
            val maleColorTints = IntArray(4) { reader.readInt32() }
            val femaleColorTints = IntArray(4) { reader.readInt32() }

            val openUsed = reader.readByte()                // [25]
            val needLv = reader.readByte()                  // [26]
            val price = reader.readInt32()                  // [27]
            val sellPrice = reader.readInt32()              // [28]
            val gender = reader.readByte()                  // [29]
            val restrict = reader.readByte()                // [30]
            val threshold = reader.readInt32()              // [31]
            val element = reader.readByte()                 // [32]
            val elementValue = reader.readInt32()           // [33]
            val skillLink = reader.readUInt16()             // [34]
            val turn = reader.readByte()                    // [35]
            val giftDot = reader.readUInt16()               // [36]
            val spare2 = reader.readByte()                  // [37]
            val spare3 = reader.readUInt16()                // [38]
            val restrict2 = reader.readByte()               // [39]
            val suitId = reader.readUInt16()                // [40]
            val spare5 = reader.readByte()                  // [41]
            val directUse = reader.readByte()               // [42]
            val roleCountIndex = reader.readUInt16()        // [43]
            val roleCountValue = reader.readInt32()         // [44]
            val sort = reader.readByte()                    // [45]
            val maleEquipSwitch = reader.readByte()         // [46]
            val femaleEquipSwitch = reader.readByte()       // [47]
            val btnState = reader.readByte()                // [48]
            val durable = reader.readByte()                 // [49]
            val furnaceKind = reader.readByte()             // [50]
            val furnaceCount = reader.readUInt32()          // [51]
            val quality = reader.readByte()                 // [52]
            val auctionTag = reader.readByte()              // [53]
            val auctionSubTag = reader.readByte()           // [54]
            val description = reader.readUnicodeString()    // Mô tả

            if (id != 0) {
                result[id] = ItemDef(
                    name = name, kind = kind, iconId = iconId,
                    malePicId = malePicId, femalePicId = femalePicId,
                    attr1Kind = attr1Kind, attr1Item = attr1Item, attr1Value = attr1Value,
                    attr2Kind = attr2Kind, attr2Item = attr2Item, attr2Value = attr2Value,
                    material = material, level = level, fitType = fitType,
                    specialAbility = specialAbility,
                    maleColorTints = maleColorTints, femaleColorTints = femaleColorTints,
                    openUsed = openUsed, needLv = needLv, price = price, sellPrice = sellPrice,
                    gender = gender, restrict = restrict, threshold = threshold,
                    element = element, elementValue = elementValue, skillLink = skillLink,
                    turn = turn, giftDot = giftDot, spare2 = spare2, spare3 = spare3,
                    restrict2 = restrict2, suitId = suitId, spare5 = spare5,
                    directUse = directUse, roleCountIndex = roleCountIndex,
                    roleCountValue = roleCountValue, sort = sort,
                    maleEquipSwitch = maleEquipSwitch, femaleEquipSwitch = femaleEquipSwitch,
                    btnState = btnState, durable = durable,
                    furnaceKind = furnaceKind, furnaceCount = furnaceCount,
                    quality = quality, auctionTag = auctionTag, auctionSubTag = auctionSubTag,
                    description = description
                )
            }
        }

        return result
    }
}

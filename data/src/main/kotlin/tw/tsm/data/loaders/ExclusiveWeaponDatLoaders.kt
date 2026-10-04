package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.CollectWarriorSortDef
import tw.tsm.domain.data.ExclusiveWeaponAttributeDef
import tw.tsm.domain.data.ExclusiveWeaponDef

/**
 * DVT Data/ExclusiveWeaponData.lua::New(reader).
 *
 * Record = itemId(U16) + enhanceLv(U8) + skillId(U16) + skillLv(U8)
 * + 2 x [attrKind(U16) + attrValue(U32)]
 * + element(U8) + elementValue(U32)
 * + plusAttrKind(U16) + plusAttrValue(U32) = 29 bytes.
 */
object ExclusiveWeaponDatLoader {
    fun load(data: ByteArray): Map<Int, Map<Int, ExclusiveWeaponDef>> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_ExclusiveWeapon_C.dat count âm: $count" }

        val mutable = LinkedHashMap<Int, LinkedHashMap<Int, ExclusiveWeaponDef>>()
        repeat(count) {
            val itemId = reader.readUInt16()
            val enhanceLevel = reader.readByte()
            val def = ExclusiveWeaponDef(
                itemId = itemId,
                enhanceLevel = enhanceLevel,
                skillId = reader.readUInt16(),
                skillLevel = reader.readByte(),
                attributes = List(2) {
                    ExclusiveWeaponAttributeDef(
                        kind = reader.readUInt16(),
                        value = reader.readUInt32(),
                    )
                },
                element = reader.readByte(),
                elementValue = reader.readUInt32(),
                plusAttribute = ExclusiveWeaponAttributeDef(
                    kind = reader.readUInt16(),
                    value = reader.readUInt32(),
                ),
            )
            val byLevel = mutable.getOrPut(itemId) { LinkedHashMap() }
            // DVT DataManager assigns exclusiveWeaponDatas[id][lv] = data.
            // Duplicate keys are therefore intentional last-row-wins data.
            byLevel[enhanceLevel] = def
        }

        require(!reader.canRead()) { "IOS_ExclusiveWeapon_C.dat còn trailing bytes" }
        return mutable.mapValues { (_, levels) -> levels.toMap() }
    }
}

/**
 * DVT Data/CollectWarriorSortData.lua::New(reader).
 *
 * Record = index(U16) + ids[3](U16) + nft(U8) = 9 bytes.
 */
object CollectWarriorSortDatLoader {
    fun load(data: ByteArray): List<CollectWarriorSortDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_CollectWarriorSort_C.dat count âm: $count" }

        val result = LinkedHashMap<Int, CollectWarriorSortDef>(count)
        repeat(count) {
            val index = reader.readUInt16()
            val def = CollectWarriorSortDef(
                index = index,
                npcIds = List(3) { reader.readUInt16() },
                nft = reader.readByte(),
            )
            // DVT DataManager assigns collectWarriorSortDatas[data.index] = data.
            result[index] = def
        }

        require(!reader.canRead()) { "IOS_CollectWarriorSort_C.dat còn trailing bytes" }
        return result.values.toList()
    }
}

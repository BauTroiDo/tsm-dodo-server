package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.NpcSoulDef
import tw.tsm.domain.data.SoulDef
import tw.tsm.domain.data.SoulLevelCostDef
import tw.tsm.domain.data.SoulLevelDef

/**
 * DVT Data/SoulData.lua::New(reader).
 *
 * quality(U8) + startPoint(U16) + basicLvLimit(U16)
 * + skillGetProb[4](U16) + convertRate[0..5](U16)
 * + compoundHigherProb(U16) + limitBreakthrough(U8) = 28 bytes.
 */
object SoulDatLoader {
    fun load(data: ByteArray): Map<Int, SoulDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_Soul_C.dat count âm: $count" }
        val result = LinkedHashMap<Int, SoulDef>(count)

        repeat(count) {
            val quality = reader.readByte()
            require(quality in 0..3) { "Soul quality ngoài DVT N/R/SR/SSR: $quality" }
            require(quality !in result) { "Soul quality bị trùng: $quality" }

            result[quality] = SoulDef(
                quality = quality,
                startPoint = reader.readUInt16(),
                basicLvLimit = reader.readUInt16(),
                skillGetProb = List(4) { reader.readUInt16() },
                convertRate = List(6) { reader.readUInt16() },
                compoundHigherProb = reader.readUInt16(),
                limitBreakthrough = reader.readByte(),
            )
        }

        require(!reader.canRead()) { "IOS_Soul_C.dat còn trailing bytes" }
        return result
    }
}

/**
 * DVT Data/SoulLevelData.lua::New(reader).
 *
 * level(U16) + 4 qualities × [reel(U16), coin(U16), affCoin(U16)] = 26 bytes.
 */
object SoulLevelDatLoader {
    fun load(data: ByteArray): Map<Int, SoulLevelDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_SoulLevel_C.dat count âm: $count" }
        val result = LinkedHashMap<Int, SoulLevelDef>(count)

        repeat(count) {
            val level = reader.readUInt16()
            require(level !in result) { "Soul level bị trùng: $level" }
            result[level] = SoulLevelDef(
                level = level,
                costs = List(4) {
                    SoulLevelCostDef(
                        reel = reader.readUInt16(),
                        coin = reader.readUInt16(),
                        affCoin = reader.readUInt16(),
                    )
                },
            )
        }

        require(!reader.canRead()) { "IOS_SoulLevel_C.dat còn trailing bytes" }
        return result
    }
}

/**
 * DVT Data/NpcSoulData.lua::New(reader).
 *
 * itemId(U16) + npcId(U16) + quality(U8) = 5 bytes.
 */
object NpcSoulDatLoader {
    fun load(data: ByteArray): Map<Int, NpcSoulDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_NpcSoul_C.dat count âm: $count" }
        val result = LinkedHashMap<Int, NpcSoulDef>(count)

        repeat(count) {
            val itemId = reader.readUInt16()
            val npcId = reader.readUInt16()
            val quality = reader.readByte()
            require(quality in 0..3) { "NpcSoul quality ngoài DVT N/R/SR/SSR: $quality" }
            require(itemId !in result) { "NpcSoul itemId bị trùng: $itemId" }
            result[itemId] = NpcSoulDef(itemId, npcId, quality)
        }

        require(!reader.canRead()) { "IOS_NpcSoul_C.dat còn trailing bytes" }
        return result
    }
}

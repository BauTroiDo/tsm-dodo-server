package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.SouvenirDef

/** Exact 18-byte DVT SouvenirData row. */
object SouvenirDatLoader {
    fun load(data: ByteArray): Map<Int, SouvenirDef> {
        val reader = DatReader(data)
        val count = reader.readInt32()
        require(count >= 0) { "IOS_Souvenir_C.dat count âm: $count" }
        val result = LinkedHashMap<Int, SouvenirDef>(count)
        repeat(count) {
            val id = reader.readUInt16()
            require(id !in result) { "Souvenir itemId trùng: $id" }
            result[id] = SouvenirDef(
                itemId = id,
                quality = reader.readByte(),
                payment = reader.readByte(),
                price = reader.readInt32(),
                addCharm = reader.readUInt16(),
                addFriendly = reader.readUInt16(),
                addExp = reader.readUInt16(),
                naming = reader.readByte() != 0,
                broadcast = reader.readByte() != 0,
                roleCount = reader.readUInt16(),
            )
        }
        require(!reader.canRead()) { "IOS_Souvenir_C.dat còn trailing bytes" }
        return result
    }
}

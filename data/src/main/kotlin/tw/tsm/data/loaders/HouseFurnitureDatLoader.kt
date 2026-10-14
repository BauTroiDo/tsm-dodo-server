package tw.tsm.data.loaders

import java.nio.ByteBuffer
import java.nio.ByteOrder
import tw.tsm.domain.data.HouseFurnitureDef

/** Faithful field order: DVT Lua FurnitureData.New and NumberOfHouseWarriorData.New. */
object HouseFurnitureDatLoader {
    fun loadFurniture(bytes: ByteArray): Map<Int, HouseFurnitureDef> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rows = bb.int
        require(rows in 1..100000)
        val result = LinkedHashMap<Int, HouseFurnitureDef>(rows)
        fun u8() = bb.get().toInt() and 0xff
        fun u16() = bb.short.toInt() and 0xffff
        fun variableString() {
            val size = u16()
            require(size <= bb.remaining())
            bb.position(bb.position() + size)
        }
        repeat(rows) {
            val id = u16()
            val type = u8()
            val kind = u8()
            repeat(8) { u8() } // faceTo,dynamic,interactive,action,emoji,GIF*3
            variableString() // musicName
            u8() // replayMusic
            u8() // interactivePointCount
            repeat(4) { bb.short } // 2 interactive X/Y points
            u8() // level
            u16() // seriesNumber
            val x = u8()
            val y = u8()
            val score = u8()
            bb.short // addedOrder
            u8() // limited
            variableString() // message
            bb.short // effectId
            if (id != 0) result[id] = HouseFurnitureDef(id, type, kind, x, y, score)
        }
        require(!bb.hasRemaining()) { "IOS_Furniture trailing bytes=${bb.remaining()}" }
        return result
    }

    fun loadWarriorThresholds(bytes: ByteArray): Map<Int, Int> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rows = bb.int
        require(rows in 1..4)
        val result = LinkedHashMap<Int, Int>(rows)
        repeat(rows) {
            val count = bb.get().toInt() and 0xff
            val score = bb.short.toInt() and 0xffff
            require(count in 1..4 && count !in result)
            result[count] = score
        }
        require(!bb.hasRemaining())
        return result
    }
}

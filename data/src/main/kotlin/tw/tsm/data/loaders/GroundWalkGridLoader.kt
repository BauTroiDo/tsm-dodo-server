package tw.tsm.data.loaders

import tw.tsm.data.DatReader
import tw.tsm.domain.data.MapWalkGrid

/** Ground.mmg index and MapData.New block payload; no map-specific coordinates. */
object GroundWalkGridLoader {
    fun load(data: ByteArray): Map<Int, MapWalkGrid> {
        require(data.size >= 2)
        val r = DatReader(data)
        r.seek(data.size - 2)
        val count = r.readUInt16()
        val index = data.size - 2 - count * 29
        require(index >= 0)
        val entries = mutableListOf<Triple<Int, Int, Int>>()
        r.seek(index)
        repeat(count) {
            val name = r.readFixedAscii(20)
            val pos = r.readInt32()
            val size = r.readInt32()
            val id = name.removeSuffix(".map").toIntOrNull()
            if (id != null && name.endsWith(".map")) entries += Triple(id, pos, size)
        }
        return buildMap {
            for ((id, pos, size) in entries) {
                require(pos >= 0 && size >= 13 && pos.toLong() + size <= index)
                r.seek(pos)
                r.readInt32(); r.readInt32()
                val pics = r.readByte()
                require(9L + pics * 6L + 4 <= size)
                r.seek(pos + 9 + pics * 6)
                val width = r.readUInt16()
                val height = r.readUInt16()
                val cells = width.toLong() * height
                require(width > 0 && height > 0 && cells <= Int.MAX_VALUE && r.position().toLong() + cells <= pos.toLong() + size)
                put(id, MapWalkGrid(width, height, ByteArray(cells.toInt()) { r.readByte().toByte() }))
            }
        }
    }
}

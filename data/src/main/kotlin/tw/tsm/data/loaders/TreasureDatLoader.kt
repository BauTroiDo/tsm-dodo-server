package tw.tsm.data.loaders

import tw.tsm.data.DatReader

/**
 * Read-only, byte-exact original DVT Treasure_C table reader.
 * Mirrors lua/Data/Treasure*Data.lua. No grant, resource debit or DB migration.
 */
object TreasureDatLoader {
    data class Activity(val id: Int, val stringId: Long, val type: Int, val number: Int)
    data class Group(val id: Int, val stringId: Long, val cards: List<Int>)
    data class GroupLv(val id: Int, val level: Int, val env: Int, val role: Int, val att: Int, val value: Int)
    data class Lv(val id: Int, val level: Int, val env: Int, val role: Int, val att: Int,
                  val value: Int, val copies: Int, val materials: Int, val gold: Int)
    data class Role(val id: Int, val role: Int, val element: Int, val turn: Int,
                    val country: Int, val collection: Int)
    data class Star(val id: Int, val star: Int, val env: Int, val role: Int, val att: Int,
                    val value: Int, val materials: Int, val gold: Int)
    data class Catalog(
        val activities: List<Activity>, val groups: List<Group>,
        val groupLevels: List<GroupLv>, val levels: List<Lv>,
        val roles: List<Role>, val stars: List<Star>,
    ) {
        fun nextLevel(id: Int, current: Int): Lv? {
            require(current in 0..10)
            return if (current == 10) null else levels.singleOrNull { it.id == id && it.level == current + 1 }
        }
        fun investedCopies(id: Int, level: Int): Int {
            require(level in 0..10)
            return levels.filter { it.id == id && it.level <= level }.sumOf { it.copies }
        }
        fun nextStarData(id: Int, current: Int): Star? {
            require(current in 0..6)
            return if (current == 6) null else stars.singleOrNull { it.id == id && it.star == current + 1 }
        }
    }

    private inline fun <T> rows(bytes: ByteArray, width: Int, read: (DatReader) -> T): List<T> {
        require(bytes.size >= 4)
        val reader = DatReader(bytes)
        val count = reader.readInt32()
        require(count >= 0 && 4L + count.toLong() * width == bytes.size.toLong())
        val result = ArrayList<T>(count)
        repeat(count) { result += read(reader) }
        require(reader.position() == bytes.size)
        return result
    }

    fun activities(b: ByteArray) = rows(b, 9) {
        Activity(it.readUInt16(), it.readUInt32(), it.readByte(), it.readUInt16())
    }
    fun groups(b: ByteArray) = rows(b, 18) {
        Group(it.readUInt16(), it.readUInt32(), List(6) { _ -> it.readUInt16() })
    }
    fun groupLevels(b: ByteArray) = rows(b, 13) {
        GroupLv(it.readUInt16(), it.readByte(), it.readUInt16(), it.readUInt16(),
            it.readUInt16(), it.readInt32())
    }
    fun levels(b: ByteArray) = rows(b, 22) {
        Lv(it.readUInt16(), it.readByte(), it.readUInt16(), it.readUInt16(),
           it.readUInt16(), it.readInt32(), it.readByte(), it.readInt32(), it.readInt32())
    }
    fun roles(b: ByteArray) = rows(b, 8) {
        Role(it.readUInt16(), it.readByte(), it.readByte(), it.readByte(),
             it.readByte(), it.readUInt16())
    }
    fun stars(b: ByteArray) = rows(b, 21) {
        Star(it.readUInt16(), it.readByte(), it.readUInt16(), it.readUInt16(),
             it.readUInt16(), it.readInt32(), it.readInt32(), it.readInt32())
    }
    fun load(
        activity: ByteArray, group: ByteArray, groupLv: ByteArray,
        level: ByteArray, criteria: ByteArray, star: ByteArray,
    ): Catalog = Catalog(
        activities(activity), groups(group), groupLevels(groupLv),
        levels(level), roles(criteria), stars(star),
    ).also { c ->
        val cardIds = (1..114).toSet()
        require(c.levels.size == 1140 && c.stars.size == 684)
        require(c.levels.groupBy { it.id }.keys == cardIds)
        require(c.stars.groupBy { it.id }.keys == cardIds)
        require(c.levels.groupBy { it.id }.values.all {
            it.size == 10 && it.map { v -> v.level }.toSet() == (1..10).toSet()
        })
        require(c.stars.groupBy { it.id }.values.all {
            it.size == 6 && it.map { v -> v.star }.toSet() == (1..6).toSet()
        })
        require(c.groups.size == 29 && c.groups.map { it.id }.toSet() == (1..29).toSet())
        require(c.groups.all { g -> g.cards.all { it == 0 || it in cardIds } })
        require(c.groupLevels.size == 290 && c.groupLevels.groupBy { it.id }.values.all {
            it.size == 10 && it.map { v -> v.level }.toSet() == (1..10).toSet()
        })
        require(c.levels.all { it.copies >= 0 && it.materials >= 0 && it.gold >= 0 })
        require(c.stars.all { it.materials >= 0 && it.gold >= 0 })
    }
}

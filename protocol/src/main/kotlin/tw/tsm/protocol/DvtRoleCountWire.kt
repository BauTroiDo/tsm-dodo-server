package tw.tsm.protocol

/**
 * DVT RoleCount wire quirks from client Logic/RoleCount.lua.
 *
 * Indexes 190..195 are "special counters": RoleCount.Get() returns value >> 16.
 * Server/runtime state stays logical (0, 1, 2, ...); only the S:085 wire value
 * is packed into the high 16 bits.
 */
object DvtRoleCountWire {
    const val SPECIAL_FIRST = 190
    const val SPECIAL_LAST = 195

    fun encodeValue(index: Int, logicalValue: Int): Int {
        if (index !in SPECIAL_FIRST..SPECIAL_LAST) return logicalValue
        val bounded = logicalValue.coerceIn(0, 0xFFFF)
        return bounded shl 16
    }
}

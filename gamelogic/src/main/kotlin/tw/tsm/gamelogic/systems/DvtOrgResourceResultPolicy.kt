package tw.tsm.gamelogic.systems

/**
 * Original DVT Lua `Logic/Organization.lua`:
 *   EOrgResource = { Gold=1, Grain=2, Mineral=3, Wood=4, Cloth=5, ... }
 *
 * The active Eve.emg has exactly 35 resultType=0/class11 results, across
 * seven event/condition chains (11572, 26522, 26525, 57523).
 * Every result has parameter=0, style=1..5, a positive quantity, and
 * is followed by other quest effects. These are ORGANIZATION resources,
 * NOT character gold or an inventory item.
 *
 * Decoder intentionally does NOT mutate a resource balance: authoritative
 * organization membership/treasury persistence, atomic updates and packet
 * synchronization are absent from this server implementation. Do not
 * silently skip these effects or reinterpret Gold as character currency.
 */
object DvtOrgResourceResultPolicy {
    enum class Kind(val style: Int, val displayName: String) {
        GOLD(1, "vàng quân đoàn"),
        GRAIN(2, "lương thực"),
        MINERAL(3, "khoáng sản"),
        WOOD(4, "gỗ"),
        CLOTH(5, "vải");

        companion object {
            fun fromStyle(style: Int): Kind? = entries.firstOrNull { it.style == style }
        }
    }

    data class Delta(val kind: Kind, val amount: Int)

    fun decode(
        resultType: Int,
        resultClass: Int,
        parameter: Int,
        parameterStyle: Int,
        resultValue: Int
    ): Delta? {
        if (resultType != 0 || resultClass != 11 || parameter != 0 || resultValue <= 0) {
            return null
        }
        val kind = Kind.fromStyle(parameterStyle) ?: return null
        return Delta(kind, resultValue)
    }

    /** This decoder documents semantics; it is NOT permission to execute. */
    fun isExecutable(): Boolean = false
}

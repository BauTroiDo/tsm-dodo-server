package tw.tsm.domain.valueobjects

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exact 93-byte DVT second-generation general state (HumanData.lua ChildData.New).
 *
 * The original bytes are preserved rather than synthesizing skills, feeding dates or
 * the 18 reserved bytes. Child level itself is NOT in this record: it belongs
 * to the selected follow NPC and must not be inferred from childItemCount.
 */
class ChildDataSnapshot private constructor(
    private val raw: ByteArray,
    val kind: Int,
    val sex: Int,
    val turn: Int,
    val element: Int,
    val skills: List<Skill>,
    val lastFeedingTime: Double,
    val stodge: Int,
    val lastSatiationTime: Double,
    val satiation: Int,
    val attributes: List<Int>,
    val attributeExperience: List<Int>,
    val childItemCount: Int,
    val career: Int,
) {
    data class Skill(val id: Int, val level: Int)

    /** Make defensive copies; database and packet senders must never mutate the snapshot. */
    fun toBytes(): ByteArray = raw.copyOf()

    /**
     * Apply an authoritative S069-008 child item-use total without rewriting
     * unrelated skills, timestamps, attributes or the 18 reserved bytes.
     *
     * DVT Common/protocal.lua protocolTable[69][8] makes an existing child
     * an adult once childItemCount reaches 400; 399 must not mature it.
     * This is deliberately NOT a client-controlled update operation.
     */
    fun withItemCount(newTotal: Int): ChildDataSnapshot {
        require(kind != 0) { "Cannot update item-use count when no child exists" }
        require(newTotal in childItemCount..65535) {
            "Child item-use total must never decrease or exceed UInt16"
        }
        val copy = raw.copyOf()
        copy[72] = newTotal.toByte()
        copy[73] = (newTotal ushr 8).toByte()
        if (newTotal >= ADULT_ITEM_USE_THRESHOLD) copy[0] = ADULT_KIND.toByte()
        return decode(copy)
    }

    companion object {
        const val ADULT_KIND = 3
        const val ADULT_ITEM_USE_THRESHOLD = 400
        const val SIZE = 93

        fun decode(payload: ByteArray): ChildDataSnapshot {
            require(payload.size == SIZE) { "DVT ChildData must be exactly $SIZE bytes" }
            val b = ByteBuffer.wrap(payload.copyOf()).order(ByteOrder.LITTLE_ENDIAN)
            fun u8(): Int = b.get().toInt() and 0xff
            fun u16(): Int = b.short.toInt() and 0xffff

            val kind = u8()
            val sex = u8()
            val turn = u8()
            val element = u8()
            val skills = List(10) { Skill(u16(), u8()) }
            val feedTime = b.double
            val stodge = u16()
            val satiationTime = b.double
            val satiation = u16()
            val attributes = List(6) { u16() }
            val exp = List(6) { u8() }
            val itemCount = u16()
            val career = u8()
            val reserved = ByteArray(18)
            b.get(reserved)

            require(b.position() == SIZE)
            require(kind in 0..3) { "Unknown DVT EChild kind=$kind" }
            require(sex in 0..2) { "Invalid child sex=$sex" }
            require(feedTime.isFinite() && satiationTime.isFinite()) {
                "Invalid stored ChildData timestamp"
            }
            return ChildDataSnapshot(
                payload.copyOf(), kind, sex, turn, element, skills, feedTime,
                stodge, satiationTime, satiation, attributes, exp, itemCount, career
            )
        }
    }
}

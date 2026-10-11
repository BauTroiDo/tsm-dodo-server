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

    companion object {
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
            require(kind in 0..4) { "Unknown DVT EChild kind=$kind" }
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

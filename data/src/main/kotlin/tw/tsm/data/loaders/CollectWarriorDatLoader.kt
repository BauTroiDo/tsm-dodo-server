package tw.tsm.data.loaders

import tw.tsm.domain.data.CollectWarriorDef
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loader for Mobile DVT Data-ios/IOS_CollectWarrior_C.dat.
 *
 * Contract from Lua:
 * count:Int32
 * repeat count:
 *   id:UInt16, getFlag:UInt16, flag:UInt16, gainStr:UInt32,
 *   award[5] { itemId:UInt16, count:UInt16 },
 *   shineValue:Byte,
 *   storyLen:UInt16, storyBytes[storyLen]
 */
object CollectWarriorDatLoader {
    fun load(bytes: ByteArray): Map<Int, CollectWarriorDef> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.remaining() >= 4) { "CollectWarrior data too short" }
        val count = bb.int
        require(count >= 0) { "CollectWarrior invalid count=$count" }

        val result = LinkedHashMap<Int, CollectWarriorDef>(count)
        repeat(count) { index ->
            require(bb.remaining() >= 33) {
                "CollectWarrior truncated before row $index: remaining=${bb.remaining()}"
            }
            val id = bb.short.toInt() and 0xFFFF
            val getFlag = bb.short.toInt() and 0xFFFF
            val rewardFlag = bb.short.toInt() and 0xFFFF
            val gainStr = bb.int.toLong() and 0xFFFF_FFFFL
            val awards = ArrayList<Pair<Int, Int>>(5)
            repeat(5) {
                val itemId = bb.short.toInt() and 0xFFFF
                val quantity = bb.short.toInt() and 0xFFFF
                awards += itemId to quantity
            }
            val shineValue = bb.get().toInt() and 0xFF
            val storyLen = bb.short.toInt() and 0xFFFF
            require(bb.remaining() >= storyLen) {
                "CollectWarrior truncated story row=$index id=$id len=$storyLen remaining=${bb.remaining()}"
            }
            bb.position(bb.position() + storyLen)

            result[id] = CollectWarriorDef(
                id = id,
                getFlag = getFlag,
                rewardFlag = rewardFlag,
                gainStr = gainStr,
                awards = awards,
                shineValue = shineValue,
            )
        }

        require(!bb.hasRemaining()) {
            "CollectWarrior trailing bytes=${bb.remaining()} after $count rows"
        }
        return result
    }
}

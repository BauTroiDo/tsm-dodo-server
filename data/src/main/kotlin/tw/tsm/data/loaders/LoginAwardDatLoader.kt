package tw.tsm.data.loaders

import tw.tsm.domain.data.LoginAwardDef
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LoginAwardDatLoader {
    fun load(bytes: ByteArray): List<LoginAwardDef> {
        require(bytes.size >= 4) { "IOS_LoginAwardData_C.dat quá ngắn" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = b.int
        require(count >= 0) { "count âm: $count" }
        require(b.remaining() == count * 46) {
            "IOS_LoginAwardData_C.dat sai kích thước: count=$count remaining=${b.remaining()}"
        }
        return List(count) {
            val group = b.get().toInt() and 0xff
            val changeKind = b.get().toInt() and 0xff
            val day = b.int.toLong() and 0xffffffffL
            val rewards = ArrayList<Pair<Int, Int>>(5)
            repeat(5) {
                val itemId = b.short.toInt() and 0xffff
                val quantity = b.int
                rewards += itemId to quantity
            }
            val changeItemId = b.short.toInt() and 0xffff
            val changeQuantity = b.int
            val roleCount = b.short.toInt() and 0xffff
            val flag = b.short.toInt() and 0xffff
            LoginAwardDef(
                group = group,
                changeKind = changeKind,
                day = day.toInt(),
                rewards = rewards,
                changeItemId = changeItemId,
                changeQuantity = changeQuantity,
                roleCount = roleCount,
                flag = flag,
            )
        }
    }
}

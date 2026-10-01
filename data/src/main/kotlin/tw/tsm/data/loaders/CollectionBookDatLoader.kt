package tw.tsm.data.loaders

import tw.tsm.domain.data.CollectCardAttributeDef
import tw.tsm.domain.data.CollectCardDef
import tw.tsm.domain.data.CollectCardSortDef
import tw.tsm.domain.data.CollectNftDef
import tw.tsm.domain.data.CollectStyleDef
import tw.tsm.domain.data.CollectStyleValueDef
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CollectionBookDatLoader {
    private fun buffer(bytes: ByteArray): ByteBuffer =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    fun loadStyles(bytes: ByteArray): Map<Int, CollectStyleDef> {
        val bb = buffer(bytes)
        val count = bb.int
        val out = LinkedHashMap<Int, CollectStyleDef>(count)
        repeat(count) {
            val id = bb.short.toInt() and 0xFFFF
            bb.int // name string id
            val itemIds = List(5) { bb.short.toInt() and 0xFFFF }
            bb.int // info string id
            val scores = List(6) { bb.short.toInt() and 0xFFFF }
            val nft = bb.get().toInt() and 0xFF
            bb.int // nft string id
            out[id] = CollectStyleDef(id, itemIds, scores, nft)
        }
        require(!bb.hasRemaining()) { "CollectStyle trailing bytes=${bb.remaining()}" }
        return out
    }

    fun loadStyleValues(bytes: ByteArray): List<CollectStyleValueDef> {
        val bb = buffer(bytes)
        val count = bb.int
        val out = ArrayList<CollectStyleValueDef>(count)
        repeat(count) {
            val score = bb.short.toInt() and 0xFFFF
            val attrs = List(4) {
                val kind = bb.get().toInt() and 0xFF
                val value = bb.short.toInt() and 0xFFFF
                kind to value
            }
            val nft = bb.get().toInt() and 0xFF
            out += CollectStyleValueDef(score, attrs, nft)
        }
        require(!bb.hasRemaining()) { "CollectStyleValue trailing bytes=${bb.remaining()}" }
        return out
    }

    fun loadNfts(bytes: ByteArray): Map<Int, CollectNftDef> {
        val bb = buffer(bytes)
        val count = bb.int
        val out = LinkedHashMap<Int, CollectNftDef>(count)
        repeat(count) {
            val kind = bb.get().toInt() and 0xFF
            val flag = bb.short.toInt() and 0xFFFF
            val warriorId = bb.short.toInt() and 0xFFFF
            val getId = bb.short.toInt() and 0xFFFF
            val canGet = bb.get().toInt() and 0xFF
            val shineValue = bb.short.toInt() and 0xFFFF
            out[warriorId] = CollectNftDef(kind, flag, warriorId, getId, canGet, shineValue)
        }
        require(!bb.hasRemaining()) { "CollectNFT trailing bytes=${bb.remaining()}" }
        return out
    }

    fun loadCards(bytes: ByteArray): Map<Int, CollectCardDef> {
        val bb = buffer(bytes)
        val count = bb.int
        val out = LinkedHashMap<Int, CollectCardDef>(count)
        repeat(count) {
            val id = bb.get().toInt() and 0xFF
            val index = bb.get().toInt() and 0xFF
            bb.int // name
            bb.int // story
            val cardIds = List(9) { bb.short.toInt() and 0xFFFF }
            val maxLevel = bb.get().toInt() and 0xFF
            val base = bb.get().toInt() and 0xFF
            val multiplier = bb.get().toInt() and 0xFF
            val values = List(6) {
                val kind = bb.get().toInt() and 0xFF
                val value = bb.short.toInt() and 0xFFFF
                val grow = bb.short.toInt() and 0xFFFF
                CollectCardAttributeDef(kind, value, grow)
            }
            out[id] = CollectCardDef(id, index, cardIds, maxLevel, base, multiplier, values)
        }
        require(!bb.hasRemaining()) { "CollectCard trailing bytes=${bb.remaining()}" }
        return out
    }

    fun loadCardSorts(bytes: ByteArray): Map<Int, CollectCardSortDef> {
        val bb = buffer(bytes)
        val count = bb.int
        val out = LinkedHashMap<Int, CollectCardSortDef>(count)
        repeat(count) {
            val id = bb.short.toInt() and 0xFFFF
            val warriorId = bb.short.toInt() and 0xFFFF
            val roleCount = bb.short.toInt() and 0xFFFF
            val getFlag = bb.short.toInt() and 0xFFFF
            out[id] = CollectCardSortDef(id, warriorId, roleCount, getFlag)
        }
        require(!bb.hasRemaining()) { "CollectCardSort trailing bytes=${bb.remaining()}" }
        return out
    }
}

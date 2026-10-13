package tw.tsm.persistence.repositories

import tw.tsm.domain.interfaces.repositories.TradeAccountSnapshot
import tw.tsm.domain.valueobjects.ThingData
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Stable SHA-256 of a pair's full trade plan. Never hash Kotlin data-class
 * toString(), because snapshot ordering/printing could change over upgrades.
 */
internal object TradeSnapshotDigest {
    fun of(first: TradeAccountSnapshot, second: TradeAccountSnapshot): String {
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { out ->
            out.writeInt(1) // fingerprint format version
            for (side in listOf(first, second).sortedBy { it.characterId }) {
                out.writeLong(side.characterId)
                out.writeInt(side.beforeGold)
                out.writeInt(side.afterGold)
                out.writeInt(side.auctionGold)
                fun writeBag(bag: Map<Int, ThingData>) {
                    out.writeInt(bag.size)
                    for ((slot, t) in bag.toSortedMap()) {
                        out.writeInt(slot)
                        out.writeInt(t.itemId)
                        out.writeInt(t.quantity)
                        out.writeInt(t.damage)
                        out.writeInt(t.element)
                        out.writeInt(t.elementValue)
                        out.writeInt(t.proofKind)
                        out.writeInt(t.growLevel)
                        out.writeInt(t.growExp)
                        out.writeInt(t.specialKind)
                        out.writeInt(t.stoneAttr)
                        out.writeInt(t.stoneLevel)
                        out.writeInt(t.enhanceLevel)
                        out.writeLong(t.deleteTime.toRawBits())
                        out.writeInt(t.damagedItemId)
                        out.writeBoolean(t.isLocked)
                        out.writeInt(t.reinforced)
                        out.writeInt(t.affix1)
                        out.writeInt(t.affix2)
                        out.writeInt(t.affix3)
                        out.writeInt(t.styleLevel)
                        out.writeInt(t.mythLevel)
                        out.writeInt(t.myth.size)
                        for (attr in t.myth) {
                            out.writeInt(attr.kind)
                            out.writeInt(attr.value)
                            out.writeInt(attr.extra)
                        }
                    }
                }
                writeBag(side.beforeBag)
                writeBag(side.afterBag)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(stream.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

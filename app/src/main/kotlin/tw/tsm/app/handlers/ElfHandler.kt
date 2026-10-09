package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.ElfRepository
import tw.tsm.domain.interfaces.repositories.ElfProgressRecord
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.protocol.PacketWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val elfLogger = KotlinLogging.logger {}

/** PC DVT Elf_C.dat and ElfExp_C.dat; not the Android CDN. */
class ElfCatalog(val defs: Map<Int, ElfDefinition>, val expByLevel: Map<Int, Int>) {
    data class ElfDefinition(val no: Int, val maxLevel: Int, val ownershipFlag: Int)

    companion object {
        fun fromPacked(elfBytes: ByteArray, expBytes: ByteArray): ElfCatalog {
            val a = ByteBuffer.wrap(elfBytes).order(ByteOrder.LITTLE_ENDIAN)
            val n = a.int
            require(n == 25 && elfBytes.size == 4 + 25 * 33)
            val defs = LinkedHashMap<Int, ElfDefinition>()
            repeat(n) {
                val no = a.short.toInt() and 0xffff
                a.short // model ID
                a.int // localized name ID
                val max = a.short.toInt() and 0xffff
                val flag = a.short.toInt() and 0xffff
                a.get() // location
                a.short // base exp
                repeat(9) { a.short } // 3 sets: openLv, attr, value
                require(defs.put(no, ElfDefinition(no, max, flag)) == null)
            }
            require(a.position() == elfBytes.size && defs.containsKey(999))
            val b = ByteBuffer.wrap(expBytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = b.int
            require(count == 20 && expBytes.size == 4 + count * 6)
            val thresholds = LinkedHashMap<Int, Int>()
            repeat(count) {
                val lv = b.short.toInt() and 0xffff
                val required = b.int
                require(lv in 1..20 && required > 0 && thresholds.put(lv, required) == null)
            }
            require(b.position() == expBytes.size)
            return ElfCatalog(defs, thresholds)
        }
        fun fromPcFiles(): ElfCatalog = fromPacked(
            Files.readAllBytes(Path.of("File.dat", "Data", "elf_C.dat")),
            Files.readAllBytes(Path.of("File.dat", "Data-ios", "IOS_ElfExp_C.dat")),
        )
    }
}

/** DVT protocol 107; no client patch required. */
object ElfPackets {
    const val DEFAULT_NO = 999
    fun snapshot(session: SessionSender, active: Int, fed: Int, progress: List<ElfProgressRecord>) {
        require(progress.size <= 255)
        val w = header(5)
            .writeShortLE(active)
            .writeIntLE(fed)
            .writeByte(progress.size)
        progress.sortedBy { it.elfNo }.forEach {
            w.writeShortLE(it.elfNo).writeShortLE(it.level).writeIntLE(it.exp)
        }
        session.send(w.toByteArray())
    }
    fun acquired(session: SessionSender, elfNo: Int) {
        session.send(header(1).writeShortLE(elfNo).toByteArray())
    }
    fun teamResult(session: SessionSender, subKind: Int, elfNo: Int, success: Boolean) {
        require(subKind == 2 || subKind == 3)
        session.send(header(subKind).writeShortLE(elfNo).writeByte(if (success) 1 else 0).toByteArray())
    }
    fun feedResult(session: SessionSender, success: Boolean) {
        session.send(header(4).writeByte(if (success) 1 else 0).toByteArray())
    }
    fun progress(session: SessionSender, row: ElfProgressRecord) {
        session.send(header(6).writeShortLE(row.elfNo).writeShortLE(row.level).writeIntLE(row.exp).toByteArray())
    }
    private fun header(subKind: Int) = PacketWriter()
        .writeByte(Opcodes.ELF).writeByte(subKind).writeByte(0)
}

/** Server source of truth: flags unlock Tinh Linh, persistent profiles select active Elf. */
class ElfHandler(
    private val repo: ElfRepository,
    private val bits: BitFlagSystem,
    private val inventory: InventorySystem,
    private val responses: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
    private val catalog: ElfCatalog,
) : PacketHandler {
    override val mainKind = Opcodes.ELF
    private val locks = ConcurrentHashMap<Long, Mutex>()
    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || session.roleId <= 0 || session.battleId != 0L) return
        val cid = session.roleId.toLong()
        when (subKind) {
            5 -> if (data.isEmpty()) query(session, cid)
            2, 3 -> if (data.size == 2) {
                val no = (data[0].toInt() and 255) or ((data[1].toInt() and 255) shl 8)
                locks.computeIfAbsent(cid) { Mutex() }.withLock { changeActive(session, cid, subKind, no) }
            }
            4 -> locks.computeIfAbsent(cid) { Mutex() }.withLock { feed(session, cid, data) }
            else -> elfLogger.debug { "Unrecognized Elf operation subKind=$subKind" }
        }
    }
    private suspend fun query(session: GameSession, cid: Long) {
        val flags = bits.getSetFlagIds(cid)
        val snapshot = repo.load(cid)
        val owned = catalog.defs.values.asSequence()
            .filter { it.no != ElfPackets.DEFAULT_NO && it.ownershipFlag in flags }
            .map { def ->
                snapshot.progress.firstOrNull { it.elfNo == def.no }
                    ?: ElfProgressRecord(def.no, 1, 0)
            }
            .toList()
        val active = snapshot.profile.activeNo.takeIf {
            it == ElfPackets.DEFAULT_NO || owned(it, flags)
        } ?: ElfPackets.DEFAULT_NO
        ElfPackets.snapshot(session, active, snapshot.profile.feedTotal, owned)
    }
    private fun owned(no: Int, flags: Set<Int>): Boolean =
        catalog.defs[no]?.let { it.no != 999 && it.ownershipFlag in flags } == true

    private suspend fun changeActive(session: GameSession, cid: Long, kind: Int, no: Int) {
        val active = repo.load(cid).profile.activeNo
        val success = if (kind == 2) {
            no == ElfPackets.DEFAULT_NO || owned(no, bits.getSetFlagIds(cid))
        } else {
            no != ElfPackets.DEFAULT_NO && active == no
        }
        if (!success) {
            ElfPackets.teamResult(session, kind, no, false)
            return
        }
        val next = if (kind == 3) ElfPackets.DEFAULT_NO else no
        try {
            repo.setActive(cid, next)
            ElfPackets.teamResult(session, kind, no, true)
        } catch (e: Exception) {
            elfLogger.error(e) { "Unable to persist Elf equip cid=$cid no=$no" }
            ElfPackets.teamResult(session, kind, no, false)
        }
    }

    private suspend fun feed(session: GameSession, cid: Long, data: ByteArray) {
        if (data.size < 7) return
        val reader = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val no = reader.short.toInt() and 65535
        val count = reader.get().toInt() and 255
        if (count < 1 || count > 20 || data.size != 3 + count * 4) {
            ElfPackets.feedResult(session, false); return
        }
        val def = catalog.defs[no]
        if (def == null || def.no == 999 || !owned(no, bits.getSetFlagIds(cid))) {
            ElfPackets.feedResult(session, false); return
        }
        val payload = (1..count).map {
            val slot = reader.short.toInt() and 65535
            val qty = reader.short.toInt() and 65535
            slot to qty
        }
        if (payload.any { it.second !in 1..999 } || payload.map { it.first }.distinct().size != count) {
            ElfPackets.feedResult(session, false); return
        }
        var points = 0L
        for ((slot, qty) in payload) {
            val item = inventory.getBagItem(cid, slot)
            val itemId = item?.itemId ?: run { ElfPackets.feedResult(session, false); return }
            if (itemId !in setOf(46037, 46038, 46234) || item.quantity < qty) {
                ElfPackets.feedResult(session, false); return
            }
            val gain = itemDefs[itemId]?.skillLink ?: 0
            if (gain <= 0) { ElfPackets.feedResult(session, false); return }
            points += gain.toLong() * qty
        }
        val state = repo.load(cid)
        val old = state.progress.firstOrNull { it.elfNo == no } ?: ElfProgressRecord(no, 1, 0)
        if (old.level >= def.maxLevel || state.profile.feedTotal.toLong() + points > 65535L) {
            ElfPackets.feedResult(session, false); return
        }
        var lv = old.level
        var exp = old.exp.toLong() + points
        while (lv < def.maxLevel && exp >= (catalog.expByLevel[lv] ?: Int.MAX_VALUE)) {
            exp -= catalog.expByLevel[lv] ?: Int.MAX_VALUE
            lv++
        }
        if (lv >= def.maxLevel) exp = 0
        val changed = ElfProgressRecord(no, lv, exp.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        // Advance progress first, then consume items. If consuming fails,
        // compensate database progress and deny the feeding acknowledgement.
        try {
            repo.setProgress(cid, changed, state.profile.feedTotal + points.toInt())
            if (!inventory.removeBagItemsExact(cid, payload)) {
                repo.setProgress(cid, old, state.profile.feedTotal)
                ElfPackets.feedResult(session, false)
                return
            }
        } catch (e: Exception) {
            elfLogger.error(e) { "Elf feeding/persistence failed cid=$cid no=$no" }
            try { repo.setProgress(cid, old, state.profile.feedTotal) }
            catch (undo: Exception) { elfLogger.error(undo) { "CRITICAL Elf rollback failed cid=$cid" } }
            ElfPackets.feedResult(session, false)
            return
        }
        payload.forEach { (slot, qty) -> responses.sendReduceBagItem(session, slot, qty) }
        ElfPackets.feedResult(session, true)
        ElfPackets.progress(session, changed)
    }
}

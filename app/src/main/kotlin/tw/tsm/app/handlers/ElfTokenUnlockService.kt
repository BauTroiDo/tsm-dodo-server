package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.ElfProgressRecord
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.InventorySystem
import java.util.concurrent.ConcurrentHashMap

private val elfTokenLogger = KotlinLogging.logger {}

/**
 * DVT PC: ItemUse_239 is the consumable Elf acquisition token.
 * IOS_Item_C.dat: 31901..31924 -> Elf_C.dat no=1..24; 31925..31928 are
 * forward-version tokens absent from current Elf_C.dat and MUST remain unused.
 *
 * The loot bag (ItemUse_48) only awards a ticket with 2% odds. This handler
 * deliberately does not change RNG chances or grant an Elf merely for opening a bag.
 */
class ElfTokenUnlockService(
    private val bitFlags: BitFlagSystem,
    private val inventory: InventorySystem,
    private val catalog: ElfCatalog,
    private val responseSender: ResponseSender,
    private val itemDefs: Map<Int, ItemDef>,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun redeem(
        session: GameSession, bagIndex: Int, requestedQuantity: Int, followIndex: Int
    ): Boolean = locks.computeIfAbsent(session.roleId.toLong()) { Mutex() }.withLock {
        val charId = session.roleId.toLong()
        if (requestedQuantity != 1 || followIndex != 0 || bagIndex < 0) return@withLock false
        val item = inventory.getBagItem(charId, bagIndex) ?: return@withLock false
        if (item.quantity < 1) return@withLock false
        val no = item.itemId - TOKEN_BASE
        val def = catalog.defs[no] ?: return@withLock false
        val itemDef = itemDefs[item.itemId] ?: return@withLock false
        if (no !in 1..24 || def.no != no || def.ownershipFlag <= 0 ||
            itemDef.specialAbility != EItemUseKind.ELF_TOKEN || itemDef.kind != ELF_ITEM_KIND
        ) return@withLock false

        val previouslyOwned = try {
            def.ownershipFlag in bitFlags.getSetFlagIds(charId)
        } catch (e: Exception) {
            elfTokenLogger.error(e) { "Elf ownership lookup failed cid=$charId" }
            return@withLock false
        }
        if (previouslyOwned) {
            elfTokenLogger.info {
                "Elf ticket kept: already owned cid=$charId no=$no itemId=${item.itemId}"
            }
            return@withLock false
        }

        try {
            bitFlags.setFlag(charId, def.ownershipFlag)
        } catch (e: Exception) {
            elfTokenLogger.error(e) { "Elf ownership DB persistence failed cid=$charId no=$no" }
            return@withLock false
        }

        val consumed = try {
            inventory.removeBagItemsExact(charId, listOf(bagIndex to 1))
        } catch (e: Exception) {
            elfTokenLogger.error(e) { "Elf token consume failed cid=$charId no=$no" }
            false
        }
        if (!consumed) {
            try {
                bitFlags.clearFlag(charId, def.ownershipFlag)
            } catch (rollback: Exception) {
                elfTokenLogger.error(rollback) {
                    "CRITICAL Elf token flag rollback failed cid=$charId no=$no"
                }
            }
            return@withLock false
        }

        // Persisted flag now drives UIElf.GetTotalElfCount and the S107-005
        // snapshot that is requested every time the player opens the Elf menu.
        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, item.itemId, 1, showMsg = true)
        responseSender.sendBitFlagChanges(session, listOf(def.ownershipFlag to true))
        ElfPackets.acquired(session, no)
        ElfPackets.progress(session, ElfProgressRecord(no, 1, 0))

        elfTokenLogger.info {
            "Elf ticket redeemed cid=$charId no=$no flag=${def.ownershipFlag} itemId=${item.itemId}"
        }
        true
    }

    companion object {
        const val TOKEN_BASE = 31900
        private const val ELF_ITEM_KIND = 93
    }
}

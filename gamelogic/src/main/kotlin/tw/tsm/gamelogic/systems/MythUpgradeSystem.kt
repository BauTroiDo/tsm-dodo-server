package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap

private val mythUpgradeLogger = KotlinLogging.logger {}

data class MythUpgradeResult(
    val code: Int,
    val mythSlot: Int,
    val supportSlot: Int,
    val updatedMyth: ThingData? = null,
    val supportRemaining: ThingData? = null,
) {
    val success: Boolean get() = code == RESULT_SUCCESS

    companion object {
        const val RESULT_INVALID = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_RATE_FAIL = 2
        const val RESULT_NOT_ENOUGH_CURRENCY = 3
        const val RESULT_GENERIC_ERROR = 255
    }
}

/** DVT UIGodskin C:108-001 deterministic Godskin-stone path only. */
class MythUpgradeSystem(
    private val inventorySystem: InventorySystem,
    private val itemDefs: Map<Int, ItemDef>,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun upgradeWithStone(
        characterId: Long,
        mythSlot: Int,
        supportSlot: Int,
    ): MythUpgradeResult {
        if (characterId <= 0 || mythSlot < 0 || supportSlot <= 0 || mythSlot == supportSlot) {
            return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        }
        return locks.computeIfAbsent(characterId) { Mutex() }.withLock {
            upgradeWithStoneLocked(characterId, mythSlot, supportSlot)
        }
    }

    private suspend fun upgradeWithStoneLocked(
        characterId: Long,
        mythSlot: Int,
        supportSlot: Int,
    ): MythUpgradeResult {
        val mythThing = inventorySystem.getBagItem(characterId, mythSlot)
            ?: return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        val supportThing = inventorySystem.getBagItem(characterId, supportSlot)
            ?: return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        val mythDef = itemDefs[mythThing.itemId]
            ?: return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        val supportDef = itemDefs[supportThing.itemId]
            ?: return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)

        if (mythDef.kind != EItemKind.MYTH || mythThing.mythLevel !in 0..14) {
            return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        }
        if (supportThing.quantity <= 0 ||
            supportDef.kind != EItemKind.SPECIAL_ABILITY ||
            supportDef.specialAbility != EItemUseKind.GODSKIN_STONE
        ) {
            return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        }

        val targetLevel = supportDef.skillLink
        if (targetLevel !in 1..15 || targetLevel <= mythThing.mythLevel) {
            return MythUpgradeResult(MythUpgradeResult.RESULT_INVALID, mythSlot, supportSlot)
        }

        val updatedMyth = mythThing.copy(mythLevel = targetLevel)
        val supportRemaining =
            if (supportThing.quantity <= 1) null else supportThing.copy(quantity = supportThing.quantity - 1)

        inventorySystem.setBagItemsAtomic(
            characterId,
            linkedMapOf(
                mythSlot to updatedMyth,
                supportSlot to supportRemaining,
            )
        )

        mythUpgradeLogger.info {
            "Myth stone upgrade success: char=$characterId mythSlot=$mythSlot " +
                "supportSlot=$supportSlot lv=${mythThing.mythLevel}->$targetLevel"
        }
        return MythUpgradeResult(
            code = MythUpgradeResult.RESULT_SUCCESS,
            mythSlot = mythSlot,
            supportSlot = supportSlot,
            updatedMyth = updatedMyth,
            supportRemaining = supportRemaining,
        )
    }
}

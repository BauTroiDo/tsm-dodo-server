package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val mythLogger = KotlinLogging.logger {}

data class MythEnchantResult(
    val code: Int,
    val mythSlot: Int,
    val runeSlot: Int,
    val updatedMyth: ThingData? = null,
    val runeRemaining: ThingData? = null,
    val tsBalance: Int = 0
) {
    val success: Boolean get() = code == RESULT_SUCCESS

    companion object {
        const val RESULT_SUCCESS = 1
        const val RESULT_STAR_TOO_HIGH = 2
        const val RESULT_NOT_ENOUGH_TS = 3
        const val RESULT_NO_MATCHING_ATTRIBUTE = 4
        const val RESULT_INVALID = 0
    }
}

/** DVT UIGodskin C:108-002 Myth Enchant. */
class MythEnchantSystem(
    private val inventorySystem: InventorySystem,
    private val currencySystem: PlayerCurrencySystem,
    private val itemDefs: Map<Int, ItemDef>,
    private val rollInclusive: (Int, Int) -> Int = { low, high ->
        if (low >= high) low else Random.nextInt(low, high + 1)
    }
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    suspend fun enchant(characterId: Long, mythSlot: Int, runeSlot: Int): MythEnchantResult {
        if (characterId <= 0 || mythSlot < 0 || runeSlot < 0 || mythSlot == runeSlot) {
            return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        }
        val mutex = locks.computeIfAbsent(characterId) { Mutex() }
        return mutex.withLock { enchantLocked(characterId, mythSlot, runeSlot) }
    }

    private suspend fun enchantLocked(
        characterId: Long,
        mythSlot: Int,
        runeSlot: Int
    ): MythEnchantResult {
        val mythThing = inventorySystem.getBagItem(characterId, mythSlot)
            ?: return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        val runeThing = inventorySystem.getBagItem(characterId, runeSlot)
            ?: return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)

        val mythDef = itemDefs[mythThing.itemId]
            ?: return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        val runeDef = itemDefs[runeThing.itemId]
            ?: return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)

        if (mythDef.kind != EItemKind.MYTH || runeDef.kind != EItemKind.ENCHANT || runeThing.quantity <= 0) {
            return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        }
        if (runeDef.threshold > mythDef.threshold) {
            return MythEnchantResult(MythEnchantResult.RESULT_STAR_TOO_HIGH, mythSlot, runeSlot)
        }

        val mythIndex = mythThing.myth.indexOfFirst { it.kind == runeDef.attr1Kind }
        if (mythIndex < 0 || runeDef.attr1Kind == 0) {
            return MythEnchantResult(MythEnchantResult.RESULT_NO_MATCHING_ATTRIBUTE, mythSlot, runeSlot)
        }
        if (currencySystem.get(characterId, TS_CURRENCY_KIND) < ENCHANT_TS) {
            return MythEnchantResult(MythEnchantResult.RESULT_NOT_ENOUGH_TS, mythSlot, runeSlot)
        }

        val low = min(runeDef.attr1Value, runeDef.attr2Value)
        val high = max(runeDef.attr1Value, runeDef.attr2Value)
        if (low < 0 || high > 0xFFFF) {
            return MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        }

        val newExtra = rollInclusive(low, high).coerceIn(low, high)
        val updatedAttrs = mythThing.myth.toMutableList()
        val oldAttr = updatedAttrs[mythIndex]
        updatedAttrs[mythIndex] = MythAttribute(
            kind = oldAttr.kind,
            value = oldAttr.value,
            extra = newExtra
        )
        val updatedMyth = mythThing.copy(myth = updatedAttrs)
        val runeRemaining = if (runeThing.quantity <= 1) null else runeThing.copy(quantity = runeThing.quantity - 1)

        if (!currencySystem.subtract(characterId, TS_CURRENCY_KIND, ENCHANT_TS)) {
            return MythEnchantResult(MythEnchantResult.RESULT_NOT_ENOUGH_TS, mythSlot, runeSlot)
        }

        try {
            inventorySystem.setBagItemsAtomic(
                characterId,
                linkedMapOf(mythSlot to updatedMyth, runeSlot to runeRemaining)
            )
        } catch (t: Throwable) {
            try {
                currencySystem.add(characterId, TS_CURRENCY_KIND, ENCHANT_TS)
            } catch (refundError: Throwable) {
                mythLogger.error(refundError) {
                    "Myth enchant TS refund failed: char=${characterId} mythSlot=${mythSlot} runeSlot=${runeSlot}"
                }
            }
            throw t
        }

        val balance = currencySystem.get(characterId, TS_CURRENCY_KIND)
        mythLogger.info {
            "Myth enchant success: char=${characterId} mythSlot=${mythSlot} runeSlot=${runeSlot} " +
                "attr=${oldAttr.kind} extra=${oldAttr.extra}->${newExtra} ts=${balance}"
        }
        return MythEnchantResult(
            code = MythEnchantResult.RESULT_SUCCESS,
            mythSlot = mythSlot,
            runeSlot = runeSlot,
            updatedMyth = updatedMyth,
            runeRemaining = runeRemaining,
            tsBalance = balance
        )
    }

    companion object {
        const val TS_CURRENCY_KIND = 8
        const val ENCHANT_TS = 10
    }
}

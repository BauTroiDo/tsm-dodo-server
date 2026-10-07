package tw.tsm.gamelogic.systems

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.tsm.domain.data.FNpcSlotDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import kotlin.random.Random

data class ExpSlotAward(
    val kind: Int,
    val exp: Int,
    val icons: List<Int>,
)

data class ExpSlotSpinResult(
    val awards: List<ExpSlotAward>,
    val gain: PetSystem.PetExpGain,
    val jackpotExtra: Long,
    val jackpotAfter: Long,
)

class ExpSlotSystem(
    defs: Map<Int, FNpcSlotDef>,
    private val repository: ExpSlotRepository,
    private val petSystem: PetSystem,
    private val npcDefs: Map<Int, NpcDef>,
) {
    private val rows = defs.values.sortedBy { it.index }
    private val jackpotMutex = Mutex()

    fun isSellId(sellId: Int): Boolean = sellId in SELL_IDS

    fun requirement(sellId: Int): Pair<Int, Int>? = when (sellId) {
        SELL_NORMAL_ONE -> NORMAL_COIN_ITEM_ID to 1
        SELL_NORMAL_TEN -> NORMAL_COIN_ITEM_ID to 10
        SELL_TURN2_ONE -> TURN2_COIN_ITEM_ID to 1
        SELL_TURN2_TEN -> TURN2_COIN_ITEM_ID to 10
        else -> null
    }

    fun validateTarget(characterId: Long, sellId: Int, args: List<Int>): Boolean {
        if (args.size != 2) return false
        val slot = args[0]
        val npcId = args[1]
        val pet = petSystem.getFollowPets(characterId)[slot] ?: return false
        if (pet.npcId != npcId) return false
        // Lua UIExpSlotMachine.CheckPull blocks exactly canGrow 0 or 2.
        if (pet.canGrow == 0 || pet.canGrow == 2) return false
        if (npcDefs[npcId]?.kind == 53) return false
        if (petSystem.getCache(characterId)?.fightNpcId == npcId) return false
        // Lua UIExpSlotMachine.CheckPull only blocks level >= 180 while turn <= 2.
        if (pet.turnKind <= 2 && pet.level >= NORMAL_MAX_LEVEL) return false
        val turn2Branch = sellId == SELL_TURN2_ONE || sellId == SELL_TURN2_TEN
        if (turn2Branch != (pet.turnKind >= 2)) return false
        return rows.isNotEmpty()
    }

    suspend fun jackpotExp(): Long = jackpotMutex.withLock {
        repository.loadJackpotExp().coerceAtLeast(0L)
    }

    suspend fun spin(characterId: Long, sellId: Int, args: List<Int>): ExpSlotSpinResult? {
        if (!validateTarget(characterId, sellId, args)) return null
        val spins = requirement(sellId)?.second ?: return null
        val awards = List(spins) {
            val row = rows[Random.nextInt(rows.size)]
            ExpSlotAward(
                kind = if (sellId == SELL_TURN2_ONE || sellId == SELL_TURN2_TEN) 1 else 0,
                exp = row.exp,
                icons = row.icons,
            )
        }

        val jackpotIndex = awards.indexOfFirst { it.exp >= JACKPOT_BASE_EXP }
        if (jackpotIndex >= 0) {
            return jackpotMutex.withLock {
                val pool = repository.loadJackpotExp()
                    .coerceIn(0L, Int.MAX_VALUE.toLong() - awards[jackpotIndex].exp.toLong())
                val adjusted = awards.toMutableList()
                adjusted[jackpotIndex] = adjusted[jackpotIndex].copy(
                    exp = (adjusted[jackpotIndex].exp.toLong() + pool).toInt()
                )
                val totalExp = adjusted.sumOf { it.exp.toLong() }
                val gain = petSystem.gainBattleExp(characterId, args[0], totalExp)
                    ?: return@withLock null
                petSystem.save(characterId)
                repository.saveJackpotExp(0L)
                ExpSlotSpinResult(adjusted, gain, pool, 0L)
            }
        }

        val gain = petSystem.gainBattleExp(
            characterId,
            args[0],
            awards.sumOf { it.exp.toLong() }
        ) ?: return null
        petSystem.save(characterId)
        return ExpSlotSpinResult(awards, gain, 0L, jackpotExp())
    }

    companion object {
        const val SELL_NORMAL_ONE = 37
        const val SELL_NORMAL_TEN = 38
        const val SELL_TURN2_ONE = 65
        const val SELL_TURN2_TEN = 66

        const val NORMAL_COIN_ITEM_ID = 31207
        const val TURN2_COIN_ITEM_ID = 29877
        const val NORMAL_MAX_LEVEL = 180
        const val JACKPOT_BASE_EXP = 5_000_000

        val SELL_IDS = setOf(SELL_NORMAL_ONE, SELL_NORMAL_TEN, SELL_TURN2_ONE, SELL_TURN2_TEN)
    }
}

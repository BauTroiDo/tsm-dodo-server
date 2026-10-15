package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.persistence.tables.Characters
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterRoleCounts

enum class OfflineClaimCurrency { FREE, GAME_GOLD, PREMIUM_POINTS }

data class OfflineClaimRequest(
    val charId: Long,
    val minutesExpected: Int,
    val percentExpected: Int,
    val expBefore: Long,
    val expAdded: Long,
    val oldLevel: Int,
    val newLevel: Int,
    val oldTurn: Int,
    val attrPointBefore: Int,
    val skillPointBefore: Int,
    val price: Int,
    val balanceExpected: Int,
    val currency: OfflineClaimCurrency,
)

data class OfflineClaimCommit(
    val expAfter: Long,
    val levelAfter: Int,
    val attrPointAfter: Int,
    val skillPointAfter: Int,
    val currencyAfter: Int,
)

/**
 * One database transaction is the durable redemption fence for the DVT offline
 * welfare feature. The runtime adapter MUST keep its normal MoneySystem/PointSystem
 * wallet writer fences and state lock before calling this SQL operation.
 *
 * This class is not attached to UISell yet: DVT 1.15 prices are not verified.
 */
class OfflineExpClaimRepository {
    suspend fun tryCommit(request: OfflineClaimRequest): OfflineClaimCommit? {
        if (request.charId <= 0L ||
            request.minutesExpected !in 1..1440 ||
            request.percentExpected !in 1..65535 ||
            request.oldLevel !in 1..250 ||
            request.oldTurn !in 0..3 ||
            request.newLevel !in request.oldLevel..(if (request.oldTurn >= 3) 250 else 200) ||
            request.expBefore < 0L ||
            request.expAdded <= 0L ||
            request.price < 0 ||
            request.balanceExpected < 0 ||
            request.attrPointBefore < 0 ||
            request.skillPointBefore < 0 ||
            (request.currency == OfflineClaimCurrency.FREE && request.price != 0) ||
            (request.currency != OfflineClaimCurrency.FREE && request.price == 0)
        ) return null
        val expAfter = try { Math.addExact(request.expBefore, request.expAdded) }
            catch (_: ArithmeticException) { return null }
        val levels = request.newLevel - request.oldLevel
        val attrAfter = try {
            Math.addExact(request.attrPointBefore, if (request.oldTurn == 3) 0 else levels * 3)
        } catch (_: ArithmeticException) { return null }
        val skillAfter = try {
            Math.addExact(request.skillPointBefore, if (request.oldTurn == 3) 0 else levels * 2)
        } catch (_: ArithmeticException) { return null }

        return suspendTransaction {
            val character = Characters.selectAll().where {
                Characters.id eq request.charId
            }.forUpdate().singleOrNull() ?: return@suspendTransaction null
            if (character[Characters.exp] != request.expBefore ||
                character[Characters.level] != request.oldLevel ||
                character[Characters.turn] != request.oldTurn ||
                character[Characters.attrPoint] != request.attrPointBefore ||
                character[Characters.skillPoint] != request.skillPointBefore
            ) return@suspendTransaction null

            fun count(index: Int) = CharacterRoleCounts.selectAll().where {
                (CharacterRoleCounts.charId eq request.charId) and
                    (CharacterRoleCounts.countIndex eq index)
            }.singleOrNull()?.get(CharacterRoleCounts.value) ?: 0

            if (count(169) != request.percentExpected ||
                count(171) != request.minutesExpected
            ) return@suspendTransaction null

            val walletAfter = when (request.currency) {
                OfflineClaimCurrency.FREE -> 0
                OfflineClaimCurrency.GAME_GOLD -> {
                    val wallet = CharacterMoney.selectAll().where {
                        CharacterMoney.characterId eq request.charId
                    }.singleOrNull() ?: return@suspendTransaction null
                    val balance = wallet[CharacterMoney.gold]
                    if (balance != request.balanceExpected || balance < request.price)
                        return@suspendTransaction null
                    balance - request.price
                }
                OfflineClaimCurrency.PREMIUM_POINTS -> {
                    val balance = character[Characters.pointReal]
                    if (balance != request.balanceExpected || balance < request.price)
                        return@suspendTransaction null
                    balance - request.price
                }
            }

            // No partial EXP or currency changes can escape this transaction.
            Characters.update({ Characters.id eq request.charId }) {
                it[Characters.exp] = expAfter
                it[Characters.level] = request.newLevel
                it[Characters.attrPoint] = attrAfter
                it[Characters.skillPoint] = skillAfter
                if (request.currency == OfflineClaimCurrency.PREMIUM_POINTS) {
                    it[Characters.pointReal] = walletAfter
                }
            }
            if (request.currency == OfflineClaimCurrency.GAME_GOLD) {
                CharacterMoney.update({ CharacterMoney.characterId eq request.charId }) {
                    it[CharacterMoney.gold] = walletAfter
                }
            }

            // Consumption of both 169 and 171 is the idempotency receipt.
            // RoleCount[172] is the DAILY cap and intentionally NOT reset.
            for (index in listOf(169,171)) {
                CharacterRoleCounts.update({
                    (CharacterRoleCounts.charId eq request.charId) and
                        (CharacterRoleCounts.countIndex eq index)
                }) { it[CharacterRoleCounts.value] = 0 }
            }
            OfflineClaimCommit(expAfter,request.newLevel,attrAfter,skillAfter,walletAfter)
        }
    }
}

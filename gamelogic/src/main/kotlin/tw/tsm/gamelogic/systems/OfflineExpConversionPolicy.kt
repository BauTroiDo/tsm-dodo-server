package tw.tsm.gamelogic.systems

import java.math.BigInteger
import tw.tsm.protocol.PacketWriter

/**
 * Offline EXP conversion contract for C087-003/S087-003 and UISell 28/29/30.
 *
 * Deliberately separated from payments and character mutations: DVT 1.15's
 * authoritative tier prices have not been recovered. A caller must supply
 * verified rates and costs; no default or silent guessed economy is enabled.
 */
object OfflineExpConversionPolicy {
    const val FREE_SELL_ID = 30
    const val GAME_GOLD_SELL_ID = 28
    const val PREMIUM_SELL_ID = 29
    const val MAX_DAILY_MINUTES = 480
    const val MAX_UNCLAIMED_MINUTES = 1440

    enum class Currency { NONE, GAME_GOLD, PREMIUM_POINTS }

    data class Tier(
        val sellId: Int,
        val ratePercent: Int,
        val currency: Currency,
        val cost: Long,
    ) {
        init {
            require(sellId in setOf(FREE_SELL_ID, GAME_GOLD_SELL_ID, PREMIUM_SELL_ID))
            require(ratePercent in 1..100)
            require(cost in 0..UInt.MAX_VALUE.toLong())
            require((currency == Currency.NONE) == (cost == 0L))
        }
    }

    data class Formula(
        val basisPointsPerMinute: Int,
        val freeRate: Int,
        val gameGoldRate: Int,
        val premiumRate: Int,
        val gameGoldPer100Minutes: Long,
        val premiumPointsPer100Minutes: Long,
    ) {
        init {
            require(basisPointsPerMinute in 1..100)
            require(freeRate in 1..100)
            require(gameGoldRate in freeRate..100)
            require(premiumRate in gameGoldRate..100)
            require(gameGoldPer100Minutes > 0L)
            require(premiumPointsPer100Minutes > 0L)
        }
    }

    /** Return no offers unless all DVT rates/prices are explicitly configured. */
    fun fromEnvironment(env: Map<String, String>): Formula? {
        val keys = listOf(
            "DVT_OFFLINE_EXP_BP_PER_MIN",
            "DVT_OFFLINE_EXP_FREE_RATE",
            "DVT_OFFLINE_EXP_GAME_GOLD_RATE",
            "DVT_OFFLINE_EXP_PREMIUM_RATE",
            "DVT_OFFLINE_EXP_GAME_GOLD_PER_100MIN",
            "DVT_OFFLINE_EXP_PREMIUM_PER_100MIN",
        )
        if (env["DVT_OFFLINE_EXP_ENABLED"] != "true") return null
        val numbers = keys.map { env[it]?.trim()?.toLongOrNull() ?: return null }
        if (numbers.any { it > Int.MAX_VALUE || it < 0 }) return null
        return try {
            Formula(
                numbers[0].toInt(), numbers[1].toInt(),
                numbers[2].toInt(), numbers[3].toInt(),
                numbers[4], numbers[5],
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun offers(minutes: Int, formula: Formula): List<Tier> {
        require(minutes in 1..MAX_UNCLAIMED_MINUTES)
        val blocks = (minutes.toLong() + 99L) / 100L
        val gold = Math.multiplyExact(blocks, formula.gameGoldPer100Minutes)
        val point = Math.multiplyExact(blocks, formula.premiumPointsPer100Minutes)
        require(gold in 1..UInt.MAX_VALUE.toLong())
        require(point in 1..UInt.MAX_VALUE.toLong())
        return listOf(
            Tier(FREE_SELL_ID, formula.freeRate, Currency.NONE, 0),
            Tier(GAME_GOLD_SELL_ID, formula.gameGoldRate, Currency.GAME_GOLD, gold),
            Tier(PREMIUM_SELL_ID, formula.premiumRate, Currency.PREMIUM_POINTS, point),
        )
    }

    /** Returns earned whole percent for RoleCount[169], safely capped to 16 bits. */
    fun earnedWholePercent(minutes: Int, formula: Formula): Int {
        require(minutes in 0..MAX_UNCLAIMED_MINUTES)
        return (minutes.toLong() * formula.basisPointsPerMinute / 100L)
            .coerceAtMost(65535L).toInt()
    }

    /**
     * EXP is a fraction of the *current level's* Calc.GetUpExp,
     * not a fraction of accumulated lifetime EXP.
     */
    fun rewardExp(currentLevelUpExp: Long, minutes: Int, tier: Tier, formula: Formula): Long {
        require(currentLevelUpExp >= 0)
        require(minutes in 0..MAX_UNCLAIMED_MINUTES)
        val basisPoints = minutes.toLong() * formula.basisPointsPerMinute
        val numerator = BigInteger.valueOf(currentLevelUpExp)
            .multiply(BigInteger.valueOf(basisPoints))
            .multiply(BigInteger.valueOf(tier.ratePercent.toLong()))
        val result = numerator.divide(BigInteger.valueOf(1_000_000L))
        return result.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }

    /** S087-003: count(U8), count×[rate(U16LE), cost(U32LE)]. */
    fun packetForOffers(tiers: List<Tier>): ByteArray {
        require(tiers.size <= 3)
        val writer = PacketWriter(4 + tiers.size * 6)
            .writeByte(87).writeByte(3).writeByte(0).writeByte(tiers.size)
        for (tier in tiers) {
            writer.writeShortLE(tier.ratePercent)
            writer.writeIntLE(tier.cost.toInt())
        }
        return writer.toByteArray()
    }
}

package tw.tsm.domain.interfaces.repositories

/**
 * PRIVATE SERVER economy, NOT original DVT backend rates. Keep disabled unless
 * explicitly enabled by DEV operator. All amounts and quota are configurable.
 * 360 seconds per fullness unit and 600M reserve remain source-backed UI values.
 */
data class CustomMiningRules(
    val enabled: Boolean = false,
    val workerTsPerTick: List<Int> = listOf(1, 2, 3, 4),
    val dailyTsCap: Int = 120,
    val zoneId: String = "Asia/Ho_Chi_Minh",
) {
    init {
        require(workerTsPerTick.size == 4 && workerTsPerTick.all { it in 1..100000 })
        require(dailyTsCap in 1..1000000)
        java.time.ZoneId.of(zoneId)
    }
    companion object {
        fun fromEnvironment(env: Map<String,String> = System.getenv()): CustomMiningRules {
            val raw = env["DVT_CUSTOM_MINE_RATES"]
            val rates = raw?.split(',')?.map { it.trim().toInt() } ?: listOf(1,2,3,4)
            return CustomMiningRules(
                enabled = env["DVT_CUSTOM_MINE_ENABLED"]?.equals("true",true) == true,
                workerTsPerTick = rates,
                dailyTsCap = env["DVT_CUSTOM_MINE_DAILY_TS"]?.toInt() ?: 120,
                zoneId = env["DVT_CUSTOM_MINE_ZONE"] ?: "Asia/Ho_Chi_Minh",
            )
        }
    }
}

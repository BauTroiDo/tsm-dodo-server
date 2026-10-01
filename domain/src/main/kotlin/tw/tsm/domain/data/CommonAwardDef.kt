package tw.tsm.domain.data

/** Exact DVT CommonAwardData/LuckyBagData payloads; selection policy belongs to server configuration. */
data class CommonAwardEntry(val kind: Int, val itemId: Int, val count: Long)
data class LuckyBagEntry(val block: Int, val itemId: Int, val count: Long)
data class LuckyBagDef(val nameId: Long, val iconId: Int, val entries: List<LuckyBagEntry>)

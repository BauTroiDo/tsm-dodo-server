package tw.tsm.domain.data

/**
 * DVT CollectWarrior static row.
 *
 * [id] is also the NPC ID used by npcDatas[id] on the client.
 * [getFlag] marks that the player has owned this warrior at least once.
 * [rewardFlag] marks that the collection reward for this warrior was claimed.
 */
data class CollectWarriorDef(
    val id: Int,
    val getFlag: Int,
    val rewardFlag: Int,
    val gainStr: Long,
    val awards: List<Pair<Int, Int>>,
    val shineValue: Int,
)

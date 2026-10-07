package tw.tsm.gamelogic.systems

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.data.ItemDef

data class TeamDungeonBotProfile(
    val name: String,
    val hairOffset: Int,
    val color1Xor: Int,
    val color2Xor: Int,
    val petNpcId: Int,
    val outfitItemIds: IntArray = intArrayOf(),
)

/**
 * Offline fallback for normal DVT team dungeons.
 *
 * The DVT client room protocol is authoritative, but no original server-side
 * auto-fill policy is present in the client. Keep this extension deliberately
 * narrow: bots are offered only while exactly one real player occupies a
 * normal multiplayer room. As soon as another real player joins, the room
 * falls back to ordinary multiplayer.
 */
object TeamDungeonBotPolicy {
    const val BOT_FILL_DELAY_MS = 5_000L

    private val BASE_PROFILES = listOf(
        TeamDungeonBotProfile("Huyết Kiếm", 1, 0x00060C18, 0x00120804, 40428),
        TeamDungeonBotProfile("Phong Vân", 2, 0x00100408, 0x00081210, 40429),
        TeamDungeonBotProfile("Băng Tâm", 3, 0x000C1404, 0x00100414, 40430),
        TeamDungeonBotProfile("Thiên Long", 4, 0x0014080C, 0x000C1018, 40431),
    )

    @Volatile
    private var profiles: List<TeamDungeonBotProfile> = BASE_PROFILES

    /**
     * Build four appearance-only outfits from authoritative Item_C.dat.
     * Only items with both male/female sprites and basic fitType 1..5 are used.
     * These IDs are serialized for visuals only; bot combat stats remain cloned
     * from the host template and do not consume/equip the real items.
     */
    fun configureOutfits(itemDefs: Map<Int, ItemDef>) {
        val bySlot = (1..5).associateWith { slot ->
            itemDefs.asSequence()
                .filter { (_, def) ->
                    def.fitType == slot &&
                        def.malePicId > 0 &&
                        def.femalePicId > 0 &&
                        def.openUsed == 0
                }
                .map { it.key }
                .sorted()
                .toList()
        }
        if (bySlot.values.any { it.size < BASE_PROFILES.size }) return

        profiles = BASE_PROFILES.mapIndexed { profileIndex, base ->
            val outfit = IntArray(5) { slotIndex ->
                val pool = bySlot.getValue(slotIndex + 1)
                val stride = (pool.size / BASE_PROFILES.size).coerceAtLeast(1)
                pool[(profileIndex * stride + slotIndex * 3).mod(pool.size)]
            }
            base.copy(outfitItemIds = outfit)
        }
    }

    fun visibleBotCount(kind: Int, realCount: Int, roomMaxPlayers: Int): Int {
        if (kind !in setOf(
                DungeonSystem.KIND_NORMAL_MULTIPLAYER,
                DungeonSystem.KIND_WEEKLY,
                DungeonSystem.KIND_SOUL_WEEKLY,
            )
        ) return 0
        if (realCount != 1 || roomMaxPlayers <= 1) return 0
        return (roomMaxPlayers - realCount)
            .coerceAtMost(BattleConstants.MAX_ROW - 1)
            .coerceAtLeast(0)
    }

    fun profile(index: Int): TeamDungeonBotProfile = profiles[index.mod(profiles.size)]

    fun assistMatchesInstance(assistInstanceId: Int, currentInstanceId: Int): Boolean =
        assistInstanceId > 0 && assistInstanceId == currentInstanceId
}

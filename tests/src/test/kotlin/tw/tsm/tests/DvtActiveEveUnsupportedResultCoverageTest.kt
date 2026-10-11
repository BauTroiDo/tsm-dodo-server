package tw.tsm.tests

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader

/**
 * Pin the unsupported-result audit to the EXACT active client/server Eve.emg,
 * not the older local reference archived under TSMobileOnLine/tsm_dodo.
 */
class DvtActiveEveUnsupportedResultCoverageTest {
    @Test fun activeEveHas35CityResourceAnd89ActivityRewardButNoClass15() {
        val file = listOf(
            Path.of("gamedata", "Eve.emg"),
            Path.of("..", "gamedata", "Eve.emg"),
        ).first { Files.isRegularFile(it) }
        val binary = Files.readAllBytes(file)
        val scenes = EveDataLoader.load(binary)
        assertEquals(3797, scenes.size, "Only populated scenes are retained by the Kotlin loader")

        // The Eve container indexes 3823 scenes. Exactly 26 are legitimate,
        // entirely empty 123-byte scene blocks (103-byte header, followed by
        // 20 zero bytes for all nine section counters). Kotlin omits these.
        val reader = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        val archiveCount = reader.short.toInt() and 0xffff
        assertEquals(3823, archiveCount)
        val entries = mutableMapOf<Int, Pair<Int, Int>>()
        repeat(archiveCount) {
            val nameLength = reader.get().toInt() and 0xff
            val nameBytes = ByteArray(23)
            reader.get(nameBytes)
            val position = reader.int
            val size = reader.int
            val id = String(nameBytes, 0, nameLength.coerceAtMost(23), Charsets.US_ASCII)
                .removeSuffix(".eve").toIntOrNull()
            if (id != null) entries[id] = position to size
        }
        val omitted = entries.keys - scenes.keys
        val knownEmpty = setOf(18281, 19510, 49993, 49994, 49995, 57537) +
            (54948..54967).toSet()
        assertEquals(26, omitted.size)
        assertEquals(knownEmpty, omitted, "A newly missing populated scene is a game regression")
        for (id in omitted) {
            val (position, size) = entries.getValue(id)
            assertEquals(123, size, "Unexpected data length for omitted Eve scene $id")
            assertTrue(binary.copyOfRange(position + 103, position + size)
                .all { it == 0.toByte() }, "Omitted scene $id contains real event data")
        }

        val results = scenes.values.flatMap { scene ->
            scene.npcEvents.values.flatMap { event ->
                event.conditions.flatMap { it.results }
            }
        }

        val toServer = results.filter { it.resultType == 0 || it.resultType == 7 }
        assertEquals(35, toServer.count { it.resultClass == 11 },
            "Never forget 35 unresolved city-resource mutations")
        assertEquals(89, toServer.count { it.resultClass == 13 },
            "Never fabricate 89 missing activity-reward packages")
        assertEquals(0, results.count { it.resultClass == 15 },
            "Old class15/10821 note was stale; not present in active DVT Eve")
    }
}

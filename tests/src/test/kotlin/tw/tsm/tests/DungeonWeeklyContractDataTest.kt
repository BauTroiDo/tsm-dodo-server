package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonComboDatLoader
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.MarkDatLoader

class DungeonWeeklyContractDataTest {
    private fun root(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Dungeon_C.dat")) }

    private fun bytes(name: String): ByteArray =
        Files.readAllBytes(root().resolve("File.dat/Data-ios").resolve(name))

    @Test
    fun weeklyCountersAreKind9AndUseScene62015WithoutEveMission() {
        val dungeons = DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat"))
        val marks = MarkDatLoader.load(bytes("IOS_Mark_C.dat")).first

        for ((index, id) in (18..37).withIndex()) {
            val def = dungeons.getValue(id)
            assertEquals(3, def.kind)
            assertEquals(62015, def.sceneId)
            assertEquals(0, def.missionFlag)
            assertEquals(30, def.timeMinutes)
            assertEquals(12722 + index * 2, def.dailyFlag)
            val mark = marks.getValue(def.dailyFlag)
            assertEquals(9, mark.kind)
            assertEquals(0, mark.bitId)
        }

        for ((index, id) in (63..82).withIndex()) {
            val def = dungeons.getValue(id)
            assertEquals(5, def.kind)
            assertEquals(62015, def.sceneId)
            assertEquals(0, def.missionFlag)
            assertEquals(30, def.timeMinutes)
            assertEquals(13328 + index * 2, def.dailyFlag)
            val mark = marks.getValue(def.dailyFlag)
            assertEquals(9, mark.kind)
            assertEquals(0, mark.bitId)
        }
    }

    @Test
    fun currentBachChienAllClearClaimIsExactAndSoulHasNone() {
        val dungeons = DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat"))
        val marks = MarkDatLoader.load(bytes("IOS_Mark_C.dat")).first

        val first = dungeons.getValue(18)
        assertEquals(12762, first.costPoint)
        assertEquals(34366, first.allPassPrize)
        assertEquals(9, marks.getValue(12762).kind)
        assertEquals(1706, marks.getValue(12762).bitId)

        for (id in 63..82) {
            assertEquals(0, dungeons.getValue(id).costPoint)
            assertEquals(0, dungeons.getValue(id).allPassPrize)
        }
    }

    @Test
    fun stageRewardTablesAreFixedByCurrentDvtData() {
        val dungeons = DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat"))
        val combos = DungeonComboDatLoader.load(bytes("IOS_DungeonCombo_C.dat"))

        for (id in 18..37) {
            val rows = combos.getValue(dungeons.getValue(id).lootTable)
            assertEquals(1, rows.size)
            val reward = requireNotNull(rows.single().rewardForTurn(0))
            assertEquals(29845, reward.itemId)
            assertEquals(1L, reward.quantity)
        }

        for ((stage, id) in (63..82).withIndex()) {
            val rows = combos.getValue(dungeons.getValue(id).lootTable)
            assertEquals(1, rows.size)
            val reward = requireNotNull(rows.single().rewardForTurn(0))
            assertEquals(29846, reward.itemId)
            assertEquals((stage + 1).toLong(), reward.quantity)
        }
    }
}

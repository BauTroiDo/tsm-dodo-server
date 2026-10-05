package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.domain.constants.EWarStyle
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DvtWeeklyDungeonFormation
import java.nio.file.Files
import java.nio.file.Path

class DungeonWeeklyBattleV2Test {
    private fun bytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", name))

    private val dungeons by lazy { DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat")) }
    private val npcs by lazy { NpcDatLoader.load(bytes("IOS_Npc_C.dat")) }

    @Test
    fun bachChienTwentyRostersResolveToRealNpcs() {
        assertEquals(24, EWarStyle.WEEK_DUNGEON)
        for (dungeonId in 18..37) {
            val ids = DvtWeeklyDungeonFormation.enemyIds(dungeonId)
            assertNotNull(ids, "dungeon=$dungeonId")
            assertEquals(10, ids!!.size)
            assertEquals(10, ids.distinct().size)
            assertTrue(ids.all(npcs::containsKey))
            assertEquals(dungeonId - 17, DvtWeeklyDungeonFormation.stageNumber(dungeonId))
        }

        assertEquals(
            listOf(40448, 40449, 40450, 40451, 40452, 40453, 40454, 40455, 40480, 40481),
            DvtWeeklyDungeonFormation.enemyIds(37),
        )
    }

    @Test
    fun soulWeeklyIsExactContiguousTwoHundredNpcBlock() {
        for (dungeonId in 63..82) {
            val stage = dungeonId - 62
            val expectedFirst = 40687 + (stage - 1) * 10
            val expected = (expectedFirst until expectedFirst + 10).toList()
            val actual = DvtWeeklyDungeonFormation.enemyIds(dungeonId)

            assertEquals(expected, actual, "dungeon=$dungeonId")
            assertTrue(actual!!.all(npcs::containsKey))
            assertEquals(stage, DvtWeeklyDungeonFormation.stageNumber(dungeonId))
        }

        val all = (63..82).flatMap { DvtWeeklyDungeonFormation.enemyIds(it)!! }
        assertEquals(200, all.size)
        assertEquals(200, all.distinct().size)
        assertEquals(40687, all.first())
        assertEquals(40886, all.last())
    }

    @Test
    fun weeklyObjectiveMatrixUsesDvtNeedKindsSixAndSeven() {
        assertEquals(3, DvtWeeklyDungeonFormation.objective(dungeons.getValue(21)).maxRound)
        assertEquals(10, DvtWeeklyDungeonFormation.objective(dungeons.getValue(22)).minRound)
        assertEquals(20, DvtWeeklyDungeonFormation.objective(dungeons.getValue(36)).maxRound)
        assertEquals(20, DvtWeeklyDungeonFormation.objective(dungeons.getValue(81)).maxRound)
        assertEquals(null, DvtWeeklyDungeonFormation.objective(dungeons.getValue(18)).maxRound)
        assertEquals(null, DvtWeeklyDungeonFormation.objective(dungeons.getValue(18)).minRound)
    }

    @Test
    fun soulHasNoAllPassRewardWhileBachChienAnchorDoes() {
        val bach = dungeons.getValue(18)
        assertEquals(34366, bach.allPassPrize)
        assertEquals(12762, bach.costPoint)

        for (id in 63..82) {
            assertEquals(0, dungeons.getValue(id).allPassPrize, "dungeon=$id")
            assertEquals(0, dungeons.getValue(id).costPoint, "dungeon=$id")
        }
    }

    @Test
    fun roomCompletionReleasesAllMembersAfterWeeklyBattle() {
        val registry = DungeonRoomRegistry()
        val owner = session(1001, "Owner")
        val member = session(1002, "Member")
        val room = registry.create(owner, 18, 5, "")
        assertNotNull(registry.join(room.id, member))
        assertNotNull(registry.roomOf(owner.playerId))
        assertNotNull(registry.roomOf(member.playerId))

        val completed = registry.completeForPlayer(owner.playerId)
        assertNotNull(completed)
        assertNull(registry.roomOf(owner.playerId))
        assertNull(registry.roomOf(member.playerId))
        assertTrue(registry.snapshotRooms().isEmpty())
    }

    private fun session(id: Long, name: String): GameSession {
        val s = mockk<GameSession>(relaxed = true)
        every { s.playerId } returns id
        every { s.characterName } returns name
        return s
    }
}

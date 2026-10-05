package tw.tsm.tests

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.LeaderboardDatLoader
import tw.tsm.domain.constants.LeaderboardConstants
import tw.tsm.domain.data.LeaderboardDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.LeaderboardEntryDto
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.LeaderboardCharRecord
import tw.tsm.gamelogic.systems.AchievementSystem
import tw.tsm.gamelogic.systems.LeaderboardSystem
import tw.tsm.app.response.LeaderboardResponseSender
import java.nio.file.Files
import java.nio.file.Path

class DvtLeaderboardRegressionTest {
    private fun realDat(name: String): ByteArray {
        val direct = Path.of("File.dat", "Data-ios", name)
        val path = if (Files.exists(direct)) direct else Path.of("..", "File.dat", "Data-ios", name)
        return Files.readAllBytes(path)
    }

    private fun def(id: Int) = LeaderboardDef(
        id = id,
        mainTagText = 0,
        subTagText = 0,
        scoreText = 0,
        nameText = 0,
        awardId = 0,
        scoreFormat = "%s",
        stateEnable = false,
        rankAwardFlagIds = listOf(0, 0, 0),
        bitFunction = 0,
    )

    private fun char(
        id: Long,
        level: Int = id.toInt(),
        element: Int = 1,
        turn: Int = 0,
        career: Int = 0,
        gold: Int = 0,
        petNpcId: Int = 0,
        petLevel: Int = 0,
        petTurn: Int = 0,
        petExp: Long = 0,
    ) = LeaderboardCharRecord(
        id = id,
        name = "P$id",
        level = level,
        exp = 0,
        element = element,
        turn = turn,
        career = career,
        sex = 1,
        hair = 0,
        color1 = 0,
        color2 = 0,
        gold = gold,
        petNpcId = petNpcId,
        petLevel = petLevel,
        petTurn = petTurn,
        petExp = petExp,
    )

    @Test
    fun realDvtLeaderboardDatHasExactCurrentIds() {
        val defs = LeaderboardDatLoader.load(realDat("IOS_LeaderbordInfo_C.dat"))

        assertEquals(19, defs.size)
        assertEquals(LeaderboardConstants.DVT_IDS, defs.keys.toSet())

        assertEquals(20557L, defs.getValue(1).subTagText) // Tất cả
        assertEquals(20558L, defs.getValue(2).subTagText) // Địa
        assertEquals(20559L, defs.getValue(3).subTagText) // Thủy
        assertEquals(20560L, defs.getValue(4).subTagText) // Hỏa
        assertEquals(20570L, defs.getValue(5).subTagText) // Phong
        assertEquals(10403L, defs.getValue(6).subTagText) // Võ tướng
        assertEquals(20572L, defs.getValue(7).mainTagText) // Bảng vàng
        assertEquals(22451L, defs.getValue(20).mainTagText) // Bảng thành tựu
    }

    @Test
    fun pageAndCumulativeLevelFollowLuaDvt() {
        assertEquals(500, LeaderboardSystem.cumulativeLevel(level = 100, turn = 2))
        assertEquals(Triple(1, 3, 0), LeaderboardSystem.pageBounds(11, 1))
        assertEquals(Triple(2, 3, 5), LeaderboardSystem.pageBounds(11, 2))
        assertEquals(Triple(1, 3, 0), LeaderboardSystem.pageBounds(11, 0))
        assertEquals(Triple(3, 3, 10), LeaderboardSystem.pageBounds(11, 99))
        assertEquals(Triple(1, 1, 0), LeaderboardSystem.pageBounds(0, 1))
    }

    @Test
    fun s88001WireMatchesLuaContract() {
        var sent = byteArrayOf()
        val session = object : SessionSender {
            override fun send(data: ByteArray) {
                sent = data
            }
            override fun close() = Unit
        }

        LeaderboardResponseSender().sendLeaderboardData(
            session = session,
            leaderboardId = 6,
            page = 1,
            maxPage = 2,
            myRank = 4,
            myScoreParams = listOf(4, 1, 10),
            entries = listOf(
                LeaderboardEntryDto(
                    rank = 2,
                    state = 1,
                    roleId = 0x0102030405060708L,
                    name = "Bob",
                    level = 10,
                    element = 4,
                    turn3Element = 0,
                    turn = 1,
                    career = 3,
                    scoreParams = listOf(4, 1, 10),
                )
            ),
        )

        val expected = intArrayOf(
            88, 1, 0,
            6, 1, 2, 4,
            3,
            4, 0, 0, 0,
            1, 0, 0, 0,
            10, 0, 0, 0,
            1,
            2, 1,
            8, 7, 6, 5, 4, 3, 2, 1,
            3, 66, 111, 98,
            10, 4, 0, 1, 3,
            3,
            4, 0, 0, 0,
            1, 0, 0, 0,
            10, 0, 0, 0,
        ).map { it.toByte() }.toByteArray()

        assertArrayEquals(expected, sent)
    }

    @Test
    fun runtimeQueryUsesFiveRowsOneBasedAndCorrectWarriorScore() = runBlocking {
        val repo = mockk<CharacterRepository>()
        val response = mockk<ResponseSender>(relaxed = true)
        val scope = mockk<CoroutineScope>(relaxed = true)
        val achievement = mockk<AchievementSystem>()

        val chars = (1L..11L).map { id ->
            char(
                id = id,
                level = id.toInt(),
                element = if (id % 2L == 0L) 1 else 2,
                career = if (id == 4L) 3 else id.toInt() % 4,
                gold = id.toInt() * 100,
                petNpcId = when (id) {
                    3L -> 9001
                    4L -> 9002
                    else -> 0
                },
                petLevel = when (id) {
                    3L -> 200
                    4L -> 10
                    else -> 0
                },
                petTurn = if (id == 4L) 1 else 0,
                petExp = id * 10,
            )
        }

        coEvery { repo.findAllForLeaderboard() } returns chars
        coEvery { achievement.calculateTotalScore(any()) } answers {
            firstArg<Long>().toInt() * 10
        }

        val npcEarth = mockk<NpcDef>()
        val npcWind = mockk<NpcDef>()
        every { npcEarth.element } returns 1
        every { npcWind.element } returns 4

        val defs = listOf(1, 2, 6, 7, 20).associateWith(::def)
        val system = LeaderboardSystem(
            characterRepository = repo,
            leaderboardDefs = defs,
            responseSender = response,
            scope = scope,
            npcDefs = mapOf(9001 to npcEarth, 9002 to npcWind),
            achievementSystem = achievement,
        )
        system.refresh()

        val session = mockk<GameSession>(relaxed = true)
        every { session.roleId } returns 6

        val pageEntries = slot<List<LeaderboardEntryDto>>()
        system.query(session, 1, 2)
        verify {
            response.sendLeaderboardData(
                session,
                1,
                2,
                3,
                6,
                listOf(6),
                capture(pageEntries),
            )
        }
        assertEquals(listOf(6, 7, 8, 9, 10), pageEntries.captured.map { it.rank })
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), pageEntries.captured.map { it.roleId })
        assertEquals(5, pageEntries.captured.size)
        assertEquals(1, pageEntries.captured.first { it.roleId == 5L }.career)

        clearMocks(response, answers = false, recordedCalls = true)

        val earthEntries = slot<List<LeaderboardEntryDto>>()
        system.query(session, 2, 1)
        verify {
            response.sendLeaderboardData(
                session,
                2,
                1,
                1,
                3,
                listOf(6),
                capture(earthEntries),
            )
        }
        assertTrue(earthEntries.captured.all { it.element == 1 })

        clearMocks(response, answers = false, recordedCalls = true)

        val warriorEntries = slot<List<LeaderboardEntryDto>>()
        every { session.roleId } returns 4
        system.query(session, 6, 1)
        verify {
            response.sendLeaderboardData(
                session,
                6,
                1,
                1,
                1,
                listOf(4, 1, 10),
                capture(warriorEntries),
            )
        }
        assertEquals(4L, warriorEntries.captured.first().roleId)
        assertEquals(3, warriorEntries.captured.first().career)
        assertEquals(listOf(4, 1, 10), warriorEntries.captured.first().scoreParams)
    }
}

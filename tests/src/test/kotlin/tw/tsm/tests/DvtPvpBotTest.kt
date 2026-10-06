package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.SkillDatLoader
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleLifecyclePolicy
import tw.tsm.gamelogic.battle.BattleSkillResolver
import tw.tsm.gamelogic.battle.PvpBotAiPolicy
import tw.tsm.gamelogic.systems.PvpBotMode
import tw.tsm.gamelogic.systems.PvpLobbyPolicy
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.TeamDungeonBotPolicy
import java.nio.file.Files
import java.nio.file.Path

class DvtPvpBotTest {
    private fun fixture(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        val path = if (Files.exists(direct)) direct
        else Path.of("..", parts.first(), *parts.drop(1).toTypedArray())
        return Files.readAllBytes(path)
    }

    private fun fighter(
        id: Long,
        kind: Int,
        col: Int,
        row: Int,
        hp: Long = 100,
        maxHp: Long = 100,
        sp: Long = 1000,
        skills: IntArray = intArrayOf(),
    ) = BattleFighter(
        roleId = id,
        kind = kind,
        name = "f$id",
        col = col,
        row = row,
        maxHp = maxHp,
        maxSp = sp,
        hp = hp,
        sp = sp,
        level = 100,
        element = 2,
        atk = 300,
        def = 200,
        agi = 100,
        intelligence = 300,
        skills = skills,
        skillLevels = skills.associateWith { 10 },
    )

    @Test
    fun `AutomanualPlayer is player-shaped but not client-controlled`() {
        val bot = fighter(-1, EHuman.AUTOMANUAL_PLAYER, 3, 0)
        val botPet = fighter(-2, EHuman.AUTOMANUAL_NPC, 2, 0)

        assertTrue(bot.isHumanPlayer)
        assertFalse(bot.isPlayerControlled)
        assertTrue(bot.isAutomanual)
        assertFalse(botPet.isPlayerControlled)
        assertTrue(botPet.isAutomanual)
    }

    @Test
    fun `PvP bot heal selection uses real Skill DAT classifier`() {
        val defs = SkillDatLoader.load(fixture("File.dat", "Data-ios", "IOS_Skill_C.dat"))
        val healEntry = defs.entries.first { (_, def) ->
            PvpBotAiPolicy.isPvpHpHeal(def)
        }

        val bot = fighter(-10, EHuman.AUTOMANUAL_PLAYER, 3, 0, skills = intArrayOf(healEntry.key))
        val wounded = fighter(2, EHuman.PLAYERS, 2, 1, hp = 20, maxHp = 100)
        val enemy = fighter(3, EHuman.PLAYER, 0, 0)

        val grid = BattleGrid()
        grid.place(bot)
        grid.place(wounded)
        grid.place(enemy)

        val actions = PvpBotAiPolicy.candidates(bot, grid, BattleSkillResolver(defs))
        assertTrue(actions.isNotEmpty())
        assertEquals(healEntry.key, actions.first().id)
        assertEquals(wounded.col, actions.first().targetCol)
        assertEquals(wounded.row, actions.first().targetRow)
    }

    @Test
    fun `five-slot lobby shows bot placeholders and maps real players to battle sides`() {
        assertEquals(1, PvpLobbyPolicy.visibleBotCount(PvpBotMode.VS, realCount = 1, roomMaxPlayers = 5))
        assertEquals(4, PvpLobbyPolicy.visibleBotCount(PvpBotMode.MIX, realCount = 1, roomMaxPlayers = 5))
        assertEquals(4, PvpLobbyPolicy.visibleBotCount(PvpBotMode.CHAOS, realCount = 1, roomMaxPlayers = 5))

        val vs = PvpLobbyPolicy.battlePlan(PvpBotMode.VS, 1)!!
        assertEquals(listOf(0), vs.rightIndices)
        assertEquals(emptyList<Int>(), vs.leftIndices)
        assertEquals(1, vs.leftBots)

        val mix = PvpLobbyPolicy.battlePlan(PvpBotMode.MIX, 2)!!
        assertEquals(listOf(0, 1), mix.rightIndices)
        assertEquals(3, mix.rightBots)
        assertEquals(5, mix.leftBots)

        val chaos = PvpLobbyPolicy.battlePlan(PvpBotMode.CHAOS, 3)!!
        assertEquals(listOf(0, 2), chaos.rightIndices)
        assertEquals(listOf(1), chaos.leftIndices)
        assertEquals(3, chaos.rightBots)
        assertEquals(4, chaos.leftBots)
    }

    @Test
    fun `normal team dungeon bots fill only a solo room and leave space for humans`() {
        assertEquals(
            4,
            TeamDungeonBotPolicy.visibleBotCount(
                DungeonSystem.KIND_NORMAL_MULTIPLAYER,
                realCount = 1,
                roomMaxPlayers = 5,
            )
        )
        assertEquals(
            0,
            TeamDungeonBotPolicy.visibleBotCount(
                DungeonSystem.KIND_NORMAL_MULTIPLAYER,
                realCount = 2,
                roomMaxPlayers = 5,
            )
        )
        assertEquals(
            0,
            TeamDungeonBotPolicy.visibleBotCount(
                DungeonSystem.KIND_SINGLE,
                realCount = 1,
                roomMaxPlayers = 5,
            )
        )
        assertEquals(
            0,
            TeamDungeonBotPolicy.visibleBotCount(
                DungeonSystem.KIND_WEEKLY,
                realCount = 1,
                roomMaxPlayers = 5,
            )
        )
    }

    @Test
    fun `arena modes map registration markers and never resume Eve after close`() {
        assertEquals(PvpBotMode.MIX, PvpBotMode.fromRegistrationMission(10007))
        assertEquals(PvpBotMode.CHAOS, PvpBotMode.fromRegistrationMission(10578))
        assertEquals(1, PvpBotMode.VS.sideSize)
        assertEquals(5, PvpBotMode.MIX.sideSize)
        assertEquals(5, PvpBotMode.CHAOS.sideSize)
        assertFalse(BattleLifecyclePolicy.shouldResumeEve(tw.tsm.domain.constants.EWar.VS))
        assertTrue(BattleLifecyclePolicy.shouldResumeEve(tw.tsm.domain.constants.EWar.EVENT))
    }

    @Test
    fun `team dungeon bot profiles use distinct Three Kingdoms identities and companions`() {
        val profiles = (0 until 4).map(TeamDungeonBotPolicy::profile)
        assertEquals(4, profiles.map { it.name }.toSet().size)
        assertEquals(setOf("Huyết Kiếm", "Phong Vân", "Băng Tâm", "Thiên Long"), profiles.map { it.name }.toSet())
        assertEquals(4, profiles.map { it.petNpcId }.toSet().size)
        assertEquals(4, profiles.map { it.hairOffset }.toSet().size)
    }


    @Test
    fun `team dungeon bot fallback waits five seconds`() {
        assertEquals(5_000L, TeamDungeonBotPolicy.BOT_FILL_DELAY_MS)
    }

}

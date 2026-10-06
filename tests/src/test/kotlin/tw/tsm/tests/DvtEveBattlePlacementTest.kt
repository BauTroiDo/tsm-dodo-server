package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.data.EveFightEnemy
import tw.tsm.gamelogic.systems.DvtEveBattlePlacement
import java.nio.file.Files
import java.nio.file.Path

class DvtEveBattlePlacementTest {
    private fun file(vararg parts: String): ByteArray {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        val path = if (Files.exists(direct)) direct else Path.of("..", *parts)
        return Files.readAllBytes(path)
    }

    @Test
    fun `Bo Lo FightData20 uses ten initial slots and three reinforcements`() {
        val eve = EveDataLoader.load(file("gamedata", "Eve.emg"))
        val fight = eve.getValue(12820).fightDatas.getValue(20)

        val plan = requireNotNull(
            DvtEveBattlePlacement.plan(
                fight.leftEnemies,
                BattleConstants.ENEMY_FRONT_COL,
                BattleConstants.ENEMY_BACK_COL,
            )
        )

        assertEquals(10, plan.initial.size)
        assertEquals(3, plan.pendingCount)

        val slots = plan.initial.map { it.slot }.toSet()
        assertEquals(10, slots.size)
        assertEquals(
            (0 until BattleConstants.MAX_ROW).flatMap { row ->
                listOf(
                    DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_FRONT_COL, row),
                    DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_BACK_COL, row),
                )
            }.toSet(),
            slots,
        )

        val boLo = plan.initial.single { it.enemy.npcId == 17133 }
        assertEquals(
            DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_FRONT_COL, 2),
            boLo.slot,
        )

        val expectedPending = mapOf(
            DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_BACK_COL, 1) to listOf(17071),
            DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_BACK_COL, 2) to listOf(17071),
            DvtEveBattlePlacement.Slot(BattleConstants.ENEMY_BACK_COL, 3) to listOf(17071),
        )
        assertEquals(
            expectedPending,
            plan.pending.mapValues { (_, queue) -> queue.map { it.npcId } },
        )
    }

    @Test
    fun `invalid zero location remains evidence blocked for legacy fallback`() {
        val enemies = listOf(
            EveFightEnemy(no = 1, npcId = 17070, locationPos = 0, ai = 1),
        )
        assertNull(
            DvtEveBattlePlacement.plan(
                enemies,
                BattleConstants.ENEMY_FRONT_COL,
                BattleConstants.ENEMY_BACK_COL,
            )
        )
    }
}

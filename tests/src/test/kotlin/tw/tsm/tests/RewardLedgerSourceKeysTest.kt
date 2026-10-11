package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.gamelogic.systems.ActiveDungeonRun
import tw.tsm.gamelogic.systems.RewardLedgerSourceKeys
import java.util.UUID

class RewardLedgerSourceKeysTest {
    @Test fun reconnectReusesSameDungeonPayoutKey() {
        val before = ActiveDungeonRun(
            dungeonId = 5001, startTime = 46280.3, instanceId = 7,
            startedAtMillis = 1_760_000_000_000L
        )
        val afterRestore = before.copy()
        assertEquals(
            RewardLedgerSourceKeys.dungeon(before, "TERMINAL"),
            RewardLedgerSourceKeys.dungeon(afterRestore, "TERMINAL")
        )
        // A copy/relogin may re-read the clock but must keep the same UUID.
        assertEquals(
            RewardLedgerSourceKeys.dungeon(before, "TERMINAL"),
            RewardLedgerSourceKeys.dungeon(before.copy(startedAtMillis = 1_760_000_000_100L), "TERMINAL")
        )
        val newRun = ActiveDungeonRun(
            dungeonId = 5001, startTime = 46280.3, instanceId = 7,
            startedAtMillis = 1_760_000_000_000L,
        )
        assertNotEquals(
            RewardLedgerSourceKeys.dungeon(before, "TERMINAL"),
            RewardLedgerSourceKeys.dungeon(newRun, "TERMINAL")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RewardLedgerSourceKeys.dungeon(before.copy(runToken = ""), "TERMINAL")
        }
        assertNull(RewardLedgerSourceKeys.verifiedDungeonOrNull(
            before.copy(runToken = "legacy-unknown"), "TERMINAL"
        ))
        assertNotEquals(
            RewardLedgerSourceKeys.dungeon(before, "TERMINAL"),
            RewardLedgerSourceKeys.dungeon(before, "WEEKLY_TERMINAL")
        )
    }

    @Test fun numericBattleIdIsNotAcceptedAsDurableIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            RewardLedgerSourceKeys.battle("42")
        }
        val durableId = UUID.randomUUID().toString()
        assertEquals(
            RewardLedgerSourceKeys.battle(durableId),
            RewardLedgerSourceKeys.battle(durableId)
        )
        assertNotEquals(
            RewardLedgerSourceKeys.battle(durableId, "GENERIC"),
            RewardLedgerSourceKeys.battle(durableId, "CAPTURE")
        )
    }

    @Test fun allPassRewardsDistinguishPeriodAndPage() {
        assertNotEquals(
            RewardLedgerSourceKeys.weeklyAllPass("2026-W40", 1),
            RewardLedgerSourceKeys.weeklyAllPass("2026-W41", 1)
        )
        assertNotEquals(
            RewardLedgerSourceKeys.weeklyAllPass("2026-W41", 1),
            RewardLedgerSourceKeys.weeklyAllPass("2026-W41", 2)
        )
    }
}

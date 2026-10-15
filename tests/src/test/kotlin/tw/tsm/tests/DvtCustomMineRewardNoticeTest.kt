package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.domain.interfaces.repositories.MiningRecord
import tw.tsm.gamelogic.systems.MiningView

/** Original DVT S035-050 updates TS silently; chat notice is custom UX. */
class DvtCustomMineRewardNoticeTest {
    private val record = MiningRecord(
        mineType = 1, machineFull = listOf(10,70,30,160),
        updatedAtMillis = 1_000_000L, dayLimit = false
    )

    @Test fun successfulNewAwardProducesAccurateVisibleNotice() {
        val view = MiningView(record, 599_999_980, tsBalanceUpdate = 20, tsRewardAmount = 4)
        assertEquals("Mỏ Thôn Thực: +4 Tiền Thôn Thực. Số dư: 20.", view.rewardNotice)
        assertEquals(20, view.tsBalanceUpdate)
    }

    @Test fun replayOrUnchangedWalletMustNeverShowNewRewardNotice() {
        assertNull(MiningView(record, 599_999_980).rewardNotice)
        assertNull(MiningView(record, 599_999_980, tsBalanceUpdate = 20).rewardNotice)
        assertNull(MiningView(record, 599_999_980, tsRewardAmount = 4).rewardNotice)
        assertNull(MiningView(record, 599_999_980, tsBalanceUpdate = 20, tsRewardAmount = 0).rewardNotice)
    }
}

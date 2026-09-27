package tw.tsm.tests

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tw.tsm.app.handlers.BankHandler
import tw.tsm.app.handlers.StorageHandler
import tw.tsm.domain.constants.BankResult
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.RoleCountSystem

class DvtBankWireTest {
    private val inventory = mockk<InventorySystem>()
    private val response = mockk<ResponseSender>(relaxed = true)
    private val counts = mockk<RoleCountSystem>()
    private val session = mockk<GameSession>(relaxed = true) {
        every { state } returns SessionState.IN_GAME
        every { roleId } returns 42
    }
    private val item = ThingData(itemId = 100, quantity = 10000)
    private val bank = BankHandler(inventory, response, emptyMap(), counts)
    private val storage = StorageHandler(inventory, response, emptyMap(), counts)
    // UIBank.lua: UInt16 index 0x1234, Int32 quantity 0x0102.
    private val packet = byteArrayOf(0x34, 0x12, 0x02, 0x01, 0, 0)

    @Test fun `bank withdrawal reads word index then quantity`() = runTest {
        every { inventory.getBankItem(42, 0x1234) } returns item
        coEvery { inventory.withdrawFromBank(42, 0x1234, 258) } returns Triple(BankResult.SUCCESS, 8, item)
        bank.handle(session, 1, packet)
        coVerify(exactly = 1) { inventory.withdrawFromBank(42, 0x1234, 258) }
        verify { response.sendBankReduceItem(session, 0x1234, 258) }
    }

    @Test fun `bank deposit reads word index then quantity`() = runTest {
        every { inventory.getBagItem(42, 0x1234) } returns item
        every { inventory.canDepositToBank(100) } returns true
        every { counts.getBankCapacity(42) } returns 5000
        coEvery { inventory.depositToBank(42, 0x1234, 258, 5000) } returns Triple(BankResult.SUCCESS, 8, item)
        bank.handle(session, 2, packet)
        coVerify(exactly = 1) { inventory.depositToBank(42, 0x1234, 258, 5000) }
        verify { response.sendReduceBagItem(session, 0x1234, 258) }
    }

    @Test fun `storage deposit reads word index then quantity`() = runTest {
        every { inventory.getBagItem(42, 0x1234) } returns item
        every { inventory.canDepositToStorage(item) } returns BankResult.SUCCESS
        every { counts.getStorageCapacity(42) } returns 5000
        coEvery { inventory.depositToStorage(42, 0x1234, 258, 5000) } returns Triple(BankResult.SUCCESS, 8, item)
        storage.handle(session, 2, packet)
        coVerify(exactly = 1) { inventory.depositToStorage(42, 0x1234, 258, 5000) }
    }

    @Test fun `storage withdrawal retains byte index`() = runTest {
        every { inventory.getStorageItem(42, 200) } returns item
        coEvery { inventory.withdrawFromStorage(42, 200, 258) } returns Triple(BankResult.SUCCESS, 8, item)
        storage.handle(session, 1, byteArrayOf(200.toByte(), 2, 1, 0, 0))
        coVerify(exactly = 1) { inventory.withdrawFromStorage(42, 200, 258) }
    }

    @Test fun `truncated or trailing partial records cause no inventory mutation`() = runTest {
        for (invalid in listOf(packet.copyOf(5), packet + 1.toByte())) {
            bank.handle(session, 1, invalid)
            bank.handle(session, 2, invalid)
            storage.handle(session, 2, invalid)
        }
        verify { inventory wasNot Called }
    }

    @Test fun `bank batch keeps word sized record boundaries`() = runTest {
        every { inventory.getBankItem(42, 0x1234) } returns item
        every { inventory.getBankItem(42, 0x5678) } returns item
        coEvery { inventory.withdrawFromBank(42, any(), any()) } returns Triple(BankResult.SUCCESS, 8, item)
        bank.handle(session, 1, packet + byteArrayOf(0x78, 0x56, 3, 0, 0, 0))
        coVerifyOrder {
            inventory.withdrawFromBank(42, 0x1234, 258)
            inventory.withdrawFromBank(42, 0x5678, 3)
        }
    }
}

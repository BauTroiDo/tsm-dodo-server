package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.app.response.NpcEventResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.gamelogic.systems.EvePlayerResultKind
import tw.tsm.gamelogic.systems.EveResultClassPolicy

class MessageBoardD9C1RegressionTest : DescribeSpec({
    describe("D9-C1 DVT message-board open bridge") {
        it("maps Eve pStyle 7 and 8 to the message board") {
            EveResultClassPolicy.playerOperation(7, 7)?.kind shouldBe EvePlayerResultKind.OPEN_MESSAGE_BOARD
            EveResultClassPolicy.playerOperation(7, 8)?.kind shouldBe EvePlayerResultKind.OPEN_MESSAGE_BOARD
            EveResultClassPolicy.playerOperation(0, 7) shouldBe null
            EveResultClassPolicy.playerOperation(0, 8) shouldBe null
        }

        it("serializes S 036-003 with no payload") {
            val packets = mutableListOf<ByteArray>()
            val session = object : SessionSender {
                override fun send(data: ByteArray) { packets += data }
                override fun close() = Unit
            }

            NpcEventResponseSender().sendOpenMessageBoard(session)

            packets.size shouldBe 1
            packets.single().toList() shouldBe listOf(36.toByte(), 3.toByte(), 0.toByte())
        }
    }
})

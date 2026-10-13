package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val treasureLogger = KotlinLogging.logger {}

/**
 * DVT Treasure protocolTable[123] -- read-only UI handshake.
 *
 * Original Function.Use(EFunction.Treasure) opens UITreasure and sends C123-001
 * without payload; S123-001 with count=0 initializes the 100% original client
 * treasure catalog and lets its pending sendOpenRequest finish the UI open.
 *
 * Treasure strength/star progression is NOT yet persisted/implemented. Do not
 * report fake success or consume inventory/currency. Explicitly reject valid
 * C123-002/003 requests with original protocol error codes, instead of
 * silently swallowing them. The actual UI unlock requires original Mark11804
 * bit936, sourced from IOS_Mark_C.dat.
 */
class TreasureHandler(private val quests: QuestSystem) : PacketHandler {
    override val mainKind: Int = TREASURE_OPCODE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || session.roleId <= 0) return
        if (quests.getFlags(session.roleId.toLong())
                .none { it.bitIndex == TREASURE_UNLOCK_BIT && it.bitValue != 0 }
        ) return

        when (subKind) {
            1 -> {
                if (data.isNotEmpty()) return
                session.send(
                    PacketWriter(7)
                        .writeByte(TREASURE_OPCODE).writeByte(1).writeByte(0)
                        .writeIntLE(0) // No persistent Treasure holdings exist yet
                        .toByteArray()
                )
            }
            2 -> {
                // C123-002: id(U16) + count(Int32) + materialBagIndex(U16).
                if (data.size != 8) return
                session.send(
                    PacketWriter(4)
                        .writeByte(TREASURE_OPCODE).writeByte(2).writeByte(0)
                        .writeByte(255) // Unsupported: no currency/item mutation
                        .toByteArray()
                )
            }
            3 -> {
                // C123-003: id(U16) + count(Int32) + materialBagIndex(U16).
                if (data.size != 8) return
                val id = PacketReader(data).readUShortLE()
                session.send(
                    PacketWriter(8)
                        .writeByte(TREASURE_OPCODE).writeByte(3).writeByte(0)
                        .writeByte(255) // No star advancement or resource charge
                        .writeShortLE(id)
                        .writeByte(0).writeByte(0) // Original client always reads these if ID exists
                        .toByteArray()
                )
            }
            else -> treasureLogger.warn { "Treasure subKind=$subKind unsupported" }
        }
    }

    companion object {
        // Local to this handler so deployment never overwrites an unrelated
        // DEV Opcodes.kt; the DVT client protocol header itself is 123.
        private const val TREASURE_OPCODE = 123
        // Original Mark_C mission11804 -> bit936; no early unlock on login.
        private const val TREASURE_UNLOCK_BIT = 936
    }
}

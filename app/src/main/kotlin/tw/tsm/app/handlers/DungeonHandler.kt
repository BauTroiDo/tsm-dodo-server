package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val dungeonHandlerLogger = KotlinLogging.logger {}

/** DVT protocolTable[47] — Phó bản. F1: room list + tạo/vào phó bản đơn. */
class DungeonHandler(
    private val system: DungeonSystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.DUNGEON

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> sendRoomListEmpty(session)
            2 -> createDungeon(session, data)
            else -> dungeonHandlerLogger.debug {
                "Dungeon C:047-${subKind.toString().padStart(3, '0')} chưa thuộc F1; bỏ qua an toàn"
            }
        }
    }

    private fun sendRoomListEmpty(session: GameSession) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.DUNGEON)
                .writeByte(1)
                .writeByte(0)
                .writeShortLE(0)
                .toByteArray()
        )
    }

    private suspend fun createDungeon(session: GameSession, data: ByteArray) {
        if (data.size !in 2..3) {
            dungeonHandlerLogger.warn { "C:047-002 sai độ dài: ${data.size}" }
            sendCreateResult(session, DungeonSystem.RESULT_NO_DUNGEON)
            return
        }

        val reader = PacketReader(data)
        val dungeonId = reader.readUShortLE()
        if (reader.remaining > 0) reader.readUByte()

        val result = system.validateSingleCreate(session, dungeonId)
        sendCreateResult(session, result)
        if (result != DungeonSystem.RESULT_OK) return

        if (!system.enterSingle(session, dungeonId)) {
            dungeonHandlerLogger.error {
                "Không suy được entry point cho dungeon=$dungeonId scene=${system.getDef(dungeonId)?.sceneId}"
            }
        }
    }

    private fun sendCreateResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.DUNGEON)
                .writeByte(2)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }
}

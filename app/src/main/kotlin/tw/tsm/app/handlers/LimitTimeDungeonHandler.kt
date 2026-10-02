package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.EveEventStateMachine
import tw.tsm.gamelogic.systems.LimitTimeDungeonSystem
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val limitDungeonHandlerLogger = KotlinLogging.logger {}

/** DVT protocolTable[97] — khu luyện công giới hạn. */
class LimitTimeDungeonHandler(
    private val system: LimitTimeDungeonSystem,
    private val roleCountSystem: RoleCountSystem,
    private val partySystem: PartySystem,
    private val stateMachine: EveEventStateMachine,
    private val specialTeleport: SpecialSceneTeleportService,
) : PacketHandler {
    override val mainKind: Int = Opcodes.LIMIT_TIME_DUNGEON

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> enter(session, data)
            2 -> limitDungeonHandlerLogger.debug {
                "C:097-002 chưa có sender trong DVT client hiện tại; bỏ qua an toàn"
            }
            else -> limitDungeonHandlerLogger.warn {
                "Sub-protocol 97 chưa xử lý: " + subKind
            }
        }
    }

    private suspend fun enter(session: GameSession, data: ByteArray) {
        if (data.size != 1) return
        val dungeonId = PacketReader(data).readUByte()
        val def = system.getDef(dungeonId)
        if (def == null) {
            sendResult(session, 1)
            return
        }

        val result = validate(session, def.limitIndex, def.limitTimeMinutes, def.limitLevel, def.sceneId)
        if (result != 0) {
            sendResult(session, result)
            return
        }

        // Lua success branch của S:097-001 không làm gì; scene change là packet riêng.
        sendResult(session, 0)
        if (!specialTeleport.enterSpecialScene(session, def.sceneId)) {
            limitDungeonHandlerLogger.error {
                "Không suy được entry point scene giới hạn " + def.sceneId
            }
        }
    }

    private fun validate(
        session: GameSession,
        limitIndex: Int,
        limitMinutes: Int,
        limitLevel: Int,
        sceneId: Int,
    ): Int {
        if (session.level < limitLevel) return 1
        if (roleCountSystem.getValue(session.roleId.toLong(), limitIndex) >= limitMinutes) return 2
        if (session.battleId != 0L) return 3
        if (stateMachine.getSession(session.roleId.toLong()) != null) return 4
        if (partySystem.isInParty(session.playerId)) return 5
        if (session.mapId == sceneId) return 6
        return 0
    }

    private fun sendResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.LIMIT_TIME_DUNGEON)
                .writeByte(1).writeByte(0).writeByte(result)
                .toByteArray()
        )
    }
}

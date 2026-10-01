package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.network.SessionManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/** DVT C:036 Message Board — validation, persistent add and realtime broadcast. */
class MessageBoardHandler(
    private val roleCountSystem: RoleCountSystem,
    private val responseSender: ResponseSender,
    private val repository: MessageBoardRepository,
    private val sessionManager: SessionManager,
) : PacketHandler {
    override val mainKind: Int = Opcodes.CHALLENGE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleAdd(session, data)
            else -> logger.debug { "MessageBoard C36 chưa xử lý subKind=$subKind" }
        }
    }

    private suspend fun handleAdd(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) {
            sendFailure(session)
            return
        }
        val message = try { PacketReader(data).readBig5(data.size) } catch (_: Exception) {
            sendFailure(session)
            return
        }
        val length = message.codePointCount(0, message.length)
        if (length !in 1..MAX_MESSAGE_CHARS) {
            sendFailure(session)
            return
        }

        val charId = session.roleId.toLong()
        if (roleCountSystem.getValue(charId, ROLE_COUNT_MESSAGE_BOARD) >= DAILY_LIMIT) {
            sendFailure(session)
            return
        }

        val record = try {
            repository.create(
                characterId = charId,
                playerId = session.playerId,
                name = session.characterName,
                sex = session.sex,
                headStyle = session.hair,
                color1 = session.color1,
                color2 = session.color2,
                message = message,
            )
        } catch (t: Throwable) {
            logger.error(t) { "MessageBoard persist failed: charId=$charId" }
            sendFailure(session)
            return
        }

        val newCount = roleCountSystem.increment(charId, ROLE_COUNT_MESSAGE_BOARD, 1)
        val max = roleCountSystem.getMaxValue(charId, ROLE_COUNT_MESSAGE_BOARD)
        responseSender.sendRoleCountUpdate(session, mapOf(ROLE_COUNT_MESSAGE_BOARD to (newCount to max)))

        for (target in sessionManager.allSessions()) {
            if (target.state == SessionState.IN_GAME) {
                responseSender.sendMessageBoardAdded(target, record)
            }
        }
        logger.info { "MessageBoard accepted: charId=$charId chars=$length daily=$newCount/$DAILY_LIMIT" }
    }

    private fun sendFailure(session: GameSession) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.CHALLENGE).writeByte(7).writeByte(0)
                .writeByte(1).writeByte(7)
                .toByteArray()
        )
    }

    companion object {
        const val ROLE_COUNT_MESSAGE_BOARD = 13
        const val DAILY_LIMIT = 3
        const val MAX_MESSAGE_CHARS = 60
    }
}

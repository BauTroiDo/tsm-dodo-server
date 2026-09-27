package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 武將出戰/收回處理器（mainKind = 19）
 *
 * 來源：protocal.lua protocolTable[19]
 * C:019-001 出戰、C:019-002 收回
 */
class FollowNpcDeployHandler(
    private val petSystem: PetSystem,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.FOLLOW_NPC_DEPLOY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        when (subKind) {
            1 -> handleDeploy(session, characterId, data)
            2 -> handleRecall(session, characterId)
            else -> logger.trace { "未處理的出戰 subKind=$subKind" }
        }
    }

    /**
     * C:019-001 武將出戰
     *
     * 格式：+npcId(2LE)
     */
    private fun handleDeploy(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val npcId = reader.readUShortLE()

        val result = petSystem.deployFollowNpc(characterId, npcId)
        if (result == 0) {
            logger.debug { "出戰失敗：charId=$characterId npc=$npcId" }
            return
        }

        // S:019-004 通知出戰（使用 UInt16 版本）
        responseSender.sendFollowNpcDeploy(session, npcId)
        logger.info { "${session.characterName} 武將出戰 npc=$npcId" }
    }

    /**
     * C:019-002 收回出戰武將（空包）
     */
    private fun handleRecall(session: GameSession, characterId: Long) {
        if (!petSystem.recallFightNpc(characterId)) {
            logger.debug { "收回失敗：charId=$characterId" }
            return
        }

        // S:019-002 通知收回（空包）
        responseSender.sendFollowNpcRecall(session)
        logger.info { "${session.characterName} 收回武將" }
    }
}

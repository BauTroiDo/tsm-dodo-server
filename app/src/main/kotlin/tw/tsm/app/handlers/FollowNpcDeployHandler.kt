package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.DailyProgressSystem
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
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository? = null,
    private val dailyProgressSystem: DailyProgressSystem? = null,
) : PacketHandler {

    override val mainKind: Int = Opcodes.FOLLOW_NPC_DEPLOY

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        val expected = when (subKind) { 1 -> 2; 2 -> 0; else -> return }
        if (data.size != expected) return
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
    private suspend fun handleDeploy(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val npcId = reader.readUShortLE()

        val result = petSystem.deployFollowNpc(characterId, npcId)
        if (result == 0) {
            logger.debug { "出戰失敗：charId=$characterId npc=$npcId" }
            return
        }

        // Trạng thái xuất chiến là dữ liệu nhân vật, phải ghi ngay xuống DB.
        // Không chờ C:000 disconnect vì đóng app/mất kết nối có thể không gửi packet đó.
        try {
            characterRepository?.updateFightNpcId(characterId, npcId)
        } catch (e: Exception) {
            logger.error(e) { "Lưu võ tướng xuất chiến thất bại: charId=$characterId npc=$npcId" }
        }

        // S:019-004 thông báo xuất chiến (UInt16)
        responseSender.sendFollowNpcDeploy(session, npcId)
        dailyProgressSystem?.setConditionFlag(
            session,
            DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC,
        )
        logger.info { "${session.characterName} 武將出戰 npc=$npcId" }
    }

    /**
     * C:019-002 收回出戰武將（空包）
     */
    private suspend fun handleRecall(session: GameSession, characterId: Long) {
        if (!petSystem.recallFightNpc(characterId)) {
            logger.debug { "收回失敗：charId=$characterId" }
            return
        }

        // Thu hồi cũng phải ghi ngay, nếu không DB có thể khôi phục nhầm pet cũ ở lần login sau.
        try {
            characterRepository?.updateFightNpcId(characterId, 0)
        } catch (e: Exception) {
            logger.error(e) { "Lưu trạng thái thu hồi võ tướng thất bại: charId=$characterId" }
        }

        // S:019-002 thông báo thu hồi (không payload)
        responseSender.sendFollowNpcRecall(session)
        logger.info { "${session.characterName} 收回武將" }
    }
}

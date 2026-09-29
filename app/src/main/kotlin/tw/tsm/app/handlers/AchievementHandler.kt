package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.AchievementSystem
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler hệ thống thành tựu — mainKind=82
 *
 * Gói tin:
 * - C:082-001 hoàn thành thành tựu (+Count(1) +<<ID thành tựu(2)>>, tối đa 10 bản ghi)
 * - C:082-002 nhận thưởng thành tựu (+ID thành tựu(2))
 */
class AchievementHandler(
    private val achievementSystem: AchievementSystem,
    private val responseSender: ResponseSender,
    private val bitFlagSystem: BitFlagSystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.ACHIEVEMENT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleComplete(session, data)
            2 -> handleClaimReward(session, data)
            else -> logger.warn { "Sub-protocol thành tựu chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:082-001 hoàn thành thành tựu
     *
     * Định dạng: +Count(1) +<<ID thành tựu(2 LE)>> (tối đa 10 bản ghi)
     *
     * Thứ tự gói tin: cập nhật BitFlag trước → phản hồi thành tựu sau (client nhận S:082-001 là RefreshPage đọc BitFlag ngay)
     */
    private suspend fun handleComplete(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val count = reader.readUByte().coerceAtMost(10)
        val charId = session.roleId.toLong()

        for (i in 0 until count) {
            if (reader.remaining < 2) break
            val achievementId = reader.readUShortLE()
            val result = achievementSystem.completeAchievement(charId, achievementId)

            if (result == AchievementSystem.RESULT_SUCCESS) {
                // Gửi cập nhật BitFlag trước để client RefreshPage thấy được trạng thái mới
                sendBitFlagUpdate(session, charId)
            }
            responseSender.sendAchievementComplete(session, result, achievementId)

            if (result == AchievementSystem.RESULT_SUCCESS) {
                broadcastAchievement(session, achievementId)
            }
        }
    }

    /**
     * C:082-002 nhận thưởng
     *
     * Định dạng: +ID thành tựu(2 LE)
     *
     * Thứ tự gói tin: cập nhật BitFlag → vật phẩm túi đồ → phản hồi nhận thưởng thành tựu
     */
    private suspend fun handleClaimReward(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val achievementId = reader.readUShortLE()
        val charId = session.roleId.toLong()
        val (result, addedItems) = achievementSystem.claimReward(charId, achievementId)

        if (result == AchievementSystem.REWARD_SUCCESS) {
            // 1. Gửi cập nhật BitFlag trước (client RefreshPage cần)
            sendBitFlagUpdate(session, charId)
            // 2. Gửi vật phẩm vào túi đồ client (S:023-008)
            for ((slot, thing) in addedItems) {
                responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
            }
        }
        // 3. Cuối cùng gửi phản hồi nhận thưởng thành tựu
        responseSender.sendAchievementReward(session, result, achievementId)
    }

    /** Gửi cập nhật BitFlag đầy đủ cho client*/
    private suspend fun sendBitFlagUpdate(session: GameSession, charId: Long) {
        val flags = bitFlagSystem.getSetFlagIds(charId)
        val packedFlags = bitFlagSystem.packFlags(flags)
        responseSender.sendInitBitFlags(session, packedFlags)
    }

    /**
     * Phát rộng hoàn thành thành tựu
     *
     * Quyết định kênh phát rộng theo trường channel trong định nghĩa thành tựu.
     * channel=0 không phát rộng; channel=1 (toàn kênh) phát rộng đến kênh thế giới.
     */
    private fun broadcastAchievement(session: GameSession, achievementId: Int) {
        val def = achievementSystem.getDef(achievementId) ?: return
        if (def.channel <= 0) return

        val channel = ChatChannel.fromCode(def.channel) ?: ChatChannel.WORLD
        responseSender.sendChatMessage(
            session, channel,
            session.playerId, session.characterName,
            "Bạn đã hoàn thành một thành tựu!"
        )
        logger.info { "Thông báo thành tựu: ${session.characterName} hoàn thành thành tựu $achievementId channel=${def.channel}" }
    }
}

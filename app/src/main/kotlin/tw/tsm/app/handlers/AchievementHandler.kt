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
 * 成就系統 Handler — mainKind=82
 *
 * 封包：
 * - C:082-001 完成成就（+Count(1) +<<成就ID(2)>>，最多 10 筆）
 * - C:082-002 成就領獎（+成就ID(2)）
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
            else -> logger.warn { "未處理的成就子協定：subKind=$subKind" }
        }
    }

    /**
     * C:082-001 完成成就
     *
     * 格式：+Count(1) +<<成就ID(2 LE)>>（最多 10 筆）
     *
     * 封包順序：先 BitFlag 更新 → 再成就回應（客戶端收到 S:082-001 立刻 RefreshPage 讀 BitFlag）
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
                // 先發 BitFlag 更新，客戶端 RefreshPage 才能看到新狀態
                sendBitFlagUpdate(session, charId)
            }
            responseSender.sendAchievementComplete(session, result, achievementId)

            if (result == AchievementSystem.RESULT_SUCCESS) {
                broadcastAchievement(session, achievementId)
            }
        }
    }

    /**
     * C:082-002 領獎
     *
     * 格式：+成就ID(2 LE)
     *
     * 封包順序：BitFlag 更新 → 背包物品 → 成就領獎回應
     */
    private suspend fun handleClaimReward(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val achievementId = reader.readUShortLE()
        val charId = session.roleId.toLong()
        val (result, addedItems) = achievementSystem.claimReward(charId, achievementId)

        if (result == AchievementSystem.REWARD_SUCCESS) {
            // 1. 先發 BitFlag 更新（客戶端 RefreshPage 需要）
            sendBitFlagUpdate(session, charId)
            // 2. 發送物品到客戶端背包（S:023-008）
            for ((slot, thing) in addedItems) {
                responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
            }
        }
        // 3. 最後發成就領獎回應
        responseSender.sendAchievementReward(session, result, achievementId)
    }

    /** 發送 BitFlag 完整更新給客戶端 */
    private suspend fun sendBitFlagUpdate(session: GameSession, charId: Long) {
        val flags = bitFlagSystem.getSetFlagIds(charId)
        val packedFlags = bitFlagSystem.packFlags(flags)
        responseSender.sendInitBitFlags(session, packedFlags)
    }

    /**
     * 成就完成廣播
     *
     * 根據成就定義的 channel 欄位決定廣播頻道。
     * channel=0 不廣播；channel=1 (全頻) 廣播到世界頻道。
     */
    private fun broadcastAchievement(session: GameSession, achievementId: Int) {
        val def = achievementSystem.getDef(achievementId) ?: return
        if (def.channel <= 0) return

        val channel = ChatChannel.fromCode(def.channel) ?: ChatChannel.WORLD
        responseSender.sendChatMessage(
            session, channel,
            session.playerId, session.characterName,
            "達成了一項成就！"
        )
        logger.info { "成就廣播：${session.characterName} 完成成就 $achievementId channel=${def.channel}" }
    }
}

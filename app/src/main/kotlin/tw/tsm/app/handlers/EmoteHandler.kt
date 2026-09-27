package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.ERolePose
import tw.tsm.domain.constants.HOT_SPRING_MAPS
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * 表情/動作系統 Handler（mainKind = 32）
 *
 * 來源：protocal.lua protocolTable[32]（第 6969-7010 行）
 *
 * C→S 格式：
 * - subKind 1（使用表情）：+表情ID(1)
 * - subKind 2（使用動作）：+動作ID(1)
 * - subKind 3（停止動作）：無資料
 */
class EmoteHandler(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.EMOTE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleEmoji(session, data)
            2 -> handleAction(session, data)
            3 -> handleStopAction(session)
            else -> logger.warn { "未處理的表情/動作子協定：subKind=$subKind" }
        }
    }

    /**
     * C:032-001 使用表情符號
     *
     * 客戶端發送表情 ID（100-124，UIEmotion.lua:18 → 99+i）
     * 廣播 S:032-001 給同地圖所有玩家
     */
    private fun handleEmoji(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val emojiId = reader.readUByte()

        // 驗證：表情 ID 範圍（客戶端發送 100-124）
        if (emojiId < 100 || emojiId > 124) {
            logger.debug { "${session.characterName} 發送無效表情 ID：$emojiId" }
            return
        }

        logger.trace { "${session.characterName} 使用表情 #$emojiId" }

        // 預先序列化一次，廣播到同地圖（含自己）
        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(1).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(emojiId)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }

    /**
     * C:032-002 使用情感動作
     *
     * 業務規則（UIAction.lua:68-101）：
     * - 騎馬中禁止
     * - 溫泉場景（57401/60441）只允許 Sit/Lie
     */
    private fun handleAction(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val poseId = reader.readUByte()

        // 驗證：動作 ID 在合法集合中
        if (poseId !in ERolePose.USABLE_POSES) {
            logger.debug { "${session.characterName} 發送無效動作 ID：$poseId" }
            return
        }

        // 驗證：騎馬中禁止做動作
        if (session.mountNpcId != 0) {
            logger.debug { "${session.characterName} 騎馬中無法使用動作" }
            return
        }

        // 驗證：溫泉場景只允許 Sit/Lie
        if (session.mapId in HOT_SPRING_MAPS && poseId !in ERolePose.HOT_SPRING_ONLY) {
            logger.debug { "${session.characterName} 溫泉場景只允許坐下/躺下" }
            return
        }

        logger.trace { "${session.characterName} 使用動作 #$poseId" }

        // S:032-002 格式：<<+玩家ID(8LE) +動作ID(1)>>
        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(2).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(poseId)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }

    /**
     * C:032-003 停止情感動作
     *
     * 廣播 S:032-002 恢復站立（ERolePose.STAND=8）
     */
    private fun handleStopAction(session: GameSession) {
        logger.trace { "${session.characterName} 停止動作" }

        val packet = PacketWriter(12)
            .writeByte(Opcodes.EMOTE).writeByte(2).writeByte(0)
            .writeLongLE(session.playerId)
            .writeByte(ERolePose.STAND)
            .toByteArray()

        worldManager.broadcastToMap(session.mapId, packet)
    }
}

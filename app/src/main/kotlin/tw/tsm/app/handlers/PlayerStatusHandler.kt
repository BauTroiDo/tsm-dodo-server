package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 玩家狀態處理器（mainKind = 5）
 *
 * 處理客戶端發送的玩家狀態相關封包。
 * 目前支援：
 * - C:005-008 看完新手教學
 * - C:005-012 更換稱號
 */
class PlayerStatusHandler(
    private val bitFlagSystem: BitFlagSystem,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager
) : PacketHandler {
    override val mainKind: Int = Opcodes.PLAYER_STATUS

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            8 -> handleTeachComplete(session, data)
            12 -> handleChangeTitle(session, data)
            else -> logger.trace { "未處理的 C:005-$subKind" }
        }
    }

    /**
     * C:005-008 看完新手教學
     *
     * 格式：+guideId(1B)
     * 客戶端 UITeach.lua 在教學完成時發送。
     * 伺服器查詢對應的 bitFlagId，設定到資料庫，並回傳 S:081-001。
     */
    private suspend fun handleTeachComplete(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val guideId = reader.readUByte()

        val charId = session.roleId.toLong()
        if (charId <= 0) return

        val bitFlagId = bitFlagSystem.completeTeach(charId, guideId)
        if (bitFlagId != null) {
            responseSender.sendBitFlagChanges(session, listOf(bitFlagId to true))
        }
    }

    /**
     * C:005-012 更換稱號
     *
     * 格式：+titleId(2B UInt16 LE)
     * titleId=0 表示卸下稱號。
     * 更新 session + DB，並廣播 S:005-012 給同地圖所有玩家。
     */
    private suspend fun handleChangeTitle(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (data.size < 2) return
        if (session.battleId != 0L) return  // 戰鬥中不能換稱號

        val reader = PacketReader(data)
        val titleId = reader.readUShortLE()

        // 更新 session 狀態
        session.titleId = titleId

        // 持久化到 DB
        characterRepository.updateTitleId(session.roleId.toLong(), titleId)

        // 廣播 S:005-012 給同地圖所有玩家（含自己）
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendTitleChange(player, session.playerId, titleId)
        }

        logger.info { "${session.characterName} 更換稱號：$titleId" }
    }
}

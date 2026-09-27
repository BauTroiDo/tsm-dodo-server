package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.TradeSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 玩家交易處理器（mainKind = 25）
 *
 * 來源：protocal.lua protocolTable[25]
 *
 * 物品交易：C:025-001 發起, C:025-002 上傳物品, C:025-003 確認/取消
 * 武將交易：C:025-010 發起, C:025-011 上傳武將, C:025-012 確認/取消
 * 快遞系統：C:025-020~021（TODO：獨立子系統）
 */
class TradeHandler(
    private val tradeSystem: TradeSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.TRADE  // 25

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleRequestTrade(session, data)
            2 -> handleUploadItems(session, data)
            3 -> handleConfirmTrade(session, data)
            10 -> handleRequestPetTrade(session, data)
            11 -> handleUploadPet(session, data)
            12 -> handleConfirmPetTrade(session, data)
            20 -> { /* TODO：快遞系統 */ }
            21 -> { /* TODO：取消快遞 */ }
            else -> logger.warn { "未處理的交易子協定：subKind=$subKind" }
        }
    }

    /**
     * C:025-001 發起物品交易
     *
     * 格式：+playerID(8)
     */
    private fun handleRequestTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        logger.info { "${session.characterName} 發起交易 → targetId=$targetId" }
        tradeSystem.requestTrade(session, targetId)
    }

    /**
     * C:025-002 上傳交易物品
     *
     * 格式：+money(4) <<+bagIndex(1)>>
     */
    private fun handleUploadItems(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val money = reader.readIntLE()
        if (money < 0) return
        val bagIndices = mutableListOf<Int>()

        while (reader.isReadable) {
            bagIndices.add(reader.readUByte())
        }

        logger.info { "${session.characterName} 上傳交易：金額=$money 物品=${bagIndices.size}件" }
        tradeSystem.uploadItems(session.playerId, money, bagIndices)
    }

    /**
     * C:025-003 確認/取消物品交易
     *
     * 格式：+result(1) [1=確認, 2=取消]
     */
    private fun handleConfirmTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()

        logger.info { "${session.characterName} 交易操作：result=$result (${if (result == 1) "確認" else "取消"})" }
        tradeSystem.confirmOrCancel(session.playerId, result, TradeSystem.MODE_ITEM)
    }

    /**
     * C:025-010 發起武將交易
     *
     * 格式：+playerID(8)
     */
    private fun handleRequestPetTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()

        logger.info { "${session.characterName} 發起武將交易 → targetId=$targetId" }
        tradeSystem.requestTrade(session, targetId, TradeSystem.MODE_PET)
    }

    /**
     * C:025-011 上傳交易武將
     *
     * 格式：+money(4) +followIndex(1)（0 = 只出金錢，UITrade.lua:375-379）
     */
    private fun handleUploadPet(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val money = reader.readIntLE()
        if (money < 0) return
        val petSlot = reader.readUByte()

        logger.info { "${session.characterName} 上傳武將交易：金額=$money petSlot=$petSlot" }
        tradeSystem.uploadPet(session.playerId, money, petSlot)
    }

    /**
     * C:025-012 確認/取消武將交易
     *
     * 格式：+result(1) [1=確認, 2=取消]
     */
    private fun handleConfirmPetTrade(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val result = reader.readUByte()

        logger.info { "${session.characterName} 武將交易操作：result=$result (${if (result == 1) "確認" else "取消"})" }
        tradeSystem.confirmOrCancel(session.playerId, result, TradeSystem.MODE_PET)
    }
}

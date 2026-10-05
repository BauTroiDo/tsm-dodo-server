package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.HouseBoardResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.GiftHistoryEntry
import tw.tsm.gamelogic.systems.HouseBoardSystem
import tw.tsm.gamelogic.systems.PointSystem
import tw.tsm.gamelogic.systems.SouvenirSendResult
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val houseBoardLogger = KotlinLogging.logger {}

/** DVT protocolTable[105] — Hồ sơ / Quà / Gift Wall. */
class HouseBoardHandler(
    private val system: HouseBoardSystem,
    private val sender: HouseBoardResponseSender,
    private val responseSender: ResponseSender,
    private val pointSystem: PointSystem,
    private val worldManager: WorldManager,
) : PacketHandler {
    override val mainKind: Int = Opcodes.HOUSE_BOARD

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleProfile(session, data)
            2 -> handleProfileUpdate(session, data)
            3 -> handleGift(session, data)
            8 -> handleGiftWall(session, data)
            9 -> handleGiftHistory(session, data)
            4, 5, 6 -> houseBoardLogger.debug {
                "HouseBoard Q3 chưa bật: subKind=" + subKind + " charId=" + session.roleId
            }
            else -> houseBoardLogger.warn { "HouseBoard subKind chưa hỗ trợ: " + subKind }
        }
    }

    private suspend fun handleProfile(session: GameSession, data: ByteArray) {
        if (data.size != 8) return
        val targetId = PacketReader(data).readULongLE()
        val view = system.view(targetId) ?: return
        sender.sendProfile(session, view)
    }

    private suspend fun handleProfileUpdate(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val settingType = reader.readUByte()

        val ok = when (settingType) {
            1, 2, 3, 6, 7 -> {
                if (reader.remaining < 1) false
                else {
                    val value = reader.readBig5WithByteLen()
                    reader.remaining == 0 && system.updateProfile(
                        session.roleId.toLong(),
                        settingType,
                        text = value,
                    )
                }
            }
            4 -> {
                if (reader.remaining != 1) false
                else system.updateProfile(
                    session.roleId.toLong(),
                    settingType,
                    byteValue = reader.readUByte(),
                )
            }
            5 -> {
                if (reader.remaining != 2) false
                else system.updateProfile(
                    session.roleId.toLong(),
                    settingType,
                    month = reader.readUByte(),
                    day = reader.readUByte(),
                )
            }
            else -> false
        }

        sender.sendProfileSetResult(session, if (ok) 1 else 0)
    }

    private suspend fun handleGift(session: GameSession, data: ByteArray) {
        // UISouvenirShop.lua actual wire:
        // targetId(Int64) + itemId(UInt16) + quantity(U8) + EUIGetGiftFrom(U8)
        if (data.size != 12) return
        val reader = PacketReader(data)
        val targetId = reader.readULongLE()
        val itemId = reader.readUShortLE()
        val quantity = reader.readUByte()
        val sourceKind = reader.readUByte()

        when (val result = system.sendGift(
            senderId = session.roleId.toLong(),
            targetId = targetId,
            itemId = itemId,
            quantity = quantity,
            sourceKind = sourceKind,
        )) {
            is SouvenirSendResult.Fail -> sender.sendGiftResult(session, result.resultCode)
            is SouvenirSendResult.Success -> {
                for ((slot, count) in result.bagReductions) {
                    responseSender.sendReduceBagItem(session, slot, count)
                }
                when (result.payment) {
                    HouseBoardSystem.PAYMENT_MONEY ->
                        responseSender.sendSubtractMoney(session, result.cost)
                    HouseBoardSystem.PAYMENT_GOLD -> {
                        val (real, virtual) = pointSystem.getPoints(session.roleId.toLong())
                        responseSender.sendPointBalance(session, real, virtual)
                    }
                }

                val friendly = result.newFriendly
                if (friendly != null) {
                    responseSender.sendFriendFriendly(session, targetId, friendly)
                    worldManager.getPlayerById(targetId)?.let { targetSession ->
                        responseSender.sendFriendFriendly(
                            targetSession,
                            session.roleId.toLong(),
                            friendly,
                        )
                    }
                }

                sender.sendGiftResult(session, HouseBoardSystem.RESULT_SUCCESS)

                worldManager.getPlayerById(targetId)?.let { targetSession ->
                    sender.sendIncomingGift(
                        targetSession,
                        GiftHistoryEntry(
                            senderId = result.record.senderId,
                            senderName = result.senderName,
                            itemId = result.record.itemId,
                            quantity = result.record.quantity,
                            time = result.record.time,
                        ),
                    )
                }

                houseBoardLogger.info {
                    "HouseBoard gift success: sender=" + session.roleId +
                        " target=" + targetId + " item=" + itemId +
                        " qty=" + quantity + " source=" + sourceKind +
                        " charm=" + result.newCharm
                }
            }
        }
    }

    private suspend fun handleGiftWall(session: GameSession, data: ByteArray) {
        if (data.size != 8) return
        val targetId = PacketReader(data).readULongLE()
        sender.sendGiftWall(session, system.giftWall(targetId))
    }

    private suspend fun handleGiftHistory(session: GameSession, data: ByteArray) {
        if (data.size != 8) return
        val targetId = PacketReader(data).readULongLE()
        sender.sendGiftHistory(session, system.history(targetId))
    }
}

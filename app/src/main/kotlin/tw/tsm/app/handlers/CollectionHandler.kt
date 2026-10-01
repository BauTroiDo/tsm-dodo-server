package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.CollectionSystem
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

class CollectionHandler(
    private val collectionSystem: CollectionSystem,
    private val responseSender: ResponseSender,
    private val mountSystem: MountSystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.COLLECTION

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleClaimOne(session, data)
            2 -> handleStoreStyle(session, data)
            3 -> handleTakeStyle(session, data)
            6 -> handleCardUpgrade(session, data)
            7 -> handleCardEquip(session, data, equip = true)
            8 -> handleCardEquip(session, data, equip = false)
            11 -> handleClaimMany(session, data)
            13 -> handleNftCardTake(session, data)
            15 -> handleNftMountTake(session, data)
            21 -> handleNftShine(session, data)
            else -> logger.warn { "Sưu Tập chưa xử lý subKind=$subKind bytes=${data.size}" }
        }
    }

    private suspend fun handleStoreStyle(session: GameSession, data: ByteArray) {
        if (data.size != 3) {
            sendSimpleResult(session, 2, false)
            return
        }
        val reader = PacketReader(data)
        val styleId = reader.readUShortLE()
        val part = reader.readUByte()
        val result = collectionSystem.storeStyle(session.roleId.toLong(), styleId, part)
        if (!result.success) {
            sendSimpleResult(session, 2, false)
            return
        }

        responseSender.sendReduceBagItem(session, result.removedBagSlot, 1)
        responseSender.sendCollectionStyleUpdate(session, result.styleId, result.flag)
        responseSender.sendCollectionNftShine(session, collectionSystem.getNftShine(session.roleId.toLong()))
        sendSimpleResult(session, 2, true)
        logger.info { "Cất Tạo hình: charId=${session.roleId} style=$styleId part=$part item=${result.itemId}" }
    }

    private suspend fun handleTakeStyle(session: GameSession, data: ByteArray) {
        if (data.size != 3) {
            sendStyleTakeResult(session, false, 0)
            return
        }
        val reader = PacketReader(data)
        val styleId = reader.readUShortLE()
        val part = reader.readUByte()
        val result = collectionSystem.takeStyle(session.roleId.toLong(), styleId, part)
        if (!result.success) {
            sendStyleTakeResult(session, false, if (result.bagFull) FAIL_STYLE_BAG_FULL else 0)
            return
        }

        for ((slot, thing) in result.addedItems) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        responseSender.sendCollectionStyleUpdate(session, result.styleId, result.flag)
        responseSender.sendCollectionNftShine(session, collectionSystem.getNftShine(session.roleId.toLong()))
        sendStyleTakeResult(session, true)
        logger.info { "Lấy Tạo hình: charId=${session.roleId} style=$styleId part=$part item=${result.itemId}" }
    }

    private suspend fun handleCardUpgrade(session: GameSession, data: ByteArray) {
        if (data.size != 1) {
            sendSimpleResult(session, 6, false)
            return
        }
        val cardId = PacketReader(data).readUByte()
        val result = collectionSystem.upgradeCard(session.roleId.toLong(), cardId)
        if (!result.success) {
            sendSimpleResult(session, 6, false)
            return
        }

        if (result.changedCounts.isNotEmpty()) {
            responseSender.sendRoleCountUpdate(session, result.changedCounts)
        }
        session.send(
            PacketWriter(6)
                .writeByte(Opcodes.COLLECTION).writeByte(6).writeByte(0)
                .writeByte(RESULT_SUCCESS)
                .writeByte(result.cardId)
                .writeByte(result.level)
                .toByteArray()
        )
        responseSender.sendCollectionCardData(
            session,
            collectionSystem.snapshot(session.roleId.toLong()).cardLevels,
        )
        logger.info { "Nâng Tổ hợp thẻ: charId=${session.roleId} card=$cardId lv=${result.level}" }
    }

    private suspend fun handleCardEquip(
        session: GameSession,
        data: ByteArray,
        equip: Boolean,
    ) {
        val subKind = if (equip) 7 else 8
        if (data.size != 1) {
            sendSimpleResult(session, subKind, false)
            return
        }
        val cardId = PacketReader(data).readUByte()
        val charId = session.roleId.toLong()
        val result = if (equip) {
            collectionSystem.equipCard(charId, cardId)
        } else {
            collectionSystem.unequipCard(charId, cardId)
        }
        if (!result.success) {
            sendSimpleResult(session, subKind, false)
            return
        }

        val snapshot = collectionSystem.snapshot(charId)
        responseSender.sendCollectionCardEquipData(
            session,
            snapshot.maxEquipCards,
            snapshot.equippedCards,
        )
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.COLLECTION).writeByte(subKind).writeByte(0)
                .writeByte(RESULT_SUCCESS)
                .writeByte(cardId)
                .toByteArray()
        )
        logger.info {
            "Tổ hợp thẻ ${if (equip) "trang bị" else "tháo"}: charId=$charId card=$cardId"
        }
    }

    private suspend fun handleClaimOne(session: GameSession, data: ByteArray) {
        if (data.size != 2) {
            sendClaimOneResult(session, false, 0)
            return
        }
        val npcId = PacketReader(data).readUShortLE()
        val result = collectionSystem.claimWarriorRewards(session.roleId.toLong(), listOf(npcId))
        if (!result.success) {
            sendClaimOneResult(session, false, if (result.bagFull) FAIL_WARRIOR_BAG_FULL else 0)
            return
        }
        responseSender.sendBitFlagChanges(session, result.rewardFlags.map { it to true })
        for ((slot, thing) in result.addedItems) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        sendClaimOneResult(session, true)
    }

    private suspend fun handleClaimMany(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) {
            sendClaimManyResult(session, RESULT_FAIL)
            return
        }
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (count <= 0 || data.size != 1 + count * 2) {
            sendClaimManyResult(session, RESULT_FAIL)
            return
        }
        val npcIds = ArrayList<Int>(count)
        repeat(count) { npcIds += reader.readUShortLE() }

        val result = collectionSystem.claimWarriorRewards(session.roleId.toLong(), npcIds)
        if (!result.success) {
            if (result.bagFull) sendClaimManyResult(session, RESULT_OTHER, FAIL_WARRIOR_BAG_FULL)
            else sendClaimManyResult(session, RESULT_FAIL)
            return
        }
        responseSender.sendBitFlagChanges(session, result.rewardFlags.map { it to true })
        for ((slot, thing) in result.addedItems) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        sendClaimManyResult(session, RESULT_SUCCESS)
    }

    private suspend fun handleNftCardTake(session: GameSession, data: ByteArray) {
        if (data.size != 2) {
            responseSender.sendCollectionNftCardResult(session, 13, false, 0)
            return
        }
        val getId = PacketReader(data).readUShortLE()
        val charId = session.roleId.toLong()
        val result = collectionSystem.takeNftCard(charId, getId)
        if (!result.success) {
            responseSender.sendCollectionNftCardResult(session, 13, false, 0)
            return
        }
        responseSender.sendBitFlagChanges(session, listOf(result.flagId to false))
        for ((slot, thing) in result.addedItems) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        responseSender.sendCollectionNftCardResult(session, 13, true, result.collectionId)
        responseSender.sendCollectionNftShine(session, result.shine)
    }

    private suspend fun handleNftMountTake(session: GameSession, data: ByteArray) {
        if (data.size != 2) {
            responseSender.sendCollectionNftMountResult(session, 15, false, 0)
            return
        }
        val npcId = PacketReader(data).readUShortLE()
        val charId = session.roleId.toLong()
        if (mountSystem.getCache(charId)?.currentNpcId == npcId) {
            responseSender.sendCollectionNftMountResult(session, 15, false, 0)
            return
        }
        val result = collectionSystem.takeNftMount(charId, npcId)
        if (!result.success) {
            responseSender.sendCollectionNftMountResult(session, 15, false, 0)
            return
        }
        responseSender.sendBitFlagChanges(session, listOf(result.flagId to false))
        for ((slot, thing) in result.addedItems) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        responseSender.sendCollectionNftMountResult(session, 15, true, npcId)
        responseSender.sendCollectionNftShine(session, result.shine)
    }

    private suspend fun handleNftShine(session: GameSession, data: ByteArray) {
        // Client sends its locally calculated value, but the server is authoritative.
        if (data.size != 2) return
        val charId = session.roleId.toLong()
        val shine = collectionSystem.refreshNftAndRuntime(charId)
        responseSender.sendCollectionNftShine(session, shine)
    }

    private fun sendSimpleResult(session: GameSession, subKind: Int, success: Boolean) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.COLLECTION).writeByte(subKind).writeByte(0)
                .writeByte(if (success) RESULT_SUCCESS else RESULT_FAIL)
                .toByteArray()
        )
    }

    private fun sendStyleTakeResult(
        session: GameSession,
        success: Boolean,
        failKind: Int = 0,
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.COLLECTION).writeByte(3).writeByte(0)
            .writeByte(if (success) RESULT_SUCCESS else RESULT_FAIL)
        if (!success) writer.writeByte(failKind)
        session.send(writer.toByteArray())
    }

    private fun sendClaimOneResult(
        session: GameSession,
        success: Boolean,
        failKind: Int = 0,
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.COLLECTION).writeByte(1).writeByte(0)
            .writeByte(if (success) RESULT_SUCCESS else RESULT_FAIL)
        if (!success) writer.writeByte(failKind)
        session.send(writer.toByteArray())
    }

    private fun sendClaimManyResult(
        session: GameSession,
        result: Int,
        failKind: Int = 0,
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.COLLECTION).writeByte(11).writeByte(0)
            .writeByte(result)
        if (result == RESULT_OTHER) writer.writeByte(failKind)
        session.send(writer.toByteArray())
    }

    companion object {
        private const val RESULT_FAIL = 0
        private const val RESULT_SUCCESS = 1
        private const val RESULT_OTHER = 2
        private const val FAIL_WARRIOR_BAG_FULL = 4
        private const val FAIL_STYLE_BAG_FULL = 6
    }
}

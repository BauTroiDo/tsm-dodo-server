package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý quản lý tướng (mainKind = 15)
 *
 * Nguồn: protocal.lua protocolTable[15]
 * Xử lý các thao tác như đày tướng, cất/lấy xe ngựa, đổi tên.
 */
class FollowNpcHandler(
    private val petSystem: PetSystem,
    private val responseSender: ResponseSender,
    private val playerStates: PlayerStateManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.FOLLOW_NPC

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        val expected = when (subKind) { 2, 10, 11 -> 1; 12 -> 2; else -> return }
        if (data.size != expected || data.any { (it.toInt() and 255) !in 1..4 }) return
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return
        // Cart.GetCartSize: no cart before rebirth, two slots at turn 1, four later.
        val turn = playerStates.getState(characterId)?.turn ?: return
        val capacity = when { turn < 1 -> 0; turn == 1 -> 2; else -> 4 }
        if (subKind in 10..12 && capacity == 0) return
        if (subKind == 11 && (data[0].toInt() and 255) > capacity) return
        if (subKind == 12 && (data[1].toInt() and 255) > capacity) return

        when (subKind) {
            2 -> handleKick(session, characterId, data)
            10 -> handleStoreToCart(session, characterId, data, capacity)
            11 -> handleRetrieveFromCart(session, characterId, data)
            12 -> handleSwap(session, characterId, data)
            else -> logger.trace { "Quản lý tướng chưa xử lý subKind=$subKind" }
        }
    }

    /**
     * C:015-002 Đày tướng
     *
     * Định dạng: +chỉ số(1)
     */
    private fun handleKick(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val slotIndex = reader.readUByte()

        val pet = petSystem.kickFollowNpc(characterId, slotIndex)
        if (pet == null) {
            logger.debug { "Thải hồi thất bại: charId=$characterId slot=$slotIndex" }
            return
        }

        // S:015-002 Thông báo xoá
        responseSender.sendFollowNpcRemove(session, session.playerId, slotIndex, showMsg = true)
        logger.info { "${session.characterName} thải hồi tướng slot=$slotIndex npc=${pet.npcId}" }
    }

    /**
     * C:015-010 Cất tướng vào xe ngựa
     *
     * Định dạng: +chỉ số(1)
     */
    private fun handleStoreToCart(session: GameSession, characterId: Long, data: ByteArray, capacity: Int) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()

        val cartSlot = petSystem.storeToCart(characterId, followSlot, capacity)
        if (cartSlot < 0) {
            logger.debug { "Gửi vào xe ngựa thất bại: charId=$characterId slot=$followSlot" }
            return
        }

        // S:015-002 Xoá khỏi theo người (không hiển thị tin nhắn)
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)

        // Gửi lại dữ liệu xe ngựa
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }

    /**
     * C:015-011 Lấy tướng ra khỏi xe ngựa
     *
     * Định dạng: +chỉ số(1)
     */
    private fun handleRetrieveFromCart(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val cartSlot = reader.readUByte()

        val followSlot = petSystem.retrieveFromCart(characterId, cartSlot)
        if (followSlot < 0) {
            logger.debug { "Lấy đồ từ xe ngựa thất bại: charId=$characterId cartSlot=$cartSlot" }
            return
        }

        // S:015-011 Xoá khỏi xe ngựa
        responseSender.sendCartRemove(session, cartSlot)

        // S:015-001 Thêm vào theo người (bằng cách gửi lại toàn bộ dữ liệu tướng theo người)
        val followPets = petSystem.getFollowPets(characterId)
        val newPet = followPets[followSlot]
        if (newPet != null) {
            // S15:1 initializes level/skills to defaults in Role.FollowNpcAppear.
            // S15:8 restores the actual saved data, including equipment and points.
            responseSender.sendFollowNpcFullData(session, listOf(newPet))
        }
    }

    /**
     * C:015-012 Đổi tướng (theo người ↔ xe ngựa)
     *
     * Định dạng: +chỉ số theo sau(1) +chỉ số xe ngựa(1)
     */
    private fun handleSwap(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val cartSlot = reader.readUByte()

        if (!petSystem.swapFollowAndCart(characterId, followSlot, cartSlot)) {
            logger.debug { "Trao đổi thất bại: charId=$characterId follow=$followSlot cart=$cartSlot" }
            return
        }

        // Gửi lại dữ liệu theo người và xe ngựa
        val followPets = petSystem.getFollowPets(characterId)
        // Remove the replaced role first so transient state cannot survive the swap.
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)
        responseSender.sendFollowNpcFullData(session, listOfNotNull(followPets[followSlot]))
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }
}

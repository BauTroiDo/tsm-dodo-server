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
 * 武將管理處理器（mainKind = 15）
 *
 * 來源：protocal.lua protocolTable[15]
 * 處理武將放逐、馬車存取、改名等操作。
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
            else -> logger.trace { "未處理的武將管理 subKind=$subKind" }
        }
    }

    /**
     * C:015-002 放逐武將
     *
     * 格式：+索引(1)
     */
    private fun handleKick(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val slotIndex = reader.readUByte()

        val pet = petSystem.kickFollowNpc(characterId, slotIndex)
        if (pet == null) {
            logger.debug { "放逐失敗：charId=$characterId slot=$slotIndex" }
            return
        }

        // S:015-002 通知刪除
        responseSender.sendFollowNpcRemove(session, session.playerId, slotIndex, showMsg = true)
        logger.info { "${session.characterName} 放逐武將 slot=$slotIndex npc=${pet.npcId}" }
    }

    /**
     * C:015-010 武將存入馬車
     *
     * 格式：+索引(1)
     */
    private fun handleStoreToCart(session: GameSession, characterId: Long, data: ByteArray, capacity: Int) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()

        val cartSlot = petSystem.storeToCart(characterId, followSlot, capacity)
        if (cartSlot < 0) {
            logger.debug { "存入馬車失敗：charId=$characterId slot=$followSlot" }
            return
        }

        // S:015-002 刪除隨身（不顯示訊息）
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)

        // 重送馬車資料
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }

    /**
     * C:015-011 武將領出馬車
     *
     * 格式：+索引(1)
     */
    private fun handleRetrieveFromCart(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val cartSlot = reader.readUByte()

        val followSlot = petSystem.retrieveFromCart(characterId, cartSlot)
        if (followSlot < 0) {
            logger.debug { "領出馬車失敗：charId=$characterId cartSlot=$cartSlot" }
            return
        }

        // S:015-011 刪除馬車
        responseSender.sendCartRemove(session, cartSlot)

        // S:015-001 新增隨身（透過重發全部隨身武將資料）
        val followPets = petSystem.getFollowPets(characterId)
        val newPet = followPets[followSlot]
        if (newPet != null) {
            // S15:1 initializes level/skills to defaults in Role.FollowNpcAppear.
            // S15:8 restores the actual saved data, including equipment and points.
            responseSender.sendFollowNpcFullData(session, listOf(newPet))
        }
    }

    /**
     * C:015-012 交換武將（隨身 ↔ 馬車）
     *
     * 格式：+跟隨索引(1) +馬車索引(1)
     */
    private fun handleSwap(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val cartSlot = reader.readUByte()

        if (!petSystem.swapFollowAndCart(characterId, followSlot, cartSlot)) {
            logger.debug { "交換失敗：charId=$characterId follow=$followSlot cart=$cartSlot" }
            return
        }

        // 重發隨身和馬車資料
        val followPets = petSystem.getFollowPets(characterId)
        // Remove the replaced role first so transient state cannot survive the swap.
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)
        responseSender.sendFollowNpcFullData(session, listOfNotNull(followPets[followSlot]))
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }
}

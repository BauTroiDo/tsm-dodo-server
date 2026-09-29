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
 * Bá»™ xá»­ lÃ½ quáº£n lÃ½ tÆ°á»›ng (mainKind = 15)
 *
 * Nguá»“n: protocal.lua protocolTable[15]
 * Xá»­ lÃ½ cÃ¡c thao tÃ¡c nhÆ° Ä‘Ã y tÆ°á»›ng, cáº¥t/láº¥y xe ngá»±a, Ä‘á»•i tÃªn.
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
        if (data.size != expected) return
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        val followCapacity = petSystem.getFollowCapacity(characterId)
        val first = data[0].toInt() and 255

        // Cart.GetCartSize: no cart before rebirth, two slots at turn 1, four later.
        // Do not widen cart just because carried follower indices become 1..9.
        val turn = playerStates.getState(characterId)?.turn ?: return
        val cartCapacity = when { turn < 1 -> 0; turn == 1 -> 2; else -> 4 }

        when (subKind) {
            2 -> if (first !in 1..followCapacity) return
            10 -> {
                if (cartCapacity == 0 || first !in 1..followCapacity) return
            }
            11 -> {
                if (cartCapacity == 0 || first !in 1..cartCapacity) return
            }
            12 -> {
                val cartSlot = data[1].toInt() and 255
                if (cartCapacity == 0 || first !in 1..followCapacity || cartSlot !in 1..cartCapacity) return
            }
        }

        when (subKind) {
            2 -> handleKick(session, characterId, data)
            10 -> handleStoreToCart(session, characterId, data, cartCapacity)
            11 -> handleRetrieveFromCart(session, characterId, data)
            12 -> handleSwap(session, characterId, data)
            else -> logger.trace { "Quáº£n lÃ½ tÆ°á»›ng chÆ°a xá»­ lÃ½ subKind=$subKind" }
        }
    }

    /**
     * C:015-002 ÄÃ y tÆ°á»›ng
     *
     * Äá»‹nh dáº¡ng: +chá»‰ sá»‘(1)
     */
    private fun handleKick(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val slotIndex = reader.readUByte()

        val pet = petSystem.kickFollowNpc(characterId, slotIndex)
        if (pet == null) {
            logger.debug { "Tháº£i há»“i tháº¥t báº¡i: charId=$characterId slot=$slotIndex" }
            return
        }

        // S:015-002 ThÃ´ng bÃ¡o xoÃ¡
        responseSender.sendFollowNpcRemove(session, session.playerId, slotIndex, showMsg = true)
        logger.info { "${session.characterName} tháº£i há»“i tÆ°á»›ng slot=$slotIndex npc=${pet.npcId}" }
    }

    /**
     * C:015-010 Cáº¥t tÆ°á»›ng vÃ o xe ngá»±a
     *
     * Äá»‹nh dáº¡ng: +chá»‰ sá»‘(1)
     */
    private fun handleStoreToCart(session: GameSession, characterId: Long, data: ByteArray, capacity: Int) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()

        val cartSlot = petSystem.storeToCart(characterId, followSlot, capacity)
        if (cartSlot < 0) {
            logger.debug { "Gá»­i vÃ o xe ngá»±a tháº¥t báº¡i: charId=$characterId slot=$followSlot" }
            return
        }

        // S:015-002 XoÃ¡ khá»i theo ngÆ°á»i (khÃ´ng hiá»ƒn thá»‹ tin nháº¯n)
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)

        // Gá»­i láº¡i dá»¯ liá»‡u xe ngá»±a
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }

    /**
     * C:015-011 Láº¥y tÆ°á»›ng ra khá»i xe ngá»±a
     *
     * Äá»‹nh dáº¡ng: +chá»‰ sá»‘(1)
     */
    private fun handleRetrieveFromCart(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val cartSlot = reader.readUByte()

        val followSlot = petSystem.retrieveFromCart(characterId, cartSlot)
        if (followSlot < 0) {
            logger.debug { "Láº¥y Ä‘á»“ tá»« xe ngá»±a tháº¥t báº¡i: charId=$characterId cartSlot=$cartSlot" }
            return
        }

        // S:015-011 XoÃ¡ khá»i xe ngá»±a
        responseSender.sendCartRemove(session, cartSlot)

        // S:015-001 ThÃªm vÃ o theo ngÆ°á»i (báº±ng cÃ¡ch gá»­i láº¡i toÃ n bá»™ dá»¯ liá»‡u tÆ°á»›ng theo ngÆ°á»i)
        val followPets = petSystem.getFollowPets(characterId)
        val newPet = followPets[followSlot]
        if (newPet != null) {
            // S15:1 initializes level/skills to defaults in Role.FollowNpcAppear.
            // S15:8 restores the actual saved data, including equipment and points.
            responseSender.sendFollowNpcFullData(session, listOf(newPet))
        }
    }

    /**
     * C:015-012 Äá»•i tÆ°á»›ng (theo ngÆ°á»i â†” xe ngá»±a)
     *
     * Äá»‹nh dáº¡ng: +chá»‰ sá»‘ theo sau(1) +chá»‰ sá»‘ xe ngá»±a(1)
     */
    private fun handleSwap(session: GameSession, characterId: Long, data: ByteArray) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val cartSlot = reader.readUByte()

        if (!petSystem.swapFollowAndCart(characterId, followSlot, cartSlot)) {
            logger.debug { "Trao Ä‘á»•i tháº¥t báº¡i: charId=$characterId follow=$followSlot cart=$cartSlot" }
            return
        }

        // Gá»­i láº¡i dá»¯ liá»‡u theo ngÆ°á»i vÃ  xe ngá»±a
        val followPets = petSystem.getFollowPets(characterId)
        // Remove the replaced role first so transient state cannot survive the swap.
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, showMsg = false)
        responseSender.sendFollowNpcFullData(session, listOfNotNull(followPets[followSlot]))
        val cartPets = petSystem.getCartPets(characterId)
        responseSender.sendCartData(session, cartPets.values.toList())
    }
}

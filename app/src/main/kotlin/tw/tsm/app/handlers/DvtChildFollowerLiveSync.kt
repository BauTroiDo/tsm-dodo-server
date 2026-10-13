package tw.tsm.app.handlers

import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.repositories.ChildRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.PetSystem

/**
 * DVT Role.SetChildNpc can only point to a follower already initialized via
 * S015-001/008. Invoke AFTER the carried NPC is actually sent to the client.
 * S015 removal already clears the child pointer in Role.FollowNpcDisappear.
 */
object DvtChildFollowerLiveSync {
    suspend fun afterCarriedPetAppeared(
        session: GameSession,
        characterId: Long,
        newlyCarried: FollowNpcData?,
        petSystem: PetSystem,
        childRepository: ChildRepository?,
    ) {
        if (characterId <= 0 || newlyCarried == null || childRepository == null) return
        val state = childRepository.loadState(characterId) ?: return
        // Neither NPC template, name nor client-supplied slot can create a link.
        if (state.petInstanceId == null || state.petInstanceId != newlyCarried.instanceId) return
        val cache = petSystem.getCache(characterId) ?: return
        val slot = ChildFollowerBindingPolicy.followIndex(
            child = state.snapshot,
            boundPetInstanceId = state.petInstanceId,
            follow = cache.follow,
            owned = cache.follow.values + cache.cart.values +
                cache.inn.values + cache.warehouse,
            maxFollowSlot = petSystem.getFollowCapacity(characterId),
        )
        if (slot == newlyCarried.slotIndex) {
            session.send(ChildPackets.deployed(slot, state.snapshot))
        }
    }
}

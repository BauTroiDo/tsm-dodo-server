package tw.tsm.app.handlers

import tw.tsm.domain.valueobjects.ChildDataSnapshot
import tw.tsm.domain.valueobjects.FollowNpcData

/**
 * Pure fail-closed resolver for original DVT S069-009 / Role.SetChildNpc().
 * Only a stored, adult child with an already-authenticated pet-instance UUID
 * may bind to a real carried follower. Never infer by npcId or slot alone.
 */
object ChildFollowerBindingPolicy {
    fun followIndex(
        child: ChildDataSnapshot,
        boundPetInstanceId: String?,
        follow: Map<Int, FollowNpcData>,
        owned: Collection<FollowNpcData>,
        maxFollowSlot: Int,
    ): Int? {
        if (child.kind != ChildDataSnapshot.ADULT_KIND ||
            boundPetInstanceId.isNullOrBlank() || maxFollowSlot <= 0
        ) return null

        // Detect corrupt duplicate instance IDs across cart/inn/warehouse,
        // not merely among visible followers. Never bind ambiguous state.
        if (owned.count { it.instanceId == boundPetInstanceId } != 1) return null
        val matches = follow.entries.filter { it.value.instanceId == boundPetInstanceId }
        if (matches.size != 1) return null
        val (slot, pet) = matches.single()
        if (slot !in 1..minOf(maxFollowSlot, 255) ||
            pet.slotIndex != slot || pet.storageType != 1
        ) return null
        return slot
    }
}

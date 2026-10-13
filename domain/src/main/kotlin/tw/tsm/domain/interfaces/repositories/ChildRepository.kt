package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ChildDataSnapshot

/** A genuine stored ChildData, with an optional proven pet-instance binding. */
data class ChildProfileState(
    val snapshot: ChildDataSnapshot,
    val petInstanceId: String?,
)

/**
 * Server-authoritative persistence for the exact DVT S:069 ChildData.
 * No client-supplied snapshot write is permitted; only validated server services
 * may create/update a child. Absence of a row means NO CHILD, never a default child.
 */
interface ChildRepository {
    suspend fun load(characterId: Long): ChildDataSnapshot?

    /** Single consistent read of the 93-byte child profile and optional pet UUID. */
    suspend fun loadState(characterId: Long): ChildProfileState?

    /**
     * Trusted server-only, write-once child-pet binding.
     * Requires an existing ADULT child, exactly one real owned pet UUID,
     * and no previous binding to another pet. No client opcode calls this.
     */
    suspend fun bindAdultToOwnedPet(characterId: Long, petInstanceId: String): Boolean
    suspend fun save(characterId: Long, snapshot: ChildDataSnapshot)
    /**
     * Atomically persist a trusted server-side item-use total. Does NOT create a
     * child or accept a client-supplied counter. Null means absent/invalid state.
     */
    suspend fun setItemUseTotal(characterId: Long, newTotal: Int): ChildDataSnapshot?

    /** No child creation / inventory spending; validated, locked care mutation. */
    suspend fun updateCare(characterId: Long, stodge: Int?, satiation: Int?): ChildDataSnapshot?
}

package tw.tsm.domain.interfaces.repositories

import tw.tsm.domain.valueobjects.ChildDataSnapshot

/**
 * Server-authoritative persistence for the exact DVT S:069 ChildData.
 * No client-supplied snapshot write is permitted; only validated server services
 * may create/update a child. Absence of a row means NO CHILD, never a default child.
 */
interface ChildRepository {
    suspend fun load(characterId: Long): ChildDataSnapshot?
    suspend fun save(characterId: Long, snapshot: ChildDataSnapshot)
    /**
     * Atomically persist a trusted server-side item-use total. Does NOT create a
     * child or accept a client-supplied counter. Null means absent/invalid state.
     */
    suspend fun setItemUseTotal(characterId: Long, newTotal: Int): ChildDataSnapshot?
}

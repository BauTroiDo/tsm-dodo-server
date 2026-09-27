package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.valueobjects.FollowNpcData

/** Normal pet skills from DVT UISkillTree.AddSkillLv / SendNpcSkill.
 * Soul replacement skill tables are not implemented by this server.
 */
class PetSkillSystem(
    private val pets: PetSystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val skillDefs: Map<Int, SkillDef>
) {
    data class Snapshot(val pet: FollowNpcData, val skills: Map<Int, Int>)

    fun upgrade(characterId: Long, slot: Int, upgrades: List<Pair<Int, Int>>): Snapshot? {
        val cache = pets.getCache(characterId) ?: return null
        synchronized(cache) {
            val pet = cache.follow[slot] ?: return null
            // Lua forbids the equipped exclusive weapon's skill. Its data table
            // is not loaded by this server yet, so armed pets cannot be validated.
            if (pet.soulId != 0 || pet.equips[3] != null) return null
            val npc = npcDefs[pet.npcId] ?: return null
            val ids = npc.skills.take(3)
            val levels = listOf(pet.skill1Lv, pet.skill2Lv, pet.skill3Lv)
            val current = ids.withIndex().filter { it.value > 0 }
                .associate { it.value to levels[it.index] }
            val unchanged = Snapshot(pet, current)
            if (upgrades.isEmpty() || upgrades.size > 3 || upgrades.map { it.first }.distinct().size != upgrades.size) return unchanged

            val proposed = current.toMutableMap()
            var cost = 0L
            val forbidden = setOf(14026, 14077, 10016, 11016, 12016, 13015, 22011, 23011,
                npc.specialSkill, npc.upgradeSkill, npc.passiveSkill)
            for ((id, target) in upgrades) {
                val from = current[id] ?: return unchanged
                val def = skillDefs[id] ?: return unchanged
                if (id in forbidden || target !in 1..minOf(255, def.maxLv) || target < from ||
                    def.learnPoint < 0 || def.levelUpPoint < 0) return unchanged
                if (target == from) continue // Replayed absolute target levels do not spend again.
                // Lua doubles only the FIRST point, and only elements 1..4.
                val first = if (from == 0) def.learnPoint.toLong() *
                    (if (def.element in 1..4 && def.element != npc.element) 2 else 1) else 0L
                cost += first + (target - maxOf(from, 1)).toLong() * def.levelUpPoint
                proposed[id] = target
            }
            if (cost > pet.skillPoint || pet.skillPoint < 0) return unchanged

            // Lua needs ANY defined prerequisite, and allows points added on this
            // same page. Resolve a batch independently of Lua pairs() iteration order;
            // cycles with no learned prerequisite cannot bootstrap themselves.
            val learned = current.filterValues { it > 0 }.keys.toMutableSet()
            val pending = upgrades.filter { current[it.first] == 0 && it.second > 0 }.map { it.first }.toMutableSet()
            while (pending.isNotEmpty()) {
                val ready = pending.filter { id ->
                    val prerequisites = skillDefs.getValue(id).preSkills.filter { it in skillDefs }
                    prerequisites.isEmpty() || prerequisites.any { it in learned }
                }
                if (ready.isEmpty()) return unchanged
                learned.addAll(ready)
                pending.removeAll(ready.toSet())
            }
            val updated = pet.copy(skillPoint = pet.skillPoint - cost.toInt(),
                skill1Lv = proposed[ids.getOrNull(0)] ?: pet.skill1Lv,
                skill2Lv = proposed[ids.getOrNull(1)] ?: pet.skill2Lv,
                skill3Lv = proposed[ids.getOrNull(2)] ?: pet.skill3Lv)
            if (updated != pet) {
                cache.follow[slot] = updated
                cache.dirty = true // Existing PetSystem.save/saveAndUnload persists these fields.
            }
            return Snapshot(updated, proposed)
        }
    }
}

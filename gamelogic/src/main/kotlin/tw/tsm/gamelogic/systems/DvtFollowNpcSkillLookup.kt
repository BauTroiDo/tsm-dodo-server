package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.CollectWarriorSortDef
import tw.tsm.domain.data.ExclusiveWeaponDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData

/**
 * Server-side mirror of DVT RoleController:GetSkillLv for FollowNpc.
 *
 * Keep this lookup separate from the active battle-command skill list:
 * Lua GetSkillLv includes passive skills even though a passive is not itself
 * proven to be a selectable C:050 battle command.
 */
object DvtFollowNpcSkillLookup {
    private const val EQUIP_HAND = 3

    fun hasSoulSkill(soul: SoulThingData?, skillId: Int): Boolean =
        soul?.skills?.any { it.id == skillId && it.level > 0 } == true

    fun getSkillLevel(
        pet: FollowNpcData,
        npc: NpcDef,
        skillId: Int,
        npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap(),
        itemDefs: Map<Int, ItemDef> = emptyMap(),
        exclusiveWeaponDefs: Map<Int, Map<Int, ExclusiveWeaponDef>> = emptyMap(),
        collectWarriorSortDefs: List<CollectWarriorSortDef> = emptyList(),
        equippedSoul: SoulThingData? = null,
    ): Int {
        // DVT returns immediately when the requested ID matches a normal slot,
        // including when that individual pet's learned level is zero.
        val normalIds = npc.skills.take(3)
        val normalLevels = listOf(pet.skill1Lv, pet.skill2Lv, pet.skill3Lv)
        for (i in normalIds.indices) {
            if (normalIds[i] == skillId) {
                return normalLevels.getOrElse(i) { 0 }
            }
        }

        if (pet.canUseSpecialSkill && npc.specialSkill == skillId) {
            return 1
        }

        val upgrade = npcUpgradeDefs[pet.upgradeLv]
        if (upgrade != null && npc.upgradeSkill == skillId) {
            return upgrade.skillLv
        }

        val hand = pet.equips[EQUIP_HAND]
        if (hand != null && ownsExclusiveWeapon(hand.itemId, pet.npcId, itemDefs, exclusiveWeaponDefs, collectWarriorSortDefs)) {
            val weapon = exclusiveWeaponDefs[hand.itemId]?.get(hand.enhanceLevel)
            if (weapon != null && weapon.skillId == skillId) {
                return weapon.skillLevel
            }
        }

        if (skillId != 0 && npc.passiveSkill == skillId) {
            return npc.passiveSkillLv
        }

        val soul = equippedSoul?.takeIf { it.equipId == pet.npcId }
        return soul?.skills?.firstOrNull { it.id == skillId }?.level ?: 0
    }

    private fun ownsExclusiveWeapon(
        itemId: Int,
        npcId: Int,
        itemDefs: Map<Int, ItemDef>,
        exclusiveWeaponDefs: Map<Int, Map<Int, ExclusiveWeaponDef>>,
        collectWarriorSortDefs: List<CollectWarriorSortDef>,
    ): Boolean {
        if (exclusiveWeaponDefs[itemId] == null) return false
        val item = itemDefs[itemId] ?: return false
        val baseNpcId = item.spare3
        val group = collectWarriorSortDefs.firstOrNull { it.npcIds.firstOrNull() == baseNpcId }
            ?: return false
        return npcId in group.npcIds
    }
}

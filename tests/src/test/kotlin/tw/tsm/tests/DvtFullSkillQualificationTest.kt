package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.CollectWarriorSortDatLoader
import tw.tsm.data.loaders.ExclusiveWeaponDatLoader
import tw.tsm.data.loaders.ItemDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.data.loaders.NpcUpgradeDatLoader
import tw.tsm.domain.data.CollectWarriorSortDef
import tw.tsm.domain.data.ExclusiveWeaponAttributeDef
import tw.tsm.domain.data.ExclusiveWeaponDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.DvtFollowNpcSkillLookup

class DvtFullSkillQualificationTest {
    private fun existing(vararg parts: String): Path {
        val direct = Path.of(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Path.of("..", *parts)
        check(Files.exists(parent)) { "Missing fixture: " + parts.joinToString("/") }
        return parent
    }

    private fun bytes(vararg parts: String): ByteArray = Files.readAllBytes(existing(*parts))

    private val npcs by lazy {
        NpcDatLoader.load(bytes("File.dat", "Data-ios", "IOS_Npc_C.dat"))
    }
    private val items by lazy {
        ItemDatLoader.load(bytes("File.dat", "Data-ios", "IOS_Item_C.dat"))
    }
    private val upgrades by lazy {
        NpcUpgradeDatLoader.load(bytes("File.dat", "Data-ios", "IOS_NpcUpgrade_C.dat"))
    }
    private val exclusiveWeapons by lazy {
        ExclusiveWeaponDatLoader.load(bytes("File.dat", "Data-ios", "IOS_ExclusiveWeapon_C.dat"))
    }
    private val warriorSorts by lazy {
        CollectWarriorSortDatLoader.load(bytes("File.dat", "Data-ios", "IOS_CollectWarriorSort_C.dat"))
    }

    @Test
    fun `real DVT exclusive weapon tables match Lua record sizes and keys`() {
        val valid = bytes("File.dat", "Data-ios", "IOS_ExclusiveWeapon_C.dat")
        val sortBytes = bytes("File.dat", "Data-ios", "IOS_CollectWarriorSort_C.dat")
        assertEquals(4 + 692 * 29, valid.size)
        assertEquals(4 + 567 * 9, sortBytes.size)
        // Lua tables are keyed assignments, so duplicate keys intentionally collapse.
        // Current DVT data has 692 rows but six duplicate (itemId, enhanceLv) keys for item 64524.
        assertEquals(686, exclusiveWeapons.values.sumOf { it.size })
        assertEquals(567, warriorSorts.size)
        assertEquals(15, upgrades.size)
        assertTrue(upgrades.values.all { it.skillLv == 0 })

        val allRows = exclusiveWeapons.values.flatMap { it.values }
        assertTrue(allRows.any { it.skillId > 0 && it.skillLevel > 0 })
        assertTrue(warriorSorts.all { it.npcIds.size == 3 })

        assertThrows(IllegalArgumentException::class.java) {
            ExclusiveWeaponDatLoader.load(valid + byteArrayOf(0))
        }
        assertThrows(Exception::class.java) {
            ExclusiveWeaponDatLoader.load(valid.copyOf(valid.size - 1))
        }
    }

    @Test
    fun `FollowNpc GetSkillLv covers normal special upgrade passive Soul and exclusive weapon`() {
        val normalEntry = npcs.entries.firstOrNull { (it.value.skills.firstOrNull() ?: 0) > 0 }
            ?: error("fixture has no NPC normal skill")
        val normalNpc = normalEntry.value
        val normalSkill = normalNpc.skills[0]
        val normalPet = FollowNpcData(
            slotIndex = 1,
            npcId = normalEntry.key,
            skill1Lv = 4,
            isDeployed = true,
        )
        assertEquals(
            4,
            DvtFollowNpcSkillLookup.getSkillLevel(normalPet, normalNpc, normalSkill),
        )
        // Lua returns immediately on a matching normal slot even when learned level is zero.
        assertEquals(
            0,
            DvtFollowNpcSkillLookup.getSkillLevel(normalPet.copy(skill1Lv = 0), normalNpc, normalSkill),
        )

        val specialEntry = npcs.entries.firstOrNull { it.value.specialSkill > 0 }
            ?: error("fixture has no NPC special skill")
        assertEquals(
            1,
            DvtFollowNpcSkillLookup.getSkillLevel(
                FollowNpcData(
                    slotIndex = 1,
                    npcId = specialEntry.key,
                    canUseSpecialSkill = true,
                ),
                specialEntry.value,
                specialEntry.value.specialSkill,
            ),
        )
        assertEquals(
            0,
            DvtFollowNpcSkillLookup.getSkillLevel(
                FollowNpcData(slotIndex = 1, npcId = specialEntry.key),
                specialEntry.value,
                specialEntry.value.specialSkill,
            ),
        )

        val syntheticUpgradeLv = 3
        val syntheticUpgradeSkill = 64888
        val syntheticUpgradeNpc = normalNpc.copy(upgradeSkill = syntheticUpgradeSkill)
        val syntheticUpgrade = NpcUpgradeDef(
            requireMoney = 0,
            requireDebris = 0,
            requirePicture = 0,
            attributeAdd = 0,
            skillLv = 7,
            upgradeLvIcon = 0,
        )
        assertEquals(
            7,
            DvtFollowNpcSkillLookup.getSkillLevel(
                FollowNpcData(
                    slotIndex = 1,
                    npcId = normalEntry.key,
                    upgradeLv = syntheticUpgradeLv,
                ),
                syntheticUpgradeNpc,
                syntheticUpgradeSkill,
                npcUpgradeDefs = mapOf(syntheticUpgradeLv to syntheticUpgrade),
            ),
        )

        val passiveEntry = npcs.entries.firstOrNull { it.value.passiveSkill > 0 && it.value.passiveSkillLv > 0 }
            ?: error("fixture has no NPC passive skill")
        assertEquals(
            passiveEntry.value.passiveSkillLv,
            DvtFollowNpcSkillLookup.getSkillLevel(
                FollowNpcData(slotIndex = 1, npcId = passiveEntry.key),
                passiveEntry.value,
                passiveEntry.value.passiveSkill,
            ),
        )

        val soulSkill = 65000
        val soulPet = FollowNpcData(slotIndex = 1, npcId = normalEntry.key)
        val soul = SoulThingData(
            sn = 7L,
            itemId = 59501,
            element = 1,
            equipId = normalEntry.key,
            skills = listOf(
                SoulSkillData(soulSkill, 6),
                SoulSkillData(),
                SoulSkillData(),
                SoulSkillData(),
            ),
        )
        assertEquals(
            6,
            DvtFollowNpcSkillLookup.getSkillLevel(
                soulPet,
                normalNpc,
                soulSkill,
                equippedSoul = soul,
            ),
        )

        val syntheticWeaponId = 65530
        val syntheticWeaponSkill = 64999
        val syntheticWeapon = ExclusiveWeaponDef(
            itemId = syntheticWeaponId,
            enhanceLevel = 3,
            skillId = syntheticWeaponSkill,
            skillLevel = 5,
            attributes = listOf(
                ExclusiveWeaponAttributeDef(0, 0),
                ExclusiveWeaponAttributeDef(0, 0),
            ),
            element = 0,
            elementValue = 0,
            plusAttribute = ExclusiveWeaponAttributeDef(0, 0),
        )
        val syntheticItem = items.values.first().copy(spare3 = normalEntry.key)
        val syntheticGroup = CollectWarriorSortDef(
            index = 1,
            npcIds = listOf(normalEntry.key, 0, 0),
            nft = 0,
        )
        val equipped = ThingData(
            itemId = syntheticWeaponId,
            quantity = 1,
            enhanceLevel = 3,
        )
        val ownerPet = normalPet.copy(equips = mapOf(3 to equipped))
        assertEquals(
            5,
            DvtFollowNpcSkillLookup.getSkillLevel(
                ownerPet,
                normalNpc,
                syntheticWeaponSkill,
                itemDefs = mapOf(syntheticWeaponId to syntheticItem),
                exclusiveWeaponDefs = mapOf(syntheticWeaponId to mapOf(3 to syntheticWeapon)),
                collectWarriorSortDefs = listOf(syntheticGroup),
            ),
        )

        val wrongEntry = npcs.entries.first { it.key != normalEntry.key }
        assertEquals(
            0,
            DvtFollowNpcSkillLookup.getSkillLevel(
                ownerPet.copy(npcId = wrongEntry.key),
                wrongEntry.value,
                syntheticWeaponSkill,
                itemDefs = mapOf(syntheticWeaponId to syntheticItem),
                exclusiveWeaponDefs = mapOf(syntheticWeaponId to mapOf(3 to syntheticWeapon)),
                collectWarriorSortDefs = listOf(syntheticGroup),
            ),
        )
    }
}

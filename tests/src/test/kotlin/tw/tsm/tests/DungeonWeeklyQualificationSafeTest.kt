package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.domain.data.DungeonNeedDef
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.gamelogic.systems.DvtWeeklyQualification

class DungeonWeeklyQualificationSafeTest {
    private fun bytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", name))

    private val dungeons by lazy { DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat")) }
    private val npcs by lazy { NpcDatLoader.load(bytes("IOS_Npc_C.dat")) }

    private fun pet(npcId: Int) = FollowNpcData(
        slotIndex = 1,
        npcId = npcId,
        isDeployed = true,
    )

    private fun soul(npcId: Int, element: Int, level: Int) = SoulThingData(
        sn = 1L,
        itemId = 59501,
        element = element,
        equipId = npcId,
        level = level,
    )

    @Test
    fun `unrestricted and round-objective stages do not require a deployed NPC at entry`() {
        for (id in listOf(18, 21, 22, 36, 37, 81, 82)) {
            assertEquals(
                DvtWeeklyQualification.Status.OK,
                DvtWeeklyQualification.checkEntry(dungeons.getValue(id), null, npcs).status,
                "dungeon=$id",
            )
        }
    }

    @Test
    fun `moral element and turn match DVT singular fight NPC semantics`() {
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(23), pet(11250), npcs).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(23), pet(10012), npcs).status,
        )

        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(30), pet(10002), npcs).status,
        )

        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(29), pet(10012), npcs).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(29), pet(10039), npcs).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(34), pet(10039), npcs).status,
        )
    }

    @Test
    fun `supported restriction without deployed NPC maps to S47-019 result three semantics`() {
        assertEquals(
            DvtWeeklyQualification.Status.FIGHT_NPC_REQUIRED,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(23), null, npcs).status,
        )
    }

    @Test
    fun `skill gates remain unsupported while Soul gates use protocol120 state`() {
        val requireSkill = DvtWeeklyQualification.checkEntry(dungeons.getValue(28), pet(10012), npcs)
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, requireSkill.status)
        assertEquals(4, requireSkill.unsupportedKind)

        val forbidSkill = DvtWeeklyQualification.checkEntry(dungeons.getValue(35), pet(41202), npcs)
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, forbidSkill.status)
        assertEquals(5, forbidSkill.unsupportedKind)

        // Dungeon63 has Soul element + forbidden-skill criteria. Once Soul kind8 is supported,
        // the remaining evidence blocker must be kind5 rather than kind8.
        val mixed = DvtWeeklyQualification.checkEntry(
            dungeons.getValue(63),
            pet(10012),
            npcs,
            soul(10012, element = 1, level = 1),
        )
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, mixed.status)
        assertEquals(5, mixed.unsupportedKind)
    }

    @Test
    fun `Soul element min and max level gates match DVT semantics`() {
        val warrior = pet(10012)

        val minStage = dungeons.getValue(71).copy(
            needPar = listOf(DungeonNeedDef(9, 10), DungeonNeedDef(8, 8)),
        )
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(
                minStage, warrior, npcs, soul(10012, element = 8, level = 10)
            ).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(minStage, warrior, npcs, null).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(
                minStage, warrior, npcs, soul(10012, element = 7, level = 10)
            ).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(
                minStage, warrior, npcs, soul(10012, element = 8, level = 9)
            ).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(
                minStage, warrior, npcs, soul(10039, element = 8, level = 10)
            ).status,
        )

        val maxStage = dungeons.getValue(72).copy(
            needPar = listOf(DungeonNeedDef(10, 50), DungeonNeedDef(0, 0)),
        )
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(
                maxStage, warrior, npcs, soul(10012, element = 2, level = 50)
            ).status,
        )
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(
                maxStage, warrior, npcs, soul(10012, element = 2, level = 51)
            ).status,
        )
    }

    @Test
    fun `late Crazy stages with only normal criteria can still be evaluated safely`() {
        val elementThree = npcs.entries.first { it.value.element == 3 }.key
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(78), pet(elementThree), npcs).status,
        )

        val firstTurn = npcs.entries.first { it.value.turn == 2 }.key
        assertEquals(
            DvtWeeklyQualification.Status.OK,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(79), pet(firstTurn), npcs).status,
        )

        val wrongTurn = npcs.entries.first { it.value.turn != 2 }.key
        assertEquals(
            DvtWeeklyQualification.Status.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkEntry(dungeons.getValue(79), pet(wrongTurn), npcs).status,
        )
    }
}

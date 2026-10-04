package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.domain.valueobjects.FollowNpcData
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
    fun `skill and Soul dependent gates remain unsupported instead of using partial server state`() {
        val requireSkill = DvtWeeklyQualification.checkEntry(dungeons.getValue(28), pet(10012), npcs)
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, requireSkill.status)
        assertEquals(4, requireSkill.unsupportedKind)

        val forbidSkill = DvtWeeklyQualification.checkEntry(dungeons.getValue(35), pet(41202), npcs)
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, forbidSkill.status)
        assertEquals(5, forbidSkill.unsupportedKind)

        val soul = DvtWeeklyQualification.checkEntry(dungeons.getValue(63), pet(10012), npcs)
        assertEquals(DvtWeeklyQualification.Status.UNSUPPORTED, soul.status)
        assertEquals(8, soul.unsupportedKind)
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

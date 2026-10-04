package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.DungeonDatLoader
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.systems.DvtWeeklyQualification

class DungeonWeeklyQualificationTest {
    private fun bytes(name: String): ByteArray =
        Files.readAllBytes(Path.of("..", "File.dat", "Data-ios", name))

    private val dungeons by lazy { DungeonDatLoader.load(bytes("IOS_Dungeon_C.dat")) }
    private val npcs by lazy { NpcDatLoader.load(bytes("IOS_Npc_C.dat")) }

    private fun pet(
        npcId: Int,
        skill1: Int = 1,
        skill2: Int = 1,
        skill3: Int = 1,
    ) = FollowNpcData(
        slotIndex = 1,
        npcId = npcId,
        skill1Lv = skill1,
        skill2Lv = skill2,
        skill3Lv = skill3,
        isDeployed = true,
    )

    @Test
    fun `unrestricted weekly stage does not require a deployed NPC`() {
        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(18), null, null),
        )
    }

    @Test
    fun `faction element and rebirth restrictions use singular deployed NPC attributes`() {
        val ranger = pet(11250)
        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(23), ranger, npcs.getValue(11250)),
        )
        assertEquals(
            DvtWeeklyQualification.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(23), pet(10012), npcs.getValue(10012)),
        )

        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(30), pet(10002), npcs.getValue(10002)),
        )

        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(29), pet(10012), npcs.getValue(10012)),
        )
        assertEquals(
            DvtWeeklyQualification.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(29), pet(10039), npcs.getValue(10039)),
        )
        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(34), pet(10039), npcs.getValue(10039)),
        )
    }

    @Test
    fun `skill restrictions respect learned level and DVT forbidden semantics`() {
        val fireArrowNpc = npcs.getValue(10012)
        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(28), pet(10012, skill3 = 1), fireArrowNpc),
        )
        assertEquals(
            DvtWeeklyQualification.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(28), pet(10012, skill3 = 0), fireArrowNpc),
        )

        val boundaryNpc = npcs.getValue(41202)
        assertEquals(
            DvtWeeklyQualification.RESTRICTION_FAILED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(35), pet(41202, skill3 = 1), boundaryNpc),
        )
        assertEquals(
            DvtWeeklyQualification.OK,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(35), pet(41202, skill3 = 0), boundaryNpc),
        )
    }

    @Test
    fun `restricted stage reports DVT missing fight NPC result three`() {
        assertEquals(
            DvtWeeklyQualification.FIGHT_NPC_REQUIRED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(28), null, null),
        )
    }

    @Test
    fun `soul weekly is not silently validated as ordinary weekly`() {
        assertEquals(
            DvtWeeklyQualification.UNSUPPORTED,
            DvtWeeklyQualification.checkKind3(dungeons.getValue(63), null, null),
        )
    }
}

package tw.tsm.tests

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.DvtOrgResourceResultPolicy

/**
 * Executable original-DVT evidence for every organization resource reward.
 * Always remain fail-closed until org membership/treasury persistence exists.
 */
class DvtOrganizationQuestResourceContractTest {
    private fun source(path: String): Path =
        listOf(Path.of(path), Path.of("..", path)).first { Files.isRegularFile(it) }

    @Test fun originalLuaNamesFiveOrgResourceKindsInExactProtocolOrder() {
        val lua = Files.readString(source("lua/Logic/Organization.lua"))
        for ((kind, name) in listOf(
            DvtOrgResourceResultPolicy.Kind.GOLD to "Gold",
            DvtOrgResourceResultPolicy.Kind.GRAIN to "Grain",
            DvtOrgResourceResultPolicy.Kind.MINERAL to "Mineral",
            DvtOrgResourceResultPolicy.Kind.WOOD to "Wood",
            DvtOrgResourceResultPolicy.Kind.CLOTH to "Cloth",
        )) {
            assertTrue(Regex("""(?m)^\s*${name}\s*=\s*${kind.style}\s*,""").containsMatchIn(lua),
                "Never alter style ${kind.style} mapping without original Organization.lua")
            assertEquals(kind, DvtOrgResourceResultPolicy.Kind.fromStyle(kind.style))
        }
        assertNull(DvtOrgResourceResultPolicy.Kind.fromStyle(6))
        assertNull(DvtOrgResourceResultPolicy.Kind.fromStyle(0))
        assertFalse(DvtOrgResourceResultPolicy.isExecutable())
    }

    @Test fun all35OriginalEveEffectsAreFiveResourceBundleChains() {
        val eve = EveDataLoader.load(Files.readAllBytes(source("gamedata/Eve.emg")))
        val found = mutableListOf<Triple<Int, Int, List<DvtOrgResourceResultPolicy.Delta>>>()
        for ((mapId, scene) in eve) {
            for ((eveNo, event) in scene.npcEvents) {
                for (condition in event.conditions) {
                    val original = condition.results.filter {
                        it.resultType in setOf(0, 7) && it.resultClass == 11
                    }
                    if (original.isEmpty()) continue
                    val decoded = original.map {
                        DvtOrgResourceResultPolicy.decode(
                            it.resultType, it.resultClass, it.parameter,
                            it.parameterStyle, it.resultValue
                        )
                    }
                    assertTrue(decoded.all { it != null }, "Unrecognized DVT class11 in map $mapId")
                    assertEquals(5, decoded.size, "All originals form five-resource bundles")
                    assertEquals((1..5).toSet(), decoded.map { it!!.kind.style }.toSet())
                    assertTrue(condition.results.any { it.resultClass == 2 },
                        "Every org-resource bundle also mutates mission state; do not skip it")
                    found.add(Triple(mapId, eveNo, decoded.filterNotNull()))
                }
            }
        }
        assertEquals(7, found.size, "All 35 effects must belong to seven original chains")
        assertEquals(
            mapOf(11572 to 1, 26522 to 1, 26525 to 4, 57523 to 1),
            found.groupingBy { it.first }.eachCount()
        )
        assertEquals(35, found.sumOf { it.third.size })
        assertEquals((1..5).associateWith { 7 },
            found.flatMap { it.third }.groupingBy { it.kind.style }.eachCount())
        assertFalse(DvtOrgResourceResultPolicy.isExecutable(),
            "Client-side enum does not authorize server-wide treasury grants")
    }

    @Test fun rejectNonOrgOrMalformedResultsWithoutGuessingWalletOperations() {
        assertEquals(
            DvtOrgResourceResultPolicy.Delta(
                DvtOrgResourceResultPolicy.Kind.GOLD, 500
            ),
            DvtOrgResourceResultPolicy.decode(0, 11, 0, 1, 500)
        )
        val bad = listOf(
            listOf(7, 11, 0, 1, 500),
            listOf(0, 5, 0, 1, 500),
            listOf(0, 11, 1, 1, 500),
            listOf(0, 11, 0, 0, 500),
            listOf(0, 11, 0, 6, 500),
            listOf(0, 11, 0, 1, 0),
            listOf(0, 11, 0, 1, -500),
        )
        for (shape in bad) {
            assertNull(DvtOrgResourceResultPolicy.decode(
                shape[0], shape[1], shape[2], shape[3], shape[4]))
        }
    }
}

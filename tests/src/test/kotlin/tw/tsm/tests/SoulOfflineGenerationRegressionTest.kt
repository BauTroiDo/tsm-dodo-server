package tw.tsm.tests

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.NpcDatLoader
import tw.tsm.data.loaders.NpcSoulDatLoader
import tw.tsm.data.loaders.SkillDatLoader
import tw.tsm.data.loaders.SoulDatLoader
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.gamelogic.systems.OfflineSoulGenerationPolicy
import tw.tsm.gamelogic.systems.SoulSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class SoulOfflineGenerationRegressionTest {
    private fun data(name: String): ByteArray {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("File.dat/Data-ios/IOS_Soul_C.dat")) }
        return Files.readAllBytes(root.resolve("File.dat/Data-ios/$name"))
    }

    @Test
    fun customOfflineGeneratorPreservesAllAuthoritativeDvtFields() {
        val soulDefs = SoulDatLoader.load(data("IOS_Soul_C.dat"))
        val npcSoulDefs = NpcSoulDatLoader.load(data("IOS_NpcSoul_C.dat"))
        val npcDefs = NpcDatLoader.load(data("IOS_Npc_C.dat"))
        val skillDefs = SkillDatLoader.load(data("IOS_Skill_C.dat"))

        val policy = OfflineSoulGenerationPolicy(
            soulDefs = soulDefs,
            npcSoulDefs = npcSoulDefs,
            npcDefs = npcDefs,
        )

        val expectedSkillNames = mapOf(
            10004 to "Cây tinh",
            10010 to "kết giới",
            11013 to "Thuật hồi sinh",
            11014 to "Băng phong",
            13005 to "Thuật ẩn mình",
        )
        for ((skillId, expectedName) in expectedSkillNames) {
            assertEquals(expectedName, skillDefs.getValue(skillId).name)
        }

        for (quality in 0..3) {
            val npcSoul = npcSoulDefs.values.first { it.quality == quality }
            val soulDef = soulDefs.getValue(quality)
            val npc = npcDefs.getValue(npcSoul.npcId)
            val guaranteedSkills = soulDef.skillGetProb.count { it >= 10_000 }
            val possibleSkills = soulDef.skillGetProb.count { it > 0 }

            repeat(40) { seed ->
                val generated = requireNotNull(policy.generate(npcSoul.itemId, Random(seed + quality * 1000)))
                assertEquals(0L, generated.sn)
                assertEquals(npcSoul.itemId, generated.itemId)
                assertEquals(npc.element, generated.element)
                assertEquals(0, generated.equipId)
                assertEquals(1, generated.level)
                assertEquals(0, generated.starLevel)
                assertEquals(6, generated.startAtt.size)
                assertEquals(soulDef.startPoint, generated.startAtt.sum())
                assertEquals(generated.startAtt, generated.nowAtt)

                val skills = generated.skills.filter { it.id > 0 }
                assertTrue(skills.size in guaranteedSkills..possibleSkills)
                assertEquals(skills.map { it.id }.distinct().size, skills.size)
                assertTrue(skills.all { it.id in OfflineSoulGenerationPolicy.OFFLINE_SOUL_SKILL_POOL })
                assertTrue(skills.all { it.level == 1 })
                assertEquals(4, generated.skills.size)
            }
        }
    }

    @Test
    fun soulSystemCreateAndDeleteArePersistFirstAndCacheSafe() = runTest {
        val repo = mockk<SoulRepository>()
        coEvery { repo.findByCharacterId(77L) } returns emptyList()
        coEvery { repo.save(any(), any()) } just Runs
        coEvery { repo.delete(any(), any()) } just Runs

        val system = SoulSystem(repo)
        system.load(77L)

        val template = SoulThingData(
            sn = 0L,
            itemId = 59507,
            element = 3,
            startAtt = listOf(25, 25, 25, 25, 25, 25),
            nowAtt = listOf(25, 25, 25, 25, 25, 25),
            skills = listOf(
                SoulSkillData(10004, 1),
                SoulSkillData(),
                SoulSkillData(),
                SoulSkillData(),
            ),
        )

        val created = requireNotNull(system.create(77L, template))
        assertTrue(created.sn > 0L)
        assertEquals(created, system.find(77L, created.sn))
        assertEquals(listOf(created), system.snapshot(77L))
        coVerify(exactly = 1) { repo.save(77L, created) }

        assertTrue(system.delete(77L, created.sn))
        assertEquals(emptyList<SoulThingData>(), system.snapshot(77L))
        coVerify(exactly = 1) { repo.delete(77L, created.sn) }
    }
}

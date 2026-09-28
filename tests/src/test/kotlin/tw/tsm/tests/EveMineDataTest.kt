package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.MineEncounterSystem
import java.nio.file.Files
import java.nio.file.Path

class EveMineDataTest : DescribeSpec({
    val eveFile = listOf(
        Path.of("gamedata", "Eve.emg"),
        Path.of("..", "gamedata", "Eve.emg")
    ).first { Files.exists(it) }
    val scenes = EveDataLoader.load(Files.readAllBytes(eveFile))

    describe("DVT MineData map 12591") {
        val scene = scenes.getValue(12591)

        it("parse đủ ba vùng MineData") {
            scene.mines.size shouldBe 3

            scene.mines.getValue(1).apply {
                events shouldBe listOf(3)
                gridX shouldBe 1
                gridY shouldBe 1
                gridWidth shouldBe 1
                gridHeight shouldBe 1
                sizeKind shouldBe 1
            }
            scene.mines.getValue(2).apply {
                events shouldBe listOf(4)
                gridX shouldBe 50
                gridY shouldBe 11
                gridWidth shouldBe 26
                gridHeight shouldBe 18
                sizeKind shouldBe 0
            }

            scene.mines.getValue(3).apply {
                events shouldBe listOf(5)
                gridX shouldBe 49
                gridY shouldBe 75
                gridWidth shouldBe 30
                gridHeight shouldBe 19
                sizeKind shouldBe 0
            }
        }

        it("ưu tiên vùng mỏ cụ thể hơn vùng toàn map") {
            MineEncounterSystem.selectMine(scene, 1090, 280)?.id shouldBe 2
            MineEncounterSystem.selectMine(scene, 1510, 1800)?.id shouldBe 3
            MineEncounterSystem.selectMine(scene, 400, 400)?.id shouldBe 1
        }

        it("các Eve của MineData thực sự dẫn tới fight data") {
            val fightIds = scene.mines.values
                .flatMap { it.events }
                .flatMap { eveNo ->
                    scene.npcEvents.getValue(eveNo).conditions
                        .flatMap { it.results }
                        .filter { it.resultType == 3 }
                        .map { it.resultMeanNo }
                }
                .toSet()

            fightIds.size shouldBe 19
            fightIds shouldBe (1..19).toSet()
        }
    }
})

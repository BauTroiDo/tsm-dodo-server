package tw.tsm.tests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import tw.tsm.data.loaders.EveDataLoader
import java.nio.file.Files
import java.nio.file.Path

class EveClassCoverageTest : DescribeSpec({
    describe("Eve.emg 類別集合") {
        val eveFile = listOf(
            Path.of("gamedata", "Eve.emg"),
            Path.of("..", "gamedata", "Eve.emg")
        ).first { Files.exists(it) }
        val eveBytes = Files.readAllBytes(eveFile)
        val events = EveDataLoader.load(eveBytes).values.flatMap { it.npcEvents.values }

        it("conditionClass 集合必須被明確追蹤") {
            val actual = events
                .flatMap { it.conditions }
                .map { it.conditionClass }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)
        }

        it("resultType 集合必須被明確追蹤") {
            val actual = events
                .flatMap { event -> event.conditions.flatMap { it.results } }
                .map { it.resultType }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        }

        it("resultClass 集合必須被明確追蹤") {
            val actual = events
                .flatMap { event -> event.conditions.flatMap { it.results } }
                .map { it.resultClass }
                .toSet()

            actual shouldBe setOf(0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15)
        }
    }
})

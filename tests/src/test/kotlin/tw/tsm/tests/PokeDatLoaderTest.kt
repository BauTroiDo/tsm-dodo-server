package tw.tsm.tests

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tw.tsm.data.loaders.PokeDatLoader
import java.nio.file.Files
import java.nio.file.Path

class PokeDatLoaderTest {
    private fun file(name: String): Path = listOf(
        Path.of("File.dat", "Data-ios", name),
        Path.of("..", "File.dat", "Data-ios", name),
    ).first { Files.isRegularFile(it) }

    @Test
    fun pokePoolAndRatesDecodeExactly() {
        val awards = PokeDatLoader.loadCandidates(Files.readAllBytes(file("IOS_PokeData_C.dat")))
        val rates = PokeDatLoader.loadRates(Files.readAllBytes(file("IOS_PokeRate_C.dat")))
        assertEquals(93, awards.size)
        assertEquals(mapOf(1 to 18, 2 to 4, 3 to 2, 4 to 1), rates)
        assertEquals(mapOf(1 to 38, 2 to 18, 3 to 16, 4 to 21), awards.groupingBy { it.kind }.eachCount())
        assertTrue(awards.all { it.id in 1..93 })
    }
}

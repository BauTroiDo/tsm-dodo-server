package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.gamelogic.systems.EveConditionResolver
import tw.tsm.gamelogic.systems.PlayerEventState
import java.nio.file.Files
import java.nio.file.Path

class DvtMissionComparatorRegressionTest {
  private fun file(vararg parts: String): ByteArray {
    val direct=Path.of(parts.first(),*parts.drop(1).toTypedArray())
    val path=if(Files.exists(direct)) direct else Path.of("..",*parts)
    return Files.readAllBytes(path)
  }

  @Test fun `mission11120 real Eve branch resolves item hand-in`() {
    val eve=EveDataLoader.load(file("gamedata","Eve.emg"))
    val event=eve.getValue(12705).npcEvents.getValue(6)
    val resolver=EveConditionResolver(eve)
    val state=PlayerEventState(
      missionSteps=mapOf(11120 to 1),
      bagItems=mapOf(44042 to 1),
      level=53,
    )
    val resolved=requireNotNull(resolver.resolveEvent(event,state,12705))
    val results=resolved.results
    assertTrue(results.any{it.resultClass==1 && it.parameter==44042 && it.parameterStyle==1 && it.resultValue==-1})
    assertTrue(results.any{it.resultClass==1 && it.parameter==45605 && it.parameterStyle==1 && it.resultValue==1})
    assertTrue(results.any{it.resultClass==2 && it.parameter==11120 && it.parameterStyle==3})
    assertTrue(results.any{it.resultClass==2 && it.parameter==11121 && it.parameterStyle==1 && it.resultValue==1})
  }
}
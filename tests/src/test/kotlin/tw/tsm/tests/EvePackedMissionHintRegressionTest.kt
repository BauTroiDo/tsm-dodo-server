package tw.tsm.tests

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.data.loaders.MarkDatLoader
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.EveMissionPreflightPolicy
import tw.tsm.gamelogic.systems.EvePackedMissionHintPolicy
import java.nio.file.Files
import java.nio.file.Path

class EvePackedMissionHintRegressionTest {
    private fun effect(mission: Int, style: Int, amount: Int, cls: Int=2, type: Int=0) =
        EveResult(0,1,type,cls,mission,style,amount,0)

    @Test fun `recognizes DVT packed mission hint only when it references a known mission`() {
        val known=setOf(12130,11488,11432,12080)
        assertTrue(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,612130),known))
        assertTrue(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,5411488),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,612131),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,254),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(12130,1,612130),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,2,612130),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,612130,cls=7),known))
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(effect(2,1,612130,type=3),known))
    }

    @Test fun `mission 12130 mainline stage can advance without mutating mission 2`() {
        val known=setOf(12130,2)
        val segment=listOf(effect(12130,1,1),effect(2,1,612130))
        val filtered=segment.filterNot { EvePackedMissionHintPolicy.isNonMutatingHint(it,known) }
        assertEquals(listOf(effect(12130,1,1)),filtered)
        assertNull(EveMissionPreflightPolicy.validate(filtered, mapOf(12130 to 1,2 to 0)))
        assertNotNull(EveMissionPreflightPolicy.validate(segment,mapOf(12130 to 1,2 to 0)))
    }

    @Test fun `actual DVT PC Eve mainline contains the packed markers in five branches at 14636`() {
        val eve=EveDataLoader.load(Files.readAllBytes(Path.of("..","gamedata","Eve.emg")))
        val known=MarkDatLoader.load(Files.readAllBytes(
            Path.of("..","File.dat","Data-ios","IOS_Mark_C.dat")
        )).first.keys
        val event=eve.getValue(14636).npcEvents.getValue(8)
        val relevant=event.conditions.filter { cond ->
            cond.results.any {
                it.resultType==0 && it.resultClass==2 &&
                    it.parameter==2 && it.parameterStyle==1 && it.resultValue==612130
            }
        }
        assertEquals(5,relevant.size,"DVT Eve scene14636/eve8 has five level-band branches")
        for(cond in relevant) {
            val hints=cond.results.filter {
                EvePackedMissionHintPolicy.isNonMutatingHint(it,known)
            }
            assertEquals(1,hints.size,"DVT chain must include one packed, non-mutating marker")
            assertEquals(612130,hints.single().resultValue)
        }
    }

    @Test fun `other invalid mission step overflows remain rejected`() {
        val known=setOf(12130)
        val invalid=effect(12130,1,612130)
        assertFalse(EvePackedMissionHintPolicy.isNonMutatingHint(invalid,known))
        assertEquals(EveMissionPreflightPolicy.Failure.INVALID_STEP,
            EveMissionPreflightPolicy.validate(listOf(invalid),mapOf(12130 to 1))?.reason)
    }
}

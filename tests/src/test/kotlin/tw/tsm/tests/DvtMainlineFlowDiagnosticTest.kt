package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.data.DatReader
import tw.tsm.data.loaders.EveDataLoader
import tw.tsm.data.loaders.MarkDatLoader
import tw.tsm.domain.data.EveResult
import tw.tsm.gamelogic.systems.EveChainResolver
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opt-in read-only DVT quest flow diagnostic.
 * Uses exact live PC DVT MarkSteps/Eve bytes, does not touch player DB.
 */
class DvtMainlineFlowDiagnosticTest {
    private data class Step(
        val missionId: Int, val stepNo: Int, val guide: String,
        val sceneId: Int, val x: Int, val y: Int, val kind: Int, val target: Int
    )
    private fun readSteps(bytes: ByteArray): List<Step> {
        val reader=DatReader(bytes)
        val items=ArrayList<Step>()
        repeat(reader.readInt32()) {
            val mission=reader.readUInt16()
            val step=reader.readByte()
            reader.readUnicodeString()
            repeat(5) {
                reader.readByte()
                reader.readUInt16()
                reader.readByte()
                reader.readUInt16()
                reader.readUInt16()
                reader.readUInt16()
                reader.readByte()
                reader.readByte()
            }
            val guide=reader.readUnicodeString()
            val scene=reader.readUInt16()
            val x=reader.readUInt16()
            val y=reader.readUInt16()
            val kind=reader.readByte()
            val target=reader.readByte()
            reader.readByte()
            reader.readByte()
            items+=Step(mission,step,guide,scene,x,y,kind,target)
        }
        return items
    }

    @Test fun scanActiveDvtQuestChainsAndGuidance() {
        val rootString=System.getenv("TSM_QUEST_AUDIT_ROOT")
        org.junit.jupiter.api.Assumptions.assumeTrue(rootString!=null,"Opt-in with TSM_QUEST_AUDIT_ROOT")
        val root=Path.of(rootString!!)
        val marks=MarkDatLoader.load(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_Mark_C.dat"))).first
        val steps=readSteps(Files.readAllBytes(root.resolve("File.dat/Data-ios/IOS_MarkStep_C.dat")))
        val scenes=EveDataLoader.load(Files.readAllBytes(root.resolve("gamedata/Eve.emg")))
        val guidedMainIds=steps.map { it.missionId }.filter { marks[it]?.kind==1 }.toSet()
        val report=StringBuilder()
        fun line(s: String="") { report.appendLine(s) }
        line("# DVT PC mainline: event-chain / guide target deep audit")
        line("Source: active production `gamedata/Eve.emg` and IOS Mark binary files. Read-only.")
        line()
        line("## Two potentially missing navigation Doors")
        val missingTargets=listOf(11566 to 2,11934 to 4)
        for ((mission,stepNo) in missingTargets) {
            val step=steps.single { it.missionId==mission && it.stepNo==stepNo }
            val map=scenes[step.sceneId]
            line("### Mission $mission step $stepNo — scene ${step.sceneId}")
            line("- Guide: ${step.guide.take(130)}; position=(${step.x},${step.y}), kind=${step.kind}, target=${step.target}")
            line("- Doors in scene: ${map?.doors?.values?.sortedBy{it.id}?.joinToString { "${it.id}@${it.x},${it.y}(events=${it.events})" }}")
            line("- SceneInfo: ${map?.sceneInfos?.values?.sortedBy{it.eveNo}?.joinToString { "${it.eveNo}->${it.backgroundNo}@${it.playerAppearX},${it.playerAppearY}" }}")
            line("- NPC placements: ${map?.npcs?.values?.sortedBy{it.id}?.take(25)?.joinToString { "${it.id}@${it.x},${it.y}(events=${it.events})" }}")
            for ((scId, sc) in scenes) {
                for ((eveId, event) in sc.npcEvents) {
                    val matches=event.conditions.filter { cond ->
                        cond.conditionClass==2 && cond.conditionParameter==mission
                    }
                    if (matches.isNotEmpty()) {
                        val extra=matches.take(4).joinToString { cond ->
                            "${cond.conditionNo}(ops=${cond.conditionOps},value=${cond.conditionValue},result=${cond.results.take(8).map{ r->"${r.resultType}/${r.resultClass}/${r.parameter}/${r.parameterStyle}/${r.resultValue}"}})"
                        }
                        line("- Eve quest condition ${scId}/${eveId}: $extra")
                    }
                }
            }
            line()
        }

        line("## Six guided steps lacking exact-value gate")
        val suspects=listOf(10092 to 2,10096 to 3,10300 to 7,11120 to 1,11120 to 2,12130 to 61)
        for ((mission,stepNo) in suspects) {
            val step=steps.single { it.missionId==mission && it.stepNo==stepNo }
            val gates=scenes.flatMap { (sceneId,scene) ->
                scene.npcEvents.flatMap { (eventId,event) ->
                    event.conditions.filter { it.conditionClass==2 && it.conditionParameter==mission }
                        .map { Triple(sceneId,eventId,it) }
                }
            }.sortedWith(compareBy({it.first},{it.second},{it.third.conditionNo}))
            line("### Mission $mission step $stepNo target=${step.sceneId}/${step.kind}/${step.target} at (${step.x},${step.y})")
            line("- Guide: ${step.guide.take(130)}")
            for ((sceneId,eventId,c) in gates.take(45)) {
                line("- scene=$sceneId eve=$eventId cond=${c.conditionNo} style=${c.conditionParameterStyle} op=${c.conditionOps} threshold=${c.conditionValue} andNum=${c.andNum} results=${c.results.take(4).map { "${it.resultType}/${it.resultClass}/${it.parameter}/${it.parameterStyle}/${it.resultValue}" }}")
            }
            line()
        }

        line("## Full chain risk audit (includes AND partner conditions and head effects)")
        data class ChainIssue(val scene:Int,val eve:Int,val condition:Int,val missions:Set<Int>,val unsupported:List<EveResult>,val class17:Boolean)
        val issues=ArrayList<ChainIssue>()
        var chainCount=0
        var guidedRelated=0
        for ((sceneId,scene) in scenes) {
            for ((eveId,event) in scene.npcEvents) {
                for (chain in EveChainResolver.buildChains(event.conditions)) {
                    chainCount++
                    val head=chain.firstOrNull()?:continue
                    val links=chain.filter{it.conditionClass==2 && it.conditionParameter in guidedMainIds}.map{it.conditionParameter}.toSet() +
                        head.results.filter{it.resultType in setOf(0,7) && it.resultClass==2 && it.parameter in guidedMainIds}.map{it.parameter}.toSet()
                    if(links.isEmpty())continue
                    guidedRelated++
                    val unsupported=head.results.filter {it.resultType in setOf(0,7) && it.resultClass in setOf(11,13,15)}
                    val class17=chain.any {it.conditionClass==17}
                    if (unsupported.isNotEmpty() || class17) {
                        issues+=ChainIssue(sceneId,eveId,head.conditionNo,links,unsupported,class17)
                    }
                }
            }
        }
        line("- Total Eve chains: $chainCount")
        line("- Related to guided mainline: $guidedRelated")
        line("- Mainline chains with class11/13/15 or condition17: ${issues.size}")
        for (q in issues.take(110)) {
            line("- scene=${q.scene} eve=${q.eve} head=${q.condition} missions=${q.missions} hasCond17=${q.class17} unsupported=${q.unsupported.map{"${it.resultClass}/${it.parameter}/${it.parameterStyle}/${it.resultValue}"}}")
        }
        line()
        line("## Oversized mission-step results sharing guided mainline chains")
        data class Oversized(val scene:Int,val eve:Int,val head:Int,val missionLinks:Set<Int>,val res:EveResult)
        val oversized=ArrayList<Oversized>()
        for ((sceneId,scene) in scenes) for ((eveId,event) in scene.npcEvents) {
            for (chain in EveChainResolver.buildChains(event.conditions)) {
                val head=chain.firstOrNull()?:continue
                val links=chain.filter{it.conditionClass==2 && it.conditionParameter in guidedMainIds}.map{it.conditionParameter}.toSet()+
                    head.results.filter{it.resultType in setOf(0,7) && it.resultClass==2 && it.parameter in guidedMainIds}.map{it.parameter}.toSet()
                if(links.isEmpty())continue
                for(r in head.results) if (r.resultType in setOf(0,7) && r.resultClass==2 &&
                    r.parameterStyle in setOf(1,2) && r.resultValue !in 0..255) {
                    oversized+=Oversized(sceneId,eveId,head.conditionNo,links,r)
                }
            }
        }
        line("- Oversized mission-step results in guided chains: "+oversized.size)
        for (o in oversized.take(120)) {
            line("- scene="+o.scene+" eve="+o.eve+" cond="+o.head+" mainline="+o.missionLinks+
                " resultMission="+o.res.parameter+" style="+o.res.parameterStyle+" value="+o.res.resultValue)
        }
        line("- Example mainline 12130 special marker: "+oversized.count{it.missionLinks.contains(12130)&&it.res.parameter==2&&it.res.resultValue==612130})
        line()
        val output=root.resolve("backups/quest_mainline_audit_20261010/flow_deep_audit.md")
        Files.createDirectories(output.parent)
        Files.writeString(output,report.toString())
        println("FLOW_DIAGNOSTIC_REPORT=$output CHAINS=$chainCount GUIDED_CHAINS=$guidedRelated UNSUPPORTED=${issues.size}")
        assertTrue(chainCount>100)
        assertTrue(guidedMainIds.size>100)
    }
}

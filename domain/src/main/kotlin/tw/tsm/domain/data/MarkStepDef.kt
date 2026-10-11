package tw.tsm.domain.data

/** Immutable DVT MarkStep navigation target in the original client grid space. */
data class MarkNavigationTarget(
    val sceneId: Int,
    val x: Int,
    val y: Int,
    val eventKind: Int,
    val eventId: Int,
) {
    /** A scene AND an actual destination are required by MarkManager.Navigation. */
    val hasDestination: Boolean get() = sceneId > 0 && (x > 0 || y > 0)
}

/** DVT MarkData.ReadStep condition kind: 1=capture, 2=kill, 3=collect. */
data class MarkStepRequirement(
    val kind: Int,
    val id: Int,
    val requiredCount: Int,
    val target: MarkNavigationTarget,
)

/**
 * Full representation of one IOS_MarkStep_C.dat record, preserving the exact
 * navigation and quest-step semantics of Lua Data/MarkData.lua.
 *
 * DVT does NOT serialize areaId in MarkStep. Lua sets endAreaId and condition.areaId
 * to zero; client navigation must derive this from the DoorGroupData graph.
 */
data class MarkStepDef(
    val missionId: Int,
    val step: Int,
    val description: String,
    val conditions: List<MarkStepRequirement>,
    val endGuide: String,
    val endTarget: MarkNavigationTarget,
    val teleport: Int,
    val checkTeam: Boolean,
)

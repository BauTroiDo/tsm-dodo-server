package tw.tsm.domain.data

/**
 * Raw effect block from DVT IOS_ExclusiveSkill_C.dat.
 *
 * The 9-byte layout is byte-stable, but gameplay semantics for kind, arg1,
 * arg2 and parameter are intentionally not guessed here.
 */
data class ExclusiveSkillEffectDef(
    val kind: Int,
    val arg1: Int,
    val arg2: Int,
    val parameter: Int,
    val value: Long,
)

/** One 27-byte DVT exclusive/passive skill record. */
data class ExclusiveSkillDef(
    val skillId: Int,
    val level: Int,
    val headerRaw: List<Int>,
    val effects: List<ExclusiveSkillEffectDef>,
)

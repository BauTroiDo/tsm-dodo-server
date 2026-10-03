package tw.tsm.tests

import io.mockk.every
import io.mockk.mockk
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.data.SkillDef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.gamelogic.battle.BattleAction
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleInstance
import tw.tsm.gamelogic.battle.BattleItemResolver
import tw.tsm.gamelogic.battle.BattleSkillResolver

class DvtBattleCommandSemanticsTest {
    private fun fighter(id: Long, col: Int, row: Int, learnedSkills: IntArray = intArrayOf(), elementValue: Int = 1, specialEquipId: Int = 0, specialDamage: Int = 0, weaponId: Int = 0, weaponKind: Int = 0) = BattleFighter(
        roleId=id, kind=EHuman.PLAYER, name="f$id", col=col, row=row,
        maxHp=1000, maxSp=500, hp=1000, sp=500, level=1, element=elementValue,
        atk=100, def=10, agi=100, intelligence=100, skills=learnedSkills, specialEquipItemId=specialEquipId, specialEquipDamage=specialDamage, weaponItemId=weaponId, weaponItemKind=weaponKind
    )

    private fun skill(fightWayValue: Int, fightAreaValue: Int = 1) = mockk<SkillDef> {
        every { fightWay } returns fightWayValue
        every { requireSp } returns 10
        every { fightArea } returns fightAreaValue
        every { fightWayGrowType } returns 0
        every { attribute } returns EAttribute.HP
        every { round } returns 0
        every { howMuchTimes } returns 1
        every { numerical } returns 100
        every { element } returns 2
    }

    private fun fixture(a: BattleFighter, b: BattleFighter, skills: Map<Int, SkillDef> = emptyMap()): BattleInstance {
        val grid = BattleGrid().also { it.place(a); it.place(b) }
        return BattleInstance(
            battleId=1, warType=0, terrain=0, eventNo=0, grid=grid,
            skillResolver=BattleSkillResolver(skills),
            itemResolver=mockk<BattleItemResolver>(relaxed=true),
            involvedNpcDefs=emptyList()
        ).also { it.addFighter(a); it.addFighter(b) }
    }

    @Test
    fun `duplicate command cannot replace already accepted action`() {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val first = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 0)
        val replay = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 1)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, first))
        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, replay))
        assertEquals(first, actor.pendingAction)
    }

    @Test
    fun `dead actor cannot queue a command`() {
        val actor = fighter(1, 2, 0).also { it.hp=0; it.isDead=true }
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, 0, 0)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertFalse(actor.hasActed)
    }
    @Test
    fun `fightWay eight accepts dead friendly target at queue time`() {
        val reviveId = 11011
        val actor = fighter(1, 2, 0, intArrayOf(reviveId))
        val deadAlly = fighter(2, 3, 0).also { it.hp=0; it.isDead=true }
        val battle = fixture(actor, deadAlly, mapOf(reviveId to skill(8)))
        val action = BattleAction(BattleAction.KIND_SKILL, reviveId, deadAlly.col, deadAlly.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertEquals(action, actor.pendingAction)
    }

    @Test
    fun `ordinary corpse remains on grid across round boundary for later revive`() = runBlocking {
        val alive = fighter(1, 2, 0)
        val dead = fighter(2, 0, 0).also { it.hp = 0; it.isDead = true }
        val battle = fixture(alive, dead)

        battle.resolveRound()

        assertEquals(dead, battle.grid.getAt(dead.col, dead.row))
        assertEquals(listOf(dead), battle.grid.getDeadEnemySide())
    }

    @Test
    fun `fly out corpse leaves grid and cannot be revived`() = runBlocking {
        val alive = fighter(1, 2, 0)
        val flyOut = fighter(2, 0, 0).also { it.hp = 0; it.isDead = true; it.isFlyOut = true }
        val battle = fixture(alive, flyOut)

        battle.resolveRound()

        assertEquals(null, battle.grid.getAt(flyOut.col, flyOut.row))
        assertTrue(battle.grid.getDeadEnemySide().isEmpty())
    }

    @Test
    fun `npc revive AI targets dead friendly when one is present`() {
        val reviveId = 11011
        val npc = BattleFighter(
            roleId = -10, kind = EHuman.MINE_NPC, name = "reviver", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 2,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(reviveId)
        )
        val deadAlly = BattleFighter(
            roleId = -11, kind = EHuman.MINE_NPC, name = "dead", col = 1, row = 1,
            maxHp = 1000, maxSp = 500, hp = 0, sp = 500, level = 1, element = 2,
            atk = 100, def = 10, agi = 100, intelligence = 100
        ).also { it.isDead = true }
        val enemy = fighter(12, 2, 0)
        val grid = BattleGrid()
        val battle = BattleInstance(
            battleId = 2, warType = 0, terrain = 0, eventNo = 0, grid = grid,
            skillResolver = BattleSkillResolver(mapOf(reviveId to skill(8))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, deadAlly, enemy).forEach(battle::addFighter)

        battle.resolveNpcActions { _ -> reviveId }

        assertEquals(reviveId, npc.pendingAction?.id)
        assertEquals(deadAlly.col, npc.pendingAction?.targetCol)
        assertEquals(deadAlly.row, npc.pendingAction?.targetRow)
    }

    @Test
    fun `npc elemental summon is normalized to large variant before target selection`() {
        val baseSummon = 11016
        val largeSummon = 11019
        val npc = BattleFighter(
            roleId = -20, kind = EHuman.MINE_NPC, name = "water-summoner", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 2,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(baseSummon)
        )
        val ally = BattleFighter(
            roleId = -21, kind = EHuman.MINE_NPC, name = "ally", col = 1, row = 1,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 2,
            atk = 100, def = 10, agi = 100, intelligence = 100
        )
        val enemy = fighter(22, 2, 0)
        val battle = BattleInstance(
            battleId = 3, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(largeSummon to skill(2))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, ally, enemy).forEach(battle::addFighter)

        battle.resolveNpcActions { _ -> baseSummon }

        assertEquals(largeSummon, npc.pendingAction?.id)
        assertEquals(enemy.col, npc.pendingAction?.targetCol)
        assertEquals(enemy.row, npc.pendingAction?.targetRow)
    }

    @Test
    fun `chaos enemy npc does not queue an AI command`() {
        val skillId = 12001
        val npc = BattleFighter(
            roleId = -29, kind = EHuman.MINE_NPC, name = "chaos-npc", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 3,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val enemy = fighter(30, 2, 0)
        val battle = BattleInstance(
            battleId = 13, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(1))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, enemy).forEach(battle::addFighter)
        npc.setGroupedStatus(3, BattleStatus.CHAOS, 2, 14021, battle.currentRound)
        var selections = 0

        battle.resolveNpcActions { _ ->
            selections++
            skillId
        }

        assertEquals(0, selections)
        assertEquals(null, npc.pendingAction)
        assertFalse(npc.hasActed)
    }

    @Test
    fun `npc AI keeps selected skill when current SP is below its cost`() {
        val skillId = 12001
        val npc = BattleFighter(
            roleId = -30, kind = EHuman.MINE_NPC, name = "low-sp-npc", col = 0, row = 0,
            maxHp = 1000, maxSp = 1, hp = 1000, sp = 1, level = 1, element = 3,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val enemy = fighter(31, 2, 0)
        val battle = BattleInstance(
            battleId = 4, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(1))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, enemy).forEach(battle::addFighter)

        battle.resolveNpcActions { _ -> skillId }

        assertEquals(skillId, npc.pendingAction?.id)
        assertEquals(enemy.col, npc.pendingAction?.targetCol)
        assertEquals(enemy.row, npc.pendingAction?.targetRow)
    }

    @Test
    fun `npc AI preserves unknown DVT slot id and uses client HandFight fallback without retry`() = runBlocking {
        val unknownId = 65534
        val npc = BattleFighter(
            roleId = -40, kind = EHuman.MINE_NPC, name = "unknown-slot-npc", col = 0, row = 0,
            maxHp = 1000, maxSp = 100, hp = 1000, sp = 100, level = 1, element = 1,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(unknownId)
        )
        val enemy = fighter(41, 2, 0)
        val battle = BattleInstance(
            battleId = 5, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(emptyMap()),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, enemy).forEach(battle::addFighter)
        var selections = 0

        battle.resolveNpcActions { _ ->
            selections++
            unknownId
        }

        assertEquals(1, selections)
        assertEquals(unknownId, npc.pendingAction?.id)
        assertTrue(npc.pendingAction?.id != BattleConstants.HAND_FIGHT_SKILL_ID)
        assertEquals(enemy.col, npc.pendingAction?.targetCol)
        assertEquals(enemy.row, npc.pendingAction?.targetRow)

        val beforeSp = npc.sp
        val result = battle.resolveRound()
        assertEquals(beforeSp, npc.sp)
        assertTrue(
            result.actionResults.single {
                it.attackerCol == npc.col && it.attackerRow == npc.row
            }.targets.isNotEmpty()
        )
    }

    @Test
    fun `queued hostile npc action retargets dead target in PC row-first order`() = runBlocking {
        val skillId = 12001
        val npc = BattleFighter(
            roleId = -45, kind = EHuman.MINE_NPC, name = "retarget-npc", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 3,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val stale = fighter(46, 2, 1).also { it.hp = 0; it.isDead = true }
        val columnFirstWouldChoose = fighter(47, 2, 2)
        val pcFirst = fighter(48, 3, 0)
        val battle = BattleInstance(
            battleId = 7, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(1))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, stale, columnFirstWouldChoose, pcFirst).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(BattleAction.KIND_SKILL, skillId, stale.col, stale.row)
        npc.hasActed = true

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }

        assertTrue(action.targets.isNotEmpty())
        assertEquals(pcFirst.col, action.targets.first().targetCol)
        assertEquals(pcFirst.row, action.targets.first().targetRow)
        assertEquals(1000L, columnFirstWouldChoose.hp)
    }

    @Test
    fun `hostile stale target with no replacement becomes no-op without SP cost`() = runBlocking {
        val skillId = 12001
        val npc = BattleFighter(
            roleId = -49, kind = EHuman.MINE_NPC, name = "retarget-empty", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 3,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val stale = fighter(50, 2, 1).also { it.hp = 0; it.isDead = true }
        val battle = BattleInstance(
            battleId = 8, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(1))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, stale).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(BattleAction.KIND_SKILL, skillId, stale.col, stale.row)
        npc.hasActed = true
        val beforeSp = npc.sp

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }

        assertTrue(action.targets.isEmpty())
        assertEquals(0, action.spConsumed)
        assertEquals(beforeSp, npc.sp)
    }

    @Test
    fun `queued higher fightWay single target does not jump to another living unit`() = runBlocking {
        val skillId = 14015
        val npc = BattleFighter(
            roleId = -52, kind = EHuman.MINE_NPC, name = "stale-debuff", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 1,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val stale = fighter(53, 2, 1).also { it.hp = 0; it.isDead = true }
        val otherLiving = fighter(54, 3, 0)
        val battle = BattleInstance(
            battleId = 9, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(15))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, stale, otherLiving).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(BattleAction.KIND_SKILL, skillId, stale.col, stale.row)
        npc.hasActed = true

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }

        assertEquals(1, action.targets.size)
        assertEquals(stale.col, action.targets.single().targetCol)
        assertEquals(stale.row, action.targets.single().targetRow)
        assertTrue(action.targets.single().attrChanges.isEmpty())
        assertEquals(10, action.spConsumed)
        assertEquals(490L, npc.sp)
        assertEquals(1000L, otherLiving.hp)
    }

    @Test
    fun `queued coordinate attacks replacement occupant at the same cell`() = runBlocking {
        val npc = BattleFighter(
            roleId = -55, kind = EHuman.MINE_NPC, name = "coordinate-npc", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 1,
            atk = 100, def = 10, agi = 100, intelligence = 100
        )
        val original = fighter(56, 2, 1)
        val elsewhere = fighter(57, 3, 0)
        val battle = BattleInstance(
            battleId = 10, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(emptyMap()),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, original, elsewhere).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(
            BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, original.col, original.row
        )
        npc.hasActed = true

        original.hp = 0
        original.isDead = true
        battle.grid.remove(original.col, original.row)
        val replacement = fighter(58, original.col, original.row)
        battle.addFighter(replacement)

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }

        assertTrue(action.targets.isNotEmpty())
        assertEquals(replacement.col, action.targets.first().targetCol)
        assertEquals(replacement.row, action.targets.first().targetRow)
        assertEquals(1000L, elsewhere.hp)
    }

    @Test
    fun `empty queued cell retargets higher fightWay on the originally selected side`() = runBlocking {
        val skillId = 14015
        val npc = BattleFighter(
            roleId = -59, kind = EHuman.MINE_NPC, name = "empty-cell-retarget", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 1,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val pcFirst = fighter(60, 3, 0)
        val later = fighter(61, 2, 4)
        val battle = BattleInstance(
            battleId = 11, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(15))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, pcFirst, later).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(BattleAction.KIND_SKILL, skillId, 2, 2)
        npc.hasActed = true

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }

        assertTrue(action.targets.isNotEmpty())
        assertEquals(pcFirst.col, action.targets.first().targetCol)
        assertEquals(pcFirst.row, action.targets.first().targetRow)
        assertEquals(10, action.spConsumed)
    }

    @Test
    fun `dead centre remains first packet anchor for higher fightWay multi target area`() = runBlocking {
        val skillId = 14015
        val npc = BattleFighter(
            roleId = -62, kind = EHuman.MINE_NPC, name = "dead-centre-area", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 1,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(skillId)
        )
        val deadCentre = fighter(63, 2, 2).also { it.hp = 0; it.isDead = true }
        val above = fighter(64, 2, 1)
        val below = fighter(65, 2, 3)
        val outside = fighter(66, 2, 0)
        val battle = BattleInstance(
            battleId = 12, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(skillId to skill(15, 3))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, deadCentre, above, below, outside).forEach(battle::addFighter)
        npc.pendingAction = BattleAction(BattleAction.KIND_SKILL, skillId, deadCentre.col, deadCentre.row)
        npc.hasActed = true

        val result = battle.resolveRound()
        val action = result.actionResults.single {
            it.attackerCol == npc.col && it.attackerRow == npc.row
        }
        val coords = action.targets.map { it.targetCol to it.targetRow }

        assertEquals(deadCentre.col to deadCentre.row, coords.first())
        assertEquals(setOf(2 to 1, 2 to 2, 2 to 3), coords.toSet())
        assertTrue((2 to 0) !in coords)
        assertTrue(action.targets.first().attrChanges.isEmpty())
        assertEquals(10, action.spConsumed)
    }

    @Test
    fun `npc revive with no corpse keeps selected skill as ineffective no-op without retry`() = runBlocking {
        val reviveId = 11011
        val npc = BattleFighter(
            roleId = -50, kind = EHuman.MINE_NPC, name = "reviver-no-corpse", col = 0, row = 0,
            maxHp = 1000, maxSp = 500, hp = 1000, sp = 500, level = 1, element = 2,
            atk = 100, def = 10, agi = 100, intelligence = 100, skills = intArrayOf(reviveId)
        )
        val enemy = fighter(51, 2, 0)
        val battle = BattleInstance(
            battleId = 6, warType = 0, terrain = 0, eventNo = 0, grid = BattleGrid(),
            skillResolver = BattleSkillResolver(mapOf(reviveId to skill(8))),
            itemResolver = mockk<BattleItemResolver>(relaxed = true), involvedNpcDefs = emptyList()
        )
        listOf(npc, enemy).forEach(battle::addFighter)
        var selections = 0

        battle.resolveNpcActions { _ ->
            selections++
            reviveId
        }

        assertEquals(1, selections)
        assertEquals(reviveId, npc.pendingAction?.id)
        assertTrue(npc.pendingAction?.id != BattleConstants.HAND_FIGHT_SKILL_ID)
        val beforeSp = npc.sp
        val result = battle.resolveRound()
        assertEquals(beforeSp, npc.sp)
        assertTrue(
            result.actionResults.single {
                it.attackerCol == npc.col && it.attackerRow == npc.row
            }.targets.isEmpty()
        )
    }

    @Test
    fun `fightWay eight rejects living friendly target`() {
        val reviveId = 11011
        val actor = fighter(1, 2, 0)
        val livingAlly = fighter(2, 3, 0)
        val battle = fixture(actor, livingAlly, mapOf(reviveId to skill(8)))
        val action = BattleAction(BattleAction.KIND_SKILL, reviveId, livingAlly.col, livingAlly.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }

    @Test
    fun `ordinary friendly skill still rejects dead target`() {
        val healId = 11001
        val actor = fighter(1, 2, 0)
        val deadAlly = fighter(2, 3, 0).also { it.hp=0; it.isDead=true }
        val battle = fixture(actor, deadAlly, mapOf(healId to skill(6)))
        val action = BattleAction(BattleAction.KIND_SKILL, healId, deadAlly.col, deadAlly.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }


    @Test
    fun `round end clears accepted command and allows next round submission`() = runBlocking {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, target.col, target.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        battle.resolveRound()

        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }

    @Test
    fun `blocked queued actor is cleared without executing and can act after status clears`() = runBlocking {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target)
        val action = BattleAction(BattleAction.KIND_SKILL, BattleConstants.HAND_FIGHT_SKILL_ID, target.col, target.row)

        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        actor.setGroupedStatus(1, BattleStatus.ICE_BOUND, 2, 11014, battle.currentRound)
        val result = battle.resolveRound()

        assertTrue(result.actionResults.none { it.attackerCol == actor.col && it.attackerRow == actor.row })
        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)

        actor.clearAllGroupedStatuses()
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
    }


    @Test
    fun `Lua forbidden non battle skill is rejected even when learned`() {
        val forbiddenId = 14003
        val actor = fighter(1, 2, 0, intArrayOf(forbiddenId))
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target, mapOf(forbiddenId to skill(1)))
        val action = BattleAction(BattleAction.KIND_SKILL, forbiddenId, target.col, target.row)

        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertFalse(actor.hasActed)
        assertEquals(null, actor.pendingAction)
    }


    @Test
    fun `earth summon requires Lua emblem mapping`() {
        val summonId = 10016
        val target = fighter(2, 0, 0)
        val valid = fighter(1, 2, 0, intArrayOf(summonId), elementValue=1, specialEquipId=23086)
        val validBattle = fixture(valid, target, mapOf(summonId to skill(1)))
        val action = BattleAction(BattleAction.KIND_SKILL, summonId, target.col, target.row)
        assertTrue(validBattle.setPlayerAction(valid.roleId, valid.col, valid.row, action))

        val invalid = fighter(3, 2, 0, intArrayOf(summonId), elementValue=1, specialEquipId=23087)
        val invalidTarget = fighter(4, 0, 0)
        val invalidBattle = fixture(invalid, invalidTarget, mapOf(summonId to skill(1)))
        assertFalse(invalidBattle.setPlayerAction(invalid.roleId, invalid.col, invalid.row, action))
    }


    @Test
    fun `bow skill requires Lua bow weapon for player kinds`() {
        val bowSkill = 14033
        val target = fighter(2, 0, 0)
        val archer = fighter(1, 2, 0, intArrayOf(bowSkill), weaponId=50000, weaponKind=7)
        val battle = fixture(archer, target, mapOf(bowSkill to skill(1)))
        val action = BattleAction(BattleAction.KIND_SKILL, bowSkill, target.col, target.row)
        assertTrue(battle.setPlayerAction(archer.roleId, archer.col, archer.row, action))

        val noBow = fighter(3, 2, 0, intArrayOf(bowSkill), weaponId=50001, weaponKind=1)
        val target2 = fighter(4, 0, 0)
        val noBowBattle = fixture(noBow, target2, mapOf(bowSkill to skill(1)))
        assertFalse(noBowBattle.setPlayerAction(noBow.roleId, noBow.col, noBow.row, action))
    }

    @Test
    fun `spiritual bow ids pass SetConSkill bow exception`() {
        val bowSkill = 14033
        val actor = fighter(1, 2, 0, intArrayOf(bowSkill), weaponId=57601, weaponKind=0)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target, mapOf(bowSkill to skill(1)))
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row,
            BattleAction(BattleAction.KIND_SKILL, bowSkill, target.col, target.row)))
    }

    @Test
    fun `damaged summon emblem is rejected at Lua threshold`() {
        val summonId = 10016
        val actor = fighter(1, 2, 0, intArrayOf(summonId), elementValue=1, specialEquipId=23086, specialDamage=250)
        val target = fighter(2, 0, 0)
        val battle = fixture(actor, target, mapOf(summonId to skill(1)))
        assertFalse(battle.setPlayerAction(actor.roleId, actor.col, actor.row,
            BattleAction(BattleAction.KIND_SKILL, summonId, target.col, target.row)))
    }


    @Test
    fun `manual SetConSkill path accepts selectable fightWay six skill`() {
        val manualId = 16001
        val actor = fighter(1, 2, 0, intArrayOf(manualId))
        val ally = fighter(2, 3, 0)
        val battle = fixture(actor, ally, mapOf(manualId to skill(6)))
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row,
            BattleAction(BattleAction.KIND_SKILL, manualId, ally.col, ally.row)))
    }


    @Test
    fun `Lua self only skills reject another target`() {
        for (selfOnlyId in listOf(13008, 14002, 20006)) {
            val actor = fighter(1, 2, 0, intArrayOf(selfOnlyId))
            val ally = fighter(2, 3, 0)
            val battle = fixture(actor, ally, mapOf(selfOnlyId to skill(6)))

            assertFalse(battle.setPlayerAction(
                actor.roleId, actor.col, actor.row,
                BattleAction(BattleAction.KIND_SKILL, selfOnlyId, ally.col, ally.row)
            ), "skill $selfOnlyId must reject a non-self target")
            assertFalse(actor.hasActed)
            assertEquals(null, actor.pendingAction)
        }
    }

    @Test
    fun `Lua self only skill accepts caster target`() {
        val selfOnlyId = 20006
        val actor = fighter(1, 2, 0, intArrayOf(selfOnlyId))
        val ally = fighter(2, 3, 0)
        val battle = fixture(actor, ally, mapOf(selfOnlyId to skill(6)))

        val action = BattleAction(BattleAction.KIND_SKILL, selfOnlyId, actor.col, actor.row)
        assertTrue(battle.setPlayerAction(actor.roleId, actor.col, actor.row, action))
        assertEquals(action, actor.pendingAction)
    }

    @Test
    fun `LifePK rejects escape command at queue time`() {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val grid = BattleGrid().also { it.place(actor); it.place(target) }
        val battle = BattleInstance(
            battleId=1, warType=0, terrain=0, eventNo=0, grid=grid,
            skillResolver=BattleSkillResolver(emptyMap()),
            itemResolver=mockk<BattleItemResolver>(relaxed=true),
            involvedNpcDefs=emptyList(),
            warStyle=tw.tsm.domain.constants.EWarStyle.LIFE_PK
        ).also { it.addFighter(actor); it.addFighter(target) }

        assertFalse(battle.setPlayerAction(
            actor.roleId, actor.col, actor.row,
            BattleAction(BattleAction.KIND_ESCAPE, BattleConstants.ESCAPE_SKILL_ID, actor.col, actor.row)
        ))
    }

    @Test
    fun `WorldBoss rejects escape command at queue time`() {
        val actor = fighter(1, 2, 0)
        val target = fighter(2, 0, 0)
        val grid = BattleGrid().also { it.place(actor); it.place(target) }
        val battle = BattleInstance(
            battleId=1, warType=0, terrain=0, eventNo=0, grid=grid,
            skillResolver=BattleSkillResolver(emptyMap()),
            itemResolver=mockk<BattleItemResolver>(relaxed=true),
            involvedNpcDefs=emptyList(),
            warStyle=tw.tsm.domain.constants.EWarStyle.WORLD_BOSS
        ).also { it.addFighter(actor); it.addFighter(target) }

        assertFalse(battle.setPlayerAction(
            actor.roleId, actor.col, actor.row,
            BattleAction(BattleAction.KIND_ESCAPE, BattleConstants.ESCAPE_SKILL_ID, actor.col, actor.row)
        ))
    }

}

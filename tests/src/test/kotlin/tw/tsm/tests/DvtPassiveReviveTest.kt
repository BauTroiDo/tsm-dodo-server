package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.app.response.BattleResponseSender
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.ExclusiveSkillDef
import tw.tsm.domain.data.ExclusiveSkillEffectDef
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.gamelogic.battle.BattleFighter
import tw.tsm.gamelogic.battle.BattleGrid
import tw.tsm.gamelogic.battle.BattleSkillResolver
import tw.tsm.gamelogic.battle.NpcPassiveCombatRules
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtPassiveReviveTest {
    private fun reviveDefs(level: Int, percent: Int) = mapOf(
        NpcPassiveCombatRules.PHOENIX_REBIRTH to mapOf(
            level to ExclusiveSkillDef(
                skillId = NpcPassiveCombatRules.PHOENIX_REBIRTH,
                level = level,
                headerRaw = List(6) { 0 },
                effects = listOf(
                    ExclusiveSkillEffectDef(4, 1, 1, 0, percent.toLong()),
                    ExclusiveSkillEffectDef(0, 0, 0, 0, 0),
                ),
            )
        )
    )

    private fun fighter(
        roleId: Long,
        col: Int,
        passiveSkillId: Int = 0,
        passiveSkillLevel: Int = 0,
        hp: Long = 10_000,
        maxHp: Long = 10_000,
        atk: Int = 500,
        def: Int = 100,
    ) = BattleFighter(
        roleId = roleId,
        kind = EHuman.MINE_NPC,
        name = "fighter-$roleId",
        col = col,
        row = 0,
        maxHp = maxHp,
        maxSp = 1000,
        hp = hp,
        sp = 1000,
        level = 100,
        element = 1,
        atk = atk,
        def = def,
        agi = 200,
        intelligence = 300,
        passiveSkillId = passiveSkillId,
        passiveSkillLevel = passiveSkillLevel,
    )

    @Test
    fun phoenixRebirthRestoresConfiguredPercentOnce() {
        val target = fighter(
            roleId = -1,
            col = 0,
            passiveSkillId = NpcPassiveCombatRules.PHOENIX_REBIRTH,
            passiveSkillLevel = 3,
            hp = 0,
        ).also { it.isDead = true }

        val defs = reviveDefs(level = 3, percent = 70)
        assertEquals(7000L, NpcPassiveCombatRules.tryAutoRevive(target, defs))
        assertEquals(7000L, target.hp)
        assertFalse(target.isDead)
        assertTrue(target.passiveReviveUsed)

        target.hp = 0
        target.isDead = true
        assertEquals(0L, NpcPassiveCombatRules.tryAutoRevive(target, defs))
        assertTrue(target.isDead)
    }

    @Test
    fun phoenixRebirthDoesNotOverrideFlyOut() {
        val target = fighter(
            roleId = -2,
            col = 0,
            passiveSkillId = NpcPassiveCombatRules.PHOENIX_REBIRTH,
            passiveSkillLevel = 6,
            hp = 0,
        ).also {
            it.isDead = true
            it.isFlyOut = true
        }

        assertEquals(0L, NpcPassiveCombatRules.tryAutoRevive(target, reviveDefs(6, 100)))
        assertEquals(0L, target.hp)
        assertTrue(target.isDead)
    }

    @Test
    fun resolverEmitsReviveAfterLethalHitAndConsumesPassive() {
        val attacker = fighter(roleId = 1, col = 2, atk = 1200, def = 100)
        val target = fighter(
            roleId = -3,
            col = 0,
            passiveSkillId = NpcPassiveCombatRules.PHOENIX_REBIRTH,
            passiveSkillLevel = 1,
            hp = 1,
            maxHp = 10_000,
            def = 0,
        )
        val grid = BattleGrid().also {
            it.place(attacker)
            it.place(target)
        }
        val resolver = BattleSkillResolver(
            skillDefs = emptyMap(),
            exclusiveSkillDefs = reviveDefs(level = 1, percent = 50),
        )

        val first = resolver.resolveSkill(
            attacker = attacker,
            grid = grid,
            skillId = BattleConstants.HAND_FIGHT_SKILL_ID,
            targetCol = target.col,
            targetRow = target.row,
            terrain = 101,
        )
        assertEquals(5000L, first.targets.single().passiveReviveHp)
        assertEquals(5000L, target.hp)
        assertFalse(target.isDead)

        target.hp = 1
        target.isDead = false
        val second = resolver.resolveSkill(
            attacker = attacker,
            grid = grid,
            skillId = BattleConstants.HAND_FIGHT_SKILL_ID,
            targetCol = target.col,
            targetRow = target.row,
            terrain = 101,
        )
        assertEquals(0L, second.targets.single().passiveReviveHp)
        assertTrue(target.isDead)
    }

    @Test
    fun battleReviveWireMatchesLuaS051001() {
        var packet = byteArrayOf()
        val capture = object : SessionSender {
            override fun send(data: ByteArray) { packet = data }
            override fun close() {}
        }

        BattleResponseSender().sendBattleRevive(capture, col = 1, row = 2, hp = 7000)

        val reader = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(51, reader.get().toInt() and 0xFF)
        assertEquals(1, reader.get().toInt() and 0xFF)
        assertEquals(0, reader.get().toInt() and 0xFF)
        assertEquals(1, reader.get().toInt() and 0xFF)
        assertEquals(1, reader.get().toInt() and 0xFF)
        assertEquals(2, reader.get().toInt() and 0xFF)
        assertEquals(EAttribute.HP, reader.get().toInt() and 0xFF)
        assertEquals(7000, reader.int)
        assertFalse(reader.hasRemaining())
    }
}

package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.tsm.domain.constants.EHuman
import tw.tsm.protocol.BattleRoleData
import tw.tsm.protocol.BattleRoleSerializer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DvtBattleRoleWireTest {
    @Test
    fun `every RoleAppear record ends with soulEquipId word`() {
        val player = BattleRoleData(
            warType=1, humanKind=EHuman.PLAYER, roleId=42, npcId=0, masterId=0,
            col=2, row=2, maxHp=100, maxSp=50, hp=100, sp=50, level=1,
            element=1, name="P"
        )
        val enemy = BattleRoleData(
            warType=1, humanKind=EHuman.MINE_NPC, roleId=1234, npcId=1234, masterId=0,
            col=0, row=2, maxHp=100, maxSp=50, hp=100, sp=50, level=1,
            element=1, name="N"
        )
        val p = BattleRoleSerializer.serialize(player)
        val n = BattleRoleSerializer.serialize(enemy)
        val all = BattleRoleSerializer.serializeAll(listOf(player, enemy))

        // DVT FightField.RoleAppear unconditionally ReadUInt16() after each role.
        assertEquals(0, ByteBuffer.wrap(p.takeLast(2).toByteArray()).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        assertEquals(0, ByteBuffer.wrap(n.takeLast(2).toByteArray()).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
        assertEquals(p.size + n.size, all.size)
        // The next record must begin immediately after the player's trailing soulEquipId.
        assertEquals(1, all[p.size].toInt() and 0xff)
        assertEquals(EHuman.MINE_NPC, all[p.size + 1].toInt() and 0xff)
    }
}

package tw.tsm.app.handlers

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.SoulEquipResult
import tw.tsm.gamelogic.systems.SoulEquipTargetValidator
import tw.tsm.gamelogic.systems.SoulSystem
import tw.tsm.protocol.PacketReader

/** C:120-001 +kind(U8) +npcId(U16) +followIndex(U8) +sn(I64). */
class SoulHandler(
    private val soulSystem: SoulSystem,
    private val petSystem: PetSystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val responseSender: ResponseSender,
) : PacketHandler {
    override val mainKind: Int = Opcodes.SOUL

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || subKind != 1 || data.size != 12) return
        if (session.battleId != 0L) {
            responseSender.sendSoulError(session, SoulSystem.ERR_IN_BATTLE)
            return
        }

        val reader = PacketReader(data)
        val kind = reader.readUByte()
        val npcId = reader.readUShortLE()
        val followIndex = reader.readUByte()
        val sn = reader.readULongLE()

        if (kind != 1 && kind != 2) {
            responseSender.sendSoulError(session, SoulSystem.ERR_EQUIP_TAKEOFF)
            return
        }

        val pet = petSystem.getFollowNpc(session.roleId.toLong(), followIndex)
        val npcElement = npcDefs[npcId]?.element
        SoulEquipTargetValidator.validate(pet, npcId, npcElement)?.let {
            responseSender.sendSoulError(session, it)
            return
        }

        val result = if (kind == 1) {
            soulSystem.equip(session.roleId.toLong(), npcId, sn, npcElement!!)
        } else {
            soulSystem.takeOff(session.roleId.toLong(), npcId, sn)
        }

        when (result) {
            is SoulEquipResult.Ok -> responseSender.sendSoulEquipResult(session, kind, npcId, sn)
            is SoulEquipResult.Fail -> responseSender.sendSoulError(session, result.errorCode)
        }
    }
}

package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.InventoryResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.MiningSystem
import tw.tsm.gamelogic.systems.MiningView
import tw.tsm.gamelogic.systems.PartySystem
import tw.tsm.gamelogic.systems.SpecialSceneTeleportService
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val miningHandlerLogger = KotlinLogging.logger {}

/** DVT protocolTable[170] — Mỏ Thôn Thực. */
class MiningHandler(
    private val system: MiningSystem,
    private val specialTeleport: SpecialSceneTeleportService,
    private val partySystem: PartySystem,
    private val worldManager: WorldManager,
) : PacketHandler {
    override val mainKind: Int = Opcodes.MINING
    private val inventoryResponse = InventoryResponseSender()

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> queryInfo(session, data)
            2 -> enterMine(session, data)
            10 -> refill(session, data)
            11 -> setMiningState(session, data)
            else -> miningHandlerLogger.warn { "Sub-protocol 170 chưa xử lý: " + subKind }
        }
    }

    private suspend fun queryInfo(session: GameSession, data: ByteArray) {
        if (data.size != 1) return
        val requestedMineType = PacketReader(data).readUByte()
        // DVT Function.Use(EFunction.Mine) sends C170-001 with type=0 on
        // the initial icon click. UIMine's dropdown then sends type=1.
        // Only the initial INFO query treats 0 as the first/only mine;
        // entrance/refill/state requests keep their original strict gates.
        val mineType = if (requestedMineType == 0) 1 else requestedMineType
        if (mineType != 1) {
            sendInfoDisabled(session)
            return
        }
        sendInfo(session, system.getView(session.roleId.toLong()))
    }

    private suspend fun enterMine(session: GameSession, data: ByteArray) {
        if (data.size != 1) return
        val mineType = PacketReader(data).readUByte()
        if (mineType != 1 || session.battleId != 0L ||
            session.mapId == FORBIDDEN_TELEPORT_MAP || partySystem.isInParty(session.playerId)
        ) return

        if (!specialTeleport.enterSpecialScene(session, MiningSystem.MINE_SCENE_ID)) {
            sendEnterResult(session, 0, mineType, system.getView(session.roleId.toLong()))
            return
        }

        val view = system.getView(session.roleId.toLong())
        sendEnterResult(session, 1, mineType, view)
    }

    private suspend fun refill(session: GameSession, data: ByteArray) {
        if (data.size != 7) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()
        val itemId = reader.readUShortLE()
        val quantity = reader.readUShortLE()
        val wireIndex = reader.readUByte()

        val result = system.refill(
            session.roleId.toLong(), bagIndex, itemId, quantity, wireIndex
        )
        if (!result.success) {
            sendRefillResult(session, 0, wireIndex, 0)
            return
        }

        inventoryResponse.sendReduceBagItem(session, bagIndex, quantity)
        sendRefillResult(session, 1, result.wireIndex, result.addedFull)
    }

    private fun setMiningState(session: GameSession, data: ByteArray) {
        if (data.size != 1) return
        val mineType = PacketReader(data).readUByte()
        sendSimpleResult(session, 11, if (mineType == 1) 1 else 255)
    }

    private fun sendInfo(session: GameSession, view: MiningView) {
        val full = (view.record.machineFull + listOf(0, 0, 0, 0)).take(4)
        val writer = PacketWriter(38)
            .writeByte(Opcodes.MINING).writeByte(1).writeByte(0)
            .writeByte(1).writeByte(view.record.mineType)
            .writeLongLE(view.remainingOre)
            .writeDoubleLE(view.setTimeOaDate)
        for (i in 3 downTo 0) {
            writer.writeShortLE(full[i]).writeShortLE(MiningSystem.MACHINE_SKIN_IDS[i])
        }
        writer.writeByte(if (view.record.dayLimit) 1 else 0)
        session.send(writer.toByteArray())
    }

    private fun sendInfoDisabled(session: GameSession) {
        session.send(
            PacketWriter(4).writeByte(Opcodes.MINING).writeByte(1).writeByte(0)
                .writeByte(255).toByteArray()
        )
    }

    private fun sendEnterResult(
        session: GameSession, result: Int, mineType: Int, view: MiningView
    ) {
        val people = worldManager.getPlayersInMap(MiningSystem.MINE_SCENE_ID).size
        session.send(
            PacketWriter(18).writeByte(Opcodes.MINING).writeByte(2).writeByte(0)
                .writeByte(result).writeByte(mineType).writeLongLE(view.remainingOre)
                .writeIntLE(people).writeByte(if (view.isMining) 1 else 0)
                .toByteArray()
        )
    }

    private fun sendRefillResult(
        session: GameSession, result: Int, wireIndex: Int, addedFull: Int
    ) {
        session.send(
            PacketWriter(7).writeByte(Opcodes.MINING).writeByte(10).writeByte(0)
                .writeByte(result).writeByte(wireIndex).writeShortLE(addedFull)
                .toByteArray()
        )
    }

    private fun sendSimpleResult(session: GameSession, subKind: Int, result: Int) {
        session.send(
            PacketWriter(4).writeByte(Opcodes.MINING).writeByte(subKind).writeByte(0)
                .writeByte(result).toByteArray()
        )
    }

    companion object {
        private const val FORBIDDEN_TELEPORT_MAP = 10701
    }
}

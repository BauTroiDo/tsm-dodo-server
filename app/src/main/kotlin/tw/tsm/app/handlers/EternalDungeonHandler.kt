package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.EternalDungeonLayerDef
import tw.tsm.domain.data.EternalDungeonPrizeDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val eternalDungeonLogger = KotlinLogging.logger {}

/**
 * DVT protocolTable[48] — Thần Binh Bảo Khố / Eternal Dungeon.
 *
 * A2 scope:
 * - C48-2 request layer info -> S48-4 fastest-record payload (empty records offline).
 * - packet helpers for S48-1/2/3/4 are byte-locked by tests.
 *
 * C48-1 battle is intentionally not faked here until the authoritative monster
 * generation table is wired.
 */
class EternalDungeonHandler(
    private val roleCountSystem: RoleCountSystem,
    private val layerDefs: Map<Int, EternalDungeonLayerDef>,
    private val prizeDefs: Map<Int, EternalDungeonPrizeDef>,
) : PacketHandler {
    override val mainKind: Int = Opcodes.ETERNAL_DUNGEON

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> {
                if (data.size != 4) return
                val requestedLayer = PacketReader(data).readIntLE()
                eternalDungeonLogger.warn {
                    "C48-001 layer=$requestedLayer battle backend chưa nối monster generation authoritative"
                }
            }
            2 -> requestLayerInfo(session, data)
            3 -> eternalDungeonLogger.debug { "C48-003 reset chưa có sender trong UI DVT hiện tại" }
            else -> eternalDungeonLogger.warn { "EternalDungeon chưa hỗ trợ subKind=$subKind (${data.size} bytes)" }
        }
    }

    private fun requestLayerInfo(session: GameSession, data: ByteArray) {
        if (data.size != 4) return
        val requested = PacketReader(data).readIntLE()
        val layer = requested.coerceIn(MIN_LAYER, MAX_LAYER)
        session.send(fastestRecordPacket(layer))
        eternalDungeonLogger.debug {
            "C48-002 info roleId=${session.roleId} cleared=" +
                roleCountSystem.getValue(session.roleId.toLong(), RoleCountSystem.ETERNAL_DUNGEON) +
                " requested=$requested layer=$layer"
        }
    }

    fun currentLayer(roleId: Long): Int =
        (roleCountSystem.getValue(roleId, RoleCountSystem.ETERNAL_DUNGEON) + 1)
            .coerceIn(MIN_LAYER, MAX_LAYER)

    fun layerDefFor(layer: Int): EternalDungeonLayerDef? {
        val normalized = layer.coerceIn(MIN_LAYER, MAX_LAYER)
        return layerDefs.entries
            .filter { it.key <= normalized }
            .maxByOrNull { it.key }
            ?.value
    }

    fun prizeDefFor(layer: Int): EternalDungeonPrizeDef? =
        prizeDefs[layer.coerceIn(MIN_LAYER, MAX_LAYER)]

    companion object {
        const val MIN_LAYER = 1
        const val MAX_LAYER = 30

        fun startResultPacket(result: Int): ByteArray =
            PacketWriter(4)
                .writeByte(Opcodes.ETERNAL_DUNGEON).writeByte(1).writeByte(0)
                .writeByte(result)
                .toByteArray()

        fun fightResultPacket(layer: Int, result: Int): ByteArray =
            PacketWriter(8)
                .writeByte(Opcodes.ETERNAL_DUNGEON).writeByte(2).writeByte(0)
                .writeIntLE(layer).writeByte(result)
                .toByteArray()

        fun registrationPacket(): ByteArray =
            PacketWriter(3)
                .writeByte(Opcodes.ETERNAL_DUNGEON).writeByte(3).writeByte(0)
                .toByteArray()

        /** Lua protocolTable[48][4]: layer Int32 + count Int32 + records. */
        fun fastestRecordPacket(layer: Int): ByteArray =
            PacketWriter(11)
                .writeByte(Opcodes.ETERNAL_DUNGEON).writeByte(4).writeByte(0)
                .writeIntLE(layer)
                .writeIntLE(0)
                .toByteArray()
    }
}

package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.DungeonRoomMember
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonRoomState
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val dungeonHandlerLogger = KotlinLogging.logger {}

/**
 * DVT protocolTable[47] — Phó bản.
 *
 * F1: single-player entry.
 * F2-A: normal multiplayer (kind=1) room lifecycle. Start/instance isolation,
 * weekly/soul criteria, reward/reset and public recruit remain separate milestones.
 */
class DungeonHandler(
    private val system: DungeonSystem,
    private val rooms: DungeonRoomRegistry = DungeonRoomRegistry(),
) : PacketHandler {
    override val mainKind: Int = Opcodes.DUNGEON

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> sendRoomList(session)
            2 -> createDungeon(session, data)
            3 -> joinRoom(session, data)
            7 -> setRoomName(session, data)
            10 -> leaveRoom(session)
            11 -> togglePrepare(session)
            17 -> kickPlayer(session, data)
            else -> dungeonHandlerLogger.debug {
                "Dungeon C:047-${subKind.toString().padStart(3, '0')} chưa thuộc F2-A; bỏ qua an toàn"
            }
        }
    }

    private fun sendRoomList(session: GameSession) {
        val activeRooms = rooms.snapshotRooms()
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(1)
            .writeByte(0)
            .writeShortLE(activeRooms.size)

        for (room in activeRooms) {
            writer.writeUIntLE(room.id)
                .writeBig5WithByteLen(room.name)
                .writeLongLE(room.ownerId)
                .writeByte(if (room.isLocked) 1 else 0)
                .writeShortLE(room.dungeonId)
                .writeByte(room.members.size)
        }
        session.send(writer.toByteArray())
    }

    private suspend fun createDungeon(session: GameSession, data: ByteArray) {
        if (data.size !in 2..3) {
            dungeonHandlerLogger.warn { "C:047-002 sai độ dài: ${data.size}" }
            sendCreateResult(session, DungeonSystem.RESULT_NO_DUNGEON)
            return
        }

        val reader = PacketReader(data)
        val dungeonId = reader.readUShortLE()
        val usePassword = reader.remaining > 0 && reader.readUByte() != 0
        val def = system.getDef(dungeonId)

        if (def?.kind == DungeonSystem.KIND_SINGLE) {
            val result = system.validateSingleCreate(session, dungeonId)
            sendCreateResult(session, result)
            if (result == DungeonSystem.RESULT_OK && !system.enterSingle(session, dungeonId)) {
                dungeonHandlerLogger.error {
                    "Không suy được entry point cho dungeon=$dungeonId scene=${system.getDef(dungeonId)?.sceneId}"
                }
            }
            return
        }

        val result = system.validateNormalRoomEntry(
            session,
            dungeonId,
            rooms.roomOf(session.playerId) != null,
        )
        if (result != DungeonSystem.RESULT_OK) {
            sendCreateResult(session, result)
            return
        }

        val password = if (usePassword) offlinePassword(session, dungeonId) else ""
        val room = rooms.create(
            owner = session,
            dungeonId = dungeonId,
            maxPlayers = def!!.maxPlayer,
            password = password,
        )
        sendCreateResult(session, DungeonSystem.RESULT_OK, room.password)
    }

    private suspend fun joinRoom(session: GameSession, data: ByteArray) {
        if (data.size < 5) {
            sendJoinResult(session, DungeonSystem.RESULT_NO_SPACE)
            return
        }

        val reader = PacketReader(data)
        val roomId = reader.readUIntLE()
        val passwordLength = reader.readUByte()
        if (reader.remaining < passwordLength) {
            sendJoinResult(session, DungeonSystem.RESULT_NO_SPACE)
            return
        }
        val password = if (passwordLength > 0) reader.readBig5(passwordLength) else ""

        val room = rooms.room(roomId)
        if (room == null) {
            sendJoinResult(session, DungeonSystem.RESULT_NO_SPACE)
            return
        }

        val result = system.validateNormalRoomEntry(
            session,
            room.dungeonId,
            rooms.roomOf(session.playerId) != null,
        )
        if (result != DungeonSystem.RESULT_OK) {
            sendJoinResult(session, result)
            return
        }
        if (room.members.size >= room.maxPlayers) {
            sendJoinResult(session, RESULT_ROOM_FULL)
            return
        }
        if (room.isLocked && room.password != password) {
            sendJoinResult(session, RESULT_PASSWORD_WRONG)
            return
        }

        val existing = room.members.values.toList()
        val joined = rooms.join(roomId, session)
        if (joined == null) {
            sendJoinResult(session, RESULT_ROOM_FULL)
            return
        }

        sendJoinSuccess(session, joined, existing)
        for (member in existing) {
            sendPlayerJoined(member.session, session)
        }
    }

    private fun setRoomName(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val len = reader.readUByte()
        if (len == 0 || reader.remaining < len) return
        val name = reader.readBig5(len)
        val room = rooms.rename(session.playerId, name) ?: return

        for (member in room.members.values) {
            member.session.send(
                PacketWriter()
                    .writeByte(Opcodes.DUNGEON)
                    .writeByte(7)
                    .writeByte(0)
                    .writeBig5WithByteLen(room.name)
                    .toByteArray()
            )
        }
    }

    private fun leaveRoom(session: GameSession) {
        val left = rooms.leave(session.playerId) ?: return
        val recipients = ArrayList<GameSession>(left.room.members.size + 1)
        recipients += left.removed.session
        recipients += left.room.members.values.map { it.session }

        for (recipient in recipients.distinctBy { it.playerId }) {
            sendPlayerLeave(recipient, session.playerId, LEAVE_SELF)
        }

        val newOwnerId = left.newOwnerId
        if (newOwnerId != null) {
            for (member in left.room.members.values) {
                sendOwnerChanged(member.session, newOwnerId)
            }
        }
    }

    private fun togglePrepare(session: GameSession) {
        val changed = rooms.togglePrepared(session.playerId) ?: return
        val (room, prepared) = changed
        for (member in room.members.values) {
            sendPrepared(member.session, session.playerId, prepared)
        }
    }

    private fun kickPlayer(session: GameSession, data: ByteArray) {
        if (data.size != 8) {
            sendKickResult(session, 1)
            return
        }

        val targetId = PacketReader(data).readULongLE()
        val kicked = rooms.kick(session.playerId, targetId)
        if (kicked == null) {
            sendKickResult(session, 1)
            return
        }

        sendKickResult(session, 0)
        val recipients = ArrayList<GameSession>(kicked.room.members.size + 1)
        recipients += kicked.removed.session
        recipients += kicked.room.members.values.map { it.session }
        for (recipient in recipients.distinctBy { it.playerId }) {
            sendPlayerLeave(recipient, targetId, LEAVE_KICKED)
        }
    }

    private fun sendCreateResult(session: GameSession, result: Int, password: String? = null) {
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(2)
            .writeByte(0)
            .writeByte(result)

        if (result == DungeonSystem.RESULT_OK && password != null) {
            writer.writeBig5WithByteLen(password)
        }
        session.send(writer.toByteArray())
    }

    private fun sendJoinResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(3)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    private fun sendJoinSuccess(
        session: GameSession,
        room: DungeonRoomState,
        existing: List<DungeonRoomMember>,
    ) {
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(3)
            .writeByte(0)
            .writeByte(DungeonSystem.RESULT_OK)
            .writeUIntLE(room.id)
            .writeByte(existing.size)

        for (member in existing) {
            writeDungeonPlayer(writer, member, includePrepare = true)
        }

        writer.writeShortLE(room.dungeonId)
        session.send(writer.toByteArray())
    }

    private fun sendPlayerJoined(target: GameSession, joined: GameSession) {
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(13)
            .writeByte(0)
        writeDungeonPlayer(writer, DungeonRoomMember(joined), includePrepare = false)
        target.send(writer.toByteArray())
    }

    private fun writeDungeonPlayer(
        writer: PacketWriter,
        member: DungeonRoomMember,
        includePrepare: Boolean,
    ) {
        val role = member.session
        writer.writeLongLE(role.playerId)
            .writeBig5WithByteLen(role.characterName)
            .writeByte(role.level)
            .writeByte(role.sex)
            .writeByte(role.hair)
            .writeIntLE(role.color1)
            .writeIntLE(role.color2)

        for (i in 0 until DUNGEON_EQUIP_STYLE_SLOTS) {
            writer.writeShortLE(role.equipItemIds.getOrElse(i) { 0 })
        }

        writer.writeByte(role.turn)
            .writeShortLE(role.titleId)

        if (includePrepare) {
            writer.writeByte(if (member.prepared) 1 else 0)
        }
    }

    private fun sendPlayerLeave(target: GameSession, playerId: Long, reason: Int) {
        target.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(10)
                .writeByte(0)
                .writeLongLE(playerId)
                .writeByte(reason)
                .toByteArray()
        )
    }

    private fun sendPrepared(target: GameSession, playerId: Long, prepared: Boolean) {
        target.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(11)
                .writeByte(0)
                .writeLongLE(playerId)
                .writeByte(if (prepared) 1 else 0)
                .toByteArray()
        )
    }

    private fun sendOwnerChanged(target: GameSession, ownerId: Long) {
        target.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(14)
                .writeByte(0)
                .writeLongLE(ownerId)
                .toByteArray()
        )
    }

    private fun sendKickResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(17)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /**
     * DVT Lua proves only that the server returns a password string; the generation
     * algorithm is not present client-side. Offline fallback: stable four digits.
     */
    private fun offlinePassword(session: GameSession, dungeonId: Int): String {
        val raw = ((session.playerId xor dungeonId.toLong()) and 0xFFFF).toInt() % 10_000
        return raw.toString().padStart(4, '0')
    }

    companion object {
        private const val RESULT_ROOM_FULL = 6
        private const val RESULT_PASSWORD_WRONG = 9
        private const val LEAVE_SELF = 0
        private const val LEAVE_KICKED = 1
        private const val DUNGEON_EQUIP_STYLE_SLOTS = 11
    }
}

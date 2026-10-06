package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.util.OleDate
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.gamelogic.systems.DungeonRoomMember
import tw.tsm.gamelogic.systems.DungeonRoomRegistry
import tw.tsm.gamelogic.systems.DungeonRoomState
import tw.tsm.gamelogic.systems.DungeonSystem
import tw.tsm.gamelogic.systems.DungeonSettlement
import tw.tsm.gamelogic.systems.DvtWeeklyDungeonFormation
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.gamelogic.systems.PvpBotMatchmakingSystem
import tw.tsm.gamelogic.systems.PvpBotMode
import tw.tsm.gamelogic.systems.PvpLobbyClaim
import tw.tsm.gamelogic.systems.PvpLobbyPolicy
import tw.tsm.gamelogic.systems.TeamDungeonBotPolicy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    private val worldManager: WorldManager? = null,
    private val battleManager: BattleManager? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val countdownMs: Long = 5_000L,
    private val pvpBotMatchmakingSystem: PvpBotMatchmakingSystem? = null,
) : PacketHandler {
    private data class LobbyBot(
        val id: Long,
        val name: String,
        val template: GameSession,
        val hairOffset: Int = 0,
        val color1Xor: Int = 0,
        val color2Xor: Int = 0,
        val copyEquipment: Boolean = true,
        val outfitItemIds: IntArray = intArrayOf(),
    )

    private val pvpRoomModes = ConcurrentHashMap<Long, PvpBotMode>()
    private val pvpRoomBots = ConcurrentHashMap<Long, List<LobbyBot>>()
    private val teamRoomBots = ConcurrentHashMap<Long, List<LobbyBot>>()
    private val nextLobbyBotId = AtomicLong(-2_000_000_000L)
    override val mainKind: Int = Opcodes.DUNGEON

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        system.refreshPeriodicProgress(session)

        when (subKind) {
            1 -> sendRoomList(session)
            2 -> createDungeon(session, data)
            3 -> joinRoom(session, data)
            7 -> setRoomName(session, data)
            8 -> invitePlayer(session, data)
            9 -> recruitRoom(session, data)
            10 -> leaveRoom(session)
            11 -> togglePrepare(session)
            12 -> startMultiplayer(session)
            13 -> quickJoin(session, data)
            17 -> kickPlayer(session, data)
            22 -> claimWeeklyAllPass(session, data)
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
            if (result == DungeonSystem.RESULT_OK) {
                val startTime = OleDate.nowLocal()
                // Capture return point before teleport; C:013-004 uses this run state.
                system.markSingleStarted(session, dungeonId, startTime)
                if (!system.enterSingle(session, dungeonId)) {
                    system.clearActiveRun(session)
                    dungeonHandlerLogger.error {
                        "Không suy được entry point cho dungeon=$dungeonId scene=${system.getDef(dungeonId)?.sceneId}"
                    }
                }
            }
            return
        }

        val alreadyInRoom = rooms.roomOf(session.playerId) != null
        val result = system.validateNormalRoomEntry(
            session,
            dungeonId,
            alreadyInRoom,
        )
        dungeonHandlerLogger.info {
            "C47-002 create player=" + session.characterName +
                " dungeonId=" + dungeonId +
                " kind=" + (def?.kind ?: -1) +
                " maxPlayer=" + (def?.maxPlayer ?: -1) +
                " weeklyFormation=" + (DvtWeeklyDungeonFormation.enemyIds(dungeonId) != null) +
                " alreadyInRoom=" + alreadyInRoom +
                " result=" + result
        }
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

        val pvpClaim = pvpBotMatchmakingSystem?.claimForLobby(session.playerId)
        if (pvpClaim != null) {
            pvpRoomModes[room.id] = pvpClaim.mode
            schedulePvpLobbyFill(room.id, pvpClaim)
            dungeonHandlerLogger.info {
                "PvP lobby claimed: room=${room.id} player=${session.characterName} mode=${pvpClaim.mode}"
            }
        } else if (def.kind == DungeonSystem.KIND_NORMAL_MULTIPLAYER && def.maxPlayer > 1) {
            scheduleTeamDungeonLobbyFill(room.id)
        }
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
        if (room.isLocked && room.password != password && !rooms.isInvited(roomId, session.playerId)) {
            sendJoinResult(session, RESULT_PASSWORD_WRONG)
            return
        }

        // PvP replaces one placeholder at a time. Normal team-dungeon bots are an
        // offline-only fallback: the first additional real player removes all of them
        // and the room immediately returns to ordinary multiplayer semantics.
        if (pvpRoomModes.containsKey(roomId)) {
            removeOneLobbyBot(roomId)
        } else if (teamRoomBots.containsKey(roomId)) {
            clearTeamDungeonLobby(roomId)
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
        pvpRoomBots[roomId].orEmpty().forEach { bot ->
            sendLobbyBotJoined(session, bot)
            sendPrepared(session, bot.id, true)
        }
        if (pvpRoomModes.containsKey(roomId)) {
            scope.launch { fillPvpLobbyBots(roomId) }
        }
    }

    private fun schedulePvpLobbyFill(roomId: Long, claim: PvpLobbyClaim) {
        val elapsed = (System.currentTimeMillis() - claim.registeredAt).coerceAtLeast(0L)
        val waitMs = (PvpBotMatchmakingSystem.BOT_FILL_DELAY_MS - elapsed).coerceAtLeast(0L)
        scope.launch {
            if (waitMs > 0) delay(waitMs)
            fillPvpLobbyBots(roomId)
        }
    }

    private fun fillPvpLobbyBots(roomId: Long) {
        val room = rooms.room(roomId)
        val mode = pvpRoomModes[roomId]
        if (room == null || mode == null || room.starting || room.started) {
            pvpRoomBots.remove(roomId)
            if (room == null) pvpRoomModes.remove(roomId)
            return
        }

        val desired = PvpLobbyPolicy.visibleBotCount(mode, room.members.size, room.maxPlayers)
        var current = pvpRoomBots[roomId].orEmpty()

        while (current.size > desired) {
            val removed = current.last()
            current = current.dropLast(1)
            room.members.values.forEach { member ->
                sendPlayerLeave(member.session, removed.id, LEAVE_DISCONNECT)
            }
        }

        if (current.size < desired) {
            val template = room.members[room.ownerId]?.session
                ?: room.members.values.firstOrNull()?.session
                ?: return
            val added = buildList {
                repeat(desired - current.size) { index ->
                    add(
                        LobbyBot(
                            id = nextLobbyBotId.getAndDecrement(),
                            name = "AI Fighter ${current.size + index + 1}",
                            template = template,
                        )
                    )
                }
            }
            current = current + added
            pvpRoomBots[roomId] = current

            for (bot in added) {
                for (member in room.members.values) {
                    sendLobbyBotJoined(member.session, bot)
                    sendPrepared(member.session, bot.id, true)
                }
            }
            dungeonHandlerLogger.info {
                "PvP lobby bots filled: room=$roomId mode=$mode real=${room.members.size} bots=${current.size}"
            }
        } else {
            pvpRoomBots[roomId] = current
        }
    }

    private fun removeOneLobbyBot(roomId: Long) {
        val current = pvpRoomBots[roomId].orEmpty()
        if (current.isEmpty()) return
        val room = rooms.room(roomId)
        val removed = current.last()
        val remain = current.dropLast(1)
        if (remain.isEmpty()) pvpRoomBots.remove(roomId) else pvpRoomBots[roomId] = remain
        room?.members?.values?.forEach { member ->
            sendPlayerLeave(member.session, removed.id, LEAVE_DISCONNECT)
        }
    }

    private fun clearPvpLobby(roomId: Long, notifyMembers: Boolean = true) {
        val room = rooms.room(roomId)
        val bots = pvpRoomBots.remove(roomId).orEmpty()
        pvpRoomModes.remove(roomId)
        if (notifyMembers && room != null) {
            for (bot in bots) {
                room.members.values.forEach { member ->
                    sendPlayerLeave(member.session, bot.id, LEAVE_DISCONNECT)
                }
            }
        }
    }

    private fun scheduleTeamDungeonLobbyFill(roomId: Long) {
        scope.launch {
            delay(TeamDungeonBotPolicy.BOT_FILL_DELAY_MS)
            fillTeamDungeonLobbyBots(roomId)
        }
    }

    private fun fillTeamDungeonLobbyBots(roomId: Long) {
        val room = rooms.room(roomId) ?: run {
            teamRoomBots.remove(roomId)
            return
        }
        if (room.starting || room.started || pvpRoomModes.containsKey(roomId)) {
            clearTeamDungeonLobby(roomId)
            return
        }

        val def = system.getDef(room.dungeonId) ?: run {
            clearTeamDungeonLobby(roomId)
            return
        }
        val desired = TeamDungeonBotPolicy.visibleBotCount(
            kind = def.kind,
            realCount = room.members.size,
            roomMaxPlayers = room.maxPlayers,
        )
        var current = teamRoomBots[roomId].orEmpty()

        while (current.size > desired) {
            val removed = current.last()
            current = current.dropLast(1)
            room.members.values.forEach { member ->
                sendPlayerLeave(member.session, removed.id, LEAVE_DISCONNECT)
            }
        }

        if (current.size < desired) {
            val template = room.members[room.ownerId]?.session
                ?: room.members.values.firstOrNull()?.session
                ?: return
            val added = buildList {
                repeat(desired - current.size) { index ->
                    val profile = TeamDungeonBotPolicy.profile(current.size + index)
                    add(
                        LobbyBot(
                            id = nextLobbyBotId.getAndDecrement(),
                            name = profile.name,
                            template = template,
                            hairOffset = profile.hairOffset,
                            color1Xor = profile.color1Xor,
                            color2Xor = profile.color2Xor,
                            copyEquipment = profile.outfitItemIds.isEmpty(),
                            outfitItemIds = profile.outfitItemIds,
                        )
                    )
                }
            }
            current = current + added
            teamRoomBots[roomId] = current
            for (bot in added) {
                room.members.values.forEach { member ->
                    sendLobbyBotJoined(member.session, bot)
                    sendPrepared(member.session, bot.id, true)
                }
            }
            dungeonHandlerLogger.info {
                "Team dungeon lobby bots filled: room=$roomId dungeon=${room.dungeonId} " +
                    "real=${room.members.size} bots=${current.size}"
            }
        } else if (current.isEmpty()) {
            teamRoomBots.remove(roomId)
        } else {
            teamRoomBots[roomId] = current
        }
    }

    private fun clearTeamDungeonLobby(roomId: Long, notifyMembers: Boolean = true) {
        val room = rooms.room(roomId)
        val bots = teamRoomBots.remove(roomId).orEmpty()
        if (notifyMembers && room != null) {
            for (bot in bots) {
                room.members.values.forEach { member ->
                    sendPlayerLeave(member.session, bot.id, LEAVE_DISCONNECT)
                }
            }
        }
    }

    private suspend fun invitePlayer(session: GameSession, data: ByteArray) {
        if (data.size != 8) {
            sendInviteResult(session, 1)
            return
        }

        val targetId = PacketReader(data).readULongLE()
        val room = rooms.roomOf(session.playerId)
        val target = worldManager?.getPlayerById(targetId)
        if (room == null || room.ownerId != session.playerId || target == null) {
            sendInviteResult(session, 1)
            return
        }

        val result = system.validateNormalRoomEntry(
            target,
            room.dungeonId,
            rooms.roomOf(target.playerId) != null,
        )
        if (result != DungeonSystem.RESULT_OK || rooms.invite(session.playerId, targetId) == null) {
            sendInviteResult(session, 1)
            return
        }

        sendInviteResult(session, 0)
        sendDungeonInvitation(target, room, session)
    }

    private fun recruitRoom(session: GameSession, data: ByteArray) {
        if (data.size != 1) {
            sendRecruitResult(session, 1)
            return
        }

        val channel = PacketReader(data).readUByte()
        val room = rooms.roomOf(session.playerId)
        val world = worldManager
        if (room == null || room.ownerId != session.playerId || world == null) {
            sendRecruitResult(session, 1)
            return
        }

        // Current offline runtime has no organization-membership routing contract.
        // World channel 2 is fully evidenced by DVT UI and packet receiver.
        if (channel != CHANNEL_WORLD) {
            sendRecruitResult(session, 1)
            return
        }

        sendRecruitResult(session, 0)
        val packet = buildRecruitPacket(room, session, channel)
        for (target in world.getOnlineSessions()) {
            target.send(packet)
        }
    }

    private suspend fun quickJoin(session: GameSession, data: ByteArray) {
        if (data.size != 2) {
            sendJoinResult(session, DungeonSystem.RESULT_NO_SPACE)
            return
        }

        val dungeonId = PacketReader(data).readUShortLE()
        val room = rooms.snapshotRooms()
            .asSequence()
            .filter { it.dungeonId == dungeonId && !it.isLocked && it.members.size < it.maxPlayers }
            .minByOrNull { it.id }

        if (room == null) {
            sendJoinResult(session, DungeonSystem.RESULT_NO_SPACE)
            return
        }

        val result = system.validateNormalRoomEntry(
            session,
            dungeonId,
            rooms.roomOf(session.playerId) != null,
        )
        if (result != DungeonSystem.RESULT_OK) {
            sendJoinResult(session, result)
            return
        }

        if (pvpRoomModes.containsKey(room.id)) {
            removeOneLobbyBot(room.id)
        }

        val existing = room.members.values.toList()
        val joined = rooms.join(room.id, session)
        if (joined == null) {
            sendJoinResult(session, RESULT_ROOM_FULL)
            return
        }

        sendJoinSuccess(session, joined, existing)
        for (member in existing) {
            sendPlayerJoined(member.session, session)
        }
        pvpRoomBots[room.id].orEmpty().forEach { bot ->
            sendLobbyBotJoined(session, bot)
            sendPrepared(session, bot.id, true)
        }
        if (pvpRoomModes.containsKey(room.id)) {
            scope.launch { fillPvpLobbyBots(room.id) }
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
        battleManager?.clearDungeonBotAssist(session.playerId)
        val roomBefore = rooms.roomOf(session.playerId)
        val left = rooms.leave(session.playerId)
        if (left == null) {
            // A server restart clears the in-memory room registry while the client can
            // still keep UIDungeon open. C47-010 must remain idempotent: acknowledge
            // the requester even when the room no longer exists so the client can
            // close the stale room UI instead of becoming permanently trapped.
            sendPlayerLeave(session, session.playerId, LEAVE_SELF)
            dungeonHandlerLogger.info {
                "C47-010 leave acknowledged for stale room player=" + session.characterName +
                    " playerId=" + session.playerId
            }
            return
        }

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

        roomBefore?.id?.let { roomId ->
            if (left.disbanded || newOwnerId != null) {
                clearPvpLobby(roomId)
                clearTeamDungeonLobby(roomId)
                if (!left.disbanded && left.room.members.size == 1) {
                    scheduleTeamDungeonLobbyFill(roomId)
                }
            } else if (pvpRoomModes.containsKey(roomId)) {
                scope.launch { fillPvpLobbyBots(roomId) }
            } else if (left.room.members.size == 1) {
                scheduleTeamDungeonLobbyFill(roomId)
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

    private fun startMultiplayer(session: GameSession) {
        val candidate = rooms.roomOf(session.playerId) ?: return
        if (candidate.ownerId != session.playerId) return

        pvpRoomModes[candidate.id]?.let { mode ->
            beginPvpRoomStart(session, candidate, mode)
            return
        }

        val def = system.getDef(candidate.dungeonId) ?: return
        if (def.kind in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            scope.launch {
                val failures = system.validateWeeklyRoomStart(candidate)
                if (failures.isNotEmpty()) {
                    for ((playerId, result) in failures) {
                        candidate.members[playerId]?.session?.let { sendQualificationFailed(it, result) }
                    }
                    return@launch
                }
                beginRoomStart(session, candidate)
            }
            return
        }

        beginRoomStart(session, candidate)
    }

    private fun beginPvpRoomStart(
        session: GameSession,
        candidate: DungeonRoomState,
        mode: PvpBotMode,
    ) {
        val room = rooms.beginStart(session.playerId) ?: return
        for (member in room.members.values) {
            sendPrepared(member.session, session.playerId, true)
        }

        scope.launch {
            delay(countdownMs)
            val started = rooms.commitStart(room.id, session.playerId) ?: return@launch
            val battle = battleManager
            val realSessions = started.members.values.map { it.session }
            val plan = PvpLobbyPolicy.battlePlan(mode, realSessions.size)
            if (battle == null || plan == null) {
                rooms.resetAfterBattleForPlayer(session.playerId)
                dungeonHandlerLogger.error {
                    "PvP lobby start plan failed room=${room.id} mode=$mode real=${realSessions.size}"
                }
                return@launch
            }

            val right = plan.rightIndices.map { realSessions[it] }
            val left = plan.leftIndices.map { realSessions[it] }
            val created = battle.createArenaBotBattle(
                mode = mode,
                rightReal = right,
                leftReal = left,
                rightBotCount = plan.rightBots,
                leftBotCount = plan.leftBots,
            )
            if (!created) {
                rooms.resetAfterBattleForPlayer(session.playerId)
                realSessions.forEach {
                    sendPrepared(it, session.playerId, false)
                }
                dungeonHandlerLogger.error {
                    "PvP lobby battle create failed room=${room.id} mode=$mode"
                }
                return@launch
            }

            // Battle init is already sent. Clear only the staging-room state so
            // reopening UIDungeon after battle cannot resurrect a ghost lobby.
            rooms.completeForPlayer(session.playerId)
            clearPvpLobby(room.id, notifyMembers = false)
            delay(100)
            realSessions.forEach {
                sendPlayerLeave(it, it.playerId, LEAVE_SELF)
            }
        }
    }

    private fun beginRoomStart(session: GameSession, candidate: DungeonRoomState) {
        val room = rooms.beginStart(session.playerId) ?: return

        // Lua UIDungeon.RecivePlayerPrePare starts a fixed five-second countdown
        // when the packet roleId is the room owner.
        for (member in room.members.values) {
            sendPrepared(member.session, session.playerId, true)
        }

        scope.launch {
            delay(countdownMs)
            val started = rooms.commitStart(room.id, session.playerId) ?: return@launch
            val def = system.getDef(started.dungeonId) ?: return@launch

            if (def.kind == DungeonSystem.KIND_NORMAL_MULTIPLAYER) {
                val bots = teamRoomBots[started.id].orEmpty()
                val battle = battleManager
                if (started.members.size == 1 && bots.isNotEmpty() && battle != null) {
                    val real = started.members.values.first().session
                    battle.configureDungeonBotAssist(real.playerId, started.instanceId, bots.size)
                } else {
                    started.members.values.forEach { member ->
                        battle?.clearDungeonBotAssist(member.session.playerId)
                    }
                }
                clearTeamDungeonLobby(started.id, notifyMembers = false)
            }

            val startTime = OleDate.nowLocal()
            system.markRoomStarted(started, startTime)
            for (member in started.members.values) {
                sendStartTime(member.session, startTime)
            }

            if (def.kind in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
                val formation = DvtWeeklyDungeonFormation.enemyIds(started.dungeonId)
                val battle = battleManager
                if (formation == null || battle == null) {
                    system.clearRoomRuns(started)
                    for (member in started.members.values) sendQualificationFailed(member.session, 1)
                    dungeonHandlerLogger.error {
                        "WeekDungeon thiếu formation/battle manager dungeon=" + started.dungeonId
                    }
                    return@launch
                }

                // DVT client protocolTable[47][20] closes UIDungeon only when the
                // packet dungeonId resolves to kind=3. Soul weekly is kind=5, so sending
                // its own id is ignored by the client and leaves the room UI over battle.
                // Use the authoritative first kind=3 weekly id only as a close-UI anchor;
                // battle/progress still use started.dungeonId (kind=5) unchanged.
                val closeUiDungeonId = closeUiDungeonId(def.kind, started.dungeonId)
                for (member in started.members.values) {
                    sendCloseDungeonUi(member.session, closeUiDungeonId)
                }

                if (!battle.createWeeklyDungeonBattle(
                        started,
                        formation,
                        DvtWeeklyDungeonFormation.objective(def),
                    )
                ) {
                    system.clearRoomRuns(started)
                    for (member in started.members.values) sendQualificationFailed(member.session, 1)
                    dungeonHandlerLogger.error {
                        "Không thể tạo WeekDungeon battle dungeon=" + started.dungeonId +
                            " room=" + started.id
                    }
                }
                return@launch
            }

            if (!system.enterMultiplayer(started)) {
                system.clearRoomRuns(started)
                dungeonHandlerLogger.error {
                    "Không suy được entry point khi start dungeon=" + started.dungeonId +
                        " room=" + started.id + " instance=" + started.instanceId
                }
            }
        }
    }

    fun handleDisconnect(session: GameSession) {
        battleManager?.clearDungeonBotAssist(session.playerId)
        system.onDisconnect(session)
        val roomBefore = rooms.roomOf(session.playerId)
        val left = rooms.leave(session.playerId) ?: return
        for (member in left.room.members.values) {
            sendPlayerLeave(member.session, session.playerId, LEAVE_DISCONNECT)
        }
        val newOwnerId = left.newOwnerId
        if (newOwnerId != null) {
            for (member in left.room.members.values) {
                sendOwnerChanged(member.session, newOwnerId)
            }
        }
        roomBefore?.id?.let { roomId ->
            if (left.disbanded || newOwnerId != null) {
                clearPvpLobby(roomId)
                clearTeamDungeonLobby(roomId)
                if (!left.disbanded && left.room.members.size == 1) {
                    scheduleTeamDungeonLobbyFill(roomId)
                }
            } else if (pvpRoomModes.containsKey(roomId)) {
                scope.launch { fillPvpLobbyBots(roomId) }
            } else if (left.room.members.size == 1) {
                scheduleTeamDungeonLobbyFill(roomId)
            }
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

    private suspend fun claimWeeklyAllPass(session: GameSession, data: ByteArray) {
        if (data.size != 1) return
        val page = PacketReader(data).readUByte()
        if (!system.claimWeeklyAllPass(session, page)) return
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(22)
                .writeByte(0)
                .toByteArray()
        )
    }

    private fun sendStartTime(session: GameSession, startTime: Double) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(18)
                .writeByte(0)
                .writeDoubleLE(startTime)
                .toByteArray()
        )
    }

    private fun sendQualificationFailed(session: GameSession, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(19)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    private fun sendCloseDungeonUi(session: GameSession, dungeonId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(20)
                .writeByte(0)
                .writeShortLE(dungeonId)
                .toByteArray()
        )
    }

    fun sendDungeonResult(session: GameSession, settlement: DungeonSettlement) {
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(12)
            .writeByte(0)
            .writeByte(settlement.result)
            .writeShortLE(settlement.dungeonId)
            .writeByte(settlement.rewards.size)

        for ((itemId, quantity) in settlement.rewards) {
            writer.writeShortLE(itemId).writeIntLE(quantity)
        }
        session.send(writer.toByteArray())

        val kind = system.getDef(settlement.dungeonId)?.kind
        // Do not clear normal-team bot assistance here. Some DVT dungeons emit
        // an intermediate settlement/result while the player remains inside the
        // same dungeon instance and can still enter later Eve fights.
        // BattleManager drops stale assists automatically when instanceId changes.
        if (kind in setOf(DungeonSystem.KIND_WEEKLY, DungeonSystem.KIND_SOUL_WEEKLY)) {
            rooms.resetAfterBattleForPlayer(session.playerId)
        }
    }

    private fun sendInviteResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(8)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    private fun sendDungeonInvitation(
        target: GameSession,
        room: DungeonRoomState,
        owner: GameSession,
    ) {
        target.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(15)
                .writeByte(0)
                .writeUIntLE(room.id)
                .writeShortLE(room.dungeonId)
                .writeLongLE(owner.playerId)
                .writeBig5WithByteLen(owner.characterName)
                .writeByte(owner.level)
                .writeByte(owner.turn)
                .writeByte(owner.element)
                .writeByte(owner.turn3Element)
                .writeByte(owner.career)
                .writeByte(if (room.isLocked) 1 else 0)
                .toByteArray()
        )
    }

    private fun sendRecruitResult(session: GameSession, result: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.DUNGEON)
                .writeByte(9)
                .writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    private fun buildRecruitPacket(
        room: DungeonRoomState,
        owner: GameSession,
        channel: Int,
    ): ByteArray =
        PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(16)
            .writeByte(0)
            .writeUIntLE(room.id)
            .writeShortLE(room.dungeonId)
            .writeLongLE(owner.playerId)
            .writeBig5WithByteLen(owner.characterName)
            .writeByte(channel)
            .writeByte(if (room.isLocked) 1 else 0)
            .toByteArray()

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

    private fun sendLobbyBotJoined(target: GameSession, bot: LobbyBot) {
        val role = bot.template
        val writer = PacketWriter()
            .writeByte(Opcodes.DUNGEON)
            .writeByte(13)
            .writeByte(0)
            .writeLongLE(bot.id)
            .writeBig5WithByteLen(bot.name)
            .writeByte(role.level)
            .writeByte(role.sex)
            .writeByte(((role.hair + bot.hairOffset - 1).floorMod(8)) + 1)
            .writeIntLE(role.color1 xor bot.color1Xor)
            .writeIntLE(role.color2 xor bot.color2Xor)

        for (i in 0 until DUNGEON_EQUIP_STYLE_SLOTS) {
            val visualItemId = bot.outfitItemIds.getOrNull(i)
                ?: if (bot.copyEquipment) role.equipItemIds.getOrElse(i) { 0 } else 0
            writer.writeShortLE(visualItemId)
        }

        writer.writeByte(role.turn)
            .writeShortLE(role.titleId)

        target.send(writer.toByteArray())
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

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
        /** Current DVT IOS_Dungeon_C.dat first kind=3 weekly node. */
        const val SOUL_WEEKLY_CLOSE_UI_ANCHOR_ID = 18

        fun closeUiDungeonId(kind: Int, dungeonId: Int): Int =
            if (kind == DungeonSystem.KIND_SOUL_WEEKLY) SOUL_WEEKLY_CLOSE_UI_ANCHOR_ID else dungeonId

        private const val RESULT_ROOM_FULL = 6
        private const val RESULT_PASSWORD_WRONG = 9
        private const val CHANNEL_WORLD = 2
        private const val LEAVE_SELF = 0
        private const val LEAVE_KICKED = 1
        private const val LEAVE_DISCONNECT = 2
        private const val DUNGEON_EQUIP_STYLE_SLOTS = 11
    }
}
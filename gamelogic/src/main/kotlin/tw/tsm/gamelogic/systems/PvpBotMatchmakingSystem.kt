package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.EWarStyle
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.atomic.AtomicLong

private val pvpQueueLogger = KotlinLogging.logger {}

enum class PvpBotMode(
    val sideSize: Int,
    val warStyle: Int,
    val registrationMissionId: Int?,
) {
    VS(1, EWarStyle.ARENA_PK, null),
    CHAOS(5, EWarStyle.NEW_CHAOS_5V5, 10578),
    MIX(5, EWarStyle.NEW_MIX_5V5, 10007);

    companion object {
        fun fromRegistrationMission(missionId: Int): PvpBotMode? =
            entries.firstOrNull { it.registrationMissionId == missionId }
    }
}

class PvpBotMatchmakingSystem(
    private val battleManager: BattleManager,
    private val worldManager: WorldManager,
    private val partySystem: PartySystem,
    private val responseSender: ResponseSender,
    private val scope: CoroutineScope,
) {
    private data class Registration(
        val token: Long,
        val mode: PvpBotMode,
        val leaderId: Long,
        val playerIds: List<Long>,
        val createdAt: Long,
    )

    private data class MatchPlan(
        val mode: PvpBotMode,
        val right: List<GameSession>,
        val left: List<GameSession>,
        val rightBots: Int,
        val leftBots: Int,
    )

    private val lock = Any()
    private val nextToken = AtomicLong(1)
    private val queues = PvpBotMode.entries.associateWith { mutableListOf<Registration>() }
    private val playerToToken = mutableMapOf<Long, Long>()

    suspend fun register(session: GameSession, mode: PvpBotMode): Boolean {
        if (session.state != SessionState.IN_GAME || session.battleId != 0L) return false
        val registration = buildRegistration(session, mode) ?: return false

        synchronized(lock) {
            registration.playerIds.forEach { playerId ->
                playerToToken[playerId]?.let(::removeTokenLocked)
            }
            queues.getValue(mode).add(registration)
            registration.playerIds.forEach { playerToToken[it] = registration.token }
        }

        responseSender.sendSystemMessage(
            session,
            when (mode) {
                PvpBotMode.VS -> "Đã đăng ký VS. Ưu tiên ghép người thật trong 10 giây."
                PvpBotMode.CHAOS -> "Đã đăng ký Hỗn Chiến. Sau 10 giây sẽ lấp đủ bằng đấu sĩ AI."
                PvpBotMode.MIX -> "Đã đăng ký MixVS. Giữ nguyên tổ đội; sau 10 giây sẽ lấp slot trống bằng đấu sĩ AI."
            }
        )

        tryMatch(mode, allowBots = false)
        scope.launch {
            delay(BOT_FILL_DELAY_MS)
            tryMatch(mode, allowBots = true)
        }
        return true
    }

    fun cancel(playerId: Long) {
        synchronized(lock) {
            playerToToken[playerId]?.let(::removeTokenLocked)
        }
    }

    fun isQueued(playerId: Long): Boolean = synchronized(lock) {
        playerId in playerToToken
    }

    private fun buildRegistration(session: GameSession, mode: PvpBotMode): Registration? {
        val playerId = session.playerId
        if (mode == PvpBotMode.MIX) {
            val leaderId = partySystem.getLeaderId(playerId)
            if (leaderId != 0L && leaderId != playerId) {
                responseSender.sendSystemMessage(session, "MixVS: chỉ đội trưởng có thể đăng ký tổ đội.")
                return null
            }

            val ids = if (leaderId == 0L) listOf(playerId)
            else partySystem.getTeamMembers(leaderId).distinct()
            if (ids.isEmpty() || ids.size > PvpBotMode.MIX.sideSize) return null
            val sessions = ids.mapNotNull(worldManager::getPlayerById)
            if (sessions.size != ids.size || sessions.any { it.battleId != 0L }) {
                responseSender.sendSystemMessage(session, "MixVS: toàn bộ tổ đội phải online và ngoài chiến đấu.")
                return null
            }
            return Registration(
                token = nextToken.getAndIncrement(),
                mode = mode,
                leaderId = leaderId,
                playerIds = ids,
                createdAt = System.currentTimeMillis(),
            )
        }

        if (partySystem.isInParty(playerId)) {
            responseSender.sendSystemMessage(
                session,
                if (mode == PvpBotMode.CHAOS) {
                    "Hỗn Chiến: hãy rời tổ đội trước khi đăng ký."
                } else {
                    "VS: hãy rời tổ đội trước khi đăng ký."
                }
            )
            return null
        }
        return Registration(
            token = nextToken.getAndIncrement(),
            mode = mode,
            leaderId = 0L,
            playerIds = listOf(playerId),
            createdAt = System.currentTimeMillis(),
        )
    }

    private suspend fun tryMatch(mode: PvpBotMode, allowBots: Boolean) {
        while (true) {
            val plan = synchronized(lock) {
                purgeInvalidLocked(mode)
                selectPlanLocked(mode, allowBots)
            } ?: return

            val created = try {
                battleManager.createArenaBotBattle(
                    mode = plan.mode,
                    rightReal = plan.right,
                    leftReal = plan.left,
                    rightBotCount = plan.rightBots,
                    leftBotCount = plan.leftBots,
                )
            } catch (e: Exception) {
                pvpQueueLogger.error(e) { "Không tạo được arena ${plan.mode}" }
                false
            }

            if (!created) {
                pvpQueueLogger.warn {
                    "Arena ${plan.mode} match creation failed: " +
                        "right=${plan.right.map { it.playerId }} left=${plan.left.map { it.playerId }}"
                }
                plan.right.plus(plan.left).forEach {
                    responseSender.sendSystemMessage(it, "Ghép trận thất bại; vui lòng đăng ký lại.")
                }
            }
        }
    }

    private fun selectPlanLocked(mode: PvpBotMode, allowBots: Boolean): MatchPlan? {
        val queue = queues.getValue(mode)
        if (queue.isEmpty()) return null

        return when (mode) {
            PvpBotMode.VS -> {
                if (queue.size >= 2) {
                    val a = queue.removeAt(0)
                    val b = queue.removeAt(0)
                    detachLocked(a); detachLocked(b)
                    MatchPlan(mode, sessionsOf(a), sessionsOf(b), 0, 0)
                } else if (allowBots && oldEnough(queue.first())) {
                    val a = queue.removeAt(0)
                    detachLocked(a)
                    MatchPlan(mode, sessionsOf(a), emptyList(), 0, 1)
                } else null
            }

            PvpBotMode.MIX -> {
                val twoFullRealTeams = queue.size >= 2 &&
                    queue[0].playerIds.size == mode.sideSize &&
                    queue[1].playerIds.size == mode.sideSize
                if (twoFullRealTeams) {
                    val a = queue.removeAt(0)
                    val b = queue.removeAt(0)
                    detachLocked(a); detachLocked(b)
                    MatchPlan(mode, sessionsOf(a), sessionsOf(b), 0, 0)
                } else if (allowBots && oldEnough(queue.first())) {
                    val a = queue.removeAt(0)
                    val b = queue.firstOrNull()?.takeIf { oldEnough(it) }?.let { queue.removeAt(0) }
                    detachLocked(a)
                    if (b != null) detachLocked(b)
                    val right = sessionsOf(a)
                    val left = b?.let(::sessionsOf).orEmpty()
                    MatchPlan(
                        mode,
                        right,
                        left,
                        mode.sideSize - right.size,
                        mode.sideSize - left.size,
                    )
                } else null
            }

            PvpBotMode.CHAOS -> {
                if (queue.size >= mode.sideSize * 2) {
                    val regs = List(mode.sideSize * 2) { queue.removeAt(0) }
                    regs.forEach(::detachLocked)
                    val sessions = regs.flatMap(::sessionsOf)
                    MatchPlan(
                        mode,
                        sessions.filterIndexed { index, _ -> index % 2 == 0 },
                        sessions.filterIndexed { index, _ -> index % 2 == 1 },
                        0,
                        0,
                    )
                } else if (allowBots && oldEnough(queue.first())) {
                    val regs = mutableListOf<Registration>()
                    while (queue.isNotEmpty() && regs.size < mode.sideSize * 2 && oldEnough(queue.first())) {
                        regs += queue.removeAt(0)
                    }
                    regs.forEach(::detachLocked)
                    val sessions = regs.flatMap(::sessionsOf)
                    val right = sessions.filterIndexed { index, _ -> index % 2 == 0 }.take(mode.sideSize)
                    val left = sessions.filterIndexed { index, _ -> index % 2 == 1 }.take(mode.sideSize)
                    MatchPlan(
                        mode,
                        right,
                        left,
                        mode.sideSize - right.size,
                        mode.sideSize - left.size,
                    )
                } else null
            }
        }
    }

    private fun purgeInvalidLocked(mode: PvpBotMode) {
        val queue = queues.getValue(mode)
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val reg = iterator.next()
            if (isValid(reg)) continue
            iterator.remove()
            detachLocked(reg)
            sessionsOf(reg).forEach {
                responseSender.sendSystemMessage(it, "Đăng ký PvP đã hủy vì trạng thái người chơi/tổ đội thay đổi.")
            }
        }
    }

    private fun isValid(reg: Registration): Boolean {
        val sessions = sessionsOf(reg)
        if (sessions.size != reg.playerIds.size) return false
        if (sessions.any {
                it.state != SessionState.IN_GAME ||
                    it.battleId != 0L ||
                    battleManager.isInBattle(it.playerId)
            }
        ) return false

        return if (reg.mode == PvpBotMode.MIX) {
            if (reg.leaderId == 0L) {
                reg.playerIds.size == 1 && !partySystem.isInParty(reg.playerIds.first())
            } else {
                partySystem.getLeaderId(reg.leaderId) == reg.leaderId &&
                    partySystem.getTeamMembers(reg.leaderId).toSet() == reg.playerIds.toSet()
            }
        } else {
            reg.playerIds.size == 1 && !partySystem.isInParty(reg.playerIds.first())
        }
    }

    private fun sessionsOf(reg: Registration): List<GameSession> =
        reg.playerIds.mapNotNull(worldManager::getPlayerById)

    private fun oldEnough(reg: Registration): Boolean =
        System.currentTimeMillis() - reg.createdAt >= BOT_FILL_DELAY_MS

    private fun removeTokenLocked(token: Long) {
        for (queue in queues.values) {
            val index = queue.indexOfFirst { it.token == token }
            if (index >= 0) {
                val reg = queue.removeAt(index)
                detachLocked(reg)
                return
            }
        }
    }

    private fun detachLocked(reg: Registration) {
        reg.playerIds.forEach { playerId ->
            if (playerToToken[playerId] == reg.token) playerToToken.remove(playerId)
        }
    }

    companion object {
        const val BOT_FILL_DELAY_MS = 10_000L
    }
}

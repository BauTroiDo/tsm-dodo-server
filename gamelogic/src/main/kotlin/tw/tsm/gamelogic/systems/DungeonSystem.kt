package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import tw.tsm.domain.data.DungeonComboDef
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord

private val dungeonLogger = KotlinLogging.logger {}

data class ActiveDungeonRun(
    val dungeonId: Int,
    val startTime: Double,
    val instanceId: Int,
    val originMapId: Int = 0,
    val originX: Int = 0,
    val originY: Int = 0,
    val originInstanceId: Int = 0,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val settled: Boolean = false,
)

data class DungeonSettlement(
    val dungeonId: Int,
    /** DVT UIResult: 0 = success, non-zero = failure. */
    val result: Int,
    val rewards: List<Pair<Int, Int>> = emptyList(),
)

/**
 * DVT Dungeon core (protocol 47).
 *
 * Lua/client/data are authoritative. Weekly/Soul enemy formations are not present
 * in the client data, so kinds 3/5 are deliberately not started until that
 * backend-only battle definition is recovered.
 */
class DungeonSystem(
    private val defs: Map<Int, DungeonDef>,
    private val questSystem: QuestSystem,
    private val partySystem: PartySystem,
    private val specialTeleport: SpecialSceneTeleportService,
    private val responseSender: ResponseSender? = null,
    private val comboDefs: Map<Int, List<DungeonComboDef>> = emptyMap(),
    private val inventorySystem: InventorySystem? = null,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val activeRuns = ConcurrentHashMap<Long, ActiveDungeonRun>()

    fun getDef(id: Int): DungeonDef? = defs[id]
    fun getActiveRun(playerId: Long): ActiveDungeonRun? = activeRuns[playerId]

    /** Capture origin before scene entry so C:013-004 can really leave a dungeon. */
    fun markSingleStarted(session: GameSession, dungeonId: Int, startTime: Double) {
        activeRuns[session.playerId] = ActiveDungeonRun(
            dungeonId = dungeonId,
            startTime = startTime,
            instanceId = 0,
            originMapId = session.mapId,
            originX = session.x,
            originY = session.y,
            originInstanceId = session.instanceId,
            startedAtMillis = clockMillis(),
        )
    }

    /** Called before room teleport; each member keeps their own return point. */
    fun markRoomStarted(room: DungeonRoomState, startTime: Double) {
        val now = clockMillis()
        for (member in room.members.values) {
            val s = member.session
            activeRuns[s.playerId] = ActiveDungeonRun(
                dungeonId = room.dungeonId,
                startTime = startTime,
                instanceId = room.instanceId,
                originMapId = s.mapId,
                originX = s.x,
                originY = s.y,
                originInstanceId = s.instanceId,
                startedAtMillis = now,
            )
        }
    }

    fun clearActiveRun(playerId: Long) {
        activeRuns.remove(playerId)
    }

    fun clearRoomRuns(room: DungeonRoomState) {
        for (member in room.members.values) activeRuns.remove(member.session.playerId)
    }

    /**
     * Keep online clients synchronized when a day/week boundary passes without relog.
     * LoginService invokes the same persistence refresh before the initial Mark snapshot.
     */
    suspend fun refreshPeriodicProgress(session: GameSession) {
        val charId = session.roleId.toLong()
        for (missionId in questSystem.refreshDungeonDaily(charId)) {
            responseSender?.sendRemoveMission(session, missionId)
        }

        val weekly = questSystem.refreshDungeonWeekly(charId)
        for (missionId in weekly.removedMissionIds) {
            responseSender?.sendRemoveMission(session, missionId)
        }
        if (weekly.clearedFlagBits.isNotEmpty()) {
            responseSender?.sendSetMissionFlags(
                session,
                weekly.clearedFlagBits.map { MissionFlagRecord(it, 0) },
            )
        }
    }

    suspend fun exitActiveDungeon(session: GameSession): Boolean {
        val run = activeRuns[session.playerId] ?: return false
        if (session.battleId != 0L || run.originMapId <= 0) return false

        specialTeleport.teleport(
            session,
            run.originMapId,
            run.originX,
            run.originY,
            run.originInstanceId,
        )
        activeRuns.remove(session.playerId, run)
        return true
    }

    suspend fun validateSingleCreate(session: GameSession, dungeonId: Int): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        if (session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (def.kind != KIND_SINGLE || def.maxPlayer != 1) return RESULT_NO_SPACE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        val countResult = validateDailyCount(session, def)
        if (countResult != RESULT_OK) return countResult

        if (session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        if (specialTeleport.deriveDungeonEntryPoint(def.sceneId) == null) return RESULT_NO_DUNGEON
        return RESULT_OK
    }

    suspend fun validateNormalRoomEntry(
        session: GameSession,
        dungeonId: Int,
        alreadyInRoom: Boolean,
    ): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        // Kinds 3/5 are special WeekDungeon style 24 and require backend-only enemy formations.
        if (def.kind != KIND_NORMAL_MULTIPLAYER || def.maxPlayer <= 1) return RESULT_NO_SPACE
        if (session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
        if (alreadyInRoom || session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        return validateDailyCount(session, def)
    }

    suspend fun enterSingle(session: GameSession, dungeonId: Int): Boolean {
        val def = defs[dungeonId] ?: return false
        val point = specialTeleport.deriveDungeonEntryPoint(def.sceneId) ?: return false
        initializeMission(session, def)
        specialTeleport.teleport(session, def.sceneId, point.first, point.second)
        return true
    }

    suspend fun enterMultiplayer(room: DungeonRoomState): Boolean {
        val def = defs[room.dungeonId] ?: return false
        val point = specialTeleport.deriveDungeonEntryPoint(def.sceneId) ?: return false
        for (member in room.members.values) {
            initializeMission(member.session, def)
            specialTeleport.teleport(
                member.session,
                def.sceneId,
                point.first,
                point.second,
                room.instanceId,
            )
        }
        return true
    }

    private suspend fun initializeMission(session: GameSession, def: DungeonDef) {
        // Kinds 3/5 have missionFlag=0 and do not use Eve missions.
        if (def.missionFlag <= 0) return
        val charId = session.roleId.toLong()
        questSystem.setMissionStep(charId, def.missionFlag, 1)
        responseSender?.sendRemoveMission(session, def.missionFlag)
        responseSender?.sendAddMissionStep(session, def.missionFlag, 1)
    }

    /**
     * Resolve the exact item row shown by DVT DungeonComboData.
     *
     * kind=1 is a remaining-time tier. UIDungeon shows row N with the previous
     * row's kindValue/60 as its lower-bound label. kind=2 is the 10/50/99/100
     * damage table; a normal terminal PvE win means the configured enemies were
     * fully defeated, so the 100 row is the only unambiguous success row.
     */
    fun resolveRewards(
        dungeonId: Int,
        turn: Int,
        remainingSeconds: Int,
        playerWin: Boolean,
    ): List<Pair<Int, Int>> {
        val def = defs[dungeonId] ?: return emptyList()
        val rows = comboDefs[def.lootTable].orEmpty()
        if (rows.isEmpty()) return emptyList()

        val row = when (rows.first().kind) {
            1 -> {
                // At/after the server time limit DVT has no timed reward.
                if (remainingSeconds <= 0) return emptyList()
                var index = 0
                for (i in 1 until rows.size) {
                    if (remainingSeconds >= rows[i - 1].kindValue.toInt()) index = i else break
                }
                rows[index]
            }
            2 -> {
                if (!playerWin) return emptyList()
                rows.lastOrNull { it.kindValue == 100L } ?: return emptyList()
            }
            else -> return emptyList()
        }

        val result = mutableListOf<Pair<Int, Int>>()
        row.rewardForTurn(turn)?.let { reward ->
            if (reward.itemId > 0 && reward.quantity in 1..Int.MAX_VALUE.toLong()) {
                result += reward.itemId to reward.quantity.toInt()
            }
        }

        if (turn >= 1 && def.extraLoot != 0) {
            comboDefs[def.extraLoot]
                ?.firstOrNull { it.kindValue == turn.toLong() }
                ?.rewardForTurn(turn)
                ?.let { reward ->
                    if (reward.itemId > 0 && reward.quantity in 1..Int.MAX_VALUE.toLong()) {
                        result += reward.itemId to reward.quantity.toInt()
                    }
                }
        }
        return result
    }

    /**
     * Called after Eve resumeAfterBattle has applied terminal mission results.
     * Intermediate fights keep missionFlag active and therefore do not settle.
     */
    suspend fun settleAfterBattle(session: GameSession, playerWin: Boolean): DungeonSettlement? {
        val playerId = session.playerId
        val run = activeRuns[playerId] ?: return null
        if (run.settled) return null
        val def = defs[run.dungeonId] ?: return null
        if (def.kind !in setOf(KIND_NORMAL_MULTIPLAYER, KIND_SINGLE) || def.missionFlag <= 0) return null

        val missionStillActive = questSystem.getMissions(session.roleId.toLong())
            .any { it.missionId == def.missionFlag }
        if (missionStillActive) return null

        val settledRun = run.copy(settled = true)
        if (!activeRuns.replace(playerId, run, settledRun)) return null

        if (!playerWin) return DungeonSettlement(def.id, result = 1)

        val elapsedSeconds = ((clockMillis() - run.startedAtMillis).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingSeconds = (def.timeMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
        val rewards = resolveRewards(def.id, session.turn, remainingSeconds, playerWin = true)

        if (rewards.isEmpty()) {
            dungeonLogger.warn {
                "Dungeon ${def.id} terminal win has no supported DVT reward row " +
                    "lootTable=${def.lootTable} remaining=$remainingSeconds"
            }
            return DungeonSettlement(def.id, result = 0)
        }

        val inventory = inventorySystem
        if (inventory == null) {
            dungeonLogger.error { "Dungeon reward inventory not wired for dungeon=${def.id}" }
            return DungeonSettlement(def.id, result = 1)
        }

        val changed = inventory.addRewardsToBagExact(session.roleId.toLong(), rewards)
        if (changed == null) {
            dungeonLogger.warn { "Dungeon reward could not fit bag dungeon=${def.id} rewards=$rewards" }
            return DungeonSettlement(def.id, result = 1)
        }

        for ((slot, thing) in changed) responseSender?.sendSetBagItem(session, slot, thing, showMsg = true)
        return DungeonSettlement(def.id, result = 0, rewards = rewards)
    }

    /**
     * C:047-022 weekly all-clear reward. Client data uses the first node's
     * costPoint as the claim Mark and allPassPrize as the item.
     */
    suspend fun claimWeeklyAllPass(session: GameSession, page: Int): Boolean {
        if (page < 0) return false
        refreshPeriodicProgress(session)
        val pageDefs = defs.values
            .filter { it.kind == KIND_WEEKLY }
            .sortedBy { it.id }
            .drop(page * WEEK_PAGE_SIZE)
            .take(WEEK_PAGE_SIZE)
        if (pageDefs.size != WEEK_PAGE_SIZE) return false

        val anchor = pageDefs.first()
        if (anchor.costPoint <= 0 || anchor.allPassPrize <= 0) return false
        val charId = session.roleId.toLong()
        val missions = questSystem.getMissions(charId).associateBy { it.missionId }
        if (pageDefs.any { (missions[it.dailyFlag]?.step ?: 0) <= 0 }) return false
        if (questSystem.hasMissionFlag(charId, anchor.costPoint)) return false

        val inventory = inventorySystem ?: return false
        val changed = inventory.addItemsToBagExact(charId, anchor.allPassPrize, 1) ?: return false
        val completed = questSystem.completeMission(charId, anchor.costPoint)
        if (completed.bitId <= 0) return false

        for ((slot, thing) in changed) responseSender?.sendSetBagItem(session, slot, thing, showMsg = true)
        responseSender?.sendSetMissionFlags(
            session,
            listOf(MissionFlagRecord(completed.bitId, 1)),
        )
        return true
    }

    private suspend fun validateDailyCount(session: GameSession, def: DungeonDef): Int {
        if (def.dailyFlag == 0 || def.dailyCount <= 0) return RESULT_OK
        // Durable reset is currently evidenced only for normal kind1/2 Mark-kind8 counters.
        if (def.kind in setOf(KIND_NORMAL_MULTIPLAYER, KIND_SINGLE)) {
            for (missionId in questSystem.refreshDungeonDaily(session.roleId.toLong())) {
                responseSender?.sendRemoveMission(session, missionId)
            }
        }
        val used = questSystem.getMissions(session.roleId.toLong())
            .firstOrNull { it.missionId == def.dailyFlag }
            ?.step ?: 0
        return if (used >= def.dailyCount) RESULT_COUNT_EXHAUSTED else RESULT_OK
    }

    companion object {
        const val KIND_NORMAL_MULTIPLAYER = 1
        const val KIND_SINGLE = 2
        const val KIND_WEEKLY = 3
        const val KIND_SOUL_WEEKLY = 5
        const val WEEK_PAGE_SIZE = 20

        const val RESULT_OK = 0
        const val RESULT_NO_DUNGEON = 1
        const val RESULT_LEVEL_MISMATCH = 2
        const val RESULT_ALREADY_IN_ROOM = 3
        const val RESULT_COUNT_EXHAUSTED = 4
        const val RESULT_CANNOT_TEAM = 5
        const val RESULT_NO_SPACE = 7
        const val RESULT_IN_BATTLE = 8
    }
}

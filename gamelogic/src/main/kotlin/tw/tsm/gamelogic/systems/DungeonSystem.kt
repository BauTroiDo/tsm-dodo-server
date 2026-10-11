package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.data.CollectWarriorSortDef
import tw.tsm.domain.data.DungeonComboDef
import tw.tsm.domain.data.DungeonDef
import tw.tsm.domain.data.ExclusiveWeaponDef
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.data.SceneSetDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.DungeonRunRecord
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
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

data class DungeonSceneTimer(
    val dungeonId: Int,
    val startTime: Double,
    /** true when the run had to be reconstructed from the current scene. */
    val recovered: Boolean,
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
    private val sceneSetDefs: Map<Int, SceneSetDef> = emptyMap(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dungeonMinuteMillis: Long = REAL_MINUTE_MS,
    private val dungeonRunRepository: DungeonRunRepository? = null,
    private val adminBypassPolicy: AdminBypassPolicy = AdminBypassPolicy(),
    private val petSystem: PetSystem? = null,
    private val soulSystem: SoulSystem? = null,
    private val npcDefs: Map<Int, NpcDef> = emptyMap(),
    private val npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap(),
    private val itemDefs: Map<Int, ItemDef> = emptyMap(),
    private val exclusiveWeaponDefs: Map<Int, Map<Int, ExclusiveWeaponDef>> = emptyMap(),
    private val collectWarriorSortDefs: List<CollectWarriorSortDef> = emptyList(),
    /** Optional for isolated legacy fixtures; production DI must pass this. */
    private val rewardLedgerRepository: RewardLedgerRepository? = null,
) {
    private val activeRuns = ConcurrentHashMap<Long, ActiveDungeonRun>()
    private val timeoutJobs = ConcurrentHashMap<Long, Job>()

    fun getDef(id: Int): DungeonDef? = defs[id]
    fun getActiveRun(playerId: Long): ActiveDungeonRun? = activeRuns[playerId]

    /**
     * C:012-001 scene-ready hook for ordinary DungeonData instances.
     *
     * DVT S:047-018 carries the OLE start time; on reconnect the client may also
     * consume a trailing UInt16 dungeonId when Dungeon.nowDungeonId was reset.
     * If the runtime run survived, keep its original start time/deadline. If the
     * server restarted and the run was lost, recover only when exactly one timed
     * DungeonData row owns this scene and start a fresh offline fallback window.
     */
    suspend fun onSceneReady(session: GameSession, recoveryStartTime: Double): DungeonSceneTimer? {
        val playerId = session.playerId
        val characterId = session.roleId.toLong()
        stopTimeout(playerId)

        if (sceneSetDefs[session.mapId]?.kind != SCENE_KIND_DYNAMIC_DUNGEON) {
            activeRuns.remove(playerId)
            dungeonRunRepository?.clear(characterId)
            return null
        }

        val existing = activeRuns[playerId]
        if (existing != null) {
            val def = defs[existing.dungeonId]
            if (isTimedRunForScene(def, session.mapId)) {
                armTimeout(session, existing, def!!)
                return DungeonSceneTimer(existing.dungeonId, existing.startTime, recovered = false)
            }
            activeRuns.remove(playerId, existing)
        }

        val persisted = dungeonRunRepository?.load(characterId)
        if (persisted != null) {
            val def = defs[persisted.dungeonId]
            if (isTimedRunForScene(def, session.mapId)) {
                val restored = persisted.toActiveDungeonRun()
                activeRuns[playerId] = restored
                armTimeout(session, restored, def!!)
                dungeonLogger.info {
                    session.characterName + " khôi phục ActiveDungeonRun bền vững dungeon=" +
                        restored.dungeonId + " scene=" + session.mapId +
                        " origin=" + restored.originMapId
                }
                return DungeonSceneTimer(restored.dungeonId, restored.startTime, recovered = true)
            }
            dungeonRunRepository.clear(characterId)
        }

        // Legacy recovery for players already stranded before durable run persistence existed.
        // The timer can be reconstructed from a unique DungeonData scene, but origin remains
        // unknown and exitSpecialScene() must use Eve or the player's saved record point.
        val def = uniqueTimedDungeonForScene(session.mapId) ?: return null
        val recovered = ActiveDungeonRun(
            dungeonId = def.id,
            startTime = recoveryStartTime,
            instanceId = session.instanceId,
            startedAtMillis = clockMillis(),
        )
        activeRuns[playerId] = recovered
        persistRun(session, recovered)
        armTimeout(session, recovered, def)
        dungeonLogger.info {
            session.characterName + " khôi phục legacy timer phụ bản scene=" + session.mapId +
                " dungeon=" + def.id + " (không có origin cũ)"
        }
        return DungeonSceneTimer(def.id, recovered.startTime, recovered = true)
    }

    /** Preserve durable state across reconnect; only the live-session timeout job is released. */
    fun onDisconnect(session: GameSession) {
        val playerId = session.playerId
        stopTimeout(playerId)
        val run = activeRuns[playerId] ?: return
        val def = defs[run.dungeonId]
        if (!isTimedRunForScene(def, session.mapId)) {
            activeRuns.remove(playerId, run)
            return
        }
        armDisconnectedExpiry(playerId, run, def!!)
    }

    /** Capture and persist origin before scene entry so C:013-004 can always leave after restart. */
    suspend fun markSingleStarted(session: GameSession, dungeonId: Int, startTime: Double) {
        val run = ActiveDungeonRun(
            dungeonId = dungeonId,
            startTime = startTime,
            instanceId = 0,
            originMapId = session.mapId,
            originX = session.x,
            originY = session.y,
            originInstanceId = session.instanceId,
            startedAtMillis = clockMillis(),
        )
        persistRun(session, run)
        activeRuns[session.playerId] = run
    }

    /** Called before room teleport; each member persists their own exact return point. */
    suspend fun markRoomStarted(room: DungeonRoomState, startTime: Double) {
        val now = clockMillis()
        for (member in room.members.values) {
            val s = member.session
            val run = ActiveDungeonRun(
                dungeonId = room.dungeonId,
                startTime = startTime,
                instanceId = room.instanceId,
                originMapId = s.mapId,
                originX = s.x,
                originY = s.y,
                originInstanceId = s.instanceId,
                startedAtMillis = now,
            )
            persistRun(s, run)
            activeRuns[s.playerId] = run
        }
    }

    suspend fun clearActiveRun(session: GameSession) {
        stopTimeout(session.playerId)
        activeRuns.remove(session.playerId)
        dungeonRunRepository?.clear(session.roleId.toLong())
    }

    suspend fun clearRoomRuns(room: DungeonRoomState) {
        for (member in room.members.values) clearActiveRun(member.session)
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

    /**
     * C:013-004 from UIMain's "Leave Dungeon" button.
     *
     * DVT UIMain.UpdateDungeon shows this HUD exit when sceneDatas[sceneId].kind == 2.
     * Therefore the server must use SceneSet kind=2 as the authoritative gate rather
     * than requiring the map to appear in IOS_Dungeon_C.dat.
     *
     * Prefer the captured pre-entry position. If the in-memory ActiveDungeonRun
     * was lost by a server restart/relog, fall back to the authoritative Eve
     * sceneInfo exit (backgroundNo + playerAppearX/Y) instead of hard-coding a map.
     */
    suspend fun exitDungeonFromHud(session: GameSession): Boolean {
        if (session.battleId != 0L) return false
        if (sceneSetDefs[session.mapId]?.kind != SCENE_KIND_DYNAMIC_DUNGEON) return false

        val run = activeRuns[session.playerId]
        if (run != null && run.originMapId > 0) {
            specialTeleport.teleport(
                session,
                run.originMapId,
                run.originX,
                run.originY,
                run.originInstanceId,
            )
            activeRuns.remove(session.playerId, run)
            stopTimeout(session.playerId)
            dungeonRunRepository?.clear(session.roleId.toLong())
            dungeonLogger.info {
                session.characterName + " rời phụ bản về origin " + run.originMapId +
                    " từ scene=" + session.mapId
            }
            return true
        }

        val fromScene = session.mapId
        val exited = specialTeleport.exitSpecialScene(session, fromScene)
        if (exited) {
            activeRuns.remove(session.playerId)
            stopTimeout(session.playerId)
            dungeonRunRepository?.clear(session.roleId.toLong())
            dungeonLogger.info { session.characterName + " rời phụ bản qua Eve sceneInfo từ scene=" + fromScene }
        } else {
            dungeonLogger.warn { session.characterName + " không tìm được Eve sceneInfo để rời scene=" + fromScene }
        }
        return exited
    }

    /** Kept for tests/callers that explicitly require an active-run-only exit. */
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
        stopTimeout(session.playerId)
        dungeonRunRepository?.clear(session.roleId.toLong())
        return true
    }

    suspend fun validateSingleCreate(session: GameSession, dungeonId: Int): Int {
        val def = defs[dungeonId] ?: return RESULT_NO_DUNGEON
        if (!adminBypassPolicy.bypassLevel(session) && session.level !in def.minLv..def.maxLv) return RESULT_LEVEL_MISMATCH
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
        val supported = when (def.kind) {
            KIND_NORMAL_MULTIPLAYER -> def.maxPlayer > 1
            KIND_WEEKLY, KIND_SOUL_WEEKLY -> def.maxPlayer >= 1 &&
                DvtWeeklyDungeonFormation.enemyIds(def.id) != null
            else -> false
        }
        if (!supported) return RESULT_NO_SPACE
        if (!adminBypassPolicy.bypassLevel(session) && session.level !in def.minLv..def.maxLv) {
            return RESULT_LEVEL_MISMATCH
        }
        if (alreadyInRoom || session.mapId == def.sceneId) return RESULT_ALREADY_IN_ROOM
        if (session.battleId != 0L) return RESULT_IN_BATTLE
        if (partySystem.isInParty(session.playerId)) return RESULT_CANNOT_TEAM

        if (def.kind == KIND_SOUL_WEEKLY &&
            !adminBypassPolicy.bypassQuestGate(session) &&
            dungeonId > SOUL_WEEKLY_FIRST_ID
        ) {
            val previous = defs[dungeonId - 1] ?: return RESULT_NO_DUNGEON
            val cleared = questSystem.getMissions(session.roleId.toLong())
                .firstOrNull { it.missionId == previous.dailyFlag }
                ?.step ?: 0
            if (cleared < previous.dailyCount.coerceAtLeast(1)) return RESULT_SOUL_STAGE_LOCKED
        }

        return validateDailyCount(session, def)
    }

    /**
     * Revalidate all weekly room members at the actual start boundary.
     * Lua's local weekly qualification guard is buggy, so server state is authoritative.
     */
    suspend fun validateWeeklyRoomStart(room: DungeonRoomState): Map<Long, Int> {
        val def = defs[room.dungeonId] ?: return mapOf(room.ownerId to QUALIFICATION_FAILED)
        if (def.kind !in setOf(KIND_WEEKLY, KIND_SOUL_WEEKLY)) return emptyMap()
        val pets = petSystem ?: return mapOf(room.ownerId to QUALIFICATION_FAILED)

        val failures = linkedMapOf<Long, Int>()
        for ((playerId, member) in room.members) {
            val session = member.session
            refreshPeriodicProgress(session)
            if (adminBypassPolicy.bypassQuestGate(session)) continue
            val selected = pets.getBattleDeployableFollowPets(session.roleId.toLong()).firstOrNull()?.second
            val soul = selected?.let {
                soulSystem?.equippedForNpc(session.roleId.toLong(), it.npcId)
            }
            val result = DvtWeeklyQualification.checkEntry(
                dungeon = def,
                deployedPet = selected,
                npcDefs = npcDefs,
                equippedSoul = soul,
                npcUpgradeDefs = npcUpgradeDefs,
                itemDefs = itemDefs,
                exclusiveWeaponDefs = exclusiveWeaponDefs,
                collectWarriorSortDefs = collectWarriorSortDefs,
            )
            when (result.status) {
                DvtWeeklyQualification.Status.OK -> Unit
                DvtWeeklyQualification.Status.FIGHT_NPC_REQUIRED -> failures[playerId] = 3
                else -> failures[playerId] = QUALIFICATION_FAILED
            }
        }
        return failures
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

    /** Non-battle Eve completion uses the exact same terminal settlement path. */
    suspend fun settleAfterEvent(session: GameSession): DungeonSettlement? =
        settleAfterBattle(session, playerWin = true)

    /**
     * Called after Eve has applied terminal mission results.
     * Intermediate fights/events keep missionFlag active and therefore do not settle.
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

        val charId = session.roleId.toLong()
        // Read-only preflight BEFORE the irreversible ledger claim. A full bag
        // must not leave a PENDING receipt for an award we never attempted.
        val elapsedSeconds = ((clockMillis() - run.startedAtMillis).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingSeconds = (def.timeMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
        val rewards = if (playerWin) {
            resolveRewards(def.id, session.turn, remainingSeconds, playerWin = true)
        } else emptyList()
        val ledger = rewardLedgerRepository
        if (playerWin && ledger != null && rewards.isNotEmpty()) {
            val inventory = inventorySystem
            if (inventory == null || !inventory.canAddRewardsToBag(charId, rewards)) {
                responseSender?.sendSystemMessage(session, "Túi đồ không đủ chỗ hoặc phần thưởng không hợp lệ; chưa giữ quyền nhận.")
                return DungeonSettlement(def.id, result = 1)
            }
        }
        var rewardClaim: Pair<String, String>? = null
        if (playerWin && ledger != null) {
            // Fail closed for legacy scene-reconstruction with no authoritative
            // run origin, or a run that was never durably saved.
            val stored = try { dungeonRunRepository?.load(charId) } catch (e: Exception) {
                dungeonLogger.error(e) { "Cannot load dungeon run before payout: charId=$charId" }
                null
            }
            if (run.originMapId <= 0 || stored == null || stored.settled ||
                stored.dungeonId != run.dungeonId ||
                stored.startedAtMillis != run.startedAtMillis ||
                stored.instanceId != run.instanceId
            ) {
                dungeonLogger.error { "Ambiguous dungeon payout run: charId=$charId dungeon=${run.dungeonId}" }
                responseSender?.sendSystemMessage(session, "Không xác minh được lượt phụ bản; hãy liên hệ quản trị viên.")
                return DungeonSettlement(def.id, result = 1)
            }
            val sourceKey = RewardLedgerSourceKeys.dungeon(run, "TERMINAL")
            val attemptToken = UUID.randomUUID().toString()
            val acquired = try {
                ledger.reserve(charId, "DUNGEON_NORMAL", sourceKey, attemptToken, clockMillis())
            } catch (e: Exception) {
                dungeonLogger.error(e) { "Cannot reserve dungeon payout: charId=$charId key=$sourceKey" }
                false
            }
            if (!acquired) {
                // Never replay after a previous attempt may have issued rewards.
                // A pending claim must be reconciled by an operator.
                dungeonLogger.error { "Dungeon payout already reserved or unconfirmed: charId=$charId key=$sourceKey" }
                responseSender?.sendSystemMessage(session, "Phần thưởng phụ bản đang chờ xác minh; không thể phát lại.")
                return DungeonSettlement(def.id, result = 1)
            }
            rewardClaim = sourceKey to attemptToken
        }

        val settledRun = run.copy(settled = true)
        if (!activeRuns.replace(playerId, run, settledRun)) return null
        persistRun(session, settledRun)

        if (!playerWin) return DungeonSettlement(def.id, result = 1)

        // DVT UIDungeon shows remaining attempts as dayilyCount - MarkManager.missions[dayilyFlag].step.
        // Record a successful completion independently from reward resolution. GM/admin accounts may
        // bypass the entry gate, but the client-visible daily counter must still advance normally.
        if (def.dailyFlag > 0 && def.dailyCount > 0) {
            val used = questSystem.getMissions(charId)
                .firstOrNull { it.missionId == def.dailyFlag }
                ?.step ?: 0
            if (used < def.dailyCount) {
                val previous = questSystem.addMissionStep(charId, def.dailyFlag, 1)
                if (previous != null) {
                    responseSender?.sendAddMissionStep(session, def.dailyFlag, 1)
                }
            }
        }

        if (rewards.isEmpty()) {
            dungeonLogger.warn {
                "Dungeon ${def.id} terminal win has no supported DVT reward row " +
                    "lootTable=${def.lootTable} remaining=$remainingSeconds"
            }
            if (!settleNormalDungeonClaim(session, ledger, rewardClaim)) {
                return DungeonSettlement(def.id, result = 1)
            }
            return DungeonSettlement(def.id, result = 0)
        }

        val inventory = inventorySystem
        if (inventory == null) {
            dungeonLogger.error { "Dungeon reward inventory not wired for dungeon=${def.id}" }
            return DungeonSettlement(def.id, result = 1)
        }

        val changed = inventory.addRewardsToBagExact(charId, rewards)
        if (changed == null) {
            dungeonLogger.warn { "Dungeon reward could not fit bag dungeon=${def.id} rewards=$rewards" }
            return DungeonSettlement(def.id, result = 1)
        }

        if (!settleNormalDungeonClaim(session, ledger, rewardClaim)) {
            // The items may already exist in DB/RAM: report unresolved, NEVER retry.
            // Defer UI reward success packets until the ledger receipt commits.
            return DungeonSettlement(def.id, result = 1)
        }
        for ((slot, thing) in changed) responseSender?.sendSetBagItem(session, slot, thing, showMsg = true)
        dungeonLogger.info {
            "Dungeon ${def.id} settled: remaining=${remainingSeconds}s turn=${session.turn} rewards=$rewards dailyFlag=${def.dailyFlag}"
        }
        return DungeonSettlement(def.id, result = 0, rewards = rewards)
    }

    /**
     * Receipt only after all supported normal-dungeon mutations completed.
     * A failed acknowledgement leaves a PENDING claim to inspect, not a second
     * attempt to grant the same items or increment the daily counter.
     */
    private suspend fun settleNormalDungeonClaim(
        session: GameSession,
        ledger: RewardLedgerRepository?,
        claim: Pair<String, String>?,
    ): Boolean {
        if (ledger == null || claim == null) return true // Legacy fixture only.
        val (sourceKey, attemptToken) = claim
        val charId = session.roleId.toLong()
        val settled = try {
            ledger.settle(charId, "DUNGEON_NORMAL", sourceKey, attemptToken, clockMillis())
        } catch (e: Exception) {
            dungeonLogger.error(e) { "Dungeon payout receipt unresolved: charId=$charId key=$sourceKey" }
            false
        }
        if (!settled) {
            responseSender?.sendSystemMessage(
                session, "Phần thưởng đã xử lý nhưng chưa xác nhận được biên nhận; hãy liên hệ quản trị viên."
            )
        }
        return settled
    }

    /**
     * Dedicated WeekDungeon/Soul settlement. These battles do not use Eve missionFlag
     * and must never receive generic NPC EXP/gold/drop settlement.
     */
    suspend fun settleWeeklyBattle(
        session: GameSession,
        dungeonId: Int,
        playerWin: Boolean,
    ): DungeonSettlement? {
        val playerId = session.playerId
        val run = activeRuns[playerId] ?: return null
        if (run.dungeonId != dungeonId || run.settled) return null
        val def = defs[dungeonId] ?: return null
        if (def.kind !in setOf(KIND_WEEKLY, KIND_SOUL_WEEKLY) || def.dailyFlag <= 0) return null

        val settledRun = run.copy(settled = true)
        if (!activeRuns.replace(playerId, run, settledRun)) return null
        if (!playerWin) {
            finishWeeklyRun(session, settledRun)
            return DungeonSettlement(dungeonId, result = 1)
        }

        refreshPeriodicProgress(session)
        val charId = session.roleId.toLong()
        val requiredCount = def.dailyCount.coerceAtLeast(1)
        val alreadyCompleted = questSystem.getMissions(charId)
            .firstOrNull { it.missionId == def.dailyFlag }
            ?.step
            ?.let { it >= requiredCount }
            ?: false
        if (alreadyCompleted) {
            finishWeeklyRun(session, settledRun)
            return DungeonSettlement(dungeonId, result = 0)
        }

        val elapsedSeconds = ((clockMillis() - run.startedAtMillis).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingSeconds = (def.timeMinutes * 60 - elapsedSeconds).coerceAtLeast(0)
        val rewards = resolveRewards(dungeonId, session.turn, remainingSeconds, playerWin = true)

        val changed = if (rewards.isNotEmpty()) {
            val inventory = inventorySystem ?: run {
                finishWeeklyRun(session, settledRun)
                return DungeonSettlement(dungeonId, result = 1)
            }
            inventory.addRewardsToBagExact(charId, rewards) ?: run {
                finishWeeklyRun(session, settledRun)
                return DungeonSettlement(dungeonId, result = 1)
            }
        } else {
            emptyList()
        }

        questSystem.setMissionStep(charId, def.dailyFlag, requiredCount)
        responseSender?.sendAddMissionStep(session, def.dailyFlag, requiredCount)
        for ((slot, thing) in changed) {
            responseSender?.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        dungeonLogger.info {
            "WeekDungeon settled dungeon=$dungeonId kind=${def.kind} charId=$charId " +
                "rewards=$rewards dailyFlag=${def.dailyFlag}"
        }
        finishWeeklyRun(session, settledRun)
        return DungeonSettlement(dungeonId, result = 0, rewards = rewards)
    }

    private suspend fun finishWeeklyRun(session: GameSession, run: ActiveDungeonRun) {
        stopTimeout(session.playerId)
        activeRuns.remove(session.playerId, run)
        dungeonRunRepository?.clear(session.roleId.toLong())
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
        if (!adminBypassPolicy.bypassQuestGate(session) && pageDefs.any { (missions[it.dailyFlag]?.step ?: 0) <= 0 }) return false
        if (!adminBypassPolicy.rewardRepeatTest(session) && questSystem.hasMissionFlag(charId, anchor.costPoint)) return false

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
        if (adminBypassPolicy.bypassEntryLimit(session)) return RESULT_OK
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

    private fun isTimedRunForScene(def: DungeonDef?, sceneId: Int): Boolean =
        def != null &&
            def.sceneId == sceneId &&
            def.timeMinutes > 0 &&
            def.kind in setOf(KIND_NORMAL_MULTIPLAYER, KIND_SINGLE)

    private suspend fun persistRun(session: GameSession, run: ActiveDungeonRun) {
        dungeonRunRepository?.save(
            DungeonRunRecord(
                characterId = session.roleId.toLong(),
                dungeonId = run.dungeonId,
                startTime = run.startTime,
                startedAtMillis = run.startedAtMillis,
                instanceId = run.instanceId,
                originMapId = run.originMapId,
                originX = run.originX,
                originY = run.originY,
                originInstanceId = run.originInstanceId,
                settled = run.settled,
            )
        )
    }

    private fun DungeonRunRecord.toActiveDungeonRun(): ActiveDungeonRun =
        ActiveDungeonRun(
            dungeonId = dungeonId,
            startTime = startTime,
            instanceId = instanceId,
            originMapId = originMapId,
            originX = originX,
            originY = originY,
            originInstanceId = originInstanceId,
            startedAtMillis = startedAtMillis,
            settled = settled,
        )

    private fun uniqueTimedDungeonForScene(sceneId: Int): DungeonDef? {
        val matches = defs.values.filter {
            it.sceneId == sceneId &&
                it.timeMinutes > 0 &&
                it.kind in setOf(KIND_NORMAL_MULTIPLAYER, KIND_SINGLE)
        }
        return when (matches.size) {
            1 -> matches.single()
            0 -> null
            else -> {
                dungeonLogger.warn {
                    "Không thể khôi phục timer scene=$sceneId vì có nhiều DungeonData: " +
                        matches.joinToString { it.id.toString() }
                }
                null
            }
        }
    }

    private fun stopTimeout(playerId: Long) {
        timeoutJobs.remove(playerId)?.cancel()
    }

    private fun armTimeout(session: GameSession, run: ActiveDungeonRun, def: DungeonDef) {
        val playerId = session.playerId
        if (adminBypassPolicy.bypassEntryLimit(session)) {
            stopTimeout(playerId)
            return
        }
        stopTimeout(playerId)

        val job = scope.launch {
            try {
                val deadline = run.startedAtMillis + def.timeMinutes.toLong() * dungeonMinuteMillis
                val initialWait = deadline - clockMillis()
                if (initialWait > 0L) delay(initialWait)

                // Manual leave is blocked during battle by the DVT client. For timeout,
                // keep the deadline authoritative but wait until battle cleanup is safe.
                while (
                    session.state == SessionState.IN_GAME &&
                    session.mapId == def.sceneId &&
                    session.battleId != 0L
                ) {
                    delay(TIMEOUT_BATTLE_RETRY_MS)
                }

                val current = activeRuns[playerId]
                val sameRun = current != null &&
                    current.dungeonId == run.dungeonId &&
                    current.startedAtMillis == run.startedAtMillis
                if (
                    sameRun &&
                    session.state == SessionState.IN_GAME &&
                    session.mapId == def.sceneId
                ) {
                    val exited = exitDungeonFromHud(session)
                    if (exited) {
                        dungeonLogger.info {
                            session.characterName + " hết thời gian dungeon=" + def.id +
                                " scene=" + def.sceneId + " và đã bị đưa ra ngoài"
                        }
                    } else {
                        dungeonLogger.warn {
                            session.characterName + " hết thời gian dungeon=" + def.id +
                                " nhưng không suy được điểm thoát scene=" + def.sceneId
                        }
                    }
                }
            } finally {
                val me = kotlin.coroutines.coroutineContext[Job]
                if (me != null) timeoutJobs.remove(playerId, me)
            }
        }
        timeoutJobs[playerId] = job
    }

    private fun armDisconnectedExpiry(playerId: Long, run: ActiveDungeonRun, def: DungeonDef) {
        stopTimeout(playerId)
        val remaining = (
            run.startedAtMillis + def.timeMinutes.toLong() * dungeonMinuteMillis - clockMillis()
        ).coerceAtLeast(0L)
        val job = scope.launch {
            try {
                if (remaining > 0L) delay(remaining)
                activeRuns.remove(playerId, run)
            } finally {
                val me = kotlin.coroutines.coroutineContext[Job]
                if (me != null) timeoutJobs.remove(playerId, me)
            }
        }
        timeoutJobs[playerId] = job
    }

    companion object {
        const val KIND_NORMAL_MULTIPLAYER = 1
        const val KIND_SINGLE = 2
        const val KIND_WEEKLY = 3
        const val KIND_SOUL_WEEKLY = 5
        const val SOUL_WEEKLY_FIRST_ID = 63
        const val SCENE_KIND_DYNAMIC_DUNGEON = 2
        const val WEEK_PAGE_SIZE = 20
        const val REAL_MINUTE_MS = 60_000L
        const val TIMEOUT_BATTLE_RETRY_MS = 1_000L

        const val RESULT_OK = 0
        const val RESULT_NO_DUNGEON = 1
        const val RESULT_LEVEL_MISMATCH = 2
        const val RESULT_ALREADY_IN_ROOM = 3
        const val RESULT_COUNT_EXHAUSTED = 4
        const val RESULT_CANNOT_TEAM = 5
        const val RESULT_NO_SPACE = 7
        const val RESULT_IN_BATTLE = 8
        const val RESULT_SOUL_STAGE_LOCKED = 9

        /** S47-019 generic weekly qualification failure; result=3 is fightNpc missing. */
        const val QUALIFICATION_FAILED = 1
    }
}

package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.constants.EWar
import tw.tsm.domain.constants.EWarStyle
import tw.tsm.domain.data.EveFightData
import tw.tsm.domain.data.EveFightEnemy
import tw.tsm.domain.data.ExclusiveSkillDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcDropDef
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.data.SceneSetDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.ElfRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.util.DvtPercentMath
import tw.tsm.gamelogic.battle.*
import tw.tsm.protocol.BattleRoleData
import tw.tsm.protocol.BattleRoleSerializer
import tw.tsm.protocol.PacketWriter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Trình quản lý chiến đấu — quản lý mọi trận đấu đang diễn ra
 *
 * Chịu trách nhiệm tạo trận, phân phát lệnh, vòng lặp hiệp đấu, quyết toán.
 * Sau khi trận đấu kết thúc, phục hồi luồng sự kiện NPC thông qua callback (tránh phụ thuộc vòng với NpcEventHandler).
 */
class BattleManager(
    private val responseSender: ResponseSender,
    private val skillDefs: Map<Int, SkillDef>,
    private val npcDefs: Map<Int, NpcDef>,
    private val playerStateManager: PlayerStateManager,
    private val inventorySystem: InventorySystem,
    private val petSystem: PetSystem,
    private val moneySystem: MoneySystem,
    private val statSystem: StatSystem,
    private val scope: CoroutineScope,
    private val npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap(),
    private val skillSystem: SkillSystem? = null,
    private val macroBoxSystem: MacroBoxSystem? = null,
    private val itemDefs: Map<Int, tw.tsm.domain.data.ItemDef> = emptyMap(),
    private val skillRepository: SkillRepository? = null,
    private val characterRepository: CharacterRepository? = null,
    private val npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap(),
    private val sceneSetDefs: Map<Int, SceneSetDef> = emptyMap(),
    private val dailyProgressSystem: DailyProgressSystem? = null,
    private val collectionSystem: CollectionSystem? = null,
    private val sacredEquipPrimaryDefs: Map<Int, SacredEquipPrimaryDef> = emptyMap(),
    private val exclusiveSkillDefs: Map<Int, Map<Int, ExclusiveSkillDef>> = emptyMap(),
    private val soulSystem: SoulSystem? = null,
    private val elfRepository: ElfRepository? = null,
    private val elfBaseExpByNo: Map<Int, Int> = emptyMap(),
) {
    /** Trình phân tích vật phẩm chiến đấu (stateless, dùng chung cho toàn trận)*/
    private val battleItemResolver = BattleItemResolver(inventorySystem, itemDefs)

    /**
     * Callback khi kết thúc trận — do ServerModule thiết lập, gọi NpcEventHandler.resumeAfterBattle()
     *
     * Tham số: (session, playerId, playerWin)
     */
    var onBattleAbortCallback: ((GameSession) -> Unit)? = null
    var onBattleEndCallback: (suspend (GameSession, Long, Boolean) -> Unit)? = null
    /**
     * Called BEFORE generic EXP, gold, drops, kill counts, Phuc Than charge
     * and captured pets are granted in a winning Eve battle.
     * False means the DB could not fence this Eve attempt: never pay or resume.
     */
    var onBeforeEveBattleRewards: (suspend (GameSession) -> Boolean)? = null
    var onWeeklyDungeonBattleEndCallback:
        (suspend (GameSession, Int, Boolean, Int) -> Unit)? = null
    /** Mọi trận đấu đang diễn ra (battleId → BattleInstance)*/
    private val battles = ConcurrentHashMap<Long, BattleInstance>()

    /** Ánh xạ người chơi → ID trận đấu*/
    private val playerBattleMap = ConcurrentHashMap<Long, Long>()

    /**
     * DVT Eve_FightData reinforcement queues keyed by the real battlefield slot.
     * Only populated for sides whose LocationPos values are all authoritative 1..10.
     */
    private val pendingEveEnemies =
        ConcurrentHashMap<Long, MutableMap<DvtEveBattlePlacement.Slot, ArrayDeque<EveFightEnemy>>>()

    /** Trình tạo ID trận đấu*/
    private val nextBattleId = AtomicLong(1)
    /** ID âm chỉ tồn tại trong battle, không bao giờ đụng characterId DB thật. */
    private val nextBotRoleId = AtomicLong(-1_000_000_000L)
    @Volatile
    var worldPartyBotProvider: ((GameSession) -> List<WorldSimBotBattleProfile>)? = null

    private val headlessWorldBotCallbacks =
        ConcurrentHashMap<Long, (Boolean, Int) -> Unit>()

    private data class DungeonBotAssist(val instanceId: Int, val botCount: Int)
    private val dungeonBotAssists = ConcurrentHashMap<Long, DungeonBotAssist>()

    /**
     * Attach offline bot assistance to one real player inside one normal-dungeon instance.
     * The assist survives intermediate Eve fights and is cleared at dungeon settlement/exit.
     */
    fun configureDungeonBotAssist(playerId: Long, instanceId: Int, botCount: Int) {
        if (botCount <= 0) {
            dungeonBotAssists.remove(playerId)
            return
        }
        dungeonBotAssists[playerId] = DungeonBotAssist(
            instanceId = instanceId,
            botCount = botCount.coerceAtMost(BattleConstants.MAX_ROW - 1),
        )
    }

    fun clearDungeonBotAssist(playerId: Long) {
        dungeonBotAssists.remove(playerId)
    }


    /**
     * DVT manual catch owner-side gates from FightRoleController.DoClick.
     * Scene NoCatch and Elephant/14034 are intentionally handled separately when their
     * authoritative server-side sources are available.
     */
    private fun installCatchOwnerValidator(battle: BattleInstance) {
        battle.catchOwnerValidator = validator@{ playerId, npcId ->
            val session = battle.playerSessions[playerId] ?: return@validator false
            val pc = petSystem.getCache(session.roleId.toLong()) ?: return@validator false
            // SceneManager.CheckLimit(sceneId, ESceneLimit.NoCatch): NoCatch=7,
            // stored in SceneData.limits[1] as bit 64.
            if ((sceneSetDefs[session.mapId]?.limit1 ?: 0) and SCENE_LIMIT_NO_CATCH_BIT != 0) return@validator false
            if (!petSystem.hasFreeUnlockedFollowSlot(session.roleId.toLong())) return@validator false
            val npcDef = npcDefs[npcId] ?: return@validator false
            // DVT ENpc.Elephant=22 requires learned riding skill 14034.
            if (npcDef.kind == NPC_KIND_ELEPHANT &&
                (skillSystem?.getCache(session.roleId.toLong())?.skills?.get(RIDING_SKILL_ID) ?: 0) <= 0
            ) return@validator false
            // DVT FightRoleController.DoClick only rejects the same NPC
            // in carried followers or Inn.HaveNpc() (inn + warrior repository).
            // Cart.followNpcs is not checked by the client.
            val duplicate = pc.follow.values.any { it.npcId == npcId } ||
                pc.inn.values.any { it.npcId == npcId } ||
                pc.warehouse.any { it.npcId == npcId }
            !duplicate
        }
    }

    /**
     * Tạo trận PvE (sự kiện kích hoạt)
     *
     * @param session người chơi kích hoạt
     * @param fightData cấu hình chiến đấu (tải từ Eve_FightData)
     * @param terrain thông tin địa chất
     */
    suspend fun createPveBattle(
        session: GameSession,
        fightData: EveFightData,
        terrain: Int = 0
    ) {
        if (isInBattle(session.playerId)) {
            logger.warn { "Người chơi ${session.playerId} đang trong trận chiến, không thể mở trận mới" }
            return
        }

        // terrain=0 nghĩa là chưa đặt, fallback về 101 (ffield hợp lệ nhỏ nhất trong CDN)
        val effectiveTerrain = if (terrain == 0) 101 else terrain

        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs)
        val grid = BattleGrid()

        // Thu thập định nghĩa NPC liên quan (npcId + NpcDef)
        val involvedNpcs = mutableListOf<Pair<Int, NpcDef>>()

        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.EVENT,
            terrain = effectiveTerrain,
            eventNo = fightData.eveNo,
            grid = grid,
            skillResolver = skillResolver,
            itemResolver = battleItemResolver,
            involvedNpcDefs = involvedNpcs
        )
        battle.linkToResult = fightData.linkToResult

        // 1. Normal PvE keeps the nine-pet extension. A solo normal team-dungeon
        // may instead reserve one row per missing party member for server-controlled
        // AutomanualPlayer (kind 28) assistants. Only the first deployed pet accompanies
        // the real player in this party-shaped layout so the 5-row DVT grid cannot overflow.
        val charId = session.roleId.toLong()
        val storedDungeonAssist = dungeonBotAssists[session.playerId]
        val dungeonAssist = storedDungeonAssist?.takeIf {
            TeamDungeonBotPolicy.assistMatchesInstance(it.instanceId, session.instanceId) && it.botCount > 0
        }
        if (storedDungeonAssist != null && dungeonAssist == null) {
            dungeonBotAssists.remove(session.playerId, storedDungeonAssist)
        }
        val battlePets = petSystem.getBattleDeployableFollowPets(charId)
        val worldPartyBots = if (dungeonAssist == null) {
            worldPartyBotProvider?.invoke(session).orEmpty()
        } else {
            emptyList()
        }

        if (dungeonAssist != null) {
            val selectedPet = battlePets.firstOrNull()
            val playerCol = if (selectedPet == null) {
                BattleConstants.PLAYER_FRONT_COL
            } else {
                BattleConstants.PLAYER_BACK_COL
            }
            battle.addFighter(createPlayerFighter(session, col = playerCol, row = 0))
            battle.playerSessions[session.playerId] = session

            if (selectedPet != null) {
                val (followSlot, petData) = selectedPet
                npcDefs[petData.npcId]?.let { npcDef ->
                    battle.addFighter(
                        createFollowNpcFighter(
                            npcDef = npcDef,
                            petData = petData,
                            masterId = session.playerId,
                            ownerCharacterId = charId,
                            npcDataId = petData.npcId,
                            followSlot = followSlot,
                            col = BattleConstants.PLAYER_FRONT_COL,
                            row = 0,
                        )
                    )
                }
            }

            repeat(dungeonAssist.botCount.coerceAtMost(BattleConstants.MAX_ROW - 1)) { index ->
                val profile = TeamDungeonBotPolicy.profile(index)
                val botRoleId = nextBotRoleId.getAndDecrement()
                val petDef = npcDefs[profile.petNpcId]
                battle.addFighter(
                    createPlayerFighter(
                        session = session,
                        col = if (petDef == null) BattleConstants.PLAYER_FRONT_COL else BattleConstants.PLAYER_BACK_COL,
                        row = index + 1,
                        kind = EHuman.AUTOMANUAL_PLAYER,
                        roleIdOverride = botRoleId,
                        nameOverride = profile.name,
                        restoreFull = true,
                        hairOverride = ((session.hair + profile.hairOffset - 1) % 8) + 1,
                        color1Xor = profile.color1Xor,
                        color2Xor = profile.color2Xor,
                        equipItemIdsOverride = profile.outfitItemIds.takeIf { it.isNotEmpty() },
                    )
                )
                if (petDef != null) {
                    val npcProfile = NpcBattleProfileCalc.from(petDef, skillDefs)
                    val petData = FollowNpcData(
                        slotIndex = 0,
                        npcId = profile.petNpcId,
                        name = petDef.name,
                        level = npcProfile.level,
                        hp = npcProfile.maxHp,
                        sp = npcProfile.maxSp.toInt(),
                        atk = npcProfile.atk,
                        def = npcProfile.def,
                        agi = npcProfile.agi,
                        intelligence = npcProfile.intelligence,
                        skill1Lv = petDef.skills.getOrNull(0)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill2Lv = petDef.skills.getOrNull(1)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill3Lv = petDef.skills.getOrNull(2)?.let { npcProfile.skillLevels[it] } ?: 0,
                    )
                    battle.addFighter(
                        createFollowNpcFighterInternal(
                            npcDef = petDef,
                            petData = petData,
                            masterId = botRoleId,
                            ownerCharacterId = charId,
                            npcDataId = profile.petNpcId,
                            followSlot = 0,
                            col = BattleConstants.PLAYER_FRONT_COL,
                            row = index + 1,
                            kind = EHuman.AUTOMANUAL_NPC,
                            roleIdOverride = profile.petNpcId.toLong(),
                            restoreFull = true,
                        )
                    )
                }
            }
            logger.info {
                "TeamDungeon bots attached battle=$battleId player=${session.characterName} " +
                    "instance=${session.instanceId} bots=${dungeonAssist.botCount}"
            }
        } else if (worldPartyBots.isNotEmpty()) {
            val selectedPet = battlePets.firstOrNull()
            val playerCol = if (selectedPet == null) BattleConstants.PLAYER_FRONT_COL else BattleConstants.PLAYER_BACK_COL
            battle.addFighter(createPlayerFighter(session, col = playerCol, row = 0))
            battle.playerSessions[session.playerId] = session

            if (selectedPet != null) {
                val (followSlot, petData) = selectedPet
                npcDefs[petData.npcId]?.let { npcDef ->
                    battle.addFighter(
                        createFollowNpcFighter(
                            npcDef = npcDef,
                            petData = petData,
                            masterId = session.playerId,
                            ownerCharacterId = charId,
                            npcDataId = petData.npcId,
                            followSlot = followSlot,
                            col = BattleConstants.PLAYER_FRONT_COL,
                            row = 0,
                        )
                    )
                }
            }

            worldPartyBots.take(BattleConstants.MAX_ROW - 1).forEachIndexed { index, profile ->
                val petDef = npcDefs[profile.petNpcId]
                battle.addFighter(
                    createPlayerFighter(
                        session = session,
                        col = if (petDef == null) BattleConstants.PLAYER_FRONT_COL else BattleConstants.PLAYER_BACK_COL,
                        row = index + 1,
                        kind = EHuman.AUTOMANUAL_PLAYER,
                        roleIdOverride = profile.roleId,
                        nameOverride = profile.name,
                        restoreFull = true,
                        hairOverride = profile.hair,
                        color1Xor = session.color1 xor profile.color1,
                        color2Xor = session.color2 xor profile.color2,
                        equipItemIdsOverride = profile.equipItemIds,
                    )
                )
                if (petDef != null) {
                    val npcProfile = NpcBattleProfileCalc.from(petDef, skillDefs)
                    val petData = FollowNpcData(
                        slotIndex = 0,
                        npcId = profile.petNpcId,
                        name = petDef.name,
                        level = npcProfile.level,
                        hp = npcProfile.maxHp,
                        sp = npcProfile.maxSp.toInt(),
                        atk = npcProfile.atk,
                        def = npcProfile.def,
                        agi = npcProfile.agi,
                        intelligence = npcProfile.intelligence,
                        skill1Lv = petDef.skills.getOrNull(0)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill2Lv = petDef.skills.getOrNull(1)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill3Lv = petDef.skills.getOrNull(2)?.let { npcProfile.skillLevels[it] } ?: 0,
                    )
                    battle.addFighter(
                        createFollowNpcFighterInternal(
                            npcDef = petDef,
                            petData = petData,
                            masterId = profile.roleId,
                            ownerCharacterId = charId,
                            npcDataId = profile.petNpcId,
                            followSlot = 0,
                            col = BattleConstants.PLAYER_FRONT_COL,
                            row = index + 1,
                            kind = EHuman.AUTOMANUAL_NPC,
                            roleIdOverride = profile.petNpcId.toLong(),
                            restoreFull = true,
                        )
                    )
                }
            }
            logger.info {
                "WorldSimBot party attached battle=" + battleId + " player=" + session.characterName +
                    " bots=" + worldPartyBots.size
            }
        } else {
            val hasBattlePets = battlePets.isNotEmpty()
            val playerCol = if (hasBattlePets) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
            val playerFighter = createPlayerFighter(session, col = playerCol)
            battle.addFighter(playerFighter)
            battle.playerSessions[session.playerId] = session

            if (hasBattlePets) {
                addBattleFollowNpcs(
                    session, battle,
                    frontCol = BattleConstants.PLAYER_FRONT_COL,
                    backCol = BattleConstants.PLAYER_BACK_COL,
                    pets = battlePets
                )
            }
        }

        // 2. Eve_FightData carries authoritative LocationPos 1..10.
        // Duplicate positions are sequential reinforcements for that slot.
        addEveEnemySide(
            fightData.leftEnemies,
            battle,
            battleId,
            involvedNpcs,
            frontCol = BattleConstants.ENEMY_FRONT_COL,
            backCol = BattleConstants.ENEMY_BACK_COL,
        )

        // RightEnemyAy uses the same per-side LocationPos contract on the player side.
        addEveEnemySide(
            fightData.rightEnemies,
            battle,
            battleId,
            involvedNpcs,
            frontCol = BattleConstants.PLAYER_FRONT_COL,
            backCol = BattleConstants.PLAYER_BACK_COL,
        )

        // 3. Đăng ký trận đấu
        battles[battleId] = battle
        playerBattleMap[session.playerId] = battleId
        session.battleId = battleId

        logger.info { "Tạo trận $battleId: người chơi ${session.characterName} vs ${involvedNpcs.size} kẻ địch (sự kiện ${fightData.eveNo}) leftEnemies=${fightData.leftEnemies.size} rightEnemies=${fightData.rightEnemies.size}" }

        installCatchOwnerValidator(battle)

        // 4. Gửi gói tin khởi tạo chiến đấu
        sendBattleInit(battle)

        // 5. Khởi động vòng lặp hiệp đấu
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * Headless PvE for autonomous WorldSimBot encounters.
     * Uses the normal battle core but has no real GameSession participant,
     * sends no battle packets, and grants no rewards/capture.
     */
    suspend fun createWorldSimBotPveBattle(
        template: GameSession,
        profile: WorldSimBotBattleProfile,
        fightData: EveFightData,
        terrain: Int = 101,
        onFinished: (Boolean, Int) -> Unit,
    ): Boolean {
        val involvedNpcs = mutableListOf<Pair<Int, NpcDef>>()
        val battleId = nextBattleId.getAndIncrement()
        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.EVENT,
            terrain = if (terrain == 0) 101 else terrain,
            eventNo = fightData.eveNo,
            grid = BattleGrid(),
            skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs),
            itemResolver = battleItemResolver,
            involvedNpcDefs = involvedNpcs,
            suppressGenericRewards = true,
        )
        battle.linkToResult = fightData.linkToResult

        val petDef = npcDefs[profile.petNpcId]
        battle.addFighter(
            createPlayerFighter(
                session = template,
                col = if (petDef == null) BattleConstants.PLAYER_FRONT_COL else BattleConstants.PLAYER_BACK_COL,
                row = 0,
                kind = EHuman.AUTOMANUAL_PLAYER,
                roleIdOverride = profile.roleId,
                nameOverride = profile.name,
                restoreFull = true,
                hairOverride = profile.hair,
                color1Xor = template.color1 xor profile.color1,
                color2Xor = template.color2 xor profile.color2,
                equipItemIdsOverride = profile.equipItemIds.takeIf { it.isNotEmpty() },
            )
        )

        if (petDef != null) {
            val npcProfile = NpcBattleProfileCalc.from(petDef, skillDefs)
            val petData = FollowNpcData(
                slotIndex = 0,
                npcId = profile.petNpcId,
                name = petDef.name,
                level = npcProfile.level,
                hp = npcProfile.maxHp,
                sp = npcProfile.maxSp.toInt(),
                atk = npcProfile.atk,
                def = npcProfile.def,
                agi = npcProfile.agi,
                intelligence = npcProfile.intelligence,
                skill1Lv = petDef.skills.getOrNull(0)?.let { npcProfile.skillLevels[it] } ?: 0,
                skill2Lv = petDef.skills.getOrNull(1)?.let { npcProfile.skillLevels[it] } ?: 0,
                skill3Lv = petDef.skills.getOrNull(2)?.let { npcProfile.skillLevels[it] } ?: 0,
            )
            battle.addFighter(
                createFollowNpcFighterInternal(
                    npcDef = petDef,
                    petData = petData,
                    masterId = profile.roleId,
                    ownerCharacterId = template.roleId.toLong(),
                    npcDataId = profile.petNpcId,
                    followSlot = 0,
                    col = BattleConstants.PLAYER_FRONT_COL,
                    row = 0,
                    kind = EHuman.AUTOMANUAL_NPC,
                    roleIdOverride = nextBotRoleId.getAndDecrement(),
                    restoreFull = true,
                )
            )
        }

        addEveEnemySide(
            fightData.leftEnemies,
            battle,
            battleId,
            involvedNpcs,
            frontCol = BattleConstants.ENEMY_FRONT_COL,
            backCol = BattleConstants.ENEMY_BACK_COL,
        )
        addEveEnemySide(
            fightData.rightEnemies,
            battle,
            battleId,
            involvedNpcs,
            frontCol = BattleConstants.PLAYER_FRONT_COL,
            backCol = BattleConstants.PLAYER_BACK_COL,
        )
        if (battle.grid.getAliveEnemySide().isEmpty()) return false

        battle.catchOwnerValidator = { _, _ -> false }
        battles[battleId] = battle
        headlessWorldBotCallbacks[battleId] = onFinished
        logger.info { "WorldSimBot headless PvE start battle=" + battleId +
            " bot=" + profile.name + " fight=" + fightData.eveNo +
            " enemies=" + involvedNpcs.size }
        scope.launch { runBattleLoop(battle) }
        return true
    }

    /**
     * DVT WeekDungeon battle style 24 for Bách Chiến / Soul weekly.
     *
     * Roster membership is DVT data-backed; placement uses the documented offline
     * reconstruction: first five enemy ids -> col0 rows0..4, next five -> col1.
     */
    suspend fun createWeeklyDungeonBattle(
        room: DungeonRoomState,
        enemyIds: List<Int>,
        objective: DvtWeeklyRoundObjective.Objective,
        botCount: Int = 0,
    ): Boolean {
        if (!room.started || room.members.isEmpty() || room.members.size > BattleConstants.MAX_ROW) return false
        if (enemyIds.size != BattleConstants.MAX_ROW * 2 || enemyIds.distinct().size != enemyIds.size) return false
        if (room.members.values.any { isInBattle(it.session.playerId) }) return false
        if (enemyIds.any { it !in npcDefs }) return false

        val battleId = nextBattleId.getAndIncrement()
        val involvedNpcs = mutableListOf<Pair<Int, NpcDef>>()
        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.EVENT,
            warStyle = EWarStyle.WEEK_DUNGEON,
            terrain = 101,
            eventNo = 0,
            grid = BattleGrid(),
            skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs),
            itemResolver = battleItemResolver,
            involvedNpcDefs = involvedNpcs,
            dungeonId = room.dungeonId,
            weeklyMaxRound = objective.maxRound ?: 0,
            weeklySurviveRound = objective.minRound ?: 0,
            suppressGenericRewards = true,
        )

        for ((row, member) in room.members.values.withIndex()) {
            val session = member.session
            val selectedPet = petSystem
                .getBattleDeployableFollowPets(session.roleId.toLong())
                .firstOrNull()
            val playerCol =
                if (selectedPet == null) BattleConstants.PLAYER_FRONT_COL
                else BattleConstants.PLAYER_BACK_COL

            battle.addFighter(createPlayerFighter(session, col = playerCol, row = row))
            battle.playerSessions[session.playerId] = session

            if (selectedPet != null) {
                val (followSlot, petData) = selectedPet
                val npcDef = npcDefs[petData.npcId] ?: return false
                battle.addFighter(
                    createFollowNpcFighter(
                        npcDef = npcDef,
                        petData = petData,
                        masterId = session.playerId,
                        ownerCharacterId = session.roleId.toLong(),
                        npcDataId = petData.npcId,
                        followSlot = followSlot,
                        col = BattleConstants.PLAYER_FRONT_COL,
                        row = row,
                    )
                )
            }
        }

        if (room.members.size == 1 && botCount > 0) {
            val template = room.members.values.first().session
            val charId = template.roleId.toLong()
            repeat(botCount.coerceAtMost(BattleConstants.MAX_ROW - room.members.size)) { index ->
                val row = room.members.size + index
                val profile = TeamDungeonBotPolicy.profile(index)
                val botRoleId = nextBotRoleId.getAndDecrement()
                val petDef = npcDefs[profile.petNpcId]
                battle.addFighter(
                    createPlayerFighter(
                        session = template,
                        col = if (petDef == null) BattleConstants.PLAYER_FRONT_COL else BattleConstants.PLAYER_BACK_COL,
                        row = row,
                        kind = EHuman.AUTOMANUAL_PLAYER,
                        roleIdOverride = botRoleId,
                        nameOverride = profile.name,
                        restoreFull = true,
                        hairOverride = ((template.hair + profile.hairOffset - 1) % 8) + 1,
                        color1Xor = profile.color1Xor,
                        color2Xor = profile.color2Xor,
                        equipItemIdsOverride = profile.outfitItemIds.takeIf { it.isNotEmpty() },
                    )
                )
                if (petDef != null) {
                    val npcProfile = NpcBattleProfileCalc.from(petDef, skillDefs)
                    val petData = FollowNpcData(
                        slotIndex = 0,
                        npcId = profile.petNpcId,
                        name = petDef.name,
                        level = npcProfile.level,
                        hp = npcProfile.maxHp,
                        sp = npcProfile.maxSp.toInt(),
                        atk = npcProfile.atk,
                        def = npcProfile.def,
                        agi = npcProfile.agi,
                        intelligence = npcProfile.intelligence,
                        skill1Lv = petDef.skills.getOrNull(0)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill2Lv = petDef.skills.getOrNull(1)?.let { npcProfile.skillLevels[it] } ?: 0,
                        skill3Lv = petDef.skills.getOrNull(2)?.let { npcProfile.skillLevels[it] } ?: 0,
                    )
                    battle.addFighter(
                        createFollowNpcFighterInternal(
                            npcDef = petDef,
                            petData = petData,
                            masterId = botRoleId,
                            ownerCharacterId = charId,
                            npcDataId = profile.petNpcId,
                            followSlot = 0,
                            col = BattleConstants.PLAYER_FRONT_COL,
                            row = row,
                            kind = EHuman.AUTOMANUAL_NPC,
                            roleIdOverride = profile.petNpcId.toLong(),
                            restoreFull = true,
                        )
                    )
                }
            }
        }

        for ((index, npcId) in enemyIds.withIndex()) {
            val npcDef = npcDefs.getValue(npcId)
            involvedNpcs += npcId to npcDef
            battle.addFighter(
                createNpcFighter(
                    npcDef = npcDef,
                    col = if (index < BattleConstants.MAX_ROW) {
                        BattleConstants.ENEMY_FRONT_COL
                    } else {
                        BattleConstants.ENEMY_BACK_COL
                    },
                    row = index % BattleConstants.MAX_ROW,
                    battleId = battleId,
                    npcDataId = npcId,
                )
            )
        }

        battles[battleId] = battle
        for ((playerId, session) in battle.playerSessions) {
            playerBattleMap[playerId] = battleId
            session.battleId = battleId
        }
        battle.catchOwnerValidator = { _, _ -> false }

        sendBattleInit(battle)
        logger.info {
            "Tạo WeekDungeon style24 dungeon=${room.dungeonId} battle=$battleId " +
                "members=${room.members.size} maxRound=${objective.maxRound} minRound=${objective.minRound}"
        }
        scope.launch { runBattleLoop(battle) }
        return true
    }

    /**
     * Tạo trận NPC trên bản đồ (PK / luyện tập với bù nhìn gỗ)
     *
     * @param session người chơi khởi xướng trận đấu
     * @param npcId số hiệu NPC
     * @param placementIndex index bố trí trên bản đồ
     */
    suspend fun createMapNpcBattle(
        session: GameSession,
        npcId: Int,
        placementIndex: Int = 0
    ) {
        if (isInBattle(session.playerId)) {
            logger.warn { "Người chơi ${session.playerId} đang trong trận chiến, không thể mở trận mới" }
            return
        }

        val npcDef = npcDefs[npcId]
        if (npcDef == null) {
            logger.warn { "createMapNpcBattle: không tìm thấy định nghĩa NPC npcId=$npcId" }
            return
        }

        val effectiveTerrain = 101
        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs)
        val grid = BattleGrid()

        val involvedNpcs = mutableListOf<Pair<Int, NpcDef>>()
        involvedNpcs.add(npcId to npcDef)

        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.PK_MAP_NPC,
            terrain = effectiveTerrain,
            eventNo = 0,
            grid = grid,
            skillResolver = skillResolver,
            itemResolver = battleItemResolver,
            involvedNpcDefs = involvedNpcs
        )

        // 1. Nine-pet extension: roster ownership is always open; battle progression
        // controls how many logical carried slots may enter this battle.
        val charId = session.roleId.toLong()
        val battlePets = petSystem.getBattleDeployableFollowPets(charId)
        val hasBattlePets = battlePets.isNotEmpty()
        val playerCol = if (hasBattlePets) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val playerFighter = createPlayerFighter(session, col = playerCol)
        battle.addFighter(playerFighter)
        battle.playerSessions[session.playerId] = session

        // 1.5 Fill the remaining player-side grid slots with deployed followers.
        if (hasBattlePets) {
            addBattleFollowNpcs(
                session, battle,
                frontCol = BattleConstants.PLAYER_FRONT_COL,
                backCol = BattleConstants.PLAYER_BACK_COL,
                pets = battlePets
            )
        }

        // 2. Thêm NPC phe địch vào bàn cờ (col=BattleConstants.ENEMY_FRONT_COL, row=BattleConstants.CENTER_ROW)
        val enemyFighter = createNpcFighter(npcDef, BattleConstants.ENEMY_FRONT_COL, BattleConstants.CENTER_ROW, battleId, npcId)
        battle.addFighter(enemyFighter)

        // 3. Đăng ký trận đấu
        battles[battleId] = battle
        playerBattleMap[session.playerId] = battleId
        session.battleId = battleId

        logger.info { "Tạo trận $battleId: người chơi ${session.characterName} vs MapNpc ${npcDef.name} (id=$npcId, placement=$placementIndex)" }

        installCatchOwnerValidator(battle)

        // 4. Gửi gói tin khởi tạo chiến đấu
        sendBattleInit(battle)

        // 5. Khởi động vòng lặp hiệp đấu
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * Tạo trận PvP (người chơi PK)
     *
     * @param attacker người chơi khởi xướng PK
     * @param defender người chơi bị PK
     */
    suspend fun createPvpBattle(attacker: GameSession, defender: GameSession) {
        if (isInBattle(attacker.playerId) || isInBattle(defender.playerId)) {
            logger.warn { "Tạo PVP thất bại: người chơi đang trong trận chiến" }
            return
        }

        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs)
        val grid = BattleGrid()

        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.PK_PLAYERS,
            terrain = 101,
            eventNo = 0,
            grid = grid,
            skillResolver = skillResolver,
            itemResolver = battleItemResolver,
            involvedNpcDefs = emptyList()
        )

        // Phe tấn công → bên phải. Player reserves the back-center slot; followers
        // fill the remaining cells according to battle deployment progression.
        val atkCharId = attacker.roleId.toLong()
        val atkBattlePets = petSystem.getBattleDeployableFollowPets(atkCharId)
        val atkHasPets = atkBattlePets.isNotEmpty()
        val atkCol = if (atkHasPets) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val attackerFighter = createPlayerFighter(attacker, col = atkCol)
        battle.addFighter(attackerFighter)
        battle.playerSessions[attacker.playerId] = attacker
        if (atkHasPets) {
            addBattleFollowNpcs(
                attacker, battle,
                BattleConstants.PLAYER_FRONT_COL, BattleConstants.PLAYER_BACK_COL,
                atkBattlePets
            )
        }

        // Bên phòng thủ → bên trái, cùng quy tắc deployment.
        val defCharId = defender.roleId.toLong()
        val defBattlePets = petSystem.getBattleDeployableFollowPets(defCharId)
        val defHasPets = defBattlePets.isNotEmpty()
        val defCol = if (defHasPets) BattleConstants.ENEMY_BACK_COL else BattleConstants.ENEMY_FRONT_COL
        val defenderFighter = createPlayerFighter(defender, col = defCol, kind = EHuman.PLAYERS)
        battle.addFighter(defenderFighter)
        battle.playerSessions[defender.playerId] = defender
        if (defHasPets) {
            addBattleFollowNpcs(
                defender, battle,
                BattleConstants.ENEMY_FRONT_COL, BattleConstants.ENEMY_BACK_COL,
                defBattlePets
            )
        }

        // Đăng ký trận đấu
        battles[battleId] = battle
        playerBattleMap[attacker.playerId] = battleId
        playerBattleMap[defender.playerId] = battleId
        attacker.battleId = battleId
        defender.battleId = battleId

        logger.info { "Tạo trận PVP $battleId: ${attacker.characterName} vs ${defender.characterName}" }

        installCatchOwnerValidator(battle)

        // Gửi gói tin khởi tạo chiến đấu
        sendBattleInit(battle)

        // Khởi động vòng lặp hiệp đấu
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * Tạo trận arena VS/Chaos/Mix có thể lấp bot sau timeout matchmaking.
     *
     * Bot dùng EHuman.AutomanualPlayer=28 và pet bot dùng 29, đúng wire contract DVT.
     * AI là policy offline custom; trận này tuyệt đối không dùng reward/drop PvE.
     */
    suspend fun createArenaBotBattle(
        mode: PvpBotMode,
        rightReal: List<GameSession>,
        leftReal: List<GameSession>,
        rightBotCount: Int,
        leftBotCount: Int,
    ): Boolean {
        val allReal = (rightReal + leftReal).distinctBy { it.playerId }
        if (allReal.isEmpty()) return false
        if (allReal.any { isInBattle(it.playerId) }) return false
        if (rightReal.size + rightBotCount !in 1..mode.sideSize) return false
        if (leftReal.size + leftBotCount !in 1..mode.sideSize) return false
        if (rightReal.size + rightBotCount != mode.sideSize ||
            leftReal.size + leftBotCount != mode.sideSize
        ) return false

        val battleId = nextBattleId.getAndIncrement()
        val battle = BattleInstance(
            battleId = battleId,
            warType = EWar.VS,
            warStyle = mode.warStyle,
            terrain = 101,
            eventNo = 0,
            grid = BattleGrid(),
            skillResolver = BattleSkillResolver(skillDefs, exclusiveSkillDefs),
            itemResolver = battleItemResolver,
            involvedNpcDefs = emptyList(),
            suppressGenericRewards = true,
        )

        suspend fun addReal(session: GameSession, right: Boolean, row: Int) {
            val charId = session.roleId.toLong()
            val selectedPet = petSystem.getBattleDeployableFollowPets(charId).firstOrNull()
            val frontCol = if (right) BattleConstants.PLAYER_FRONT_COL else BattleConstants.ENEMY_FRONT_COL
            val backCol = if (right) BattleConstants.PLAYER_BACK_COL else BattleConstants.ENEMY_BACK_COL
            val kind = if (right) EHuman.PLAYER else EHuman.PLAYERS
            val player = createPlayerFighter(
                session = session,
                col = if (selectedPet == null) frontCol else backCol,
                row = row,
                kind = kind,
            )
            battle.addFighter(player)
            battle.playerSessions[session.playerId] = session

            if (selectedPet != null) {
                val (followSlot, petData) = selectedPet
                val npcDef = npcDefs[petData.npcId]
                if (npcDef != null) {
                    battle.addFighter(
                        createFollowNpcFighter(
                            npcDef = npcDef,
                            petData = petData,
                            masterId = session.playerId,
                            ownerCharacterId = charId,
                            npcDataId = petData.npcId,
                            followSlot = followSlot,
                            col = frontCol,
                            row = row,
                        )
                    )
                }
            }
        }

        suspend fun addBot(template: GameSession, right: Boolean, row: Int, ordinal: Int) {
            val botRoleId = nextBotRoleId.getAndDecrement()
            val charId = template.roleId.toLong()
            val selectedPet = petSystem.getBattleDeployableFollowPets(charId).firstOrNull()
            val frontCol = if (right) BattleConstants.PLAYER_FRONT_COL else BattleConstants.ENEMY_FRONT_COL
            val backCol = if (right) BattleConstants.PLAYER_BACK_COL else BattleConstants.ENEMY_BACK_COL
            val bot = createPlayerFighter(
                session = template,
                col = if (selectedPet == null) frontCol else backCol,
                row = row,
                kind = EHuman.AUTOMANUAL_PLAYER,
                roleIdOverride = botRoleId,
                nameOverride = "Đấu Sĩ $ordinal",
                restoreFull = true,
            )
            battle.addFighter(bot)

            if (selectedPet != null) {
                val (_, petData) = selectedPet
                val npcDef = npcDefs[petData.npcId]
                if (npcDef != null) {
                    battle.addFighter(
                        createFollowNpcFighterInternal(
                            npcDef = npcDef,
                            petData = petData,
                            masterId = botRoleId,
                            ownerCharacterId = charId,
                            npcDataId = petData.npcId,
                            followSlot = 0,
                            col = frontCol,
                            row = row,
                            kind = EHuman.AUTOMANUAL_NPC,
                            roleIdOverride = nextBotRoleId.getAndDecrement(),
                            restoreFull = true,
                        )
                    )
                }
            }
        }

        rightReal.forEachIndexed { row, session -> addReal(session, true, row) }
        leftReal.forEachIndexed { row, session -> addReal(session, false, row) }

        val templates = allReal.ifEmpty { return false }
        repeat(rightBotCount) { i ->
            addBot(templates[i % templates.size], true, rightReal.size + i, i + 1)
        }
        repeat(leftBotCount) { i ->
            addBot(
                templates[(rightBotCount + i) % templates.size],
                false,
                leftReal.size + i,
                rightBotCount + i + 1,
            )
        }

        battles[battleId] = battle
        for ((playerId, session) in battle.playerSessions) {
            playerBattleMap[playerId] = battleId
            session.battleId = battleId
        }
        battle.catchOwnerValidator = { _, _ -> false }

        sendBattleInit(battle)
        logger.info {
            "ArenaBot battle=$battleId mode=$mode " +
                "rightReal=${rightReal.map { it.characterName }} rightBots=$rightBotCount " +
                "leftReal=${leftReal.map { it.characterName }} leftBots=$leftBotCount"
        }
        scope.launch { runBattleLoop(battle) }
        return true
    }

    /**
     * Xử lý lệnh chiến đấu của người chơi (bao gồm tướng)
     *
     * @param playerId ID người chơi gửi lệnh
     * @param srcCol cột của bên ra chiêu (dùng để định vị nhân vật, roleId của tướng ≠ playerId)
     * @param srcRow hàng của bên ra chiêu
     * @param action hành động được chọn
     */
    fun handlePlayerAction(playerId: Long, srcCol: Int, srcRow: Int, action: BattleAction): Boolean {
        val battleId = playerBattleMap[playerId] ?: return false
        val battle = battles[battleId] ?: return false

        if (battle.state != BattleInstance.State.WAITING_COMMANDS) {
            logger.debug { "Trận $battleId: hiện không nhận lệnh (state=${battle.state})" }
            return false
        }

        return battle.setPlayerAction(playerId, srcCol, srcRow, action)
    }

    /**
     * Lấy trận đấu mà người chơi đang ở
     */
    fun getPlayerBattle(playerId: Long): BattleInstance? {
        val battleId = playerBattleMap[playerId] ?: return null
        return battles[battleId]
    }

    /**
     * Người chơi có đang trong trận đấu hay không
     */
    fun isInBattle(playerId: Long): Boolean = playerBattleMap.containsKey(playerId)

    /**
     * Tham gia trận đấu đang diễn ra (tham chiến)
     *
     * Được gọi khi người chơi bấm nút "tham chiến" trên nhân vật đang chiến đấu.
     * Người tham gia được xếp vào cùng phe với mục tiêu.
     *
     * @param joiner session người chơi cần tham gia
     * @param battle instance trận đấu cần tham gia
     * @param targetPlayerId ID người chơi mục tiêu bị bấm (dùng để phán định phe đảng)
     * @return true=vào thành công, false=vào thất bại (tổ đội đầy)
     */
    suspend fun joinBattle(joiner: GameSession, battle: BattleInstance, targetPlayerId: Long): Boolean {
        // 1. Phán định mục tiêu ở bên nào
        val targetOnRight = battle.isPlayerOnRightSide(targetPlayerId)
        if (targetOnRight == null) {
            logger.warn { "joinBattle: mục tiêu $targetPlayerId không ở trong trận ${battle.battleId}" }
            return false
        }

        val (frontCol, backCol) = if (targetOnRight) {
            BattleConstants.PLAYER_FRONT_COL to BattleConstants.PLAYER_BACK_COL
        } else {
            BattleConstants.ENEMY_FRONT_COL to BattleConstants.ENEMY_BACK_COL
        }

        // 2. Carried pets are battle-progressed; joining always prioritizes the player
        // and then fills any remaining side slots with as many eligible pets as fit.
        val charId = joiner.roleId.toLong()
        val battlePets = petSystem.getBattleDeployableFollowPets(charId)
        val hasBattlePets = battlePets.isNotEmpty()

        // 3. At least one grid cell is required for the joining player.
        val occupied = battle.grid.countOccupied(frontCol, backCol)
        if (occupied >= BattleConstants.MAX_ROW * 2) {
            logger.info { "joinBattle: trận ${battle.battleId} đội đã đầy (${occupied}/10), không thể tham gia" }
            return false
        }

        // 4. Đặt bản thân người chơi
        val kind = if (targetOnRight) EHuman.PLAYER else EHuman.PLAYERS
        val playerCol = if (hasBattlePets) backCol else frontCol
        val playerSlot = battle.grid.findEmptySlot(
            if (hasBattlePets) backCol else frontCol,
            if (hasBattlePets) frontCol else backCol
        )
        if (playerSlot == null) {
            logger.info { "joinBattle: trận ${battle.battleId} không tìm thấy ô trống" }
            return false
        }

        val joinerFighter = createPlayerFighter(joiner, col = playerSlot.first, kind = kind)
        // Ghi đè row (findEmptySlot đã quyết định vị trí)
        joinerFighter.row = playerSlot.second
        battle.addFighter(joinerFighter)
        battle.playerSessions[joiner.playerId] = joiner

        // 5. Fill remaining cells with this player's battle-eligible followers.
        if (hasBattlePets) {
            addBattleFollowNpcs(joiner, battle, frontCol, backCol, battlePets)
        }

        // 6. Đăng ký
        playerBattleMap[joiner.playerId] = battle.battleId
        joiner.battleId = battle.battleId

        // 7. Tuần tự hoá dữ liệu nhân vật mới tham gia (cho người chơi hiện có xem, S:011-005)
        val newFighters = battle.getAllFighters().filter { f ->
            f.roleId == joiner.playerId || f.masterId == joiner.playerId
        }
        val newRoleDataList = newFighters.map { fighter ->
            BattleRoleData(
                warType = battle.warType,
                humanKind = fighter.kind,
                roleId = fighter.roleId,
                npcId = fighter.npcId,
                masterId = fighter.masterId,
                col = fighter.col,
                row = fighter.row,
                maxHp = fighter.maxHp,
                maxSp = fighter.maxSp,
                hp = fighter.hp,
                sp = fighter.sp,
                level = fighter.level,
                upgradeLv = fighter.upgradeLv,
                element = fighter.element,
                name = fighter.name,
                sex = fighter.sex,
                face = fighter.face,
                hair = fighter.hair,
                color1 = fighter.color1,
                color2 = fighter.color2,
                turn = fighter.turn,
                career = fighter.career,
                equipItemIds = fighter.equipItemIds,
                outfitItemIds = fighter.outfitItemIds,
                soulEquipId = fighter.soulEquipId
            )
        }
        val newRoleBytes = BattleRoleSerializer.serializeAll(newRoleDataList)

        // 8. Gửi toàn bộ thông tin chiến đấu cho người tham gia (S:011-250 + S:011-010)
        sendBattleInit(battle, onlyTo = joiner)

        // 9. Thông báo cho người chơi hiện có: nhân vật mới tham gia (S:011-005)
        for (session in battle.playerSessions.values) {
            if (session.playerId != joiner.playerId) {
                responseSender.sendBattleJoinRoleAppear(session, newRoleBytes)
            }
        }

        // 10. Phát rộng bản đồ: cập nhật loại chiến đấu của người chơi trong cảnh (S:011-004)
        val warTypeWriter = PacketWriter(12)
            .writeByte(EHuman.PLAYER)
            .writeLongLE(joiner.playerId)
            .writeShortLE(0)
            .writeByte(EWar.JOIN)
        val warTypeEntries = warTypeWriter.toByteArray()
        // Thông báo mọi người chơi cùng bản đồ (bao gồm cả chính người tham gia)
        // Gói tin này do tầng Handler phát rộng qua worldManager, ở đây chưa xử lý
        // (BattleManager không giữ tham chiếu worldManager)

        logger.info { "Trận ${battle.battleId}: ${joiner.characterName} tham chiến (${if (targetOnRight) "右方" else "左方"}）" }
        return true
    }

    /**
     * Xử lý người chơi mất kết nối (mất kết nối khi đang chiến đấu)
     */
    fun handleDisconnect(playerId: Long) {
        val battleId = playerBattleMap.remove(playerId) ?: return
        val battle = battles[battleId] ?: return

        battle.playerSessions.remove(playerId)
        logger.info { "Trận $battleId: người chơi $playerId mất kết nối và rời đi" }

        // Nếu không còn người chơi nào, kết thúc trận đấu
        if (battle.playerSessions.isEmpty()) {
            battle.state = BattleInstance.State.FINISHED
        }
    }

    // ── Phương thức private ──

    /**
     * Place one Eve_FightData side using authoritative LocationPos when available.
     * Repeated slots are queued as reinforcements and are not present in S11-250.
     *
     * LocationPos=0 still exists in legacy data and its server semantics are not
     * authoritative yet. Those fights keep the old centered placement, capped to
     * the 10 physical cells so malformed/unknown data can never crash BattleGrid.
     */
    private fun addEveEnemySide(
        enemies: List<EveFightEnemy>,
        battle: BattleInstance,
        battleId: Long,
        involvedNpcs: MutableList<Pair<Int, NpcDef>>,
        frontCol: Int,
        backCol: Int,
    ) {
        if (enemies.isEmpty()) return

        val plan = DvtEveBattlePlacement.plan(enemies, frontCol, backCol)
        if (plan == null) {
            if (enemies.size > BattleConstants.MAX_ROW * 2) {
                logger.warn {
                    "Trận $battleId: Eve side có ${enemies.size} record nhưng LocationPos chưa authoritative; " +
                        "chỉ spawn 10 record đầu để tránh tràn BattleGrid"                }
            }
            addCenteredEnemies(
                enemies.take(BattleConstants.MAX_ROW * 2),
                battle,
                battleId,
                involvedNpcs,
                frontCol,
                backCol,
            )
            return
        }

        for (enemy in enemies) {
            val npcDef = npcDefs[enemy.npcId]
            if (npcDef != null) {
                involvedNpcs += enemy.npcId to npcDef
            } else {
                logger.warn { "Trận $battleId: không tìm thấy định nghĩa NPC npcId=${enemy.npcId}" }
            }
        }

        for (placed in plan.initial) {
            val npcDef = npcDefs[placed.enemy.npcId] ?: continue
            battle.addFighter(
                createNpcFighter(
                    npcDef = npcDef,
                    col = placed.slot.col,
                    row = placed.slot.row,
                    battleId = battleId,
                    npcDataId = placed.enemy.npcId,
                )
            )
        }

        if (plan.pending.isNotEmpty()) {
            val bySlot = pendingEveEnemies.computeIfAbsent(battleId) { LinkedHashMap() }
            for ((slot, queue) in plan.pending) {
                val valid = queue.filter { it.npcId in npcDefs }
                if (valid.isNotEmpty()) {
                    bySlot[slot] = ArrayDeque(valid)
                }
            }
            if (bySlot.isEmpty()) pendingEveEnemies.remove(battleId)
        }
    }

    /**
     * Xếp kẻ địch vào hàng trước trước rồi hàng sau, trong mỗi hàng sắp canh giữa
     *
     * Hàng trước tối đa MAX_ROW (5) con, tràn sang hàng sau.
     * Công thức canh giữa: startRow = (MAX_ROW - count) / 2, mỗi con row = startRow + index
     */
    private fun addCenteredEnemies(
        enemies: List<EveFightEnemy>,
        battle: BattleInstance,
        battleId: Long,
        involvedNpcs: MutableList<Pair<Int, NpcDef>>,
        frontCol: Int = BattleConstants.ENEMY_FRONT_COL,
        backCol: Int = BattleConstants.ENEMY_BACK_COL
    ) {
        val frontEnemies = enemies.take(BattleConstants.MAX_ROW)
        val backEnemies = enemies
            .drop(BattleConstants.MAX_ROW)
            .take(BattleConstants.MAX_ROW)
        addColumnCentered(frontEnemies, frontCol, battle, battleId, involvedNpcs)
        addColumnCentered(backEnemies, backCol, battle, battleId, involvedNpcs)
    }

    /**
     * Bố trí canh giữa cho một hàng kẻ địch
     */
    private fun addColumnCentered(
        enemies: List<EveFightEnemy>,
        col: Int,
        battle: BattleInstance,
        battleId: Long,
        involvedNpcs: MutableList<Pair<Int, NpcDef>>
    ) {
        if (enemies.isEmpty()) return
        val count = enemies.size
        val startRow = (BattleConstants.MAX_ROW - count) / 2
        enemies.forEachIndexed { index, enemy ->
            val npcDef = npcDefs[enemy.npcId]
            if (npcDef == null) {
                logger.warn { "Trận $battleId: không tìm thấy định nghĩa NPC npcId=${enemy.npcId}" }
                return@forEachIndexed
            }
            involvedNpcs.add(enemy.npcId to npcDef)
            val npcFighter = createNpcFighter(npcDef, col, startRow + index, battleId, enemy.npcId)
            battle.addFighter(npcFighter)
        }
    }

    private fun toBattleRoleData(battle: BattleInstance, fighter: BattleFighter): BattleRoleData =
        BattleRoleData(
            warType = battle.warType,
            humanKind = fighter.kind,
            roleId = fighter.roleId,
            npcId = fighter.npcId,
            masterId = fighter.masterId,
            col = fighter.col,
            row = fighter.row,
            maxHp = fighter.maxHp,
            maxSp = fighter.maxSp,
            hp = fighter.hp,
            sp = fighter.sp,
            level = fighter.level,
            upgradeLv = fighter.upgradeLv,
            element = fighter.element,
            name = fighter.name,
            sex = fighter.sex,
            face = fighter.face,
            hair = fighter.hair,
            color1 = fighter.color1,
            color2 = fighter.color2,
            turn = fighter.turn,
            career = fighter.career,
            equipItemIds = fighter.equipItemIds,
            outfitItemIds = fighter.outfitItemIds,
            soulEquipId = fighter.soulEquipId,
        )

    /**
     * Gửi gói tin khởi tạo chiến đấu cho mọi người chơi tham chiến (hoặc người chơi chỉ định)
     *
     * @param onlyTo nếu khác null thì chỉ gửi cho người chơi đó (dùng khi tham chiến)
     */
    private fun sendBattleInit(battle: BattleInstance, onlyTo: GameSession? = null) {
        val roleBytes = BattleRoleSerializer.serializeAll(
            battle.getAllFighters().map { toBattleRoleData(battle, it) }
        )

        val targets = if (onlyTo != null) listOf(onlyTo) else battle.playerSessions.values.toList()

        // S:011-250 khởi tạo chiến đấu
        for (session in targets) {
            responseSender.sendBattleInit(session, battle.terrain, battle.eventNo, roleBytes)
        }

        // S:011-010 loại chiến trường (Normal PvE)
        for (session in targets) {
            responseSender.sendBattleFieldType(session, battle.warStyle, 0, 0, 0)
        }
    }

    /**
     * Spawn at most one queued Eve reinforcement per newly-vacant slot.
     *
     * The old role must leave the client field before S11-005 reuses its slot.
     */
    private fun spawnEveReinforcements(battle: BattleInstance): Int {
        val pending = pendingEveEnemies[battle.battleId] ?: return 0
        if (pending.isEmpty()) {
            pendingEveEnemies.remove(battle.battleId)
            return 0
        }

        var spawnedCount = 0
        val emptyQueues = mutableListOf<DvtEveBattlePlacement.Slot>()

        for ((slot, queue) in pending.entries.toList()) {
            if (queue.isEmpty()) {
                emptyQueues += slot
                continue
            }

            val current = battle.grid.getAt(slot.col, slot.row)
            if (current?.isAlive == true) continue

            // Never evict a player-controlled corpse on the allied side.
            if (current?.isPlayerControlled == true) continue

            if (current != null) {
                battle.grid.remove(slot.col, slot.row)
                for (session in battle.playerSessions.values) {
                    responseSender.sendBattleExitFight(session, slot.col, slot.row)
                }
            }

            var next: EveFightEnemy? = null
            while (queue.isNotEmpty() && next == null) {
                val candidate = queue.removeFirst()
                if (candidate.npcId in npcDefs) {
                    next = candidate
                } else {
                    logger.warn {
                        "Trận ${battle.battleId}: reinforcement thiếu NpcDef npcId=${candidate.npcId}"
                    }
                }
            }

            if (next != null) {
                val npcDef = npcDefs.getValue(next.npcId)
                val fighter = createNpcFighter(
                    npcDef = npcDef,
                    col = slot.col,
                    row = slot.row,
                    battleId = battle.battleId,
                    npcDataId = next.npcId,
                )
                battle.addFighter(fighter)

                val roleBytes = BattleRoleSerializer.serializeAll(
                    listOf(toBattleRoleData(battle, fighter))
                )
                for (session in battle.playerSessions.values) {
                    responseSender.sendBattleJoinRoleAppear(session, roleBytes)
                }
                spawnedCount++
                logger.info {
                    "Trận ${battle.battleId}: reinforcement npcId=${next.npcId} " +
                        "LocationPos=${next.locationPos} -> (${slot.col},${slot.row}), " +
                        "còn=${queue.size}"
                }
            }

            if (queue.isEmpty()) emptyQueues += slot
        }

        emptyQueues.forEach(pending::remove)
        if (pending.isEmpty()) pendingEveEnemies.remove(battle.battleId)
        return spawnedCount
    }

    /**
     * Coroutine vòng lặp hiệp đấu
     */
    private suspend fun runBattleLoop(battle: BattleInstance) {
        try {
            // Chờ client tải cảnh chiến đấu (hoạt ảnh vào trận ~1.5s + độ trễ mạng)
            delay(BattleConstants.SCENE_LOAD_DELAY_MS)

            battle.state = BattleInstance.State.COUNTDOWN

            while (battle.state != BattleInstance.State.FINISHED) {
                // PC start_round applies surviving periodic statuses before commands/countdown.
                val statusResult = battle.resolveRoundStartStatusEffects()
                broadcastRoundResult(battle, statusResult)
                spawnEveReinforcements(battle)
                val statusEnd = battle.checkBattleEnd()
                if (statusEnd != null) {
                    handleBattleEnd(battle, statusEnd)
                    break
                }

                // 1. Gửi đếm ngược
                broadcastCountdown(battle)
                battle.state = BattleInstance.State.WAITING_COMMANDS

                // 2. Chờ lệnh người chơi (tối đa 20 giây hoặc tất cả người chơi đã sẵn sàng)
                waitForCommands(battle, BattleConstants.COUNTDOWN_NORMAL * 1000L)

                // 3. AI NPC ra quyết định
                battle.resolveNpcActions()

                // 4. Thực hiện hiệp
                val roundResult = battle.resolveRound()

                // 5. Phát rộng kết quả hiệp
                broadcastRoundResult(battle, roundResult)

                // 5.5 Bỏ chạy thành công → kiểm tra liệu tất cả người chơi đã bỏ chạy chưa
                if (roundResult.escapeResults.isNotEmpty()) {
                    val escapedPlayerIds = roundResult.escapeResults
                        .filter { it.fighter.isHumanPlayer }
                        .map { it.fighter.roleId }
                        .toSet()

                    if (escapedPlayerIds.isNotEmpty()) {
                        // Tất cả phía người chơi đều bỏ chạy → kết thúc thua trận
                        val remainingPlayers = battle.grid.getAlivePlayerSide()
                            .filter { it.isHumanPlayer }
                        if (remainingPlayers.isEmpty()) {
                            logger.info { "Trận ${battle.battleId}: toàn bộ người chơi bỏ chạy, kết thúc trận" }
                            handleBattleEnd(battle, BattleEndResult(playerWin = false))
                            break
                        }
                    }
                }

                // 6. Chờ hoạt ảnh cuối cùng phát xong
                delay(BattleConstants.ROUND_END_DELAY_MS)

                // 6.5 DVT Eve_FightData: dead slots may immediately receive the
                // next queued NPC with the same LocationPos.
                spawnEveReinforcements(battle)

                // 7. Kiểm tra kết thúc trận đấu
                val endResult = battle.checkBattleEnd()
                if (endResult != null) {
                    handleBattleEnd(battle, endResult)
                    break
                }

                battle.state = BattleInstance.State.COUNTDOWN
            }
        } catch (e: CancellationException) {
            logger.debug { "Trận ${battle.battleId} coroutine bị hủy" }
        } catch (e: Exception) {
            logger.error(e) { "Trận ${battle.battleId} vòng lặp lượt gặp lỗi" }
            abortFailedBattle(battle)
        } catch (e: LinkageError) {
            logger.error(e) { "Trận ${battle.battleId} thư viện runtime lỗi" }
            abortFailedBattle(battle)
        } finally {
            cleanupBattle(battle)
        }
    }

    /**
     * Gửi đếm ngược
     */
    private fun broadcastCountdown(battle: BattleInstance) {
        for (session in battle.playerSessions.values) {
            responseSender.sendBattleCountdown(session)
        }
    }

    /**
     * Chờ lệnh người chơi
     */
    private suspend fun waitForCommands(battle: BattleInstance, timeoutMs: Long) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (battle.allPlayersReady()) break
            if (battle.state == BattleInstance.State.FINISHED) break
            delay(200)  // Kiểm tra mỗi 200ms một lần
        }

        // Người chơi quá hạn chưa hành động thì nhân vật của họ tự động tấn công thường (bao gồm tướng)
        for (fighter in battle.getAllFighters()) {
            if (fighter.isPlayerControlled && fighter.isAlive && !fighter.isStunned && !fighter.hasActed) {
                // Chọn mục tiêu theo phe đang ở (trong PVP hai bên đều là người chơi)
                val enemies = if (fighter.isPlayerSide)
                    battle.grid.getAliveEnemySide()
                else
                    battle.grid.getAlivePlayerSide()
                if (enemies.isNotEmpty()) {
                    val target = enemies.first()
                    fighter.pendingAction = BattleAction(
                        actionKind = BattleAction.KIND_SKILL,
                        id = BattleConstants.HAND_FIGHT_SKILL_ID,
                        targetCol = target.col,
                        targetRow = target.row
                    )
                    fighter.hasActed = true

                    // Gửi S:053-005 thông báo client nhân vật này đã xác nhận động tác
                    // Client FightField.NextControlMan() phụ thuộc gói tin này để tiến conIdx
                    val ownerSession = findOwnerSession(battle, fighter)
                    if (ownerSession != null) {
                        sendActSetOK(ownerSession, fighter.col, fighter.row)
                    }
                }
            }
        }
    }

    /**
     * Broadcast one resolved action at a time. RevAttackSkill calls ClearAllAttack() on
     * each S:050-001, so the next packet is deliberately delayed until the previous
     * action's playback window has elapsed.
     */
    /**
     * DVT attack scripts finish from their own animation state machines (arrive/frame/light),
     * not SkillData.spendSecond. The client has no animation-complete ACK, so use the
     * original-server command pacing scale instead of treating spendSecond as seconds.
     */
    private fun actionPlaybackDelayMs(@Suppress("UNUSED_PARAMETER") action: ActionResult): Long =
        BattleConstants.ACTION_COMMAND_PACING_MS

    private suspend fun broadcastRoundResult(battle: BattleInstance, roundResult: RoundResult) {
        // 0. Người bỏ chạy thành công — gửi S:011-001 trước để nhân vật rời chiến trường
        for (escape in roundResult.escapeResults) {
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleExitFight(session, escape.fighter.col, escape.fighter.row)
            }
        }

        // 1. S:050-001 — PC-original execution model:
        // independent commands are separate packets, but a physical same-target combo chain
        // is serialized into the SAME packet so DVT starts those combo animations together.
        val actionGroups = mutableListOf<MutableList<ActionResult>>()
        for (actionResult in roundResult.actionResults) {
            val previousGroupEndsWithRevive = actionGroups.lastOrNull()?.lastOrNull()
                ?.targets?.any { it.passiveReviveHp > 0 } == true
            if (actionResult.comboWithPrevious && actionGroups.isNotEmpty() && !previousGroupEndsWithRevive) {
                actionGroups.last().add(actionResult)
            } else {
                actionGroups.add(mutableListOf(actionResult))
            }
        }
        for ((index, group) in actionGroups.withIndex()) {
            val skillData = serializeSkillResults(group)
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleSkillResult(session, skillData)
            }

            // 14080 auto-revive is an S:051-001 absolute HP update after the lethal hit.
            for (target in group.flatMap { it.targets }) {
                if (target.passiveReviveHp <= 0) continue
                val hp = target.passiveReviveHp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                for (session in battle.playerSessions.values) {
                    responseSender.sendBattleRevive(session, target.targetCol, target.targetRow, hp)
                }
            }

            if (index < actionGroups.lastIndex) {
                delay(actionPlaybackDelayMs(group.last()))
            }
        }

        // 1.2 S:023-009 tiêu hao vật phẩm chiến đấu — thông báo khấu trừ từ túi đồ của chủ sở hữu
        for (consumed in roundResult.consumedItems) {
            battle.playerSessions[consumed.ownerPlayerId]?.let { ownerSession ->
                responseSender.sendReduceBagItem(ownerSession, consumed.bagSlot, consumed.amount)
            }
        }

        // 1.5 S:053-003 đánh bay (sau kết quả tấn công, trước phục hồi trạng thái)
        if (roundResult.flyOutTargets.isNotEmpty()) {
            delay(BattleConstants.FLY_OUT_ANIM_DELAY_MS)  // Chờ hoạt ảnh số sát thương bắt đầu
            for ((col, row) in roundResult.flyOutTargets) {
                for (session in battle.playerSessions.values) {
                    responseSender.sendBattleFlyOut(session, col, row)
                }
                logger.info { "Trận ${battle.battleId}: nhân vật ($col,$row) bị đánh văng" }
            }
        }

        // 2. S:053-001 phục hồi trạng thái
        if (roundResult.statusRestores.isNotEmpty()) {
            val statusData = serializeStatusRestores(roundResult.statusRestores)
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleRestoreStatus(session, statusData)
            }
        }

        // 3. Đồng bộ HP/SP người chơi vào PlayerState + S:008-001 cập nhật HUD nhân vật chính
        //    Đồng bộ đúng thực tế trong mỗi hiệp (HP=0 = chết), mức đảm bảo 1 chỉ áp dụng khi kết thúc trận
        for ((playerId, session) in battle.playerSessions) {
            val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId } ?: continue
            val charId = session.roleId.toLong()
            playerStateManager.setHp(charId, playerFighter.hp)
            playerStateManager.setSp(charId, playerFighter.sp.toInt())
            responseSender.sendSetAttribute(session, EAttribute.HP, playerFighter.hp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.SP, playerFighter.sp.toInt(), true)

            // Keep every deployed pet's persistent HP/SP authoritative after each round.
            // followSlot is server-only metadata, so duplicate NPC IDs remain unambiguous.
            val petFighters = battle.getAllFighters().filter {
                it.kind == EHuman.FOLLOW_NPC && it.masterId == playerId && it.followSlot > 0
            }
            for (petFighter in petFighters) {
                val slot = petFighter.followSlot
                petSystem.updateFollowNpcHpSp(charId, slot, petFighter.hp, petFighter.sp.toInt())
                responseSender.sendFollowNpcAttribute(session, slot, EAttribute.HP, petFighter.hp.toInt())
                responseSender.sendFollowNpcAttribute(session, slot, EAttribute.SP, petFighter.sp.toInt())
            }
        }
    }

    /**
     * Xử lý kết thúc trận đấu
     */
    private suspend fun handleBattleEnd(battle: BattleInstance, endResult: BattleEndResult) {
        battle.state = BattleInstance.State.FINISHED

        headlessWorldBotCallbacks.remove(battle.battleId)?.let { callback ->
            cleanupBattle(battle)
            callback(endResult.playerWin, battle.currentRound)
            logger.info { "WorldSimBot headless PvE end battle=" + battle.battleId +
                " win=" + endResult.playerWin + " rounds=" + battle.currentRound }
            return
        }
        logger.info { "Trận ${battle.battleId} kết thúc: người chơi${if (endResult.playerWin) "勝利" else "失敗"}" }

        // Mobile DVT: Honor = Calc.GetHonor(KillNum), hiện công thức 1:1.
        // Chỉ tính NPC địch thực sự đã chết; PVP player không tạo KillNum PvE.
        val defeatedEnemyNpcCount = if (
            endResult.playerWin &&
            !battle.suppressGenericRewards &&
            battle.warType != EWar.PK_PLAYERS
        ) {
            battle.getAllFighters().count {
                it.isEnemySide &&
                    !it.isHumanPlayer &&
                    it.npcId > 0 &&
                    it.isDead
            }
        } else {
            0
        }

        val deniedEveRewardPlayers = mutableSetOf<Long>()
        for ((playerId, session) in battle.playerSessions) {
            // Generic Eve battle rewards are non-transactional (drops, EXP,
            // gold, pets, charge debits). Fence the attempt before ANY one of
            // those mutations, not merely when returning to EveFlowController.
            val eventRewardAllowed = if (EveBattleRewardFencePolicy.needsReservation(
                    battle.warType, endResult.playerWin, battle.suppressGenericRewards
                )
            ) {
                try {
                    onBeforeEveBattleRewards?.invoke(session) ?: true
                } catch (e: Exception) {
                    logger.error(e) {
                        "Eve battle reward fence failed: battle=${battle.battleId} playerId=$playerId"
                    }
                    false
                }
            } else true
            if (!eventRewardAllowed) {
                deniedEveRewardPlayers.add(playerId)
                logger.error {
                    "Suppressing unreserved Eve battle rewards: battle=${battle.battleId} playerId=$playerId"
                }
                responseSender.sendSystemMessage(
                    session, "Phần thưởng chiến đấu đang chờ xác minh. Hãy liên hệ quản trị viên."
                )
            }

            // Thưởng (chỉ khi thắng và quyền nhận thưởng đã được xác nhận)
            if (endResult.playerWin && !battle.suppressGenericRewards && eventRewardAllowed) {
                if (defeatedEnemyNpcCount > 0) {
                    grantKillNum(session, battle.battleId, defeatedEnemyNpcCount)
                }

                // A Phuc Than charge is one eligible EXP-paying win, not a session EXP buff.
                // Debit first: persistence failures must not award boosted EXP.
                val charIdForGod = session.roleId.toLong()
                val godMultiplier = if (defeatedEnemyNpcCount > 0 && elfRepository != null) {
                    try {
                        val profile = elfRepository.load(charIdForGod).profile
                        if (profile.godMission <= 0 || elfRepository.changeGodMission(charIdForGod, -1) == null) {
                            1.0
                        } else {
                            val baseExp = elfBaseExpByNo[profile.activeNo] ?: elfBaseExpByNo[999] ?: 100
                            (baseExp.toDouble() / 100.0).coerceAtLeast(1.0)
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Unable to debit Phuc Than charge cid=$charIdForGod" }
                        1.0
                    }
                } else 1.0
                val reward = battle.calculateRewards(session.level, npcDropTable, godMultiplier)
                logger.info { "Trận ${battle.battleId}: người chơi $playerId nhận exp=${reward.exp} gold=${reward.gold} drops=${reward.drops.size}" }

                // Hoạt ảnh rơi vật phẩm + thực tế phát ra
                if (reward.drops.isNotEmpty()) {
                    val charId = session.roleId.toLong()
                    // Tìm vị trí kẻ địch và người chơi đã chết (dùng cho hoạt ảnh)
                    val deadEnemy = battle.getAllFighters().firstOrNull { it.isEnemySide && it.isDead }
                    val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId }
                    for (drop in reward.drops) {
                        // Grant in full before displaying success or advancing quest battles.
                        val added = inventorySystem.addItemsToBagExact(charId, drop.itemId, drop.quantity)
                        if (added == null) {
                            responseSender.sendSystemMessage(session, "Túi không đủ chỗ nhận vật phẩm rơi. Hãy chừa chỗ trước trận tiếp theo.")
                            logger.warn { "Trận ${battle.battleId}: không nhận được itemId=${drop.itemId} x${drop.quantity} charId=$charId" }
                            continue
                        }
                        responseSender.sendBattleDrop(
                            session, drop.itemId,
                            fromCol = deadEnemy?.col ?: 0, fromRow = deadEnemy?.row ?: 2,
                            toCol = playerFighter?.col ?: 2, toRow = playerFighter?.row ?: 0,
                        )
                        for ((slot, thing) in added) {
                            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
                        }
                        logger.info { "Trận ${battle.battleId}: người chơi $playerId nhận vật phẩm itemId=${drop.itemId} x${drop.quantity}" }
                    }
                }

                // Phát EXP
                val charId = session.roleId.toLong()
                val state = playerStateManager.getState(charId)
                if (state != null && reward.exp > 0) {
                    distributeExp(session, charId, state, reward.exp)
                    // DVT system channel is the player's battle/reward notification area.
                    // S:008-001 updates the EXP gauge; it does not create a chat entry.
                    responseSender.sendSystemMessage(session, "Nhận được ${reward.exp} điểm kinh nghiệm.")
                }

                // Every deployed living follower receives its own DVT-style cumulative EXP.
                val activePetFighters = battle.getAllFighters().filter {
                    it.kind == EHuman.FOLLOW_NPC &&
                        it.masterId == playerId &&
                        it.followSlot > 0 &&
                        it.isAlive
                }
                for (activePetFighter in activePetFighters) {
                    val petEntry = petSystem.getFollowPets(charId)[activePetFighter.followSlot] ?: continue
                    val petExp = BattleRewardCalc.calculateExp(
                        battle.involvedNpcDefs,
                        petEntry.level,
                        godMultiplier
                    )
                    val gain = petSystem.gainBattleExp(charId, activePetFighter.followSlot, petExp)
                    if (gain != null) {
                        responseSender.sendFollowNpcAttribute(
                            session, gain.slotIndex, EAttribute.EXP,
                            gain.after.exp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                        val petName = gain.after.name.ifEmpty { npcDefs[gain.after.npcId]?.name ?: "Pet" }
                        responseSender.sendSystemMessage(
                            session, "$petName nhận được $petExp điểm kinh nghiệm."
                        )
                        if (gain.levelGained > 0) {
                            responseSender.sendSystemMessage(
                                session, "$petName đã lên cấp ${gain.after.level}."
                            )
                            responseSender.sendFollowNpcAttribute(
                                session, gain.slotIndex, EAttribute.LV, gain.after.level
                            )
                            responseSender.sendFollowNpcAttribute(
                                session, gain.slotIndex, EAttribute.SKILL_POINT, gain.after.skillPoint
                            )
                            responseSender.sendFollowNpcAttribute(
                                session, gain.slotIndex, EAttribute.HP, gain.after.hp.toInt()
                            )
                            responseSender.sendFollowNpcAttribute(
                                session, gain.slotIndex, EAttribute.SP, gain.after.sp
                            )
                        }
                        logger.info {
                            "Trận ${battle.battleId}: tướng slot=${gain.slotIndex} npcId=${gain.after.npcId} " +
                                "nhận exp=$petExp lv=${gain.before.level}->${gain.after.level}"
                        }
                    }
                }

                // Phát tiền vàng
                if (reward.gold > 0) {
                    val goldCharId = session.roleId.toLong()
                    if (moneySystem.addGold(goldCharId, reward.gold)) {
                        responseSender.sendAddMoney(session, reward.gold)
                        responseSender.sendSystemMessage(session, "Nhận được ${reward.gold} vàng.")
                        logger.info { "Trận ${battle.battleId}: người chơi $playerId nhận ${reward.gold} tiền vàng" }
                    }
                }
            }

            // NPC bắt thành công → thêm vào PetSystem + gửi gói tin cho client
            if (endResult.playerWin && !battle.suppressGenericRewards &&
                eventRewardAllowed && battle.capturedNpcs.isNotEmpty()) {
                val charId = session.roleId.toLong()
                for ((capturerPlayerId, npcId) in battle.capturedNpcs) {
                    if (capturerPlayerId != playerId) continue
                    val npcDef = npcDefs[npcId]
                    if (npcDef == null) {
                        logger.warn { "Trận ${battle.battleId}: bắt được npcId=$npcId nhưng npcDefs không có định nghĩa này" }
                        continue
                    }
                    val added = petSystem.addFollowNpc(
                        charId, npcId, npcDef,
                        canGrow = 1,
                        allowDuplicateInCart = true,
                    )
                    if (added != null) {
                        val (slotIndex, pet) = added
                        responseSender.sendFollowNpcAdd(session, playerId, slotIndex, npcId, pet.canGrow, showMsg = true)
                        collectionSystem?.markWarriorOwned(charId, npcId, session)
                        dailyProgressSystem?.incrementRoleCount(
                            session,
                            DailyProgressSystem.CATCH_SUCCESS,
                        )
                        logger.info { "Trận ${battle.battleId}: người chơi $playerId bắt được tướng npcId=$npcId → slot=$slotIndex" }
                    } else {
                        if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                            petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                                responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                            }
                        }
                        logger.warn { "Trận ${battle.battleId}: thêm tướng npcId=$npcId thất bại (trùng hoặc đội đầy)" }
                    }
                }
                // Đồng bộ toàn bộ dữ liệu tướng cho client
                val allPets = petSystem.getFollowPets(charId)
                if (allPets.isNotEmpty()) {
                    responseSender.sendFollowNpcFullData(session, allPets.values.sortedBy { it.slotIndex })
                }
            }

            // ERoleCount.Fight=163 — mỗi trận kết thúc tăng một lần.
            dailyProgressSystem?.incrementRoleCount(session, DailyProgressSystem.FIGHT)

            // Đồng bộ HP/SP cuối cùng của người chơi vào PlayerState + cập nhật HUD nhân vật chính
            val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId }
            if (playerFighter != null) {
                val syncCharId = session.roleId.toLong()
                // HP đảm bảo tối thiểu 1: khi thua HP=0 → ép về 1 (trạng thái thập tử nhất sinh), tránh lần vào trận sau nằm luôn
                val syncHp = playerFighter.hp.coerceAtLeast(1)
                playerStateManager.setHp(syncCharId, syncHp)
                playerStateManager.setSp(syncCharId, playerFighter.sp.toInt())
                responseSender.sendSetAttribute(session, EAttribute.HP, syncHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.SP, playerFighter.sp.toInt(), true)
            }

            // Đồng bộ HP/SP cuối cùng của tướng vào PetSystem + Role followNpc của client.
            //
            // DVT MachineBox.Supply() không đọc HP/SP từ FightField mà đọc trực tiếp
            // Role.GetFollowNpcs(). Nếu chỉ cập nhật cache server, client vẫn giữ HP/SP
            // trước trận và sẽ tính needHp/needSp = 0 → không tự dùng đồ hồi cho pet.
            val followNpcFighter = battle.getAllFighters().firstOrNull {
                it.kind == EHuman.FOLLOW_NPC && it.masterId == playerId
            }
            if (followNpcFighter != null) {
                val syncCharId = session.roleId.toLong()
                val followSlot = petSystem.getFollowPets(syncCharId)
                    .entries
                    .firstOrNull { it.value.npcId == followNpcFighter.npcId }
                    ?.key

                petSystem.updateFightNpcHpSp(syncCharId, followNpcFighter.hp, followNpcFighter.sp.toInt())

                if (followSlot != null) {
                    responseSender.sendFollowNpcAttribute(
                        session, followSlot, EAttribute.HP, followNpcFighter.hp.toInt()
                    )
                    responseSender.sendFollowNpcAttribute(
                        session, followSlot, EAttribute.SP, followNpcFighter.sp.toInt()
                    )
                }
            }

            // Persist toàn bộ dữ liệu vừa thay đổi bởi battle ngay sau kết toán.
            // Autosave vẫn chạy nền, nhưng battle checkpoint này loại bỏ cửa sổ mất
            // EXP/vàng/vật phẩm/pet khi máy chủ bị reset ngay sau trận.
            val checkpointCharId = session.roleId.toLong()
            try {
                val checkpointState = playerStateManager.getState(checkpointCharId)
                if (checkpointState != null) {
                    characterRepository?.updatePlayerState(
                        checkpointCharId,
                        checkpointState.exp,
                        checkpointState.level,
                        checkpointState.attrPoint,
                        checkpointState.skillPoint,
                        checkpointState.intelligence,
                        checkpointState.atk,
                        checkpointState.def,
                        checkpointState.agi,
                        checkpointState.hpx,
                        checkpointState.spx,
                    )
                }
                inventorySystem.save(checkpointCharId)
                moneySystem.saveMoney(checkpointCharId)
                petSystem.save(checkpointCharId)
            } catch (e: Exception) {
                logger.error(e) { "Trận ${battle.battleId}: lưu checkpoint thất bại charId=$checkpointCharId" }
            }

            // S:011-000 kết thúc chiến đấu
            responseSender.sendBattleEnd(session, playerId, 0)

            // Chỉ kích hoạt tiếp tế khi Hộp Cơ Quan thực sự đang bật.
            // S:065-010 gọi MachineBox.Supply(true), phía client sẽ tự chọn đồ hồi
            // cho cả người chơi và pet theo ngưỡng HP/SP đã lưu trong client.
            if (macroBoxSystem?.isActive(session.roleId.toLong()) == true) {
                responseSender.sendMacroBoxSupply(session, checkItem = true)
            }
        }

        // S:011-001 drives per-fighter exit animation. The local player's own
        // fighter must be the last exit packet seen by that client.
        delay(500)
        val allExitFighters = battle.getAllFighters()
        for (session in battle.playerSessions.values) {
            val localPlayerId = session.playerId
            val exitOrder = allExitFighters.sortedBy { fighter ->
                if (fighter.isHumanPlayer && fighter.roleId == localPlayerId) 1 else 0
            }
            for (fighter in exitOrder) {
                responseSender.sendBattleExitFight(session, fighter.col, fighter.row)
                delay(100)
            }
        }

        // Release battle ownership before a continuation can warp or start another fight.
        cleanupBattle(battle)

        if (battle.warStyle == EWarStyle.WEEK_DUNGEON && battle.dungeonId > 0) {
            val callback = onWeeklyDungeonBattleEndCallback
            if (callback != null) {
                for (session in battle.playerSessions.values) {
                    callback(session, battle.dungeonId, endResult.playerWin, battle.currentRound)
                }
            } else {
                for (session in battle.playerSessions.values) {
                    responseSender.sendEventEnd(session)
                }
            }
            return
        }

        // Trận PVP và PK với NPC trên bản đồ không đi qua callback sự kiện
        if (!BattleLifecyclePolicy.shouldResumeEve(battle.warType)) {
            logger.info { "Trận ${battle.battleId} không thuộc sự kiện đã kết thúc, không gọi callback sự kiện" }
            return
        }

        // Trận chiến sự kiện PVE: phục hồi luồng sự kiện NPC (thông qua callback, do NpcEventHandler xử lý)
        delay(300)
        val callback = onBattleEndCallback
        if (callback != null) {
            for ((playerId, session) in battle.playerSessions) {
                if (playerId in deniedEveRewardPlayers) {
                    onBattleAbortCallback?.invoke(session) ?: responseSender.sendEventEnd(session)
                    continue
                }
                BattleEventContinuation.run(
                    resume = { callback(session, playerId, endResult.playerWin) },
                    abort = {
                        onBattleAbortCallback?.invoke(session) ?: responseSender.sendEventEnd(session)
                    }
                )
            }
        } else {
            // Không có callback (môi trường thử nghiệm v.v.), kết thúc sự kiện trực tiếp
            for (session in battle.playerSessions.values) {
                responseSender.sendEventEnd(session)
            }
        }
    }

    /**
     * Cộng điểm diệt địch sau một trận PvE thắng.
     *
     * DB được ghi trước runtime/client để relog không làm mất tiến độ.
     * Client tự tính Honor từ KillNum khi login, nhưng cập nhật realtime cần gửi
     * cả KILL_NUM và HONOR để CheckCondition(Honor) được kích hoạt ngay.
     */
    private suspend fun grantKillNum(
        session: GameSession,
        battleId: Long,
        defeatedEnemyNpcCount: Int,
    ) {
        if (defeatedEnemyNpcCount <= 0) return
        val repository = characterRepository ?: return
        val charId = session.roleId.toLong()
        val current = playerStateManager.getKillNum(charId)
        val next = (current.toLong() + defeatedEnemyNpcCount.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        try {
            repository.updateKillNum(charId, next)
        } catch (e: Exception) {
            logger.error(e) {
                "Trận $battleId: lưu KillNum thất bại charId=$charId current=$current add=$defeatedEnemyNpcCount"
            }
            return
        }

        if (!playerStateManager.setKillNum(charId, next)) {
            logger.error {
                "Trận $battleId: DB KillNum=$next đã lưu nhưng runtime player không tồn tại charId=$charId"
            }
            return
        }

        responseSender.sendWorldAttribute(session, EAttribute.KILL_NUM, next.toLong(), true, 0)
        responseSender.sendWorldAttribute(session, EAttribute.HONOR, next.toLong(), true, 0)
        logger.info {
            "Trận $battleId: KillNum charId=$charId $current -> $next (+$defeatedEnemyNpcCount)"
        }
    }

    /**
     * Phát EXP và xử lý lên cấp
     *
     * Cộng dồn EXP → kiểm tra lên cấp → tính lại maxHP/maxSP → thông báo client
     */
    private suspend fun distributeExp(session: GameSession, charId: Long, state: PlayerState, expGain: Long) {
        if (state.turn >= 3) {
            val repo = skillRepository ?: return
            val turn3 = repo.findTurn3State(charId)
            // Lua UIStatus treats Turn3Exp as cumulative EXP:
            // progress = Turn3Exp - Calc.GetNeedTExp(turn3Lv, 3).
            // Never subtract level costs from the persisted value.
            val exp = (turn3.exp + expGain).coerceAtLeast(0)
            var level = state.level
            var gained = 0
            while (level < TURN3_MAX_LEVEL) {
                val nextTurn3Level = (level - PLAYER_MAX_LEVEL) + 1
                val threshold = statSystem.calculateNeedTurn3Exp(nextTurn3Level)
                if (exp < threshold) break
                level++
                gained++
            }
            repo.saveTurn3State(charId, turn3.copy(exp = exp))
            responseSender.sendSetAttribute(session, EAttribute.EXP, exp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), true)
            if (gained > 0) {
                synchronized(state) { state.level = level }
                session.level = level
                characterRepository?.updateLevel(charId, level)
                responseSender.sendSetAttribute(session, EAttribute.LV, level, true)

                // Calculator.lua clamps Turn3's base formula level to 200, then applies
                // automatic growth through turn3Lv: HPX += min(turn3Lv, 70), SPX += turn3Lv.
                // Recalculate immediately on level-up so runtime/client do not stay stale
                // until the next login.
                val turn3Lv = (level - PLAYER_MAX_LEVEL).coerceAtLeast(0)
                val collectHpx = collectionSystem?.getPlayerCollectionAttribute(charId, EAttribute.HPX) ?: 0
                val baseMaxHp = statSystem.calculateMaxHp(
                    level = PLAYER_MAX_LEVEL,
                    hpx = state.hpx + state.equipHpx + turn3Lv.coerceAtMost(70) + state.turn3Hpx +
                        collectHpx + state.astrolabeHpx,
                    equipMaxHp = state.equipMaxHp,
                    extraHp = 0,
                    turn = state.turn,
                    career = state.career,
                    turn3Lv = turn3Lv
                )
                val newMaxHp = DvtPercentMath.applyFinalPercentFloor(baseMaxHp, state.hpPercent)
                    .coerceAtLeast(1L)
                val newMaxSp = statSystem.calculateMaxSp(
                    level = PLAYER_MAX_LEVEL,
                    spx = state.spx + state.equipSpx + turn3Lv + state.turn3Spx,
                    equipMaxSp = state.equipMaxSp,
                    extraSp = 0,
                    turn = state.turn,
                    career = state.career,
                    turn3Lv = turn3Lv
                )
                playerStateManager.updateMaxHp(charId, newMaxHp)?.let { (maxHp, hp) ->
                    responseSender.sendSetAttribute(session, EAttribute.MAX_HP, maxHp.toInt(), true)
                    responseSender.sendSetAttribute(session, EAttribute.HP, hp.toInt(), true)
                }
                playerStateManager.updateMaxSp(charId, newMaxSp)?.let { (maxSp, sp) ->
                    responseSender.sendSetAttribute(session, EAttribute.MAX_SP, maxSp, true)
                    responseSender.sendSetAttribute(session, EAttribute.SP, sp, true)
                }
                logger.info { "Lên cấp sau chuyển sinh 3: charId=$charId +$gained → Lv.$level, exp=$exp" }
            } else {
                logger.info { "EXP chuyển sinh 3: charId=$charId +$expGain → $exp" }
            }
            return
        }
        // Compatibility migration for characters persisted by the old remainder-EXP server.
        // A valid DVT cumulative EXP at level L can never be below NeedTExp(L).
        val cumulativeFloor = statSystem.calculateNeedTotalExp(state.level, state.turn)
        if (state.exp < cumulativeFloor) {
            val legacyRemainder = state.exp.coerceAtLeast(0)
            state.exp = cumulativeFloor + legacyRemainder
            logger.info { "EXP migration: charId=$charId lv=${state.level} legacy=$legacyRemainder → cumulative=${state.exp}" }
        }

        synchronized(state) {
            // DVT EAttribute.Exp is cumulative lifetime EXP. UIStatus computes:
            // currentBar = Exp - Calc.GetNeedTExp(level, turn)
            // Never subtract level costs from state.exp.
            val expBeforeReward = state.exp
            state.exp += expGain
            responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)
            logger.info {
                "EXP sync: charId=$charId lv=${state.level} before=$expBeforeReward gain=$expGain cumulative=${state.exp} floor=${statSystem.calculateNeedTotalExp(state.level, state.turn)}"
            }

            // Level L advances when cumulative EXP reaches NeedTExp(L + 1).
            var levelsGained = 0
            while (true) {
                val nextLevelThreshold = statSystem.calculateNeedTotalExp(state.level + 1, state.turn)
                if (state.exp < nextLevelThreshold) break
                state.level++
                levelsGained++
            }

            if (levelsGained > 0) {
                // Mỗi cấp +2 điểm kỹ năng, +3 điểm thuộc tính
                val skillPointGain = levelsGained * 2
                val attrPointGain = levelsGained * 3
                state.skillPoint += skillPointGain
                state.attrPoint += attrPointGain
                skillSystem?.adjustSkillPoint(charId, skillPointGain)

                // Tính lại MaxHP theo cùng contract DVT với login/battle.
                val collectHpx = collectionSystem?.getPlayerCollectionAttribute(charId, EAttribute.HPX) ?: 0
                val baseMaxHp = statSystem.calculateMaxHp(
                    level = state.level,
                    hpx = state.hpx + state.equipHpx + collectHpx + state.astrolabeHpx,
                    equipMaxHp = state.equipMaxHp, extraHp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                val newMaxHp = DvtPercentMath.applyFinalPercentFloor(baseMaxHp, state.hpPercent)
                    .coerceAtLeast(1L)
                val newMaxSp = statSystem.calculateMaxSp(
                    level = state.level, spx = state.spx,
                    equipMaxSp = state.equipMaxSp, extraSp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )

                // Lên cấp hồi đầy
                playerStateManager.updateMaxHp(charId, newMaxHp)
                playerStateManager.updateMaxSp(charId, newMaxSp)
                playerStateManager.fullHp(charId)
                playerStateManager.fullSp(charId)

                // Thông báo client (SetAttribute là thiết đặt tuyệt đối, phải gửi tổng giá trị chứ không gửi lượng gia tăng)
                responseSender.sendSetAttribute(session, EAttribute.LV, state.level, true)
                responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.MAX_HP, newMaxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.HP, newMaxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.MAX_SP, newMaxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SP, newMaxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SKILL_POINT, state.skillPoint, true)
                responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, state.attrPoint, true)

                // Cập nhật session
                session.level = state.level

                logger.info { "Lên cấp: charId=$charId tăng $levelsGained cấp → Lv.${state.level} điểm kỹ năng+$skillPointGain điểm thuộc tính+$attrPointGain" }
            }

        }

        // Repository I/O is suspendable; keep it outside synchronized(state).
        // Snapshot after the atomic EXP/level mutation so persistence matches the client state.
        val persistedExp: Long
        val persistedLevel: Int
        synchronized(state) {
            persistedExp = state.exp
            persistedLevel = state.level
        }
        characterRepository?.updateExpAndLevel(charId, persistedExp, persistedLevel)
    }

    /**
     * Dọn dẹp tài nguyên trận đấu
     */
    /** Fail closed: release battle and event locks without granting a win. */
    private fun abortFailedBattle(battle: BattleInstance) {
        battle.state = BattleInstance.State.FINISHED
        headlessWorldBotCallbacks.remove(battle.battleId)?.let { callback ->
            cleanupBattle(battle)
            callback(false, battle.currentRound)
            logger.warn { "WorldSimBot headless PvE stopped battle=" + battle.battleId }
            return
        }
        cleanupBattle(battle)
        for ((playerId, session) in battle.playerSessions) {
            responseSender.sendBattleEnd(session, playerId, 0)
            val exitOrder = battle.getAllFighters().sortedBy {
                if (it.isHumanPlayer && it.roleId == playerId) 1 else 0
            }
            for (fighter in exitOrder) responseSender.sendBattleExitFight(session, fighter.col, fighter.row)
            onBattleAbortCallback?.invoke(session) ?: responseSender.sendEventEnd(session)
            responseSender.sendSystemMessage(session, "Trận bị gián đoạn. Đã mở lại thao tác; bạn có thể di chuyển hoặc thử lại.")
        }
    }

    private fun cleanupBattle(battle: BattleInstance) {
        pendingEveEnemies.remove(battle.battleId)
        battles.remove(battle.battleId)
        for (playerId in battle.playerSessions.keys) {
            playerBattleMap.remove(playerId, battle.battleId)
            battle.playerSessions[playerId]?.let { session ->
                if (session.battleId == battle.battleId) session.battleId = 0
            }
        }
        logger.debug { "Trận ${battle.battleId} đã dọn tài nguyên" }
    }

    companion object {
        private const val PLAYER_MAX_LEVEL = 200
        private const val TURN3_EXTRA_LEVELS = 50
        private const val TURN3_MAX_LEVEL = PLAYER_MAX_LEVEL + TURN3_EXTRA_LEVELS
        private const val SCENE_LIMIT_NO_CATCH_BIT = 1 shl 6
        private const val NPC_KIND_ELEPHANT = 22
        private const val RIDING_SKILL_ID = 14034
    }

    // ── Hỗ trợ tuần tự hoá gói tin ──

    /**
     * Tuần tự hoá dữ liệu kết quả kỹ năng S:050-001
     *
     * Định dạng: vòng lặp [độ dài(2) + cột kẻ tấn công(1) + hàng(1) + ID kỹ năng(2) + trường chiến(1)
     *        + số mục tiêu(1) + vòng lặp [cột mục tiêu(1) + hàng mục tiêu(1) + kết quả tấn công(1) + động tác(1)
     *        + số thuộc tính(1) + vòng lặp [loại(1) + giá trị(4) + âm/dương(1)]]]
     */
    private fun serializeSkillResults(results: List<ActionResult>): ByteArray {
        val outer = PacketWriter(256)
        for (result in results) {
            // Lắp từng phần: ghi nội dung vào bộ tạm trước, rồi thêm tiền tố độ dài
            val segment = PacketWriter(64)
            segment.writeByte(result.attackerCol)     // Cột kẻ tấn công
            segment.writeByte(result.attackerRow)     // Hàng kẻ tấn công
            segment.writeShortLE(result.skillId)      // ID kỹ năng
            segment.writeByte(result.fightArea)       // Trường chiến
            segment.writeByte(result.targets.size)    // Số mục tiêu

            for (target in result.targets) {
                segment.writeByte(target.targetCol)          // Cột mục tiêu
                segment.writeByte(target.targetRow)          // Hàng mục tiêu
                segment.writeByte(target.attackResult)        // Kết quả tấn công
                segment.writeByte(target.beHitAction)         // Động tác mục tiêu
                segment.writeByte(target.attrChanges.size)    // Số thuộc tính

                for (change in target.attrChanges) {
                    segment.writeByte(change.attrKind)        // Loại thuộc tính
                    segment.writeIntLE(change.value)          // Giá trị
                    segment.writeByte(if (change.isPositive) 0 else 1) // Dấu: 0=cộng, 1=trừ
                }
            }

            val segmentBytes = segment.toByteArray()
            outer.writeShortLE(segmentBytes.size)    // Tiền tố độ dài
            outer.writeBytes(segmentBytes)           // Nội dung
        }
        return outer.toByteArray()
    }

    /**
     * Tuần tự hoá dữ liệu phục hồi trạng thái S:053-001
     */
    private fun serializeStatusRestores(restores: List<StatusRestore>): ByteArray {
        val writer = PacketWriter(restores.size * 5)
        for (r in restores) {
            writer.writeByte(r.col)
            writer.writeByte(r.row)
            writer.writeByte(r.statusKind)
            writer.writeShortLE(r.skillId)
        }
        return writer.toByteArray()
    }

    /**
     * Gửi S:053-005 hoàn tất thiết lập động tác
     */
    private fun sendActSetOK(session: GameSession, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(53)   // mainKind
            .writeByte(5)    // subKind
            .writeByte(0)    // compress
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }

    /**
     * Tìm session của chủ nhân vật (chính người chơi hoặc chủ của tướng)
     */
    private fun findOwnerSession(battle: BattleInstance, fighter: BattleFighter): GameSession? {
        if (fighter.isHumanPlayer) {
            return battle.playerSessions[fighter.roleId]
        }
        if (fighter.kind == EHuman.FOLLOW_NPC && fighter.masterId > 0) {
            return battle.playerSessions[fighter.masterId]
        }
        return null
    }

    // ── Hỗ trợ tạo nhân vật ──

    /**
     * Tạo nhân vật chiến đấu của người chơi từ GameSession
     *
     * Nguồn thuộc tính: PlayerStateManager (tính lúc đăng nhập bởi PlayerDataDto + StatSystem)
     * Nguồn trang bị: InventorySystem (dùng để tuần tự hoá ngoại hình chiến đấu)
     */
    private suspend fun createPlayerFighter(
        session: GameSession,
        col: Int = BattleConstants.PLAYER_FRONT_COL,
        row: Int = BattleConstants.CENTER_ROW,
        kind: Int = EHuman.PLAYER,
        roleIdOverride: Long? = null,
        nameOverride: String? = null,
        restoreFull: Boolean = false,
        hairOverride: Int? = null,
        color1Xor: Int = 0,
        color2Xor: Int = 0,
        equipItemIdsOverride: IntArray? = null,
    ): BattleFighter {
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
            ?: error("Người chơi charId=$charId (playerId=${session.playerId}) không có trạng thái runtime")

        val equips = inventorySystem.getEquipment(charId)
        // Keep the SPECIAL slot authoritative for Light/Dark summon validation.
        // Other equipment IDs are still serialized as before.
        val equipIds = equips.values.map { it.itemId }.toIntArray()
        val weaponItem = equips[3]
        val specialEquip = equips[6]
        val specialEquipId = specialEquip?.itemId ?: 0
        val weaponItemId = weaponItem?.itemId ?: 0
        val weaponItemKind = weaponItem?.let { itemDefs[it.itemId]?.kind } ?: 0
        val normalSkillLevels = skillSystem?.getCache(charId)?.skills?.filterValues { it > 0 }.orEmpty()
        val turn3SkillLevels = skillRepository
            ?.findTurn3State(charId)
            ?.skills
            ?.filterValues { it > 0 }
            .orEmpty()
        val learnedSkillLevels = normalSkillLevels + turn3SkillLevels
        val learnedSkills = learnedSkillLevels.keys.toIntArray()

        // DVT Collection Book bonuses are account-wide and apply to both Player/FollowNpc.
        // EAttribute: Int=27 Atk=28 Def=29 Agi=30 Hpx=31 Spx=32.
        val collectInt = collectionSystem?.getPlayerCollectionAttribute(charId, 27) ?: 0
        val collectAtk = collectionSystem?.getPlayerCollectionAttribute(charId, 28) ?: 0
        val collectDef = collectionSystem?.getPlayerCollectionAttribute(charId, 29) ?: 0
        val collectAgi = collectionSystem?.getPlayerCollectionAttribute(charId, 30) ?: 0
        val collectHpx = collectionSystem?.getPlayerCollectionAttribute(charId, 31) ?: 0
        val collectSpx = collectionSystem?.getPlayerCollectionAttribute(charId, 32) ?: 0

        val turn3Lv = if (state.turn >= 3) (state.level - 200).coerceAtLeast(0) else 0
        val calcLevel = if (state.turn >= 3) 200 else state.level
        val battleBaseMaxHp = statSystem.calculateMaxHp(
            level = calcLevel,
            hpx = state.hpx + state.equipHpx +
                (if (state.turn >= 3) turn3Lv.coerceAtMost(70) + state.turn3Hpx else 0) +
                collectHpx + state.astrolabeHpx,
            equipMaxHp = state.equipMaxHp,
            extraHp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )
        val battleMaxHp = DvtPercentMath.applyFinalPercentFloor(battleBaseMaxHp, state.hpPercent)
            .coerceAtLeast(1L)
        val battleMaxSp = statSystem.calculateMaxSp(
            level = calcLevel,
            spx = state.spx + state.equipSpx +
                (if (state.turn >= 3) turn3Lv + state.turn3Spx else 0) +
                collectSpx + state.astrolabeSpx,
            equipMaxSp = state.equipMaxSp,
            extraSp = 0,
            turn = state.turn,
            career = state.career,
            turn3Lv = turn3Lv,
        )

        return BattleFighter(
            roleId = roleIdOverride ?: session.playerId,
            kind = kind,
            name = nameOverride ?: session.characterName,
            col = col,
            row = row,
            maxHp = battleMaxHp,
            maxSp = battleMaxSp.toLong(),
            hp = if (restoreFull) battleMaxHp else state.hp.coerceIn(0L, battleMaxHp),
            sp = if (restoreFull) battleMaxSp.toLong() else state.sp.toLong().coerceIn(0L, battleMaxSp.toLong()),
            level = state.level,
            element = state.element,
            atk = BattlePercentStatCalc.addBasePercent(state.atk, state.atkPercent) +
                state.equipAtk + state.turn3Atk + collectAtk + state.astrolabeAtk,
            def = BattlePercentStatCalc.addBasePercent(state.def, state.defPercent) +
                state.equipDef + state.turn3Def + collectDef + state.astrolabeDef,
            agi = state.agi + state.equipAgi + state.turn3Agi + collectAgi + state.astrolabeAgi,
            intelligence = DvtPercentMath.addBasePercentRound(state.intelligence, state.intPercent) +
                state.equipInt + state.turn3Int + collectInt + state.astrolabeInt,
            atkPercent = state.atkPercent,
            defPercent = state.defPercent,
            crit = state.crit,
            critDamage = state.critDamage,
            dodge = state.dodge,
            hit = state.hit,
            increaseTotalDamage = state.increaseTotalDamage,
            damageReduction = state.damageReduction,
            reduceTotalDamage = state.reduceTotalDamage,
            increaseDamageTakenSelf = state.increaseDamageTakenSelf,
            skills = learnedSkills,
            skillLevels = learnedSkillLevels,
            turn = state.turn,
            career = state.career,
            turn3Element = session.turn3Element,
            specialEquipItemId = specialEquipId,
            specialEquipDamage = specialEquip?.damage ?: 0,
            weaponItemId = weaponItemId,
            weaponItemKind = weaponItemKind,
            sex = session.sex,
            face = session.face,
            hair = hairOverride ?: session.hair,
            color1 = session.color1 xor color1Xor,
            color2 = session.color2 xor color2Xor,
            equipItemIds = equipItemIdsOverride ?: equipIds
        )
    }

    /**
     * Add battle-eligible carried followers to the existing DVT grid.
     *
     * No new wire format is introduced: each pet is serialized as the normal
     * FOLLOW_NPC BattleRoleData with the same masterId, which FightField.FindMasterID
     * already iterates for command control.
     */
    private fun addBattleFollowNpcs(
        session: GameSession,
        battle: BattleInstance,
        frontCol: Int,
        backCol: Int,
        pets: List<Pair<Int, FollowNpcData>> = petSystem.getBattleDeployableFollowPets(session.roleId.toLong())
    ): Int {
        var added = 0
        for ((followSlot, petData) in pets) {
            val position = battle.grid.findEmptySlot(frontCol, backCol) ?: break
            val npcDef = npcDefs[petData.npcId]
            if (npcDef == null) {
                logger.warn { "Battle follower slot=$followSlot npcId=${petData.npcId} has no NPC definition" }
                continue
            }

            val followFighter = createFollowNpcFighter(
                npcDef = npcDef,
                petData = petData,
                masterId = session.playerId,
                ownerCharacterId = session.roleId.toLong(),
                npcDataId = petData.npcId,
                followSlot = followSlot,
                col = position.first,
                row = position.second
            )
            battle.addFighter(followFighter)
            added++
            logger.info {
                "Trận ${battle.battleId}: tướng slot=$followSlot ${npcDef.name} " +
                    "(npcId=${petData.npcId}) vào phe ${session.characterName} " +
                    "col=${position.first} row=${position.second}"
            }
        }
        return added
    }

    /**
     * Tạo nhân vật chiến đấu của tướng xuất chiến từ FollowNpcData
     *
     * Thuộc tính ưu tiên dùng FollowNpcData (tướng có thể đã lên cấp), kỹ năng lấy từ NpcDef.
     *
     * @param npcDef định nghĩa cơ bản của NPC (nguồn kỹ năng)
     * @param petData dữ liệu lúc chạy của tướng (nguồn thuộc tính)
     * @param masterId ID người chơi chủ
     * @param npcDataId ID loại NPC
     * @param col cột bàn cờ
     */
    /**
     * Stable legacy helper signature used by existing battle code/tests.
     * Bot-specific metadata stays in createFollowNpcFighterInternal so normal pet
     * behavior and reflective tests are not changed.
     */
    private fun createFollowNpcFighter(
        npcDef: NpcDef,
        petData: FollowNpcData,
        masterId: Long,
        ownerCharacterId: Long,
        npcDataId: Int,
        followSlot: Int,
        col: Int,
        row: Int,
    ): BattleFighter = createFollowNpcFighterInternal(
        npcDef = npcDef,
        petData = petData,
        masterId = masterId,
        ownerCharacterId = ownerCharacterId,
        npcDataId = npcDataId,
        followSlot = followSlot,
        col = col,
        row = row,
    )

    private fun createFollowNpcFighterInternal(
        npcDef: NpcDef,
        petData: FollowNpcData,
        masterId: Long,
        ownerCharacterId: Long,
        npcDataId: Int,
        followSlot: Int = 0,
        col: Int = BattleConstants.PLAYER_BACK_COL,
        row: Int = BattleConstants.CENTER_ROW,
        kind: Int = EHuman.FOLLOW_NPC,
        roleIdOverride: Long? = null,
        restoreFull: Boolean = false,
    ): BattleFighter {
        // Cộng dồn trang bị của tướng (Calc.GetMaxHp: hpx và EquipHpx cùng bậc được tính, EquipMaxHp cộng trực tiếp)
        val equipBonus = EquipBonusCalculator.calculateTotalBonus(
            petData.equips, itemDefs, sacredEquipPrimaryDefs
        )

        // DVT Calculator.lua handles FollowNpc with the normal stat formula.
        // Collection Style/Card bonuses are account-wide and also apply to followers.
        val collectInt = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 27) ?: 0
        val collectAtk = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 28) ?: 0
        val collectDef = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 29) ?: 0
        val collectAgi = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 30) ?: 0
        val collectHpx = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 31) ?: 0
        val collectSpx = collectionSystem?.getFollowerCollectionAttribute(ownerCharacterId, 32) ?: 0

        val petTurn = if (npcDef.turn == 2) 1 else 0
        val baseMaxHp = statSystem.calculateMaxHp(
            level = petData.level,
            hpx = petData.hpx + equipBonus.equipHpx + collectHpx,
            equipMaxHp = equipBonus.equipMaxHp,
            extraHp = petData.hpPillCount * 20,
            turn = petTurn
        )
        val maxHp = DvtPercentMath.applyFinalPercentFloor(baseMaxHp, equipBonus.hpPercent)
            .coerceAtLeast(1L)
        val maxSp = statSystem.calculateMaxSp(
            level = petData.level,
            spx = petData.spx + equipBonus.equipSpx + collectSpx,
            equipMaxSp = equipBonus.equipMaxSp,
            extraSp = petData.spPillCount * 5,
            turn = petTurn
        ).toLong()
        val hp = petData.hp.coerceIn(1L, maxHp)
        val sp = petData.sp.toLong().coerceIn(0L, maxSp)

        val equippedSoul = soulSystem?.equippedForNpc(ownerCharacterId, npcDataId)

        val normalSkills = npcDef.skills.take(3)
            .zip(listOf(petData.skill1Lv, petData.skill2Lv, petData.skill3Lv))
            .filter { (id, lv) -> id > 0 && lv > 0 }
            .associate { it.first to it.second }
            .toMutableMap()
        if (petData.canUseSpecialSkill && npcDef.specialSkill > 0 &&
            skillDefs[npcDef.specialSkill]?.let { it.useLimit != 0 && it.fightWay != 0 } == true
        ) {
            normalSkills[npcDef.specialSkill] = 1
        }
        val upgradeSkillLv = npcUpgradeDefs[petData.upgradeLv]?.skillLv ?: 0
        if (npcDef.upgradeSkill > 0 && upgradeSkillLv > 0 &&
            skillDefs[npcDef.upgradeSkill]?.let { it.useLimit != 0 && it.fightWay != 0 } == true
        ) {
            normalSkills[npcDef.upgradeSkill] = upgradeSkillLv
        }

        val battleSkillLevels = SoulSkillLevelMerge.merge(normalSkills, equippedSoul)

        return BattleFighter(
            roleId = roleIdOverride ?: npcDataId.toLong(),
            kind = kind,
            masterId = masterId,
            npcId = npcDataId,
            followSlot = followSlot,
            soulEquipId = equippedSoul?.itemId ?: 0,
            name = petData.name.ifEmpty { npcDef.name },
            col = col,
            row = row,
            maxHp = maxHp,
            maxSp = maxSp,
            hp = if (restoreFull) maxHp else hp,
            sp = if (restoreFull) maxSp else sp,
            level = petData.level,
            element = npcDef.element,
            atk = BattlePercentStatCalc.addBasePercent(petData.atk, equipBonus.atkPercent) +
                equipBonus.equipAtk + collectAtk,
            def = BattlePercentStatCalc.addBasePercent(petData.def, equipBonus.defPercent) +
                equipBonus.equipDef + collectDef,
            agi = petData.agi + equipBonus.equipAgi + collectAgi,
            intelligence = DvtPercentMath.addBasePercentRound(
                petData.intelligence, equipBonus.intPercent
            ) + equipBonus.equipInt + collectInt,
            atkPercent = equipBonus.atkPercent,
            defPercent = equipBonus.defPercent,
            crit = equipBonus.crit,
            critDamage = equipBonus.critDamage,
            dodge = equipBonus.dodge,
            hit = equipBonus.hit,
            increaseTotalDamage = equipBonus.increaseTotalDamage,
            damageReduction = equipBonus.damageReduction,
            reduceTotalDamage = equipBonus.reduceTotalDamage,
            increaseDamageTakenSelf = equipBonus.increaseDamageTakenSelf,
            skills = battleSkillLevels.keys.toIntArray(),
            skillLevels = battleSkillLevels
        )
    }

    /**
     * Tạo nhân vật chiến đấu của NPC từ NpcDef
     *
     * @param npcDataId số hiệu NPC (key của Npc_C.dat, client dùng npcDatas[roleId] tra sprite)
     */
    private fun createNpcFighter(npcDef: NpcDef, col: Int, row: Int, battleId: Long, npcDataId: Int): BattleFighter {
        val profile = NpcBattleProfileCalc.from(npcDef, skillDefs)

        logger.info {
            "Trận $battleId: NPC ${npcDef.name} (id=$npcDataId) lv=${profile.level} " +
                "hp=${profile.maxHp} sp=${profile.maxSp} atk=${profile.atk} def=${profile.def} " +
                "agi=${profile.agi} int=${profile.intelligence} turn=${profile.turn} " +
                "element=${profile.element} skills=${profile.skillLevels}"
        }

        return BattleFighter(
            roleId = npcDataId.toLong(),  // Số hiệu NPC → client dùng npcDatas[roleId] tra sprite
            kind = EHuman.MINE_NPC,
            npcId = npcDataId,
            name = npcDef.name,
            col = col,
            row = row,
            maxHp = profile.maxHp,
            maxSp = profile.maxSp,
            hp = profile.maxHp,
            sp = profile.maxSp,
            level = profile.level,
            element = profile.element,
            atk = profile.atk,
            def = profile.def,
            agi = profile.agi,
            intelligence = profile.intelligence,
            turn = profile.turn,
            skills = profile.skills,
            skillLevels = profile.skillLevels,
            passiveSkillId = profile.passiveSkillId,
            passiveSkillLevel = profile.passiveSkillLevel
        )
    }
}
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
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcDropDef
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.gamelogic.battle.*
import tw.tsm.protocol.BattleRoleData
import tw.tsm.protocol.BattleRoleSerializer
import tw.tsm.protocol.PacketWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * 戰鬥管理器 — 管理所有進行中的戰鬥
 *
 * 負責戰鬥的建立、指令分發、回合迴圈、結算。
 * 戰鬥結束後透過回呼恢復 NPC 事件流程（避免循環依賴 NpcEventHandler）。
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
    private val characterRepository: CharacterRepository? = null
) {
    /** 戰鬥道具解析器（無狀態，全戰鬥共用） */
    private val battleItemResolver = BattleItemResolver(inventorySystem, itemDefs)

    /**
     * 戰鬥結束回呼 — 由 ServerModule 設定，呼叫 NpcEventHandler.resumeAfterBattle()
     *
     * 參數：(session, playerId, playerWin)
     */
    var onBattleEndCallback: (suspend (GameSession, Long, Boolean) -> Unit)? = null
    /** 所有進行中的戰鬥（battleId → BattleInstance） */
    private val battles = ConcurrentHashMap<Long, BattleInstance>()

    /** 玩家 → 戰鬥 ID 的映射 */
    private val playerBattleMap = ConcurrentHashMap<Long, Long>()

    /** 戰鬥 ID 生成器 */
    private val nextBattleId = AtomicLong(1)

    /**
     * 建立 PvE 戰鬥（事件觸發）
     *
     * @param session 觸發的玩家
     * @param fightData 戰鬥配置（從 Eve_FightData 載入）
     * @param terrain 地質資訊
     */
    suspend fun createPveBattle(
        session: GameSession,
        fightData: EveFightData,
        terrain: Int = 0
    ) {
        if (isInBattle(session.playerId)) {
            logger.warn { "玩家 ${session.playerId} 已在戰鬥中，無法發起新戰鬥" }
            return
        }

        // terrain=0 代表未設定，fallback 到 101（CDN 中最小的有效 ffield）
        val effectiveTerrain = if (terrain == 0) 101 else terrain

        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs)
        val grid = BattleGrid()

        // 收集涉及的 NPC 定義（npcId + NpcDef）
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

        // 1. 判斷是否有出戰武將 — 有的話玩家退到後排、武將站前排
        val charId = session.roleId.toLong()
        val hasFightNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0
        val playerCol = if (hasFightNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val playerFighter = createPlayerFighter(session, col = playerCol)
        battle.addFighter(playerFighter)
        battle.playerSessions[session.playerId] = session

        // 1.5 若有出戰武將，加入棋盤前排（col=2）
        if (hasFightNpc) {
            addFollowNpcIfDeployed(session, battle, col = BattleConstants.PLAYER_FRONT_COL)
        }

        // 2. 將敵方 NPC 加入棋盤（左側 col=0-1，居中排列）
        addCenteredEnemies(fightData.leftEnemies, battle, battleId, involvedNpcs)

        // rightEnemies（通常為空，備用，同樣居中排列，使用玩家側列）
        addCenteredEnemies(
            fightData.rightEnemies, battle, battleId, involvedNpcs,
            frontCol = BattleConstants.PLAYER_FRONT_COL,
            backCol = BattleConstants.PLAYER_BACK_COL
        )

        // 3. 註冊戰鬥
        battles[battleId] = battle
        playerBattleMap[session.playerId] = battleId
        session.battleId = battleId

        logger.info { "戰鬥 $battleId 建立：玩家 ${session.characterName} vs ${involvedNpcs.size} 個敵人（事件 ${fightData.eveNo}）leftEnemies=${fightData.leftEnemies.size} rightEnemies=${fightData.rightEnemies.size}" }

        // 4. 發送戰鬥初始化封包
        sendBattleInit(battle)

        // 5. 啟動回合迴圈
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * 建立地圖 NPC 戰鬥（PK / 木人樁訓練）
     *
     * @param session 發起戰鬥的玩家
     * @param npcId NPC 編號
     * @param placementIndex 地圖擺放索引
     */
    suspend fun createMapNpcBattle(
        session: GameSession,
        npcId: Int,
        placementIndex: Int = 0
    ) {
        if (isInBattle(session.playerId)) {
            logger.warn { "玩家 ${session.playerId} 已在戰鬥中，無法發起新戰鬥" }
            return
        }

        val npcDef = npcDefs[npcId]
        if (npcDef == null) {
            logger.warn { "createMapNpcBattle: 找不到 NPC 定義 npcId=$npcId" }
            return
        }

        val effectiveTerrain = 101
        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs)
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

        // 1. 判斷是否有出戰武將 — 有的話玩家退到後排、武將站前排
        val charId = session.roleId.toLong()
        val hasFightNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0
        val playerCol = if (hasFightNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val playerFighter = createPlayerFighter(session, col = playerCol)
        battle.addFighter(playerFighter)
        battle.playerSessions[session.playerId] = session

        // 1.5 若有出戰武將，加入棋盤前排（col=2）
        if (hasFightNpc) {
            addFollowNpcIfDeployed(session, battle, col = BattleConstants.PLAYER_FRONT_COL)
        }

        // 2. 將敵方 NPC 加入棋盤 (col=BattleConstants.ENEMY_FRONT_COL, row=BattleConstants.CENTER_ROW)
        val enemyFighter = createNpcFighter(npcDef, BattleConstants.ENEMY_FRONT_COL, BattleConstants.CENTER_ROW, battleId, npcId)
        battle.addFighter(enemyFighter)

        // 3. 註冊戰鬥
        battles[battleId] = battle
        playerBattleMap[session.playerId] = battleId
        session.battleId = battleId

        logger.info { "戰鬥 $battleId 建立：玩家 ${session.characterName} vs MapNpc ${npcDef.name} (id=$npcId, placement=$placementIndex)" }

        // 4. 發送戰鬥初始化封包
        sendBattleInit(battle)

        // 5. 啟動回合迴圈
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * 建立 PvP 戰鬥（玩家 PK）
     *
     * @param attacker 發起 PK 的玩家
     * @param defender 被 PK 的玩家
     */
    suspend fun createPvpBattle(attacker: GameSession, defender: GameSession) {
        if (isInBattle(attacker.playerId) || isInBattle(defender.playerId)) {
            logger.warn { "PVP 建立失敗：玩家已在戰鬥中" }
            return
        }

        val battleId = nextBattleId.getAndIncrement()
        val skillResolver = BattleSkillResolver(skillDefs)
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

        // 攻擊方 → 右側（有武將時退到後排 col=3，否則前排 col=2）
        val atkCharId = attacker.roleId.toLong()
        val atkHasNpc = (petSystem.getCache(atkCharId)?.fightNpcId ?: 0) > 0
        val atkCol = if (atkHasNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val attackerFighter = createPlayerFighter(attacker, col = atkCol)
        battle.addFighter(attackerFighter)
        battle.playerSessions[attacker.playerId] = attacker
        if (atkHasNpc) addFollowNpcIfDeployed(attacker, battle, col = BattleConstants.PLAYER_FRONT_COL)

        // 防守方 → 左側（有武將時退到後排 col=1，否則前排 col=0）
        val defCharId = defender.roleId.toLong()
        val defHasNpc = (petSystem.getCache(defCharId)?.fightNpcId ?: 0) > 0
        val defCol = if (defHasNpc) BattleConstants.ENEMY_BACK_COL else BattleConstants.ENEMY_FRONT_COL
        val defenderFighter = createPlayerFighter(defender, col = defCol, kind = EHuman.PLAYERS)
        battle.addFighter(defenderFighter)
        battle.playerSessions[defender.playerId] = defender
        if (defHasNpc) addFollowNpcIfDeployed(defender, battle, col = BattleConstants.ENEMY_FRONT_COL)

        // 註冊戰鬥
        battles[battleId] = battle
        playerBattleMap[attacker.playerId] = battleId
        playerBattleMap[defender.playerId] = battleId
        attacker.battleId = battleId
        defender.battleId = battleId

        logger.info { "PVP 戰鬥 $battleId 建立：${attacker.characterName} vs ${defender.characterName}" }

        // 發送戰鬥初始化封包
        sendBattleInit(battle)

        // 啟動回合迴圈
        scope.launch {
            runBattleLoop(battle)
        }
    }

    /**
     * 處理玩家戰鬥指令（含武將）
     *
     * @param playerId 發送指令的玩家 ID
     * @param srcCol 發招者所在列（用於定位角色，武將的 roleId ≠ playerId）
     * @param srcRow 發招者所在行
     * @param action 選擇的行動
     */
    fun handlePlayerAction(playerId: Long, srcCol: Int, srcRow: Int, action: BattleAction): Boolean {
        val battleId = playerBattleMap[playerId] ?: return false
        val battle = battles[battleId] ?: return false

        if (battle.state != BattleInstance.State.WAITING_COMMANDS) {
            logger.debug { "戰鬥 $battleId: 目前不接受指令（state=${battle.state}）" }
            return false
        }

        return battle.setPlayerAction(playerId, srcCol, srcRow, action)
    }

    /**
     * 取得玩家所在戰鬥
     */
    fun getPlayerBattle(playerId: Long): BattleInstance? {
        val battleId = playerBattleMap[playerId] ?: return null
        return battles[battleId]
    }

    /**
     * 玩家是否在戰鬥中
     */
    fun isInBattle(playerId: Long): Boolean = playerBattleMap.containsKey(playerId)

    /**
     * 加入正在進行的戰鬥（參戰）
     *
     * 玩家點擊正在戰鬥中的角色「參戰」按鈕時呼叫。
     * 加入者會分配到與目標相同的陣營。
     *
     * @param joiner 要加入的玩家 session
     * @param battle 要加入的戰鬥實例
     * @param targetPlayerId 被點擊的目標玩家 ID（用於判斷陣營）
     * @return true=成功加入, false=加入失敗（隊伍滿）
     */
    suspend fun joinBattle(joiner: GameSession, battle: BattleInstance, targetPlayerId: Long): Boolean {
        // 1. 判斷目標在哪一方
        val targetOnRight = battle.isPlayerOnRightSide(targetPlayerId)
        if (targetOnRight == null) {
            logger.warn { "joinBattle: 目標 $targetPlayerId 不在戰鬥 ${battle.battleId} 中" }
            return false
        }

        val (frontCol, backCol) = if (targetOnRight) {
            BattleConstants.PLAYER_FRONT_COL to BattleConstants.PLAYER_BACK_COL
        } else {
            BattleConstants.ENEMY_FRONT_COL to BattleConstants.ENEMY_BACK_COL
        }

        // 2. 檢查是否有武將
        val charId = joiner.roleId.toLong()
        val hasNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0

        // 3. 找空位（有武將需要 2 格：武將前排、本體後排）
        if (hasNpc) {
            val npcSlot = battle.grid.findEmptySlot(frontCol, backCol)
            val playerSlot = battle.grid.findEmptySlot(frontCol, backCol)
            // 實際上需要確認兩格都有空位
            val occupied = battle.grid.countOccupied(frontCol, backCol)
            if (occupied + 2 > BattleConstants.MAX_ROW * 2) {
                logger.info { "joinBattle: 戰鬥 ${battle.battleId} 隊伍已滿（${occupied}/10），無法加入" }
                return false
            }
        } else {
            val occupied = battle.grid.countOccupied(frontCol, backCol)
            if (occupied >= BattleConstants.MAX_ROW * 2) {
                logger.info { "joinBattle: 戰鬥 ${battle.battleId} 隊伍已滿（${occupied}/10），無法加入" }
                return false
            }
        }

        // 4. 放置玩家本體
        val kind = if (targetOnRight) EHuman.PLAYER else EHuman.PLAYERS
        val playerCol = if (hasNpc) backCol else frontCol
        val playerSlot = battle.grid.findEmptySlot(
            if (hasNpc) backCol else frontCol,
            if (hasNpc) frontCol else backCol
        )
        if (playerSlot == null) {
            logger.info { "joinBattle: 戰鬥 ${battle.battleId} 找不到空格位" }
            return false
        }

        val joinerFighter = createPlayerFighter(joiner, col = playerSlot.first, kind = kind)
        // 覆寫 row（findEmptySlot 已決定位置）
        joinerFighter.row = playerSlot.second
        battle.addFighter(joinerFighter)
        battle.playerSessions[joiner.playerId] = joiner

        // 5. 放置武將
        if (hasNpc) {
            val npcSlot = battle.grid.findEmptySlot(frontCol, backCol)
            if (npcSlot != null) {
                addFollowNpcIfDeployed(joiner, battle, col = npcSlot.first, row = npcSlot.second)
            }
        }

        // 6. 註冊
        playerBattleMap[joiner.playerId] = battle.battleId
        joiner.battleId = battle.battleId

        // 7. 序列化新加入角色的資料（給已有玩家看 S:011-005）
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
                outfitItemIds = fighter.outfitItemIds
            )
        }
        val newRoleBytes = BattleRoleSerializer.serializeAll(newRoleDataList)

        // 8. 發送完整戰鬥資訊給加入者（S:011-250 + S:011-010）
        sendBattleInit(battle, onlyTo = joiner)

        // 9. 通知已有玩家：新角色加入（S:011-005）
        for (session in battle.playerSessions.values) {
            if (session.playerId != joiner.playerId) {
                responseSender.sendBattleJoinRoleAppear(session, newRoleBytes)
            }
        }

        // 10. 廣播地圖：場景玩家戰鬥類型更新（S:011-004）
        val warTypeWriter = PacketWriter(12)
            .writeByte(EHuman.PLAYER)
            .writeLongLE(joiner.playerId)
            .writeShortLE(0)
            .writeByte(EWar.JOIN)
        val warTypeEntries = warTypeWriter.toByteArray()
        // 通知同地圖所有玩家（包含加入者自己）
        // 此封包由 Handler 層透過 worldManager 廣播，這裡先不做
        // （BattleManager 不持有 worldManager 引用）

        logger.info { "戰鬥 ${battle.battleId}: ${joiner.characterName} 參戰（${if (targetOnRight) "右方" else "左方"}）" }
        return true
    }

    /**
     * 處理玩家斷線（戰鬥中斷線）
     */
    fun handleDisconnect(playerId: Long) {
        val battleId = playerBattleMap.remove(playerId) ?: return
        val battle = battles[battleId] ?: return

        battle.playerSessions.remove(playerId)
        logger.info { "戰鬥 $battleId: 玩家 $playerId 斷線離開" }

        // 如果沒有玩家了，結束戰鬥
        if (battle.playerSessions.isEmpty()) {
            battle.state = BattleInstance.State.FINISHED
        }
    }

    // ── 私有方法 ──

    /**
     * 將敵人先填前排再填後排，每排內居中排列
     *
     * 前排最多 MAX_ROW（5）隻，溢出到後排。
     * 居中公式：startRow = (MAX_ROW - count) / 2，每隻 row = startRow + index
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
        val backEnemies = enemies.drop(BattleConstants.MAX_ROW)
        addColumnCentered(frontEnemies, frontCol, battle, battleId, involvedNpcs)
        addColumnCentered(backEnemies, backCol, battle, battleId, involvedNpcs)
    }

    /**
     * 將一排敵人居中放置
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
                logger.warn { "戰鬥 $battleId: 找不到 NPC 定義 npcId=${enemy.npcId}" }
                return@forEachIndexed
            }
            involvedNpcs.add(enemy.npcId to npcDef)
            val npcFighter = createNpcFighter(npcDef, col, startRow + index, battleId, enemy.npcId)
            battle.addFighter(npcFighter)
        }
    }

    /**
     * 發送戰鬥初始化封包給所有參戰玩家（或指定玩家）
     *
     * @param onlyTo 若非 null，則只發給該玩家（用於參戰加入時）
     */
    private fun sendBattleInit(battle: BattleInstance, onlyTo: GameSession? = null) {
        // 序列化所有角色資料
        val roleDataList = battle.getAllFighters().map { fighter ->
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
                outfitItemIds = fighter.outfitItemIds
            )
        }
        val roleBytes = BattleRoleSerializer.serializeAll(roleDataList)

        val targets = if (onlyTo != null) listOf(onlyTo) else battle.playerSessions.values.toList()

        // S:011-250 戰鬥初始化
        for (session in targets) {
            responseSender.sendBattleInit(session, battle.terrain, battle.eventNo, roleBytes)
        }

        // S:011-010 戰場類型（Normal PvE）
        for (session in targets) {
            responseSender.sendBattleFieldType(session, EWarStyle.NORMAL, 0, 0, 0)
        }
    }

    /**
     * 回合迴圈協程
     */
    private suspend fun runBattleLoop(battle: BattleInstance) {
        try {
            // 等待客戶端載入戰鬥場景（進場動畫 ~1.5s + 網路延遲）
            delay(BattleConstants.SCENE_LOAD_DELAY_MS)

            battle.state = BattleInstance.State.COUNTDOWN

            while (battle.state != BattleInstance.State.FINISHED) {
                // 1. 發送倒數計時
                broadcastCountdown(battle)
                battle.state = BattleInstance.State.WAITING_COMMANDS

                // 2. 等待玩家指令（最多 20 秒 或所有玩家就緒）
                waitForCommands(battle, BattleConstants.COUNTDOWN_NORMAL * 1000L)

                // 3. NPC AI 決策
                battle.resolveNpcActions()

                // 4. 執行回合
                val roundResult = battle.resolveRound()

                // 5. 廣播回合結果
                broadcastRoundResult(battle, roundResult)

                // 5.5 逃跑成功 → 檢查是否所有玩家都逃跑了
                if (roundResult.escapeResults.isNotEmpty()) {
                    val escapedPlayerIds = roundResult.escapeResults
                        .filter { it.fighter.isHumanPlayer }
                        .map { it.fighter.roleId }
                        .toSet()

                    if (escapedPlayerIds.isNotEmpty()) {
                        // 所有玩家方都逃跑 → 戰鬥失敗結束
                        val remainingPlayers = battle.grid.getAlivePlayerSide()
                            .filter { it.isHumanPlayer }
                        if (remainingPlayers.isEmpty()) {
                            logger.info { "戰鬥 ${battle.battleId}: 所有玩家逃跑，戰鬥結束" }
                            handleBattleEnd(battle, BattleEndResult(playerWin = false))
                            break
                        }
                    }
                }

                // 6. 等待最後一個動畫播完
                delay(BattleConstants.ROUND_END_DELAY_MS)

                // 7. 檢查戰鬥結束
                val endResult = battle.checkBattleEnd()
                if (endResult != null) {
                    handleBattleEnd(battle, endResult)
                    break
                }

                battle.state = BattleInstance.State.COUNTDOWN
            }
        } catch (e: CancellationException) {
            logger.debug { "戰鬥 ${battle.battleId} 協程取消" }
        } catch (e: Exception) {
            logger.error(e) { "戰鬥 ${battle.battleId} 回合迴圈異常" }
        } finally {
            cleanupBattle(battle)
        }
    }

    /**
     * 發送倒數計時
     */
    private fun broadcastCountdown(battle: BattleInstance) {
        for (session in battle.playerSessions.values) {
            responseSender.sendBattleCountdown(session)
        }
    }

    /**
     * 等待玩家指令
     */
    private suspend fun waitForCommands(battle: BattleInstance, timeoutMs: Long) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (battle.allPlayersReady()) break
            if (battle.state == BattleInstance.State.FINISHED) break
            delay(200)  // 每 200ms 檢查一次
        }

        // 超時未行動的玩家操控角色自動普攻（含武將）
        for (fighter in battle.getAllFighters()) {
            if (fighter.isPlayerControlled && fighter.isAlive && !fighter.isStunned && !fighter.hasActed) {
                // 根據所在陣營選擇目標（PVP 中兩邊都是玩家）
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

                    // 發送 S:053-005 通知客戶端此角色已確認動作
                    // 客戶端 FightField.NextControlMan() 依賴此封包推進 conIdx
                    val ownerSession = findOwnerSession(battle, fighter)
                    if (ownerSession != null) {
                        sendActSetOK(ownerSession, fighter.col, fighter.row)
                    }
                }
            }
        }
    }

    /**
     * 廣播回合結果 — 整回合所有行動塞進「同一個」S:050-001
     *
     * 客戶端 RevAttackSkill 以封包為回合單位：
     * - 收包即 ClearAllAttack()（清除「前一回合」殘留演出）並設 isRound = true
     * - 包內以 while 迴圈解析多段行動，各演出由 hitCount 機制處理同目標並擊
     * 若拆成多包發送，後到的封包會把還在播放中的技能演出整批清除（特效被腰斬），
     * 且每包都被客戶端誤認為新回合開始。協定外層 <<…>> 重複結構即為此設計。
     */
    private suspend fun broadcastRoundResult(battle: BattleInstance, roundResult: RoundResult) {
        // 0. 逃跑成功者 — 先發 S:011-001 讓角色離開戰場
        for (escape in roundResult.escapeResults) {
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleExitFight(session, escape.fighter.col, escape.fighter.row)
            }
        }

        // 1. S:050-001 技能攻擊結果 — 整回合單一封包
        if (roundResult.actionResults.isNotEmpty()) {
            val skillData = serializeSkillResults(roundResult.actionResults)
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleSkillResult(session, skillData)
            }
        }

        // 1.2 S:023-009 戰鬥道具消耗 — 通知擁有者背包扣除
        for (consumed in roundResult.consumedItems) {
            battle.playerSessions[consumed.ownerPlayerId]?.let { ownerSession ->
                responseSender.sendReduceBagItem(ownerSession, consumed.bagSlot, consumed.amount)
            }
        }

        // 1.5 S:053-003 打飛（攻擊結果之後、狀態恢復之前）
        if (roundResult.flyOutTargets.isNotEmpty()) {
            delay(BattleConstants.FLY_OUT_ANIM_DELAY_MS)  // 等待傷害數字動畫開始
            for ((col, row) in roundResult.flyOutTargets) {
                for (session in battle.playerSessions.values) {
                    responseSender.sendBattleFlyOut(session, col, row)
                }
                logger.info { "戰鬥 ${battle.battleId}: 角色 ($col,$row) 被打飛" }
            }
        }

        // 2. S:053-001 狀態恢復
        if (roundResult.statusRestores.isNotEmpty()) {
            val statusData = serializeStatusRestores(roundResult.statusRestores)
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleRestoreStatus(session, statusData)
            }
        }

        // 3. 同步玩家 HP/SP 到 PlayerState + S:008-001 更新主角 HUD
        //    回合中如實同步（HP=0 = 死亡），保底 1 只在戰鬥結束時做
        for ((playerId, session) in battle.playerSessions) {
            val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId } ?: continue
            val charId = session.roleId.toLong()
            playerStateManager.setHp(charId, playerFighter.hp)
            playerStateManager.setSp(charId, playerFighter.sp.toInt())
            responseSender.sendSetAttribute(session, EAttribute.HP, playerFighter.hp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.SP, playerFighter.sp.toInt(), true)
        }
    }

    /**
     * 處理戰鬥結束
     */
    private suspend fun handleBattleEnd(battle: BattleInstance, endResult: BattleEndResult) {
        battle.state = BattleInstance.State.FINISHED
        logger.info { "戰鬥 ${battle.battleId} 結束：玩家${if (endResult.playerWin) "勝利" else "失敗"}" }

        for ((playerId, session) in battle.playerSessions) {
            // 獎勵（僅勝利時）
            if (endResult.playerWin) {
                val reward = battle.calculateRewards(session.level, npcDropTable)
                logger.info { "戰鬥 ${battle.battleId}: 玩家 $playerId 獲得 exp=${reward.exp} gold=${reward.gold} drops=${reward.drops.size}" }

                // 掉落物品動畫 + 實際發放
                if (reward.drops.isNotEmpty()) {
                    val charId = session.roleId.toLong()
                    // 找死亡敵人和玩家的位置（用於動畫）
                    val deadEnemy = battle.getAllFighters().firstOrNull { it.isEnemySide && it.isDead }
                    val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId }
                    for (drop in reward.drops) {
                        // S:053-004 掉寶動畫
                        responseSender.sendBattleDrop(
                            session,
                            drop.itemId,
                            fromCol = deadEnemy?.col ?: 0,
                            fromRow = deadEnemy?.row ?: 2,
                            toCol = playerFighter?.col ?: 2,
                            toRow = playerFighter?.row ?: 0
                        )
                        // 實際加入背包
                        val addResult = inventorySystem.addItemToBag(charId, drop.itemId, drop.quantity)
                        if (addResult != null) {
                            val (slot, thing) = addResult
                            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
                        }
                        logger.info { "戰鬥 ${battle.battleId}: 玩家 $playerId 獲得物品 itemId=${drop.itemId} x${drop.quantity}" }
                    }
                }

                // 經驗發放
                val charId = session.roleId.toLong()
                val state = playerStateManager.getState(charId)
                if (state != null && reward.exp > 0) {
                    distributeExp(session, charId, state, reward.exp)
                }

                // 金錢發放
                if (reward.gold > 0) {
                    val goldCharId = session.roleId.toLong()
                    if (moneySystem.addGold(goldCharId, reward.gold)) {
                        responseSender.sendAddMoney(session, reward.gold)
                        logger.info { "戰鬥 ${battle.battleId}: 玩家 $playerId 獲得金錢 ${reward.gold}" }
                    }
                }
            }

            // 捕捉成功的 NPC → 加入 PetSystem + 發送客戶端封包
            if (endResult.playerWin && battle.capturedNpcs.isNotEmpty()) {
                val charId = session.roleId.toLong()
                for (npcId in battle.capturedNpcs) {
                    val npcDef = npcDefs[npcId]
                    if (npcDef == null) {
                        logger.warn { "戰鬥 ${battle.battleId}: 捕獲 npcId=$npcId 但 npcDefs 無此定義" }
                        continue
                    }
                    val added = petSystem.addFollowNpc(charId, npcId, npcDef, canGrow = 1)
                    if (added != null) {
                        val (slotIndex, pet) = added
                        responseSender.sendFollowNpcAdd(session, playerId, slotIndex, npcId, pet.canGrow, showMsg = true)
                        logger.info { "戰鬥 ${battle.battleId}: 玩家 $playerId 捕獲武將 npcId=$npcId → slot=$slotIndex" }
                    } else {
                        logger.warn { "戰鬥 ${battle.battleId}: 武將 npcId=$npcId 加入失敗（重複或隊伍滿）" }
                    }
                }
                // 同步完整武將資料給客戶端
                val allPets = petSystem.getFollowPets(charId)
                if (allPets.isNotEmpty()) {
                    responseSender.sendFollowNpcFullData(session, allPets.values.toList())
                }
            }

            // 同步玩家最終 HP/SP 到 PlayerState + 更新主角 HUD
            val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId }
            if (playerFighter != null) {
                val syncCharId = session.roleId.toLong()
                // HP 保底 1：戰敗時 HP=0 → 強制為 1（瀕死狀態），避免下次進戰鬥直接躺著
                val syncHp = playerFighter.hp.coerceAtLeast(1)
                playerStateManager.setHp(syncCharId, syncHp)
                playerStateManager.setSp(syncCharId, playerFighter.sp.toInt())
                responseSender.sendSetAttribute(session, EAttribute.HP, syncHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.SP, playerFighter.sp.toInt(), true)
            }

            // 同步武將最終 HP/SP 到 PetSystem
            val followNpcFighter = battle.getAllFighters().firstOrNull {
                it.kind == EHuman.FOLLOW_NPC && it.masterId == playerId
            }
            if (followNpcFighter != null) {
                val syncCharId = session.roleId.toLong()
                petSystem.updateFightNpcHpSp(syncCharId, followNpcFighter.hp, followNpcFighter.sp.toInt())
            }

            // S:011-000 結束戰鬥
            responseSender.sendBattleEnd(session, playerId, 0)

            // 機關盒補給觸發：戰鬥結束後通知客戶端進行補品/HP/SP 補給（比照 PC/手遊原版戰後自動補血補魔）
            responseSender.sendMacroBoxSupply(session, checkItem = true)
        }

        // 延遲後發送離場
        delay(500)

        for (fighter in battle.getAllFighters()) {
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleExitFight(session, fighter.col, fighter.row)
            }
        }

        // PVP 戰鬥與地圖 NPC PK 不走事件回呼
        if (battle.warType == EWar.PK_PLAYERS || battle.warType == EWar.PK_MAP_NPC) {
            logger.info { "非事件戰鬥 ${battle.battleId} 結束，不觸發事件回呼" }
            return
        }

        // PVE 事件戰鬥：恢復 NPC 事件流程（透過回呼，由 NpcEventHandler 處理）
        delay(300)
        val callback = onBattleEndCallback
        if (callback != null) {
            for ((playerId, session) in battle.playerSessions) {
                try {
                    callback(session, playerId, endResult.playerWin)
                } catch (e: Exception) {
                    logger.error(e) { "戰鬥 ${battle.battleId}: 事件恢復回呼異常 playerId=$playerId" }
                }
            }
        } else {
            // 無回呼（測試環境等），直接結束事件
            for (session in battle.playerSessions.values) {
                responseSender.sendEventEnd(session)
            }
        }
    }

    /**
     * 發放經驗值並處理升級
     *
     * 累加經驗 → 檢查升級 → 重算 maxHP/maxSP → 通知客戶端
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
                val newMaxHp = statSystem.calculateMaxHp(
                    level = PLAYER_MAX_LEVEL,
                    hpx = state.hpx + state.equipHpx + turn3Lv.coerceAtMost(70) + state.turn3Hpx,
                    equipMaxHp = state.equipMaxHp,
                    extraHp = 0,
                    turn = state.turn,
                    career = state.career,
                    turn3Lv = turn3Lv
                )
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
                logger.info { "三轉升級：charId=$charId +$gained → Lv.$level, exp=$exp" }
            } else {
                logger.info { "三轉經驗：charId=$charId +$expGain → $exp" }
            }
            return
        }
        synchronized(state) {
            state.exp += expGain
            // 通知客戶端經驗變化（EAttribute.EXP = 36）
            responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)

            // 升級迴圈
            var levelsGained = 0
            while (true) {
                val upExp = statSystem.calculateUpExp(state.level, state.turn)
                if (state.exp < upExp) break
                state.exp -= upExp
                state.level++
                levelsGained++
            }

            if (levelsGained > 0) {
                // 每級 +2 技能點、+3 屬性點
                val skillPointGain = levelsGained * 2
                val attrPointGain = levelsGained * 3
                state.skillPoint += skillPointGain
                state.attrPoint += attrPointGain
                skillSystem?.adjustSkillPoint(charId, skillPointGain)

                // 重算 maxHP/maxSP（含裝備加成）
                val newMaxHp = statSystem.calculateMaxHp(
                    level = state.level, hpx = state.hpx,
                    equipMaxHp = state.equipMaxHp, extraHp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                val newMaxSp = statSystem.calculateMaxSp(
                    level = state.level, spx = state.spx,
                    equipMaxSp = state.equipMaxSp, extraSp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )

                // 升級回滿
                playerStateManager.updateMaxHp(charId, newMaxHp)
                playerStateManager.updateMaxSp(charId, newMaxSp)
                playerStateManager.fullHp(charId)
                playerStateManager.fullSp(charId)

                // 通知客戶端（SetAttribute 是絕對設定，必須發送總量而非增量）
                responseSender.sendSetAttribute(session, EAttribute.LV, state.level, true)
                responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.MAX_HP, newMaxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.HP, newMaxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.MAX_SP, newMaxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SP, newMaxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SKILL_POINT, state.skillPoint, true)
                responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, state.attrPoint, true)

                // 更新 session
                session.level = state.level

                logger.info { "升級：charId=$charId 升了 $levelsGained 級 → Lv.${state.level} 技能點+$skillPointGain 屬性點+$attrPointGain" }
            }
        }
    }

    /**
     * 清理戰鬥資源
     */
    private fun cleanupBattle(battle: BattleInstance) {
        battles.remove(battle.battleId)
        for (playerId in battle.playerSessions.keys) {
            playerBattleMap.remove(playerId)
            battle.playerSessions[playerId]?.let { session ->
                session.battleId = 0
            }
        }
        logger.debug { "戰鬥 ${battle.battleId} 資源已清理" }
    }

    companion object {
        private const val PLAYER_MAX_LEVEL = 200
        private const val TURN3_EXTRA_LEVELS = 50
        private const val TURN3_MAX_LEVEL = PLAYER_MAX_LEVEL + TURN3_EXTRA_LEVELS
    }

    // ── 封包序列化輔助 ──

    /**
     * 序列化 S:050-001 技能結果資料
     *
     * 格式：迴圈 [長度(2) + 攻擊者列(1) + 行(1) + 技能ID(2) + 場地(1)
     *        + 目標數(1) + 迴圈 [目標列(1) + 目標行(1) + 攻擊結果(1) + 動作(1)
     *        + 屬性數(1) + 迴圈 [種類(1) + 值(4) + 正負(1)]]]
     */
    private fun serializeSkillResults(results: List<ActionResult>): ByteArray {
        val outer = PacketWriter(256)
        for (result in results) {
            // 分段組裝：先寫內容到暫存，再加長度前綴
            val segment = PacketWriter(64)
            segment.writeByte(result.attackerCol)     // 攻擊者列
            segment.writeByte(result.attackerRow)     // 攻擊者行
            segment.writeShortLE(result.skillId)      // 技能 ID
            segment.writeByte(result.fightArea)       // 場地
            segment.writeByte(result.targets.size)    // 目標數

            for (target in result.targets) {
                segment.writeByte(target.targetCol)          // 目標列
                segment.writeByte(target.targetRow)          // 目標行
                segment.writeByte(target.attackResult)        // 攻擊結果
                segment.writeByte(target.beHitAction)         // 目標動作
                segment.writeByte(target.attrChanges.size)    // 屬性數

                for (change in target.attrChanges) {
                    segment.writeByte(change.attrKind)        // 屬性種類
                    segment.writeIntLE(change.value)          // 數值
                    segment.writeByte(if (change.isPositive) 0 else 1) // 正負：0=加, 1=扣
                }
            }

            val segmentBytes = segment.toByteArray()
            outer.writeShortLE(segmentBytes.size)    // 長度前綴
            outer.writeBytes(segmentBytes)           // 內容
        }
        return outer.toByteArray()
    }

    /**
     * 序列化 S:053-001 狀態恢復資料
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
     * 發送 S:053-005 動作設定完成
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
     * 找到角色的主人 session（玩家本人或武將的主人）
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

    // ── 角色建立輔助 ──

    /**
     * 從 GameSession 建立玩家戰鬥角色
     *
     * 屬性來源：PlayerStateManager（登入時由 PlayerDataDto + StatSystem 計算）
     * 裝備來源：InventorySystem（用於戰鬥外觀序列化）
     */
    private suspend fun createPlayerFighter(
        session: GameSession,
        col: Int = BattleConstants.PLAYER_FRONT_COL,
        kind: Int = EHuman.PLAYER
    ): BattleFighter {
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
            ?: error("玩家 charId=$charId (playerId=${session.playerId}) 無運行時狀態")

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

        return BattleFighter(
            roleId = session.playerId,
            kind = kind,
            name = session.characterName,
            col = col,
            row = BattleConstants.CENTER_ROW,
            maxHp = state.maxHp,
            maxSp = state.maxSp.toLong(),
            hp = state.hp,
            sp = state.sp.toLong(),
            level = state.level,
            element = state.element,
            atk = state.atk + state.equipAtk + state.turn3Atk,
            def = state.def + state.equipDef + state.turn3Def,
            agi = state.agi + state.equipAgi + state.turn3Agi,
            intelligence = state.intelligence + state.equipInt + state.turn3Int,
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
            hair = session.hair,
            color1 = session.color1,
            color2 = session.color2,
            equipItemIds = equipIds
        )
    }

    /**
     * 若玩家有出戰武將，將其加入戰鬥棋盤
     *
     * @param session 玩家 session
     * @param battle 戰鬥實例
     * @param col 武將放置的列（預設 PLAYER_BACK_COL=3，PVP 防守方用 ENEMY_BACK_COL=0）
     */
    private fun addFollowNpcIfDeployed(
        session: GameSession,
        battle: BattleInstance,
        col: Int = BattleConstants.PLAYER_BACK_COL,
        row: Int = BattleConstants.CENTER_ROW
    ) {
        val charId = session.roleId.toLong()
        val fightNpcId = petSystem.getCache(charId)?.fightNpcId ?: 0
        if (fightNpcId <= 0) return

        val npcDef = npcDefs[fightNpcId]
        val petData = petSystem.getFollowPets(charId).values.find { it.npcId == fightNpcId }
        if (npcDef == null || petData == null) {
            logger.warn { "出戰武將 npcId=$fightNpcId 但找不到定義或資料" }
            return
        }

        val followFighter = createFollowNpcFighter(npcDef, petData, session.playerId, fightNpcId, col, row)
        battle.addFighter(followFighter)
        logger.info { "戰鬥 ${battle.battleId}: 武將 ${npcDef.name} (npcId=$fightNpcId) 加入玩家 ${session.characterName} 方 col=$col row=$row" }
    }

    /**
     * 從 FollowNpcData 建立出戰武將戰鬥角色
     *
     * 屬性優先使用 FollowNpcData（武將可能已升級），技能從 NpcDef 取得。
     *
     * @param npcDef NPC 基礎定義（技能來源）
     * @param petData 武將運行時資料（屬性來源）
     * @param masterId 主人玩家 ID
     * @param npcDataId NPC 種類 ID
     * @param col 棋盤列
     */
    private fun createFollowNpcFighter(
        npcDef: NpcDef,
        petData: FollowNpcData,
        masterId: Long,
        npcDataId: Int,
        col: Int = BattleConstants.PLAYER_BACK_COL,
        row: Int = BattleConstants.CENTER_ROW
    ): BattleFighter {
        // 武將裝備加成（Calc.GetMaxHp：hpx 與 EquipHpx 同階計入，EquipMaxHp 直接相加）
        val equipBonus = EquipBonusCalculator.calculateTotalBonus(petData.equips, itemDefs)

        val hp = petData.hp.coerceAtLeast(1)
        val maxHp = (npcDef.hpBase + petData.level * (npcDef.hpx + equipBonus.equipHpx) + equipBonus.equipMaxHp)
            .toLong().coerceAtLeast(hp)
        val sp = petData.sp.toLong()
        val maxSp = (npcDef.spBase + petData.level * (npcDef.spx + equipBonus.equipSpx) + equipBonus.equipMaxSp)
            .toLong().coerceAtLeast(sp)

        return BattleFighter(
            roleId = npcDataId.toLong(),
            kind = EHuman.FOLLOW_NPC,
            masterId = masterId,
            npcId = npcDataId,
            name = petData.name.ifEmpty { npcDef.name },
            col = col,
            row = row,
            maxHp = maxHp,
            maxSp = maxSp,
            hp = hp,
            sp = sp,
            level = petData.level,
            element = npcDef.element,
            atk = petData.atk + equipBonus.equipAtk,
            def = petData.def + equipBonus.equipDef,
            agi = petData.agi + equipBonus.equipAgi,
            intelligence = petData.intelligence + equipBonus.equipInt,
            skills = npcDef.skills.filter { it > 0 }.toIntArray()
        )
    }

    /**
     * 從 NpcDef 建立 NPC 戰鬥角色
     *
     * @param npcDataId NPC 編號（Npc_C.dat 的 key，客戶端用 npcDatas[roleId] 查精靈圖）
     */
    private fun createNpcFighter(npcDef: NpcDef, col: Int, row: Int, battleId: Long, npcDataId: Int): BattleFighter {
        // NPC HP = hpBase + level * hpx
        val hp = (npcDef.hpBase + npcDef.level * npcDef.hpx).toLong()
        val sp = (npcDef.spBase + npcDef.level * npcDef.spx).toLong()

        logger.info { "戰鬥 $battleId: NPC ${npcDef.name} (id=$npcDataId) lv=${npcDef.level} " +
            "hp=$hp atk=${npcDef.atk} def=${npcDef.def} agi=${npcDef.agi} int=${npcDef.intelligence} " +
            "element=${npcDef.element} skills=${npcDef.skills.filter { it > 0 }}" }

        return BattleFighter(
            roleId = npcDataId.toLong(),  // NPC 編號 → 客戶端用 npcDatas[roleId] 查精靈圖
            kind = EHuman.MINE_NPC,
            npcId = npcDataId,
            name = npcDef.name,
            col = col,
            row = row,
            maxHp = hp,
            maxSp = sp,
            hp = hp,
            sp = sp,
            level = npcDef.level,
            element = npcDef.element,
            atk = npcDef.atk,
            def = npcDef.def,
            agi = npcDef.agi,
            intelligence = npcDef.intelligence,
            skills = npcDef.skills.filter { it > 0 }.toIntArray()
        )
    }
}

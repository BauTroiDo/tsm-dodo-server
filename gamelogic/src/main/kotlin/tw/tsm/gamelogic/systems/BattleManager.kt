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
import tw.tsm.domain.data.NpcUpgradeDef
import tw.tsm.domain.data.SkillDef
import tw.tsm.domain.data.SceneSetDef
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
) {
    /** Trình phân tích vật phẩm chiến đấu (stateless, dùng chung cho toàn trận)*/
    private val battleItemResolver = BattleItemResolver(inventorySystem, itemDefs)

    /**
     * Callback khi kết thúc trận — do ServerModule thiết lập, gọi NpcEventHandler.resumeAfterBattle()
     *
     * Tham số: (session, playerId, playerWin)
     */
    var onBattleEndCallback: (suspend (GameSession, Long, Boolean) -> Unit)? = null
    /** Mọi trận đấu đang diễn ra (battleId → BattleInstance)*/
    private val battles = ConcurrentHashMap<Long, BattleInstance>()

    /** Ánh xạ người chơi → ID trận đấu*/
    private val playerBattleMap = ConcurrentHashMap<Long, Long>()

    /** Trình tạo ID trận đấu*/
    private val nextBattleId = AtomicLong(1)

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
            if (pc.follow.size >= petSystem.getFollowCapacity(session.roleId.toLong())) return@validator false
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
        val skillResolver = BattleSkillResolver(skillDefs)
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

        // 1. Phán định có tướng xuất chiến hay không — nếu có thì người chơi lùi về hàng sau, tướng đứng hàng trước
        val charId = session.roleId.toLong()
        val hasFightNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0
        val playerCol = if (hasFightNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val playerFighter = createPlayerFighter(session, col = playerCol)
        battle.addFighter(playerFighter)
        battle.playerSessions[session.playerId] = session

        // 1.5 Nếu có tướng xuất chiến, thêm vào hàng trước bàn cờ (col=2)
        if (hasFightNpc) {
            addFollowNpcIfDeployed(session, battle, col = BattleConstants.PLAYER_FRONT_COL)
        }

        // 2. Thêm NPC phe địch vào bàn cờ (bên trái col=0-1, sắp canh giữa)
        addCenteredEnemies(fightData.leftEnemies, battle, battleId, involvedNpcs)

        // rightEnemies (thường để trống, dự phòng, cũng sắp canh giữa, dùng cột phía người chơi)
        addCenteredEnemies(
            fightData.rightEnemies, battle, battleId, involvedNpcs,
            frontCol = BattleConstants.PLAYER_FRONT_COL,
            backCol = BattleConstants.PLAYER_BACK_COL
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

        // 1. Phán định có tướng xuất chiến hay không — nếu có thì người chơi lùi về hàng sau, tướng đứng hàng trước
        val charId = session.roleId.toLong()
        val hasFightNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0
        val playerCol = if (hasFightNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val playerFighter = createPlayerFighter(session, col = playerCol)
        battle.addFighter(playerFighter)
        battle.playerSessions[session.playerId] = session

        // 1.5 Nếu có tướng xuất chiến, thêm vào hàng trước bàn cờ (col=2)
        if (hasFightNpc) {
            addFollowNpcIfDeployed(session, battle, col = BattleConstants.PLAYER_FRONT_COL)
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

        // Phe tấn công → bên phải (có tướng thì lùi hàng sau col=3, không thì hàng trước col=2)
        val atkCharId = attacker.roleId.toLong()
        val atkHasNpc = (petSystem.getCache(atkCharId)?.fightNpcId ?: 0) > 0
        val atkCol = if (atkHasNpc) BattleConstants.PLAYER_BACK_COL else BattleConstants.PLAYER_FRONT_COL
        val attackerFighter = createPlayerFighter(attacker, col = atkCol)
        battle.addFighter(attackerFighter)
        battle.playerSessions[attacker.playerId] = attacker
        if (atkHasNpc) addFollowNpcIfDeployed(attacker, battle, col = BattleConstants.PLAYER_FRONT_COL)

        // Bên phòng thủ → bên trái (có tướng thì lùi về hàng sau col=1, ngược lại hàng trước col=0)
        val defCharId = defender.roleId.toLong()
        val defHasNpc = (petSystem.getCache(defCharId)?.fightNpcId ?: 0) > 0
        val defCol = if (defHasNpc) BattleConstants.ENEMY_BACK_COL else BattleConstants.ENEMY_FRONT_COL
        val defenderFighter = createPlayerFighter(defender, col = defCol, kind = EHuman.PLAYERS)
        battle.addFighter(defenderFighter)
        battle.playerSessions[defender.playerId] = defender
        if (defHasNpc) addFollowNpcIfDeployed(defender, battle, col = BattleConstants.ENEMY_FRONT_COL)

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

        // 2. Kiểm tra có tướng hay không
        val charId = joiner.roleId.toLong()
        val hasNpc = (petSystem.getCache(charId)?.fightNpcId ?: 0) > 0

        // 3. Tìm chỗ trống (có tướng cần 2 ô: tướng hàng trước, bản thân hàng sau)
        if (hasNpc) {
            val npcSlot = battle.grid.findEmptySlot(frontCol, backCol)
            val playerSlot = battle.grid.findEmptySlot(frontCol, backCol)
            // Thực tế cần xác nhận cả hai ô đều trống
            val occupied = battle.grid.countOccupied(frontCol, backCol)
            if (occupied + 2 > BattleConstants.MAX_ROW * 2) {
                logger.info { "joinBattle: trận ${battle.battleId} đội đã đầy (${occupied}/10), không thể tham gia" }
                return false
            }
        } else {
            val occupied = battle.grid.countOccupied(frontCol, backCol)
            if (occupied >= BattleConstants.MAX_ROW * 2) {
                logger.info { "joinBattle: trận ${battle.battleId} đội đã đầy (${occupied}/10), không thể tham gia" }
                return false
            }
        }

        // 4. Đặt bản thân người chơi
        val kind = if (targetOnRight) EHuman.PLAYER else EHuman.PLAYERS
        val playerCol = if (hasNpc) backCol else frontCol
        val playerSlot = battle.grid.findEmptySlot(
            if (hasNpc) backCol else frontCol,
            if (hasNpc) frontCol else backCol
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

        // 5. Đặt tướng
        if (hasNpc) {
            val npcSlot = battle.grid.findEmptySlot(frontCol, backCol)
            if (npcSlot != null) {
                addFollowNpcIfDeployed(joiner, battle, col = npcSlot.first, row = npcSlot.second)
            }
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
                outfitItemIds = fighter.outfitItemIds
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
        val backEnemies = enemies.drop(BattleConstants.MAX_ROW)
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

    /**
     * Gửi gói tin khởi tạo chiến đấu cho mọi người chơi tham chiến (hoặc người chơi chỉ định)
     *
     * @param onlyTo nếu khác null thì chỉ gửi cho người chơi đó (dùng khi tham chiến)
     */
    private fun sendBattleInit(battle: BattleInstance, onlyTo: GameSession? = null) {
        // Tuần tự hoá dữ liệu của mọi nhân vật
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
     * Coroutine vòng lặp hiệp đấu
     */
    private suspend fun runBattleLoop(battle: BattleInstance) {
        try {
            // Chờ client tải cảnh chiến đấu (hoạt ảnh vào trận ~1.5s + độ trễ mạng)
            delay(BattleConstants.SCENE_LOAD_DELAY_MS)

            battle.state = BattleInstance.State.COUNTDOWN

            while (battle.state != BattleInstance.State.FINISHED) {
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
            if (actionResult.comboWithPrevious && actionGroups.isNotEmpty()) {
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

            // Keep the deployed pet's persistent/current SP authoritative after every round.
            // FightField deducts SP locally during animation, while BattleSkillResolver deducts
            // it server-side; S:008-002 prevents the out-of-battle pet object from retaining
            // stale/full SP and being restored as if skills were free.
            val petFighter = battle.getAllFighters().firstOrNull {
                it.kind == EHuman.FOLLOW_NPC && it.masterId == playerId
            }
            if (petFighter != null) {
                val slot = petSystem.getFollowPets(charId).entries
                    .firstOrNull { it.value.npcId == petFighter.npcId }
                    ?.key
                petSystem.updateFightNpcHpSp(charId, petFighter.hp, petFighter.sp.toInt())
                if (slot != null) {
                    responseSender.sendFollowNpcAttribute(session, slot, EAttribute.HP, petFighter.hp.toInt())
                    responseSender.sendFollowNpcAttribute(session, slot, EAttribute.SP, petFighter.sp.toInt())
                }
            }
        }
    }

    /**
     * Xử lý kết thúc trận đấu
     */
    private suspend fun handleBattleEnd(battle: BattleInstance, endResult: BattleEndResult) {
        battle.state = BattleInstance.State.FINISHED
        logger.info { "Trận ${battle.battleId} kết thúc: người chơi${if (endResult.playerWin) "勝利" else "失敗"}" }

        for ((playerId, session) in battle.playerSessions) {
            // Thưởng (chỉ khi thắng)
            if (endResult.playerWin) {
                val reward = battle.calculateRewards(session.level, npcDropTable)
                logger.info { "Trận ${battle.battleId}: người chơi $playerId nhận exp=${reward.exp} gold=${reward.gold} drops=${reward.drops.size}" }

                // Hoạt ảnh rơi vật phẩm + thực tế phát ra
                if (reward.drops.isNotEmpty()) {
                    val charId = session.roleId.toLong()
                    // Tìm vị trí kẻ địch và người chơi đã chết (dùng cho hoạt ảnh)
                    val deadEnemy = battle.getAllFighters().firstOrNull { it.isEnemySide && it.isDead }
                    val playerFighter = battle.getAllFighters().firstOrNull { it.roleId == playerId }
                    for (drop in reward.drops) {
                        // S:053-004 hoạt ảnh rơi đồ
                        responseSender.sendBattleDrop(
                            session,
                            drop.itemId,
                            fromCol = deadEnemy?.col ?: 0,
                            fromRow = deadEnemy?.row ?: 2,
                            toCol = playerFighter?.col ?: 2,
                            toRow = playerFighter?.row ?: 0
                        )
                        // Thực sự thêm vào túi đồ
                        val addResult = inventorySystem.addItemToBag(charId, drop.itemId, drop.quantity)
                        if (addResult != null) {
                            val (slot, thing) = addResult
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

                // Võ tướng đang xuất chiến và còn sống nhận EXP riêng theo cấp của chính nó.
                // DVT UIStatus/NpcData đọc EAttribute.Exp dạng cumulative lifetime EXP.
                val activePetFighter = battle.getAllFighters().firstOrNull {
                    it.kind == EHuman.FOLLOW_NPC && it.masterId == playerId
                }
                if (activePetFighter != null && activePetFighter.isAlive) {
                    val petEntry = petSystem.getFollowPets(charId).entries
                        .firstOrNull { it.value.npcId == activePetFighter.npcId }
                    if (petEntry != null) {
                        val petExp = BattleRewardCalc.calculateExp(
                            battle.involvedNpcDefs,
                            petEntry.value.level
                        )
                        val gain = petSystem.gainBattleExp(charId, petEntry.key, petExp)
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
                                // PC TSPet.levelUp restores HP/SP; client recomputes
                                // MaxHP/MaxSP from the new level and these absolute values.
                                responseSender.sendFollowNpcAttribute(
                                    session, gain.slotIndex, EAttribute.HP, gain.after.hp.toInt()
                                )
                                responseSender.sendFollowNpcAttribute(
                                    session, gain.slotIndex, EAttribute.SP, gain.after.sp
                                )
                            }
                            logger.info {
                                "Trận ${battle.battleId}: tướng npcId=${gain.after.npcId} " +
                                    "nhận exp=$petExp lv=${gain.before.level}->${gain.after.level}"
                            }
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
            if (endResult.playerWin && battle.capturedNpcs.isNotEmpty()) {
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
                        dailyProgressSystem?.incrementRoleCount(
                            session,
                            DailyProgressSystem.CATCH_SUCCESS,
                        )
                        logger.info { "Trận ${battle.battleId}: người chơi $playerId bắt được tướng npcId=$npcId → slot=$slotIndex" }
                    } else {
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

        // Trì hoãn rồi gửi gói rời đi
        delay(500)

        for (fighter in battle.getAllFighters()) {
            for (session in battle.playerSessions.values) {
                responseSender.sendBattleExitFight(session, fighter.col, fighter.row)
            }
        }

        // Trận PVP và PK với NPC trên bản đồ không đi qua callback sự kiện
        if (battle.warType == EWar.PK_PLAYERS || battle.warType == EWar.PK_MAP_NPC) {
            logger.info { "Trận ${battle.battleId} không thuộc sự kiện đã kết thúc, không gọi callback sự kiện" }
            return
        }

        // Trận chiến sự kiện PVE: phục hồi luồng sự kiện NPC (thông qua callback, do NpcEventHandler xử lý)
        delay(300)
        val callback = onBattleEndCallback
        if (callback != null) {
            for ((playerId, session) in battle.playerSessions) {
                try {
                    callback(session, playerId, endResult.playerWin)
                } catch (e: Exception) {
                    logger.error(e) { "Trận ${battle.battleId}: callback phục hồi sự kiện lỗi playerId=$playerId" }
                }
            }
        } else {
            // Không có callback (môi trường thử nghiệm v.v.), kết thúc sự kiện trực tiếp
            for (session in battle.playerSessions.values) {
                responseSender.sendEventEnd(session)
            }
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

                // Tính lại maxHP/maxSP (bao gồm cộng dồn trang bị)
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
    private fun cleanupBattle(battle: BattleInstance) {
        battles.remove(battle.battleId)
        for (playerId in battle.playerSessions.keys) {
            playerBattleMap.remove(playerId)
            battle.playerSessions[playerId]?.let { session ->
                session.battleId = 0
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
        kind: Int = EHuman.PLAYER
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
     * Nếu người chơi có tướng xuất chiến, thêm tướng đó vào bàn cờ chiến đấu
     *
     * @param session session người chơi
     * @param battle instance trận đấu
     * @param col cột đặt tướng (mặc định PLAYER_BACK_COL=3, phe thủ trong PVP dùng ENEMY_BACK_COL=0)
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
            logger.warn { "Tướng xuất chiến npcId=$fightNpcId nhưng không tìm thấy định nghĩa hoặc dữ liệu" }
            return
        }

        val followFighter = createFollowNpcFighter(npcDef, petData, session.playerId, fightNpcId, col, row)
        battle.addFighter(followFighter)
        logger.info { "Trận ${battle.battleId}: tướng ${npcDef.name} (npcId=$fightNpcId) vào phe người chơi ${session.characterName} col=$col row=$row" }
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
    private fun createFollowNpcFighter(
        npcDef: NpcDef,
        petData: FollowNpcData,
        masterId: Long,
        npcDataId: Int,
        col: Int = BattleConstants.PLAYER_BACK_COL,
        row: Int = BattleConstants.CENTER_ROW
    ): BattleFighter {
        // Cộng dồn trang bị của tướng (Calc.GetMaxHp: hpx và EquipHpx cùng bậc được tính, EquipMaxHp cộng trực tiếp)
        val equipBonus = EquipBonusCalculator.calculateTotalBonus(petData.equips, itemDefs)

        // DVT Calculator.lua handles FollowNpc with the normal stat formula;
        // do not approximate max HP/SP as base + level*stat. That old shortcut
        // inflated SP heavily and made pets look effectively unlimited.
        val petTurn = if (npcDef.turn == 2) 1 else 0
        val maxHp = statSystem.calculateMaxHp(
            level = petData.level,
            hpx = petData.hpx + equipBonus.equipHpx,
            equipMaxHp = equipBonus.equipMaxHp,
            extraHp = petData.hpPillCount * 20,
            turn = petTurn
        )
        val maxSp = statSystem.calculateMaxSp(
            level = petData.level,
            spx = petData.spx + equipBonus.equipSpx,
            equipMaxSp = equipBonus.equipMaxSp,
            extraSp = petData.spPillCount * 5,
            turn = petTurn
        ).toLong()
        val hp = petData.hp.coerceIn(1L, maxHp)
        val sp = petData.sp.toLong().coerceIn(0L, maxSp)

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
            skills = normalSkills.keys.toIntArray(),
            skillLevels = normalSkills
        )
    }

    /**
     * Tạo nhân vật chiến đấu của NPC từ NpcDef
     *
     * @param npcDataId số hiệu NPC (key của Npc_C.dat, client dùng npcDatas[roleId] tra sprite)
     */
    private fun createNpcFighter(npcDef: NpcDef, col: Int, row: Int, battleId: Long, npcDataId: Int): BattleFighter {
        // NPC HP = hpBase + level * hpx
        val hp = (npcDef.hpBase + npcDef.level * npcDef.hpx).toLong()
        val sp = (npcDef.spBase + npcDef.level * npcDef.spx).toLong()

        logger.info { "Trận $battleId: NPC ${npcDef.name} (id=$npcDataId) lv=${npcDef.level} " +
            "hp=$hp atk=${npcDef.atk} def=${npcDef.def} agi=${npcDef.agi} int=${npcDef.intelligence} " +
            "element=${npcDef.element} skills=${npcDef.skills.filter { it > 0 }}" }

        return BattleFighter(
            roleId = npcDataId.toLong(),  // Số hiệu NPC → client dùng npcDatas[roleId] tra sprite
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

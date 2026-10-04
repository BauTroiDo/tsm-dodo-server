package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EAttackResult
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EBeHitAction
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.constants.EWarStyle
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcDropDef
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.domain.interfaces.GameSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Một instance trận chiến — quản lý toàn bộ vòng đời của một trận chiến
 *
 * Tương ứng FightField.lua + FightManager.lua của client.
 * Bao gồm bàn cờ, người tham chiến, trạng thái hiệp.
 */
class BattleInstance(
    /** ID trận chiến duy nhất*/
    val battleId: Long,
    /** Loại trận chiến (EWar)*/
    val warType: Int,
    /** DVT ffWar_Style, gửi cho client qua S:011-010; không được đoán từ EWar.*/
    val warStyle: Int = EWarStyle.NORMAL,
    /** Thông tin địa chất (ảnh hưởng nền cảnh)*/
    val terrain: Int,
    /** Số sự kiện (eveNo của Eve_FightData)*/
    val eventNo: Int,
    /** Bàn cờ chiến trường*/
    val grid: BattleGrid,
    /** Trình phân tích kỹ năng*/
    val skillResolver: BattleSkillResolver,
    /** Trình phân tích vật phẩm trận chiến*/
    val itemResolver: BattleItemResolver,
    /** Định nghĩa NPC tham chiến (npcId + NpcDef, dùng cho tính thưởng và rơi đồ)*/
    val involvedNpcDefs: List<Pair<Int, NpcDef>>,
    /** DVT protocol-47 dungeon id for server-driven special battles; 0 = ordinary battle. */
    val dungeonId: Int = 0,
    /** NeedKind MaxRound: all enemies must be defeated by the end of this round. */
    val winWithinRounds: Int = 0,
    /** NeedKind LimitRound: remaining alive through this many resolved rounds clears the stage. */
    val surviveRounds: Int = 0,
    /** Weekly stage rewards come from DungeonCombo, not generic NPC EXP/gold/drop settlement. */
    val suppressGenericRewards: Boolean = false,
) {
    /** Máy trạng thái trận chiến*/
    enum class State {
        /** Đang khởi tạo*/
        INIT,
        /** Đang đếm ngược (chờ người chơi nhập lệnh)*/
        COUNTDOWN,
        /** Chờ tất cả người chơi gửi lệnh*/
        WAITING_COMMANDS,
        /** Đang phân tích hiệp*/
        RESOLVING,
        /** Kết thúc hiệp*/
        ROUND_END,
        /** Trận chiến kết thúc*/
        FINISHED
    }

    /** Trạng thái hiện tại (đa luồng: coroutine ghi / Netty đọc)*/
    @Volatile
    var state: State = State.INIT

    /** Số hiệp hiện tại*/
    var currentRound: Int = 0

    /** Session của người chơi tham chiến (playerId → GameSession)*/
    val playerSessions = ConcurrentHashMap<Long, GameSession>()

    /** Tất cả người tham chiến (bao gồm người chơi và NPC)*/
    private val allFighters = mutableListOf<BattleFighter>()

    /** Kết quả cần chuyển đến sau khi trận chiến kết thúc (linkToResult)*/
    var linkToResult: Int = 0

    /** Bắt thành công trong trận chiến: playerId người bắt → ID NPC. Không được trong trận nhiều người phát cùng một NPC cho tất cả người chơi.*/
    val capturedNpcs = mutableListOf<Pair<Long, Int>>()

    /** BattleManager tiêm kiểm tra sở hữu/dung lượng của người chơi; null chỉ dành cho fixture test cấp thấp.*/
    var catchOwnerValidator: ((playerId: Long, npcId: Int) -> Boolean)? = null

    /**
     * Thêm nhân vật chiến đấu vào bàn cờ
     */
    fun addFighter(fighter: BattleFighter) {
        allFighters.add(fighter)
        grid.place(fighter)
        logger.debug { "Trận $battleId: ${fighter.name} vào bàn cờ col=${fighter.col} row=${fighter.row}" }
    }

    /**
     * Lấy tất cả người tham chiến
     */
    fun getAllFighters(): List<BattleFighter> = allFighters.toList()

    /**
     * Phán đoán phe đảng của người chơi
     *
     * @return true=bên phải (col 2-3), false=bên trái (col 0-1), null=không thuộc trận chiến này
     */
    fun isPlayerOnRightSide(playerId: Long): Boolean? {
        val fighter = allFighters.find { it.roleId == playerId } ?: return null
        return fighter.col >= 2
    }

    /**
     * Đặt hành động cho nhân vật do người chơi điều khiển (gồm tướng)
     *
     * Gói tin C:050-001 của client chứa srcCol/srcRow xác định vị trí người ra chiêu,
     * Dùng vị trí để xác định nhân vật thay vì playerId, vì roleId của tướng là npcId chứ không phải ID người chơi.
     * Xác minh nhân vật này thuộc người chơi thông qua masterId hoặc roleId.
     *
     * @param playerId ID người chơi gửi lệnh
     * @param srcCol cột của người ra chiêu
     * @param srcRow hàng của bên ra chiêu
     * @param action hành động được chọn
     */
    fun setPlayerAction(playerId: Long, srcCol: Int, srcRow: Int, action: BattleAction): Boolean {
        val fighter = grid.getAt(srcCol, srcRow)
        if (fighter == null) {
            logger.warn { "Trận $battleId: không tìm thấy nhân vật ở ($srcCol,$srcRow)" }
            return false
        }
        // Kiểm tra hợp lệ quyền sở hữu: roleId khớp (bản thân người chơi) hoặc masterId khớp (tướng)
        if (fighter.roleId != playerId && fighter.masterId != playerId) {
            logger.warn { "Trận $battleId: nhân vật ${fighter.name} ở ($srcCol,$srcRow) không thuộc người chơi $playerId" }
            return false
        }
        // S:053-005 is the client acknowledgement that this fighter's command was
        // accepted and control may advance. Do not let a duplicate/replayed C:050
        // silently replace an already acknowledged command in the same round.
        if (fighter.hasActed || fighter.pendingAction != null) {
            logger.debug { "Trận $battleId: ${fighter.name} đã gửi hành động của lượt này, bỏ qua lệnh trùng" }
            return false
        }
        if (fighter.isAlive && !fighter.isCommandBlocked) {
            if (action.actionKind == BattleAction.KIND_SKILL) {
                val target = grid.getAt(action.targetCol, action.targetRow)
                if (target == null) {
                    logger.warn { "Trận $battleId: ${fighter.name} chỉ định mục tiêu kỹ năng không tồn tại (${action.targetCol},${action.targetRow})" }
                    return false
                }
                val skill = if (action.id == BattleConstants.HAND_FIGHT_SKILL_ID) null else skillResolver.getSkillDef(action.id)
                val isRevive = skill?.fightWay == 8
                // DVT FightField.UpdateSelectFrame exposes dead friendlies specifically
                // for fightWay 8. Every other skill keeps the normal alive-target gate.
                if (isRevive) {
                    if (target.isAlive) {
                        logger.warn { "Trận $battleId: ${fighter.name} dùng kỹ năng hồi sinh id=${action.id} lại chỉ định mục tiêu còn sống" }
                        return false
                    }
                } else if (!target.isAlive) {
                    logger.warn { "Trận $battleId: ${fighter.name} kỹ năng không hồi sinh lại chỉ định mục tiêu đã chết (${action.targetCol},${action.targetRow})" }
                    return false
                }
                val sameSide = fighter.isPlayerSide == target.isPlayerSide
                if (skill == null) {
                    if (sameSide) {
                        logger.warn { "Trận $battleId: ${fighter.name} tấn công thường lại chỉ định mục tiêu phe mình" }
                        return false
                    }
                } else {
                    // FightRoleController:CheckSkillTargetLegal uses fightWay, not HP-heal classification.
                    if (sameSide && skill.fightWay in ENEMY_ONLY_FIGHT_WAYS) {
                        logger.warn { "Trận $battleId: ${fighter.name} kỹ năng thù địch id=${action.id} chỉ định phe mình" }
                        return false
                    }
                    if (!sameSide && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS) {
                        logger.warn { "Trận $battleId: ${fighter.name} kỹ năng hỗ trợ đồng minh id=${action.id} chỉ định phe địch" }
                        return false
                    }
                }
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in SELF_ONLY_SKILLS &&
                (action.targetCol != fighter.col || action.targetRow != fighter.row)
            ) {
                // DVT FightRoleController.DoClick: 13008/14002/20006 require
                // FightField.conIdx == selected target index, i.e. caster targets self.
                logger.warn { "Trận $battleId: ${fighter.name} kỹ năng chỉ áp dụng bản thân id=${action.id} lại chỉ định mục tiêu khác (${action.targetCol},${action.targetRow})" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in NON_BATTLE_SKILLS
            ) {
                // DVT FightField.SetConSkill explicitly blocks these skills in battle.
                logger.warn { "Trận $battleId: ${fighter.name} thử dùng kỹ năng ngoài chiến đấu bị Lua cấm rõ ràng id=${action.id}" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id != BattleConstants.HAND_FIGHT_SKILL_ID
            ) {
                val skill = skillResolver.getSkillDef(action.id)
                if (skill == null) {
                    logger.warn { "Trận $battleId: ${fighter.name} thử dùng kỹ năng không tồn tại id=${action.id}" }
                    return false
                }
                if (fighter.sp < skill.requireSp) {
                    logger.debug { "Trận $battleId: ${fighter.name} không đủ SP, từ chối kỹ năng id=${action.id} need=${skill.requireSp} sp=${fighter.sp}" }
                    return false
                }
            }
            if (action.actionKind == BattleAction.KIND_ESCAPE &&
                warStyle in setOf(EWarStyle.LIFE_PK, EWarStyle.WORLD_BOSS)
            ) {
                // FightRoleController.DoClick: fightWay=12 is disabled in LifePK/WorldBoss.
                logger.warn { "Trận $battleId: warStyle=$warStyle cấm chạy trốn" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_CATCH) {
                val target = grid.getAt(action.targetCol, action.targetRow)
                val npcDef = target?.let { t -> involvedNpcDefs.firstOrNull { it.first == t.npcId }?.second }
                if (target == null || !target.isAlive || !target.isEnemySide ||
                    target.kind == EHuman.PLAYER || target.kind == EHuman.PLAYERS ||
                    target.kind == EHuman.FOLLOW_NPC || npcDef == null || npcDef.canBeCatch ||
                    npcDef.level - fighter.level > BattleConstants.MANUAL_CATCH_LEVEL_DIFF ||
                    target.npcId in MANUAL_CATCH_NPC_BLACKLIST ||
                    target.groupedStatuses.containsKey(SEAL_STATUS_GROUP) ||
                    catchOwnerValidator?.invoke(playerId, target.npcId) == false
                ) {
                    // DVT FightRoleController.DoClick manual catch (15002):
                    // enemy NPC only, NpcData.canBeCatch must allow it, and target
                    // level may exceed the actor by at most five.
                    logger.warn { "Trận $battleId: ${fighter.name} thử bắt mục tiêu mà Lua không cho phép (${action.targetCol},${action.targetRow})" }
                    return false
                }
            }
            if (action.actionKind == BattleAction.KIND_ITEM) {
                val target = grid.getAt(action.targetCol, action.targetRow)
                val ownerSession = playerSessions[playerId]
                if (target == null || ownerSession == null ||
                    !itemResolver.canQueueItemUse(
                        user = fighter,
                        target = target,
                        itemId = action.id,
                        ownerCharId = ownerSession.roleId.toLong()
                    )
                ) {
                    logger.warn { "Trận $battleId: ${fighter.name} thử xếp vật phẩm chiến đấu không hợp lệ/không sở hữu id=${action.id}" }
                    return false
                }
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in BOW_SKILLS &&
                fighter.kind in PLAYER_BOW_CHECK_KINDS &&
                !hasLuaBowWeapon(fighter)
            ) {
                logger.warn { "Trận $battleId: ${fighter.name} kỹ năng cung id=${action.id} nhưng chưa trang bị vũ khí cung hợp lệ theo Lua" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in SUMMON_SKILLS &&
                !canUseSummon(fighter, action.id)
            ) {
                logger.warn { "Trận $battleId: ${fighter.name} thử triệu hồi id=${action.id} không đáp điều kiện nguyên tố/huy hiệu của Lua" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id != BattleConstants.HAND_FIGHT_SKILL_ID &&
                action.id !in BattleConstants.DEFEND_SKILL_IDS &&
                action.id !in BattleConstants.ESCAPE_SKILL_IDS &&
                action.id !in BattleConstants.CATCH_COMMAND_SKILL_IDS &&
                action.id !in fighter.skills
            ) {
                logger.warn { "Trận $battleId: ${fighter.name} thử dùng kỹ năng chưa học id=${action.id}" }
                return false
            }
            fighter.pendingAction = action
            fighter.hasActed = true
            logger.debug { "Trận $battleId: ${fighter.name} đặt hành động kind=${action.actionKind} id=${action.id}" }
            return true
        }
        return false
    }

    /**
     * Kiểm tra tất cả người chơi đã gửi lệnh chưa
     */
    /**
     * Kiểm tra tất cả nhân vật do người chơi điều khiển (gồm tướng) đã gửi lệnh chưa
     */
    fun allPlayersReady(): Boolean {
        return allFighters
            .filter { it.isPlayerControlled && it.isAlive && !it.isCommandBlocked }
            .all { it.hasActed }
    }

    /**
     * Phân bổ hành động AI cho NPC
     *
     * Chọn mục tiêu NPC theo fightWay DVT và fallback PC TSBattleNPC.seekTarget.
     */
    fun resolveNpcActions(skillSelector: (IntArray) -> Int = ::selectNpcSkill) {
        val npcs = allFighters.filter { fighter ->
            // DVT client CheckCanAct blocks Chaos from accepting a player command, and the
            // recovered PC enemy-AI path explicitly skips debuff 14021 (Chaos) before seekTarget.
            // Taunt is intentionally not folded into this filter: DVT proves that it removes
            // manual control, but does not expose whether enemy server AI should skip or be forced
            // into a particular action/target. Keep that separate until its server contract is found.
            val hasChaos = fighter.groupedStatuses.values.any { it.status == BattleStatus.CHAOS }
            !fighter.isPlayerControlled && fighter.isAlive && !fighter.isStunned && !hasChaos
        }

        for (npc in npcs) {
            if (npc.pendingAction != null) continue

            val selectedSkillId = skillSelector(npc.skills)
            val skillId = NpcAiSkillRules.normalizeSelectedSkill(selectedSkillId)
            val skillDef = skillResolver.getSkillDef(skillId)

            // DVT Attack.GetAttackNewFunction falls back unknown/non-Skill_C IDs to
            // HandFight while preserving the packet skill ID. Do not reroll another AI slot.
            // A null skillDef therefore intentionally continues through normal hostile targeting.

            // DVT client gates SP only for player-controlled Players/FollowNpc command input.
            // Enemy NPC commands are server-driven: once selected, the client executes the skill,
            // subtracts requireSp and clamps SP at zero. PC TSBattleNPC follows the same contract.

            // DVT exposes target-side semantics for player selection; NPC AI itself is
            // server-side, so unresolved cases follow PC TSBattleNPC.seekTarget exactly.
            val targets = NpcAiTargetRules.candidates(npc, grid, skillDef?.fightWay)
            if (targets.isEmpty()) continue

            val target = targets.random()

            npc.pendingAction = BattleAction(
                actionKind = BattleAction.KIND_SKILL,
                id = skillId,
                targetCol = target.col,
                targetRow = target.row
            )
            npc.hasActed = true
            logger.debug { "Trận $battleId: AI NPC ${npc.name} → skill=$skillId fightWay=${skillDef?.fightWay ?: 1} target=(${target.col},${target.row})" }
        }
    }

    /**
     * Thực thi một hiệp
     *
     * Sắp xếp tất cả người hành động theo nhanh nhẹn, phân tích hiệu ứng kỹ năng từng người.
     *
     * @return kết quả hiệp
     */
    suspend fun resolveRound(): RoundResult {
        state = State.RESOLVING
        val actionResults = mutableListOf<ActionResult>()
        val statusRestores = mutableListOf<StatusRestore>()
        val escapeResults = mutableListOf<EscapeResult>()
        val flyOutTargets = mutableListOf<Pair<Int, Int>>()
        val consumedItems = mutableListOf<ConsumedItem>()

        // Reset trạng thái phòng thủ của tất cả mọi người (reset mỗi đầu hiệp, chế độ KERO)
        for (fighter in allFighters) {
            fighter.isDefending = false
        }

        // Xử lý lệnh phòng thủ trước (KERO: phòng thủ không vào hàng đợi, đặt cờ trực tiếp)
        for (fighter in allFighters.filter { it.isAlive && it.hasActed && it.pendingAction != null }) {
            val action = fighter.pendingAction ?: continue
            if (action.actionKind == BattleAction.KIND_DEFEND) {
                fighter.isDefending = true
                fighter.pendingAction = null
                fighter.hasActed = false
                logger.debug { "Trận $battleId: ${fighter.name} phòng thủ" }
            }
        }

        // Status duration is decremented after actions so round=1 blocks this round.\n\n        // Sắp xếp theo nhanh nhẹn (nhanh nhẹn cao hành động trước)
        val actingFighters = allFighters
            .filter { it.isAlive && it.hasActed && it.pendingAction != null }
            .sortedByDescending { it.agi }

        // ── Tính Combo ──────────────────────────────────────────────────────────
        // Reset hệ số combo của tất cả mọi người (tránh tồn dư từ hiệp trước)
        for (fighter in allFighters) fighter.comboMultiplier = 1.0

        // Lần lượt kiểm tra từng cặp [i-1, i] có tạo thành Combo không, nếu có thì nhân dồn ×1.2
        for (i in 1 until actingFighters.size) {
            val prev = actingFighters[i - 1]
            val curr = actingFighters[i]
            if (BattleFighter.checkCombo(prev, curr, skillResolver::getSkillDef)) {
                curr.comboMultiplier = prev.comboMultiplier * 1.2
                val multStr = String.format("%.2f", curr.comboMultiplier)
                logger.debug { "Trận $battleId: Combo! ${prev.name}→${curr.name} hệ số=$multStr" }
            }
        }
        // ────────────────────────────────────────────────────────────────────────

        for (fighter in actingFighters) {
            // Kiểm tra lại trước khi hành động: có thể đã bị hạ trong hiệp này
            if (!fighter.isAlive) continue

            // Re-check all client-proven control states at the actual AGI turn.
            // A faster actor may have applied Chaos/Seal after commands were queued.
            if (fighter.isActionBlocked) {
                fighter.pendingAction = null
                fighter.hasActed = false
                continue
            }

            val action = fighter.pendingAction ?: continue

            when (action.actionKind) {
                BattleAction.KIND_SKILL -> {
                    val result = skillResolver.resolveSkill(
                        attacker = fighter,
                        grid = grid,
                        skillId = action.id,
                        targetCol = action.targetCol,
                        targetRow = action.targetRow,
                        terrain = terrain,
                        comboMultiplier = fighter.comboMultiplier,
                        currentRound = currentRound,
                        allowInsufficientSp = fighter.kind == EHuman.MINE_NPC
                    )
                    actionResults.add(
                        ActionResult(
                            attackerCol = fighter.col,
                            attackerRow = fighter.row,
                            skillId = action.id,
                            // Lớp phủ hiệu ứng của kỹ năng diện rộng do client tính theo trường này (GetEnemyIdx), không được hard-code đơn thể
                            fightArea = result.fightArea,
                            targets = result.targets,
                            spConsumed = result.spConsumed,
                            comboWithPrevious = fighter.comboMultiplier > 1.0
                        )
                    )
                    // Thu thập các mục tiêu bị đánh bay
                    for (tr in result.targets) {
                        if (tr.targetFlyOut) flyOutTargets.add(tr.targetCol to tr.targetRow)
                    }
                }
                BattleAction.KIND_ITEM -> {
                    // Chủ sở hữu vật phẩm = người chơi điều khiển đơn vị này (tướng lấy masterId)
                    val ownerPlayerId = if (fighter.masterId != 0L) fighter.masterId else fighter.roleId
                    val ownerSession = playerSessions[ownerPlayerId]
                    val target = grid.getAt(action.targetCol, action.targetRow)

                    if (ownerSession != null && target != null) {
                        val outcome = itemResolver.resolveItemUse(
                            user = fighter,
                            target = target,
                            itemId = action.id,
                            ownerCharId = ownerSession.roleId.toLong()
                        )
                        if (outcome != null) {
                            consumedItems.add(ConsumedItem(ownerPlayerId, outcome.bagSlot, 1))
                            actionResults.add(
                                ActionResult(
                                    attackerCol = fighter.col,
                                    attackerRow = fighter.row,
                                    // 19001 = hiệu ứng dùng vật phẩm hỗ trợ (client UseItem.lua)
                                    skillId = BattleConstants.USE_ITEM_SKILL_ID,
                                    fightArea = 1,
                                    targets = listOf(
                                        TargetResult(
                                            targetCol = target.col,
                                            targetRow = target.row,
                                            attackResult = tw.tsm.domain.constants.EAttackResult.HIT,
                                            beHitAction = tw.tsm.domain.constants.EBeHitAction.NONE,
                                            attrChanges = outcome.attrChanges
                                        )
                                    ),
                                    spConsumed = 0
                                )
                            )
                        }
                    } else {
                        logger.debug { "Trận $battleId: ${fighter.name} dùng vật phẩm ${action.id} không có tác dụng (không có chủ sở hữu hoặc mục tiêu)" }
                    }
                }
                BattleAction.KIND_CATCH -> {
                    // Chủ sở hữu khi bắt = người chơi điều khiển đơn vị này; nhất quán với quy tắc ownership của vật phẩm.
                    val ownerPlayerId = if (fighter.masterId != 0L) fighter.masterId else fighter.roleId
                    val target = grid.getAt(action.targetCol, action.targetRow)
                    if (target != null && target.isAlive && target.kind != EHuman.PLAYER) {
                        // DVT manual +5/catchability/ownership gates are validated when queued.
                        // DVT client does not calculate the random result. PC original server CatchPet
                        // supplies the server-side formula: chance = lost HP percentage.
                        val hpRatio = if (target.maxHp > 0) {
                            (target.hp.toDouble() / target.maxHp.toDouble()).coerceIn(0.0, 1.0)
                        } else {
                            1.0
                        }
                        val successRate = (1.0 - hpRatio).coerceIn(0.0, 1.0)
                        val success = Math.random() < successRate

                        val resultSkillId = if (success) BattleConstants.CATCH_NET_SUCCESS
                                           else BattleConstants.CATCH_NET_FAIL

                        logger.info { "Trận $battleId: ${fighter.name} bắt ${target.name} (hp=${target.hp}/${target.maxHp} máu=${(hpRatio*100).toInt()}% tỉ lệ thành công=${(successRate*100).toInt()}%) → ${if (success) "成功" else "失敗"}" }

                        actionResults.add(
                            ActionResult(
                                attackerCol = fighter.col,
                                attackerRow = fighter.row,
                                skillId = resultSkillId,
                                fightArea = 1,
                                targets = listOf(TargetResult(
                                    targetCol = target.col,
                                    targetRow = target.row,
                                    attackResult = if (success) 1 else 0,
                                    beHitAction = if (success) 4 else 0,
                                    attrChanges = emptyList()
                                )),
                                spConsumed = 0
                            )
                        )

                        if (success) {
                            target.hp = 0
                            target.isDead = true
                            // A captured NPC leaves the battlefield; unlike an ordinary corpse,
                            // it must not remain targetable by fightWay=8 revive skills.
                            grid.remove(target.col, target.row)
                            capturedNpcs.add(ownerPlayerId to target.npcId)
                        }
                    }
                }
                BattleAction.KIND_ESCAPE -> {
                    // Tỷ lệ bỏ chạy thành công: cơ bản 50%, cộng thêm theo chênh lệch nhanh nhẹn
                    val enemies = if (fighter.isPlayerSide) grid.getAliveEnemySide() else grid.getAlivePlayerSide()
                    val avgEnemyAgi = if (enemies.isNotEmpty()) enemies.sumOf { it.agi } / enemies.size else 0
                    val agiDiff = fighter.agi - avgEnemyAgi
                    val escapeRate = (0.5 + agiDiff * 0.01).coerceIn(0.1, 0.9)
                    val success = Math.random() < escapeRate

                    logger.info { "Trận $battleId: ${fighter.name} chạy trốn (chênh nhanh nhẹn=$agiDiff tỉ lệ thành công=${(escapeRate*100).toInt()}%) → ${if (success) "成功" else "失敗"}" }

                    if (success) {
                        escapeResults.add(EscapeResult(fighter, true))
                    } else {
                        // Bỏ chạy thất bại → phát hoạt cảnh thất bại (skillId=18002), không gây sát thương
                        actionResults.add(
                            ActionResult(
                                attackerCol = fighter.col,
                                attackerRow = fighter.row,
                                skillId = BattleConstants.ESCAPE_FAIL_SKILL_ID,
                                fightArea = 1,
                                targets = listOf(TargetResult(
                                    targetCol = fighter.col,
                                    targetRow = fighter.row,
                                    attackResult = 0,
                                    beHitAction = 3,  // NONE — không đổi
                                    attrChanges = emptyList()
                                )),
                                spConsumed = 0
                            )
                        )
                    }
                }
            }

            // Xoá hành động
            fighter.pendingAction = null
            fighter.hasActed = false
        }

        // DVT SkillData.round is a semantic occupied-round count. A status inflicted in
        // the current action phase starts its first full affected round on the NEXT command
        // phase; do not consume a duration unit immediately on application. This mirrors the
        // PC server's +1 internal counter followed by start-round decrement, while keeping the
        // Kotlin state as the semantic number of affected rounds still remaining.
        for (fighter in allFighters) {
            if (!fighter.isAlive) continue
            val expiredGroups = mutableListOf<Int>()
            for ((group, status) in fighter.groupedStatuses) {
                if (status.appliedRound >= currentRound) continue
                status.rounds--
                if (status.rounds <= 0) {
                    // DVT RevRestoreStatus -> HandleStatus(group, skillId). Every modeled group
                    // uses skillId=0 as the generic clear contract; re-sending the source skill
                    // would map the same status back onto the client instead of removing it.
                    statusRestores.add(StatusRestore(fighter.col, fighter.row, group, 0))
                    expiredGroups.add(group)
                }
            }
            for (group in expiredGroups) fighter.groupedStatuses.remove(group)
            fighter.syncLegacyStatusView()
        }

        // Xoá tất cả hành động còn lại (nhân vật chưa hành động) và reset hệ số Combo
        for (fighter in allFighters) {
            fighter.pendingAction = null
            fighter.hasActed = false
            fighter.comboMultiplier = 1.0
        }

        // Đánh dấu người bỏ chạy thành công đã tử vong và xoá khỏi bàn cờ (rời đi khi đang chiến đấu = xoá)
        for (escape in escapeResults) {
            escape.fighter.isDead = true
            grid.remove(escape.fighter.col, escape.fighter.row)
        }

        // DVT keeps ordinary corpses on their battlefield cells so fightWay=8 can
        // select/revive them in later rounds. Only fly-out units leave the grid here.
        for (fighter in allFighters) {
            if (fighter.hp <= 0) fighter.isDead = true
            if (fighter.isFlyOut) {
                grid.remove(fighter.col, fighter.row)
            }
        }

        currentRound++
        state = State.ROUND_END

        return RoundResult(actionResults, statusRestores, escapeResults, flyOutTargets, consumedItems)
    }

    /**
     * Periodic status phase that runs before the countdown/command phase of each round.
     * Newly inflicted statuses have appliedRound == previous currentRound and therefore
     * first tick only after resolveRound advances currentRound, matching PC start_round.
     */
    fun resolveRoundStartStatusEffects(): RoundResult {
        val actionResults = mutableListOf<ActionResult>()
        if (currentRound <= 0) return RoundResult(actionResults, emptyList())

        for (fighter in allFighters) {
            if (!fighter.isAlive) continue
            for (status in fighter.groupedStatuses.values) {
                if (status.appliedRound >= currentRound || status.rounds <= 0) continue
                periodicStatusTick(fighter, status)?.let(actionResults::add)
                if (!fighter.isAlive) break
            }
        }
        return RoundResult(actionResults, emptyList())
    }

    /**
     * PC fallback for the two legacy periodic-status ticks that DVT still exposes as
     * dedicated client animation skill IDs. Keep this exact: newer multi-target/super
     * Clump/Poison skills are not generalized because DVT data does not expose their
     * server tick magnitude or prove that they share this legacy periodic behavior.
     */
    private fun periodicStatusTick(fighter: BattleFighter, status: GroupedBattleStatus): ActionResult? {
        if (!fighter.isAlive) return null
        val (tickSkillId, damage) = when (status.skillId) {
            10004 -> 20001 to 30 // PC: Clump -> ClumpAtk
            14015 -> 20003 to 90 // PC: Poison -> special no-animation damage
            else -> return null
        }

        val actual = minOf(damage.toLong(), fighter.hp).toInt()
        fighter.hp = (fighter.hp - actual).coerceAtLeast(0)
        if (fighter.hp <= 0) fighter.isDead = true

        return ActionResult(
            attackerCol = fighter.col,
            attackerRow = fighter.row,
            skillId = tickSkillId,
            fightArea = 1,
            targets = listOf(
                TargetResult(
                    targetCol = fighter.col,
                    targetRow = fighter.row,
                    attackResult = EAttackResult.HIT,
                    beHitAction = EBeHitAction.HIT,
                    attrChanges = if (actual > 0) listOf(BattleAttrChange(EAttribute.HP, actual, false)) else emptyList(),
                    targetDied = fighter.isDead,
                    targetFlyOut = false
                )
            ),
            spConsumed = 0
        )
    }

    /**
     * Kiểm tra trận chiến đã kết thúc chưa
     *
     * @return kết quả kết thúc (null=chưa kết thúc)
     */
    fun checkBattleEnd(): BattleEndResult? {
        val aliveEnemies = grid.getAliveEnemySide()
        val alivePlayers = grid.getAlivePlayerSide()
        logger.debug { "Trận $battleId kiểm tra kết thúc: enemies=${aliveEnemies.size}${aliveEnemies.map { " ${it.name}(hp=${it.hp})" }} players=${alivePlayers.size}" }

        return when {
            aliveEnemies.isEmpty() -> BattleEndResult(playerWin = true)
            alivePlayers.isEmpty() -> BattleEndResult(playerWin = false)
            surviveRounds > 0 && currentRound >= surviveRounds -> BattleEndResult(playerWin = true)
            winWithinRounds > 0 && currentRound >= winWithinRounds -> BattleEndResult(playerWin = false)
            else -> null
        }
    }

    /**
     * Chọn kỹ năng AI NPC theo fallback PC TSBattleNPC.seekTarget.
     * DVT IOS_NpcSkillProb_C.dat thật chỉ có 4 byte `02 00 00 00`, không có bảng xác suất.
     * PC dùng đúng một roll 0..99 rồi ưu tiên modulo %5 -> %4 -> %3 -> %2; đây không phải
     * bốn lần roll độc lập. Khi đủ cả 4 skill, phân bố thực là 20/20/20/14%, basic 26%.
     */
    private fun selectNpcSkill(skills: IntArray): Int =
        NpcAiSkillRules.selectByRoll(skills, Random.nextInt(100))

    /**
     * Tính thưởng trận chiến (chỉ khi người chơi thắng)
     *
     * @param playerLevel cấp độ người chơi
     * @param npcDropTable bảng rơi đồ của NPC
     */
    fun calculateRewards(playerLevel: Int, npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap()): BattleReward {
        return BattleRewardCalc.calculate(involvedNpcDefs, playerLevel, npcDropTable)
    }
    private fun canUseSummon(fighter: BattleFighter, skillId: Int): Boolean {
        if (skillId !in fighter.skills || fighter.specialEquipDamage >= 250) return false
        val element = if (skillId == 22011 || skillId == 23011) {
            if (fighter.isHumanPlayer) fighter.turn3Element else fighter.element
        } else {
            fighter.element
        }
        return when (skillId) {
            10016 -> element == 1 && fighter.specialEquipItemId in intArrayOf(23086, 23135)
            11016 -> element == 2 && fighter.specialEquipItemId in intArrayOf(23087, 23136)
            12016 -> element == 3 && fighter.specialEquipItemId in intArrayOf(23088, 23137)
            13015 -> element == 4 && fighter.specialEquipItemId in intArrayOf(23089, 23138)
            22011 -> element == 7 && fighter.specialEquipItemId in intArrayOf(23361, 23367)
            23011 -> element == 8 && fighter.specialEquipItemId in intArrayOf(23362, 23368)
            else -> false
        }
    }

    private fun hasLuaBowWeapon(fighter: BattleFighter): Boolean =
        fighter.weaponItemKind == 7 || fighter.weaponItemId in SPIRITUAL_BOW_ITEM_IDS

    private val ENEMY_ONLY_FIGHT_WAYS = setOf(1, 2, 3, 11, 15, 21)
    private val FRIENDLY_ONLY_FIGHT_WAYS = setOf(6, 7, 8, 14)

    companion object {
        private const val SEAL_STATUS_GROUP = 1
        private val MANUAL_CATCH_NPC_BLACKLIST = setOf(
            35029, 35030, 35031, 35032, 35033, 35034, 35054, 35055, 35056
        )
        private val SUMMON_SKILLS = setOf(10016, 11016, 12016, 13015, 22011, 23011)
        // FightRoleController.DoClick explicitly requires the selected target to be the caster.
        private val SELF_ONLY_SKILLS = setOf(13008, 14002, 20006)
        private val BOW_SKILLS = setOf(14033, 14036, 14037, 14064)
        private val SPIRITUAL_BOW_ITEM_IDS = setOf(57601, 57602, 57603, 57604)
        private val PLAYER_BOW_CHECK_KINDS = setOf(EHuman.PLAYER, EHuman.PLAYERS, EHuman.DIVIDE)
        // Exact DVT FightField.SetConSkill list: these are non-battle skills.
        private val NON_BATTLE_SKILLS = setOf(
            14003, 14004, 14005,
            14009, 14010, 14011,
            14016, 14017, 14018, 14019, 14020,
            14023, 14026, 14027, 14028,
            14032, 14034,
            10020, 11020, 12020, 13019, 14035,
            14038, 14039, 14041, 14043, 14045,
            14047, 14048, 14049, 14050, 14051,
            14052, 14025, 14077
        )
    }

}

/**
 * Kết quả thực thi hiệp
 */
data class RoundResult(
    /** Kết quả tấn công của mỗi người hành động*/
    val actionResults: List<ActionResult>,
    /** Hết hạn trạng thái bất thường, hồi phục*/
    val statusRestores: List<StatusRestore>,
    /** Kết quả bỏ chạy*/
    val escapeResults: List<EscapeResult> = emptyList(),
    /** Vị trí nhân vật bị đánh bay trong hiệp này (col, row)*/
    val flyOutTargets: List<Pair<Int, Int>> = emptyList(),
    /** Vật phẩm tiêu hao trong hiệp này (dùng cho thông báo cập nhật túi đồ)*/
    val consumedItems: List<ConsumedItem> = emptyList()
)

/**
 * Bản ghi vật phẩm tiêu hao trong trận chiến
 */
data class ConsumedItem(
    /** ID người chơi sở hữu vật phẩm*/
    val ownerPlayerId: Long,
    /** Ô túi đồ*/
    val bagSlot: Int,
    /** Số lượng tiêu hao*/
    val amount: Int
)

/**
 * Kết quả bỏ chạy
 */
data class EscapeResult(
    /** Nhân vật bỏ chạy*/
    val fighter: BattleFighter,
    /** Có thành công không*/
    val success: Boolean
)

/**
 * Kết quả tấn công của một hành động
 */
data class ActionResult(
    /** Cột của người tấn công*/
    val attackerCol: Int,
    /** Hàng của người tấn công*/
    val attackerRow: Int,
    /** ID kỹ năng đã dùng*/
    val skillId: Int,
    /** Phạm vi kỹ năng*/
    val fightArea: Int,
    /** Kết quả với từng mục tiêu*/
    val targets: List<TargetResult>,
    /** SP tiêu hao*/
    val spConsumed: Int,
    /** PC-original combo chain: this physical action joins the previous action packet. */
    val comboWithPrevious: Boolean = false
)

/**
 * Thông báo hồi phục trạng thái
 */
data class StatusRestore(
    /** Cột nhân vật*/
    val col: Int,
    /** Hàng nhân vật*/
    val row: Int,
    /** Loại trạng thái*/
    val statusKind: Int,
    /** ID kỹ năng (kỹ năng gây ra trạng thái này)*/
    val skillId: Int
)

/**
 * Kết quả kết thúc trận chiến
 */
data class BattleEndResult(
    /** Người chơi có thắng không*/
    val playerWin: Boolean
)

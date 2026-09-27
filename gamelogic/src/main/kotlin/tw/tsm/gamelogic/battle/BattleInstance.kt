package tw.tsm.gamelogic.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcDropDef
import tw.tsm.domain.enums.BattleStatus
import tw.tsm.domain.interfaces.GameSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * 單場戰鬥實例 — 管理一場戰鬥的完整生命週期
 *
 * 對應客戶端 FightField.lua + FightManager.lua。
 * 包含棋盤、參戰者、回合狀態。
 */
class BattleInstance(
    /** 唯一戰鬥 ID */
    val battleId: Long,
    /** 戰鬥類型（EWar） */
    val warType: Int,
    /** 地質資訊（影響背景） */
    val terrain: Int,
    /** 事件編號（Eve_FightData 的 eveNo） */
    val eventNo: Int,
    /** 戰場棋盤 */
    val grid: BattleGrid,
    /** 技能解析器 */
    val skillResolver: BattleSkillResolver,
    /** 戰鬥道具解析器 */
    val itemResolver: BattleItemResolver,
    /** 參戰 NPC 定義（npcId + NpcDef，用於獎勵計算與掉落） */
    val involvedNpcDefs: List<Pair<Int, NpcDef>>
) {
    /** 戰鬥狀態機 */
    enum class State {
        /** 初始化中 */
        INIT,
        /** 倒數計時中（等待玩家輸入指令） */
        COUNTDOWN,
        /** 等待所有玩家提交指令 */
        WAITING_COMMANDS,
        /** 解析回合中 */
        RESOLVING,
        /** 回合結束 */
        ROUND_END,
        /** 戰鬥結束 */
        FINISHED
    }

    /** 目前狀態（跨執行緒：協程寫入 / Netty 讀取） */
    @Volatile
    var state: State = State.INIT

    /** 目前回合數 */
    var currentRound: Int = 0

    /** 參戰玩家 session（playerId → GameSession） */
    val playerSessions = ConcurrentHashMap<Long, GameSession>()

    /** 所有參戰者（包含玩家和 NPC） */
    private val allFighters = mutableListOf<BattleFighter>()

    /** 戰鬥結束後要跳轉的結果（linkToResult） */
    var linkToResult: Int = 0

    /** 戰鬥中被捕捉的 NPC ID 列表 */
    val capturedNpcs = mutableListOf<Int>()

    /**
     * 新增戰鬥角色到棋盤
     */
    fun addFighter(fighter: BattleFighter) {
        allFighters.add(fighter)
        grid.place(fighter)
        logger.debug { "戰鬥 $battleId: ${fighter.name} 加入棋盤 col=${fighter.col} row=${fighter.row}" }
    }

    /**
     * 取得所有參戰者
     */
    fun getAllFighters(): List<BattleFighter> = allFighters.toList()

    /**
     * 判斷玩家所在陣營
     *
     * @return true=右方(col 2-3), false=左方(col 0-1), null=不在此戰鬥中
     */
    fun isPlayerOnRightSide(playerId: Long): Boolean? {
        val fighter = allFighters.find { it.roleId == playerId } ?: return null
        return fighter.col >= 2
    }

    /**
     * 設定玩家操控角色的行動（含武將）
     *
     * 客戶端 C:050-001 封包內含 srcCol/srcRow 標識發招者位置，
     * 用位置定位角色而非 playerId，因為武將的 roleId 是 npcId 而非玩家 ID。
     * 透過 masterId 或 roleId 驗證此角色屬於該玩家。
     *
     * @param playerId 發送指令的玩家 ID
     * @param srcCol 發招者所在列
     * @param srcRow 發招者所在行
     * @param action 選擇的行動
     */
    fun setPlayerAction(playerId: Long, srcCol: Int, srcRow: Int, action: BattleAction): Boolean {
        val fighter = grid.getAt(srcCol, srcRow)
        if (fighter == null) {
            logger.warn { "戰鬥 $battleId: 找不到 ($srcCol,$srcRow) 的角色" }
            return false
        }
        // 驗證歸屬：roleId 匹配（玩家本體）或 masterId 匹配（武將）
        if (fighter.roleId != playerId && fighter.masterId != playerId) {
            logger.warn { "戰鬥 $battleId: ($srcCol,$srcRow) 的角色 ${fighter.name} 不屬於玩家 $playerId" }
            return false
        }
        // S:053-005 is the client acknowledgement that this fighter's command was
        // accepted and control may advance. Do not let a duplicate/replayed C:050
        // silently replace an already acknowledged command in the same round.
        if (fighter.hasActed || fighter.pendingAction != null) {
            logger.debug { "戰鬥 $battleId: ${fighter.name} 已提交本回合行動，忽略重複指令" }
            return false
        }
        if (fighter.isAlive && !fighter.isCommandBlocked) {
            if (action.actionKind == BattleAction.KIND_SKILL) {
                val target = grid.getAt(action.targetCol, action.targetRow)
                if (target == null) {
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 指定不存在的技能目標 (${action.targetCol},${action.targetRow})" }
                    return false
                }
                val skill = if (action.id == BattleConstants.HAND_FIGHT_SKILL_ID) null else skillResolver.getSkillDef(action.id)
                if (skill != null &&
                    skill.fightWay in CASE_SKILL_EXCEPT_FIGHT_WAYS &&
                    action.id != CASE_SKILL_EXCEPTION_ID
                ) {
                    // DVT FightField.CaseSkill falls back to hand attack for these
                    // fightWay values unless SkillData:Except() is false (only 21026).
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試繞過 Lua CaseSkill Except id=${action.id} fightWay=${skill.fightWay}" }
                    return false
                }
                val isRevive = skill?.fightWay == 8
                // DVT FightField.UpdateSelectFrame exposes dead friendlies specifically
                // for fightWay 8. Every other skill keeps the normal alive-target gate.
                if (isRevive) {
                    if (target.isAlive) {
                        logger.warn { "戰鬥 $battleId: ${fighter.name} 復活技能 id=${action.id} 指定存活目標" }
                        return false
                    }
                } else if (!target.isAlive) {
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 非復活技能指定死亡目標 (${action.targetCol},${action.targetRow})" }
                    return false
                }
                val sameSide = fighter.isPlayerSide == target.isPlayerSide
                if (skill == null) {
                    if (sameSide) {
                        logger.warn { "戰鬥 $battleId: ${fighter.name} 普攻指定己方目標" }
                        return false
                    }
                } else {
                    // FightRoleController:CheckSkillTargetLegal uses fightWay, not HP-heal classification.
                    if (sameSide && skill.fightWay in ENEMY_ONLY_FIGHT_WAYS) {
                        logger.warn { "戰鬥 $battleId: ${fighter.name} 敵對技能 id=${action.id} 指定己方" }
                        return false
                    }
                    if (!sameSide && skill.fightWay in FRIENDLY_ONLY_FIGHT_WAYS) {
                        logger.warn { "戰鬥 $battleId: ${fighter.name} 友方技能 id=${action.id} 指定敵方" }
                        return false
                    }
                }
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in NON_BATTLE_SKILLS
            ) {
                // DVT FightField.SetConSkill explicitly blocks these skills in battle.
                logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試使用 Lua 明確禁止的戰鬥外技能 id=${action.id}" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id != BattleConstants.HAND_FIGHT_SKILL_ID
            ) {
                val skill = skillResolver.getSkillDef(action.id)
                if (skill == null) {
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試使用不存在的技能 id=${action.id}" }
                    return false
                }
                if (fighter.sp < skill.requireSp) {
                    logger.debug { "戰鬥 $battleId: ${fighter.name} SP 不足，拒絕技能 id=${action.id} need=${skill.requireSp} sp=${fighter.sp}" }
                    return false
                }
            }
            if (action.actionKind == BattleAction.KIND_CATCH) {
                val target = grid.getAt(action.targetCol, action.targetRow)
                val npcDef = target?.let { t -> involvedNpcDefs.firstOrNull { it.first == t.npcId }?.second }
                if (target == null || !target.isAlive || !target.isEnemySide ||
                    target.kind == EHuman.PLAYER || target.kind == EHuman.PLAYERS ||
                    target.kind == EHuman.FOLLOW_NPC || npcDef == null || npcDef.canBeCatch ||
                    npcDef.level - fighter.level > 5
                ) {
                    // DVT FightRoleController.DoClick manual catch (15002):
                    // enemy NPC only, NpcData.canBeCatch must allow it, and target
                    // level may exceed the actor by at most five.
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試捕捉 Lua 不允許的目標 (${action.targetCol},${action.targetRow})" }
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
                    logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試排入無效/未持有的戰鬥道具 id=${action.id}" }
                    return false
                }
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in BOW_SKILLS &&
                fighter.kind in PLAYER_BOW_CHECK_KINDS &&
                !hasLuaBowWeapon(fighter)
            ) {
                logger.warn { "戰鬥 $battleId: ${fighter.name} 弓技能 id=${action.id} 未裝備 Lua 合法弓武器" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id in SUMMON_SKILLS &&
                !canUseSummon(fighter, action.id)
            ) {
                logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試使用不符合 Lua 元素/紋章條件的召喚 id=${action.id}" }
                return false
            }
            if (action.actionKind == BattleAction.KIND_SKILL &&
                action.id != BattleConstants.HAND_FIGHT_SKILL_ID &&
                action.id !in BattleConstants.DEFEND_SKILL_IDS &&
                action.id !in BattleConstants.ESCAPE_SKILL_IDS &&
                action.id !in BattleConstants.CATCH_SKILL_IDS &&
                action.id !in fighter.skills
            ) {
                logger.warn { "戰鬥 $battleId: ${fighter.name} 嘗試使用未學技能 id=${action.id}" }
                return false
            }
            fighter.pendingAction = action
            fighter.hasActed = true
            logger.debug { "戰鬥 $battleId: ${fighter.name} 設定行動 kind=${action.actionKind} id=${action.id}" }
            return true
        }
        return false
    }

    /**
     * 檢查所有玩家是否已提交指令
     */
    /**
     * 檢查所有玩家操控角色（含武將）是否已提交指令
     */
    fun allPlayersReady(): Boolean {
        return allFighters
            .filter { it.isPlayerControlled && it.isAlive && !it.isCommandBlocked }
            .all { it.hasActed }
    }

    /**
     * 為 NPC 分配 AI 行動
     *
     * 根據技能類型選擇目標：回復技能→己方，攻擊技能→敵方。
     */
    fun resolveNpcActions() {
        val npcs = allFighters.filter {
            !it.isPlayerControlled && it.isAlive && !it.isStunned
        }

        for (npc in npcs) {
            if (npc.pendingAction != null) continue

            val skillId = selectNpcSkill(npc.skills)
            val skillDef = skillResolver.getSkillDef(skillId)
            val isFriendlyUtility = skillDef != null && skillDef.fightWay in FRIENDLY_ONLY_FIGHT_WAYS

            // Never queue a skill the NPC cannot currently pay for.
            // requireSp==1 is the Lua consume-all-SP sentinel and still requires SP >= 1.
            if (skillDef != null && npc.sp < skillDef.requireSp) {
                val basicTargets = if (npc.isPlayerSide) grid.getAliveEnemySide() else grid.getAlivePlayerSide()
                val target = basicTargets.randomOrNull()
                if (target != null) {
                    npc.pendingAction = BattleAction(
                        actionKind = BattleAction.KIND_SKILL,
                        id = BattleConstants.HAND_FIGHT_SKILL_ID,
                        targetCol = target.col,
                        targetRow = target.row
                    )
                    npc.hasActed = true
                    logger.debug { "戰鬥 $battleId: NPC ${npc.name} SP不足，改用普攻 target=(${target.col},${target.row})" }
                }
                continue
            }

            // 根據 NPC 所在陣營決定目標：
            // 敵方 NPC（col 0-1）攻擊→玩家方，回復→敵方
            // 玩家方武將（col 2-3）攻擊→敵方，回復→玩家方
            val targets = if (isFriendlyUtility) {
                if (npc.isPlayerSide) grid.getAlivePlayerSide() else grid.getAliveEnemySide()
            } else {
                if (npc.isPlayerSide) grid.getAliveEnemySide() else grid.getAlivePlayerSide()
            }
            if (targets.isEmpty()) continue

            val target = targets.random()

            npc.pendingAction = BattleAction(
                actionKind = BattleAction.KIND_SKILL,
                id = skillId,
                targetCol = target.col,
                targetRow = target.row
            )
            npc.hasActed = true
            logger.debug { "戰鬥 $battleId: NPC ${npc.name} AI → skill=$skillId friendly=$isFriendlyUtility target=(${target.col},${target.row})" }
        }
    }

    /**
     * 執行一個回合
     *
     * 依敏捷排序所有行動者，逐一解析技能效果。
     *
     * @return 回合結果
     */
    suspend fun resolveRound(): RoundResult {
        state = State.RESOLVING
        val actionResults = mutableListOf<ActionResult>()
        val statusRestores = mutableListOf<StatusRestore>()
        val escapeResults = mutableListOf<EscapeResult>()
        val flyOutTargets = mutableListOf<Pair<Int, Int>>()
        val consumedItems = mutableListOf<ConsumedItem>()

        // 重置所有人的防禦狀態（每回合開始重置，KERO 模式）
        for (fighter in allFighters) {
            fighter.isDefending = false
        }

        // 先處理防禦指令（KERO：防禦不加入隊列，直接設旗標）
        for (fighter in allFighters.filter { it.isAlive && it.hasActed && it.pendingAction != null }) {
            val action = fighter.pendingAction ?: continue
            if (action.actionKind == BattleAction.KIND_DEFEND) {
                fighter.isDefending = true
                fighter.pendingAction = null
                fighter.hasActed = false
                logger.debug { "戰鬥 $battleId: ${fighter.name} 防禦" }
            }
        }

        // Status duration is decremented after actions so round=1 blocks this round.\n\n        // 依敏捷排序（高敏先行動）
        val actingFighters = allFighters
            .filter { it.isAlive && it.hasActed && it.pendingAction != null }
            .sortedByDescending { it.agi }

        // ── Combo 計算 ──────────────────────────────────────────────────────────
        // 重置所有人的 combo 倍率（防止上回合殘留）
        for (fighter in allFighters) fighter.comboMultiplier = 1.0

        // 依序檢查 [i-1, i] 對是否構成 Combo，若是則累計 ×1.2
        for (i in 1 until actingFighters.size) {
            val prev = actingFighters[i - 1]
            val curr = actingFighters[i]
            if (BattleFighter.checkCombo(prev, curr, skillResolver::getSkillDef)) {
                curr.comboMultiplier = prev.comboMultiplier * 1.2
                val multStr = String.format("%.2f", curr.comboMultiplier)
                logger.debug { "戰鬥 $battleId: Combo！${prev.name}→${curr.name} 倍率=$multStr" }
            }
        }
        // ────────────────────────────────────────────────────────────────────────

        for (fighter in actingFighters) {
            // 行動前再檢查：可能在這回合中被擊殺
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
                        currentRound = currentRound
                    )
                    actionResults.add(
                        ActionResult(
                            attackerCol = fighter.col,
                            attackerRow = fighter.row,
                            skillId = action.id,
                            // 範圍技的特效覆蓋由客戶端依此欄位計算（GetEnemyIdx），不可寫死單體
                            fightArea = result.fightArea,
                            targets = result.targets,
                            spConsumed = result.spConsumed
                        )
                    )
                    // 收集被打飛的目標
                    for (tr in result.targets) {
                        if (tr.targetFlyOut) flyOutTargets.add(tr.targetCol to tr.targetRow)
                    }
                }
                BattleAction.KIND_ITEM -> {
                    // 道具擁有者 = 操控此單位的玩家（武將取 masterId）
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
                                    // 19001 = 使用輔助物品演出（客戶端 UseItem.lua）
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
                        logger.debug { "戰鬥 $battleId: ${fighter.name} 使用道具 ${action.id} 無效（無擁有者或目標）" }
                    }
                }
                BattleAction.KIND_CATCH -> {
                    val target = grid.getAt(action.targetCol, action.targetRow)
                    if (target != null && target.isAlive && target.kind != EHuman.PLAYER) {
                        // 等級差限制
                        val levelDiff = fighter.level - target.level
                        val canCatch = levelDiff >= -BattleConstants.CATCH_LEVEL_DIFF

                        // 血量越低成功率越高
                        val hpRatio = target.hp.toDouble() / target.maxHp.toDouble()
                        val baseRate = 0.3 + 0.7 * (1.0 - hpRatio)
                        val levelMod = (levelDiff * 0.02).coerceIn(-0.3, 0.3)
                        val successRate = (baseRate + levelMod).coerceIn(0.05, 0.95)
                        val success = canCatch && Math.random() < successRate

                        val resultSkillId = if (success) BattleConstants.CATCH_NET_SUCCESS
                                           else BattleConstants.CATCH_NET_FAIL

                        logger.info { "戰鬥 $battleId: ${fighter.name} 捕捉 ${target.name}（lv差=$levelDiff hp=${target.hp}/${target.maxHp} 血量=${(hpRatio*100).toInt()}% 成功率=${(successRate*100).toInt()}%）→ ${if (success) "成功" else "失敗"}" }

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
                            capturedNpcs.add(target.npcId)
                        }
                    }
                }
                BattleAction.KIND_ESCAPE -> {
                    // 逃跑成功率：基礎 50%，敏捷差距加成
                    val enemies = if (fighter.isPlayerSide) grid.getAliveEnemySide() else grid.getAlivePlayerSide()
                    val avgEnemyAgi = if (enemies.isNotEmpty()) enemies.sumOf { it.agi } / enemies.size else 0
                    val agiDiff = fighter.agi - avgEnemyAgi
                    val escapeRate = (0.5 + agiDiff * 0.01).coerceIn(0.1, 0.9)
                    val success = Math.random() < escapeRate

                    logger.info { "戰鬥 $battleId: ${fighter.name} 逃跑（敏差=$agiDiff 成功率=${(escapeRate*100).toInt()}%）→ ${if (success) "成功" else "失敗"}" }

                    if (success) {
                        escapeResults.add(EscapeResult(fighter, true))
                    } else {
                        // 逃跑失敗 → 播放失敗動畫（skillId=18002），不造成傷害
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
                                    beHitAction = 3,  // NONE — 不變
                                    attrChanges = emptyList()
                                )),
                                spConsumed = 0
                            )
                        )
                    }
                }
            }

            // 清除行動
            fighter.pendingAction = null
            fighter.hasActed = false
        }

        // Statuses that were already present at action-phase start consume one round now.
        // A status inflicted during this round starts counting from the next round.
        for (fighter in allFighters) {
            if (!fighter.isAlive) continue
            val expiredGroups = mutableListOf<Int>()
            for ((group, status) in fighter.groupedStatuses) {
                if (status.appliedRound >= currentRound) continue
                status.rounds--
                if (status.rounds <= 0) {
                    statusRestores.add(StatusRestore(fighter.col, fighter.row, group, status.skillId))
                    expiredGroups.add(group)
                }
            }
            for (group in expiredGroups) fighter.groupedStatuses.remove(group)
            fighter.syncLegacyStatusView()
        }

        // 清除所有剩餘行動（未行動的角色）並重置 Combo 倍率
        for (fighter in allFighters) {
            fighter.pendingAction = null
            fighter.hasActed = false
            fighter.comboMultiplier = 1.0
        }

        // 逃跑成功者標記死亡並從棋盤移除（戰鬥中離開 = 移除）
        for (escape in escapeResults) {
            escape.fighter.isDead = true
            grid.remove(escape.fighter.col, escape.fighter.row)
        }

        // 移除死亡角色（防禦性：isDead 或 hp<=0 都視為死亡）
        for (fighter in allFighters) {
            if (fighter.isDead || fighter.hp <= 0) {
                fighter.isDead = true
                grid.remove(fighter.col, fighter.row)
            }
        }

        currentRound++
        state = State.ROUND_END

        return RoundResult(actionResults, statusRestores, escapeResults, flyOutTargets, consumedItems)
    }

    /**
     * 檢查戰鬥是否結束
     *
     * @return 結束結果（null=尚未結束）
     */
    fun checkBattleEnd(): BattleEndResult? {
        val aliveEnemies = grid.getAliveEnemySide()
        val alivePlayers = grid.getAlivePlayerSide()
        logger.debug { "戰鬥 $battleId 結束檢查：enemies=${aliveEnemies.size}${aliveEnemies.map { " ${it.name}(hp=${it.hp})" }} players=${alivePlayers.size}" }

        return when {
            aliveEnemies.isEmpty() -> BattleEndResult(playerWin = true)
            alivePlayers.isEmpty() -> BattleEndResult(playerWin = false)
            else -> null
        }
    }

    /**
     * NPC AI 技能選擇 — 參考 KERO TSBattleNPC.seekTarget 概率系統
     *
     * 越高序號的技能越強，使用概率越低：
     * - skill4: 20% | skill3: 25% | skill2: 33% | skill1: 50% | 普攻: 其餘
     */
    private fun selectNpcSkill(skills: IntArray): Int {
        val rand = Random.nextInt(100)
        if (skills.size > 3 && skills[3] > 0 && rand % 5 == 0) return skills[3]
        if (skills.size > 2 && skills[2] > 0 && rand % 4 == 0) return skills[2]
        if (skills.size > 1 && skills[1] > 0 && rand % 3 == 0) return skills[1]
        if (skills.isNotEmpty() && skills[0] > 0 && rand % 2 == 0) return skills[0]
        return BattleConstants.HAND_FIGHT_SKILL_ID
    }

    /**
     * 計算戰鬥獎勵（僅玩家勝利時）
     *
     * @param playerLevel 玩家等級
     * @param npcDropTable NPC 掉落表
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
        private val SUMMON_SKILLS = setOf(10016, 11016, 12016, 13015, 22011, 23011)
        private val BOW_SKILLS = setOf(14033, 14036, 14037, 14064)
        private val SPIRITUAL_BOW_ITEM_IDS = setOf(57601, 57602, 57603, 57604)
        private val PLAYER_BOW_CHECK_KINDS = setOf(EHuman.PLAYER, EHuman.PLAYERS, EHuman.DIVIDE)
        private val CASE_SKILL_EXCEPT_FIGHT_WAYS = setOf(6, 7, 8, 14, 19)
        private const val CASE_SKILL_EXCEPTION_ID = 21026
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
 * 回合執行結果
 */
data class RoundResult(
    /** 每個行動者的攻擊結果 */
    val actionResults: List<ActionResult>,
    /** 狀態異常到期恢復 */
    val statusRestores: List<StatusRestore>,
    /** 逃跑結果 */
    val escapeResults: List<EscapeResult> = emptyList(),
    /** 本回合被打飛的角色位置（col, row） */
    val flyOutTargets: List<Pair<Int, Int>> = emptyList(),
    /** 本回合消耗的道具（背包更新通知用） */
    val consumedItems: List<ConsumedItem> = emptyList()
)

/**
 * 戰鬥中消耗的道具記錄
 */
data class ConsumedItem(
    /** 道具擁有者玩家 ID */
    val ownerPlayerId: Long,
    /** 背包格位 */
    val bagSlot: Int,
    /** 消耗數量 */
    val amount: Int
)

/**
 * 逃跑結果
 */
data class EscapeResult(
    /** 逃跑的角色 */
    val fighter: BattleFighter,
    /** 是否成功 */
    val success: Boolean
)

/**
 * 單個行動的攻擊結果
 */
data class ActionResult(
    /** 攻擊者列 */
    val attackerCol: Int,
    /** 攻擊者行 */
    val attackerRow: Int,
    /** 使用的技能 ID */
    val skillId: Int,
    /** 技能範圍 */
    val fightArea: Int,
    /** 對每個目標的結果 */
    val targets: List<TargetResult>,
    /** 消耗的 SP */
    val spConsumed: Int
)

/**
 * 狀態恢復通知
 */
data class StatusRestore(
    /** 角色列 */
    val col: Int,
    /** 角色行 */
    val row: Int,
    /** 狀態種類 */
    val statusKind: Int,
    /** 技能 ID（造成此狀態的技能） */
    val skillId: Int
)

/**
 * 戰鬥結束結果
 */
data class BattleEndResult(
    /** 玩家是否勝利 */
    val playerWin: Boolean
)

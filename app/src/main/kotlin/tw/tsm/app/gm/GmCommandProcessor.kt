package tw.tsm.app.gm

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.data.*
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.AccountRepository
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.gamelogic.systems.*
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * GM 指令處理器
 *
 * 解析聊天中以 "/" 開頭的指令，分發到對應的處理方法。
 * 所有結果透過 S:002-000 系統訊息回傳給 GM 玩家。
 */
class GmCommandProcessor(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val groundItemManager: GroundItemManager,
    private val characterRepository: CharacterRepository,
    private val statSystem: StatSystem,
    private val accountRepository: AccountRepository,
    private val itemDefs: Map<Int, ItemDef>,
    private val eveData: Map<Int, SceneEveData> = emptyMap(),
    private val petSystem: PetSystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val battleManager: BattleManager,
    private val skillSystem: SkillSystem,
    private val playerStateManager: PlayerStateManager,
    private val questSystem: QuestSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val warpDefs: Map<Int, WarpDef>,
    private val markDefs: Map<Int, MarkDef>
) {

    /**
     * 執行 GM 指令
     *
     * @param session 發送指令的 GM session
     * @param input 完整指令字串（含 "/" 前綴）
     */
    suspend fun execute(session: GameSession, input: String) {
        val parts = input.trim().split("\\s+".toRegex())
        val command = parts[0].lowercase()
        val args = parts.drop(1)

        logger.info { "[GM] ${session.characterName}: $input" }

        try {
            when (command) {
                "/加物品",   "/additem"              -> cmdAddItem(session, args)
                "/移除物品", "/removeitem"            -> cmdRemoveItem(session, args)
                "/加金幣",   "/addgold"               -> cmdAddGold(session, args)
                "/設金幣",   "/setgold"               -> cmdSetGold(session, args)
                "/加元寶",   "/addpoint"              -> cmdAddPoint(session, args)
                "/清背包",   "/clearbag"              -> cmdClearBag(session)
                "/傳送",     "/tp", "/teleport", "/warp" -> cmdTeleport(session, args)
                "/前往",     "/goto"                  -> cmdGoto(session, args)
                "/召喚",     "/summon"                -> cmdSummon(session, args)
                "/設等級",   "/level", "/setlevel"    -> cmdSetLevel(session, args)
                "/資訊",     "/info"                  -> cmdInfo(session)
                "/在線",     "/online"                -> cmdOnline(session)
                "/廣播",     "/broadcast", "/bc", "/motd" -> cmdBroadcast(session, args)
                "/踢人",     "/kick"                  -> cmdKick(session, args)
                "/清地圖",   "/clearmap"              -> cmdClearMap(session)
                "/設權限",   "/op", "/setperm"        -> cmdSetGm(session, args)
                "/位置",     "/where"                  -> cmdWhere(session)
                "/回家",     "/home"                   -> cmdHome(session)
                "/加武將",   "/addpet"                 -> cmdAddPet(session, args)
                "/加經驗",   "/addexp"                 -> cmdAddExp(session, args)
                "/gm訊息",  "/gm"                     -> cmdGmBroadcast(session, args)
                "/設元素",   "/setcharelement"         -> cmdSetElement(session, args)
                "/變身",     "/setoutfit"              -> cmdSetOutfit(session, args)
                "/開倉庫",   "/opensto"                -> cmdOpenStorage(session)
                "/開旅館",   "/hotel"                  -> cmdOpenHotel(session)
                "/戰鬥",     "/battle"                 -> cmdBattle(session, args)
                "/任務",     "/quest"                  -> cmdQuest(session, args)
                "/設稱號",   "/settitle"               -> cmdSetTitle(session, args)
                "/加永標",   "/setflag"                -> cmdSetBitFlag(session, args)
                "/解鎖傳送", "/warpall"                -> cmdWarpAll(session)
                "/武將轉生", "/npcturn"                -> cmdOpenNpcTurn(session, args)
                "/測試",     "/test"                   -> cmdTest(session, args)
                "/指令",     "/help", "/commands"      -> cmdHelp(session)
                else -> reply(session, "未知指令：$command（輸入 /help 查看指令列表）")
            }
        } catch (e: NumberFormatException) {
            reply(session, "參數格式錯誤：請輸入正確的數字")
        } catch (e: IndexOutOfBoundsException) {
            reply(session, "參數不足：輸入 /help 查看用法")
        } catch (e: Exception) {
            logger.error(e) { "GM 指令執行失敗：$input" }
            reply(session, "指令執行錯誤：${e.message}")
        }
    }

    // ── 物品/金錢指令 ──

    /**
     * /additem <id> [qty=1] — 新增物品到背包
     */
    private suspend fun cmdAddItem(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加物品 <物品ID> [數量=1]")
            return
        }
        val itemId = args[0].toInt()
        val qty = args.getOrNull(1)?.toInt() ?: 1

        val def = itemDefs[itemId]
        if (def == null) {
            reply(session, "物品 ID $itemId 不存在")
            return
        }

        val result = inventorySystem.addItemToBag(session.roleId.toLong(), itemId, qty)
        if (result == null) {
            reply(session, "背包已滿，無法新增物品")
            return
        }

        val (slot, thing) = result
        responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        reply(session, "已新增 ${def.name} x$qty 到格位 $slot")
    }

    /**
     * /removeitem <slot> [qty=全部] — 移除背包物品
     */
    private suspend fun cmdRemoveItem(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/移除物品 <格位> [數量=全部]")
            return
        }
        val slot = args[0].toInt()
        val thing = inventorySystem.getBagItem(session.roleId.toLong(), slot)
        if (thing == null) {
            reply(session, "格位 $slot 沒有物品")
            return
        }

        val qty = args.getOrNull(1)?.toInt() ?: thing.quantity
        inventorySystem.removeFromBag(session.roleId.toLong(), slot, qty)
        responseSender.sendReduceBagItem(session, slot, qty)

        val name = itemDefs[thing.itemId]?.name ?: "未知物品"
        reply(session, "已移除格位 $slot 的 $name x$qty")
    }

    /**
     * /addgold <amount> — 增加金幣
     */
    private fun cmdAddGold(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加金幣 <數量>")
            return
        }
        val amount = args[0].toInt()
        if (amount <= 0) {
            reply(session, "數量必須大於 0")
            return
        }

        if (moneySystem.addGold(session.roleId.toLong(), amount)) {
            responseSender.sendAddMoney(session, amount)
            reply(session, "已增加 $amount 金幣（目前：${moneySystem.getGold(session.roleId.toLong())}）")
        } else {
            reply(session, "金幣已達上限")
        }
    }

    /**
     * /addpoint <實點> [虛點] — 增加元寶點數
     *
     * 對應 GM 封包 C:100-015 <加元寶> +實點(4) +虛點(4) 的語意，
     * 透過 S:035-005 刷新客戶端元寶顯示。
     */
    private fun cmdAddPoint(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加元寶 <實點> [虛點]")
            return
        }
        val real = args[0].toInt()
        val virtual = args.getOrNull(1)?.toInt() ?: 0
        if (real < 0 || virtual < 0 || (real == 0 && virtual == 0)) {
            reply(session, "數量必須大於 0")
            return
        }

        val charId = session.roleId.toLong()
        if (real > 0 && !pointSystem.addRealPoint(charId, real)) {
            reply(session, "元寶實點已達上限")
            return
        }
        if (virtual > 0 && !pointSystem.addVirtualPoint(charId, virtual)) {
            reply(session, "元寶虛點已達上限")
            return
        }

        val (newReal, newVirtual) = pointSystem.getPoints(charId)
        responseSender.sendPointBalance(session, newReal, newVirtual)
        reply(session, "已增加元寶：實點+$real 虛點+$virtual（目前：$newReal / $newVirtual）")
    }

    /**
     * /setgold <amount> — 設定金幣數量
     */
    private fun cmdSetGold(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/設金幣 <數量>")
            return
        }
        val target = args[0].toInt()
        if (target < 0) {
            reply(session, "數量不能為負數")
            return
        }

        val current = moneySystem.getGold(session.roleId.toLong())
        val diff = target - current

        if (diff > 0) {
            moneySystem.addGold(session.roleId.toLong(), diff)
            responseSender.sendAddMoney(session, diff)
        } else if (diff < 0) {
            moneySystem.subtractGold(session.roleId.toLong(), -diff)
            responseSender.sendSubtractMoney(session, -diff)
        }

        reply(session, "金幣已設定為 $target")
    }

    /**
     * /clearbag — 清空背包
     */
    private suspend fun cmdClearBag(session: GameSession) {
        val bag = inventorySystem.getBag(session.roleId.toLong())
        for ((slot, _) in bag) {
            inventorySystem.removeFromBag(session.roleId.toLong(), slot, Int.MAX_VALUE)
        }
        responseSender.sendAllBagItems(session, emptyMap(), refresh = false)
        reply(session, "背包已清空（移除 ${bag.size} 個物品）")
    }

    // ── 移動/場景指令 ──

    /**
     * /tp <mapId> <x> <y> — 傳送到指定座標
     */
    private fun cmdTeleport(session: GameSession, args: List<String>) {
        if (args.size < 3) {
            reply(session, "用法：/傳送 <地圖ID> <X> <Y>")
            return
        }
        val mapId = args[0].toInt()
        val x = args[1].toInt()
        val y = args[2].toInt()

        worldManager.leaveMap(session)
        worldManager.enterMap(session, mapId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, mapId, x, y)
        responseSender.sendSceneChange(session, session.playerId, mapId, x, y, session.equipItemIds, session.titleId)
        // S:022-004 由 RelocateHandler 在 C:012-001 後發送
        reply(session, "已傳送到 mapId=$mapId ($x, $y)")
    }

    /**
     * /goto <playerName> — 傳送到指定玩家位置
     */
    private fun cmdGoto(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/前往 <玩家名稱>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "找不到玩家：$targetName")
            return
        }

        worldManager.leaveMap(session)
        worldManager.enterMap(session, target.mapId, target.x, target.y)
        responseSender.sendPlayerTeleport(session, session.playerId, target.mapId, target.x, target.y)
        responseSender.sendSceneChange(session, session.playerId, target.mapId, target.x, target.y, session.equipItemIds, session.titleId)
        // S:022-004 由 RelocateHandler 在 C:012-001 後發送
        reply(session, "已傳送到 ${target.characterName} 所在位置 (mapId=${target.mapId} ${target.x},${target.y})")
    }

    /**
     * /summon <playerName> — 召喚玩家到自己位置
     */
    private fun cmdSummon(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/召喚 <玩家名稱>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "找不到玩家：$targetName")
            return
        }

        worldManager.leaveMap(target)
        worldManager.enterMap(target, session.mapId, session.x, session.y)
        responseSender.sendPlayerTeleport(target, target.playerId, session.mapId, session.x, session.y)
        responseSender.sendSceneChange(target, target.playerId, session.mapId, session.x, session.y, target.equipItemIds, target.titleId)
        // S:022-004 由 RelocateHandler 在 C:012-001 後發送
        reply(session, "已將 ${target.characterName} 召喚到你的位置")
    }

    // ── 角色指令 ──

    /**
     * /setlevel <level> — 設定角色等級（含配點計算）
     */
    private suspend fun cmdSetLevel(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/設等級 <等級>")
            return
        }
        val newLevel = args[0].toInt().coerceIn(1, 200)
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
        if (state == null) {
            reply(session, "找不到玩家狀態")
            return
        }

        synchronized(state) {
            val oldLevel = state.level
            val levelDiff = newLevel - oldLevel

            state.level = newLevel
            state.exp = 0

            // 升級給點數（降級不扣）
            if (levelDiff > 0) {
                state.skillPoint += levelDiff * 2
                state.attrPoint += levelDiff * 3
                skillSystem.adjustSkillPoint(charId, levelDiff * 2)
            }

            // 重算 maxHP/maxSP + 回滿
            val newMaxHp = statSystem.calculateMaxHp(
                level = state.level, hpx = state.hpx + state.equipHpx,
                equipMaxHp = state.equipMaxHp, extraHp = 0,
                turn = state.turn, career = state.career, turn3Lv = 0
            )
            val newMaxSp = statSystem.calculateMaxSp(
                level = state.level, spx = state.spx + state.equipSpx,
                equipMaxSp = state.equipMaxSp, extraSp = 0,
                turn = state.turn, career = state.career, turn3Lv = 0
            )
            playerStateManager.updateMaxHp(charId, newMaxHp)
            playerStateManager.updateMaxSp(charId, newMaxSp)
            playerStateManager.fullHp(charId)
            playerStateManager.fullSp(charId)

            // 通知客戶端所有屬性
            responseSender.sendSetAttribute(session, EAttribute.LV, state.level, true)
            responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.HP, newMaxHp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.SP, newMaxSp, true)
            responseSender.sendSetAttribute(session, EAttribute.SKILL_POINT, state.skillPoint, true)
            responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, state.attrPoint, true)
            session.level = newLevel
        }

        characterRepository.updatePlayerState(
            charId, state.exp, state.level,
            state.attrPoint, state.skillPoint,
            state.intelligence, state.atk, state.def, state.agi, state.hpx, state.spx
        )
        reply(session, "等級已設定為 $newLevel")
    }

    /**
     * /info — 顯示角色資訊
     */
    private fun cmdInfo(session: GameSession) {
        val charId = session.roleId.toLong()
        val gold = moneySystem.getGold(charId)
        val bagCount = inventorySystem.getBag(charId).size
        val equipCount = inventorySystem.getEquipment(charId).size
        val bankCount = inventorySystem.getBank(charId).size

        val info = buildString {
            append("=== 角色資訊 ===\n")
            append("名稱：${session.characterName}\n")
            append("等級：${session.level}\n")
            append("位置：mapId=${session.mapId} (${session.x}, ${session.y})\n")
            append("金幣：$gold\n")
            append("背包：$bagCount 個物品\n")
            append("裝備：$equipCount 件\n")
            append("錢莊：$bankCount 個物品\n")
            append("GM等級：${session.gmLevel}")
        }
        reply(session, info)
    }

    /**
     * /online — 顯示在線玩家
     */
    private fun cmdOnline(session: GameSession) {
        val count = worldManager.onlineCount
        reply(session, "在線玩家：$count 人")
    }

    // ── 管理指令 ──

    /**
     * /broadcast <msg> — 全服系統廣播
     */
    private fun cmdBroadcast(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/廣播 <訊息內容>")
            return
        }
        val message = args.joinToString(" ")
        val data = buildSystemBroadcastPacket(message)
        worldManager.broadcastToAll(data)
        reply(session, "已廣播：$message")
    }

    /**
     * /kick <name> — 踢出玩家
     */
    private fun cmdKick(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/踢人 <玩家名稱>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "找不到玩家：$targetName")
            return
        }

        responseSender.sendDisconnect(target, 4)  // 4 = 被Server踢下線
        target.close()
        reply(session, "已踢出玩家：$targetName")
    }

    /**
     * /設權限 <玩家名> <等級> — 設定玩家的 GM 等級
     */
    private suspend fun cmdSetGm(session: GameSession, args: List<String>) {
        if (args.size < 2) {
            reply(session, "用法：/設權限 <玩家名> <等級>")
            return
        }
        val targetName = args.dropLast(1).joinToString(" ")
        val level = args.last().toInt().coerceIn(0, 99)
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "找不到在線玩家：$targetName")
            return
        }

        // 非 GM 只能設定自己，且最高等級 1（Bootstrap 用）
        if (session.gmLevel == 0) {
            if (target.accountId != session.accountId) {
                reply(session, "權限不足：無法設定其他玩家的等級")
                return
            }
            if (level > 1) {
                reply(session, "權限不足：初次設定最高等級為 1")
                return
            }
        }

        accountRepository.updateGmLevel(target.accountId, level)
        target.gmLevel = level
        reply(target, "你的管理員等級已變更為 $level")
        if (target.accountId != session.accountId) {
            reply(session, "已將 ${target.characterName} 的 GM 等級設為 $level")
        }
    }

    /**
     * /clearmap — 清空目前地圖的地面物品
     */
    private fun cmdClearMap(session: GameSession) {
        groundItemManager.clearMap(session.mapId)
        reply(session, "已清空地圖 ${session.mapId} 的地面物品")
    }

    // ── tskero 對齊指令 ──

    /** /where — 查詢當前位置 */
    private fun cmdWhere(session: GameSession) {
        reply(session, "位置：mapId=${session.mapId} (${session.x}, ${session.y})")
    }

    /** /home — 傳送回家（mapId=12003, 500,500） */
    private fun cmdHome(session: GameSession) {
        cmdTeleport(session, listOf("12003", "500", "500"))
    }

    /** /addpet <npcId> — 新增武將到隨身 */
    private fun cmdAddPet(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加武將 <NPC ID>")
            return
        }
        val npcId = args[0].toInt()
        val def = npcDefs[npcId]
        if (def == null) {
            reply(session, "NPC ID $npcId 不存在")
            return
        }
        val result = petSystem.addFollowNpc(session.roleId.toLong(), npcId, def, canGrow = 1)
        if (result == null) {
            reply(session, "武將已在隊伍中或隊伍已滿")
            return
        }
        val (slot, _) = result
        responseSender.sendFollowNpcAdd(session, session.playerId, slot, npcId, 1, showMsg = true)
        reply(session, "已新增武將 ${def.name} (ID=$npcId) → 欄位 $slot")
    }

    /** /addexp <amount> — 增加經驗值（含自動升級） */
    private fun cmdAddExp(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加經驗 <數量>")
            return
        }
        val amount = args[0].toLong()
        if (amount <= 0) {
            reply(session, "數量必須大於 0")
            return
        }
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
        if (state == null) {
            reply(session, "找不到玩家狀態")
            return
        }
        synchronized(state) {
            state.exp += amount
            responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)

            // 升級迴圈（參考 BattleManager.distributeExp）
            var levelsGained = 0
            while (state.level < 200) {
                val upExp = statSystem.calculateUpExp(state.level, state.turn)
                if (state.exp < upExp) break
                state.exp -= upExp
                state.level++
                levelsGained++
            }

            if (levelsGained > 0) {
                state.skillPoint += levelsGained * 2
                state.attrPoint += levelsGained * 3
                skillSystem.adjustSkillPoint(charId, levelsGained * 2)
                // 重算 maxHP/maxSP + 回滿
                val newMaxHp = statSystem.calculateMaxHp(
                    level = state.level, hpx = state.hpx + state.equipHpx,
                    equipMaxHp = state.equipMaxHp, extraHp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                val newMaxSp = statSystem.calculateMaxSp(
                    level = state.level, spx = state.spx + state.equipSpx,
                    equipMaxSp = state.equipMaxSp, extraSp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                playerStateManager.updateMaxHp(charId, newMaxHp)
                playerStateManager.updateMaxSp(charId, newMaxSp)
                playerStateManager.fullHp(charId)
                playerStateManager.fullSp(charId)

                responseSender.sendSetAttribute(session, EAttribute.LV, state.level, true)
                responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.HP, newMaxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.SP, newMaxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SKILL_POINT, state.skillPoint, true)
                responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, state.attrPoint, true)
                session.level = state.level
                reply(session, "已增加 $amount 經驗值，升了 $levelsGained 級 → Lv.${state.level}")
            } else {
                reply(session, "已增加 $amount 經驗值（Lv.${state.level}）")
            }
        }
    }

    /** /gm <msg> — GM 頻道廣播（S:002-004） */
    private fun cmdGmBroadcast(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/gm <訊息內容>")
            return
        }
        val message = args.joinToString(" ")
        val packet = PacketWriter()
            .writeByte(tw.tsm.domain.constants.Opcodes.CHAT)
            .writeByte(4)   // subKind = 4（GM 頻道）
            .writeByte(0)
            .writeLongLE(session.playerId)
            .writeShortLE(session.titleId) // titleId
            .writeBig5WithByteLen(session.characterName ?: "GM")
            .writeUnicodeWithByteLen(message)
            .writeByte(0)   // itemCount
            .writeByte(0)   // npcCount
            .toByteArray()
        worldManager.broadcastToAll(packet)
        reply(session, "已發送 GM 訊息：$message")
    }

    /** /setcharelement <elem> — 設定角色元素 (1=地, 2=水, 3=火, 4=風) */
    private fun cmdSetElement(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/設元素 <元素ID> (1=地, 2=水, 3=火, 4=風)")
            return
        }
        val elem = args[0].toInt().coerceIn(0, 7)
        session.element = elem
        val cache = skillSystem.getCache(session.roleId.toLong())
        if (cache != null) {
            responseSender.sendPlayerSkillData(session, elem, cache.skillPoint, cache.skills)
        }
        reply(session, "元素已設定為 $elem")
    }

    /** /setoutfit <npcId> — 變身 NPC 外觀（S:005-005），0=隨機 */
    private fun cmdSetOutfit(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/變身 <NPC ID>（0=隨機）")
            return
        }
        var npcId = args[0].toInt()
        if (npcId == 0 && npcDefs.isNotEmpty()) {
            npcId = npcDefs.keys.random()
        }
        val packet = PacketWriter()
            .writeByte(5).writeByte(5).writeByte(0)
            .writeLongLE(session.playerId)
            .writeShortLE(npcId)
            .toByteArray()
        worldManager.broadcastToMap(session.mapId, packet)
        val name = npcDefs[npcId]?.name ?: "未知 NPC"
        reply(session, "已變身為 $name (ID=$npcId)")
    }

    /** /opensto 或 /bank — 開啟錢莊 UI（S:029-009 + S:029-006 + S:030-001）+ 送寶庫物品 */
    private fun cmdOpenStorage(session: GameSession) {
        responseSender.sendBankLevel(session, 0) // TODO：從角色資料取得實際等級
        responseSender.sendOpenBank(session)
        responseSender.sendBankClear(session)
        responseSender.sendStorageClear(session)
        val bankItems = inventorySystem.getBank(session.roleId.toLong())
        responseSender.sendBankAllItems(session, bankItems)
        val storageItems = inventorySystem.getStorage(session.roleId.toLong())
        responseSender.sendStorageAllItems(session, storageItems)
        reply(session, "已開啟錢莊（含寶庫）")
    }

    /** /hotel — 開啟旅館 UI（S:031-010 kind=1 客棧） */
    private fun cmdOpenHotel(session: GameSession) {
        session.send(PacketWriter()
            .writeByte(31).writeByte(10).writeByte(0)
            .writeByte(1) // kind=1 客棧
            .toByteArray())
        reply(session, "已開啟旅館")
    }

    /** /battle <npcId> — 與指定 NPC 發起測試戰鬥 */
    private suspend fun cmdBattle(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/戰鬥 <NPC ID>")
            return
        }
        val npcId = args[0].toInt()
        if (npcDefs[npcId] == null) {
            reply(session, "NPC ID $npcId 不存在")
            return
        }
        if (battleManager.isInBattle(session.playerId)) {
            reply(session, "你已在戰鬥中")
            return
        }
        val fightData = EveFightData(
            eveNo = 0, fightType = 0, linkToResult = 0,
            leftEnemies = listOf(EveFightEnemy(no = 1, npcId = npcId, locationPos = 2, ai = 0)),
            rightEnemies = emptyList(), fightLimit = 0
        )
        battleManager.createPveBattle(session, fightData, terrain = 101)
        reply(session, "已發起與 ${npcDefs[npcId]?.name} 的戰鬥")
    }

    /**
     * /quest list — 列出角色所有進行中任務與已完成標記
     * /quest set <missionId> <step> — 設定任務步驟（0=刪除）
     * /quest flag <bitIndex> <value> — 設定任務完成標記
     */
    private suspend fun cmdQuest(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/quest list | set <任務ID> <步驟> | flag <bitIndex> <值>")
            return
        }
        val charId = session.roleId.toLong()
        when (args[0].lowercase()) {
            "list" -> {
                val missions = questSystem.getMissions(charId)
                val flags = questSystem.getFlags(charId)
                if (missions.isEmpty() && flags.isEmpty()) {
                    reply(session, "無進行中任務，無已完成標記")
                    return
                }
                val sb = buildString {
                    if (missions.isNotEmpty()) {
                        append("進行中任務（${missions.size}）：\n")
                        for (m in missions) {
                            append("  任務${m.missionId} step=${m.step}\n")
                        }
                    }
                    if (flags.isNotEmpty()) {
                        append("已完成標記（${flags.size}）：\n")
                        for (f in flags) {
                            append("  bit${f.bitIndex}=${f.bitValue}\n")
                        }
                    }
                }
                reply(session, sb.trimEnd())
            }
            "set" -> {
                if (args.size < 3) {
                    reply(session, "用法：/quest set <任務ID> <步驟>（步驟=0 刪除任務）")
                    return
                }
                val missionId = args[1].toInt()
                val step = args[2].toInt()
                questSystem.setMissionStep(charId, missionId, step)
                if (step <= 0) {
                    reply(session, "已刪除任務 $missionId")
                } else {
                    reply(session, "已設定任務 $missionId step=$step")
                }
            }
            "flag" -> {
                if (args.size < 3) {
                    reply(session, "用法：/quest flag <bitIndex> <值>")
                    return
                }
                val bitIndex = args[1].toInt()
                val bitValue = args[2].toInt()
                questSystem.setFlag(charId, bitIndex, bitValue)
                reply(session, "已設定任務標記 bit$bitIndex=$bitValue")
            }
            else -> reply(session, "未知子指令：${args[0]}（可用：list / set / flag）")
        }
    }

    /** /設稱號 <titleId> — 直接穿戴指定稱號（0=卸下） */
    private suspend fun cmdSetTitle(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/設稱號 <稱號ID>（0=卸下）")
            return
        }
        val titleId = args[0].toInt()
        session.titleId = titleId
        characterRepository.updateTitleId(session.roleId.toLong(), titleId)
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendTitleChange(player, session.playerId, titleId)
        }
        reply(session, if (titleId == 0) "已卸下稱號" else "已穿戴稱號 ID=$titleId")
    }

    /** /加永標 <flagId> — 設定永久旗標（解鎖稱號等用途） */
    private suspend fun cmdSetBitFlag(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/加永標 <flagId>（設定永久旗標，可用於解鎖稱號）")
            return
        }
        val flagId = args[0].toInt()
        if (flagId <= 0) {
            reply(session, "flagId 必須大於 0")
            return
        }
        bitFlagSystem.setFlag(session.roleId.toLong(), flagId)
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))
        reply(session, "已設定永標 flagId=$flagId")
    }

    /** /warpall — 解鎖所有天行異能傳送點 */
    private suspend fun cmdWarpAll(session: GameSession) {
        val charId = session.roleId.toLong()
        val flagIds = mutableSetOf<Int>()

        for ((_, warpDef) in warpDefs) {
            if (warpDef.mark == 0) continue
            val markDef = markDefs[warpDef.mark] ?: continue
            if (markDef.bitId <= 0) continue
            flagIds.add(markDef.bitId)
        }

        if (flagIds.isEmpty()) {
            reply(session, "找不到需要解鎖的傳送點標記")
            return
        }

        for (flagId in flagIds) {
            bitFlagSystem.setFlag(charId, flagId)
        }

        // 通知客戶端永標變更
        val changes = flagIds.map { it to true }
        responseSender.sendBitFlagChanges(session, changes)

        reply(session, "已解鎖 ${flagIds.size} 個傳送點永標")
    }

    /** /npcturn [1|2] — 開啟武將轉生介面（1=一轉, 2=二轉，預設 1） */
    private fun cmdOpenNpcTurn(session: GameSession, args: List<String>) {
        when (args.getOrNull(0)?.toIntOrNull() ?: 1) {
            1 -> {
                responseSender.sendOpenNpcReincarnation(session)
                reply(session, "已開啟武將轉生（一轉）介面")
            }
            2 -> {
                responseSender.sendOpenNpcSecondReincarnation(session)
                reply(session, "已開啟武將二轉介面")
            }
            else -> reply(session, "用法：/武將轉生 [1|2]")
        }
    }

    /** /test <mainKind> [subKind] — 封包測試工具 */
    private fun cmdTest(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "用法：/測試 <mainKind> [subKind]（省略 subKind 則發送 0-255）")
            return
        }
        val mainKind = args[0].toInt()
        if (args.size >= 2) {
            val subKind = args[1].toInt()
            session.send(PacketWriter().writeByte(mainKind).writeByte(subKind).writeByte(0).toByteArray())
            reply(session, "已發送測試封包：$mainKind-$subKind")
        } else {
            for (i in 0..255) {
                session.send(PacketWriter().writeByte(mainKind).writeByte(i).writeByte(0).toByteArray())
            }
            reply(session, "已發送 $mainKind 的 256 個子協定")
        }
    }

    /**
     * /指令 — 顯示所有可用指令
     */
    private fun cmdHelp(session: GameSession) {
        val help = buildString {
            append("=== GM 指令列表 ===\n")
            append("/加物品 | /additem <ID> [數量] - 新增物品\n")
            append("/移除物品 | /removeitem <格位> [數量] - 移除物品\n")
            append("/加金幣 | /addgold <數量> - 增加金幣\n")
            append("/設金幣 | /setgold <數量> - 設定金幣\n")
            append("/加元寶 | /addpoint <實點> [虛點] - 增加元寶\n")
            append("/清背包 | /clearbag - 清空背包\n")
            append("/加武將 | /addpet <NPC ID> - 新增武將\n")
            append("/加經驗 | /addexp <數量> - 增加經驗值\n")
            append("/傳送 | /tp | /warp <地圖> <X> <Y> - 傳送\n")
            append("/前往 | /goto <玩家名> - 前往玩家\n")
            append("/召喚 | /summon <玩家名> - 召喚玩家\n")
            append("/位置 | /where - 查詢當前位置\n")
            append("/回家 | /home - 回到出生點\n")
            append("/設等級 | /level <等級> - 設定等級\n")
            append("/設元素 | /setcharelement <ID> - 設定元素\n")
            append("/變身 | /setoutfit <NPC ID> - 變身 NPC\n")
            append("/開倉庫 | /opensto - 開啟錢莊\n")
            append("/開旅館 | /hotel - 開啟旅館\n")
            append("/戰鬥 | /battle <NPC ID> - 測試戰鬥\n")
            append("/任務 | /quest list|set|flag - 任務管理\n")
            append("/設稱號 | /settitle <ID> - 穿戴稱號（0=卸下）\n")
            append("/加永標 | /setflag <flagId> - 設定永標（解鎖稱號）\n")
            append("/解鎖傳送 | /warpall - 解鎖所有天行傳送點\n")
            append("/武將轉生 | /npcturn [1|2] - 開啟武將轉生介面\n")
            append("/資訊 | /info - 角色資訊\n")
            append("/在線 | /online - 在線人數\n")
            append("/廣播 | /broadcast | /motd <訊息> - 全服廣播\n")
            append("/gm訊息 | /gm <訊息> - GM 頻道廣播\n")
            append("/踢人 | /kick <玩家名> - 踢出玩家\n")
            append("/清地圖 | /clearmap - 清空地面物品\n")
            append("/設權限 | /op <玩家名> <等級> - 設定權限\n")
            append("/測試 | /test <mainKind> [subKind] - 封包測試\n")
            append("/指令 | /help - 顯示此列表")
        }
        reply(session, help)
    }

    // ── 輔助方法 ──

    /**
     * 回傳系統訊息給 GM 玩家
     */
    private fun reply(session: GameSession, message: String) {
        responseSender.sendSystemMessage(session, message)
    }

    /**
     * 組裝系統頻道廣播封包（S:002-000）
     */
    private fun buildSystemBroadcastPacket(content: String): ByteArray {
        val writer = tw.tsm.protocol.PacketWriter()
            .writeByte(tw.tsm.domain.constants.Opcodes.CHAT)
            .writeByte(0)   // subKind = 0（系統頻道）
            .writeByte(0)   // compress
            .writeLongLE(0) // senderId = 0
            .writeShortLE(0) // titleId = 0
            .writeBig5WithByteLen("系統")
            .writeUnicodeWithByteLen(content)
            .writeByte(0)   // itemCount = 0
            .writeByte(0)   // npcCount = 0
        return writer.toByteArray()
    }
}

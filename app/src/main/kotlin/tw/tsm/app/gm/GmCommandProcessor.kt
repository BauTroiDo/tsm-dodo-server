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
 * Bộ xử lý lệnh GM
 *
 * Phân tích các lệnh trong trò chuyện bắt đầu bằng "/", phân phối tới hàm xử lý tương ứng.
 * Tất cả kết quả trả về cho người chơi GM qua thông báo hệ thống S:002-000.
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
    private val markDefs: Map<Int, MarkDef>,
    private val collectionSystem: CollectionSystem? = null,
) {

    /**
     * Thực thi lệnh GM
     *
     * @param session GM session gửi lệnh
     * @param input chuỗi lệnh đầy đủ (bao gồm tiền tố "/")
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
                else -> reply(session, "Lệnh không rõ ràng: $command (gõ /help để xem danh sách lệnh)")
            }
        } catch (e: NumberFormatException) {
            reply(session, "Tham số sai định dạng: vui lòng nhập số hợp lệ")
        } catch (e: IndexOutOfBoundsException) {
            reply(session, "Tham số thiếu: gõ /help để xem cách dùng")
        } catch (e: Exception) {
            logger.error(e) { "Lệnh GM thực thi thất bại: $input" }
            reply(session, "Lệnh thực thi lỗi: ${e.message}")
        }
    }

    // ── Lệnh vật phẩm/tiền ──

    /**
     * /additem <id> [qty=1] — thêm vật phẩm vào túi đồ
     */
    private suspend fun cmdAddItem(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加物品 <ID vật phẩm> [số lượng=1]")
            return
        }
        val itemId = args[0].toInt()
        val qty = args.getOrNull(1)?.toInt() ?: 1

        val def = itemDefs[itemId]
        if (def == null) {
            reply(session, "ID vật phẩm $itemId không tồn tại")
            return
        }

        val result = inventorySystem.addItemToBag(session.roleId.toLong(), itemId, qty)
        if (result == null) {
            reply(session, "Túi đồ đã đầy, không thể thêm vật phẩm")
            return
        }

        val (slot, thing) = result
        responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        reply(session, "Đã thêm ${def.name} x$qty vào ô $slot")
    }

    /**
     * /removeitem <slot> [qty=tất cả] — loại bỏ vật phẩm trong túi đồ
     */
    private suspend fun cmdRemoveItem(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /移除物品 <ô> [số lượng=tất cả]")
            return
        }
        val slot = args[0].toInt()
        val thing = inventorySystem.getBagItem(session.roleId.toLong(), slot)
        if (thing == null) {
            reply(session, "Ô $slot không có vật phẩm")
            return
        }

        val qty = args.getOrNull(1)?.toInt() ?: thing.quantity
        inventorySystem.removeFromBag(session.roleId.toLong(), slot, qty)
        responseSender.sendReduceBagItem(session, slot, qty)

        val name = itemDefs[thing.itemId]?.name ?: "Vật phẩm không xác định"
        reply(session, "Đã xóa $name x$qty ở ô $slot")
    }

    /**
     * /addgold <amount> — tăng tiền vàng
     */
    private fun cmdAddGold(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加金幣 <số lượng>")
            return
        }
        val amount = args[0].toInt()
        if (amount <= 0) {
            reply(session, "Số lượng phải lớn hơn 0")
            return
        }

        if (moneySystem.addGold(session.roleId.toLong(), amount)) {
            responseSender.sendAddMoney(session, amount)
            reply(session, "Đã thêm $amount tiền vàng (hiện tại: ${moneySystem.getGold(session.roleId.toLong())})")
        } else {
            reply(session, "Tiền vàng đã đạt giới hạn")
        }
    }

    /**
     * /addpoint <điểm thực> [điểm ảo] — tăng điểm nguyên bảo
     *
     * Tương ứng ngữ nghĩa của gói GM C:100-015 <thêm nguyên bảo> +điểm thực(4) +điểm ảo(4),
     * Làm mới hiển thị nguyên bảo trên client qua S:035-005.
     */
    private fun cmdAddPoint(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加元寶 <điểm thực> [điểm ảo]")
            return
        }
        val real = args[0].toInt()
        val virtual = args.getOrNull(1)?.toInt() ?: 0
        if (real < 0 || virtual < 0 || (real == 0 && virtual == 0)) {
            reply(session, "Số lượng phải lớn hơn 0")
            return
        }

        val charId = session.roleId.toLong()
        if (real > 0 && !pointSystem.addRealPoint(charId, real)) {
            reply(session, "Nguyên bảo điểm thực đã đạt giới hạn")
            return
        }
        if (virtual > 0 && !pointSystem.addVirtualPoint(charId, virtual)) {
            reply(session, "Nguyên bảo điểm ảo đã đạt giới hạn")
            return
        }

        val (newReal, newVirtual) = pointSystem.getPoints(charId)
        responseSender.sendPointBalance(session, newReal, newVirtual)
        reply(session, "Đã thêm nguyên bảo: điểm thực+$real điểm ảo+$virtual (hiện tại: $newReal / $newVirtual)")
    }

    /**
     * /setgold <amount> — đặt số lượng tiền vàng
     */
    private fun cmdSetGold(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /設金幣 <số lượng>")
            return
        }
        val target = args[0].toInt()
        if (target < 0) {
            reply(session, "Số lượng không được là số âm")
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

        reply(session, "Tiền vàng đã đặt thành $target")
    }

    /**
     * /clearbag — xoá sạch túi đồ
     */
    private suspend fun cmdClearBag(session: GameSession) {
        val bag = inventorySystem.getBag(session.roleId.toLong())
        for ((slot, _) in bag) {
            inventorySystem.removeFromBag(session.roleId.toLong(), slot, Int.MAX_VALUE)
        }
        responseSender.sendAllBagItems(session, emptyMap(), refresh = false)
        reply(session, "Túi đồ đã được làm trống (đã xóa ${bag.size} vật phẩm)")
    }

    // ── Lệnh di chuyển/cảnh ──

    /**
     * /tp <mapId> <x> <y> — dịch chuyển đến toạ độ chỉ định
     */
    private fun cmdTeleport(session: GameSession, args: List<String>) {
        if (args.size < 3) {
            reply(session, "Cách dùng: /傳送 <ID bản đồ> <X> <Y>")
            return
        }
        val mapId = args[0].toInt()
        val x = args[1].toInt()
        val y = args[2].toInt()

        worldManager.leaveMap(session)
        worldManager.enterMap(session, mapId, x, y)
        responseSender.sendPlayerTeleport(session, session.playerId, mapId, x, y)
        responseSender.sendSceneChange(session, session.playerId, mapId, x, y, session.equipItemIds, session.titleId)
        // S:022-004 được RelocateHandler gửi sau C:012-001
        reply(session, "Đã dịch chuyển đến mapId=$mapId ($x, $y)")
    }

    /**
     * /goto <playerName> — dịch chuyển đến vị trí người chơi chỉ định
     */
    private fun cmdGoto(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /前往 <tên nhân vật>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "Không tìm thấy người chơi: $targetName")
            return
        }

        worldManager.leaveMap(session)
        worldManager.enterMap(session, target.mapId, target.x, target.y)
        responseSender.sendPlayerTeleport(session, session.playerId, target.mapId, target.x, target.y)
        responseSender.sendSceneChange(session, session.playerId, target.mapId, target.x, target.y, session.equipItemIds, session.titleId)
        // S:022-004 được RelocateHandler gửi sau C:012-001
        reply(session, "Đã dịch chuyển đến vị trí của ${target.characterName} (mapId=${target.mapId} ${target.x},${target.y})")
    }

    /**
     * /summon <playerName> — triệu hồi người chơi đến vị trí của mình
     */
    private fun cmdSummon(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /召喚 <tên nhân vật>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "Không tìm thấy người chơi: $targetName")
            return
        }

        worldManager.leaveMap(target)
        worldManager.enterMap(target, session.mapId, session.x, session.y)
        responseSender.sendPlayerTeleport(target, target.playerId, session.mapId, session.x, session.y)
        responseSender.sendSceneChange(target, target.playerId, session.mapId, session.x, session.y, target.equipItemIds, target.titleId)
        // S:022-004 được RelocateHandler gửi sau C:012-001
        reply(session, "Đã triệu hồi ${target.characterName} đến vị trí của bạn")
    }

    // ── Lệnh nhân vật ──

    /**
     * /setlevel <level> — đặt cấp độ nhân vật (bao gồm tính điểm phân bổ)
     */
    private suspend fun cmdSetLevel(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /設等級 <cấp độ>")
            return
        }
        val newLevel = args[0].toInt().coerceIn(1, 200)
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
        if (state == null) {
            reply(session, "Không tìm thấy trạng thái người chơi")
            return
        }

        synchronized(state) {
            val oldLevel = state.level
            val levelDiff = newLevel - oldLevel

            state.level = newLevel
            state.exp = 0

            // Tăng cấp thì cộng điểm (giảm cấp không trừ)
            if (levelDiff > 0) {
                state.skillPoint += levelDiff * 2
                state.attrPoint += levelDiff * 3
                skillSystem.adjustSkillPoint(charId, levelDiff * 2)
            }

            // Tính lại maxHP/maxSP + hồi đầy
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

            // Thông báo cho client tất cả thuộc tính
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
        reply(session, "Cấp độ đã đặt thành $newLevel")
    }

    /**
     * /info — hiển thị thông tin nhân vật
     */
    private fun cmdInfo(session: GameSession) {
        val charId = session.roleId.toLong()
        val gold = moneySystem.getGold(charId)
        val bagCount = inventorySystem.getBag(charId).size
        val equipCount = inventorySystem.getEquipment(charId).size
        val bankCount = inventorySystem.getBank(charId).size

        val info = buildString {
            append("=== THÔNG TIN NHÂN VẬT ===\n")
            append("Tên: ${session.characterName}\n")
            append("Cấp: ${session.level}\n")
            append("Vị trí: mapId=${session.mapId} (${session.x}, ${session.y})\n")
            append("Tiền vàng: $gold\n")
            append("Túi đồ: $bagCount vật phẩm\n")
            append("Trang bị: $equipCount món\n")
            append("Tiền trang: $bankCount vật phẩm\n")
            append("Cấp GM: ${session.gmLevel}")
        }
        reply(session, info)
    }

    /**
     * /online — hiển thị người chơi trực tuyến
     */
    private fun cmdOnline(session: GameSession) {
        val count = worldManager.onlineCount
        reply(session, "Người chơi trực tuyến: $count người")
    }

    // ── Lệnh quản trị ──

    /**
     * /broadcast <msg> — phát rộng hệ thống toàn server
     */
    private fun cmdBroadcast(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /廣播 <nội dung thông báo>")
            return
        }
        val message = args.joinToString(" ")
        val data = buildSystemBroadcastPacket(message)
        worldManager.broadcastToAll(data)
        reply(session, "Đã broadcast: $message")
    }

    /**
     * /kick <name> — đá người chơi
     */
    private fun cmdKick(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /踢人 <tên nhân vật>")
            return
        }
        val targetName = args.joinToString(" ")
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "Không tìm thấy người chơi: $targetName")
            return
        }

        responseSender.sendDisconnect(target, 4)  // 4 = bị Server đá offline
        target.close()
        reply(session, "Đá người chơi: $targetName")
    }

    /**
     * /設權限 <tên người chơi> <cấp độ> — đặt cấp GM của người chơi
     */
    private suspend fun cmdSetGm(session: GameSession, args: List<String>) {
        if (args.size < 2) {
            reply(session, "Cách dùng: /設權限 <tên nhân vật> <cấp>")
            return
        }
        val targetName = args.dropLast(1).joinToString(" ")
        val level = args.last().toInt().coerceIn(0, 99)
        val target = worldManager.getPlayerByName(targetName)
        if (target == null) {
            reply(session, "Không tìm thấy người chơi trực tuyến: $targetName")
            return
        }

        // Không phải GM chỉ đặt được chính mình, và cấp tối đa 1 (dùng cho Bootstrap)
        if (session.gmLevel == 0) {
            if (target.accountId != session.accountId) {
                reply(session, "Không đủ phân quyền: không thể đặt cấp cho người chơi khác")
                return
            }
            if (level > 1) {
                reply(session, "Không đủ phân quyền: lần đầu chỉ đặt tối đa cấp 1")
                return
            }
        }

        accountRepository.updateGmLevel(target.accountId, level)
        target.gmLevel = level
        reply(target, "Cấp quản trị của bạn đã đổi thành $level")
        if (target.accountId != session.accountId) {
            reply(session, "Đã đặt cấp GM của ${target.characterName} thành $level")
        }
    }

    /**
     * /clearmap — xoá sạch vật phẩm trên mặt đất của bản đồ hiện tại
     */
    private fun cmdClearMap(session: GameSession) {
        groundItemManager.clearMap(session.mapId)
        reply(session, "Đã dọn sạch vật phẩm trên mặt đất của bản đồ ${session.mapId}")
    }

    // ── Lệnh căn chỉnh tskero ──

    /** /where — truy vấn vị trí hiện tại*/
    private fun cmdWhere(session: GameSession) {
        reply(session, "Vị trí: mapId=${session.mapId} (${session.x}, ${session.y})")
    }

    /** /home — dịch chuyển về nhà (mapId=12003, 500,500)*/
    private fun cmdHome(session: GameSession) {
        cmdTeleport(session, listOf("12003", "500", "500"))
    }

    /** /addpet <npcId> — thêm tướng vào túi mang theo*/
    private suspend fun cmdAddPet(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加武將 <NPC ID>")
            return
        }
        val npcId = args[0].toInt()
        val def = npcDefs[npcId]
        if (def == null) {
            reply(session, "NPC ID $npcId không tồn tại")
            return
        }
        val result = petSystem.addFollowNpc(session.roleId.toLong(), npcId, def, canGrow = 1)
        if (result == null) {
            reply(session, "Tướng đã ở trong tổ đội hoặc tổ đội đã đầy")
            return
        }
        val (slot, _) = result
        responseSender.sendFollowNpcAdd(session, session.playerId, slot, npcId, 1, showMsg = true)
        collectionSystem?.markWarriorOwned(session.roleId.toLong(), npcId, session)
        reply(session, "Đã thêm tướng ${def.name} (ID=$npcId) → ô $slot")
    }

    /** /addexp <amount> — tăng EXP (bao gồm tự lên cấp)*/
    private fun cmdAddExp(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加經驗 <số lượng>")
            return
        }
        val amount = args[0].toLong()
        if (amount <= 0) {
            reply(session, "Số lượng phải lớn hơn 0")
            return
        }
        val charId = session.roleId.toLong()
        val state = playerStateManager.getState(charId)
        if (state == null) {
            reply(session, "Không tìm thấy trạng thái người chơi")
            return
        }
        synchronized(state) {
            state.exp += amount
            responseSender.sendSetAttribute(session, EAttribute.EXP, state.exp.toInt(), true)

            // Vòng lặp tăng cấp (tham khảo BattleManager.distributeExp)
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
                // Tính lại maxHP/maxSP + hồi đầy
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
                reply(session, "Đã thêm $amount EXP, lên $levelsGained cấp → Lv.${state.level}")
            } else {
                reply(session, "Đã thêm $amount EXP (Lv.${state.level})")
            }
        }
    }

    /** /gm <msg> — phát rộng kênh GM (S:002-004)*/
    private fun cmdGmBroadcast(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /gm <nội dung thông báo>")
            return
        }
        val message = args.joinToString(" ")
        val packet = PacketWriter()
            .writeByte(tw.tsm.domain.constants.Opcodes.CHAT)
            .writeByte(4)   // subKind = 4 (kênh GM)
            .writeByte(0)
            .writeLongLE(session.playerId)
            .writeShortLE(session.titleId) // titleId
            .writeBig5WithByteLen(session.characterName ?: "GM")
            .writeUnicodeWithByteLen(message)
            .writeByte(0)   // itemCount
            .writeByte(0)   // npcCount
            .toByteArray()
        worldManager.broadcastToAll(packet)
        reply(session, "Đã gửi tin nhắn GM: $message")
    }

    /** /setcharelement <elem> — đặt nguyên tố nhân vật (1=đất, 2=nước, 3=lửa, 4=gió)*/
    private fun cmdSetElement(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /設元素 <ID nguyên tố> (1=Đất, 2=Nước, 3=Lửa, 4=Gió)")
            return
        }
        val elem = args[0].toInt().coerceIn(0, 7)
        session.element = elem
        val cache = skillSystem.getCache(session.roleId.toLong())
        if (cache != null) {
            responseSender.sendPlayerSkillData(session, elem, cache.skillPoint, cache.skills)
        }
        reply(session, "Nguyên tố đã đặt thành $elem")
    }

    /** /setoutfit <npcId> — biến hình ngoại hình NPC (S:005-005), 0=ngẫu nhiên*/
    private fun cmdSetOutfit(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /變身 <NPC ID> (0=ngẫu nhiên)")
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
        worldManager.broadcastToMap(session.mapId, packet, instanceId = session.instanceId)
        val name = npcDefs[npcId]?.name ?: "NPC không xác định"
        reply(session, "Đã biến thân thành $name (ID=$npcId)")
    }

    /** /opensto hoặc /bank — mở UI Tiền Trang (S:029-009 + S:029-006 + S:030-001) + gửi vật phẩm kho báu*/
    private fun cmdOpenStorage(session: GameSession) {
        responseSender.sendBankLevel(session, 0) // TODO: lấy cấp độ thực tế từ dữ liệu nhân vật
        responseSender.sendOpenBank(session)
        responseSender.sendBankClear(session)
        responseSender.sendStorageClear(session)
        val bankItems = inventorySystem.getBank(session.roleId.toLong())
        responseSender.sendBankAllItems(session, bankItems)
        val storageItems = inventorySystem.getStorage(session.roleId.toLong())
        responseSender.sendStorageAllItems(session, storageItems)
        reply(session, "Đã mở Tiền trang (bao gồm kho báu)")
    }

    /** /hotel — mở UI quán trọ (S:031-010 kind=1 lữ quán)*/
    private fun cmdOpenHotel(session: GameSession) {
        session.send(PacketWriter()
            .writeByte(31).writeByte(10).writeByte(0)
            .writeByte(1) // kind=1 lữ quán
            .toByteArray())
        reply(session, "Đã mở lữ quán")
    }

    /** /battle <npcId> — bắt đầu chiến đấu thử nghiệm với NPC chỉ định*/
    private suspend fun cmdBattle(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /戰鬥 <NPC ID>")
            return
        }
        val npcId = args[0].toInt()
        if (npcDefs[npcId] == null) {
            reply(session, "NPC ID $npcId không tồn tại")
            return
        }
        if (battleManager.isInBattle(session.playerId)) {
            reply(session, "Bạn đang trong trận chiến")
            return
        }
        val fightData = EveFightData(
            eveNo = 0, fightType = 0, linkToResult = 0,
            leftEnemies = listOf(EveFightEnemy(no = 1, npcId = npcId, locationPos = 2, ai = 0)),
            rightEnemies = emptyList(), fightLimit = 0
        )
        battleManager.createPveBattle(session, fightData, terrain = 101)
        reply(session, "Đã mở trận chiến với ${npcDefs[npcId]?.name}")
    }

    /**
     * /quest list — liệt kê tất cả nhiệm vụ đang tiến hành và cờ đã hoàn thành của nhân vật
     * /quest set <missionId> <step> — đặt bước nhiệm vụ (0=xoá)
     * /quest flag <bitIndex> <value> — đặt cờ hoàn thành nhiệm vụ
     */
    private suspend fun cmdQuest(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /quest list | set <ID nhiệm vụ> <bước> | flag <bitIndex> <giá trị>")
            return
        }
        val charId = session.roleId.toLong()
        when (args[0].lowercase()) {
            "list" -> {
                val missions = questSystem.getMissions(charId)
                val flags = questSystem.getFlags(charId)
                if (missions.isEmpty() && flags.isEmpty()) {
                    reply(session, "Không có nhiệm vụ đang làm, không có mốc hoàn thành")
                    return
                }
                val sb = buildString {
                    if (missions.isNotEmpty()) {
                        append("Nhiệm vụ đang làm (${missions.size}):\n")
                        for (m in missions) {
                            append("  Nhiệm vụ ${m.missionId} step=${m.step}\n")
                        }
                    }
                    if (flags.isNotEmpty()) {
                        append("Mốc hoàn thành (${flags.size}):\n")
                        for (f in flags) {
                            append("  bit${f.bitIndex}=${f.bitValue}\n")
                        }
                    }
                }
                reply(session, sb.trimEnd())
            }
            "set" -> {
                if (args.size < 3) {
                    reply(session, "Cách dùng: /quest set <ID nhiệm vụ> <bước> (bước=0 để xóa nhiệm vụ)")
                    return
                }
                val missionId = args[1].toInt()
                val step = args[2].toInt()
                if (missionId !in 1..65535 || step !in 0..255) {
                    reply(session, "ID nhiệm vụ phải từ 1 đến 65535, bước phải từ 0 đến 255.")
                    return
                }
                questSystem.setMissionStep(charId, missionId, step)
                if (step <= 0) {
                    reply(session, "Đã xóa nhiệm vụ $missionId")
                } else {
                    reply(session, "Đã đặt nhiệm vụ $missionId step=$step")
                }
            }
            "flag" -> {
                if (args.size < 3) {
                    reply(session, "Cách dùng: /quest flag <bitIndex> <giá trị>")
                    return
                }
                val bitIndex = args[1].toInt()
                val bitValue = args[2].toInt()
                questSystem.setFlag(charId, bitIndex, bitValue)
                reply(session, "Đã đặt mốc nhiệm vụ bit$bitIndex=$bitValue")
            }
            else -> reply(session, "Lệnh con không rõ: ${args[0]} (dùng được: list / set / flag)")
        }
    }

    /** /設稱號 <titleId> — mặc trực tiếp danh hiệu chỉ định (0=tháo)*/
    private suspend fun cmdSetTitle(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /設稱號 <ID danh hiệu> (0=tháo)")
            return
        }
        val titleId = args[0].toInt()
        session.titleId = titleId
        characterRepository.updateTitleId(session.roleId.toLong(), titleId)
        val players = worldManager.getPlayersInMap(session.mapId, session.instanceId)
        for (player in players) {
            responseSender.sendTitleChange(player, session.playerId, titleId)
        }
        reply(session, if (titleId == 0) "Đã tháo danh hiệu" else "Đã mang danh hiệu ID=$titleId")
    }

    /** /加永標 <flagId> — đặt cờ vĩnh viễn (dùng để mở khoá danh hiệu v.v.)*/
    private suspend fun cmdSetBitFlag(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /加永標 <flagId> (đặt cờ vĩnh viễn, dùng để mở khóa danh hiệu)")
            return
        }
        val flagId = args[0].toInt()
        if (flagId <= 0) {
            reply(session, "flagId phải lớn hơn 0")
            return
        }
        bitFlagSystem.setFlag(session.roleId.toLong(), flagId)
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))
        reply(session, "Đã đặt cờ vĩnh viễn flagId=$flagId")
    }

    /** /warpall — mở khoá tất cả điểm dịch chuyển Thiên Hành Dị Năng*/
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
            reply(session, "Không tìm thấy mốc điểm dịch chuyển cần mở khóa")
            return
        }

        for (flagId in flagIds) {
            bitFlagSystem.setFlag(charId, flagId)
        }

        // Thông báo cho client thay đổi cờ vĩnh viễn
        val changes = flagIds.map { it to true }
        responseSender.sendBitFlagChanges(session, changes)

        reply(session, "Đã mở khóa ${flagIds.size} cờ vĩnh viễn điểm dịch chuyển")
    }

    /** /npcturn [1|2] — mở giao diện chuyển sinh tướng (1=nhất chuyển, 2=nhị chuyển, mặc định 1)*/
    private fun cmdOpenNpcTurn(session: GameSession, args: List<String>) {
        when (args.getOrNull(0)?.toIntOrNull() ?: 1) {
            1 -> {
                responseSender.sendOpenNpcReincarnation(session)
                reply(session, "Đã mở giao diện chuyển sinh tướng (chuyển 1)")
            }
            2 -> {
                responseSender.sendOpenNpcSecondReincarnation(session)
                reply(session, "Đã mở giao diện chuyển sinh tướng lần 2")
            }
            else -> reply(session, "Cách dùng: /武將轉生 [1|2]")
        }
    }

    /** /test <mainKind> [subKind] — công cụ kiểm thử gói tin*/
    private fun cmdTest(session: GameSession, args: List<String>) {
        if (args.isEmpty()) {
            reply(session, "Cách dùng: /測試 <mainKind> [subKind] (bỏ subKind sẽ gửi 0-255)")
            return
        }
        val mainKind = args[0].toInt()
        if (args.size >= 2) {
            val subKind = args[1].toInt()
            session.send(PacketWriter().writeByte(mainKind).writeByte(subKind).writeByte(0).toByteArray())
            reply(session, "Đã gửi gói tin thử: $mainKind-$subKind")
        } else {
            for (i in 0..255) {
                session.send(PacketWriter().writeByte(mainKind).writeByte(i).writeByte(0).toByteArray())
            }
            reply(session, "Đã gửi 256 sub-protocol của $mainKind")
        }
    }

    /**
     * /指令 — hiển thị tất cả lệnh khả dụng
     */
    private fun cmdHelp(session: GameSession) {
        val help = buildString {
            append("=== DANH SÁCH LỆNH GM ===\n")
            append("/加物品 | /additem <ID> [số lượng] - thêm vật phẩm\n")
            append("/移除物品 | /removeitem <ô> [số lượng] - xóa vật phẩm\n")
            append("/加金幣 | /addgold <số lượng> - thêm tiền vàng\n")
            append("/設金幣 | /setgold <số lượng> - đặt tiền vàng\n")
            append("/加元寶 | /addpoint <điểm thực> [điểm ảo] - thêm nguyên bảo\n")
            append("/清背包 | /clearbag - làm trống túi đồ\n")
            append("/加武將 | /addpet <NPC ID> - thêm tướng\n")
            append("/加經驗 | /addexp <số lượng> - thêm EXP\n")
            append("/傳送 | /tp | /warp <bản đồ> <X> <Y> - dịch chuyển\n")
            append("/前往 | /goto <tên người chơi> - đến chỗ người chơi\n")
            append("/召喚 | /summon <tên người chơi> - triệu hồi người chơi\n")
            append("/位置 | /where - xem vị trí hiện tại\n")
            append("/回家 | /home - về điểm xuất sinh\n")
            append("/設等級 | /level <cấp> - đặt cấp độ\n")
            append("/設元素 | /setcharelement <ID> - đặt nguyên tố\n")
            append("/變身 | /setoutfit <NPC ID> - biến thân NPC\n")
            append("/開倉庫 | /opensto - mở Tiền trang\n")
            append("/開旅館 | /hotel - mở lữ quán\n")
            append("/戰鬥 | /battle <NPC ID> - thử chiến đấu\n")
            append("/任務 | /quest list|set|flag - quản lý nhiệm vụ\n")
            append("/設稱號 | /settitle <ID> - mang danh hiệu (0=tháo)\n")
            append("/加永標 | /setflag <flagId> - đặt cờ vĩnh viễn (mở khóa danh hiệu)\n")
            append("/解鎖傳送 | /warpall - mở khóa toàn bộ điểm dịch chuyển Thiên Hành\n")
            append("/武將轉生 | /npcturn [1|2] - mở giao diện chuyển sinh tướng\n")
            append("/資訊 | /info - thông tin nhân vật\n")
            append("/在線 | /online - số người trực tuyến\n")
            append("/廣播 | /broadcast | /motd <thông báo> - broadcast toàn server\n")
            append("/gm訊息 | /gm <thông báo> - broadcast kênh GM\n")
            append("/踢人 | /kick <tên người chơi> - đá người chơi\n")
            append("/清地圖 | /clearmap - dọn vật phẩm trên mặt đất\n")
            append("/設權限 | /op <tên người chơi> <cấp> - đặt phân quyền\n")
            append("/測試 | /test <mainKind> [subKind] - thử gói tin\n")
            append("/指令 | /help - hiển thị danh sách này")
        }
        reply(session, help)
    }

    // ── Phương thức phụ trợ ──

    /**
     * Trả thông báo hệ thống cho người chơi GM
     */
    private fun reply(session: GameSession, message: String) {
        responseSender.sendSystemMessage(session, message)
    }

    /**
     * Lắp ráp gói tin phát rộng kênh hệ thống (S:002-000)
     */
    private fun buildSystemBroadcastPacket(content: String): ByteArray {
        val writer = tw.tsm.protocol.PacketWriter()
            .writeByte(tw.tsm.domain.constants.Opcodes.CHAT)
            .writeByte(0)   // subKind = 0 (kênh hệ thống)
            .writeByte(0)   // compress
            .writeLongLE(0) // senderId = 0
            .writeShortLE(0) // titleId = 0
            .writeBig5WithByteLen("Hệ thống")
            .writeUnicodeWithByteLen(content)
            .writeByte(0)   // itemCount = 0
            .writeByte(0)   // npcCount = 0
        return writer.toByteArray()
    }
}

package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EGdThing
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.EquipBonusCalculator
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.GroundItemManager
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.ItemUseProcessor
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.gamelogic.systems.PetEquipSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 物品操作處理器（mainKind = 23）
 *
 * 來源：protocal.lua protocolTable[23]
 * 處理背包物品操作、裝備穿脫、地面物品拾取/丟棄。
 */
class ItemHandler(
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val groundItemManager: GroundItemManager,
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val itemDefs: Map<Int, ItemDef>,
    private val itemUseProcessor: ItemUseProcessor,
    private val playerStateManager: PlayerStateManager,
    private val statCalc: StatCalculator,
    private val mountSystem: MountSystem,
    private val flyPointSystem: FlyPointSystem,
    private val characterRepository: CharacterRepository,
    private val petEquipSystem: PetEquipSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.SHOP  // 23

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            2 -> handlePickup(session, data)
            3 -> handleDropBagItem(session, data)
            10 -> handleMoveBagItem(session, data)
            11 -> handleEquip(session, data)
            12 -> handleUnequip(session, data)
            13 -> handleDropEquip(session, data)
            15 -> handleUseItem(session, data)
            16 -> handleDropMoney(session, data)
            17 -> handlePetEquip(session, data)
            18 -> handlePetUnequip(session, data)
            19 -> handlePetDropEquip(session, data)
            46 -> handleRebirth(session, data, targetTurn = 1)
            54 -> handleRebirth(session, data, targetTurn = 2)
            81 -> handleRebirth(session, data, targetTurn = 3)
            73 -> handleFlyPoint(session, data)
            else -> logger.warn { "未處理的物品子協定：subKind=$subKind" }
        }
    }

    /**
     * C:023-046/054/081 玩家一/二/三轉。
     * Lua UITurn wire: hair(1)+color1(Int32LE)+color2(Int32LE)+[career(1) for turn 2/3].
     *
     * Quest/item eligibility is intentionally not invented here. The server still enforces
     * strict monotonic turn progression, packet length, career domain and in-game state.
     */
    private suspend fun handleRebirth(session: GameSession, data: ByteArray, targetTurn: Int) {
        val expected = if (targetTurn == 1) 9 else 10
        if (data.size != expected) return
        val characterId = session.roleId.toLong()
        val state = playerStateManager.getState(characterId) ?: return

        synchronized(state) {
            if (state.turn + 1 != targetTurn) return
        }

        val reader = PacketReader(data)
        val hair = reader.readUByte()
        val color1 = reader.readIntLE()
        val color2 = reader.readIntLE()
        val career = if (targetTurn >= 2) reader.readUByte() else 0
        if (targetTurn >= 2 && career !in 1..4) return

        characterRepository.updateTurnAppearance(characterId, hair, color1, color2)
        characterRepository.updateTurnCareer(characterId, targetTurn, career)

        synchronized(state) {
            state.turn = targetTurn
            state.career = career
        }
        session.hair = hair
        session.color1 = color1
        session.color2 = color2

        // Re-send authoritative player appearance so Role attributes Turn/Career update immediately.
        val record = characterRepository.findById(characterId) ?: return
        responseSender.sendPlayerAppear(session, session.playerId, record, session.serverId)
        logger.info { "${session.characterName} rebirth success: turn=$targetTurn career=$career hair=$hair" }
    }

    /**
     * C:023-002 拾取場景物品
     *
     * 格式：+index(UInt16) +kind(UInt16) +value(UInt16) +x(UInt16) +y(UInt16)
     * 來源：GdThing.lua:82-89
     */
    private suspend fun handlePickup(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val index = reader.readUShortLE()
        val kind = reader.readUShortLE()
        val value = reader.readUShortLE()
        val x = reader.readUShortLE()
        val y = reader.readUShortLE()

        val characterId = session.roleId.toLong()

        // 移除地面物品
        val groundItem = groundItemManager.removeItem(session.mapId, index)
        if (groundItem == null) {
            responseSender.sendPickupResult(session, 1)  // 1=無法拾取
            return
        }

        if (kind == EGdThing.MONEY) {
            // 撿金錢
            if (!moneySystem.addGold(characterId, value)) {
                // 金錢已滿，放回地面
                groundItemManager.addItem(session.mapId, kind, value, x, y, characterId)
                responseSender.sendMoneyFull(session, 1)
                return
            }
            responseSender.sendAddMoney(session, value)
        } else {
            // 撿物品
            val result = inventorySystem.addItemToBag(characterId, value, 1)
            if (result == null) {
                // 背包滿，放回地面
                groundItemManager.addItem(session.mapId, kind, value, x, y, characterId)
                responseSender.sendPickupResult(session, 1)
                return
            }
            val (slot, thing) = result
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }

        // 拾取成功
        responseSender.sendPickupResult(session, 0, kind, value, x, y)

        // 發給撿取者本人（isMe=true）
        session.send(buildRemoveGroundItemPacket(index, true))
        // 廣播給同地圖其他玩家（isMe=false）
        val removeData = buildRemoveGroundItemPacket(index, false)
        worldManager.broadcastToMap(session.mapId, removeData, exclude = session)
    }

    /**
     * C:023-003 丟棄背包物品
     *
     * 格式：+bagIndex(2) +quantity(4)
     * 來源：Item.lua:2626-2650
     */
    private suspend fun handleDropBagItem(session: GameSession, data: ByteArray) {
        if (data.size != 6) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()
        val quantity = reader.readIntLE()
        if (quantity < 0) return

        val characterId = session.roleId.toLong()
        val bagItem = inventorySystem.getBagItem(characterId, bagIndex) ?: return

        val dropQty = if (quantity == 0) bagItem.quantity else minOf(quantity, bagItem.quantity)

        // 從背包移除
        inventorySystem.removeFromBag(characterId, bagIndex, dropQty)
        responseSender.sendReduceBagItem(session, bagIndex, dropQty)

        // 在地面生成
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, bagItem.itemId, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            // 廣播場景物品
            responseSender.sendAddGroundItem(session, bagItem.itemId, session.x, session.y, 1)
            val addData = buildAddGroundItemPacket(bagItem.itemId, session.x, session.y, 0)
            worldManager.broadcastToMap(session.mapId, addData, exclude = session)
        }
    }

    /**
     * C:023-010 移動背包物品
     *
     * 格式：+count(1) +<<from(2) +quantity(4) +to(2)>>
     * 來源：UIBag.lua:402-410
     */
    private suspend fun handleMoveBagItem(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (data.size != 1 + count * 8) return
        if (count > 50) return  // 背包格數上限
        val characterId = session.roleId.toLong()

        for (i in 0 until count) {
            val from = reader.readUShortLE()
            val quantity = reader.readIntLE()
            val to = reader.readUShortLE()

            if (inventorySystem.moveBagItem(characterId, from, quantity, to)) {
                responseSender.sendMoveBagItem(session, from, quantity, to)
            }
        }
    }

    /**
     * C:023-011 穿上裝備
     *
     * 格式：+bagIndex(2)
     * 來源：Item.lua:928-931
     */
    private suspend fun handleEquip(session: GameSession, data: ByteArray) {
        if (data.size != 2) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()

        val characterId = session.roleId.toLong()
        val result = inventorySystem.equipItem(characterId, bagIndex, session.level) ?: return

        val (fitType, oldEquip) = result

        // 通知客戶端穿上裝備
        responseSender.sendEquip(session, bagIndex)

        // 如果有舊裝備被替換到背包，通知客戶端更新
        if (oldEquip != null) {
            responseSender.sendSetBagItem(session, bagIndex, oldEquip, showMsg = false)
        }

        // 更新 session 裝備外觀（供 sendOtherPlayerAppear 使用）
        session.equipItemIds = inventorySystem.getEquipment(characterId)
            .values.map { it.itemId }.toIntArray()

        // 重算裝備加成並同步屬性（參考 tskero equipOnChar → refreshBonus）
        recalcAndSendEquipBonus(session, characterId)

        logger.trace { "穿上裝備：characterId=$characterId bagSlot=$bagIndex fitType=$fitType" }
    }

    /**
     * C:023-012 卸下裝備
     *
     * 格式：+fitType(1) +bagIndex(2)
     * 來源：Item.lua:2699-2703
     */
    private suspend fun handleUnequip(session: GameSession, data: ByteArray) {
        if (data.size != 3) return
        val reader = PacketReader(data)
        val fitType = reader.readUByte()
        @Suppress("UNUSED_VARIABLE")
        val requestedBagIndex = reader.readUShortLE()  // 客戶端建議的背包位，伺服器自行決定

        val characterId = session.roleId.toLong()
        val bagSlot = inventorySystem.unequipItem(characterId, fitType)
        if (bagSlot < 0) {
            logger.warn { "卸下裝備失敗（背包滿？）：characterId=$characterId fitType=$fitType" }
            return
        }

        responseSender.sendUnequip(session, fitType, bagSlot)

        // 更新 session 裝備外觀（供 sendOtherPlayerAppear 使用）
        session.equipItemIds = inventorySystem.getEquipment(characterId)
            .values.map { it.itemId }.toIntArray()

        // 重算裝備加成並同步屬性（參考 tskero unEquipChar → refreshBonus）
        recalcAndSendEquipBonus(session, characterId)

        logger.trace { "卸下裝備：characterId=$characterId fitType=$fitType → bagSlot=$bagSlot" }
    }

    /**
     * C:023-013 丟棄裝備
     *
     * 格式：+bagIndex(1)（此處的 bagIndex 對應裝備部位）
     * 來源：Item.lua:2804-2807
     */
    private suspend fun handleDropEquip(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val fitType = reader.readUByte()

        val characterId = session.roleId.toLong()
        val equipItem = inventorySystem.getEquipItem(characterId, fitType) ?: return

        // 使用 unequipItem 卸下後再丟棄
        val bagSlot = inventorySystem.unequipItem(characterId, fitType)
        if (bagSlot >= 0) {
            inventorySystem.removeFromBag(characterId, bagSlot, equipItem.quantity)
            responseSender.sendUnequip(session, fitType, bagSlot)
            responseSender.sendReduceBagItem(session, bagSlot, equipItem.quantity)

            // 更新 session 裝備外觀
            session.equipItemIds = inventorySystem.getEquipment(characterId)
                .values.map { it.itemId }.toIntArray()

            // 重算裝備加成並同步屬性
            recalcAndSendEquipBonus(session, characterId)

            // 在地面生成
            val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, equipItem.itemId, session.x, session.y, characterId)
            if (gdIndex >= 0) {
                responseSender.sendAddGroundItem(session, equipItem.itemId, session.x, session.y, 1)
            }
        }
    }

    /**
     * C:023-017 武將穿上裝備
     *
     * 格式：+followIndex(1) +bagIndex(2)
     * 來源：Item.lua:947-950
     *
     * 成功回 S:023-023，客戶端自行做背包格 ↔ 裝備格互換
     * （舊裝備回到同一背包格），武將屬性由客戶端本地重算（Item.GetEQIncrease）。
     */
    private suspend fun handlePetEquip(session: GameSession, data: ByteArray) {
        if (data.size != 3) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val bagSlot = reader.readUShortLE()

        val characterId = session.roleId.toLong()
        when (petEquipSystem.equip(characterId, followSlot, bagSlot)) {
            is PetEquipSystem.EquipResult.Ok ->
                responseSender.sendPetEquip(session, followSlot, bagSlot)
            PetEquipSystem.EquipResult.Fail -> {
                // 驗證失敗：客戶端已先擋過一輪，伺服器靜默拒絕（僅留 log）
            }
        }
    }

    /**
     * C:023-018 武將卸下裝備
     *
     * 格式：+followIndex(1) +fitType(1) +bagIndex(2)
     * DVT Item.UnEquip and UnEquipAll both send a UInt16 destination suggestion;
     * the server selects the authoritative destination slot.
     * 來源：Item.lua:2692-2697
     *
     * 成功回 S:023-022（武將卸下裝備到背包）。
     */
    private suspend fun handlePetUnequip(session: GameSession, data: ByteArray) {
        if (data.size != 4) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val fitType = reader.readUByte()
        reader.readUShortLE() // Client suggestion; authoritative destination chosen below.

        val characterId = session.roleId.toLong()
        when (val result = petEquipSystem.unequip(characterId, followSlot, fitType)) {
            is PetEquipSystem.UnequipResult.Ok ->
                responseSender.sendPetUnequipToBag(session, followSlot, fitType, result.bagSlot)
            PetEquipSystem.UnequipResult.BagFull ->
                logger.warn { "武將卸裝失敗（背包滿）：charId=$characterId slot=$followSlot fitType=$fitType" }
            PetEquipSystem.UnequipResult.Fail -> {
                // 資料不存在：靜默拒絕（僅留 log）
            }
        }
    }

    /**
     * C:023-019 丟棄武將裝備
     *
     * 格式：+followIndex(1) +fitType(1)
     * 來源：Item.lua:2798-2802、MachineBox.lua:912-916（機關盒丟壞裝）
     *
     * 成功回 S:023-021（直接從武將身上刪除），並在地面生成掉落物（與玩家丟裝一致）。
     */
    private suspend fun handlePetDropEquip(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val fitType = reader.readUByte()

        val characterId = session.roleId.toLong()
        val dropped = petEquipSystem.dropEquip(characterId, followSlot, fitType) ?: return

        responseSender.sendPetEquipDelete(session, followSlot, fitType)

        // 在地面生成掉落物（與玩家 C:023-013 丟裝行為一致）
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, dropped.itemId, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            responseSender.sendAddGroundItem(session, dropped.itemId, session.x, session.y, 1)
        }
    }

    /**
     * C:023-015 使用物品
     *
     * 格式：+bagIndex(2) +quantity(4) +followIndex(1) +useType(1)
     * 來源：Item.lua:954-961
     *
     * 處理流程：
     * 1. 讀取封包參數
     * 2. 呼叫 ItemUseProcessor 計算效果
     * 3. 扣除物品
     * 4. S:023-015 通知使用成功
     * 5. S:008-001 逐筆發送屬性變化
     */
    private suspend fun handleUseItem(session: GameSession, data: ByteArray) {
        if (data.size != 8) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()
        val quantity = reader.readIntLE()
        val followIndex = reader.readUByte()
        val useType = reader.readUByte()

        val characterId = session.roleId.toLong()
        val bagItem = inventorySystem.getBagItem(characterId, bagIndex) ?: return

        // 坐騎畫冊特殊處理（specialAbility=217）
        val itemDef = itemDefs[bagItem.itemId]
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.MOUNTS) {
            handleMountCollectionItem(session, characterId, bagIndex, bagItem, itemDef)
            return
        }

        val useQty = minOf(quantity, bagItem.quantity)

        // 計算物品使用效果
        val result = itemUseProcessor.processUseItem(
            characterId, bagItem.itemId, useQty, followIndex, useType
        )

        if (!result.consumed) {
            logger.trace { "物品使用未消耗：characterId=$characterId itemId=${bagItem.itemId}" }
            return
        }

        // 扣除物品
        inventorySystem.removeFromBag(characterId, bagIndex, useQty)
        responseSender.sendReduceBagItem(session, bagIndex, useQty)

        // 通知使用成功
        responseSender.sendUseItemSuccess(session, bagItem.itemId, useQty, showMsg = true)

        // 發送屬性變化
        for (change in result.attrChanges) {
            responseSender.sendSetAttribute(
                session, change.attrKind, change.value, change.isPositive
            )
        }

        // 福袋獎勵物品入袋（支援溢出分格）
        for (reward in result.rewardItems) {
            val addedList = inventorySystem.addItemsToBag(characterId, reward.itemId, reward.count)
            for ((slot, thing) in addedList) {
                responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
            }
            if (addedList.isEmpty()) {
                logger.warn { "福袋獎勵入袋失敗（背包滿）：itemId=${reward.itemId} x${reward.count}" }
            }
        }

        // 回城符效果（傳送回儲存點）
        if (result.returnToSavePoint) {
            // 傳送到預設起始點（TODO：改為角色儲存點）
            val targetMap = tw.tsm.domain.constants.DEFAULT_START_MAP
            val targetX = tw.tsm.domain.constants.DEFAULT_START_X
            val targetY = tw.tsm.domain.constants.DEFAULT_START_Y
            worldManager.enterMap(session, targetMap, targetX, targetY)
            responseSender.sendPlayerTeleport(session, session.playerId, targetMap, targetX, targetY)
            responseSender.sendSceneChange(session, session.playerId, targetMap, targetX, targetY, session.equipItemIds, session.titleId)
        }

        logger.trace { "使用物品：characterId=$characterId itemId=${bagItem.itemId} qty=$useQty 效果=${result.attrChanges.size}筆 獎勵=${result.rewardItems.size}筆" }
    }

    /**
     * C:023-016 丟棄金錢到場景
     *
     * 格式：+amount(4)
     * 來源：Item.lua:3174-3177
     */
    private suspend fun handleDropMoney(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val amount = reader.readIntLE()

        val characterId = session.roleId.toLong()
        if (amount <= 0 || !moneySystem.subtractGold(characterId, amount)) return

        responseSender.sendSubtractMoney(session, amount)

        // 在地面生成金錢
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.MONEY, amount, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            // 自己看到
            responseSender.sendDropMoneyToGround(session, amount, session.x, session.y, true)
            // 廣播給其他人
            val broadcastData = buildDropMoneyPacket(amount, session.x, session.y, false)
            worldManager.broadcastToMap(session.mapId, broadcastData, exclude = session)
        }
    }

    // ── 坐騎畫冊使用 ──

    /**
     * 使用坐騎畫冊解鎖坐騎收集
     *
     * 對照 Item.lua L2311-2316：
     * `specialAbility == EItemUseKind.Mounts`，`spare3` = 坐騎 NPC ID。
     * 使用後設定 BitFlag → S:081-001 → 客戶端 `Mounts.OnGetCollection(flagId)`。
     */
    private suspend fun handleMountCollectionItem(
        session: GameSession, characterId: Long,
        bagIndex: Int, bagItem: ThingData, itemDef: ItemDef
    ) {
        val npcId = itemDef.spare3
        val flagId = mountSystem.useCollectionItem(characterId, npcId) ?: return

        // 消耗物品
        inventorySystem.removeFromBag(characterId, bagIndex, 1)
        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)

        // S:081-001 永標更新 → 觸發客戶端 Mounts.OnGetCollection
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))

        // 若自動選擇了此坐騎，通知客戶端
        val mountData = mountSystem.getCache(characterId)
        if (mountData != null && mountData.currentNpcId == npcId) {
            responseSender.sendMountSwitch(session, npcId)
        }
    }

    // ── 裝備加成重算 ──

    /**
     * 重算全身裝備加成並通知客戶端
     *
     * 穿上/卸下/丟棄裝備後統一呼叫。
     * 參考 tskero `TSCharacter.refreshBonus()` 邏輯。
     */
    private fun recalcAndSendEquipBonus(session: GameSession, characterId: Long) {
        val allEquips = inventorySystem.getEquipment(characterId)
        val bonus = EquipBonusCalculator.calculateTotalBonus(allEquips, itemDefs)
        val result = playerStateManager.updateEquipBonus(characterId, bonus, statCalc) ?: return

        responseSender.sendEquipBonusUpdate(
            session, bonus,
            maxHp = result.newMaxHp, maxSp = result.newMaxSp,
            hp = result.newHp, sp = result.newSp
        )
    }

    // ── 記憶傳送符 ──

    /**
     * C:023-073 記憶傳送符
     *
     * 格式：+kind(1B) +index(1B)
     * kind=1 使用（正常流程由 UISell 系統觸發，此處作備用路徑）
     * kind=2 記憶當前位置
     *
     * 來源：UITeleport.lua:623-628
     */
    private suspend fun handleFlyPoint(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val kind = reader.readUByte()
        val index = reader.readUByte()

        val charId = session.roleId.toLong()

        when (kind) {
            2 -> {
                // 記憶當前位置
                if (session.mapId <= 0) return

                val extendCount = flyPointSystem.getExtendCount(charId)
                if (!flyPointSystem.memorize(charId, index, session.mapId, session.x, session.y, extendCount)) {
                    return
                }

                // 重新下發完整記憶傳送符資料
                val points = flyPointSystem.loadAll(charId)
                responseSender.sendFlyPointData(session, extendCount, points)

                logger.info { "${session.characterName} 記憶傳送：index=$index → map=${session.mapId} (${session.x}, ${session.y})" }
            }
            1 -> {
                // 使用傳送（備用路徑，正常由 UISell mainKind=84 處理）
                val points = flyPointSystem.loadAll(charId)
                val point = points[index]
                if (point == null) {
                    logger.warn { "${session.characterName} 記憶傳送失敗：index=$index 不存在" }
                    return
                }

                worldManager.leaveMap(session)
                worldManager.enterMap(session, point.sceneId, point.x, point.y)
                characterRepository.updatePosition(charId, point.sceneId, point.x, point.y)
                responseSender.sendPlayerTeleport(session, session.playerId, point.sceneId, point.x, point.y)
                responseSender.sendSceneChange(
                    session, session.playerId, point.sceneId, point.x, point.y,
                    session.equipItemIds, session.titleId
                )

                logger.info { "${session.characterName} 記憶傳送：index=$index → scene=${point.sceneId} (${point.x}, ${point.y})" }
            }
            else -> logger.warn { "未處理的記憶傳送 kind=$kind" }
        }
    }

    // ── 輔助方法：組裝廣播封包 ──

    private fun buildRemoveGroundItemPacket(index: Int, isMe: Boolean): ByteArray {
        return tw.tsm.protocol.PacketWriter(6)
            .writeByte(Opcodes.SHOP).writeByte(2).writeByte(0)
            .writeShortLE(index)
            .writeBoolean(isMe)
            .toByteArray()
    }

    private fun buildAddGroundItemPacket(itemId: Int, x: Int, y: Int, isMe: Int): ByteArray {
        return tw.tsm.protocol.PacketWriter(18)
            .writeByte(Opcodes.SHOP).writeByte(3).writeByte(0)
            .writeShortLE(itemId)
            .writeShortLE(x)
            .writeShortLE(y)
            .writeByte(isMe)
            .writeLongLE(0L)  // canPickPlayerId = 0（所有人可撿）
            .toByteArray()
    }

    private fun buildDropMoneyPacket(amount: Int, x: Int, y: Int, isMe: Boolean): ByteArray {
        return tw.tsm.protocol.PacketWriter(12)
            .writeByte(Opcodes.SHOP).writeByte(18).writeByte(0)
            .writeIntLE(amount)
            .writeShortLE(x)
            .writeShortLE(y)
            .writeBoolean(isMe)
            .toByteArray()
    }
}

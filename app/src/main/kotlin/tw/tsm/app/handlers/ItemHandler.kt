package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.EGdThing
import tw.tsm.domain.constants.EItemKind
import tw.tsm.domain.constants.EItemUseKind
import tw.tsm.domain.constants.ItemConstants
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.CompoundResultEntry
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.AstrolabeSystem
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.EquipBonusCalculator
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.FlyPointSystem
import tw.tsm.gamelogic.systems.GroundItemManager
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.gamelogic.systems.ItemUseProcessor
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.gamelogic.systems.OfflineSoulGenerationPolicy
import tw.tsm.gamelogic.systems.SoulSystem
import tw.tsm.app.response.SoulResponseSender
import tw.tsm.gamelogic.systems.PetEquipSystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.QuestSystem
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.CollectionSystem
import tw.tsm.gamelogic.systems.CompoundInput
import tw.tsm.gamelogic.systems.CompoundResult
import tw.tsm.gamelogic.systems.CompoundSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý thao tác vật phẩm (mainKind = 23)
 *
 * Nguồn: protocal.lua protocolTable[23]
 * Xử lý thao tác vật phẩm trong túi đồ, mặc/tháo trang bị, nhặt/vứt vật phẩm trên mặt đất.
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
    private val petEquipSystem: PetEquipSystem,
    private val questSystem: QuestSystem,
    private val skillSystem: SkillSystem,
    private val dailyProgressSystem: DailyProgressSystem? = null,
    private val petSystem: PetSystem? = null,
    private val npcDefs: Map<Int, NpcDef> = emptyMap(),
    private val roleCountSystem: RoleCountSystem? = null,
    private val collectionSystem: CollectionSystem? = null,
    private val astrolabeSystem: AstrolabeSystem? = null,
    private val sacredEquipPrimaryDefs: Map<Int, SacredEquipPrimaryDef> = emptyMap(),
    private val compoundSystem: CompoundSystem? = null,
    private val soulSystem: SoulSystem? = null,
    private val soulGenerationPolicy: OfflineSoulGenerationPolicy? = null,
    private val soulResponseSender: SoulResponseSender? = null,
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
            14 -> handleCompound(session, data)
            15 -> handleUseItem(session, data)
            16 -> handleDropMoney(session, data)
            17 -> handlePetEquip(session, data)
            18 -> handlePetUnequip(session, data)
            19 -> handlePetDropEquip(session, data)
            46 -> handleRebirth(session, data, targetTurn = 1)
            54 -> handleRebirth(session, data, targetTurn = 2)
            81 -> handleRebirth(session, data, targetTurn = 3)
            73 -> handleFlyPoint(session, data)
            76 -> handleCareerChange(session, data)
            else -> logger.warn { "Sub-protocol vật phẩm chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:023-014 Chế tạo.
     * Lua UICompound.SendCompound(): 3 × [bagIndex(UInt16 LE) + quantity(Int32 LE)] + kind(Byte).
     * Ô trống luôn được gửi dưới dạng 0/0; tab Chế tạo dùng kind=1.
     */
    private suspend fun handleCompound(session: GameSession, data: ByteArray) {
        if (data.size != 19) return
        val system = compoundSystem ?: return
        val reader = PacketReader(data)
        val inputs = ArrayList<CompoundInput>(3)
        repeat(3) {
            val bagIndex = reader.readUShortLE()
            val quantity = reader.readIntLE()
            if (bagIndex == 0 && quantity == 0) return@repeat
            if (bagIndex <= 0 || quantity <= 0) return
            inputs += CompoundInput(bagIndex, quantity)
        }
        val kind = reader.readUByte()
        if (inputs.isEmpty()) return

        when (val result = system.compound(session.roleId.toLong(), kind, inputs)) {
            is CompoundResult.Success -> {
                for ((slot, qty) in result.consumedBySlot) {
                    responseSender.sendReduceBagItem(session, slot, qty)
                }
                responseSender.sendSetBagItem(session, result.resultSlot, result.result, showMsg = false)
                responseSender.sendCompoundResult(
                    session,
                    listOf(
                        CompoundResultEntry(
                            itemId = result.result.itemId,
                            quantity = result.result.quantity,
                            bagIndex = result.resultSlot,
                        )
                    )
                )
                logger.info {
                    "${session.characterName} compound success: kind=$kind inputs=$inputs " +
                        "result=${result.result.itemId}x${result.result.quantity} slot=${result.resultSlot}"
                }
            }
            CompoundResult.InvalidRequest,
            CompoundResult.BagFull,
            CompoundResult.UnsupportedKind -> Unit
        }
    }

    /** C:023-076 Phù đổi nghề: bagIndex(UInt16 LE) + career(Byte).*/
    private suspend fun handleCareerChange(session: GameSession, data: ByteArray) {
        if (data.size != 3) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()
        val newCareer = reader.readUByte()
        if (newCareer !in 1..4) return

        val characterId = session.roleId.toLong()
        val state = playerStateManager.getState(characterId) ?: return
        val currentTurn: Int
        val oldCareer: Int
        synchronized(state) {
            currentTurn = state.turn
            oldCareer = state.career
        }
        if (currentTurn < 2 || oldCareer == newCareer) return

        val item = inventorySystem.getBagItem(characterId, bagIndex) ?: return
        val itemDef = itemDefs[item.itemId] ?: return
        if (item.quantity <= 0 || itemDef.specialAbility != EItemUseKind.CAREER_CHANGE) return

        // Item.lua requires 11495 before opening career UI.
        if (!questSystem.hasMissionFlag(characterId, 11495)) return
        val requiredMission = when (newCareer) {
            1 -> 11487
            2 -> 11463
            3 -> 11491
            4 -> 11489
            else -> return
        }
        if (!questSystem.hasMissionFlag(characterId, requiredMission)) return

        // Validate everything before consuming the token.
        if (!inventorySystem.removeFromBag(characterId, bagIndex, 1)) return
        characterRepository.updateTurnCareer(characterId, currentTurn, newCareer)
        skillSystem.resetTurn2CareerSkills(characterId)
        skillSystem.save(characterId)
        synchronized(state) { state.career = newCareer }

        responseSender.sendPlayerCareerChange(
            session, session.playerId, session.hair, session.color1, session.color2,
            currentTurn, newCareer
        )
        logger.info { "${session.characterName} career changed: $oldCareer -> $newCareer, bagIndex=$bagIndex" }
    }

    /**
     * C:023-046/054/081 Người chơi chuyển sinh 1/2/3.
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
        session.turn = targetTurn
        session.career = career
        if (targetTurn == 3) session.turn3Element = 0
        session.hair = hair
        session.color1 = color1
        session.color2 = color2

        // Lua protocolTable[23][69] is the dedicated authoritative turn/career update.
        responseSender.sendPlayerCareerChange(
            session, session.playerId, hair, color1, color2, targetTurn, career
        )
        logger.info { "${session.characterName} rebirth success: turn=$targetTurn career=$career hair=$hair" }
    }

    /**
     * C:023-002 Nhặt vật phẩm trong cảnh
     *
     * Định dạng: +index(UInt16) +kind(UInt16) +value(UInt16) +x(UInt16) +y(UInt16)
     * Nguồn: GdThing.lua:82-89
     */
    private suspend fun handlePickup(session: GameSession, data: ByteArray) {
        if (data.size != 10) { responseSender.sendPickupResult(session, 1); return }
        val reader = PacketReader(data)
        val index = reader.readUShortLE()
        val kind = reader.readUShortLE()
        val value = reader.readUShortLE()
        val x = reader.readUShortLE()
        val y = reader.readUShortLE()

        val characterId = session.roleId.toLong()

        val expected = groundItemManager.getItem(session.mapId, index)
        if (expected == null || expected.kind != kind || expected.value != value || expected.x != x || expected.y != y) {
            responseSender.sendPickupResult(session, 1)
            return
        }

        // xóa vật phẩm trên mặt đất
        val groundItem = groundItemManager.removeItem(session.mapId, index)
        if (groundItem == null) {
            responseSender.sendPickupResult(session, 1)  // 1=không thể nhặt
            return
        }

        if (kind == EGdThing.MONEY) {
            // nhặt tiền
            if (!moneySystem.addGold(characterId, value)) {
                // tiền đã đầy, đặt trả về mặt đất
                groundItemManager.restoreItem(session.mapId, groundItem)
                responseSender.sendMoneyFull(session, 1)
                return
            }
            responseSender.sendAddMoney(session, value)
        } else {
            // nhặt vật phẩm
            val result = inventorySystem.addItemToBag(characterId, value, 1)
            if (result == null) {
                // túi đồ đầy, đặt trả về mặt đất
                groundItemManager.restoreItem(session.mapId, groundItem)
                responseSender.sendPickupResult(session, 1)
                return
            }
            val (slot, thing) = result
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }

        // nhặt thành công
        responseSender.sendPickupResult(session, 0, kind, value, x, y)

        // gửi cho chính người nhặt (isMe=true)
        session.send(buildRemoveGroundItemPacket(index, true))
        // phát rộng cho người chơi khác cùng bản đồ (isMe=false)
        val removeData = buildRemoveGroundItemPacket(index, false)
        worldManager.broadcastToMap(session.mapId, removeData, exclude = session)
    }

    /**
     * C:023-003 Vứt vật phẩm túi đồ
     *
     * Định dạng: +bagIndex(2) +quantity(4)
     * Nguồn: Item.lua:2626-2650
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

        // xóa khỏi túi đồ
        inventorySystem.removeFromBag(characterId, bagIndex, dropQty)
        responseSender.sendReduceBagItem(session, bagIndex, dropQty)

        // sinh ra trên mặt đất
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, bagItem.itemId, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            // phát rộng vật phẩm trong cảnh
            responseSender.sendAddGroundItem(session, bagItem.itemId, session.x, session.y, 1)
            val addData = buildAddGroundItemPacket(bagItem.itemId, session.x, session.y, 0)
            worldManager.broadcastToMap(session.mapId, addData, exclude = session)
        }
    }

    /**
     * C:023-010 Di chuyển vật phẩm túi đồ
     *
     * Định dạng: +count(1) +<<from(2) +quantity(4) +to(2)>>
     * Nguồn: UIBag.lua:402-410
     */
    private suspend fun handleMoveBagItem(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (data.size != 1 + count * 8) return
        if (count > 50) return  // giới hạn số ô túi đồ
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
     * C:023-011 Mặc trang bị
     *
     * Định dạng: +bagIndex(2)
     * Nguồn: Item.lua:928-931
     */
    private suspend fun handleEquip(session: GameSession, data: ByteArray) {
        if (data.size != 2) return
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()

        val characterId = session.roleId.toLong()
        val result = inventorySystem.equipItem(characterId, bagIndex, session.level) ?: return

        val (fitType, oldEquip) = result

        // thông báo client mặc trang bị
        responseSender.sendEquip(session, bagIndex)

        // nếu có trang bị cũ bị đẩy về túi đồ, thông báo client cập nhật
        if (oldEquip != null) {
            responseSender.sendSetBagItem(session, bagIndex, oldEquip, showMsg = false)
        }

        // cập nhật diện mạo trang bị của session (để sendOtherPlayerAppear sử dụng)
        session.equipItemIds = inventorySystem.getEquipment(characterId)
            .values.map { it.itemId }.toIntArray()

        // tính lại cộng hưởng trang bị và đồng bộ thuộc tính (tham khảo tskero equipOnChar → refreshBonus)
        recalcAndSendEquipBonus(session, characterId)

        logger.trace { "Mặc trang bị: characterId=$characterId bagSlot=$bagIndex fitType=$fitType" }
    }

    /**
     * C:023-012 Tháo trang bị
     *
     * Định dạng: +fitType(1) +bagIndex(2)
     * Nguồn: Item.lua:2699-2703
     */
    private suspend fun handleUnequip(session: GameSession, data: ByteArray) {
        if (data.size != 3) return
        val reader = PacketReader(data)
        val fitType = reader.readUByte()
        @Suppress("UNUSED_VARIABLE")
        val requestedBagIndex = reader.readUShortLE()  // vị trí túi đồ do client đề nghị, server tự quyết định

        val characterId = session.roleId.toLong()
        val bagSlot = inventorySystem.unequipItem(characterId, fitType)
        if (bagSlot < 0) {
            logger.warn { "Tháo trang bị thất bại (túi đồ đầy?): characterId=$characterId fitType=$fitType" }
            return
        }

        responseSender.sendUnequip(session, fitType, bagSlot)

        // cập nhật diện mạo trang bị của session (để sendOtherPlayerAppear sử dụng)
        session.equipItemIds = inventorySystem.getEquipment(characterId)
            .values.map { it.itemId }.toIntArray()

        // tính lại cộng hưởng trang bị và đồng bộ thuộc tính (tham khảo tskero unEquipChar → refreshBonus)
        recalcAndSendEquipBonus(session, characterId)

        logger.trace { "Tháo trang bị: characterId=$characterId fitType=$fitType → bagSlot=$bagSlot" }
    }

    /**
     * C:023-013 Vứt trang bị
     *
     * Định dạng: +bagIndex(1) (bagIndex ở đây ứng với vị trí trang bị)
     * Nguồn: Item.lua:2804-2807
     */
    private suspend fun handleDropEquip(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val fitType = reader.readUByte()

        val characterId = session.roleId.toLong()
        val equipItem = inventorySystem.getEquipItem(characterId, fitType) ?: return

        // dùng unequipItem tháo ra rồi mới vứt
        val bagSlot = inventorySystem.unequipItem(characterId, fitType)
        if (bagSlot >= 0) {
            inventorySystem.removeFromBag(characterId, bagSlot, equipItem.quantity)
            responseSender.sendUnequip(session, fitType, bagSlot)
            responseSender.sendReduceBagItem(session, bagSlot, equipItem.quantity)

            // cập nhật diện mạo trang bị của session
            session.equipItemIds = inventorySystem.getEquipment(characterId)
                .values.map { it.itemId }.toIntArray()

            // tính lại cộng hưởng trang bị và đồng bộ thuộc tính
            recalcAndSendEquipBonus(session, characterId)

            // sinh ra trên mặt đất
            val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, equipItem.itemId, session.x, session.y, characterId)
            if (gdIndex >= 0) {
                responseSender.sendAddGroundItem(session, equipItem.itemId, session.x, session.y, 1)
            }
        }
    }

    /**
     * C:023-017 Tướng mặc trang bị
     *
     * Định dạng: +followIndex(1) +bagIndex(2)
     * Nguồn: Item.lua:947-950
     *
     * Thành công trả về S:023-023, client tự đổi chỗ ô túi đồ ↔ ô trang bị
     * (trang bị cũ quay về đúng ô túi đồ đó), thuộc tính tướng do client tự tính lại nội bộ (Item.GetEQIncrease).
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
                // kiểm tra hợp lệ thất bại: client đã chặn một vòng trước đó, server từ chối im lặng (chỉ lưu log)
            }
        }
    }

    /**
     * C:023-018 Tướng tháo trang bị
     *
     * Định dạng: +followIndex(1) +fitType(1) +bagIndex(2)
     * DVT Item.UnEquip and UnEquipAll both send a UInt16 destination suggestion;
     * the server selects the authoritative destination slot.
     * Nguồn: Item.lua:2692-2697
     *
     * Thành công trả về S:023-022 (tướng tháo trang bị vào túi đồ).
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
                logger.warn { "Tướng tháo trang bị thất bại (túi đồ đầy): charId=$characterId slot=$followSlot fitType=$fitType" }
            PetEquipSystem.UnequipResult.Fail -> {
                // dữ liệu không tồn tại: từ chối im lặng (chỉ lưu log)
            }
        }
    }

    /**
     * C:023-019 Vứt trang bị của tướng
     *
     * Định dạng: +followIndex(1) +fitType(1)
     * Nguồn: Item.lua:2798-2802, MachineBox.lua:912-916 (hộp cơ quan vứt trang bị hỏng)
     *
     * Thành công trả về S:023-021 (xoá trực tiếp khỏi tướng), và sinh vật rơi trên mặt đất (giống người chơi vứt trang bị).
     */
    private suspend fun handlePetDropEquip(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val fitType = reader.readUByte()

        val characterId = session.roleId.toLong()
        val dropped = petEquipSystem.dropEquip(characterId, followSlot, fitType) ?: return

        responseSender.sendPetEquipDelete(session, followSlot, fitType)

        // sinh vật rơi trên mặt đất (giống hành vi vứt trang bị C:023-013 của người chơi)
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.ITEM, dropped.itemId, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            responseSender.sendAddGroundItem(session, dropped.itemId, session.x, session.y, 1)
        }
    }

    /**
     * C:023-015 Dùng vật phẩm
     *
     * Định dạng: +bagIndex(2) +quantity(4) +followIndex(1) +useType(1)
     * Nguồn: Item.lua:954-961
     *
     * Quy trình xử lý:
     * 1. Đọc tham số gói tin
     * 2. Gọi ItemUseProcessor tính hiệu quả
     * 3. Khấu trừ vật phẩm
     * 4. S:023-015 Thông báo sử dụng thành công
     * 5. S:008-001 Gửi từng bản thay đổi thuộc tính
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

        // Các direct-use đặc biệt cần mutation server + packet riêng.
        val itemDef = itemDefs[bagItem.itemId]
        if (collectionSystem?.findNftByGetId(bagItem.itemId, 1) != null) {
            handleNftCardItem(session, characterId, bagIndex, bagItem)
            return
        }
        if (
            itemDef != null &&
            itemDef.kind == EItemKind.TRANSFORM &&
            itemDef.specialAbility == EItemUseKind.TRANSFORM_DOLL
        ) {
            handleTransformDoll(session, characterId, bagIndex, bagItem, itemDef, followIndex)
            return
        }
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.NPC_PICTURE) {
            handleNpcPictureItem(session, characterId, bagIndex, bagItem, itemDef)
            return
        }
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.MOUNTS) {
            handleMountCollectionItem(session, characterId, bagIndex, bagItem, itemDef)
            return
        }
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.ROLE_COUNT) {
            handleRoleCountItem(session, characterId, bagIndex, bagItem, itemDef, quantity)
            return
        }
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.ASTROLABE) {
            handleAstrolabePointItem(session, characterId, bagIndex, bagItem, itemDef, quantity)
            return
        }
        if (itemDef != null && itemDef.specialAbility == EItemUseKind.SOUL) {
            handleSoulItem(session, characterId, bagIndex, bagItem)
            return
        }

        val useQty = minOf(quantity, bagItem.quantity)

        // tính hiệu quả sử dụng vật phẩm
        val result = itemUseProcessor.processUseItem(
            characterId, bagItem.itemId, useQty, followIndex, useType
        )

        if (!result.consumed) {
            logger.trace { "Dùng vật phẩm mà không tiêu hao: characterId=$characterId itemId=${bagItem.itemId}" }
            return
        }

        // khấu trừ vật phẩm
        inventorySystem.removeFromBag(characterId, bagIndex, useQty)
        responseSender.sendReduceBagItem(session, bagIndex, useQty)

        // thông báo sử dụng thành công
        responseSender.sendUseItemSuccess(session, bagItem.itemId, useQty, showMsg = true)

        // gửi thay đổi thuộc tính
        // DVT S:008-001/S:008-002 đều là 'đặt giá trị tuyệt đối' chứ không phải gia số.
        // tướng dùng S:008-002; nhân vật chính dùng S:008-001.
        for (change in result.attrChanges) {
            if (followIndex > 0) {
                responseSender.sendFollowNpcAttribute(
                    session, followIndex, change.attrKind, change.value
                )
            } else {
                responseSender.sendSetAttribute(
                    session, change.attrKind, change.value, change.isPositive
                )
            }
        }

        // vật phẩm thưởng túi phúc vào túi (hỗ trợ tràn sang ô khác)
        for (reward in result.rewardItems) {
            val addedList = inventorySystem.addItemsToBag(characterId, reward.itemId, reward.count)
            for ((slot, thing) in addedList) {
                responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
            }
            if (addedList.isEmpty()) {
                logger.warn { "Thưởng túi may mắn vào túi thất bại (túi đầy): itemId=${reward.itemId} x${reward.count}" }
            }
        }

        // hiệu ứng phù về thành (dịch chuyển về điểm lưu)
        if (result.returnToSavePoint) {
            // dịch chuyển đến điểm bắt đầu mặc định (TODO: đổi thành điểm lưu của nhân vật)
            val targetMap = tw.tsm.domain.constants.DEFAULT_START_MAP
            val targetX = tw.tsm.domain.constants.DEFAULT_START_X
            val targetY = tw.tsm.domain.constants.DEFAULT_START_Y
            worldManager.enterMap(session, targetMap, targetX, targetY)
            responseSender.sendPlayerTeleport(session, session.playerId, targetMap, targetX, targetY)
            responseSender.sendSceneChange(session, session.playerId, targetMap, targetX, targetY, session.equipItemIds, session.titleId)
        }

        logger.trace { "Dùng vật phẩm: characterId=$characterId itemId=${bagItem.itemId} qty=$useQty hiệu ứng=${result.attrChanges.size} dòng thưởng=${result.rewardItems.size} dòng" }
    }

    private suspend fun handleSoulItem(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
    ) {
        val system = soulSystem ?: return
        val policy = soulGenerationPolicy ?: return
        val sender = soulResponseSender ?: return
        if (bagItem.quantity <= 0) return

        val template = policy.generate(bagItem.itemId)
        if (template == null) {
            logger.warn { "Không tạo được Soul template cho itemId=" + bagItem.itemId }
            return
        }

        val created = system.create(characterId, template)
        if (created == null) {
            logger.warn { "Tạo/persist Soul thất bại hoặc túi Hồn đầy: characterId=" + characterId + " itemId=" + bagItem.itemId }
            return
        }

        if (!inventorySystem.removeFromBag(characterId, bagIndex, 1)) {
            val rolledBack = system.delete(characterId, created.sn)
            logger.error {
                "Đã tạo Soul nhưng không trừ được item, rollback=" + rolledBack +
                    " characterId=" + characterId + " itemId=" + bagItem.itemId + " sn=" + created.sn
            }
            return
        }

        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)

        // Lua DVT protocolTable[120][8]: result != 1 appends every received
        // Soul into the next empty Soul bag slot. Therefore result=2 must carry
        // only the newly created Soul, never the full snapshot.
        sender.sendSnapshot(
            session = session,
            souls = listOf(created),
            result = SoulResponseSender.RESULT_GACHA,
        )

        logger.info {
            "CUSTOM OFFLINE Soul created: characterId=" + characterId +
                " itemId=" + created.itemId + " sn=" + created.sn +
                " element=" + created.element + " startAtt=" + created.startAtt +
                " skills=" + created.skills
        }
    }

    /**
     * DVT EItemKind.Transform=48 + EItemUseKind.TransformDoll=5.
     * Item_C.dat: malePicId chính là NPC ID đích; Lua GetMaxUseCount mặc định 1.
     * Trạng thái biến hình chỉ tồn tại trong runtime vì login PlayerData không mang npcId.
     */
    private suspend fun handleTransformDoll(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
        itemDef: ItemDef,
        followIndex: Int,
    ) {
        if (followIndex != 0) return

        val targetNpcId = itemDef.malePicId
        if (targetNpcId <= 0 || npcDefs[targetNpcId] == null) {
            logger.warn {
                "TransformDoll dữ liệu không hợp lệ: itemId=${bagItem.itemId} malePicId=$targetNpcId"
            }
            return
        }

        val runtimeState = playerStateManager.getState(characterId) ?: return
        if (!inventorySystem.removeFromBag(characterId, bagIndex, 1)) return
        runtimeState.transformNpcId = targetNpcId

        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)

        responseSender.sendPlayerTransform(session, session.playerId, targetNpcId)
        worldManager.getPlayersInMap(session.mapId, session.instanceId)
            .filter { it !== session && it.loginFinished }
            .forEach { target ->
                responseSender.sendPlayerTransform(target, session.playerId, targetNpcId)
            }

        logger.info {
            "${session.characterName} dùng TransformDoll itemId=${bagItem.itemId} -> npcId=$targetNpcId"
        }
    }

    /**
     * DVT EItemUseKind.Astrolabe=211.
     * Item.lua giới hạn số lượng theo floor((65535-currentPoint) / itemData.elementValue).
     */
    private suspend fun handleAstrolabePointItem(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
        itemDef: ItemDef,
        requestedQuantity: Int
    ) {
        val system = astrolabeSystem ?: return
        if (itemDef.elementValue <= 0 || requestedQuantity <= 0) return

        val maxUsable = system.maxUsableItemQuantity(characterId, itemDef.elementValue)
        val useQty = minOf(requestedQuantity, bagItem.quantity, maxUsable)
        if (useQty <= 0) return

        val amount = itemDef.elementValue * useQty
        val before = system.snapshot(characterId)
        val result = system.addNatalPoint(characterId, amount)
        if (!result.success) return

        val remaining = if (bagItem.quantity <= useQty) null
        else bagItem.copy(quantity = bagItem.quantity - useQty)
        try {
            // Repository commit trước rồi cache mới đổi; tránh cache túi bị mutate khi DB fail.
            inventorySystem.setBagItemsAtomic(
                characterId,
                mapOf(bagIndex to remaining)
            )
        } catch (t: Throwable) {
            runCatching { system.restore(characterId, before) }
                .onFailure { rollbackError ->
                    logger.error(rollbackError) {
                        "Rollback Astrolabe thất bại: characterId=$characterId item=${bagItem.itemId}"
                    }
                }
            throw t
        }

        responseSender.sendReduceBagItem(session, bagIndex, useQty)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, useQty, showMsg = true)
        responseSender.sendSetAttribute(
            session,
            EAttribute.ASTROLABE_POINT,
            result.state.natalPoint,
            true
        )
    }

    /**
     * C:023-016 Vứt tiền ra cảnh
     *
     * Định dạng: +amount(4)
     * Nguồn: Item.lua:3174-3177
     */
    private suspend fun handleDropMoney(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val amount = reader.readIntLE()

        val characterId = session.roleId.toLong()
        if (amount <= 0 || !moneySystem.subtractGold(characterId, amount)) return

        responseSender.sendSubtractMoney(session, amount)

        // sinh tiền trên mặt đất
        val gdIndex = groundItemManager.addItem(session.mapId, EGdThing.MONEY, amount, session.x, session.y, characterId)
        if (gdIndex >= 0) {
            // bản thân mình nhìn thấy
            responseSender.sendDropMoneyToGround(session, amount, session.x, session.y, true)
            // phát rộng cho những người khác
            val broadcastData = buildDropMoneyPacket(amount, session.x, session.y, false)
            worldManager.broadcastToMap(session.mapId, broadcastData, exclude = session)
        }
    }

    /** DVT NFT card insertion via normal item use (C:023-015). */
    private suspend fun handleNftCardItem(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
    ) {
        val collections = collectionSystem ?: return
        val result = collections.putNftCard(characterId, bagItem.itemId)
        if (!result.success) {
            responseSender.sendCollectionNftCardResult(session, 12, false, 0)
            return
        }
        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)
        responseSender.sendBitFlagChanges(session, listOf(result.flagId to true))
        responseSender.sendCollectionNftCardResult(session, 12, true, result.collectionId)
        responseSender.sendCollectionNftShine(session, result.shine)
        logger.info {
            "NFT card cất vào collection: charId=$characterId itemId=${bagItem.itemId} " +
                "collectionId=${result.collectionId} shine=${result.shine}"
        }
    }

    /**
     * DVT EItemUseKind.RoleCount = 218.
     *
     * Item_C.dat carries roleCountIndex + roleCountValue directly. This is also
     * how collection-card items 505xx grant counters 401..559.
     */
    private suspend fun handleRoleCountItem(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
        itemDef: ItemDef,
        requestedQuantity: Int,
    ) {
        val roleCounts = roleCountSystem ?: run {
            logger.error { "RoleCount item chưa nối RoleCountSystem: itemId=${bagItem.itemId}" }
            return
        }
        val index = itemDef.roleCountIndex
        val deltaPerItem = itemDef.roleCountValue
        if (index <= 0 || deltaPerItem == 0 || requestedQuantity <= 0) {
            logger.warn {
                "RoleCount item không hợp lệ: itemId=${bagItem.itemId} index=$index delta=$deltaPerItem qty=$requestedQuantity"
            }
            return
        }

        val useQty = minOf(requestedQuantity, bagItem.quantity)
        if (useQty <= 0) return

        val oldValue = roleCounts.getValue(characterId, index)
        val maxValue = roleCounts.getMaxValue(characterId, index)
        val deltaLong = deltaPerItem.toLong() * useQty.toLong()
        val nextLong = oldValue.toLong() + deltaLong
        if (nextLong !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            logger.warn {
                "RoleCount item tràn số: charId=$characterId itemId=${bagItem.itemId} index=$index old=$oldValue delta=$deltaLong"
            }
            return
        }

        val nextValue = nextLong.toInt()
        roleCounts.setValue(characterId, index, nextValue, maxValue)
        try {
            roleCounts.saveCounts(characterId)
        } catch (e: Exception) {
            roleCounts.setValue(characterId, index, oldValue, maxValue)
            logger.error(e) {
                "Lưu RoleCount item thất bại, rollback: charId=$characterId itemId=${bagItem.itemId} index=$index"
            }
            return
        }

        if (!inventorySystem.removeFromBag(characterId, bagIndex, useQty)) {
            roleCounts.setValue(characterId, index, oldValue, maxValue)
            try {
                roleCounts.saveCounts(characterId)
            } catch (rollbackError: Exception) {
                logger.error(rollbackError) {
                    "Rollback RoleCount sau lỗi trừ item thất bại: charId=$characterId index=$index"
                }
            }
            return
        }

        responseSender.sendReduceBagItem(session, bagIndex, useQty)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, useQty, showMsg = true)
        responseSender.sendRoleCountUpdate(session, mapOf(index to (nextValue to maxValue)))

        val newFlags = collectionSystem?.syncOwnedCardFlags(characterId).orEmpty()
        if (newFlags.isNotEmpty()) {
            responseSender.sendBitFlagChanges(session, newFlags.map { it to true })
        }

        logger.info {
            "Dùng RoleCount item: charId=$characterId itemId=${bagItem.itemId} " +
                "index=$index $oldValue->$nextValue qty=$useQty newCardFlags=$newFlags"
        }
    }

    // ── dùng cho võ tướng họa sách / token ──

    /**
     * DVT EItemUseKind.NpcPicture = 195.
     *
     * Lua Item.UseItem kiểm tra:
     * - không có cùng npcId trong FollowNpc;
     * - Inn.HaveNpc() không có cùng npcId (khách sạn + kho võ tướng);
     * - còn ô FollowNpc trống;
     * rồi mới gửi C:023-015.
     *
     * spare3 là npcId. Cart cố ý không nằm trong duplicate-check của Lua, vì vậy
     * allowDuplicateInCart=true để khớp client DVT.
     */
    private suspend fun handleNpcPictureItem(
        session: GameSession,
        characterId: Long,
        bagIndex: Int,
        bagItem: ThingData,
        itemDef: ItemDef,
    ) {
        val pets = petSystem
        if (pets == null) {
            logger.error { "NpcPicture chưa được nối PetSystem: itemId=${bagItem.itemId}" }
            return
        }

        val npcId = itemDef.spare3
        val npcDef = npcDefs[npcId]
        if (npcId <= 0 || npcDef == null) {
            logger.warn {
                "NpcPicture dữ liệu không hợp lệ: itemId=${bagItem.itemId} spare3=$npcId npcDef=${npcDef != null}"
            }
            return
        }

        // DVT chỉ nhận một võ tướng cho mỗi lần dùng token. Nếu trùng/đầy,
        // addFollowNpc trả null và tuyệt đối không tiêu hao item.
        val added = pets.addFollowNpc(
            characterId = characterId,
            npcId = npcId,
            npcDef = npcDef,
            canGrow = 1,
            allowDuplicateInCart = true,
        ) ?: run {
            if (!pets.hasFreeUnlockedFollowSlot(characterId)) {
                pets.getNextFollowUnlockLevel(characterId)?.let { level ->
                    responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                }
            }
            logger.debug {
                "NpcPicture từ chối (trùng hoặc đầy): charId=$characterId itemId=${bagItem.itemId} npcId=$npcId"
            }
            return
        }

        // Thêm pet trước để validate toàn bộ semantics DVT, rồi tiêu token.
        // Nếu inventory mutation thất bại thì rollback pet vừa tạo.
        if (!inventorySystem.removeFromBag(characterId, bagIndex, 1)) {
            pets.removeFollowNpcByNpcId(characterId, npcId)
            logger.error {
                "NpcPicture rollback: không thể tiêu item charId=$characterId itemId=${bagItem.itemId} npcId=$npcId"
            }
            return
        }

        val (slotIndex, pet) = added
        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)
        responseSender.sendFollowNpcAdd(
            session, session.playerId, slotIndex, npcId, pet.canGrow, showMsg = true
        )
        responseSender.sendFollowNpcFullData(
            session,
            pets.getFollowPets(characterId).values.sortedBy { it.slotIndex }
        )
        collectionSystem?.markWarriorOwned(characterId, npcId, session)

        logger.info {
            "${session.characterName} dùng NpcPicture: itemId=${bagItem.itemId} npcId=$npcId → followSlot=$slotIndex"
        }
    }

    // ── dùng cho bộ sưu tập vật cưỡi ──

    /**
     * Dùng sổ tranh vật cưỡi để mở khoá sưu tập vật cưỡi
     *
     * Đối chiếu Item.lua L2311-2316:
     * `specialAbility == EItemUseKind.Mounts`, `spare3` = ID NPC vật cưỡi.
     * Sau khi dùng đặt BitFlag → S:081-001 → client `Mounts.OnGetCollection(flagId)`.
     */
    private suspend fun handleMountCollectionItem(
        session: GameSession, characterId: Long,
        bagIndex: Int, bagItem: ThingData, itemDef: ItemDef
    ) {
        val npcId = itemDef.spare3
        val flagId = mountSystem.useCollectionItem(characterId, npcId) ?: return

        // tiêu hao vật phẩm
        inventorySystem.removeFromBag(characterId, bagIndex, 1)
        responseSender.sendReduceBagItem(session, bagIndex, 1)
        responseSender.sendUseItemSuccess(session, bagItem.itemId, 1, showMsg = true)

        // S:081-001 cập nhật cờ vĩnh viễn → kích hoạt client Mounts.OnGetCollection
        responseSender.sendBitFlagChanges(session, listOf(flagId to true))

        // DVT NFT mount uses the same physical mount ticket / permanent mount flag.
        val nft = collectionSystem?.findNftByGetId(bagItem.itemId, 2)
        if (nft != null) {
            val nftResult = collectionSystem.onNftMountPut(characterId, npcId)
            if (nftResult.success) {
                responseSender.sendCollectionNftMountResult(session, 14, true, npcId)
                responseSender.sendCollectionNftShine(session, nftResult.shine)
            }
        }

        dailyProgressSystem?.incrementRoleCount(
            session,
            DailyProgressSystem.MOUNTS,
        )

        // nếu tự động chọn vật cưỡi này, thông báo client
        val mountData = mountSystem.getCache(characterId)
        if (mountData != null && mountData.currentNpcId == npcId) {
            responseSender.sendMountSwitch(session, npcId)
        }
    }

    // ── tính lại cộng hưởng trang bị ──

    /**
     * Tính lại cộng hưởng của toàn bộ trang bị và thông báo client
     *
     * Được gọi tập trung sau khi mặc/tháo/vứt trang bị.
     * Tham khảo logic của tskero `TSCharacter.refreshBonus()`.
     */
    private fun recalcAndSendEquipBonus(session: GameSession, characterId: Long) {
        val allEquips = inventorySystem.getEquipment(characterId)
        val bonus = EquipBonusCalculator.calculateTotalBonus(allEquips, itemDefs, sacredEquipPrimaryDefs)
        val collectionHpx = collectionSystem?.getPlayerCollectionAttribute(characterId, EAttribute.HPX) ?: 0
        val collectionSpx = collectionSystem?.getPlayerCollectionAttribute(characterId, EAttribute.SPX) ?: 0
        val result = playerStateManager.updateEquipBonus(
            characterId, bonus, statCalc, collectionHpx, collectionSpx
        ) ?: return

        responseSender.sendEquipBonusUpdate(
            session, bonus,
            maxHp = result.newMaxHp, maxSp = result.newMaxSp,
            hp = result.newHp, sp = result.newSp
        )
    }

    // ── Phù dịch chuyển ghi nhớ ──

    /**
     * C:023-073 Phù dịch chuyển ghi nhớ
     *
     * Định dạng: +kind(1B) +index(1B)
     * kind=1 sử dụng (luồng bình thường do hệ thống UISell kích hoạt, ở đây là đường dự phòng)
     * kind=2 ghi nhớ vị trí hiện tại
     *
     * Nguồn: UITeleport.lua:623-628
     */
    private suspend fun handleFlyPoint(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val kind = reader.readUByte()
        val index = reader.readUByte()

        val charId = session.roleId.toLong()

        when (kind) {
            2 -> {
                // ghi nhớ vị trí hiện tại
                if (session.mapId <= 0) return

                val extendCount = flyPointSystem.getExtendCount(charId)
                if (!flyPointSystem.memorize(charId, index, session.mapId, session.x, session.y, extendCount)) {
                    return
                }

                // gửi xuống lại đầy đủ dữ liệu phù dịch chuyển ghi nhớ
                val points = flyPointSystem.loadAll(charId)
                responseSender.sendFlyPointData(session, extendCount, points)

                logger.info { "${session.characterName} dịch chuyển theo điểm nhớ: index=$index → map=${session.mapId} (${session.x}, ${session.y})" }
            }
            1 -> {
                // sử dụng dịch chuyển (đường dự phòng, bình thường do UISell mainKind=84 xử lý)
                val points = flyPointSystem.loadAll(charId)
                val point = points[index]
                if (point == null) {
                    logger.warn { "${session.characterName} dịch chuyển điểm nhớ thất bại: index=$index không tồn tại" }
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

                logger.info { "${session.characterName} dịch chuyển theo điểm nhớ: index=$index → scene=${point.sceneId} (${point.x}, ${point.y})" }
            }
            else -> logger.warn { "Dịch chuyển điểm nhớ chưa xử lý kind=$kind" }
        }
    }

    // ── hàm phụ trợ: ghép gói tin phát rộng ──

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
            .writeLongLE(0L)  // canPickPlayerId = 0 (ai cũng nhặt được)
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

package tw.tsm.app

import tw.tsm.app.response.*
import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SparSlot
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.FriendDataDto
import tw.tsm.domain.interfaces.FriendInviteDto
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.LeaderboardEntryDto
import tw.tsm.domain.interfaces.MountAttribute
import tw.tsm.domain.interfaces.PlayerInfoDto
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.interfaces.repositories.FriendRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.MountData
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.gamelogic.systems.WorldManager

/**
 * Hiện thực tuần tự hóa gói tin ResponseSender (Mẫu ủy quyền - Delegation Pattern)
 *
 * Ủy quyền logic tuần tự hóa thực tế cho các sub-module được nhóm theo mainKind,
 * lớp này chỉ thực hiện cài đặt giao diện và chuyển tiếp phương thức.
 *
 * Sub-module:
 * - [LoginResponseSender] — mainKind=0,1,3,5,9,10,40,85
 * - [ChatResponseSender] — mainKind=2,6
 * - [SceneResponseSender] — mainKind=7,12,22
 * - [NpcEventResponseSender] — mainKind=20,24,81
 * - [InventoryResponseSender] — mainKind=23,25,26,27,30
 * - [BattleResponseSender] — mainKind=8,11,33,50,51,52,53
 * - [PartyPetResponseSender] — mainKind=13,15,19
 * - [MountResponseSender] — mainKind=79
 * - [MacroBoxResponseSender] — mainKind=65
 */
class ResponseSenderImpl(worldManager: WorldManager) : ResponseSender {

    private val login = LoginResponseSender()
    private val chat = ChatResponseSender(worldManager)
    private val scene = SceneResponseSender()
    private val npcEvent = NpcEventResponseSender()
    private val inventory = InventoryResponseSender()
    private val battle = BattleResponseSender()
    private val partyPet = PartyPetResponseSender()
    private val mount = MountResponseSender()
    private val inn = InnResponseSender()
    private val macroBox = MacroBoxResponseSender()
    private val achievement = AchievementResponseSender()
    private val uiSell = UISellResponseSender()

    // ── Đăng nhập/Nhân vật (LoginResponseSender) ──

    override fun sendLoginResult(
        session: SessionSender, resultCode: Int, dbId: Int, gsId: Int,
        playerId: Long, serverTime: Double, account: String, password: String, accountKind: Int
    ) = login.sendLoginResult(session, resultCode, dbId, gsId, playerId, serverTime, account, password, accountKind)

    override fun sendLoginSuccess(session: SessionSender, needChangePassword: Boolean) =
        login.sendLoginSuccess(session, needChangePassword)

    override fun sendLoginFinished(session: SessionSender, connectCode: Long) =
        login.sendLoginFinished(session, connectCode)

    override fun sendPlayerData(session: SessionSender, data: PlayerDataDto) =
        login.sendPlayerData(session, data)

    override fun sendPillCounts(session: SessionSender, attrPill: Int, hpPill: Int, spPill: Int, skillPill: Int) =
        login.sendPillCounts(session, attrPill, hpPill, spPill, skillPill)

    override fun sendLoginDay(session: SessionSender, loginDay: Int, continueLogin: Int) =
        login.sendLoginDay(session, loginDay, continueLogin)

    override fun sendOnlineTime(session: SessionSender, time: Double) =
        login.sendOnlineTime(session, time)

    override fun sendHalfMonthCardTime(session: SessionSender, time: Double) =
        login.sendHalfMonthCardTime(session, time)

    override fun sendPlayerAppear(session: SessionSender, playerId: Long, record: CharacterRecord, serverId: Int) =
        login.sendPlayerAppear(session, playerId, record, serverId)

    override fun sendCreateCharSuccess(session: SessionSender) =
        login.sendCreateCharSuccess(session)

    override fun sendCheckNameResult(session: SessionSender, result: Int) =
        login.sendCheckNameResult(session, result)

    override fun sendKeepalive(session: SessionSender) =
        login.sendKeepalive(session)

    override fun sendDisconnect(session: SessionSender, reason: Int) =
        login.sendDisconnect(session, reason)

    override fun sendOtherPlayerAppear(target: SessionSender, other: GameSession) =
        login.sendOtherPlayerAppear(target, other)

    override fun sendTitleChange(target: SessionSender, roleId: Long, titleId: Int) =
        login.sendTitleChange(target, roleId, titleId)

    override fun sendRoleCountData(session: SessionSender, counts: Map<Int, Int>) =
        login.sendRoleCountData(session, counts)

    override fun sendHotkeyData(session: SessionSender, mainKeys: List<Int>, subKeys: List<Int>) =
        login.sendHotkeyData(session, mainKeys, subKeys)

    override fun sendHotkeySetResult(session: SessionSender, result: Int,
                                     dataIndex: Int, pageIndex: Int,
                                     keyIndex: Int, useId: Int) =
        login.sendHotkeySetResult(session, result, dataIndex, pageIndex, keyIndex, useId)

    // ── Trò chuyện/Di chuyển (ChatResponseSender) ──

    override fun sendPlayerMove(sender: GameSession, playerId: Long, direction: Int, x: Int, y: Int) =
        chat.sendPlayerMove(sender, playerId, direction, x, y)

    override fun sendChatMessage(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String
    ) = chat.sendChatMessage(sender, channel, senderId, senderName, content)

    override fun sendChatMessageToMap(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String, mapId: Int
    ) = chat.sendChatMessageToMap(sender, channel, senderId, senderName, content, mapId)

    override fun sendWhisperMessage(
        target: SessionSender, senderId: Long, senderName: String, content: String,
        titleId: Int
    ) = chat.sendWhisperMessage(target, senderId, senderName, content, titleId)

    override fun sendSystemMessage(session: SessionSender, content: String) =
        chat.sendSystemMessage(session, content)

    // ── Cảnh/Bản đồ (SceneResponseSender) ──

    override fun sendAreaList(session: SessionSender, onlineCount: Int) =
        scene.sendAreaList(session, onlineCount)

    override fun sendAreaSwitchResult(session: SessionSender, resultCode: Int) =
        scene.sendAreaSwitchResult(session, resultCode)

    override fun sendPlayerTeleport(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int) =
        scene.sendPlayerTeleport(session, playerId, sceneId, x, y)

    override fun sendSceneChange(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int, equipItemIds: IntArray, titleId: Int) =
        scene.sendSceneChange(session, playerId, sceneId, x, y, equipItemIds, titleId)

    override fun sendMapNpcInit(session: SessionSender, npcs: List<EveNpcPlacement>) =
        scene.sendMapNpcInit(session, npcs)

    override fun buildMapNpcMovePacket(npcIndex: Int, x: Int, y: Int): ByteArray =
        scene.buildMapNpcMovePacket(npcIndex, x, y)

    // ── Sự kiện NPC/Nhiệm vụ/Cờ (NpcEventResponseSender) ──

    override fun sendEventChangeScene(session: SessionSender) =
        npcEvent.sendEventChangeScene(session)

    override fun sendCommonEvent(session: SessionSender, result: EveResult) =
        npcEvent.sendCommonEvent(session, result)

    override fun sendEventEnd(session: SessionSender, endKind: Int) =
        npcEvent.sendEventEnd(session, endKind)

    override fun sendOpenPlayerReincarnation(session: SessionSender) =
        npcEvent.sendOpenPlayerReincarnation(session)

    override fun sendOpenKillPointShop(session: SessionSender) =
        npcEvent.sendOpenKillPointShop(session)

    override fun sendOpenWaterWarOrgManage(session: SessionSender) =
        npcEvent.sendOpenWaterWarOrgManage(session)

    override fun sendOpenNpcSecondReincarnation(session: SessionSender) =
        npcEvent.sendOpenNpcSecondReincarnation(session)

    override fun sendOpenNpcReincarnation(session: SessionSender) =
        npcEvent.sendOpenNpcReincarnation(session)

    override fun sendPetRebornMessage(session: SessionSender, msgKind: Int) =
        partyPet.sendPetRebornMessage(session, msgKind)

    override fun sendBadoErrorMessage(session: SessionSender) =
        npcEvent.sendBadoErrorMessage(session)

    override fun sendBadoStartMessage(session: SessionSender) =
        npcEvent.sendBadoStartMessage(session)

    override fun sendOpenGalaxyLeaderboard(session: SessionSender) =
        npcEvent.sendOpenGalaxyLeaderboard(session)

    override fun sendChristmasEventMessage(session: SessionSender) =
        npcEvent.sendChristmasEventMessage(session)

    override fun sendArenaErrorMessage(session: SessionSender) =
        npcEvent.sendArenaErrorMessage(session)

    override fun sendLanternErrorMessage(session: SessionSender) =
        npcEvent.sendLanternErrorMessage(session)

    override fun sendUnknownActivityMessage(session: SessionSender) =
        npcEvent.sendUnknownActivityMessage(session)

    override fun sendAddMissionStep(session: SessionSender, missionId: Int, step: Int) =
        npcEvent.sendAddMissionStep(session, missionId, step)

    override fun sendDecMissionStep(session: SessionSender, missionId: Int, step: Int) =
        npcEvent.sendDecMissionStep(session, missionId, step)

    override fun sendRemoveMission(session: SessionSender, missionId: Int) =
        npcEvent.sendRemoveMission(session, missionId)

    override fun sendInitMissionSteps(session: SessionSender, missions: List<MissionRecord>) =
        npcEvent.sendInitMissionSteps(session, missions)

    override fun sendInitMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>) =
        npcEvent.sendInitMissionFlags(session, flags)

    override fun sendSetMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>) =
        npcEvent.sendSetMissionFlags(session, flags)

    override fun sendInitBitFlags(session: SessionSender, packedFlags: ByteArray) =
        npcEvent.sendInitBitFlags(session, packedFlags)

    override fun sendBitFlagChanges(session: SessionSender, changes: List<Pair<Int, Boolean>>) =
        npcEvent.sendBitFlagChanges(session, changes)

    // ── Vật phẩm/Trang bị/Tiền vàng/Giao dịch/Ngân hàng (InventoryResponseSender) ──

    override fun sendAllBagItems(session: SessionSender, items: Map<Int, ThingData>, refresh: Boolean) =
        inventory.sendAllBagItems(session, items, refresh)

    override fun sendSetBagItem(session: SessionSender, slot: Int, thing: ThingData, showMsg: Boolean) =
        inventory.sendSetBagItem(session, slot, thing, showMsg)

    override fun sendReduceBagItem(session: SessionSender, slot: Int, amount: Int) =
        inventory.sendReduceBagItem(session, slot, amount)

    override fun sendMoveBagItem(session: SessionSender, fromSlot: Int, amount: Int, toSlot: Int) =
        inventory.sendMoveBagItem(session, fromSlot, amount, toSlot)

    override fun sendAllEquipment(session: SessionSender, equips: Map<Int, ThingData>) =
        inventory.sendAllEquipment(session, equips)

    override fun sendDeleteEquip(session: SessionSender, fitType: Int) =
        inventory.sendDeleteEquip(session, fitType)

    override fun sendUseItemSuccess(session: SessionSender, itemId: Int, count: Int, showMsg: Boolean) =
        inventory.sendUseItemSuccess(session, itemId, count, showMsg)

    override fun sendUnequip(session: SessionSender, fitType: Int, bagSlot: Int) =
        inventory.sendUnequip(session, fitType, bagSlot)

    override fun sendEquip(session: SessionSender, bagSlot: Int) =
        inventory.sendEquip(session, bagSlot)

    override fun sendPetEquipDelete(session: SessionSender, followSlot: Int, fitType: Int) =
        inventory.sendPetEquipDelete(session, followSlot, fitType)

    override fun sendPetUnequipToBag(session: SessionSender, followSlot: Int, fitType: Int, bagSlot: Int) =
        inventory.sendPetUnequipToBag(session, followSlot, fitType, bagSlot)

    override fun sendPetEquip(session: SessionSender, followSlot: Int, bagSlot: Int) =
        inventory.sendPetEquip(session, followSlot, bagSlot)

    override fun sendPickupResult(session: SessionSender, result: Int, kind: Int, value: Int, x: Int, y: Int) =
        inventory.sendPickupResult(session, result, kind, value, x, y)

    override fun sendAddGroundItem(session: SessionSender, itemId: Int, x: Int, y: Int, isMe: Int, canPickPlayerId: Long) =
        inventory.sendAddGroundItem(session, itemId, x, y, isMe, canPickPlayerId)

    override fun sendRemoveGroundItem(session: SessionSender, index: Int, isMe: Boolean) =
        inventory.sendRemoveGroundItem(session, index, isMe)

    override fun sendDropMoneyToGround(session: SessionSender, amount: Int, x: Int, y: Int, isMe: Boolean) =
        inventory.sendDropMoneyToGround(session, amount, x, y, isMe)

    override fun sendInitMoney(session: SessionSender, gold: Int, auctionGold: Int) =
        inventory.sendInitMoney(session, gold, auctionGold)

    override fun sendAddMoney(session: SessionSender, amount: Int) =
        inventory.sendAddMoney(session, amount)

    override fun sendSubtractMoney(session: SessionSender, amount: Int) =
        inventory.sendSubtractMoney(session, amount)

    override fun sendMoneyFull(session: SessionSender, kind: Int) =
        inventory.sendMoneyFull(session, kind)

    override fun sendBuyResult(session: SessionSender, result: Int) =
        inventory.sendBuyResult(session, result)

    override fun sendSellResult(session: SessionSender, result: Int, bagIndices: List<Int>) =
        inventory.sendSellResult(session, result, bagIndices)

    override fun sendOpenNpcStore(session: SessionSender, kind: Int) =
        inventory.sendOpenNpcStore(session, kind)

    override fun sendFlyPointData(session: SessionSender, flyPointCount: Int, points: Map<Int, FlyPointRecord>) =
        inventory.sendFlyPointData(session, flyPointCount, points)

    // ── Tiêu thụ giao diện (UISellResponseSender) ──

    override fun sendUiSellFail(session: SessionSender, subKind: Int, sellId: Int, reason: Int, value: Long) =
        uiSell.sendUiSellFail(session, subKind, sellId, reason, value)

    override fun sendUiSellMoneyQuery(session: SessionSender, sellId: Int, currencyKind: Int, cost: Long) =
        uiSell.sendUiSellMoneyQuery(session, sellId, currencyKind, cost)

    override fun sendUiSellItemQuery(session: SessionSender, sellId: Int, itemId: Int, count: Int) =
        uiSell.sendUiSellItemQuery(session, sellId, itemId, count)

    override fun sendUiSellFreeQuery(session: SessionSender, sellId: Int) =
        uiSell.sendUiSellFreeQuery(session, sellId)

    override fun sendUiSellSuccess(session: SessionSender, sellId: Int) =
        uiSell.sendUiSellSuccess(session, sellId)

    override fun sendPointBalance(session: SessionSender, pointReal: Int, pointVirtual: Int) =
        uiSell.sendPointBalance(session, pointReal, pointVirtual)

    override fun sendBankAllItems(session: SessionSender, items: Map<Int, ThingData>) =
        inventory.sendBankAllItems(session, items)

    override fun sendBankSetItem(session: SessionSender, slot: Int, thing: ThingData) =
        inventory.sendBankSetItem(session, slot, thing)

    override fun sendBankReduceItem(session: SessionSender, slot: Int, amount: Int) =
        inventory.sendBankReduceItem(session, slot, amount)

    override fun sendBankFail(session: SessionSender, failCode: Int) =
        inventory.sendBankFail(session, failCode)

    override fun sendBankClear(session: SessionSender) =
        inventory.sendBankClear(session)

    override fun sendOpenBank(session: SessionSender) =
        inventory.sendOpenBank(session)

    override fun sendBankLevel(session: SessionSender, level: Int) =
        inventory.sendBankLevel(session, level)

    // ── Quán trọ/Kho võ tướng (InnResponseSender) ──

    override fun sendInnRestConfirm(session: SessionSender, cost: Int) =
        inn.sendInnRestConfirm(session, cost)

    override fun sendOpenInn(session: SessionSender, innLevel: Int, innPets: Map<Int, FollowNpcData>) {
        inn.sendInnLevel(session, innLevel)
        if (innPets.isNotEmpty()) {
            inn.sendInnNpcData(session, innPets)
        }
        inn.sendOpenWarriorRepository(session)
    }

    override fun sendOpenWarehouse(session: SessionSender, warehousePets: List<FollowNpcData>) {
        inn.sendWarehouseData(session, warehousePets)
    }

    override fun sendStorageAllItems(session: SessionSender, items: Map<Int, ThingData>) =
        inventory.sendStorageAllItems(session, items)

    override fun sendStorageSetItem(session: SessionSender, slot: Int, thing: ThingData) =
        inventory.sendStorageSetItem(session, slot, thing)

    override fun sendStorageReduceItem(session: SessionSender, slot: Int, amount: Int) =
        inventory.sendStorageReduceItem(session, slot, amount)

    override fun sendStorageFail(session: SessionSender, failCode: Int) =
        inventory.sendStorageFail(session, failCode)

    override fun sendStorageClear(session: SessionSender) =
        inventory.sendStorageClear(session)

    override fun sendTradeStart(session: SessionSender, partnerId: Long) =
        inventory.sendTradeStart(session, partnerId)

    override fun sendTradeCancel(session: SessionSender, reason: Int) =
        inventory.sendTradeCancel(session, reason)

    override fun sendTradeConfirm(session: SessionSender, money: Int, items: List<ThingData>) =
        inventory.sendTradeConfirm(session, money, items)

    override fun sendPetTradeStart(session: SessionSender, partnerId: Long) =
        inventory.sendPetTradeStart(session, partnerId)

    override fun sendPetTradeCancel(session: SessionSender, reason: Int) =
        inventory.sendPetTradeCancel(session, reason)

    override fun sendPetTradeConfirm(session: SessionSender, money: Int, pet: FollowNpcData?, maxHp: Int, maxSp: Int) =
        inventory.sendPetTradeConfirm(session, money, pet, maxHp, maxSp)

    // ── Chiến đấu/Thuộc tính/PK (BattleResponseSender) ──

    override fun sendSetAttribute(session: SessionSender, attrKind: Int, value: Int, isPositive: Boolean, arg: Int) =
        battle.sendSetAttribute(session, attrKind, value, isPositive, arg)

    override fun sendFollowNpcAttribute(session: SessionSender, slot: Int, attrKind: Int, value: Int, arg: Int) =
        battle.sendFollowNpcAttribute(session, slot, attrKind, value, arg)

    override fun sendEquipBonusUpdate(session: SessionSender, bonus: EquipBonusData, maxHp: Long, maxSp: Int, hp: Long, sp: Int) =
        battle.sendEquipBonusUpdate(session, bonus, maxHp, maxSp, hp, sp)

    override fun sendPlayerSkillData(session: SessionSender, element: Int, skillPoint: Int, skills: Map<Int, Int>) =
        battle.sendPlayerSkillData(session, element, skillPoint, skills)

    override fun sendTurn2Balls(session: SessionSender, available: Int, active: Set<Int>) =
        battle.sendTurn2Balls(session, available, active)

    override fun sendTurn2HeroSkills(session: SessionSender, heroSkills: Map<Int, Int>) =
        battle.sendTurn2HeroSkills(session, heroSkills)

    override fun sendBattleInit(session: SessionSender, terrain: Int, eventNo: Int, roleData: ByteArray) =
        battle.sendBattleInit(session, terrain, eventNo, roleData)

    override fun sendSceneWarTypeUpdate(session: SessionSender, entries: ByteArray) =
        battle.sendSceneWarTypeUpdate(session, entries)

    override fun sendBattleJoinRoleAppear(session: SessionSender, roleData: ByteArray) =
        battle.sendBattleJoinRoleAppear(session, roleData)

    override fun sendBattleCountdown(session: SessionSender) =
        battle.sendBattleCountdown(session)

    override fun sendBattleSkillResult(session: SessionSender, data: ByteArray) =
        battle.sendBattleSkillResult(session, data)

    override fun sendBattleAttrChange(session: SessionSender, data: ByteArray) =
        battle.sendBattleAttrChange(session, data)

    override fun sendBattleEnd(session: SessionSender, playerId: Long, npcIndex: Int) =
        battle.sendBattleEnd(session, playerId, npcIndex)

    override fun sendBattleExitFight(session: SessionSender, col: Int, row: Int) =
        battle.sendBattleExitFight(session, col, row)

    override fun sendBattleRestoreStatus(session: SessionSender, data: ByteArray) =
        battle.sendBattleRestoreStatus(session, data)

    override fun sendBattleFlyOut(session: SessionSender, col: Int, row: Int) =
        battle.sendBattleFlyOut(session, col, row)

    override fun sendBattleUseItem(session: SessionSender, col: Int, row: Int, itemId: Int, count: Int, tgtCol: Int, tgtRow: Int) =
        battle.sendBattleUseItem(session, col, row, itemId, count, tgtCol, tgtRow)

    override fun sendBattleFieldType(session: SessionSender, warStyle: Int, rounds: Int, limitType: Int, limitSwitch: Int) =
        battle.sendBattleFieldType(session, warStyle, rounds, limitType, limitSwitch)

    override fun sendBattleError(session: SessionSender, errorKind: Int) =
        battle.sendBattleError(session, errorKind)

    override fun sendBattleDrop(session: SessionSender, itemId: Int, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) =
        battle.sendBattleDrop(session, itemId, fromCol, fromRow, toCol, toRow)

    override fun sendPkResult(session: SessionSender, resultCode: Int) =
        battle.sendPkResult(session, resultCode)

    override fun sendPkSwitch(session: SessionSender, pkEnabled: Boolean, joinEnabled: Boolean) =
        battle.sendPkSwitch(session, pkEnabled, joinEnabled)

    // ── Đội ngũ/Võ tướng (PartyPetResponseSender) ──

    override fun sendPartyJoinRequest(session: SessionSender, requesterId: Long) =
        partyPet.sendPartyJoinRequest(session, requesterId)

    override fun sendPartyJoinResult(session: SessionSender, result: Int, leaderName: String) =
        partyPet.sendPartyJoinResult(session, result, leaderName)

    override fun sendPartyLeave(session: SessionSender, playerId: Long, x: Int, y: Int) =
        partyPet.sendPartyLeave(session, playerId, x, y)

    override fun sendPartyMemberJoin(session: SessionSender, leaderId: Long, newMemberId: Long) =
        partyPet.sendPartyMemberJoin(session, leaderId, newMemberId)

    override fun sendPartyData(session: SessionSender, parties: Map<Long, List<Long>>) =
        partyPet.sendPartyData(session, parties)

    override fun sendPartySetAdviser(session: SessionSender, adviserId: Long) =
        partyPet.sendPartySetAdviser(session, adviserId)

    override fun sendPartyRemoveAdviser(session: SessionSender) =
        partyPet.sendPartyRemoveAdviser(session)

    override fun sendPartyInvite(session: SessionSender, inviterId: Long) =
        partyPet.sendPartyInvite(session, inviterId)

    override fun sendPartyInviteResult(session: SessionSender, result: Int, targetName: String) =
        partyPet.sendPartyInviteResult(session, result, targetName)

    override fun sendPartyAdviserData(session: SessionSender, adviserIds: List<Long>) =
        partyPet.sendPartyAdviserData(session, adviserIds)

    override fun sendPartyMessage(session: SessionSender, kind: Int) =
        partyPet.sendPartyMessage(session, kind)

    override fun sendPartyChangeLeader(session: SessionSender, newLeaderId: Long, memberIds: List<Long>) =
        partyPet.sendPartyChangeLeader(session, newLeaderId, memberIds)

    override fun sendPartyMoveStatus(session: SessionSender, playerId: Long, status: Int) =
        partyPet.sendPartyMoveStatus(session, playerId, status)

    override fun sendFollowNpcFullData(session: SessionSender, pets: List<FollowNpcData>) =
        partyPet.sendFollowNpcFullData(session, pets)

    override fun sendFollowNpcAdd(session: SessionSender, playerId: Long, slotIndex: Int, npcId: Int, canGrow: Int, showMsg: Boolean) =
        partyPet.sendFollowNpcAdd(session, playerId, slotIndex, npcId, canGrow, showMsg)

    override fun sendFollowNpcRemove(session: SessionSender, playerId: Long, slotIndex: Int, showMsg: Boolean) =
        partyPet.sendFollowNpcRemove(session, playerId, slotIndex, showMsg)

    override fun sendCartData(session: SessionSender, pets: List<FollowNpcData>) =
        partyPet.sendCartData(session, pets)

    override fun sendCartRemove(session: SessionSender, slotIndex: Int) =
        partyPet.sendCartRemove(session, slotIndex)

    override fun sendFollowNpcDeploy(session: SessionSender, npcId: Int) =
        partyPet.sendFollowNpcDeploy(session, npcId)

    override fun sendFollowNpcRecall(session: SessionSender) =
        partyPet.sendFollowNpcRecall(session)

    override fun sendPetTempUpgradeData(session: SessionSender, kind: Int, npcId: Int, gains: IntArray) =
        partyPet.sendPetTempUpgradeData(session, kind, npcId, gains)

    override fun sendPetUpgradeLv(session: SessionSender, followIndex: Int, upgradeLv: Int) =
        partyPet.sendPetUpgradeLv(session, followIndex, upgradeLv)

    override fun sendPetUpgradeResult(session: SessionSender, success: Boolean) =
        partyPet.sendPetUpgradeResult(session, success)

    // ── Thú cưỡi (MountResponseSender) ──

    override fun sendMountData(session: SessionSender, data: MountData) =
        mount.sendMountData(session, data)

    override fun sendMountLevel(session: SessionSender, level: Int) =
        mount.sendMountLevel(session, level)

    override fun sendMountAttributePoint(session: SessionSender, kind: Int, points: Int) =
        mount.sendMountAttributePoint(session, kind, points)

    override fun sendMountEquip(session: SessionSender, bagIndex: Int) =
        mount.sendMountEquip(session, bagIndex)

    override fun sendMountUnequip(session: SessionSender, fitType: Int, bagIndex: Int) =
        mount.sendMountUnequip(session, fitType, bagIndex)

    override fun sendMountSwitch(session: SessionSender, npcId: Int) =
        mount.sendMountSwitch(session, npcId)

    override fun sendPlayerMount(session: SessionSender, playerId: Long, npcId: Int) =
        mount.sendPlayerMount(session, playerId, npcId)

    override fun sendMountAttributes(session: SessionSender, attrs: List<MountAttribute>) =
        mount.sendMountAttributes(session, attrs)

    // ── Hộp cơ quan (MacroBoxResponseSender) ──

    override fun sendMacroBoxStart(session: SessionSender) =
        macroBox.sendMacroBoxStart(session)

    override fun sendMacroBoxPause(session: SessionSender) =
        macroBox.sendMacroBoxPause(session)

    override fun sendMiniPetAppear(session: SessionSender, roleId: Long, kind: Int) =
        macroBox.sendMiniPetAppear(session, roleId, kind)

    override fun sendMiniPetList(session: SessionSender, pets: Map<Long, Int>) =
        macroBox.sendMiniPetList(session, pets)

    override fun sendMacroBoxOpen(session: SessionSender) =
        macroBox.sendMacroBoxOpen(session)

    override fun sendMacroBoxSparData(session: SessionSender, sparSlots: Map<Int, SparSlot>) =
        macroBox.sendMacroBoxSparData(session, sparSlots)

    override fun sendMacroBoxMessage(session: SessionSender, kind: Int) =
        macroBox.sendMacroBoxMessage(session, kind)

    override fun sendMacroBoxSparExtend(session: SessionSender, count: Int, showMsg: Boolean) =
        macroBox.sendMacroBoxSparExtend(session, count, showMsg)

    override fun sendMacroBoxSparRemoveMsg(session: SessionSender) =
        macroBox.sendMacroBoxSparRemoveMsg(session)

    override fun sendMacroBoxSupply(session: SessionSender, checkItem: Boolean) =
        macroBox.sendMacroBoxSupply(session, checkItem)

    override fun sendMacroBoxExpBottleData(session: SessionSender, priority: Int, bottles: Map<Int, ExpBottleSlot>) =
        macroBox.sendMacroBoxExpBottleData(session, priority, bottles)

    override fun sendMacroBoxUpdateExpBottle(session: SessionSender, index: Int, exp: Int) =
        macroBox.sendMacroBoxUpdateExpBottle(session, index, exp)

    override fun sendMacroBoxSetExpBottle(session: SessionSender, index: Int) =
        macroBox.sendMacroBoxSetExpBottle(session, index)

    override fun sendMacroBoxExpExtend(session: SessionSender, count: Int, showMsg: Boolean) =
        macroBox.sendMacroBoxExpExtend(session, count, showMsg)

    // ── Hệ thống thành tựu (AchievementResponseSender) ──

    override fun sendAchievementComplete(session: SessionSender, result: Int, achievementId: Int) =
        achievement.sendAchievementComplete(session, result, achievementId)

    override fun sendAchievementReward(session: SessionSender, result: Int, achievementId: Int) =
        achievement.sendAchievementReward(session, result, achievementId)

    override fun sendRoleCountUpdate(session: SessionSender, counts: Map<Int, Pair<Int, Int>>) =
        achievement.sendRoleCountUpdate(session, counts)

    // ── Hệ thống thư tín (MailResponseSender) ──

    private val mail = MailResponseSender()

    override fun sendMails(session: SessionSender, mails: List<MailRecord>) =
        mail.sendMails(session, mails)

    override fun sendMailTakeConfirm(session: SessionSender, mailIds: List<Int>) =
        mail.sendMailTakeConfirm(session, mailIds)

    override fun sendMailDeleteConfirm(session: SessionSender, mailIds: List<Int>) =
        mail.sendMailDeleteConfirm(session, mailIds)

    override fun sendMailReadConfirm(session: SessionSender, mailIds: List<Int>) =
        mail.sendMailReadConfirm(session, mailIds)

    override fun sendMailError(session: SessionSender, errorKind: Int) =
        mail.sendMailError(session, errorKind)

    // ── Hệ thống bảng xếp hạng (LeaderboardResponseSender) ──

    private val leaderboard = LeaderboardResponseSender()

    override fun sendLeaderboardData(
        session: SessionSender,
        leaderboardId: Int, page: Int, maxPage: Int, myRank: Int,
        myScoreParams: List<Int>,
        entries: List<LeaderboardEntryDto>
    ) = leaderboard.sendLeaderboardData(session, leaderboardId, page, maxPage, myRank, myScoreParams, entries)

    // ── Hệ thống bạn bè (FriendResponseSender) ──

    private val friend = FriendResponseSender()

    override fun sendFriendInviteResult(session: SessionSender, name: String, result: Int) =
        friend.sendFriendInviteResult(session, name, result)

    override fun sendFriendRemove(session: SessionSender, playerId: Long) =
        friend.sendFriendRemove(session, playerId)

    override fun sendFriendAdd(session: SessionSender, friends: List<FriendDataDto>) =
        friend.sendFriendAdd(session, friends)

    override fun sendFriendMailFull(session: SessionSender) =
        friend.sendFriendMailFull(session)

    override fun sendFriendMessage(session: SessionSender, kind: Int) =
        friend.sendFriendMessage(session, kind)

    override fun sendFriendInvites(session: SessionSender, invites: List<FriendInviteDto>) =
        friend.sendFriendInvites(session, invites)

    override fun sendFriendRemoveInvite(session: SessionSender, playerId: Long) =
        friend.sendFriendRemoveInvite(session, playerId)

    override fun sendFriendFunctionFlags(session: SessionSender, updates: List<Pair<Long, Int>>) =
        friend.sendFriendFunctionFlags(session, updates)

    override fun sendFriendFriendly(session: SessionSender, playerId: Long, friendly: Int) =
        friend.sendFriendFriendly(session, playerId, friendly)

    override fun sendPlayerInfo(session: SessionSender, data: PlayerInfoDto) =
        friend.sendPlayerInfo(session, data)

    override fun sendGiftReceiveResult(session: SessionSender, result: Int) =
        friend.sendGiftReceiveResult(session, result)

    override fun sendFriendOnlineStatus(session: SessionSender, playerId: Long, online: Boolean) =
        friend.sendFriendOnlineStatus(session, playerId, online)

    override fun sendMaxRecordFriendCount(session: SessionSender, count: Int) =
        friend.sendMaxRecordFriendCount(session, count)
    override fun sendPlayerCareerChange(
        session: SessionSender, playerId: Long, hair: Int,
        color1: Int, color2: Int, turn: Int, career: Int
    ) = battle.sendPlayerCareerChange(session as GameSession, playerId, hair, color1, color2, turn, career)

}

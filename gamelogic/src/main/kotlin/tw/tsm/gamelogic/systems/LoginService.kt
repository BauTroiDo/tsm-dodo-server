package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.DEFAULT_PORT
import tw.tsm.domain.constants.EAttribute
import tw.tsm.protocol.PacketWriter
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.data.SacredEquipPrimaryDef
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.domain.util.OleDate

private val logger = KotlinLogging.logger {}

/**
 * Dịch vụ điều phối chuỗi đăng nhập
 *
 * Chịu trách nhiệm gửi các gói tin đăng nhập theo trình tự, giúp client hoàn tất khởi tạo.
 * Được chia làm 2 giai đoạn:
 * 1. Giai đoạn đăng nhập (sau khi xác thực tài khoản thành công) → Mở UICreateRole
 * 2. Giai đoạn nhân vật đăng nhập (sau khi chọn nhân vật C:003) → Vào thế giới game
 */
class LoginService(
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager,
    private val statSystem: StatSystem,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val playerCurrencySystem: PlayerCurrencySystem,
    private val groundItemManager: GroundItemManager,
    private val questSystem: QuestSystem,
    private val stateMachine: EveEventStateMachine,
    private val playerStateManager: PlayerStateManager,
    private val bitFlagSystem: BitFlagSystem,
    private val skillSystem: SkillSystem,
    private val petSystem: PetSystem,
    private val mountSystem: MountSystem,
    private val astrolabeSystem: AstrolabeSystem,
    private val soulSystem: SoulSystem,
    private val hotkeyRepository: HotkeyRepository,
    private val skillRepository: SkillRepository,
    private val roleCountSystem: RoleCountSystem,
    private val flyPointSystem: FlyPointSystem,
    private val mailSystem: MailSystem,
    private val friendSystem: FriendSystem,
    private val eveData: Map<Int, SceneEveData> = emptyMap(),
    private val itemDefs: Map<Int, ItemDef> = emptyMap(),
    private val jiugonggeSystem: JiugonggeSystem? = null,
    private val activityScheduleSystem: ActivityScheduleSystem? = null,
    private val dailyProgressSystem: DailyProgressSystem? = null,
    private val collectionSystem: CollectionSystem? = null,
    private val sacredEquipPrimaryDefs: Map<Int, SacredEquipPrimaryDef> = emptyMap(),
    private val messageBoardRepository: MessageBoardRepository? = null,
    private val pokeSystem: PokeSystem? = null,
) {

    /**
     * Giai đoạn 1: Xác thực đăng nhập thành công
     *
     * Gửi S:001-002 + S:001-003, sau khi client nhận được:
     * - Đóng UILogin
     * - Mở UICreateRole (Màn hình chọn/tạo nhân vật)
     */
    fun executeLoginPhase(
        session: SessionSender,
        dbId: Int,
        gsId: Int,
        playerId: Long,
        account: String,
        password: String
    ) {
        val serverTime = currentServerTime()

        // S:001-002 Kết quả đăng nhập (Thành công)
        responseSender.sendLoginResult(
            session = session,
            resultCode = 0,
            dbId = dbId,
            gsId = gsId,
            playerId = playerId,
            serverTime = serverTime,
            account = account,
            password = password,
            accountKind = 0
        )

        // S:001-003 Đăng nhập thành công (Client mở UICreateRole)
        responseSender.sendLoginSuccess(session, needChangePassword = false)

        logger.info { "Giai đoạn đăng nhập hoàn tất: playerId=$playerId" }
    }

    /**
     * Giai đoạn 2: Nhân vật tiến vào thế giới game
     *
     * Được gọi sau khi chọn nhân vật ở C:003, gửi lần lượt:
     * 1. S:003-000 Người chơi xuất hiện (Dữ liệu hình ảnh/Vị trí nhân vật)
     * 2. S:005-003 Gói dữ liệu lớn của người chơi (Thuộc tính/Kỹ năng/Chỉ số cộng thêm từ trang bị)
     * 3. S:005-011 Số lần dùng đan dược
     * 4. S:003-001 Số ngày đăng nhập
     * 5. S:003-002 Thời gian online hằng ngày
     * 6. S:003-003 Thời gian thẻ bán nguyệt (Half-month card)
     * 7. S:001-013 Hoàn tất chuỗi đăng nhập
     *
     * @param session Kết nối game (Cần GameSession để thiết lập tọa độ bản đồ)
     * @param record Dữ liệu nhân vật (Tải từ cơ sở dữ liệu)
     * @param serverId ID Máy chủ
     * @param connectCode Mã kết nối
     */
    suspend fun executeCharacterEnterSequence(
        session: GameSession,
        playerId: Long,
        record: CharacterRecord,
        serverId: Int,
        connectCode: Long
    ) {
        val serverTime = currentServerTime()

        // S:024-006 Khởi tạo nhiệm vụ đang tiến hành + S:024-007 Khởi tạo cờ đánh dấu nhiệm vụ đã hoàn thành
        // ⚠️ Bắt buộc phải gửi trước S:003-000!
        // Bởi vì PlayerAppear sẽ kích hoạt Function.UpdateVisible(),
        // cần MarkManager.flags đã được khởi tạo để có thể hiển thị đúng các nút chức năng
        questSystem.refreshDungeonDaily(record.id)
        questSystem.refreshDungeonWeekly(record.id)
        val missions = questSystem.getMissions(record.id)
        val flags    = questSystem.getFlags(record.id)
        responseSender.sendInitMissionSteps(session, missions)
        responseSender.sendInitMissionFlags(session, flags)

        // 1. S:003-000 Người chơi xuất hiện
        responseSender.sendPlayerAppear(session, playerId, record, serverId, skillRepository.findTurn3State(record.id).element)

        // 2. Tải vật phẩm/trang bị/tiền tệ/nguyên bảo (Phải tải trước mới có thể tính được chỉ số cộng thêm từ trang bị)
        inventorySystem.loadCharacterData(record.id)
        moneySystem.loadMoney(record.id)
        pointSystem.loadPoints(record.id)
        val specialCurrencies = playerCurrencySystem.load(record.id)
        val astrolabeData = astrolabeSystem.load(record.id)
        val astrolabeBonus = astrolabeSystem.bonuses(record.id)

        // 2.5 Tính toán chỉ số cộng thêm từ trang bị (Tham khảo tskero loadEquipmentJson → addEquipBonus)
        val equips = inventorySystem.getEquipment(record.id)
        val equipBonus = EquipBonusCalculator.calculateTotalBonus(equips, itemDefs, sacredEquipPrimaryDefs)

        // S:005-003 Gói dữ liệu lớn của người chơi (Bao gồm chỉ số cộng thêm từ trang bị)
        val turn3State = skillRepository.findTurn3State(record.id)
        session.turn = record.turn
        session.career = record.career
        session.turn3Element = turn3State.element
        session.titleId = record.titleId
        val playerData = PlayerDataDto.fromCharacterRecord(
            record, statSystem, equipBonus, turn3State,
            astrolabePoint = astrolabeData.natalPoint,
            astrolabeBonus = astrolabeBonus
        )
        responseSender.sendPlayerData(session, playerData)
        responseSender.sendAstrolabeLevels(session, isLevelUp = false, astrolabeData.starLevels)

        // S:085-001 Đếm số lượng của nhân vật — Bắt buộc gửi trước vật phẩm trong túi đồ
        // DVT Item.lua: currentThingGrid = 250 + (Bag_1+Bag_2+Bag_3)×5.
        // DVT Item.lua: currentBThingGrid = 250 + (Bank_1+Bank_2+Bank_3)×5.
        // Bảo khố: currentStorageGrid = (Storage_1+Storage_2)×5 (Private server mặc định 14 → 70 ô, đầy kho)
        // Khách sạn: Inn.currentSpace = RoleCount[102] + 6 (Khởi tạo 0 → 6 ô)
        // Kho võ tướng: Inn.currentWarriorRepositorySpace = RoleCount[822] + 5 (Khởi tạo 0 → 5 ô)
        roleCountSystem.loadCounts(record.id)
        // Lưu giá trị mặc định cho Private server: Khi không có bản ghi trong DB thì ghi vào RoleCountSystem (thay vì chỉ gộp vào gói tin),
        // để đảm bảo việc tính toán dung lượng ở server/số lần mua tiêu hao khớp với hiển thị ở client
        val defaultRoleCounts = mapOf(
            RoleCountSystem.COLLECTION_CARD_EQUIP_MAX to 1, // DVT CollectCard mặc định cho trang bị 1 tổ hợp
            RoleCountSystem.BANK_1 to 0,              // Mở rộng tiền trang 1 (Tiêu hao sellId=1)
            RoleCountSystem.INN to 0,                 // Mở rộng thêm khách sạn (Tiêu hao sellId=2)
            RoleCountSystem.BAG_1 to 0,               // Mở rộng túi đồ 1 (Tiêu hao sellId=3)
            RoleCountSystem.BAG_2 to 0,               // Mở rộng túi đồ 2
            RoleCountSystem.BAG_3 to 0,               // Mở rộng túi đồ 3
            RoleCountSystem.BANK_2 to 0,              // Mở rộng tiền trang 2
            RoleCountSystem.BANK_3 to 0,              // Mở rộng tiền trang 3
            RoleCountSystem.SKILL_POINT to 0,         // DVT special counter: client RoleCount.Get(190) dereferences directly
            RoleCountSystem.ATTRIBUTE_POINT to 0,     // DVT special counter: client RoleCount.Get(191) dereferences directly
            RoleCountSystem.FRIEND_COUNT to 0,        // 190..195 must exist before any UI reads them
            RoleCountSystem.SPECIAL_NONE_1 to 0,
            RoleCountSystem.SPECIAL_NONE_2 to 0,
            RoleCountSystem.SPECIAL_NONE_3 to 0,
            RoleCountSystem.STORAGE_1 to 14,          // Mở rộng bảo khố bằng Nguyên Bảo (Private server mặc định 14 → 70 ô, đầy kho)
            RoleCountSystem.STORAGE_2 to 0,           // Mở rộng bảo khố qua sự kiện
            RoleCountSystem.WARRIOR_REPOSITORY to 0   // Mở rộng thêm kho võ tướng
        )
        for ((index, defaultValue) in defaultRoleCounts) {
            if (!roleCountSystem.hasCount(record.id, index)) {
                roleCountSystem.setValue(record.id, index, defaultValue)
            }
        }
        val mergedCounts = roleCountSystem.getAllCounts(record.id)
            .mapValues { (_, valuePair) -> valuePair.first }  // value (Không phải maxValue)
        responseSender.sendRoleCountData(session, mergedCounts)

        pokeSystem?.state(record.id)?.let { poke ->
            responseSender.sendPokeCoin(session, poke.coin)
            responseSender.sendPokeAwards(session, pokeSystem.awards(poke))
        }

        // S:120-008 Linh Hồn: gửi sau RoleCount vì UI Soul dùng RoleCount.Get(823)
        // để tính sức chứa kho Hồn. result=1 là login init theo protocal.lua.
        val soulSnapshot = soulSystem.load(record.id)
        responseSender.sendSoulSnapshot(session, soulSnapshot, result = 1)

        // DVT Chat.Clear() xóa leaveMessages khi reset phiên; nạp snapshot đúng một lần khi đăng nhập.
        val messageBoardSnapshot = try {
            messageBoardRepository?.findRecent(500).orEmpty()
        } catch (t: Throwable) {
            logger.error(t) { "Không thể nạp snapshot bảng nhắn cho characterId=${record.id}" }
            emptyList()
        }
        responseSender.sendMessageBoardSnapshot(session, messageBoardSnapshot)

        // S:023-005 Tất cả vật phẩm trong túi đồ
        responseSender.sendAllBagItems(session, inventorySystem.getBag(record.id), refresh = false)

        // S:023-011 Tất cả trang bị
        responseSender.sendAllEquipment(session, equips)
        // Đồng bộ trường ngoại hình vào session (Dùng cho sendOtherPlayerAppear / Đánh giá điều kiện sự kiện)
        session.equipItemIds = equips.values.map { it.itemId }.toIntArray()
        session.level = record.level
        session.sex = record.sex
        session.element = record.element
        session.hair = record.hair
        session.color1 = record.color1
        session.color2 = record.color2

        // S:026-004 Khởi tạo tiền tệ
        val (gold, auctionGold) = moneySystem.getMoney(record.id)
        responseSender.sendInitMoney(session, gold, auctionGold)

        // S:035-005 Hiển thị số điểm nguyên bảo còn lại
        val (pointReal, pointVirtual) = pointSystem.getPoints(record.id)
        responseSender.sendPointBalance(session, pointReal, pointVirtual)

        // S:035-050 DVT generic currencies. Gửi cả 4..16 để character switch/relog không giữ số dư cũ trên client.
        for (currencyKind in PlayerCurrencySystem.MIN_KIND..PlayerCurrencySystem.MAX_KIND) {
            responseSender.sendPlayerCurrency(session, currencyKind, specialCurrencies[currencyKind] ?: 0)
        }

        // 3. S:005-011 Số lần dùng đan dược
        responseSender.sendPillCounts(session, attrPill = 0, hpPill = 0, spPill = 0, skillPill = 0)

        // 4. S:003-001 Số ngày đăng nhập
        responseSender.sendLoginDay(session, loginDay = 1, continueLogin = 1)

        // 5. S:003-002 Thời gian online hằng ngày
        responseSender.sendOnlineTime(session, serverTime)

        // 6. S:003-003 Thời gian thẻ bán nguyệt
        responseSender.sendHalfMonthCardTime(session, 0.0)

        // S:008-013 Dữ liệu kỹ năng nhân vật chính
        val skillCache = skillSystem.load(record.id, record.element, record.skillPoint)
        responseSender.sendPlayerSkillData(session, skillCache.element, skillCache.skillPoint, skillCache.skills)
        responseSender.sendTurn2Balls(session, skillCache.availableTurn2Balls, skillCache.activeTurn2Balls)
        responseSender.sendTurn2HeroSkills(session, skillCache.heroSkills)

        // S:040-001 Dữ liệu phím tắt (Tải từ DB)
        val hotkeyEntries = hotkeyRepository.findByCharId(record.id)
        val mainKeys = MutableList(5) { 0 }  // keyIndex 1-5 → index 0-4
        val subKeys = MutableList(5) { 0 }
        for (entry in hotkeyEntries) {
            val idx = entry.keyIndex - 1  // keyIndex hệ cơ số 1 (1-based) → hệ cơ số 0 (0-based)
            if (idx !in 0..4) continue
            when (entry.dataIndex) {
                1 -> mainKeys[idx] = entry.useId
                2 -> subKeys[idx] = entry.useId
            }
        }
        responseSender.sendHotkeyData(session, mainKeys = mainKeys, subKeys = subKeys)

        // S:015-008 Dữ liệu võ tướng mang theo bên mình của nhân vật chính
        val petCache = petSystem.load(record.id)
        // S15-8 only adds/updates slots on the DVT client; it does not delete an
        // old FollowNpc object that is absent from the new server snapshot. Clear
        // every carried slot visible to this server/client mode so reconnect/login
        // cannot leave a stale 'ghost' pet on the map or in Role.followNpcs.
        val followCapacity = petSystem.getFollowCapacity(record.id)
        responseSender.sendFollowNpcRecall(session)
        for (slot in 1..followCapacity) {
            responseSender.sendFollowNpcRemove(session, session.playerId, slot, showMsg = false)
        }
        responseSender.sendFollowNpcFullData(
            session,
            petCache.follow.values
                .filter { it.slotIndex in 1..followCapacity }
                .sortedBy { it.slotIndex }
        )
        // S:015-010 Dữ liệu võ tướng trong xe ngựa
        if (petCache.cart.isNotEmpty()) {
            responseSender.sendCartData(session, petCache.cart.values.toList())
        }
        // Multi-pet deployment restore. Existing databases only have characters.fight_npc_id;
        // migrate that legacy single selection once if no per-pet flag exists yet.
        if (petSystem.getDeployedFollowSlots(record.id).isEmpty() &&
            record.fightNpcId > 0 &&
            petCache.follow.values.any { it.npcId == record.fightNpcId }
        ) {
            petSystem.deployFollowNpc(record.id, record.fightNpcId)
            petSystem.save(record.id)
        }

        val deployedSlots = petSystem.getDeployedFollowSlots(record.id)
        val primaryDeployed = deployedSlots.firstOrNull()?.let { petCache.follow[it] }
        if (primaryDeployed != null) {
            // Legacy field/view remains populated for unchanged DVT logic.
            responseSender.sendFollowNpcDeployInit(session, primaryDeployed.npcId)
        }
        for (slot in deployedSlots) {
            responseSender.sendFollowNpcDeployState(session, slot, true)
        }

        // Load persistent Collection Book state and reconcile permanent ownership flags
        // before S:081-002 so CardView/Warrior tabs see authoritative flags immediately.
        val collectionSnapshot = collectionSystem?.loadCharacter(record.id)
        collectionSystem?.syncOwnedWarriors(record.id, petSystem.getAllOwnedNpcIds(record.id))

        // S:081-002 Khởi tạo cờ vĩnh viễn (Cờ hoàn thành hướng dẫn, Sưu Tập, v.v.).
        val bitFlagIds = bitFlagSystem.getSetFlagIds(record.id)
        val packedFlags = bitFlagSystem.packFlags(bitFlagIds)
        responseSender.sendInitBitFlags(session, packedFlags)

        // S:095-004/009/010 persistent Collection Book state.
        if (collectionSnapshot != null && collectionSystem != null) {
            responseSender.sendCollectionStyleData(
                session,
                collectionSnapshot.styleFlags,
                collectionSystem.getStyleDataCount(),
            )
            responseSender.sendCollectionCardEquipData(
                session,
                collectionSnapshot.maxEquipCards,
                collectionSnapshot.equippedCards,
            )
            responseSender.sendCollectionCardData(session, collectionSnapshot.cardLevels)
            responseSender.sendCollectionNftShine(session, collectionSystem.getNftShine(record.id))
        }

        // S:077 runtime schedule/state. Lua UIActivity keeps static ActivityTime_C locally
        // but requires S:077-001/002 to know the actual current window/state.
        activityScheduleSystem?.sendAllActivityStates(session)

        // S:172-001 activity-server route. Keep a distinct logical activity-server ID so
        // FightField/UITeleport do not classify the production GS itself as an event server.
        // C:001-100 is handled as a ticketed reconnect back to this same physical GS.
        session.send(
            activityServerInfoPacket(
                ACTIVITY_VIRTUAL_SERVER_ID,
                ip = ACTIVITY_ROUTE_MARKER_IP,
            )
        )

        // S:077 thời gian hoạt động runtime + S:091-001 bảng cửu cung daily.
        // UIJiugongge opens later from the Hằng ngày button and expects this state to exist.
        jiugonggeSystem?.sendDailyInit(session)

        // S:023-118 Dữ liệu bùa truyền tống ghi nhớ (Điểm nhớ)
        val flyPoints = flyPointSystem.loadAll(record.id)
        val flyExtendCount = flyPointSystem.getExtendCount(record.id)
        responseSender.sendFlyPointData(session, flyExtendCount, flyPoints)

        // S:079-001 Dữ liệu thú cưỡi
        val mountData = mountSystem.load(record.id)
        responseSender.sendMountData(session, mountData)
        session.mountLevel = mountData.level
        if (mountData.currentNpcId > 0) session.mountNpcId = mountData.currentNpcId

        // Reconcile observable one-time Daily conditions for characters that already
        // satisfied them before DailyProgress tracking was added.
        if (mountData.currentNpcId > 0) {
            dailyProgressSystem?.setRoleCountAtLeast(
                session,
                DailyProgressSystem.MOUNTS,
                1,
            )
        }
        if (petCache.fightNpcId > 0) {
            dailyProgressSystem?.setConditionFlag(
                session,
                DailyProgressSystem.FLAG_DEPLOY_FOLLOW_NPC,
            )
        }

        // 7. S:001-013 Hoàn tất chuỗi đăng nhập
        responseSender.sendLoginFinished(session, connectCode)

        // Đăng ký vào trình quản lý thế giới (WorldManager)
        worldManager.registerPlayer(session)
        // Lưới an toàn: Nếu DB lưu mapId không hợp lệ (Do race condition dẫn đến mapId=0), lùi về làng tân thủ
        val safeMapId = if (record.mapId > 0) record.mapId else tw.tsm.domain.constants.DEFAULT_START_MAP
        val safeX = if (record.mapId > 0) record.x else tw.tsm.domain.constants.DEFAULT_START_X
        val safeY = if (record.mapId > 0) record.y else tw.tsm.domain.constants.DEFAULT_START_Y
        worldManager.enterMap(session, safeMapId, safeX, safeY)

        // Gửi gói tin khởi tạo cảnh, kích hoạt DoorManager.OnEnterScene() của client
        // Đảm bảo navigationDatas → areas → areaId được khởi tạo chính xác (Dựa vào auto-pathing / Tự động tìm đường)
        responseSender.sendPlayerTeleport(session, session.playerId, safeMapId, safeX, safeY)
        responseSender.sendSceneChange(session, session.playerId, safeMapId, safeX, safeY, session.equipItemIds, session.titleId)

        // S:022-004 Khởi tạo MapNpc đã được dời sang RelocateHandler (Chỉ gửi sau khi load xong cảnh C:012-001)
        // Tránh việc kích hoạt ContinueNavigation khi cảnh chưa load xong, dẫn đến lỗi "Cảnh mục tiêu chưa mở"

        // Khởi tạo trạng thái runtime của người chơi (Cần thiết cho việc sử dụng vật phẩm, hệ thống chiến đấu)
        playerStateManager.initPlayer(
            characterId = record.id,
            hp = playerData.maxHp,
            sp = playerData.maxSp,
            maxHp = playerData.maxHp,
            maxSp = playerData.maxSp,
            level = record.level,
            atk = playerData.atk,
            def = playerData.def,
            agi = playerData.agi,
            intelligence = playerData.intelligence,
            hpx = playerData.hpx,
            spx = playerData.spx,
            element = playerData.element,
            turn = record.turn,
            career = record.career,
            attrPoint = playerData.attrPoint,
            skillPoint = playerData.skillPoint,
            equipBonus = equipBonus,
            astrolabeBonus = astrolabeBonus,
            turn3Int = turn3State.intelligence,
            turn3Atk = turn3State.atk,
            turn3Def = turn3State.def,
            turn3Agi = turn3State.agi,
            turn3Hpx = turn3State.hpx,
            turn3Spx = turn3State.spx,
            killNum = playerData.killNum,
            moral = playerData.moral,
            sex = record.sex
        )
        // Collection state is loaded earlier, before PlayerState exists. Apply its
        // authoritative max-HP/SP delta now that runtime state has been initialized,
        // then synchronize absolute values to the client before login completes.
        collectionSystem?.refreshRuntimeMax(record.id)
        playerStateManager.getState(record.id)?.let { runtime ->
            responseSender.sendSetAttribute(session, EAttribute.MAX_HP, runtime.maxHp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.HP, runtime.hp.toInt(), true)
            responseSender.sendSetAttribute(session, EAttribute.MAX_SP, runtime.maxSp, true)
            responseSender.sendSetAttribute(session, EAttribute.SP, runtime.sp, true)
        }

        // Tải điểm kinh nghiệm (EXP) từ DB
        playerStateManager.getState(record.id)?.exp = record.exp

        // Bảo lưu completedEveCounts — conditionClass=12 cần tích lũy kỷ lục hoàn thành để ngăn ngừa nhiệm vụ bị kích hoạt lặp lại

        // S:083-001 Dữ liệu thư tín
        mailSystem.loadAndSend(session)

        // S:014-005 Danh sách bạn bè + S:014-008 Lời mời kết bạn
        friendSystem.loadAndSend(session)

        // S:033-002 Khởi tạo trạng thái công tắc PK
        responseSender.sendPkSwitch(session, session.pkEnabled, session.joinEnabled)

        logger.info { "Hoàn tất chuỗi nhân vật tiến vào: '${record.name}' (id=${record.id}) Bản đồ=$safeMapId ($safeX, $safeY)" }
    }

    /** Refresh S:077 daily activity time after the client asks C:001-016. */
    fun refreshDailyActivityTime(session: GameSession) {
        jiugonggeSystem?.refreshDailyActivityTime(session)
    }

    /** Re-push S:091-001 after the activity clock is valid so UIJiugongge can re-arm its timer. */
    suspend fun refreshDailySnapshot(session: GameSession) {
        jiugonggeSystem?.sendDailySnapshot(session)
    }

    companion object {
        /** Logical activity-shard identity used by the original DVT client handoff path. */
        const val ACTIVITY_VIRTUAL_SERVER_ID = 90

        /**
         * Network.ConnectToActivityServer only requires a non-empty activityServerIp before
         * it sends C:001-100. The actual reconnect host is selected from S:001-100 GSID,
         * so this marker is never used as a socket destination.
         */
        const val ACTIVITY_ROUTE_MARKER_IP = "same-gs"

        /**
         * S:172-001 <activity server> + ServerID(Int32) + ServerPort(Int32) + ServerIP(raw Big5).
         */
        fun activityServerInfoPacket(
            serverId: Int,
            port: Int = DEFAULT_PORT,
            ip: String = "",
        ): ByteArray =
            PacketWriter()
                .writeByte(172).writeByte(1).writeByte(0)
                .writeIntLE(serverId)
                .writeIntLE(port)
                .writeBytes(ip.toByteArray(PacketWriter.BIG5))
                .toByteArray()

        /**
         * Lấy thời gian máy chủ theo định dạng OLE Automation Date
         *
         * OLE Automation Date = Số ngày tính từ 30-12-1899 + Phần thập phân là thời gian trong một ngày
         */
        fun currentServerTime(): Double = OleDate.nowLocal()
    }
}
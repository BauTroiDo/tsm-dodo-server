package tw.tsm.app

import io.github.oshai.kotlinlogging.KotlinLogging
import com.typesafe.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module
import tw.tsm.app.gm.GmCommandProcessor
import tw.tsm.app.handlers.*
import tw.tsm.app.response.FurnaceResponseSender
import tw.tsm.app.response.GodskinResponseSender
import tw.tsm.app.response.InnResponseSender
import tw.tsm.app.response.SoulResponseSender
import tw.tsm.app.response.HouseBoardResponseSender
import tw.tsm.data.GameDataManager
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.interfaces.repositories.AccountRepository
import tw.tsm.domain.interfaces.repositories.AstrolabeRepository
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.interfaces.repositories.ElfRepository
import tw.tsm.domain.interfaces.repositories.MallQuotaRepository
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.BitFlagRepository
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*
import tw.tsm.network.NettyGameServer
import tw.tsm.network.PacketDispatcher
import tw.tsm.network.SessionManager
import tw.tsm.persistence.repositories.AccountRepositoryImpl
import tw.tsm.persistence.repositories.AstrolabeRepositoryImpl
import tw.tsm.persistence.repositories.SoulRepositoryImpl
import tw.tsm.persistence.repositories.ElfRepositoryImpl
import tw.tsm.persistence.repositories.MallQuotaRepositoryImpl
import tw.tsm.persistence.repositories.CharacterRepositoryImpl
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.repositories.BitFlagRepositoryImpl
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.domain.interfaces.repositories.MountRepository
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.interfaces.repositories.FlyPointRepository
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.domain.interfaces.repositories.LimitedAreaDailyRepository
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.domain.interfaces.repositories.DungeonRunRepository
import tw.tsm.domain.interfaces.repositories.RewardLedgerRepository
import tw.tsm.domain.interfaces.repositories.FriendRepository
import tw.tsm.domain.interfaces.repositories.HouseBoardRepository
import tw.tsm.domain.interfaces.repositories.WelfareStateRepository
import tw.tsm.domain.interfaces.repositories.MailRepository
import tw.tsm.domain.interfaces.repositories.MiningRepository
import tw.tsm.domain.interfaces.repositories.MessageBoardRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository
import tw.tsm.domain.interfaces.repositories.CharacterCurrencyRepository
import tw.tsm.domain.interfaces.repositories.CollectionBookRepository
import tw.tsm.domain.interfaces.repositories.SlotMachineRepository
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import tw.tsm.domain.interfaces.repositories.PokeRepository
import tw.tsm.persistence.repositories.UiSellCountRepositoryImpl
import tw.tsm.persistence.repositories.CharacterCurrencyRepositoryImpl
import tw.tsm.persistence.repositories.CollectionBookRepositoryImpl
import tw.tsm.persistence.repositories.SlotMachineRepositoryImpl
import tw.tsm.persistence.repositories.ExpSlotRepositoryImpl
import tw.tsm.persistence.repositories.PokeRepositoryImpl
import tw.tsm.persistence.repositories.DispatchRepositoryImpl
import tw.tsm.persistence.repositories.DungeonRunRepositoryImpl
import tw.tsm.persistence.repositories.RewardLedgerRepositoryImpl
import tw.tsm.persistence.repositories.FriendRepositoryImpl
import tw.tsm.persistence.repositories.HouseBoardRepositoryImpl
import tw.tsm.persistence.repositories.WelfareStateRepositoryImpl
import tw.tsm.persistence.repositories.MailRepositoryImpl
import tw.tsm.persistence.repositories.MiningRepositoryImpl
import tw.tsm.persistence.repositories.MessageBoardRepositoryImpl
import tw.tsm.persistence.repositories.HotkeyRepositoryImpl
import tw.tsm.persistence.repositories.MountRepositoryImpl
import tw.tsm.persistence.repositories.FlyPointRepositoryImpl
import tw.tsm.persistence.repositories.PetRepositoryImpl
import tw.tsm.persistence.repositories.QuestRepositoryImpl
import tw.tsm.persistence.repositories.RoleCountRepositoryImpl
import tw.tsm.persistence.repositories.LimitedAreaDailyRepositoryImpl
import tw.tsm.persistence.repositories.SkillRepositoryImpl

private val serverLogger = KotlinLogging.logger {}

/**
 * Định nghĩa module Koin DI
 *
 * @param config cấu hình ứng dụng
 * @param gameDataManager bộ quản lý dữ liệu game đã khởi tạo
 */
fun serverModule(config: Config, gameDataManager: GameDataManager) = module {
    TeamDungeonBotPolicy.configureOutfits(gameDataManager.itemDefs)
    // CoroutineScope dùng chung
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Tầng mạng
    single { SessionManager() }
    single {
        PacketDispatcher().apply {
            register(get<KeepaliveHandler>())
            register(get<LoginHandler>())
            register(get<CreateCharHandler>())
            register(get<SelectCharHandler>())
            register(get<DisconnectHandler>())
            register(get<MoveHandler>())
            register(get<ChatHandler>())
            register(get<SceneHandler>())
            register(get<RelocateHandler>())
            register(get<NpcEventHandler>())
            register(get<ItemHandler>())
            register(get<NpcTradeHandler>())
            register(get<BankHandler>())
            register(get<StorageHandler>())
            register(get<TradeHandler>())
            register(get<BattleManageHandler>())
            register(get<BattleCommandHandler>())
            register(get<PlayerStatusHandler>())
            register(get<SkillUpgradeHandler>())
            register(get<PartyHandler>())
            register(get<FollowNpcHandler>())
            register(get<FollowNpcDeployHandler>())
            register(get<PkSettingHandler>())
            register(get<StatHandler>())
            register(get<AstrolabeHandler>())
            register(get<SoulHandler>())
            register(get<ElfHandler>())
            register(get<HouseBoardHandler>())
            register(get<GodskinHandler>())
            register(get<MountHandler>())
            register(get<HotkeyHandler>())
            register(get<InnHandler>())
            register(get<WarriorRepositoryHandler>())
            register(get<MacroBoxHandler>())
            register(get<TeleportHandler>())
            register(get<WorldAtlasHandler>())
            register(get<LimitTimeDungeonHandler>())
            register(get<DungeonHandler>())
            register(get<EternalDungeonHandler>())
            register(get<AchievementHandler>())
            register(get<EmoteHandler>())
            register(get<DispatchHandler>())
            register(get<WelfareHandler>())
            register(get<MailHandler>())
            register(get<LeaderboardHandler>())
            register(get<FurnaceHandler>())
            register(get<MessageBoardHandler>())
            register(get<FriendHandler>())
            register(get<CollectionHandler>())
            register(get<UISellHandler>())
            register(get<PetUpgradeHandler>())
            register(get<PetRebornHandler>())
            register(get<JiugonggeHandler>())
            register(get<ActivityHandler>())
            register(get<ActivityModelHandler>())
            register(get<MallHandler>())
            register(get<SlotMachineHandler>())
            register(get<ExpSlotHandler>())
            register(get<PokeHandler>())
            register(get<MiningHandler>())
        }
    }
    single {
        val worldManager = get<WorldManager>()
        val inventorySystem = get<InventorySystem>()
        val moneySystem = get<MoneySystem>()
        val pointSystem = get<PointSystem>()
        val skillSystem = get<SkillSystem>()
        val petSystem = get<PetSystem>()
        val mountSystem = get<MountSystem>()
        val partySystem = get<PartySystem>()
        val characterRepository = get<CharacterRepository>()
        val tradeSystem = get<TradeSystem>()
        val battleManager = get<BattleManager>()
        val playerStateManager = get<PlayerStateManager>()
        val eveStateMachine = get<EveEventStateMachine>()
        val macroBoxSystem = get<MacroBoxSystem>()
        val roleCountSystem = get<RoleCountSystem>()
        val collectionSystem = get<CollectionSystem>()
        val astrolabeSystem = get<AstrolabeSystem>()
        val soulSystem = get<SoulSystem>()
        val friendSystem = get<FriendSystem>()
        val scope = get<CoroutineScope>()
        NettyGameServer(get(), get(), get()) { session ->
            // Khi mất kết nối huỷ giao dịch đang tiến hành
            tradeSystem.cancelTradeOnDisconnect(session.playerId)
            // Khi mất kết nối dọn dẹp chiến đấu
            battleManager.handleDisconnect(session.playerId)
            // Khi mất kết nối dọn dẹp tổ đội
            partySystem.handleDisconnect(session.playerId)
            // Dọn lobby/active dungeon room và chuyển chủ phòng nếu cần.
            get<DungeonHandler>().handleDisconnect(session)
            // Khi mất kết nối dọn dẹp trạng thái sự kiện
            eveStateMachine.clear(session.roleId.toLong())
            // Khi mất kết nối dọn dẹp trạng thái hộp cơ quan
            macroBoxSystem.cleanup(session.roleId.toLong())
            // Khi mất kết nối thông báo bạn bè offline
            if (session.roleId > 0) {
                scope.launch {
                    try { friendSystem.handleDisconnect(session.roleId.toLong()) }
                    catch (e: Exception) { serverLogger.error(e) { "Thông báo ngắt kết nối tới bạn bè thất bại" } }
                }
            }
            // Khi mất kết nối lưu và xoá trạng thái runtime
            if (session.roleId > 0) {
                val roleId = session.roleId.toLong()
                get<LimitTimeDungeonSystem>().stopTracking(roleId)
                val state = playerStateManager.getState(roleId)
                if (state != null) {
                    scope.launch {
                        try {
                            characterRepository.updatePlayerState(
                                roleId, state.exp, state.level,
                                state.attrPoint, state.skillPoint,
                                state.intelligence, state.atk, state.def, state.agi, state.hpx, state.spx
                            )
                        } catch (e: Exception) {
                            serverLogger.error(e) { "Lưu khi ngắt kết nối bất thường thất bại (trạng thái nhân vật): roleId=$roleId" }
                        }
                    }
                }
                collectionSystem.unload(roleId)
                soulSystem.unload(roleId)
                playerStateManager.removePlayer(roleId)
            }

            // Chụp snapshot vị trí trước, rồi loại bỏ (leaveMap sẽ đặt session.mapId về 0)
            val mapId = session.mapId
            val x = session.x
            val y = session.y

            worldManager.removePlayer(session)
            // Khi mất kết nối bất thường lưu vị trí/vật phẩm/tiền của nhân vật (không suspend, dùng scope.launch)
            // Kiểm tra mapId > 0: tránh sau khi DisconnectHandler đã lưu, channelInactive dùng mapId=0 ghi đè
            if (session.roleId > 0) {
                val roleId = session.roleId.toLong()
                scope.launch {
                    // Mỗi mục dùng try-catch độc lập, đảm bảo một mục thất bại không ảnh hưởng việc lưu các mục khác
                    if (mapId > 0) {
                        try { characterRepository.updatePosition(roleId, mapId, x, y) }
                        catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (vị trí): roleId=$roleId" } }
                    }
                    try { inventorySystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (vật phẩm): roleId=$roleId" } }

                    try { moneySystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (tiền vàng): roleId=$roleId" } }

                    try { pointSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (nguyên bảo): roleId=$roleId" } }

                    try { skillSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (kỹ năng): roleId=$roleId" } }

                    try { petSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (tướng): roleId=$roleId" } }

                    try { mountSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (vật cưỡi): roleId=$roleId" } }

                    try { roleCountSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Lưu ngắt kết nối thất bại (bộ đếm): roleId=$roleId" } }

                    try { astrolabeSystem.unload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "Gỡ cache Mệnh Bàn thất bại: roleId=$roleId" } }
                }
            }
        }
    }

    // Repository (interface domain → triển khai persistence)
    single<AccountRepository> { AccountRepositoryImpl() }
    single<AstrolabeRepository> { AstrolabeRepositoryImpl() }
    single<SoulRepository> { SoulRepositoryImpl() }
    single<ElfRepository> { ElfRepositoryImpl() }
    single<MallQuotaRepository> { MallQuotaRepositoryImpl() }
    single<CharacterRepository> { CharacterRepositoryImpl() }
    single<InventoryRepository> { InventoryRepositoryImpl() }
    single<QuestRepository> { QuestRepositoryImpl() }
    single<BitFlagRepository> { BitFlagRepositoryImpl() }
    single<SkillRepository> { SkillRepositoryImpl() }
    single<PetRepository> { PetRepositoryImpl() }
    single<MountRepository> { MountRepositoryImpl() }
    single<HotkeyRepository> { HotkeyRepositoryImpl() }
    single<RoleCountRepository> { RoleCountRepositoryImpl() }
    single<LimitedAreaDailyRepository> { LimitedAreaDailyRepositoryImpl() }
    single<FlyPointRepository> { FlyPointRepositoryImpl() }
    single<UiSellCountRepository> { UiSellCountRepositoryImpl() }
    single<CharacterCurrencyRepository> { CharacterCurrencyRepositoryImpl() }
    single<CollectionBookRepository> { CollectionBookRepositoryImpl() }
    single<SlotMachineRepository> { SlotMachineRepositoryImpl() }
    single<ExpSlotRepository> { ExpSlotRepositoryImpl() }
    single<PokeRepository> { PokeRepositoryImpl() }
    single<DispatchRepository> { DispatchRepositoryImpl() }
    single<DungeonRunRepository> { DungeonRunRepositoryImpl() }
    single<RewardLedgerRepository> { RewardLedgerRepositoryImpl() }
    single<MailRepository> { MailRepositoryImpl() }
    single<MessageBoardRepository> { MessageBoardRepositoryImpl() }
    single<FriendRepository> { FriendRepositoryImpl() }
    single<HouseBoardRepository> { HouseBoardRepositoryImpl() }
    single<WelfareStateRepository> { WelfareStateRepositoryImpl() }
    single<MiningRepository> { MiningRepositoryImpl() }

    // Dữ liệu game
    single { gameDataManager }
    single { StatSystem(get<GameDataManager>().formulaParams) }
    single<StatCalculator> { get<StatSystem>() }

    // Logic game
    single { WorldManager() }
    single { AuthSystem(get()) }
    single<ResponseSender> {
        val wm = get<WorldManager>()
        val rs = ResponseSenderImpl(wm)
        wm.responseSender = rs  // Phá vỡ phụ thuộc vòng
        rs
    }
    single {
        NpcPatrolSystem(get(), get(), get<GameDataManager>().eveData, get(), get<EveTriggerRouter>()).also { patrol ->
            get<WorldManager>().npcPatrolSystem = patrol
            patrol.start()
        }
    }
    single { InventorySystem(get(), get<GameDataManager>().itemDefs, get()) }
    single { MoneySystem(get()) }
    single { PointSystem(get()) }
    single { PlayerCurrencySystem(get<CharacterCurrencyRepository>()) }
    single { MythEnchantSystem(get<InventorySystem>(), get<PlayerCurrencySystem>(), get<GameDataManager>().itemDefs) }
    single { MythUpgradeSystem(get<InventorySystem>(), get<GameDataManager>().itemDefs) }
    single { DoroSystem(get<GameDataManager>().goodsSaleDefs, get<GameDataManager>().doroBlissBagDefs) }
    single {
        SlotMachineSystem(
            get<SlotMachineRepository>(),
            get<GameDataManager>().slotMachinePools
                ?: error("IOS_Slot.dat chưa được nạp"),
            get<InventorySystem>(),
            get<PlayerCurrencySystem>(),
            get<GameDataManager>().itemDefs
        )
    }
    single {
        PokeSystem(
            get<PokeRepository>(),
            get<GameDataManager>().pokeRewardDefs,
            get<GameDataManager>().pokeRateByKind,
            get<InventorySystem>(),
            get<GameDataManager>().itemDefs,
            get<PlayerCurrencySystem>(),
        )
    }
    single { GroundItemManager(get<GameDataManager>().eveData) }
    single { PlayerStateManager() }
    single {
        AstrolabeSystem(
            repository = get<AstrolabeRepository>(),
            defs = get<GameDataManager>().astrolabeDefs,
            moneySystem = get<MoneySystem>(),
            playerStateManager = get<PlayerStateManager>(),
            statSystem = get<StatSystem>()
        )
    }
    single { SoulSystem(get<SoulRepository>()) }
    single { ElfCatalog.fromPcFiles() }
    single { MallQuotaSystem(get<MallQuotaRepository>(), get<RoleCountSystem>(), get<GameDataManager>().goodsSaleDefs) }
    single {
        OfflineSoulGenerationPolicy(
            soulDefs = get<GameDataManager>().soulDefs,
            npcSoulDefs = get<GameDataManager>().npcSoulDefs,
            npcDefs = get<GameDataManager>().npcDefs,
        )
    }
    single { SoulResponseSender() }
    single { ItemUseProcessor(get(), get<PetSystem>(), get<GameDataManager>().itemDefs, get<GameDataManager>().blissBagDefs) }
    single { EveEventStateMachine() }
    single {
        val data = get<GameDataManager>()
        val normalDungeonDefs = data.dungeonDefs.values.filter { it.kind in setOf(1, 2) }
        val weeklyDungeonDefs = data.dungeonDefs.values.filter { it.kind in setOf(3, 5) }
        QuestSystem(
            repo = get(),
            markDefs = data.markDefs,
            dailyDungeonMissionIds = normalDungeonDefs.map { it.dailyFlag }.filter { it > 0 }.toSet(),
            weeklyDungeonMissionIds = weeklyDungeonDefs.map { it.dailyFlag }.filter { it > 0 }.toSet(),
            weeklyClaimMissionIds = weeklyDungeonDefs.map { it.costPoint }.filter { it > 0 }.toSet(),
        )
    }
    single { BitFlagSystem(get(), get<GameDataManager>().guideLastBitFlagId) }
    single {
        CollectionSystem(
            get<BitFlagSystem>(),
            get<GameDataManager>().collectWarriorDefs,
            get<GameDataManager>().collectStyleDefs,
            get<GameDataManager>().collectStyleValueDefs,
            get<GameDataManager>().collectCardDefs,
            get<GameDataManager>().collectCardSortDefs,
            get<GameDataManager>().collectNftDefs,
            get<GameDataManager>().mountDefs,
            get<InventorySystem>(),
            get<RoleCountSystem>(),
            get<PlayerStateManager>(),
            get<StatSystem>(),
            get<CollectionBookRepository>(),
            get<ResponseSender>(),
        )
    }
    single { SkillSystem(get(), get(), get<GameDataManager>().skillDefs) }
    single { PetSkillSystem(get(), get<GameDataManager>().npcDefs, get<GameDataManager>().skillDefs) }
    single { PartySystem() }
    single {
        PetRosterPolicy(
            ninePetEnabled = config.getBoolean("features.ninePet.enabled"),
            extensionMaxCarried = config.getInt("features.ninePet.maxCarried"),
            progressionEnabled = config.getBoolean("features.ninePet.progression.enabled"),
            unlockLevels = config.getIntList("features.ninePet.progression.unlockLevels").map { it.toInt() },
            rebirthUnlockAll = config.getBoolean("features.ninePet.progression.rebirthUnlockAll"),
        )
    }
    single {
        PetSystem(
            get(),
            get<StatSystem>(),
            get<GameDataManager>().npcDefs,
            get<GameDataManager>().itemDefs,
            get<RoleCountSystem>(),
            get<PetRosterPolicy>(),
            get<PlayerStateManager>(),
            get<GameDataManager>().sacredEquipPrimaryDefs,
        )
    }
    single {
        ExpSlotSystem(
            get<GameDataManager>().fNpcSlotDefs,
            get<ExpSlotRepository>(),
            get<PetSystem>(),
            get<GameDataManager>().npcDefs,
            get<InventorySystem>(),
        )
    }
    single {
        MountSystem(
            get(), get<GameDataManager>().mountDefs, get<GameDataManager>().mountGrowDefs,
            get(), get(), get(), get<GameDataManager>().itemDefs
        )
    }
    single { MacroBoxSystem() }
    single { RoleCountSystem(get()) }
    single {
        SpecialSceneTeleportService(
            get<ResponseSender>(), get<WorldManager>(), get<CharacterRepository>(),
            get<GameDataManager>().eveData, get<GameDataManager>().mapWalkGrids,
            get<GameDataManager>().sceneSetDefs
        )
    }
    single {
        LimitTimeDungeonSystem(
            get<GameDataManager>().limitTimeDungeonDefs,
            get<RoleCountSystem>(), get<ResponseSender>(), get<SpecialSceneTeleportService>(), get<CoroutineScope>(), get<AdminBypassPolicy>(), get<QuestSystem>(),
            get<GameDataManager>().anotherWorldLvDefs,
            dailyRepository = get<LimitedAreaDailyRepository>()
        )
    }
    single {
        DungeonSystem(
            get<GameDataManager>().dungeonDefs,
            get<QuestSystem>(),
            get<PartySystem>(),
            get<SpecialSceneTeleportService>(),
            get<ResponseSender>(),
            get<GameDataManager>().dungeonComboDefs,
            get<InventorySystem>(),
            sceneSetDefs = get<GameDataManager>().sceneSetDefs,
            scope = get<CoroutineScope>(),
            dungeonRunRepository = get<DungeonRunRepository>(),
            adminBypassPolicy = get<AdminBypassPolicy>(),
            petSystem = get<PetSystem>(),
            soulSystem = get<SoulSystem>(),
            npcDefs = get<GameDataManager>().npcDefs,
            npcUpgradeDefs = get<GameDataManager>().npcUpgradeDefs,
            itemDefs = get<GameDataManager>().itemDefs,
            exclusiveWeaponDefs = get<GameDataManager>().exclusiveWeaponDefs,
            collectWarriorSortDefs = get<GameDataManager>().collectWarriorSortDefs,
            rewardLedgerRepository = get<RewardLedgerRepository>(),
        )
    }
    single { DungeonRoomRegistry() }
    single {
        MiningSystem(
            get<MiningRepository>(),
            get<InventorySystem>(),
            get<GameDataManager>().itemDefs,
        )
    }
    single {
        CompoundSystem(
            get<InventorySystem>(),
            get<GameDataManager>().itemDefs,
            get<GameDataManager>().compoundRecipes,
            get<RoleCountSystem>(),
            get<SkillSystem>(),
        )
    }
    single { DailyProgressSystem(get<RoleCountSystem>(), get<BitFlagSystem>(), get<ResponseSender>()) }
    single { FlyPointSystem(get(), get()) }
    single { UISellSystem(get<GameDataManager>().uiSellDefs, get<GameDataManager>().uiSellFormulaDefs, get(), get(), get()) }
    single {
        FurnaceSystem(
            get<GameDataManager>().furnaceSlotDefs,
            get<GameDataManager>().furnaceSelectDefs,
            get<GameDataManager>().itemDefs,
            get<InventorySystem>(),
            get<RoleCountSystem>(),
            get<BitFlagSystem>(),
            get<UISellSystem>(),
        )
    }
    single { PetUpgradeSystem(get(), get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().npcUpgradeDefs) }
    single { PetRebornSystem(get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().itemDefs) }
    single { PetEquipSystem(get(), get(), get<GameDataManager>().itemDefs) }
    single {
        AchievementSystem(
            get(), get(), get(), get(), get(), get(),
            get<GameDataManager>().achievementDefs,
        )
    }
    single { TradeSystem(get(), get(), get(), get(), get<GameDataManager>().itemDefs, get(), get(), get<StatSystem>(), get<GameDataManager>().sacredEquipPrimaryDefs) }
    single {
        DispatchSystem(
            get(),
            get(),
            get<GameDataManager>().dispatchDefs,
            get<GameDataManager>().dispatchBonusDefs,
            get<GameDataManager>().commonAwardDefs,
            get<GameDataManager>().luckyBagDefs,
            get<InventorySystem>(),
            get<MoneySystem>(),
            get<PointSystem>(),
            get<RoleCountSystem>(),
        )
    }
    single { MailSystem(get(), get(), get()) }
    single { FriendSystem(get(), get(), get(), get(), get<RoleCountSystem>()) }
    single {
        HouseBoardSystem(
            get<HouseBoardRepository>(),
            get<CharacterRepository>(),
            get<FriendRepository>(),
            get<InventorySystem>(),
            get<MoneySystem>(),
            get<PointSystem>(),
            get<RoleCountSystem>(),
            get<GameDataManager>().souvenirDefs,
        )
    }
    single { HouseBoardResponseSender() }
    single {
        LeaderboardSystem(
            get(),
            get<GameDataManager>().leaderboardDefs,
            get(),
            get(),
            get<GameDataManager>().npcDefs,
            get<AchievementSystem>(),
        ).also {
            it.startPeriodicRefresh()
        }
    }
    single { AdminBypassPolicy() }
    single { BattlePassSystem(get<GameDataManager>().passAwardDefs, get<GameDataManager>().activityTimeDefs,
        get<GameDataManager>().itemDefs, get<InventorySystem>(), get<RoleCountSystem>(), get<BitFlagSystem>(),
        get<GameDataManager>().missionAwardDefs) }
    single { ActivityScheduleSystem(get<GameDataManager>().activityTimeDefs, get<AdminBypassPolicy>(),
        get<GameDataManager>().passAwardDefs.map { it.seasonId }.toSet()) }

    single {
        JiugonggeSystem(
            get<GameDataManager>().jiugonggeInfoDefs,
            get<GameDataManager>().jiugonggeMissionDefs,
            get<GameDataManager>().activityTimeDefs,
            get<RoleCountSystem>(),
            get<BitFlagSystem>(),
            get<InventorySystem>(),
            get<ResponseSender>(),
            get<GameDataManager>().itemDefs,
        )
    }
    single {
        BattleManager(get(), get<GameDataManager>().skillDefs, get<GameDataManager>().npcDefs, get(), get(), get<PetSystem>(), get<MoneySystem>(), get<StatSystem>(), get(), get<GameDataManager>().npcDropTable, get<SkillSystem>(), get<MacroBoxSystem>(), get<GameDataManager>().itemDefs, get<SkillRepository>(), get<CharacterRepository>(), get<GameDataManager>().npcUpgradeDefs, get<GameDataManager>().sceneSetDefs, get<DailyProgressSystem>(), get<CollectionSystem>(), get<GameDataManager>().sacredEquipPrimaryDefs, get<GameDataManager>().exclusiveSkillDefs, get<SoulSystem>(), get<ElfRepository>(), get<ElfCatalog>().defs.mapValues { it.value.baseExp }).also { bm ->
            // Callback kết thúc chiến đấu → EveFlowController khôi phục luồng sự kiện
            bm.onBattleAbortCallback = { session -> get<EveFlowController>().abortAfterBattle(session) }
            bm.onBattleEndCallback = { session, playerId, playerWin ->
                // After reward settlement, refresh DVT S024-008 with remaining Phuc Than charges.
                try { get<ElfHandler>().syncGodMission(session) }
                catch (_: Exception) { /* DB result is already persistent; map reload will resync. */ }
                // Mine cooldown phải bắt đầu từ lúc trận gặp ngẫu nhiên kết thúc,
                // trước khi EveFlowController có thể complete/remove EventSession.
                get<MineEncounterSystem>().onBattleEnd(playerId)
                get<EveFlowController>().resumeAfterBattle(session, playerId, playerWin)
                get<DungeonSystem>().settleAfterBattle(session, playerWin)?.let { settlement ->
                    get<DungeonHandler>().sendDungeonResult(session, settlement)
                }
            }
            bm.onWeeklyDungeonBattleEndCallback = { session, dungeonId, playerWin, _ ->
                get<DungeonSystem>().settleWeeklyBattle(session, dungeonId, playerWin)?.let { settlement ->
                    get<DungeonHandler>().sendDungeonResult(session, settlement)
                }
            }
        }
    }
    single {
        PvpBotMatchmakingSystem(
            get<BattleManager>(), get<WorldManager>(), get<PartySystem>(),
            get<ResponseSender>(), get<CoroutineScope>(),
        )
    }
    single {
        WorldSimBotSystem(
            get<WorldManager>(), get<CoroutineScope>(),
            get<GameDataManager>().itemDefs, get<GameDataManager>().eveData,
            get<GameDataManager>().mapWalkGrids,
            get<GameDataManager>().mountDefs, get<GameDataManager>().npcDefs,
            get<PartySystem>(), get<BattleManager>(),
        ).also { worldBots ->
            get<BattleManager>().worldPartyBotProvider = worldBots::battleProfilesFor
        }
    }
    single { LoginService(get(), get(), get(), get(), get(), get<PointSystem>(), get<PlayerCurrencySystem>(), get(), get(), get<EveEventStateMachine>(), get(), get(), get(), get(), get(), get<AstrolabeSystem>(), get<SoulSystem>(), get(), get<SkillRepository>(), get<RoleCountSystem>(), get<FlyPointSystem>(), get<MailSystem>(), get<FriendSystem>(), get<GameDataManager>().eveData, get<GameDataManager>().itemDefs, get<JiugonggeSystem>(), get<ActivityScheduleSystem>(), get<DailyProgressSystem>(), get<CollectionSystem>(), get<GameDataManager>().sacredEquipPrimaryDefs, get<MessageBoardRepository>(), get<MallQuotaSystem>()) }

    // Hệ thống sự kiện Eve (9 dịch vụ con sau khi tách)
    single {
        EveStateBuilder(
            questSystem = get(),
            inventorySystem = get(),
            petSystem = get(),
            stateMachine = get<EveEventStateMachine>(),
            playerStateManager = get(),
            markDefs = get<GameDataManager>().markDefs,
            roleCountSystem = get(),
            skillSystem = get(),
            moneySystem = get(),
            questRepository = get(),
            pointSystem = get<PointSystem>(),
            partySystem = get<PartySystem>(),
            sceneSetDefs = get<GameDataManager>().sceneSetDefs,
            activityTimeDefs = get<GameDataManager>().activityTimeDefs,
            markStepMissionIds = get<GameDataManager>().markStepMissionIds,
        )
    }
    single { EveConditionResolver(get<GameDataManager>().eveData) }
    single { EveAutoChainEngine(get<EveEventStateMachine>(), get(), get(), get<GameDataManager>().eveData) }
    single {
        EveResultExecutor(get(), get(), get(), get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().markDefs, get<GameDataManager>().markFlagToGuideId, get<GameDataManager>().eveData, get<MountSystem>(), get<AchievementSystem>(), get<BitFlagSystem>(), get<RoleCountSystem>(), get<DailyProgressSystem>(), get<PointSystem>(), get<CollectionSystem>(), get<PlayerStateManager>(), get<CharacterRepository>(), get<GameDataManager>().markStepMissionIds).also { executor ->
            executor.onMissionStarted = { session, missionId ->
                PvpBotMode.fromRegistrationMission(missionId)?.let { mode ->
                    get<PvpBotMatchmakingSystem>().register(session, mode)
                }
            }
        }
    }
    single { EveDoorWarpService(get(), get(), get(), get<EveEventStateMachine>(), get<GameDataManager>().eveData) }
    single { EveBattleService(get(), get<EveEventStateMachine>(), get(), get<GameDataManager>().mapTerrains, get<GameDataManager>().eveData) }
    single { NpcShopManager() }
    single {
        EveFlowController(get(), get<EveEventStateMachine>(), get(), get(), get(), get(), get(), get(), get(), get(), get<GameDataManager>().eveData, get()).also { flow ->
            flow.onEventCompleted = { session ->
                get<DungeonSystem>().settleAfterEvent(session)?.let { settlement ->
                    get<DungeonHandler>().sendDungeonResult(session, settlement)
                }
            }
            flow.onVsRegistration = { session ->
                get<PvpBotMatchmakingSystem>().register(session, PvpBotMode.VS)
            }
        }
    }
    single {
        EveTriggerRouter(
            get<EveEventStateMachine>(), get(), get(), get(), get(),
            get<GameDataManager>().eveData, get<AdminBypassPolicy>()
        )
    }
    single {
        MineEncounterSystem(
            get<GameDataManager>().eveData,
            get<EveEventStateMachine>(),
            get<EveTriggerRouter>(),
            get<PlayerStateManager>()
        )
    }

    // Lưu tự động định kỳ (mỗi 3 phút lưu dữ liệu tất cả người chơi trực tuyến)
    single { AutoSaveService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get<QuestSystem>(), get<ResponseSender>()) }

    // Bộ xử lý lệnh GM
    single {
        GmCommandProcessor(
            get(), get(), get(), get(), get<PointSystem>(), get(), get(), get(), get(),
            get<GameDataManager>().itemDefs, get<GameDataManager>().eveData,
            get(), get<GameDataManager>().npcDefs, get(), get(), get(), get(), get(),
            get<GameDataManager>().warpDefs, get<GameDataManager>().markDefs, get<CollectionSystem>()
        )
    }

    // Handler
    single { KeepaliveHandler() }
    single { LoginHandler(get(), get(), get(), get(), get()) }
    single { CreateCharHandler(get(), get()) }
    single { SelectCharHandler(get(), get(), get<PokeSystem>()) }
    single { DisconnectHandler(get(), get(), get(), get(), get<PlayerCurrencySystem>(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get<PvpBotMatchmakingSystem>()) }
    single { MoveHandler(get(), get(), get(), get<MineEncounterSystem>()) }
    single { ChatHandler(get(), get(), get()) }
    single { SceneHandler(get(), get()) }
    single { RelocateHandler(get(), get(), get(), get<LimitTimeDungeonSystem>(), get<DungeonSystem>(), get<EveTriggerRouter>(), get<ElfHandler>()) }
    single { NpcEventHandler(get<EveTriggerRouter>(), get<EveFlowController>()) }
    single { ItemHandler(get(), get(), get(), get(), get(), get<GameDataManager>().itemDefs, get(), get<PlayerStateManager>(), get<StatSystem>(), get(), get<FlyPointSystem>(), get<CharacterRepository>(), get<PetEquipSystem>(), get<QuestSystem>(), get<SkillSystem>(), get<DailyProgressSystem>(), get<PetSystem>(), get<GameDataManager>().npcDefs, get<RoleCountSystem>(), get<CollectionSystem>(), get<AstrolabeSystem>(), get<GameDataManager>().sacredEquipPrimaryDefs, get<CompoundSystem>(), get<SoulSystem>(), get<OfflineSoulGenerationPolicy>(), get<SoulResponseSender>(), get<PlayerCurrencySystem>(), get<ElfTokenUnlockService>(), get<ElfRepository>(), get<ElfHandler>()) }
    single { NpcTradeHandler(get(), get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { BankHandler(get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { StorageHandler(get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { TradeHandler(get(), get<CollectionSystem>()) }
    single { BattleManageHandler(get(), get(), get()) }
    single { BattleCommandHandler(get()) }
    single { PlayerStatusHandler(get(), get(), get(), get(), get<GameDataManager>().titleDefs) }
    single { SkillUpgradeHandler(get(), get(), get(), get(), get<DailyProgressSystem>(), get<PetSystem>()) }
    single { PartyHandler(get(), get(), get(), get<DailyProgressSystem>(), get<DungeonSystem>(), get<DungeonRoomRegistry>(), get<WorldSimBotSystem>()) }
    single { FollowNpcHandler(get(), get(), get()) }
    single { FollowNpcDeployHandler(get(), get(), get<CharacterRepository>(), get<DailyProgressSystem>()) }
    single { PkSettingHandler(get()) }
    single { StatHandler(get(), get(), get(), get<StatSystem>(), get(), get<SkillRepository>(), get<DailyProgressSystem>()) }
    single { AstrolabeHandler(get<AstrolabeSystem>(), get<ResponseSender>()) }
    single { SoulHandler(get<SoulSystem>(), get<PetSystem>(), get<GameDataManager>().npcDefs, get<ResponseSender>()) }
    single { ElfHandler(get<ElfRepository>(), get<BitFlagSystem>(), get<InventorySystem>(), get<ResponseSender>(), get<GameDataManager>().itemDefs, get<ElfCatalog>(), get<WorldManager>()) }
    single { ElfTokenUnlockService(get<BitFlagSystem>(), get<InventorySystem>(), get<ElfCatalog>(), get<ResponseSender>(), get<GameDataManager>().itemDefs) }
    single { FurnaceResponseSender() }
    single { GodskinResponseSender() }
    single { GodskinHandler(get<MythEnchantSystem>(), get<MythUpgradeSystem>(), get<GodskinResponseSender>(), get<ResponseSender>()) }
    single { MountHandler(get(), get(), get()) }
    single { HotkeyHandler(get(), get()) }
    single { InnResponseSender() }
    single { InnHandler(get(), get(), get(), get(), get(), get()) }
    single { WarriorRepositoryHandler(get(), get(), get()) }
    single { MacroBoxHandler(get(), get(), get(), get()) }
    single {
        TeleportHandler(
            get(), get(), get(), get(), get(),
            get<GameDataManager>().warpDefs,
            get<GameDataManager>().markDefs,
            get<AdminBypassPolicy>()
        )
    }
    single { WorldAtlasHandler(get(), get(), get(), get<GameDataManager>().mapWalkGrids) }
    single {
        LimitTimeDungeonHandler(
            get<LimitTimeDungeonSystem>(), get<RoleCountSystem>(), get<PartySystem>(),
            get<EveEventStateMachine>(), get<SpecialSceneTeleportService>()
        )
    }
    single {
        DungeonHandler(
            get<DungeonSystem>(),
            get<DungeonRoomRegistry>(),
            get<WorldManager>(),
            get<BattleManager>(),
            get<CoroutineScope>(),
            pvpBotMatchmakingSystem = get<PvpBotMatchmakingSystem>(),
        )
    }
    single { AchievementHandler(get(), get(), get()) }
    single { EmoteHandler(get(), get()) }
    single {
        DispatchHandler(
            get(),
            get(),
            get<ResponseSender>(),
            get<MoneySystem>(),
            get<PointSystem>(),
        )
    }
    single {
        WelfareHandler(
            get<GameDataManager>().loginAwardDefs, get<WelfareStateRepository>(), get<RoleCountSystem>(),
            get<BitFlagSystem>(), get<InventorySystem>(), get<PointSystem>(), get<MoneySystem>(),
            get<PlayerCurrencySystem>(), get<ResponseSender>(),
        )
    }
    single { MailHandler(get()) }
    single { LeaderboardHandler(get()) }
    single {
        FurnaceHandler(
            get<FurnaceSystem>(),
            get<FurnaceResponseSender>(),
            get<ResponseSender>(),
            get<InventorySystem>(),
        )
    }
    single { MessageBoardHandler(get<RoleCountSystem>(), get<ResponseSender>(), get<MessageBoardRepository>(), get<SessionManager>()) }
    single { FriendHandler(get()) }
    single { HouseBoardHandler(get(), get(), get<ResponseSender>(), get<PointSystem>(), get<WorldManager>()) }
    single { CollectionHandler(get<CollectionSystem>(), get<ResponseSender>(), get<MountSystem>()) }
    single {
        UISellHandler(
            get(),
            get(),
            get<PointSystem>(),
            get<InventorySystem>(),
            get(),
            get(),
            get(),
            get(),
            get<RoleCountSystem>(),
            get<SkillRepository>(),
            get<PlayerStateManager>(),
            get<SkillSystem>(),
            get<SlotMachineSystem>(),
            get<MoneySystem>(),
            get<DispatchSystem>(),
            get<FurnaceSystem>(),
            get<FurnaceResponseSender>(),
            get<PokeSystem>(),
            get<ExpSlotSystem>(),
        )
    }
    single { ExpSlotHandler(get<ExpSlotSystem>()) }
    single { PetUpgradeHandler(get(), get()) }
    single { PetRebornHandler(get(), get(), get()) }
    single { JiugonggeHandler(get()) }
    single {
        EternalDungeonHandler(
            get<RoleCountSystem>(),
            get<GameDataManager>().eternalDungeonLayerDefs,
            get<GameDataManager>().eternalDungeonPrizeDefs,
        )
    }
    single { ActivityHandler(get<ActivityScheduleSystem>(), get<AdminBypassPolicy>(), get<SpecialSceneTeleportService>(),
        get<GameDataManager>().activityTimeDefs, get<GameDataManager>().eveData, get<BattlePassSystem>(),
        get<RoleCountSystem>(), get<InventorySystem>(), get<ResponseSender>()) }
    single { ActivityModelHandler() }
    single { MallHandler(get(), get(), get<PointSystem>(), get<MoneySystem>(), get<PlayerCurrencySystem>(), get<ResponseSender>(), get<GameDataManager>().goodsSaleDefs, get<MallQuotaSystem>()) }
    single { SlotMachineHandler(get<SlotMachineSystem>(), get<ResponseSender>()) }
    single { PokeHandler(get<PokeSystem>(), get<ResponseSender>()) }
    single {
        MiningHandler(
            get<MiningSystem>(),
            get<SpecialSceneTeleportService>(),
            get<PartySystem>(),
            get<WorldManager>(),
        )
    }
}

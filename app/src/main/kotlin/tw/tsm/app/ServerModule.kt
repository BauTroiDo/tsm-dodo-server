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
import tw.tsm.app.response.InnResponseSender
import tw.tsm.data.GameDataManager
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.interfaces.repositories.AccountRepository
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.InventoryRepository
import tw.tsm.domain.interfaces.repositories.BitFlagRepository
import tw.tsm.domain.interfaces.repositories.QuestRepository
import tw.tsm.gamelogic.systems.*
import tw.tsm.network.NettyGameServer
import tw.tsm.network.PacketDispatcher
import tw.tsm.network.SessionManager
import tw.tsm.persistence.repositories.AccountRepositoryImpl
import tw.tsm.persistence.repositories.CharacterRepositoryImpl
import tw.tsm.persistence.repositories.InventoryRepositoryImpl
import tw.tsm.persistence.repositories.BitFlagRepositoryImpl
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.domain.interfaces.repositories.MountRepository
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.interfaces.repositories.FlyPointRepository
import tw.tsm.domain.interfaces.repositories.RoleCountRepository
import tw.tsm.domain.interfaces.repositories.DispatchRepository
import tw.tsm.domain.interfaces.repositories.FriendRepository
import tw.tsm.domain.interfaces.repositories.MailRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository
import tw.tsm.persistence.repositories.UiSellCountRepositoryImpl
import tw.tsm.persistence.repositories.DispatchRepositoryImpl
import tw.tsm.persistence.repositories.FriendRepositoryImpl
import tw.tsm.persistence.repositories.MailRepositoryImpl
import tw.tsm.persistence.repositories.HotkeyRepositoryImpl
import tw.tsm.persistence.repositories.MountRepositoryImpl
import tw.tsm.persistence.repositories.FlyPointRepositoryImpl
import tw.tsm.persistence.repositories.PetRepositoryImpl
import tw.tsm.persistence.repositories.QuestRepositoryImpl
import tw.tsm.persistence.repositories.RoleCountRepositoryImpl
import tw.tsm.persistence.repositories.SkillRepositoryImpl

private val serverLogger = KotlinLogging.logger {}

/**
 * Koin DI 模組定義
 *
 * @param config 應用程式設定
 * @param gameDataManager 已初始化的遊戲資料管理器
 */
fun serverModule(config: Config, gameDataManager: GameDataManager) = module {
    // 共用 CoroutineScope
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // 網路層
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
            register(get<MountHandler>())
            register(get<HotkeyHandler>())
            register(get<InnHandler>())
            register(get<WarriorRepositoryHandler>())
            register(get<MacroBoxHandler>())
            register(get<TeleportHandler>())
            register(get<AchievementHandler>())
            register(get<EmoteHandler>())
            register(get<DispatchHandler>())
            register(get<MailHandler>())
            register(get<LeaderboardHandler>())
            register(get<FriendHandler>())
            register(get<UISellHandler>())
            register(get<PetUpgradeHandler>())
            register(get<PetRebornHandler>())
            register(get<JiugonggeHandler>())
            register(get<ActivityModelHandler>())
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
        val friendSystem = get<FriendSystem>()
        val scope = get<CoroutineScope>()
        NettyGameServer(get(), get(), get()) { session ->
            // 斷線時取消進行中的交易
            tradeSystem.cancelTradeOnDisconnect(session.playerId)
            // 斷線時清理戰鬥
            battleManager.handleDisconnect(session.playerId)
            // 斷線時清理隊伍
            partySystem.handleDisconnect(session.playerId)
            // 斷線時清理事件狀態
            eveStateMachine.clear(session.roleId.toLong())
            // 斷線時清理機關盒狀態
            macroBoxSystem.cleanup(session.roleId.toLong())
            // 斷線時通知好友下線
            if (session.roleId > 0) {
                scope.launch {
                    try { friendSystem.handleDisconnect(session.roleId.toLong()) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線通知好友失敗" } }
                }
            }
            // 斷線時保存並清除運行時狀態
            if (session.roleId > 0) {
                val roleId = session.roleId.toLong()
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
                            serverLogger.error(e) { "異常斷線存檔失敗（角色狀態）：roleId=$roleId" }
                        }
                    }
                }
                playerStateManager.removePlayer(roleId)
            }

            // 先快照位置，再移除（leaveMap 會把 session.mapId 清為 0）
            val mapId = session.mapId
            val x = session.x
            val y = session.y

            worldManager.removePlayer(session)
            // 異常斷線時儲存角色位置/物品/金錢（非 suspend，用 scope.launch）
            // mapId > 0 檢查：避免 DisconnectHandler 已保存後，channelInactive 用 mapId=0 覆蓋
            if (session.roleId > 0) {
                val roleId = session.roleId.toLong()
                scope.launch {
                    // 每項獨立 try-catch，確保一項失敗不影響其他項保存
                    if (mapId > 0) {
                        try { characterRepository.updatePosition(roleId, mapId, x, y) }
                        catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（位置）：roleId=$roleId" } }
                    }
                    try { inventorySystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（物品）：roleId=$roleId" } }

                    try { moneySystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（金錢）：roleId=$roleId" } }

                    try { pointSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（元寶）：roleId=$roleId" } }

                    try { skillSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（技能）：roleId=$roleId" } }

                    try { petSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（武將）：roleId=$roleId" } }

                    try { mountSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（坐騎）：roleId=$roleId" } }

                    try { roleCountSystem.saveAndUnload(roleId) }
                    catch (e: Exception) { serverLogger.error(e) { "斷線存檔失敗（計數器）：roleId=$roleId" } }
                }
            }
        }
    }

    // Repository（domain 介面 → persistence 實作）
    single<AccountRepository> { AccountRepositoryImpl() }
    single<CharacterRepository> { CharacterRepositoryImpl() }
    single<InventoryRepository> { InventoryRepositoryImpl() }
    single<QuestRepository> { QuestRepositoryImpl() }
    single<BitFlagRepository> { BitFlagRepositoryImpl() }
    single<SkillRepository> { SkillRepositoryImpl() }
    single<PetRepository> { PetRepositoryImpl() }
    single<MountRepository> { MountRepositoryImpl() }
    single<HotkeyRepository> { HotkeyRepositoryImpl() }
    single<RoleCountRepository> { RoleCountRepositoryImpl() }
    single<FlyPointRepository> { FlyPointRepositoryImpl() }
    single<UiSellCountRepository> { UiSellCountRepositoryImpl() }
    single<DispatchRepository> { DispatchRepositoryImpl() }
    single<MailRepository> { MailRepositoryImpl() }
    single<FriendRepository> { FriendRepositoryImpl() }

    // 遊戲資料
    single { gameDataManager }
    single { StatSystem(get<GameDataManager>().formulaParams) }
    single<StatCalculator> { get<StatSystem>() }

    // 遊戲邏輯
    single { WorldManager() }
    single { AuthSystem(get()) }
    single<ResponseSender> {
        val wm = get<WorldManager>()
        val rs = ResponseSenderImpl(wm)
        wm.responseSender = rs  // 打破循環依賴
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
    single { GroundItemManager() }
    single { PlayerStateManager() }
    single { ItemUseProcessor(get(), get<PetSystem>(), get<GameDataManager>().itemDefs, get<GameDataManager>().blissBagDefs) }
    single { EveEventStateMachine() }
    single { QuestSystem(get(), get<GameDataManager>().markDefs) }
    single { BitFlagSystem(get(), get<GameDataManager>().guideLastBitFlagId) }
    single { SkillSystem(get(), get(), get<GameDataManager>().skillDefs) }
    single { PetSkillSystem(get(), get<GameDataManager>().npcDefs, get<GameDataManager>().skillDefs) }
    single { PartySystem() }
    single {
        PetSystem(
            get(),
            get<StatSystem>(),
            get<GameDataManager>().npcDefs,
            get<GameDataManager>().itemDefs,
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
    single { DailyProgressSystem(get<RoleCountSystem>(), get<BitFlagSystem>(), get<ResponseSender>()) }
    single { FlyPointSystem(get(), get()) }
    single { UISellSystem(get<GameDataManager>().uiSellDefs, get<GameDataManager>().uiSellFormulaDefs, get(), get(), get()) }
    single { PetUpgradeSystem(get(), get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().npcUpgradeDefs) }
    single { PetRebornSystem(get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().itemDefs) }
    single { PetEquipSystem(get(), get(), get<GameDataManager>().itemDefs) }
    single {
        AchievementSystem(
            get(), get(), get(), get(), get(), get(),
            get<GameDataManager>().achievementDefs,
        )
    }
    single { TradeSystem(get(), get(), get(), get(), get<GameDataManager>().itemDefs, get(), get(), get<StatSystem>()) }
    single { DispatchSystem(get(), get(), get<GameDataManager>().dispatchDefs, get<GameDataManager>().dispatchBonusDefs) }
    single { MailSystem(get(), get(), get()) }
    single { FriendSystem(get(), get(), get(), get(), get<RoleCountSystem>()) }
    single {
        LeaderboardSystem(get(), get<GameDataManager>().leaderboardDefs, get(), get()).also {
            it.startPeriodicRefresh()
        }
    }
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
        BattleManager(get(), get<GameDataManager>().skillDefs, get<GameDataManager>().npcDefs, get(), get(), get<PetSystem>(), get<MoneySystem>(), get<StatSystem>(), get(), get<GameDataManager>().npcDropTable, get<SkillSystem>(), get<MacroBoxSystem>(), get<GameDataManager>().itemDefs, get<SkillRepository>(), get<CharacterRepository>(), get<GameDataManager>().npcUpgradeDefs, get<GameDataManager>().sceneSetDefs, get<DailyProgressSystem>()).also { bm ->
            // 戰鬥結束回呼 → EveFlowController 恢復事件流程
            bm.onBattleEndCallback = { session, playerId, playerWin ->
                get<EveFlowController>().resumeAfterBattle(session, playerId, playerWin)
            }
        }
    }
    single { LoginService(get(), get(), get(), get(), get(), get<PointSystem>(), get(), get(), get<EveEventStateMachine>(), get(), get(), get(), get(), get(), get(), get<SkillRepository>(), get<RoleCountSystem>(), get<FlyPointSystem>(), get<MailSystem>(), get<FriendSystem>(), get<GameDataManager>().eveData, get<GameDataManager>().itemDefs, get<JiugonggeSystem>(), get<DailyProgressSystem>()) }

    // Eve 事件系統（拆分後的 9 個子服務）
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
            pointSystem = get<PointSystem>()
        )
    }
    single { EveConditionResolver(get<GameDataManager>().eveData) }
    single { EveAutoChainEngine(get<EveEventStateMachine>(), get(), get(), get<GameDataManager>().eveData) }
    single { EveResultExecutor(get(), get(), get(), get(), get(), get(), get<GameDataManager>().npcDefs, get<GameDataManager>().markDefs, get<GameDataManager>().markFlagToGuideId, get<GameDataManager>().eveData, get<MountSystem>(), get<AchievementSystem>(), get<BitFlagSystem>(), get<RoleCountSystem>(), get<DailyProgressSystem>(), get<PointSystem>()) }
    single { EveDoorWarpService(get(), get(), get(), get<EveEventStateMachine>(), get<GameDataManager>().eveData) }
    single { EveBattleService(get(), get<EveEventStateMachine>(), get(), get<GameDataManager>().mapTerrains, get<GameDataManager>().eveData) }
    single { NpcShopManager() }
    single { EveFlowController(get(), get<EveEventStateMachine>(), get(), get(), get(), get(), get(), get(), get(), get(), get<GameDataManager>().eveData, get()) }
    single { EveTriggerRouter(get<EveEventStateMachine>(), get(), get(), get(), get(), get<GameDataManager>().eveData) }
    single { MineEncounterSystem(get<GameDataManager>().eveData, get<EveEventStateMachine>(), get<EveTriggerRouter>()) }

    // 定期自動存檔（每 3 分鐘保存所有在線玩家資料）
    single { AutoSaveService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // GM 指令處理器
    single {
        GmCommandProcessor(
            get(), get(), get(), get(), get<PointSystem>(), get(), get(), get(), get(),
            get<GameDataManager>().itemDefs, get<GameDataManager>().eveData,
            get(), get<GameDataManager>().npcDefs, get(), get(), get(), get(), get(),
            get<GameDataManager>().warpDefs, get<GameDataManager>().markDefs
        )
    }

    // Handler
    single { KeepaliveHandler() }
    single { LoginHandler(get(), get(), get(), get(), get()) }
    single { CreateCharHandler(get(), get()) }
    single { SelectCharHandler(get(), get()) }
    single { DisconnectHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { MoveHandler(get(), get(), get(), get<MineEncounterSystem>()) }
    single { ChatHandler(get(), get(), get()) }
    single { SceneHandler(get(), get()) }
    single { RelocateHandler(get(), get()) }
    single { NpcEventHandler(get<EveTriggerRouter>(), get<EveFlowController>()) }
    single { ItemHandler(get(), get(), get(), get(), get(), get<GameDataManager>().itemDefs, get(), get<PlayerStateManager>(), get<StatSystem>(), get(), get<FlyPointSystem>(), get<CharacterRepository>(), get<PetEquipSystem>(), get<QuestSystem>(), get<SkillSystem>(), get<DailyProgressSystem>()) }
    single { NpcTradeHandler(get(), get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { BankHandler(get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { StorageHandler(get(), get(), get<GameDataManager>().itemDefs, get()) }
    single { TradeHandler(get()) }
    single { BattleManageHandler(get(), get(), get()) }
    single { BattleCommandHandler(get()) }
    single { PlayerStatusHandler(get(), get(), get(), get()) }
    single { SkillUpgradeHandler(get(), get(), get(), get(), get<DailyProgressSystem>()) }
    single { PartyHandler(get(), get(), get(), get<DailyProgressSystem>()) }
    single { FollowNpcHandler(get(), get(), get()) }
    single { FollowNpcDeployHandler(get(), get(), get<DailyProgressSystem>()) }
    single { PkSettingHandler(get()) }
    single { StatHandler(get(), get(), get(), get<StatSystem>(), get(), get<SkillRepository>(), get<DailyProgressSystem>()) }
    single { MountHandler(get(), get(), get()) }
    single { HotkeyHandler(get(), get()) }
    single { InnResponseSender() }
    single { InnHandler(get(), get(), get(), get(), get()) }
    single { WarriorRepositoryHandler(get(), get(), get()) }
    single { MacroBoxHandler(get(), get(), get(), get()) }
    single {
        TeleportHandler(
            get(), get(), get(), get(), get(),
            get<GameDataManager>().warpDefs,
            get<GameDataManager>().markDefs
        )
    }
    single { AchievementHandler(get(), get(), get()) }
    single { EmoteHandler(get(), get()) }
    single { DispatchHandler(get()) }
    single { MailHandler(get()) }
    single { LeaderboardHandler(get()) }
    single { FriendHandler(get()) }
    single { UISellHandler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get<SkillRepository>(), get<PlayerStateManager>(), get<SkillSystem>()) }
    single { PetUpgradeHandler(get(), get()) }
    single { PetRebornHandler(get(), get(), get()) }
    single { JiugonggeHandler(get()) }
    single { ActivityModelHandler() }
}

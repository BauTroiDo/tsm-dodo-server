package tw.tsm.data

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.loaders.*
import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.*
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * 遊戲靜態資料管理器 — 對應 `DataManager.lua`
 *
 * 在伺服器啟動時呼叫 [initialize]，依序載入各 .dat 檔案。
 * 作為 Koin 單例注入，提供各系統查詢靜態資料。
 *
 * @param basePath 資料根目錄（包含 `Data/` 子目錄）
 * @param gamedataPath 伺服器端 gamedata 目錄（包含 npc_drops.json 等設定）
 */
class GameDataManager(
    private val basePath: Path,
    private val gamedataPath: Path = basePath
) {

    /** 公式參數（Calculator.lua 用） */
    lateinit var formulaParams: FormulaParams
        private set

    /** 傳送門（key = 0-based index） */
    var warpDefs: Map<Int, WarpDef> = emptyMap()
        private set

    /** 場景設定（key = 場景 ID） */
    var sceneSetDefs: Map<Int, SceneSetDef> = emptyMap()
        private set

    /** NPC 定義（key = NPC ID） */
    var npcDefs: Map<Int, NpcDef> = emptyMap()
        private set

    /** 技能定義（key = 技能 ID） */
    var skillDefs: Map<Int, SkillDef> = emptyMap()
        private set

    /** 物品定義（key = 物品 ID） */
    var itemDefs: Map<Int, ItemDef> = emptyMap()
        private set

    /** NPC 事件資料（key = 場景 ID） */
    var eveData: Map<Int, SceneEveData> = emptyMap()
        private set

    /** 任務標記定義（key = 任務 ID） */
    var markDefs: Map<Int, MarkDef> = emptyMap()
        private set

    /** bitId → missionId 反向映射（完成標記查詢用） */
    var bitToMissionId: Map<Int, Int> = emptyMap()
        private set

    /** 福袋定義（key = 福袋物品 ID） */
    var blissBagDefs: Map<Int, BlissBagDef> = emptyMap()
        private set

    /** Mall/Doro goods from the Mobile DVT IOS_GS_C.dat in file order. */
    var goodsSaleDefs: List<GoodsSaleDef> = emptyList()
        private set

    /** Doro reward pools from Mobile DVT IOS_BlissBag_C.dat. */
    var doroBlissBagDefs: Map<Int, BlissBagDef> = emptyMap()
        private set

    /** 地圖地質資料（key = mapId, value = geolBaseAtt）— 用於戰鬥背景 */
    var mapTerrains: Map<Int, Int> = emptyMap()
        private set

    /** 教學引導映射（guideId → 最後一步的 bitFlagId）— C:005-008 用 */
    var guideLastBitFlagId: Map<Int, Int> = emptyMap()
        private set

    /** 教學觸發映射（markFlagId → guideId）— S:024-005 觸發 UITeach 用 */
    var markFlagToGuideId: Map<Int, Int> = emptyMap()
        private set

    /** 教學觸發映射（guideId → markFlagId）— 正向查詢用 */
    var guideMarkFlagIds: Map<Int, Int> = emptyMap()
        private set

    /** NPC 掉落表（key = NPC ID） */
    var npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap()
        private set

    /** 坐騎定義（key = NPC ID） */
    var mountDefs: Map<Int, MountDef> = emptyMap()
        private set

    /** 坐騎成長定義（key = 等級） */
    var mountGrowDefs: Map<Int, MountGrowDef> = emptyMap()
        private set

    /** 成就定義（key = 成就 ID） */
    var achievementDefs: Map<Int, AchievementDef> = emptyMap()
        private set

    /** 派遣任務定義（key = mainKind → subKind → DispatchDef） */
    var dispatchDefs: Map<Int, Map<Int, DispatchDef>> = emptyMap()
        private set

    /** 派遣獎勵加成效果（key = 效果 ID） */
    var dispatchBonusDefs: Map<Int, DispatchBonusDef> = emptyMap()
        private set

    /** 排行榜定義（key = 排行榜 ID） */
    var leaderboardDefs: Map<Int, LeaderboardDef> = emptyMap()
        private set

    /** UI 介面消費定義（key = sellId） */
    var uiSellDefs: Map<Int, UISellDef> = emptyMap()
        private set

    /** 武將強化定義（key = 目標星等 1~15） */
    var npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap()
        private set

    /** UI 介面消費費用公式（key = sellId） */
    var uiSellFormulaDefs: Map<Int, UISellFormulaDef> = emptyMap()
        private set

    /** 九宮格盤面定義（key = grid ID） */
    var jiugonggeInfoDefs: Map<Int, JiugonggeInfoDef> = emptyMap()
        private set

    /** 九宮格任務定義（key = mission ID） */
    var jiugonggeMissionDefs: Map<Int, JiugonggeMissionDef> = emptyMap()
        private set

    /** 活動時間定義（key = activity ID） */
    var activityTimeDefs: Map<Int, ActivityTimeDef> = emptyMap()
        private set

    /**
     * 初始化：依序載入各 .dat 檔案
     *
     * 載入順序對照 `DataManager.LoadResourcePacked()`。
     * 每個檔案載入失敗只會 log 警告，不會中斷啟動。
     */
    fun initialize() {
        val dataDir = basePath.resolve("Data")
        val dataIosDir = listOf(
            basePath.resolve("Data-ios"),
            Path.of("File.dat", "Data-ios"),
        ).firstOrNull { Files.isDirectory(it) } ?: basePath.resolve("Data-ios")
        if (!Files.isDirectory(dataDir)) {
            logger.warn { "資料目錄不存在：$dataDir — 跳過 .dat 載入" }
            return
        }

        val startTime = System.currentTimeMillis()

        // Formula（最優先，StatSystem 需要）
        loadFile(dataDir, "Formula.dat") { bytes ->
            formulaParams = FormulaDatLoader.load(bytes)
            logger.info { "Formula 載入完成：baseHp=${formulaParams.baseHp}, baseSp=${formulaParams.baseSp}, w35=${formulaParams.w35}, w36=${formulaParams.w36}" }
        }

        // Item
        loadFile(dataDir, "Item_C.dat") { bytes ->
            itemDefs = ItemDatLoader.load(bytes)
            logger.info { "Item 載入完成：${itemDefs.size} 筆" }
        }

        // SceneSet
        loadFile(dataDir, "SceneSet_C.dat") { bytes ->
            sceneSetDefs = SceneSetDatLoader.load(bytes)
            logger.info { "SceneSet 載入完成：${sceneSetDefs.size} 筆" }
        }

        // Npc
        loadFile(dataDir, "Npc_C.dat") { bytes ->
            npcDefs = NpcDatLoader.load(bytes)
            logger.info { "Npc 載入完成：${npcDefs.size} 筆" }
        }

        // Skill
        loadFile(dataDir, "Skill_C.dat") { bytes ->
            skillDefs = SkillDatLoader.load(bytes)
            logger.info { "Skill 載入完成：${skillDefs.size} 筆" }
        }

        // Warp
        loadFile(dataDir, "Warp_C.dat") { bytes ->
            warpDefs = WarpDatLoader.load(bytes)
            logger.info { "Warp 載入完成：${warpDefs.size} 筆" }
        }

        // Mark（任務標記定義）
        loadFile(dataDir, "Mark_C.dat") { bytes ->
            val (marks, bitMap) = MarkDatLoader.load(bytes)
            markDefs = marks
            bitToMissionId = bitMap
            logger.info { "Mark 載入完成：${markDefs.size} 筆任務定義，${bitToMissionId.size} 筆完成標記映射" }
            // ── 診斷：教學相關任務的 bitId 映射 ──
            val diagMissions = listOf(12280, 12284, 12285, 12286, 12287, 12288, 10037)
            for (mid in diagMissions) {
                val def = markDefs[mid]
                logger.debug { "Mark 診斷：mission=$mid → ${def?.let { "bitId=${it.bitId} name=${it.name}" } ?: "不存在"}" }
            }
        }

        // TeachInfo（教學引導資料）
        loadFile(dataDir, "TeachInfo_C.dat") { bytes ->
            val teachResult = TeachInfoDatLoader.load(bytes)
            guideLastBitFlagId = teachResult.guideLastBitFlagId
            guideMarkFlagIds = teachResult.guideMarkFlagIds
            // 建立反向映射：markFlagId → guideId
            markFlagToGuideId = teachResult.guideMarkFlagIds.entries
                .associate { (guideId, markFlagId) -> markFlagId to guideId }
            logger.info { "TeachInfo 載入完成：${guideLastBitFlagId.size} 筆 bitFlagId，${guideMarkFlagIds.size} 筆 markFlagId" }
            // ── 診斷：交叉比對 guide markFlagId 與 Mark_C.dat bitId ──
            for ((guideId, markFlagId) in guideMarkFlagIds) {
                val missionId = bitToMissionId[markFlagId]
                logger.debug { "教學交叉：guide=$guideId markFlagId=$markFlagId → ${
                    missionId?.let { "mission=$it (${markDefs[it]?.name})" } ?: "無對應任務（需主動觸發）"
                }" }
            }
        }

        // BlissBag（一般開袋邏輯沿用目前 server data）
        loadFile(dataDir, "BlissBag_C.dat") { bytes ->
            blissBagDefs = BlissBagDatLoader.load(bytes)
            logger.info { "BlissBag 載入完成：${blissBagDefs.size} 筆福袋定義" }
        }

        // Mall/Doro 必須跟 Mobile DVT client 同一 bộ data:
        // Lua DataManager.OnLoadGoodsSaleData / OnLoadBlissBagData 讀 IOS_*.
        // Runtime basePath 的 Data/GS_C.dat có thể là bộ CDN khác (production 2.4.76 chỉ có 8 Doro),
        // trong khi client đang gửi ID theo IOS_GS_C.dat (32 Doro). Ưu tiên Data-ios.
        val iosGs = dataIosDir.resolve("IOS_GS_C.dat")
        val iosBliss = dataIosDir.resolve("IOS_BlissBag_C.dat")

        if (Files.exists(iosGs)) {
            try {
                goodsSaleDefs = GoodsSaleDatLoader.load(Files.readAllBytes(iosGs))
                logger.info {
                    "IOS_GS 載入完成：${goodsSaleDefs.size} 筆，其中 Doro=${goodsSaleDefs.count { it.storeKind == 5 }} source=$iosGs"
                }
            } catch (e: Exception) {
                logger.error(e) { "載入 IOS_GS_C.dat 失敗：$iosGs" }
            }
        } else {
            loadFile(dataDir, "GS_C.dat") { bytes ->
                goodsSaleDefs = GoodsSaleDatLoader.load(bytes)
                logger.warn {
                    "IOS_GS_C.dat 不存在，fallback GS_C.dat：${goodsSaleDefs.size} 筆，其中 Doro=${goodsSaleDefs.count { it.storeKind == 5 }}"
                }
            }
        }

        if (Files.exists(iosBliss)) {
            try {
                doroBlissBagDefs = BlissBagDatLoader.load(Files.readAllBytes(iosBliss))
                logger.info {
                    "IOS_BlissBag 載入完成：${doroBlissBagDefs.size} 筆 Doro pool source=$iosBliss"
                }
            } catch (e: Exception) {
                logger.error(e) { "載入 IOS_BlissBag_C.dat 失敗：$iosBliss" }
            }
        }
        if (doroBlissBagDefs.isEmpty()) {
            doroBlissBagDefs = blissBagDefs
            logger.warn { "Doro fallback dùng BlissBag_C.dat vì IOS_BlissBag_C.dat không khả dụng" }
        }

        // Ground.mmg（地圖地質資料，從 CompreseData/ 目錄讀取）
        val groundFile = basePath.resolve("CompreseData").resolve("Ground.mmg")
        if (Files.exists(groundFile)) {
            try {
                val bytes = Files.readAllBytes(groundFile)
                mapTerrains = GroundMmgLoader.load(bytes)
                logger.info { "Ground 載入完成：${mapTerrains.size} 筆地圖地質資料" }
            } catch (e: Exception) {
                logger.error(e) { "載入 Ground.mmg 失敗" }
            }
        } else {
            logger.warn { "Ground.mmg 不存在：$groundFile — 跳過" }
        }

        // Eve.emg（從 CompreseData/ 目錄讀取）
        val eveFile = basePath.resolve("CompreseData").resolve("Eve.emg")
        if (Files.exists(eveFile)) {
            try {
                val bytes = Files.readAllBytes(eveFile)
                eveData = EveDataLoader.load(bytes)
                val npcCount = eveData.values.sumOf { it.npcs.size }
                val eventCount = eveData.values.sumOf { it.npcEvents.size }
                val sceneInfoCount = eveData.values.sumOf { it.sceneInfos.size }
                logger.info { "Eve 載入完成：${eveData.size} 場景，$npcCount NPC 擺放，$eventCount 事件定義，$sceneInfoCount 場景資訊" }
            } catch (e: Exception) {
                logger.error(e) { "載入 Eve.emg 失敗" }
            }
        } else {
            logger.warn { "Eve.emg 不存在：$eveFile — 跳過" }
        }

        // Mounts / MountsGrow 必須 bám đúng Mobile DVT Lua DataManager:
        // Data/IOS_Mounts_C.dat + IOS_MountsGrow_C.dat.
        // CDN Data/MountsGrow_C.dat hiện là một bộ khác, mở rộng tới Lv15 trong khi
        // Mobile DVT IOS table dừng ở Lv10.
        val iosMounts = dataIosDir.resolve("IOS_Mounts_C.dat")
        val iosMountsGrow = dataIosDir.resolve("IOS_MountsGrow_C.dat")

        if (Files.exists(iosMounts)) {
            mountDefs = MountDatLoader.load(Files.readAllBytes(iosMounts))
            logger.info { "IOS_Mounts 載入完成：${mountDefs.size} 筆 source=$iosMounts" }
        } else {
            loadFile(dataDir, "Mounts_C.dat") { bytes ->
                mountDefs = MountDatLoader.load(bytes)
                logger.warn { "IOS_Mounts_C.dat 不存在，fallback Mounts_C.dat：${mountDefs.size} 筆" }
            }
        }

        if (Files.exists(iosMountsGrow)) {
            mountGrowDefs = MountGrowDatLoader.load(Files.readAllBytes(iosMountsGrow))
            logger.info {
                "IOS_MountsGrow 載入完成：${mountGrowDefs.size} 筆，maxLv=${mountGrowDefs.keys.maxOrNull() ?: 0} source=$iosMountsGrow"
            }
        } else {
            loadFile(dataDir, "MountsGrow_C.dat") { bytes ->
                mountGrowDefs = MountGrowDatLoader.load(bytes)
                logger.warn {
                    "IOS_MountsGrow_C.dat 不存在，fallback MountsGrow_C.dat：${mountGrowDefs.size} 筆"
                }
            }
        }

        // Achievement（成就定義）
        loadFile(dataDir, "AchievementData_C.dat") { bytes ->
            achievementDefs = AchievementDatLoader.load(bytes)
            logger.info { "Achievement 載入完成：${achievementDefs.size} 筆" }
        }

        // ActivityTime — client UIActivity/UIJiugongge relies on serverStartTime/serverCloseTime.
        loadFile(dataDir, "ActivityTime_C.dat") { bytes ->
            activityTimeDefs = JiugonggeDatLoader.loadActivityTimes(bytes)
            logger.info { "ActivityTime 載入完成：${activityTimeDefs.size} 筆" }
        }

        // Jiugongge static data. DVT DataManager.lua explicitly reads the IOS_ files.
        loadFile(dataIosDir, "IOS_JiugonggeInfo_C.dat") { bytes ->
            jiugonggeInfoDefs = JiugonggeDatLoader.loadInfo(bytes)
            logger.info { "JiugonggeInfo 載入完成：${jiugonggeInfoDefs.size} 筆" }
        }
        loadFile(dataIosDir, "IOS_JiugonggeMission_C.dat") { bytes ->
            jiugonggeMissionDefs = JiugonggeDatLoader.loadMissions(bytes)
            logger.info { "JiugonggeMission 載入完成：${jiugonggeMissionDefs.size} 筆" }
        }

        // Dispatch（派遣任務定義）
        loadFile(dataDir, "Dispatch_C.dat") { bytes ->
            dispatchDefs = DispatchDatLoader.loadDispatch(bytes)
            val total = dispatchDefs.values.sumOf { it.size }
            logger.info { "Dispatch 載入完成：$total 筆" }
        }

        // DispatchBonus（派遣獎勵加成效果）
        loadFile(dataDir, "DispatchBonus_C.dat") { bytes ->
            dispatchBonusDefs = DispatchDatLoader.loadBonus(bytes)
            logger.info { "DispatchBonus 載入完成：${dispatchBonusDefs.size} 筆" }
        }

        // Leaderboard（排行榜定義）
        loadFile(dataDir, "LeaderbordInfo_C.dat") { bytes ->
            leaderboardDefs = LeaderboardDatLoader.load(bytes)
            logger.info { "LeaderbordInfo 載入完成：${leaderboardDefs.size} 筆" }
        }

        // NpcUpgrade（武將強化定義）
        loadFile(dataDir, "NpcUpgrade_C.dat") { bytes ->
            npcUpgradeDefs = NpcUpgradeDatLoader.load(bytes)
            logger.info { "NpcUpgrade 載入完成：${npcUpgradeDefs.size} 筆" }
        }

        // UISell（介面消費定義）
        loadFile(dataDir, "UISell_C.dat") { bytes ->
            uiSellDefs = UISellDatLoader.loadSellDefs(bytes)
            logger.info { "UISell 載入完成：${uiSellDefs.size} 筆" }
        }

        // UISellFormula（介面消費費用公式，客戶端不讀的伺服器費用表）
        loadFile(dataDir, "UISell_Formula_C.dat") { bytes ->
            uiSellFormulaDefs = UISellDatLoader.loadFormulaDefs(bytes)
            logger.info { "UISellFormula 載入完成：${uiSellFormulaDefs.size} 筆" }
        }

        // NPC 掉落表（JSON 格式，從伺服器端 gamedata 目錄載入）
        val npcDropFile = gamedataPath.resolve("npc_drops.json")
        npcDropTable = NpcDropLoader.load(npcDropFile)

        val elapsed = System.currentTimeMillis() - startTime
        logger.info { "全部 .dat 載入完成（${elapsed}ms）" }
    }

    /**
     * 載入單一檔案，失敗時 log 警告
     */
    private fun loadFile(dataDir: Path, fileName: String, loader: (ByteArray) -> Unit) {
        val file = dataDir.resolve(fileName)
        if (!Files.exists(file)) {
            logger.warn { "檔案不存在：$file — 跳過" }
            return
        }
        try {
            val bytes = Files.readAllBytes(file)
            loader(bytes)
        } catch (e: Exception) {
            logger.error(e) { "載入 $fileName 失敗" }
        }
    }
}

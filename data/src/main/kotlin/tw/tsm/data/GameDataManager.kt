package tw.tsm.data

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.loaders.*
import tw.tsm.domain.data.BlissBagDef
import tw.tsm.domain.data.*
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Trình quản lý dữ liệu tĩnh của game — tương ứng `DataManager.lua`
 *
 * Gọi [initialize] khi server khởi động, lần lượt nạp từng file .dat.
 * Được tiêm dưới dạng singleton Koin, cung cấp cho các hệ thống truy vấn dữ liệu tĩnh.
 *
 * @param basePath thư mục gốc dữ liệu (chứa thư mục con `Data/`)
 * @param gamedataPath thư mục gamedata phía server (chứa các cấu hình như npc_drops.json)
 */
class GameDataManager(
    private val basePath: Path,
    private val gamedataPath: Path = basePath
) {

    /** Tham số công thức (dùng cho Calculator.lua)*/
    lateinit var formulaParams: FormulaParams
        private set

    /** Cổng dịch chuyển (key = 0-based index)*/
    var warpDefs: Map<Int, WarpDef> = emptyMap()
        private set

    /** Cài đặt cảnh (key = ID cảnh)*/
    var sceneSetDefs: Map<Int, SceneSetDef> = emptyMap()
        private set

    /** Định nghĩa NPC (key = NPC ID)*/
    var npcDefs: Map<Int, NpcDef> = emptyMap()
        private set

    /** Định nghĩa kỹ năng (key = ID kỹ năng)*/
    var skillDefs: Map<Int, SkillDef> = emptyMap()
        private set

    /** Định nghĩa vật phẩm (key = ID vật phẩm)*/
    var itemDefs: Map<Int, ItemDef> = emptyMap()
        private set

    /** Dữ liệu sự kiện NPC (key = ID cảnh)*/
    var eveData: Map<Int, SceneEveData> = emptyMap()
        private set

    /** Định nghĩa cờ đánh dấu nhiệm vụ (key = ID nhiệm vụ)*/
    var markDefs: Map<Int, MarkDef> = emptyMap()
        private set

    /** Ánh xạ ngược bitId → missionId (dùng để truy vấn cờ đánh dấu hoàn thành)*/
    var bitToMissionId: Map<Int, Int> = emptyMap()
        private set

    /** Định nghĩa túi phúc (key = ID vật phẩm túi phúc)*/
    var blissBagDefs: Map<Int, BlissBagDef> = emptyMap()
        private set

    /** Mall/Doro goods from the Mobile DVT IOS_GS_C.dat in file order. */
    var goodsSaleDefs: List<GoodsSaleDef> = emptyList()
        private set

    /** Doro reward pools from Mobile DVT IOS_BlissBag_C.dat. */
    var doroBlissBagDefs: Map<Int, BlissBagDef> = emptyMap()
        private set

    /** Dữ liệu địa chất bản đồ (key = mapId, value = geolBaseAtt) — dùng cho nền chiến đấu*/
    var mapTerrains: Map<Int, Int> = emptyMap()
        private set

    /** Ánh xạ hướng dẫn (guideId → bitFlagId của bước cuối) — dùng cho C:005-008*/
    var guideLastBitFlagId: Map<Int, Int> = emptyMap()
        private set

    /** Ánh xạ kích hoạt hướng dẫn (markFlagId → guideId) — dùng để S:024-005 kích hoạt UITeach*/
    var markFlagToGuideId: Map<Int, Int> = emptyMap()
        private set

    /** Ánh xạ kích hoạt hướng dẫn (guideId → markFlagId) — dùng cho truy vấn thuận*/
    var guideMarkFlagIds: Map<Int, Int> = emptyMap()
        private set

    /** Bảng rơi đồ NPC (key = NPC ID)*/
    var npcDropTable: Map<Int, List<NpcDropDef>> = emptyMap()
        private set

    /** Định nghĩa vật cưỡi (key = NPC ID)*/
    var mountDefs: Map<Int, MountDef> = emptyMap()
        private set

    /** Định nghĩa tăng trưởng vật cưỡi (key = cấp độ)*/
    var mountGrowDefs: Map<Int, MountGrowDef> = emptyMap()
        private set

    /** Định nghĩa thành tựu (key = ID thành tựu)*/
    var achievementDefs: Map<Int, AchievementDef> = emptyMap()
        private set

    /** Định nghĩa nhiệm vụ phái cử (key = mainKind → subKind → DispatchDef)*/
    var dispatchDefs: Map<Int, Map<Int, DispatchDef>> = emptyMap()
        private set

    /** Hiệu ứng cộng thưởng phái cử (key = ID hiệu ứng)*/
    var dispatchBonusDefs: Map<Int, DispatchBonusDef> = emptyMap()
        private set

    /** Định nghĩa bảng xếp hạng (key = ID bảng xếp hạng)*/
    var leaderboardDefs: Map<Int, LeaderboardDef> = emptyMap()
        private set

    /** Định nghĩa tiêu hao của giao diện UI (key = sellId)*/
    var uiSellDefs: Map<Int, UISellDef> = emptyMap()
        private set

    /** Định nghĩa tăng cường tướng (key = cấp sao đích 1~15)*/
    var npcUpgradeDefs: Map<Int, NpcUpgradeDef> = emptyMap()
        private set

    /** Công thức chi phí tiêu hao của giao diện UI (key = sellId)*/
    var uiSellFormulaDefs: Map<Int, UISellFormulaDef> = emptyMap()
        private set

    /** Định nghĩa bàn cờ cửu cung (key = grid ID)*/
    var jiugonggeInfoDefs: Map<Int, JiugonggeInfoDef> = emptyMap()
        private set

    /** Định nghĩa nhiệm vụ lưới cửu cung (key = mission ID)*/
    var jiugonggeMissionDefs: Map<Int, JiugonggeMissionDef> = emptyMap()
        private set

    /** Định nghĩa thời gian hoạt động (key = activity ID)*/
    var activityTimeDefs: Map<Int, ActivityTimeDef> = emptyMap()
        private set

    /**
     * Khởi tạo: lần lượt nạp từng file .dat
     *
     * Thứ tự nạp đối chiếu `DataManager.LoadResourcePacked()`.
     * Mỗi file nạp thất bại chỉ log cảnh báo, không làm gián đoạn khởi động.
     */
    fun initialize() {
        val dataDir = basePath.resolve("Data")
        val dataIosDir = listOf(
            basePath.resolve("Data-ios"),
            Path.of("File.dat", "Data-ios"),
        ).firstOrNull { Files.isDirectory(it) } ?: basePath.resolve("Data-ios")
        if (!Files.isDirectory(dataDir)) {
            logger.warn { "Thư mục dữ liệu không tồn tại: $dataDir — bỏ qua nạp .dat" }
            return
        }

        val startTime = System.currentTimeMillis()

        // Formula (ưu tiên nhất, StatSystem cần)
        loadFile(dataDir, "Formula.dat") { bytes ->
            formulaParams = FormulaDatLoader.load(bytes)
            logger.info { "Nạp Formula xong: baseHp=${formulaParams.baseHp}, baseSp=${formulaParams.baseSp}, w35=${formulaParams.w35}, w36=${formulaParams.w36}" }
        }

        // Item
        loadFile(dataDir, "Item_C.dat") { bytes ->
            itemDefs = ItemDatLoader.load(bytes)
            logger.info { "Nạp Item xong: ${itemDefs.size} bản ghi" }
        }

        // SceneSet
        loadFile(dataDir, "SceneSet_C.dat") { bytes ->
            sceneSetDefs = SceneSetDatLoader.load(bytes)
            logger.info { "Nạp SceneSet xong: ${sceneSetDefs.size} bản ghi" }
        }

        // Npc
        loadFile(dataDir, "Npc_C.dat") { bytes ->
            npcDefs = NpcDatLoader.load(bytes)
            logger.info { "Nạp Npc xong: ${npcDefs.size} bản ghi" }
        }

        // Skill
        loadFile(dataDir, "Skill_C.dat") { bytes ->
            skillDefs = SkillDatLoader.load(bytes)
            logger.info { "Nạp Skill xong: ${skillDefs.size} bản ghi" }
        }

        // Warp
        loadFile(dataDir, "Warp_C.dat") { bytes ->
            warpDefs = WarpDatLoader.load(bytes)
            logger.info { "Nạp Warp xong: ${warpDefs.size} bản ghi" }
        }

        // Mark (Định nghĩa cờ đánh dấu nhiệm vụ)
        loadFile(dataDir, "Mark_C.dat") { bytes ->
            val (marks, bitMap) = MarkDatLoader.load(bytes)
            markDefs = marks
            bitToMissionId = bitMap
            logger.info { "Nạp Mark xong: ${markDefs.size} định nghĩa nhiệm vụ, ${bitToMissionId.size} ánh xạ mốc hoàn thành" }
            // ── Chẩn đoán: ánh xạ bitId của các nhiệm vụ liên quan hướng dẫn ──
            val diagMissions = listOf(12280, 12284, 12285, 12286, 12287, 12288, 10037)
            for (mid in diagMissions) {
                val def = markDefs[mid]
                logger.debug { "Chẩn đoán Mark: mission=$mid → ${def?.let { "bitId=${it.bitId} name=${it.name}" } ?: "不存在"}" }
            }
        }

        // TeachInfo (dữ liệu hướng dẫn)
        loadFile(dataDir, "TeachInfo_C.dat") { bytes ->
            val teachResult = TeachInfoDatLoader.load(bytes)
            guideLastBitFlagId = teachResult.guideLastBitFlagId
            guideMarkFlagIds = teachResult.guideMarkFlagIds
            // Tạo ánh xạ ngược: markFlagId → guideId
            markFlagToGuideId = teachResult.guideMarkFlagIds.entries
                .associate { (guideId, markFlagId) -> markFlagId to guideId }
            logger.info { "Nạp TeachInfo xong: ${guideLastBitFlagId.size} bitFlagId, ${guideMarkFlagIds.size} markFlagId" }
            // ── Chẩn đoán: đối chiếu chéo markFlagId của guide với bitId trong Mark_C.dat ──
            for ((guideId, markFlagId) in guideMarkFlagIds) {
                val missionId = bitToMissionId[markFlagId]
                logger.debug { "教學交叉：guide=$guideId markFlagId=$markFlagId → ${
                    missionId?.let { "mission=$it (${markDefs[it]?.name})" } ?: "Không có nhiệm vụ tương ứng (cần chủ động kích hoạt)"
                }" }
            }
        }

        // BlissBag (logic mở túi thông thường tái sử dụng server data hiện tại)
        loadFile(dataDir, "BlissBag_C.dat") { bytes ->
            blissBagDefs = BlissBagDatLoader.load(bytes)
            logger.info { "Nạp BlissBag xong: ${blissBagDefs.size} định nghĩa túi may mắn" }
        }

        // Mall/Doro phải dùng cùng bộ data với Mobile DVT client:
        // Lua DataManager.OnLoadGoodsSaleData / OnLoadBlissBagData đọc IOS_*.
        // Data/GS_C.dat của basePath Runtime có thể là bộ CDN khác (production 2.4.76 chỉ có 8 Doro),
        // trong khi client đang gửi ID theo IOS_GS_C.dat (32 Doro). Ưu tiên Data-ios.
        val iosGs = dataIosDir.resolve("IOS_GS_C.dat")
        val iosBliss = dataIosDir.resolve("IOS_BlissBag_C.dat")

        if (Files.exists(iosGs)) {
            try {
                goodsSaleDefs = GoodsSaleDatLoader.load(Files.readAllBytes(iosGs))
                logger.info {
                    "Nạp IOS_GS xong: ${goodsSaleDefs.size} bản ghi, trong đó Doro=${goodsSaleDefs.count { it.storeKind == 5 }} source=$iosGs"
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_GS_C.dat thất bại: $iosGs" }
            }
        } else {
            loadFile(dataDir, "GS_C.dat") { bytes ->
                goodsSaleDefs = GoodsSaleDatLoader.load(bytes)
                logger.warn {
                    "IOS_GS_C.dat không tồn tại, dùng GS_C.dat thay thế: ${goodsSaleDefs.size} bản ghi, trong đó Doro=${goodsSaleDefs.count { it.storeKind == 5 }}"
                }
            }
        }

        if (Files.exists(iosBliss)) {
            try {
                doroBlissBagDefs = BlissBagDatLoader.load(Files.readAllBytes(iosBliss))
                logger.info {
                    "Nạp IOS_BlissBag xong: ${doroBlissBagDefs.size} bản ghi Doro pool source=$iosBliss"
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_BlissBag_C.dat thất bại: $iosBliss" }
            }
        }
        if (doroBlissBagDefs.isEmpty()) {
            doroBlissBagDefs = blissBagDefs
            logger.warn { "Doro fallback dùng BlissBag_C.dat vì IOS_BlissBag_C.dat không khả dụng" }
        }

        // Ground.mmg (dữ liệu địa chất bản đồ, đọc từ thư mục CompreseData/)
        val groundFile = basePath.resolve("CompreseData").resolve("Ground.mmg")
        if (Files.exists(groundFile)) {
            try {
                val bytes = Files.readAllBytes(groundFile)
                mapTerrains = GroundMmgLoader.load(bytes)
                logger.info { "Nạp Ground xong: ${mapTerrains.size} dữ liệu địa hình bản đồ" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp Ground.mmg thất bại" }
            }
        } else {
            logger.warn { "Ground.mmg không tồn tại: $groundFile — bỏ qua" }
        }

        // Eve.emg (đọc từ thư mục CompreseData/)
        val eveFile = basePath.resolve("CompreseData").resolve("Eve.emg")
        if (Files.exists(eveFile)) {
            try {
                val bytes = Files.readAllBytes(eveFile)
                eveData = EveDataLoader.load(bytes)
                val npcCount = eveData.values.sumOf { it.npcs.size }
                val eventCount = eveData.values.sumOf { it.npcEvents.size }
                val sceneInfoCount = eveData.values.sumOf { it.sceneInfos.size }
                logger.info { "Nạp Eve xong: ${eveData.size} cảnh, $npcCount NPC bố trí, $eventCount định nghĩa sự kiện, $sceneInfoCount thông tin cảnh" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp Eve.emg thất bại" }
            }
        } else {
            logger.warn { "Eve.emg không tồn tại: $eveFile — bỏ qua" }
        }

        // Mounts / MountsGrow phải bám đúng Mobile DVT Lua DataManager:
        // Data/IOS_Mounts_C.dat + IOS_MountsGrow_C.dat.
        // CDN Data/MountsGrow_C.dat hiện là một bộ khác, mở rộng tới Lv15 trong khi
        // Mobile DVT IOS table dừng ở Lv10.
        val iosMounts = dataIosDir.resolve("IOS_Mounts_C.dat")
        val iosMountsGrow = dataIosDir.resolve("IOS_MountsGrow_C.dat")

        if (Files.exists(iosMounts)) {
            mountDefs = MountDatLoader.load(Files.readAllBytes(iosMounts))
            logger.info { "Nạp IOS_Mounts xong: ${mountDefs.size} bản ghi source=$iosMounts" }
        } else {
            loadFile(dataDir, "Mounts_C.dat") { bytes ->
                mountDefs = MountDatLoader.load(bytes)
                logger.warn { "IOS_Mounts_C.dat không tồn tại, dùng Mounts_C.dat thay thế: ${mountDefs.size} bản ghi" }
            }
        }

        if (Files.exists(iosMountsGrow)) {
            mountGrowDefs = MountGrowDatLoader.load(Files.readAllBytes(iosMountsGrow))
            logger.info {
                "Nạp IOS_MountsGrow xong: ${mountGrowDefs.size} bản ghi, maxLv=${mountGrowDefs.keys.maxOrNull() ?: 0} source=$iosMountsGrow"
            }
        } else {
            loadFile(dataDir, "MountsGrow_C.dat") { bytes ->
                mountGrowDefs = MountGrowDatLoader.load(bytes)
                logger.warn {
                    "IOS_MountsGrow_C.dat không tồn tại, dùng MountsGrow_C.dat thay thế: ${mountGrowDefs.size} bản ghi"
                }
            }
        }

        // Achievement (Định nghĩa thành tựu)
        loadFile(dataDir, "AchievementData_C.dat") { bytes ->
            achievementDefs = AchievementDatLoader.load(bytes)
            logger.info { "Nạp Achievement xong: ${achievementDefs.size} bản ghi" }
        }

        // ActivityTime — client UIActivity/UIJiugongge relies on serverStartTime/serverCloseTime.
        loadFile(dataDir, "ActivityTime_C.dat") { bytes ->
            activityTimeDefs = JiugonggeDatLoader.loadActivityTimes(bytes)
            logger.info { "Nạp ActivityTime xong: ${activityTimeDefs.size} bản ghi" }
        }

        // Jiugongge static data. DVT DataManager.lua explicitly reads the IOS_ files.
        loadFile(dataIosDir, "IOS_JiugonggeInfo_C.dat") { bytes ->
            jiugonggeInfoDefs = JiugonggeDatLoader.loadInfo(bytes)
            logger.info { "Nạp JiugonggeInfo xong: ${jiugonggeInfoDefs.size} bản ghi" }
        }
        loadFile(dataIosDir, "IOS_JiugonggeMission_C.dat") { bytes ->
            jiugonggeMissionDefs = JiugonggeDatLoader.loadMissions(bytes)
            logger.info { "Nạp JiugonggeMission xong: ${jiugonggeMissionDefs.size} bản ghi" }
        }

        // Dispatch (Định nghĩa nhiệm vụ phái cử)
        loadFile(dataDir, "Dispatch_C.dat") { bytes ->
            dispatchDefs = DispatchDatLoader.loadDispatch(bytes)
            val total = dispatchDefs.values.sumOf { it.size }
            logger.info { "Nạp Dispatch xong: $total bản ghi" }
        }

        // DispatchBonus (Hiệu ứng cộng thưởng phái cử)
        loadFile(dataDir, "DispatchBonus_C.dat") { bytes ->
            dispatchBonusDefs = DispatchDatLoader.loadBonus(bytes)
            logger.info { "Nạp DispatchBonus xong: ${dispatchBonusDefs.size} bản ghi" }
        }

        // Leaderboard (Định nghĩa bảng xếp hạng)
        loadFile(dataDir, "LeaderbordInfo_C.dat") { bytes ->
            leaderboardDefs = LeaderboardDatLoader.load(bytes)
            logger.info { "Nạp LeaderbordInfo xong: ${leaderboardDefs.size} bản ghi" }
        }

        // NpcUpgrade (Định nghĩa tăng cường tướng)
        loadFile(dataDir, "NpcUpgrade_C.dat") { bytes ->
            npcUpgradeDefs = NpcUpgradeDatLoader.load(bytes)
            logger.info { "Nạp NpcUpgrade xong: ${npcUpgradeDefs.size} bản ghi" }
        }

        // UISell (Định nghĩa tiêu hao giao diện)
        loadFile(dataDir, "UISell_C.dat") { bytes ->
            uiSellDefs = UISellDatLoader.loadSellDefs(bytes)
            logger.info { "Nạp UISell xong: ${uiSellDefs.size} bản ghi" }
        }

        // UISellFormula (công thức chi phí tiêu hao giao diện, bảng phí server mà client không đọc)
        loadFile(dataDir, "UISell_Formula_C.dat") { bytes ->
            uiSellFormulaDefs = UISellDatLoader.loadFormulaDefs(bytes)
            logger.info { "Nạp UISellFormula xong: ${uiSellFormulaDefs.size} bản ghi" }
        }

        // Bảng rơi đồ NPC (định dạng JSON, nạp từ thư mục gamedata phía server)
        val npcDropFile = gamedataPath.resolve("npc_drops.json")
        npcDropTable = NpcDropLoader.load(npcDropFile)

        val elapsed = System.currentTimeMillis() - startTime
        logger.info { "Nạp toàn bộ .dat xong (${elapsed}ms)" }
    }

    /**
     * Nạp một file duy nhất, khi thất bại log cảnh báo
     */
    private fun loadFile(dataDir: Path, fileName: String, loader: (ByteArray) -> Unit) {
        val file = dataDir.resolve(fileName)
        if (!Files.exists(file)) {
            logger.warn { "File không tồn tại: $file — bỏ qua" }
            return
        }
        try {
            val bytes = Files.readAllBytes(file)
            loader(bytes)
        } catch (e: Exception) {
            logger.error(e) { "Nạp $fileName thất bại" }
        }
    }
}

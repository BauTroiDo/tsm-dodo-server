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

    /** DVT IOS_ExclusiveSkill_C.dat: skillId -> level -> raw effect record. */
    var exclusiveSkillDefs: Map<Int, Map<Int, ExclusiveSkillDef>> = emptyMap()
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

    /** Full DVT MarkStep data for route/NPC/progression parity diagnostics. */
    var markSteps: Map<Int, Map<Int, MarkStepDef>> = emptyMap()
        private set

    /** Missions with real MarkStep records (distinct from DVT marker missions). */
    var markStepMissionIds: Set<Int> = emptySet()
        private set

    /** DVT danh hiệu từ IOS_TitleData_C.dat, key=titleId. */
    var titleDefs: Map<Int, TitleDef> = emptyMap()
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

    /** Bốn pool giải thưởng của Vòng quay từ Mobile DVT IOS_Slot.dat. */
    var slotMachinePools: SlotMachinePools? = null
        private set

    /** Trạc/Poke possible rewards from IOS_PokeData_C.dat. */
    var pokeRewardDefs: List<PokeRewardDef> = emptyList()
        private set

    /** Trạc/Poke per-tier probability weight from IOS_PokeRate_C.dat. */
    var pokeRateByKind: Map<Int, Int> = emptyMap()
        private set

    /** DVT IOS_PokeBonusReward_C.dat, preserved for bonus/jackpot semantics. */
    var pokeBonusRewardDefs: List<PokeBonusRewardDef> = emptyList()
        private set

    /** Bảng recipe Chế tạo Mobile DVT từ IOS_Compound.dat. */
    var compoundRecipes: List<CompoundRecipeDef> = emptyList()
        private set

    /** Phó bản DVT từ IOS_Dungeon_C.dat, key=dungeonId. */
    var dungeonDefs: Map<Int, DungeonDef> = emptyMap()
        private set

    /** DVT Linh Hồn: cấu hình phẩm chất N/R/SR/SSR từ IOS_Soul_C.dat. */
    var soulDefs: Map<Int, SoulDef> = emptyMap()
        private set

    /** DVT Linh Hồn: chi phí theo cấp 1..300 từ IOS_SoulLevel_C.dat. */
    var soulLevelDefs: Map<Int, SoulLevelDef> = emptyMap()
        private set

    /** DVT Linh Hồn: item Hồn -> NPC/avatar từ IOS_NpcSoul_C.dat. */
    var npcSoulDefs: Map<Int, NpcSoulDef> = emptyMap()
        private set

    /** DVT Quà/Souvenir shop từ IOS_Souvenir_C.dat, key=itemId. */
    var souvenirDefs: Map<Int, SouvenirDef> = emptyMap()
        private set

    /** DVT Welfare/LoginAward từ IOS_LoginAwardData_C.dat. */
    var loginAwardDefs: List<LoginAwardDef> = emptyList()
        private set

    /** DVT chuyên võ: itemId -> enhanceLevel -> skill/attribute row. */
    var exclusiveWeaponDefs: Map<Int, Map<Int, ExclusiveWeaponDef>> = emptyMap()
        private set

    /** DVT ownership groups used by ExclusiveWeapon.CheckExclusiveWarrior. */
    var collectWarriorSortDefs: List<CollectWarriorSortDef> = emptyList()
        private set

    var dungeonComboDefs: Map<Int, List<DungeonComboDef>> = emptyMap()
        private set

    /** Tiêu chí phó bản DVT từ IOS_DungeonCriteria_C.dat, key=criteriaId. */
    var dungeonCriteriaDefs: Map<Int, DungeonCriteriaDef> = emptyMap()
        private set

    /** DVT Eternal Dungeon layer breakpoints from IOS_EternalDungeonLayer_C.dat. */
    var eternalDungeonLayerDefs: Map<Int, EternalDungeonLayerDef> = emptyMap()
        private set

    /** DVT Eternal Dungeon rewards for layers 1..30. */
    var eternalDungeonPrizeDefs: Map<Int, EternalDungeonPrizeDef> = emptyMap()
        private set

    /** Khu luyện công giới hạn DVT từ IOS_LimitTimeDungeon_C.dat, key=index. */
    var limitTimeDungeonDefs: Map<Int, LimitTimeDungeonDef> = emptyMap()
        private set

    /** C97-002 selected monster level / rebirth, from IOS_AnotherWorldLv_C.dat. */
    var anotherWorldLvDefs: Map<Int, AnotherWorldLvDef> = emptyMap()
        private set

    /** Danh sách điều hướng luyện công/chuyển sinh DVT từ IOS_SceneFight_C.dat. */
    var sceneFightDefs: List<SceneFightDef> = emptyList()
        private set

    /** Lò DVT: pool item theo loại/bậc từ IOS_FurnaceSlot_C.dat. */
    var furnaceSlotDefs: List<FurnaceSlotDef> = emptyList()
        private set

    /** Lò DVT: mẫu phân bố 8 ô theo shop kind từ IOS_FurnaceSelect_C.dat. */
    var furnaceSelectDefs: List<FurnaceSelectDef> = emptyList()
        private set

    /** Dữ liệu địa chất bản đồ (key = mapId, value = geolBaseAtt) — dùng cho nền chiến đấu*/
    var mapWalkGrids: Map<Int, tw.tsm.domain.data.MapWalkGrid> = emptyMap()
        private set

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

    /** DVT Thất Tinh Mệnh Bàn (key = sao 1..7, mỗi sao 10 cấp). */
    var astrolabeDefs: Map<Int, AstrolabeDef> = emptyMap()
        private set

    /** DVT Myth/Sacred main attribute (key = SacredEquip primary no). */
    var sacredEquipPrimaryDefs: Map<Int, SacredEquipPrimaryDef> = emptyMap()
        private set

    /** DVT Myth/Sacred upgrade table (key = target Myth level 1..15). */
    var sacredEquipUpgradeDefs: Map<Int, SacredEquipUpgradeDef> = emptyMap()
        private set

    /** DVT Myth/Sacred sub-attribute random table (key = SacredEquip sub no). */
    var sacredEquipSubDefs: Map<Int, SacredEquipSubDef> = emptyMap()
        private set

    /** DVT Myth/Sacred allowed sub-attributes grouped by threshold 1..6. */
    var sacredEquipSubDomainDefs: List<SacredEquipSubDomainDef> = emptyList()
        private set

    /** Định nghĩa thành tựu (key = ID thành tựu)*/
    var achievementDefs: Map<Int, AchievementDef> = emptyMap()
        private set

    /** Định nghĩa nhiệm vụ phái cử (key = mainKind → subKind → DispatchDef)*/
    var dispatchDefs: Map<Int, Map<Int, List<DispatchDef>>> = emptyMap()

    var commonAwardDefs: Map<Int, List<CommonAwardEntry>> = emptyMap()
        private set
    var luckyBagDefs: Map<Int, LuckyBagDef> = emptyMap()
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

    /** DVT warrior EXP slot rows (IOS_FNpcSlot_C.dat). */
    var fNpcSlotDefs: Map<Int, FNpcSlotDef> = emptyMap()
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
    var passAwardDefs: List<PassAwardDef> = emptyList()
        private set

    var missionAwardDefs: Map<Int, MissionAwardDef> = emptyMap()
        private set

    var activityTimeDefs: Map<Int, ActivityTimeDef> = emptyMap()
        private set

    /** DVT Sưu Tập võ tướng (key = NPC ID). */
    var collectWarriorDefs: Map<Int, CollectWarriorDef> = emptyMap()
        private set

    /** DVT Sưu Tập tạo hình (key = collect style ID). */
    var collectStyleDefs: Map<Int, CollectStyleDef> = emptyMap()
        private set

    /** DVT mốc điểm tạo hình. */
    var collectStyleValueDefs: List<CollectStyleValueDef> = emptyList()
        private set

    /** DVT tổ hợp thẻ (key = combination ID). */
    var collectCardDefs: Map<Int, CollectCardDef> = emptyMap()
        private set

    /** DVT thẻ thành phần (key = card-sort ID). */
    var collectCardSortDefs: Map<Int, CollectCardSortDef> = emptyMap()
        private set

    /** DVT NFT collection (key = warrior/display ID). */
    var collectNftDefs: Map<Int, CollectNftDef> = emptyMap()
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

        // Item — Lua DVT DataManager.LoadResourcePacked() đọc Data/IOS_Item_C.dat.
        // Không dùng ResourcesPacked/Data/Item_C.dat legacy vì bản đó thiếu item mới
        // (ví dụ reward 35671) và làm backend lệch client DVT.
        loadFile(dataIosDir, "IOS_Item_C.dat") { bytes ->
            itemDefs = ItemDatLoader.load(bytes)
            logger.info { "Nạp IOS_Item xong: ${itemDefs.size} bản ghi source=${dataIosDir.resolve("IOS_Item_C.dat")}" }
        }

        // SceneSet
        loadFile(dataDir, "SceneSet_C.dat") { bytes ->
            sceneSetDefs = SceneSetDatLoader.load(bytes)
            logger.info { "Nạp SceneSet xong: ${sceneSetDefs.size} bản ghi" }
        }

        // NPC/Skill — Lua DVT dùng IOS_Npc_C.dat + IOS_Skill_C.dat.
        // Bản Data legacy đang thiếu một số võ tướng Quang/Ám và có record cũ sai hệ/kỹ năng.
        loadFile(dataIosDir, "IOS_Npc_C.dat") { bytes ->
            npcDefs = NpcDatLoader.load(bytes)
            logger.info { "Nạp IOS_Npc xong: ${npcDefs.size} bản ghi source=${dataIosDir.resolve("IOS_Npc_C.dat")}" }
        }

        loadFile(dataIosDir, "IOS_Skill_C.dat") { bytes ->
            skillDefs = SkillDatLoader.load(bytes)
            logger.info { "Nạp IOS_Skill xong: ${skillDefs.size} bản ghi source=${dataIosDir.resolve("IOS_Skill_C.dat")}" }
        }

        // DVT passive/exclusive server-effect table. Preserve raw semantics only;
        // gameplay consumers are added separately once each effect kind is proven.
        val exclusiveSkillSource = dataIosDir.resolve("IOS_ExclusiveSkill_C.dat")
        if (Files.isRegularFile(exclusiveSkillSource)) {
            try {
                exclusiveSkillDefs = ExclusiveSkillDatLoader.load(Files.readAllBytes(exclusiveSkillSource))
                val total = exclusiveSkillDefs.values.sumOf { it.size }
                logger.info { "Nạp IOS_ExclusiveSkill xong: $total bản ghi, ${exclusiveSkillDefs.size} skill source=$exclusiveSkillSource" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_ExclusiveSkill_C.dat thất bại: $exclusiveSkillSource" }
            }
        }

        // Warp
        loadFile(dataDir, "Warp_C.dat") { bytes ->
            warpDefs = WarpDatLoader.load(bytes)
            logger.info { "Nạp Warp xong: ${warpDefs.size} bản ghi" }
        }

        // Mark (Định nghĩa cờ đánh dấu nhiệm vụ)
        val markSource = dataIosDir.resolve("IOS_Mark_C.dat")
        val markDir = if (Files.exists(markSource)) dataIosDir else dataDir
        val markFile = if (Files.exists(markSource)) "IOS_Mark_C.dat" else "Mark_C.dat"
        loadFile(markDir, markFile) { bytes ->
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

        // Preserve the client DVT MarkStep condition and completion targets, not
        // just mission IDs. Client auto-route and Eve mission state must agree.
        val markStepSource = dataIosDir.resolve("IOS_MarkStep_C.dat")
        if (Files.isRegularFile(markStepSource)) {
            markSteps = MarkStepDatLoader.loadSteps(Files.readAllBytes(markStepSource))
            markStepMissionIds = markSteps.keys
            val allSteps = markSteps.values.sumOf { it.size }
            val endUnlocated = markSteps.values.sumOf { defs ->
                defs.values.count { !it.endTarget.hasDestination }
            }
            logger.info {
                "DVT MarkStep: ${markStepMissionIds.size} missions, $allSteps steps, " +
                    "$endUnlocated without completion navigation (may be intentional)"
            }
        } else {
            logger.warn { "Không tìm thấy IOS_MarkStep_C.dat; marker mission sẽ không được tự nhận diện từ data" }
        }

        // TitleData — Lua DVT DataManager đọc IOS_TitleData_C.dat.
        val titleSource = dataIosDir.resolve("IOS_TitleData_C.dat")
        if (Files.isRegularFile(titleSource)) {
            titleDefs = TitleDataDatLoader.load(Files.readAllBytes(titleSource))
            logger.info { "Nạp IOS_TitleData xong: ${titleDefs.size} danh hiệu source=$titleSource" }
        } else {
            logger.warn { "Không tìm thấy IOS_TitleData_C.dat; đổi danh hiệu sẽ bị từ chối an toàn" }
        }

        // TeachInfo — DVT client hiện tại đọc IOS_TeachInfo_C.dat.
        // Bản Data/TeachInfo_C.dat cũ thiếu các guide NFT 24..27 nên client sẽ lặp hướng dẫn mãi.
        val iosTeachInfo = dataIosDir.resolve("IOS_TeachInfo_C.dat")
        val teachInfoFile = if (Files.isRegularFile(iosTeachInfo)) {
            iosTeachInfo
        } else {
            dataDir.resolve("TeachInfo_C.dat")
        }
        if (Files.isRegularFile(teachInfoFile)) {
            try {
                val teachResult = TeachInfoDatLoader.load(Files.readAllBytes(teachInfoFile))
                guideLastBitFlagId = teachResult.guideLastBitFlagId
                guideMarkFlagIds = teachResult.guideMarkFlagIds
                // Tạo ánh xạ ngược: markFlagId → guideId
                markFlagToGuideId = teachResult.guideMarkFlagIds.entries
                    .associate { (guideId, markFlagId) -> markFlagId to guideId }
                logger.info {
                    "Nạp TeachInfo xong: ${guideLastBitFlagId.size} bitFlagId, " +
                        "${guideMarkFlagIds.size} markFlagId source=$teachInfoFile"
                }
                // ── Chẩn đoán: đối chiếu chéo markFlagId của guide với bitId trong Mark_C.dat ──
                for ((guideId, markFlagId) in guideMarkFlagIds) {
                    val missionId = bitToMissionId[markFlagId]
                    logger.debug { "教學交叉：guide=$guideId markFlagId=$markFlagId → ${
                        missionId?.let { "mission=$it (${markDefs[it]?.name})" } ?: "Không có nhiệm vụ tương ứng (cần chủ động kích hoạt)"
                    }" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp TeachInfo thất bại: $teachInfoFile" }
            }
        } else {
            logger.warn { "Không tìm thấy TeachInfo: $teachInfoFile" }
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

        // Vòng quay Tiểu Mary: IOS_Slot.dat là bảng server-side của Mobile DVT.
        val iosSlot = dataIosDir.resolve("IOS_Slot.dat")
        if (Files.exists(iosSlot)) {
            try {
                slotMachinePools = SlotMachineDatLoader.load(Files.readAllBytes(iosSlot))
                val p = slotMachinePools!!
                logger.info {
                    "Nạp IOS_Slot xong: A=${p.poolA.size}, B=${p.poolB.size}, C=${p.poolC.size}, D=${p.poolD.size} source=$iosSlot"
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_Slot.dat thất bại: $iosSlot" }
            }
        } else {
            val slot = dataDir.resolve("Slot.dat")
            if (Files.exists(slot)) {
                try {
                    slotMachinePools = SlotMachineDatLoader.load(Files.readAllBytes(slot))
                    logger.warn { "IOS_Slot.dat không tồn tại, fallback Slot.dat: $slot" }
                } catch (e: Exception) {
                    logger.error(e) { "Nạp Slot.dat fallback thất bại: $slot" }
                }
            }
        }

        // Chế tạo Mobile DVT: recipe kind/material/ratio từ IOS_Compound.dat.
        val iosCompound = dataIosDir.resolve("IOS_Compound.dat")
        if (Files.exists(iosCompound)) {
            try {
                compoundRecipes = CompoundDatLoader.load(Files.readAllBytes(iosCompound))
                logger.info { "Nạp IOS_Compound xong: ${compoundRecipes.size} recipe source=$iosCompound" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_Compound.dat thất bại: $iosCompound" }
            }
        } else {
            logger.warn { "IOS_Compound.dat không tồn tại — Chế tạo generic sẽ bị evidence-block" }
        }

        // Phó bản chính — Lua DVT DungeonData.New / protocol 47.
        loadFile(dataIosDir, "IOS_Dungeon_C.dat") { bytes ->
            dungeonDefs = DungeonDatLoader.load(bytes)
            logger.info { "Nạp IOS_Dungeon xong: ${dungeonDefs.size} bản ghi" }
        }

        loadFile(dataIosDir, "IOS_DungeonCombo_C.dat") { bytes ->
            dungeonComboDefs = DungeonComboDatLoader.load(bytes)
            logger.info { "Nạp IOS_DungeonCombo xong: ${dungeonComboDefs.values.sumOf { it.size }} bản ghi" }
        }

        // Lua DVT DataManager.OnLoadDungeonCriteriaData.
        loadFile(dataIosDir, "IOS_DungeonCriteria_C.dat") { bytes ->
            dungeonCriteriaDefs = DungeonCriteriaDatLoader.load(bytes)
            logger.info { "Nạp IOS_DungeonCriteria xong: ${dungeonCriteriaDefs.size} bản ghi" }
        }

        loadFile(dataIosDir, "IOS_EternalDungeonLayer_C.dat") { bytes ->
            eternalDungeonLayerDefs = EternalDungeonLayerDatLoader.load(bytes)
            logger.info { "Nạp IOS_EternalDungeonLayer xong: ${eternalDungeonLayerDefs.size} mốc tầng" }
        }

        loadFile(dataIosDir, "IOS_EternalDungeonPrize_C.dat") { bytes ->
            eternalDungeonPrizeDefs = EternalDungeonPrizeDatLoader.load(bytes)
            logger.info { "Nạp IOS_EternalDungeonPrize xong: ${eternalDungeonPrizeDefs.size} tầng thưởng" }
        }

        // Linh Hồn — đúng ba bảng Lua DVT DataManager.OnLoadSoul*.
        loadFile(dataIosDir, "IOS_Soul_C.dat") { bytes ->
            soulDefs = SoulDatLoader.load(bytes)
            logger.info { "Nạp IOS_Soul xong: ${soulDefs.size} phẩm chất" }
        }
        loadFile(dataIosDir, "IOS_SoulLevel_C.dat") { bytes ->
            soulLevelDefs = SoulLevelDatLoader.load(bytes)
            logger.info { "Nạp IOS_SoulLevel xong: ${soulLevelDefs.size} cấp" }
        }
        loadFile(dataIosDir, "IOS_NpcSoul_C.dat") { bytes ->
            npcSoulDefs = NpcSoulDatLoader.load(bytes)
            logger.info { "Nạp IOS_NpcSoul xong: " + npcSoulDefs.size + " Hồn" }
        }
        loadFile(dataIosDir, "IOS_Souvenir_C.dat") { bytes ->
            souvenirDefs = SouvenirDatLoader.load(bytes)
            logger.info { "Nạp IOS_Souvenir xong: " + souvenirDefs.size + " quà" }
        }
        loadFile(dataIosDir, "IOS_LoginAwardData_C.dat") { bytes ->
            loginAwardDefs = LoginAwardDatLoader.load(bytes)
            logger.info { "Nạp IOS_LoginAwardData xong: " + loginAwardDefs.size + " bản ghi" }
        }

        // DVT ExclusiveWeapon.GetSkill / CheckExclusiveWarrior — full FollowNpc skill lookup.
        loadFile(dataIosDir, "IOS_ExclusiveWeapon_C.dat") { bytes ->
            exclusiveWeaponDefs = ExclusiveWeaponDatLoader.load(bytes)
            val rows = exclusiveWeaponDefs.values.sumOf { it.size }
            logger.info { "Nạp IOS_ExclusiveWeapon xong: $rows bản ghi, ${exclusiveWeaponDefs.size} vũ khí" }
        }
        loadFile(dataIosDir, "IOS_CollectWarriorSort_C.dat") { bytes ->
            collectWarriorSortDefs = CollectWarriorSortDatLoader.load(bytes)
            logger.info { "Nạp IOS_CollectWarriorSort xong: ${collectWarriorSortDefs.size} nhóm" }
        }

        // UITeleport — cùng hai bảng client DVT dùng cho LimitFightArea / Turn navigation.
        loadFile(dataIosDir, "IOS_LimitTimeDungeon_C.dat") { bytes ->
            limitTimeDungeonDefs = LimitTimeDungeonDatLoader.load(bytes)
            logger.info { "Nạp IOS_LimitTimeDungeon xong: ${limitTimeDungeonDefs.size} bản ghi" }
        }
        loadFile(dataIosDir, "IOS_AnotherWorldLv_C.dat") { bytes ->
            anotherWorldLvDefs = AnotherWorldLvDatLoader.load(bytes)
            logger.info { "Nạp IOS_AnotherWorldLv xong: ${anotherWorldLvDefs.size} mốc cấp quái" }
        }
        loadFile(dataIosDir, "IOS_SceneFight_C.dat") { bytes ->
            sceneFightDefs = SceneFightDatLoader.load(bytes)
            logger.info {
                "Nạp IOS_SceneFight xong: ${sceneFightDefs.size} bản ghi, " +
                    "turn=${sceneFightDefs.count { it.group > 100 }}"
            }
        }

        // Lò (UIFurnace / protocol 89).
        loadFile(dataIosDir, "IOS_FurnaceSlot_C.dat") { bytes ->
            furnaceSlotDefs = FurnaceSlotDatLoader.load(bytes)
            logger.info { "Nạp IOS_FurnaceSlot xong: ${furnaceSlotDefs.size} bản ghi" }
        }
        loadFile(dataIosDir, "IOS_FurnaceSelect_C.dat") { bytes ->
            furnaceSelectDefs = FurnaceSelectDatLoader.load(bytes)
            logger.info {
                "Nạp IOS_FurnaceSelect xong: ${furnaceSelectDefs.size} mẫu, " +
                    "kinds=${furnaceSelectDefs.map { it.kind }.distinct().sorted()}"
            }
        }

        // Ground.mmg (dữ liệu địa chất bản đồ, ưu tiên CompreseData của runtime;
        // staging có thể chỉ mount Data/Data-ios nên cho phép fallback sang server data).
        val groundFile = resolvePackedResource("Ground.mmg")
        if (groundFile != null) {
            try {
                val bytes = Files.readAllBytes(groundFile)
                mapTerrains = GroundMmgLoader.load(bytes)
                mapWalkGrids = GroundWalkGridLoader.load(bytes)
                logger.info { "Nạp Ground xong: ${mapTerrains.size} dữ liệu địa hình bản đồ source=$groundFile" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp Ground.mmg thất bại: $groundFile" }
            }
        } else {
            logger.warn { "Ground.mmg không tồn tại trong runtime/server data — bỏ qua" }
        }

        // Eve.emg là contract cốt lõi cho NPC/door/event. Không được im lặng chạy khi thiếu:
        // ưu tiên runtime CompreseData, sau đó fallback SERVER_DATA_PATH để staging không mất toàn bộ EVE.
        val eveFile = resolvePackedResource("Eve.emg")
            ?: throw IllegalStateException(
                "Thiếu Eve.emg: cần có ở ${basePath.resolve("CompreseData").resolve("Eve.emg")} " +
                    "hoặc SERVER_DATA_PATH=$gamedataPath"
            )
        try {
            val bytes = Files.readAllBytes(eveFile)
            eveData = EveDataLoader.load(bytes)
            if (eveData.isEmpty()) {
                throw IllegalStateException("Eve.emg đã đọc nhưng không có scene/event: $eveFile")
            }
            val npcCount = eveData.values.sumOf { it.npcs.size }
            val eventCount = eveData.values.sumOf { it.npcEvents.size }
            val sceneInfoCount = eveData.values.sumOf { it.sceneInfos.size }
            logger.info { "Nạp Eve xong: ${eveData.size} cảnh, $npcCount NPC bố trí, $eventCount định nghĩa sự kiện, $sceneInfoCount thông tin cảnh source=$eveFile" }
        } catch (e: Exception) {
            logger.error(e) { "Nạp Eve.emg thất bại: $eveFile" }
            throw IllegalStateException("Không thể nạp Eve.emg bắt buộc: $eveFile", e)
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

        // SacredEquipPrimary — DVT DataManager:
        // ReadData("Data/IOS_SacredEquipPrimaryStatus_C.dat", OnLoadSacredEquipPrimaryData).
        val iosSacredEquipPrimary = dataIosDir.resolve("IOS_SacredEquipPrimaryStatus_C.dat")
        if (Files.isRegularFile(iosSacredEquipPrimary)) {
            sacredEquipPrimaryDefs = SacredEquipPrimaryDatLoader.load(Files.readAllBytes(iosSacredEquipPrimary))
            logger.info {
                "Nạp IOS_SacredEquipPrimaryStatus xong: ${sacredEquipPrimaryDefs.size} bản ghi source=$iosSacredEquipPrimary"
            }
        } else {
            logger.warn { "Không tìm thấy IOS_SacredEquipPrimaryStatus_C.dat: $iosSacredEquipPrimary" }
        }

        // SacredEquipSub — server-side remake/random source.
        val iosSacredEquipSub = dataIosDir.resolve("IOS_SacredEquipSubStatus_C.dat")
        if (Files.isRegularFile(iosSacredEquipSub)) {
            sacredEquipSubDefs = SacredEquipSubDatLoader.load(Files.readAllBytes(iosSacredEquipSub))
            logger.info { "Nạp IOS_SacredEquipSubStatus xong: ${sacredEquipSubDefs.size} bản ghi" }
        }

        val iosSacredEquipSubDomain = dataIosDir.resolve("IOS_SacredEquipSubStatusDomain_C.dat")
        if (Files.isRegularFile(iosSacredEquipSubDomain)) {
            sacredEquipSubDomainDefs =
                SacredEquipSubDomainDatLoader.load(Files.readAllBytes(iosSacredEquipSubDomain))
            logger.info {
                "Nạp IOS_SacredEquipSubStatusDomain xong: ${sacredEquipSubDomainDefs.size} bản ghi"
            }
        }

        // SacredEquipUpgrade — DVT DataManager:
        // ReadData("Data/IOS_SacredEquipUpgrade_C.dat", OnLoadSacredEquipUpgradeData).
        val iosSacredEquipUpgrade = dataIosDir.resolve("IOS_SacredEquipUpgrade_C.dat")
        if (Files.isRegularFile(iosSacredEquipUpgrade)) {
            sacredEquipUpgradeDefs = SacredEquipUpgradeDatLoader.load(Files.readAllBytes(iosSacredEquipUpgrade))
            logger.info {
                "Nạp IOS_SacredEquipUpgrade xong: ${sacredEquipUpgradeDefs.size} cấp source=$iosSacredEquipUpgrade"
            }
        } else {
            logger.warn { "Không tìm thấy IOS_SacredEquipUpgrade_C.dat: $iosSacredEquipUpgrade" }
        }

        // Astrolabe — DVT DataManager.ReadData("Data/IOS_Astrolabe.dat").
        val iosAstrolabe = dataIosDir.resolve("IOS_Astrolabe.dat")
        if (Files.isRegularFile(iosAstrolabe)) {
            astrolabeDefs = AstrolabeDatLoader.load(Files.readAllBytes(iosAstrolabe))
            logger.info { "Nạp IOS_Astrolabe xong: ${astrolabeDefs.size} sao source=$iosAstrolabe" }
        } else {
            logger.warn { "Không tìm thấy IOS_Astrolabe.dat: $iosAstrolabe" }
        }

        // Achievement (Định nghĩa thành tựu)
        loadFile(dataDir, "AchievementData_C.dat") { bytes ->
            achievementDefs = AchievementDatLoader.load(bytes)
            logger.info { "Nạp Achievement xong: ${achievementDefs.size} bản ghi" }
        }

        val passAwardFile = dataIosDir.resolve("IOS_PassAward_C.dat")
        if (Files.isRegularFile(passAwardFile)) {
            try {
                passAwardDefs = PassAwardDatLoader.load(Files.readAllBytes(passAwardFile))
                logger.info { "Nạp IOS_PassAward xong: ${passAwardDefs.size} cấp, seasons=${passAwardDefs.map { it.seasonId }.distinct()}" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_PassAward thất bại; BattlePass disabled" }
            }
        }

        val missionAwardFile = dataIosDir.resolve("IOS_MissionAward_C.dat")
        if (Files.isRegularFile(missionAwardFile)) {
            try {
                missionAwardDefs = MissionAwardDatLoader.load(Files.readAllBytes(missionAwardFile))
                logger.info { "Nạp IOS_MissionAward xong: ${missionAwardDefs.size} nhiệm vụ" }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_MissionAward thất bại; mission rewards disabled" }
            }
        }

        // ActivityTime — Lua DVT DataManager đọc Data/IOS_ActivityTime_C.dat.
        // Ưu tiên đúng nguồn IOS; chỉ fallback bản Data cũ nếu IOS không tồn tại.
        val iosActivityTime = dataIosDir.resolve("IOS_ActivityTime_C.dat")
        val activityTimeFile = if (Files.isRegularFile(iosActivityTime)) {
            iosActivityTime
        } else {
            dataDir.resolve("ActivityTime_C.dat")
        }
        if (Files.isRegularFile(activityTimeFile)) {
            try {
                activityTimeDefs = JiugonggeDatLoader.loadActivityTimes(Files.readAllBytes(activityTimeFile))
                logger.info {
                    "Nạp ActivityTime xong: ${activityTimeDefs.size} bản ghi source=$activityTimeFile"
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp ActivityTime thất bại: $activityTimeFile" }
            }
        } else {
            logger.warn { "Không tìm thấy ActivityTime: $activityTimeFile" }
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

        // CollectWarrior — DVT client UICollectBook reads IOS_CollectWarrior_C.dat.
        val iosCollectWarrior = dataIosDir.resolve("IOS_CollectWarrior_C.dat")
        if (Files.exists(iosCollectWarrior)) {
            try {
                collectWarriorDefs = CollectWarriorDatLoader.load(Files.readAllBytes(iosCollectWarrior))
                logger.info {
                    "Nạp CollectWarrior xong: ${collectWarriorDefs.size} võ tướng source=$iosCollectWarrior"
                }
            } catch (e: Exception) {
                logger.error(e) { "Nạp IOS_CollectWarrior_C.dat thất bại: $iosCollectWarrior" }
            }
        } else {
            loadFile(dataDir, "CollectWarrior_C.dat") { bytes ->
                collectWarriorDefs = CollectWarriorDatLoader.load(bytes)
                logger.warn {
                    "IOS_CollectWarrior_C.dat không tồn tại, dùng CollectWarrior_C.dat: ${collectWarriorDefs.size} bản ghi"
                }
            }
        }

        // Remaining Collection Book static data — same IOS tables used by DVT client.
        fun loadIosCollection(name: String, fallback: String, consumer: (ByteArray) -> Unit) {
            val ios = dataIosDir.resolve(name)
            if (Files.exists(ios)) {
                consumer(Files.readAllBytes(ios))
            } else {
                loadFile(dataDir, fallback, consumer)
            }
        }

        loadIosCollection("IOS_CollectStyle_C.dat", "CollectStyle_C.dat") { bytes ->
            collectStyleDefs = CollectionBookDatLoader.loadStyles(bytes)
            logger.info { "Nạp CollectStyle xong: ${collectStyleDefs.size} bộ" }
        }
        loadIosCollection("IOS_CollectStyleValue_C.dat", "CollectStyleValue_C.dat") { bytes ->
            collectStyleValueDefs = CollectionBookDatLoader.loadStyleValues(bytes)
            logger.info { "Nạp CollectStyleValue xong: ${collectStyleValueDefs.size} mốc" }
        }
        loadIosCollection("IOS_CollectNFT_C.dat", "CollectNFT_C.dat") { bytes ->
            collectNftDefs = CollectionBookDatLoader.loadNfts(bytes)
            logger.info { "Nạp CollectNFT xong: ${collectNftDefs.size} bản ghi" }
        }
        loadIosCollection("IOS_CollectCard_C.dat", "CollectCard_C.dat") { bytes ->
            collectCardDefs = CollectionBookDatLoader.loadCards(bytes)
            logger.info { "Nạp CollectCard xong: ${collectCardDefs.size} tổ hợp" }
        }
        loadIosCollection("IOS_CollectCardSort_C.dat", "CollectCardSort_C.dat") { bytes ->
            collectCardSortDefs = CollectionBookDatLoader.loadCardSorts(bytes)
            logger.info { "Nạp CollectCardSort xong: ${collectCardSortDefs.size} thẻ" }
        }

        loadFile(dataIosDir, "IOS_PokeData_C.dat") { bytes ->
            pokeRewardDefs = PokeDatLoader.loadRewards(bytes)
            logger.info { "Nạp IOS_PokeData xong: " + pokeRewardDefs.size + " giải" }
        }
        loadFile(dataIosDir, "IOS_PokeRate_C.dat") { bytes ->
            pokeRateByKind = PokeDatLoader.loadRates(bytes)
            logger.info { "Nạp IOS_PokeRate xong: " + pokeRateByKind }
        }
        loadFile(dataIosDir, "IOS_PokeBonusReward_C.dat") { bytes ->
            pokeBonusRewardDefs = PokeDatLoader.loadBonusRewards(bytes)
            logger.info { "Nạp IOS_PokeBonusReward xong: " + pokeBonusRewardDefs.size + " bản ghi" }
        }

        loadFile(dataIosDir, "IOS_CommonAward_C.dat") { bytes ->
            commonAwardDefs = CommonAwardDatLoader.load(bytes)
        }
        loadFile(dataIosDir, "IOS_LuckyBag2_C.dat") { bytes ->
            luckyBagDefs = CommonAwardDatLoader.loadLuckyBags(bytes)
        }

        // Dispatch (Định nghĩa nhiệm vụ phái cử)
        val dispatchSource = dataIosDir.resolve("IOS_Dispatch_C.dat")
        loadFile(
            if (Files.exists(dispatchSource)) dataIosDir else dataDir,
            if (Files.exists(dispatchSource)) "IOS_Dispatch_C.dat" else "Dispatch_C.dat"
        ) { bytes ->
            dispatchDefs = DispatchDatLoader.loadDispatch(bytes)
            val total = dispatchDefs.values.sumOf { kinds -> kinds.values.sumOf { it.size } }
            logger.info { "Nạp Dispatch xong: $total bản ghi" }
        }

        // DispatchBonus (Hiệu ứng cộng thưởng phái cử)
        val dispatchBonusSource = dataIosDir.resolve("IOS_DispatchBonus_C.dat")
        loadFile(
            if (Files.exists(dispatchBonusSource)) dataIosDir else dataDir,
            if (Files.exists(dispatchBonusSource)) "IOS_DispatchBonus_C.dat" else "DispatchBonus_C.dat"
        ) { bytes ->
            dispatchBonusDefs = DispatchDatLoader.loadBonus(bytes)
            logger.info { "Nạp DispatchBonus xong: ${dispatchBonusDefs.size} bản ghi" }
        }

        // Leaderboard — Lua DVT DataManager đọc Data/IOS_LeaderbordInfo_C.dat.
        // Không dùng LeaderbordInfo_C.dat legacy của server-data vì bộ đó có ID cũ
        // (27, 32..36, 45) không tồn tại trong client DVT hiện tại.
        loadFile(dataIosDir, "IOS_LeaderbordInfo_C.dat") { bytes ->
            leaderboardDefs = LeaderboardDatLoader.load(bytes)
            logger.info {
                "Nạp IOS_LeaderbordInfo xong: ${leaderboardDefs.size} bản ghi source=" +
                    dataIosDir.resolve("IOS_LeaderbordInfo_C.dat")
            }
        }

        // NpcUpgrade — Mobile DVT DataManager đọc Data/IOS_NpcUpgrade_C.dat.
        val iosNpcUpgrade = dataIosDir.resolve("IOS_NpcUpgrade_C.dat")
        if (Files.isRegularFile(iosNpcUpgrade)) {
            npcUpgradeDefs = NpcUpgradeDatLoader.load(Files.readAllBytes(iosNpcUpgrade))
            logger.info { "Nạp IOS_NpcUpgrade xong: ${npcUpgradeDefs.size} bản ghi source=$iosNpcUpgrade" }
        } else {
            loadFile(dataDir, "NpcUpgrade_C.dat") { bytes ->
                npcUpgradeDefs = NpcUpgradeDatLoader.load(bytes)
                logger.warn { "IOS_NpcUpgrade_C.dat không tồn tại, fallback NpcUpgrade_C.dat: ${npcUpgradeDefs.size} bản ghi" }
            }
        }

        // Warrior EXP slot — Lua DVT DataManager đọc Data/IOS_FNpcSlot_C.dat.
        loadFile(dataIosDir, "IOS_FNpcSlot_C.dat") { bytes ->
            fNpcSlotDefs = FNpcSlotDatLoader.load(bytes)
            logger.info { "Nạp IOS_FNpcSlot xong: ${fNpcSlotDefs.size} bản ghi" }
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

    private fun resolvePackedResource(fileName: String): Path? {
        val candidates = listOf(
            basePath.resolve("CompreseData").resolve(fileName),
            gamedataPath.resolve(fileName),
            gamedataPath.resolve("CompreseData").resolve(fileName),
        ).distinct()
        val resolved = candidates.firstOrNull { Files.isRegularFile(it) }
        if (resolved != null && resolved != candidates.first()) {
            logger.warn { "$fileName fallback resource: $resolved" }
        }
        return resolved
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

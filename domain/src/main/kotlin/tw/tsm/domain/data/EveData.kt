package tw.tsm.domain.data

/**
 * Value object của hệ thống sự kiện NPC — tương ứng cấu trúc dữ liệu Eve.emg
 *
 * Nguồn: đường dẫn Lua của client `Data/Eve/`
 *   - Eve_NpcData.lua        → [EveNpcPlacement]
 *   - Eve_NpcEventData.lua   → [NpcEventData]、[EveCondition]、[EveResult]
 */

/**
 * Kết quả sự kiện — tương ứng từng bản ghi trong ResultArray của Eve_NpcEventData.lua
 *
 * Các trường tương ứng 1:1 với gói tin S:020-001 (15 bytes).
 *
 * @param resultGroupNo  S:020-001 resultGroupNo (2B)
 * @param resultNo       S:020-001 resultNo (1B)
 * @param resultType     S:020-001 resultType (1B): 0=hiệu ứng vật phẩm 1=hội thoại 2=cửa 3=chiến đấu 4=di chuyển NPC 5=hoạt ảnh 6=tương tác
 * @param resultClass    S:020-001 resultClass (1B): 3=NPC 7=người chơi
 * @param parameter      S:020-001 parameter (2B): clickId của NPC (dùng để xoay hướng mặt)
 * @param parameterStyle S:020-001 parameterStyle (1B)
 * @param resultValue    S:020-001 resultValue (4B)
 * @param resultMeanNo   S:020-001 resultMeanNo (2B): ID hội thoại (client tra bảng talkDatas[])
 */
data class EveResult(
    val resultGroupNo: Int,
    val resultNo: Int,
    val resultType: Int,
    val resultClass: Int,
    val parameter: Int,
    val parameterStyle: Int,
    val resultValue: Int,
    val resultMeanNo: Int
)

/**
 * Điều kiện sự kiện (kèm ResultArray) — tương ứng từng bản ghi trong ConditionArray của Eve_NpcEventData.lua
 *
 * @param conditionNo        số thứ tự điều kiện
 * @param conditionClass     phân loại điều kiện (0=không điều kiện; 2=bước nhiệm vụ; còn lại=ngữ nghĩa chưa xác minh)
 * @param conditionParameter tham số điều kiện (khi conditionClass=2 là ID nhiệm vụ)
 * @param conditionParameterStyle kiểu tham số điều kiện (ngữ nghĩa thay đổi theo conditionClass)
 * @param conditionOps       toán tử điều kiện (0/1=bằng 2=nhỏ hơn 3=nhỏ hơn hoặc bằng 4=lớn hơn 5=lớn hơn hoặc bằng 6=khác)
 * @param conditionValue     giá trị so sánh của điều kiện
 * @param conditionSubItem   mục con của điều kiện (UInt16, ngữ nghĩa chưa xác minh)
 * @param toResult           nếu điều kiện này thoả thì nhảy đến vị trí kết quả nào (dùng cho cấu trúc chuỗi AND)
 * @param andNum             AND với N điều kiện phía sau (>0 có nghĩa điều kiện này là tiền đề của chuỗi AND)
 * @param results            danh sách kết quả cần thực thi khi điều kiện này thoả
 */
data class EveCondition(
    val conditionNo: Int,
    val conditionClass: Int,
    val conditionParameter: Int,
    val conditionParameterStyle: Int,
    val conditionOps: Int,
    val conditionValue: Int,
    val conditionSubItem: Int,
    val toResult: Int,
    val andNum: Int,
    val results: List<EveResult>
)

/**
 * Dữ liệu sự kiện NPC — tương ứng Eve_NpcEventData.lua
 *
 * @param eveNo      số thứ tự sự kiện (EveNo trong NpcData.events[])
 * @param whenHappen cờ thời điểm kích hoạt (4 boolean):
 *                   [0]=OnClickObject（ClickNpc）、[1]=OnStrokeObject（BumpNpc）、
 *                   [2]=OnIntoObjectArea (ScanNpc), [3]=OnSerStrokeObject (server va chạm)
 * @param conditions danh sách điều kiện (đánh giá theo thứ tự, điều kiện đầu tiên thoả sẽ được thực thi)
 */
data class NpcEventData(
    val eveNo: Int,
    val whenHappen: List<Boolean>,
    val conditions: List<EveCondition>
)

/**
 * Dữ liệu bố trí NPC — tương ứng Eve_NpcData.lua
 *
 * @param id     clickId (chỉ số đính kèm khi client gửi gói tin kích hoạt)
 * @param npcId  ID loại NPC tương ứng trong Npc_C.dat
 * @param events danh sách EveNo của các sự kiện liên kết với NPC này
 * @param x      toạ độ X của NPC (dùng cho S:022-004 khởi tạo và kiểm tra khoảng cách phía server)
 * @param y      toạ độ Y của NPC
 * @param close  NPC có bị đóng hay không (không hiển thị)
 * @param motionType    kiểu tuần tra: 1=đứng yên 2=đi ngẫu nhiên 3=đi trong phạm vi 4=đi theo đường dẫn
 * @param motionBack    khi motionType=4: 1=dừng 2=lặp vòng
 * @param motionCycleNum số vòng lặp đường dẫn (0=vô hạn)
 * @param direction     hướng ban đầu (0-7, ngược chiều kim đồng hồ)
 * @param motionSuspendMS thời gian dừng tại mỗi điểm đường dẫn (ms)
 * @param motionSpeedLv cấp tốc độ di chuyển (1=chậm 2=vừa 3=nhanh)
 * @param motionNodes   danh sách toạ độ điểm tuần tra (x, y)
 * @param traceRadius   bán kính truy kích (>0 thì NPC sẽ đuổi theo người chơi)
 */
data class EveNpcPlacement(
    val id: Int,
    val npcId: Int,
    val events: List<Int>,
    val x: Int = 0,
    val y: Int = 0,
    val close: Boolean = false,
    val motionType: Int = 1,
    val motionBack: Int = 1,
    val motionCycleNum: Int = 0,
    val direction: Int = 0,
    val motionSuspendMS: Int = 0,
    val motionSpeedLv: Int = 1,
    val motionNodes: List<Pair<Int, Int>> = emptyList(),
    val traceRadius: Int = 0,
    /** Khả năng trưởng thành của NPC: 0=không trưởng thành được (tên màu cam), 1=trưởng thành được (tên màu xanh)*/
    val canGrow: Int = 0,
    val isHide: Boolean = false,
    val isVisible: Boolean = false,
)

/**
 * Dữ liệu bố trí điểm dịch chuyển — tương ứng Eve_DoorData.lua
 *
 * Door và NPC dùng chung bảng NpcEventData, nhưng mỗi bên có không gian ID riêng.
 * Khi client kích hoạt Door sẽ gửi C:020-004 (ClickDoor) hoặc C:020-008 (MeetDoor),
 * triggerID = Door.id (không phải clickId của NPC).
 *
 * @param id     ID điểm dịch chuyển (DoorData.id của client)
 * @param events danh sách EveNo của các sự kiện liên kết với điểm dịch chuyển này
 * @param x      toạ độ pixel X của điểm dịch chuyển (UInt16, dùng để kiểm tra khoảng cách phía server)
 * @param y      toạ độ pixel Y của điểm dịch chuyển (UInt16, dùng để kiểm tra khoảng cách phía server)
 */
data class EveDoorPlacement(
    val id: Int,
    val events: List<Int>,
    val x: Int = 0,
    val y: Int = 0
)

/**
 * Dữ liệu thông tin cảnh — tương ứng Eve_SceneInfoData.lua
 *
 * Khi resultType=2 (Door dịch chuyển), result.parameter = giá trị khoá eveNo của bảng này.
 * Server tra bảng này để lấy ID cảnh đích và toạ độ xuất hiện.
 *
 * @param eveNo          số thứ tự sự kiện (key của bảng SceneInfo)
 * @param backgroundNo   ID cảnh đích
 * @param playerAppearX  toạ độ X xuất hiện của người chơi
 * @param playerAppearY  toạ độ Y xuất hiện của người chơi
 * @param direction      hướng mặt
 */
data class EveSceneInfo(
    val eveNo: Int,
    val backgroundNo: Int,
    val playerAppearX: Int,
    val playerAppearY: Int,
    val direction: Int
)

/**
 * Cấu hình kẻ địch chiến đấu — tương ứng ReadEnemy() trong Eve_FightData.lua
 *
 * @param no          mã số kẻ địch
 * @param npcId       ID loại NPC (tương ứng Npc_C.dat)
 * @param locationPos vị trí bàn cờ (tương đối phía kẻ địch: 0-4=hàng trước col2 row0-4, 5-9=hàng sau col3 row0-4)
 * @param ai          chế độ hành vi AI
 */
data class EveFightEnemy(
    val no: Int,
    val npcId: Int,
    val locationPos: Int,
    val ai: Int
) {
    /** Cột bàn cờ (hàng trước kẻ địch=0, hàng sau=1; bên trái quay mặt sang phải)*/
    val col: Int get() = locationPos / 5
    /** Hàng bàn cờ (0-4)*/
    val row: Int get() = locationPos % 5
}

/**
 * Dữ liệu chiến đấu sự kiện — tương ứng Eve_FightData.lua FightData.New()
 *
 * Khi resultType=3, EveResult.resultMeanNo = giá trị key eveNo của bảng này.
 * Server tra bảng này để lấy đội hình và cấu hình chiến đấu của kẻ địch.
 *
 * @param eveNo        mã sự kiện (key của bảng FightData)
 * @param fightType    kiểu chiến đấu
 * @param linkToResult liên kết kết quả (dùng để nhảy sau khi chiến đấu kết thúc)
 * @param leftEnemies  mảng kẻ địch bên trái (nhóm địch chính)
 * @param rightEnemies mảng kẻ địch bên phải (nhóm phụ, thường để trống)
 * @param fightLimit   cờ giới hạn chiến đấu
 */
data class EveFightData(
    val eveNo: Int,
    val fightType: Int,
    val linkToResult: Int,
    val leftEnemies: List<EveFightEnemy>,
    val rightEnemies: List<EveFightEnemy>,
    val fightLimit: Long
)

/**
 * Câu Surface — tương ứng Eve_SurfaceData.lua sentence
 *
 * @param dataId    mã DB (index của Talk.dat / Item.dat / Npc.dat)
 * @param dataKind  nguồn dữ liệu (1=Talk.dat 2=Item.dat 3=Npc.dat)
 * @param style     kiểu hiển thị (1=Label/chữ 2=Button/nút 3=ListItem/lựa chọn menu 4=CloseButton/nút đóng)
 * @param canCut    có xuống dòng hay không (chỉ Button dùng)
 */
data class EveSentence(
    val dataId: Int,
    val dataKind: Int,
    val style: Int,
    val canCut: Boolean
)

/**
 * Dữ liệu tương tác Surface — tương ứng Eve_SurfaceData.lua
 *
 * Khi resultType=6, EveResult.resultMeanNo = giá trị key id của bảng này.
 * Client tra surfaceDatas[resultMeanNo] cục bộ để hiển thị UI.
 *
 * @param id            mã tương tác
 * @param sentenceCount số Sentence (đã khấu trừ dataId=20393 bị bỏ qua)
 * @param optionIndex   index của lựa chọn đầu tiên trong sentences[] (1-based)
 * @param optionCount   số lượng lựa chọn
 * @param optionMode    chế độ kích hoạt menu (0=hội thoại thường 1=cửa sổ mua bán 2=chiêu binh sản xuất 3=dự phòng)
 * @param sentences     danh sách câu (index 1-based, bỏ qua khoảng trống tại dataId=20393)
 */
data class EveSurfaceData(
    val id: Int,
    val sentenceCount: Int,
    val optionIndex: Int,
    val optionCount: Int,
    val optionMode: Int,
    val sentences: Map<Int, EveSentence>
)

/**
 * GroupData — tương ứng Eve_GroupData.lua
 *
 * Cơ chế phân nhóm kết quả: khi `EveResult.resultGroupNo > 0` thì tham chiếu bảng này.
 * Server dựa vào `ProbabilityRateAy` để chọn một `resultNo` từ `MemberNoAy`,
 * Chỉ giữ lại kết quả được chọn.
 *
 * Client lưu `self.Group[group.EveNo] = group` trong `EventData.lua`,
 * Nhưng EventManager/EventHandler không bao giờ dùng → thuần logic phía server.
 *
 * @param eveNo            ID của chính GroupData (= giá trị resultGroupNo được kết quả tham chiếu)
 * @param eventNo          mã sự kiện NpcEvent trực thuộc
 * @param conditionNo      mã điều kiện trực thuộc
 * @param useMode          chế độ lựa chọn
 * @param memberNoAy       danh sách resultNo của các thành viên
 * @param probabilityRateAy trọng số xác suất của từng thành viên
 * @param pickMember       thành viên chọn trước (0=không)
 */
data class EveGroupData(
    val eveNo: Int,
    val eventNo: Int,
    val conditionNo: Int,
    val useMode: Int,
    val memberNoAy: List<Int>,
    val probabilityRateAy: List<Int>,
    val pickMember: Int
)

/**
 * MineData — đối ứng trực tiếp với client lua/Data/Eve/Eve_MineData.lua.
 *
 * Lua DVT ghi rõ EEventTrigger.Mine=7 là "địa lôi (hiện do Server kích hoạt)".
 * sizeKind=0 dùng vùng grid cụ thể; dữ liệu Eve.emg thực tế cho thấy sizeKind=1
 * hầu hết dùng grid 1,1,1,1 và đóng vai trò vùng gặp quái toàn map.
 */
data class EveMinePlacement(
    val id: Int,
    val events: List<Int>,
    val gridX: Int,
    val gridY: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val sizeKind: Int
) {
    fun containsPixel(x: Int, y: Int, blockUnit: Int = 20): Boolean {
        if (sizeKind == 1) return true
        if (gridWidth <= 0 || gridHeight <= 0) return false
        val left = gridX * blockUnit
        val top = gridY * blockUnit
        val right = (gridX + gridWidth) * blockUnit
        val bottom = (gridY + gridHeight) * blockUnit
        return x >= left && x < right && y >= top && y < bottom
    }
}

/**
 * Dữ liệu sự kiện cảnh (NPC + Door + Mine + sự kiện + chiến đấu + Surface + GroupData)
 *
 * @param npcs         clickId → EveNpcPlacement
 * @param doors        doorId  → EveDoorPlacement
 * @param npcEvents    eveNo   → NpcEventData
 * @param sceneInfos   eveNo   → EveSceneInfo (đích dịch chuyển của Door)
 * @param fightDatas   eveNo   → EveFightData (cấu hình kẻ địch của chiến đấu sự kiện)
 * @param surfaceDatas id      → EveSurfaceData (định nghĩa tương tác Surface)
 * @param groupDatas   eveNo   → EveGroupData (chọn phân nhóm kết quả)
 * @param mines        mineId  → EveMinePlacement（server-triggered random encounter zone）
 */
data class SceneEveData(
    val npcs: Map<Int, EveNpcPlacement>,
    val doors: Map<Int, EveDoorPlacement>,
    val npcEvents: Map<Int, NpcEventData>,
    val sceneInfos: Map<Int, EveSceneInfo>,
    val fightDatas: Map<Int, EveFightData> = emptyMap(),
    val surfaceDatas: Map<Int, EveSurfaceData> = emptyMap(),
    val groupDatas: Map<Int, EveGroupData> = emptyMap(),
    val mines: Map<Int, EveMinePlacement> = emptyMap(),
    val goods: Map<Int, EveGoodsPlacement> = emptyMap()
)

/** DVT Eve_GoodsData.lua: NO, DBID, PosX, PosY, HideTime. */
data class EveGoodsPlacement(val id: Int, val itemId: Int, val x: Int, val y: Int, val hideTime: Int)

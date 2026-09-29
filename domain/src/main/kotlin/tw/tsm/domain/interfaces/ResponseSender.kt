package tw.tsm.domain.interfaces

import tw.tsm.domain.data.ExpBottleSlot
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.SparSlot
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.interfaces.repositories.FriendRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.PlayerDataDto
import tw.tsm.domain.valueobjects.ThingData

/**
 * Interface gửi phản hồi — gamelogic chỉ gửi ý định, không đụng định dạng gói tin
 *
 * Tầng gamelogic gửi phản hồi qua interface này,
 * ResponseSenderImpl ở tầng app chịu trách nhiệm tuần tự hoá ý định thành gói tin thật.
 */
interface ResponseSender {

    // ── Liên quan đăng nhập ──

    /**
     * Gửi kết quả đăng nhập (S:001-002)
     *
     * @param serverTime OLE Automation Date（Double）
     */
    fun sendLoginResult(
        session: SessionSender,
        resultCode: Int,
        dbId: Int = 0,
        gsId: Int = 0,
        playerId: Long = 0,
        serverTime: Double = 0.0,
        account: String = "",
        password: String = "",
        accountKind: Int = 0
    )

    /**
     * Gửi đăng nhập thành công (S:001-003)
     */
    fun sendLoginSuccess(session: SessionSender, needChangePassword: Boolean = false)

    /**
     * Gửi hoàn tất chuỗi đăng nhập (S:001-013)
     */
    fun sendLoginFinished(session: SessionSender, connectCode: Long)

    // ── Dữ liệu người chơi ──

    /**
     * Gửi gói dữ liệu lớn của người chơi (S:005-003)
     */
    fun sendPlayerData(session: SessionSender, data: PlayerDataDto)

    /**
     * Gửi số lần dùng đan dược (S:005-011)
     */
    fun sendPillCounts(
        session: SessionSender,
        attrPill: Int,
        hpPill: Int,
        spPill: Int,
        skillPill: Int
    )

    // ── Xã hội/thời gian ──

    /**
     * Gửi số ngày đăng nhập (S:003-001)
     */
    fun sendLoginDay(session: SessionSender, loginDay: Int, continueLogin: Int)

    /**
     * Gửi thời gian trực tuyến mỗi ngày (S:003-002)
     */
    fun sendOnlineTime(session: SessionSender, time: Double)

    /**
     * Gửi thời hạn thẻ bán nguyệt (S:003-003)
     */
    fun sendHalfMonthCardTime(session: SessionSender, time: Double)

    // ── Nhân vật đi vào ──

    /**
     * Gửi người chơi xuất hiện (S:003-000)
     *
     * Nguồn: protocal.lua protocolTable[3][0] → Role.PlayerAppear
     *
     * @param playerId ID người chơi (khớp với S:001-002)
     * @param record dữ liệu nhân vật
     * @param serverId ID server
     */
    fun sendPlayerAppear(session: SessionSender, playerId: Long, record: CharacterRecord, serverId: Int = 1, turn3Element: Int = 0)

    // ── Liên quan tạo nhân vật ──

    /**
     * Gửi tạo nhân vật thành công (S:009-001)
     *
     * Client nhận xong sẽ đóng UICreateRole và tự gửi C:003 để chọn nhân vật
     */
    fun sendCreateCharSuccess(session: SessionSender)

    /**
     * Gửi kết quả kiểm tra tên (S:009-003)
     *
     * @param result 0=dùng được, 1=trùng tên, 2=không hợp lệ, 3=lỗi khác
     */
    fun sendCheckNameResult(session: SessionSender, result: Int)

    // ── Chung ──

    /**
     * Gửi phản hồi heartbeat (S:010-000)
     */
    fun sendKeepalive(session: SessionSender)

    /**
     * Gửi thông báo mất kết nối (S:000-000)
     */
    fun sendDisconnect(session: SessionSender, reason: Int)

    // ── Liên quan di chuyển ──

    /**
     * Phát rộng di chuyển của người chơi cho những người chơi khác cùng bản đồ (S:006-001)
     *
     * Nguồn: protocal.lua protocolTable[6][1]
     * Định dạng: +roleId(8) +direction(1) +destX(2) +destY(2)
     *
     * @param sender session khởi phát di chuyển (dùng để lấy mapId mà phát rộng, không gửi cho chính nó)
     */
    fun sendPlayerMove(sender: GameSession, playerId: Long, direction: Int, x: Int, y: Int)

    // ── Liên quan trò chuyện ──

    /**
     * Phát rộng tin nhắn trò chuyện tới mọi người chơi trực tuyến (S:002-{channel})
     *
     * Nguồn: protocal.lua protocolTable[2][0~7]
     * Định dạng: +senderId(8) +titleId(2) +senderNameLen(1) +senderName(Big5)
     *       +messageLen(1) +message(Big5) +itemCount(1) +npcCount(1)
     */
    fun sendChatMessage(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String
    )

    /**
     * Phát tin nhắn trò chuyện tới người chơi trong bản đồ chỉ định (kênh nhẹ) (S:002-002)
     */
    fun sendChatMessageToMap(
        sender: GameSession, channel: ChatChannel,
        senderId: Long, senderName: String, content: String,
        mapId: Int
    )

    /**
     * Gửi mật ngữ tới người chơi chỉ định (S:002-003)
     */
    fun sendWhisperMessage(
        target: SessionSender, senderId: Long, senderName: String, content: String,
        titleId: Int = 0
    )

    // ── Tin nhắn hệ thống ──

    /**
     * Gửi tin nhắn hệ thống tới một người chơi (S:002-000)
     *
     * Mục đích: phản hồi lệnh GM, thông báo hệ thống.
     * Dùng ChatChannel.SYSTEM (subKind=0), chỉ gửi cho session chỉ định, không phát rộng.
     */
    fun sendSystemMessage(session: SessionSender, content: String)

    // ── Liên quan cảnh/bản đồ ──

    /**
     * Gửi danh sách phân khu (S:007-001)
     *
     * Định dạng: +count(1) + [index(2) + currentPlayers(2) + maxPlayers(2)]...
     */
    fun sendAreaList(session: SessionSender, onlineCount: Int)

    /**
     * Gửi kết quả chuyển phân khu (S:007-002)
     *
     * @param resultCode 1=cùng phân khu 2=không tìm thấy 3=đang trong tổ đội 4=đã đầy, khác=thành công
     */
    fun sendAreaSwitchResult(session: SessionSender, resultCode: Int)

    /**
     * Gửi người chơi dịch chuyển tức thời (S:007-000)
     *
     * Định dạng: +roleId(8) +sceneId(2) +x(2) +y(2)
     */
    fun sendPlayerTeleport(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int)

    /**
     * Gửi chuyển cảnh (S:012-000)
     *
     * Định dạng: +roleId(8) +sceneId(2) +x(2) +y(2) +tag(2) +instanceId(2)
     *       +equipCount(1) +[itemId(2)]... +outfitCount(1) +[outfitId(2)]... +titleId(2)
     */
    fun sendSceneChange(session: SessionSender, playerId: Long, sceneId: Int, x: Int, y: Int, equipItemIds: IntArray = intArrayOf(), titleId: Int = 0)

    /**
     * Gửi người chơi khác xuất hiện (S:003-000)
     *
     * Dùng dữ liệu session đã biết để dựng gói tin người chơi xuất hiện bản đơn giản
     */
    fun sendOtherPlayerAppear(target: SessionSender, other: GameSession)

    /**
     * Phát rộng việc người chơi đổi danh hiệu (S:005-012)
     *
     * Định dạng: +roleId(8) +titleId(2)
     */
    fun sendTitleChange(target: SessionSender, roleId: Long, titleId: Int)

    // ── Liên quan sự kiện NPC ──

    /**
     * Gửi thông báo sự kiện đổi cảnh (S:020-007)
     *
     * Báo cho client sắp chuyển cảnh (EventManager.ReceiveChangeSceneEvent).
     * Phải gửi trước S:012-000; client nhận xong sẽ đặt isChangingScene=true và chuẩn bị đổi cảnh.
     */
    fun sendEventChangeScene(session: SessionSender)

    /**
     * Gửi kết quả sự kiện thường (S:020-001)
     *
     * Nguồn: protocal.lua protocolTable[20][1] → EventManager.ReceiveCommonEvent
     * Định dạng (15 bytes LE):
     *   +resultDiagnosis(1) +resultGroupNo(2) +resultNo(1) +resultType(1)
     *   +resultClass(1) +parameter(2) +parameterStyle(1) +resultValue(4) +resultMeanNo(2)
     *
     * @param result một phần tử ResultArray của Eve_NpcEventData.lua
     */
    fun sendCommonEvent(session: SessionSender, result: EveResult)

    /**
     * Gửi kết thúc sự kiện (S:020-008)
     *
     * Báo client xoá trạng thái Running của EventManager.
     * Nguồn: protocal.lua protocolTable[20][8] → EventManager.ReceiveEndEvent
     *
     * @param endKind loại kết thúc (0=kết thúc bình thường, 45=chết dừng dẫn đường, 46=chết)
     */
    fun sendEventEnd(session: SessionSender, endKind: Int = 0)

    /**
     * Mở giao diện chuyển sinh lần 1 của người chơi (S:036-011)
     *
     * Nguồn: protocal.lua protocolTable[36][11] → UI.Open(UITurn, ETrunMode.Reincarnation, 1).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=12.
     */
    fun sendOpenPlayerReincarnation(session: SessionSender)

    /**
     * Mở cửa hàng đổi điểm diệt địch (S:036-013)
     *
     * Nguồn: protocal.lua protocolTable[36][13] → UI.Open(UIShop, EUIShop.KillPoint, options).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=13.
     */
    fun sendOpenKillPointShop(session: SessionSender)

    /**
     * Mở giao diện thủy chiến Phụng Ấp hoặc tiếp nối sự kiện (S:036-015)
     *
     * Nguồn: protocal.lua protocolTable[36][15], hiện UI thủy chiến của client đã tắt, chỉ gọi
     * EventManager.SetConduct(true) để luồng sự kiện đi tiếp.
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=15.
     */
    fun sendOpenWaterWarOrgManage(session: SessionSender)

    /**
     * Mở giao diện nhị chuyển của tướng (S:044-003)
     *
     * Nguồn: protocal.lua protocolTable[44][3] → UI.Open(UINpcTurn, 2).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=21.
     */
    fun sendOpenNpcSecondReincarnation(session: SessionSender)

    /**
     * Mở giao diện chuyển sinh tướng (nhất chuyển) (S:044-002)
     *
     * Nguồn: protocal.lua protocolTable[44][2] → UI.Open(UINpcTurn, 1).
     * Quét toàn bộ Eve.emg xác nhận không có lối vào tương ứng (docs/eve-gap-analysis.md §5),
     * suy đoán do hệ thống nhiệm vụ kích hoạt; hiện tại kích hoạt bằng lệnh GM /武將轉生.
     */
    fun sendOpenNpcReincarnation(session: SessionSender)

    /**
     * Gửi tin nhắn chuyển sinh tướng (S:044-001)
     *
     * Định dạng: +loại(1)
     *
     * @param msgKind 0=chuyển sinh thành công, 1=không có tín vật, 2=không có tướng để chuyển sinh, 3=cấp <30, 4/9=tướng sau chuyển sinh đã ở lữ quán,
     *   5=tướng sau chuyển sinh đã mang trên người, 6=không thể trưởng thành, 7=trung thành <60, 8=ô trang bị chưa trống,
     *   10=cấp <60, 11=trung thành <100, 255=lỗi ngoại lệ
     */
    fun sendPetRebornMessage(session: SessionSender, msgKind: Int)

    /**
     * Gửi tin nhắn lỗi tìm ác ma Ba Đậu Yêu (S:020-027)
     *
     * Nguồn: protocal.lua protocolTable[20][27], client hiển thị string.Get(30099).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=27.
     */
    fun sendBadoErrorMessage(session: SessionSender)

    /**
     * Gửi tin nhắn bắt đầu tìm ác ma Ba Đậu Yêu (S:020-029)
     *
     * Nguồn: protocal.lua protocolTable[20][29], client hiển thị chat hệ thống và chạy chữ string.Get(30101).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=29.
     */
    fun sendBadoStartMessage(session: SessionSender)

    /**
     * Mở bảng xếp hạng Vô Giới (S:036-010)
     *
     * Nguồn: protocal.lua protocolTable[36][10].
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=10.
     */
    fun sendOpenGalaxyLeaderboard(session: SessionSender)

    /**
     * Gửi tin nhắn hoạt động Giáng Sinh (S:020-030)
     *
     * Nguồn: protocal.lua protocolTable[20][30], client hiển thị string.Get(30107).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=30.
     */
    fun sendChristmasEventMessage(session: SessionSender)

    /**
     * Gửi tin nhắn lỗi đấu đài (S:020-032)
     *
     * Nguồn: protocal.lua protocolTable[20][32], client hiển thị string.Get(30110).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=32.
     */
    fun sendArenaErrorMessage(session: SessionSender)

    /**
     * Gửi tin nhắn lỗi Tết Nguyên Tiêu (S:020-035)
     *
     * Nguồn: protocal.lua protocolTable[20][35], client hiển thị string.Get(30117).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=35.
     */
    fun sendLanternErrorMessage(session: SessionSender)

    /**
     * Gửi tin nhắn hoạt động chưa xác định (S:020-039)
     *
     * Nguồn: protocal.lua protocolTable[20][39], client hiển thị string.Get(30125).
     * Được kích hoạt bởi Eve resultType=7/resultClass=7/pStyle=39.
     */
    fun sendUnknownActivityMessage(session: SessionSender)

    // ── Hệ thống nhiệm vụ (mainKind=24) ──

    /**
     * Gửi tăng bước nhiệm vụ (S:024-001)
     *
     * Tương ứng MarkManager.AddMissionStep().
     * Định dạng: +missionId(2B LE) +step(1B)
     *
     * @param missionId ID nhiệm vụ
     * @param step      lượng tăng bước
     */
    fun sendAddMissionStep(session: SessionSender, missionId: Int, step: Int)

    /**
     * Gửi giảm bước nhiệm vụ (S:024-002)
     *
     * Tương ứng MarkManager.DecMissionStep().
     * Định dạng: +missionId(2B LE) +step(1B)
     *
     * @param missionId ID nhiệm vụ
     * @param step      lượng giảm bước
     */
    fun sendDecMissionStep(session: SessionSender, missionId: Int, step: Int)

    /**
     * Gửi xóa nhiệm vụ (S:024-004)
     *
     * Tương ứng MarkManager.RemoveMission().
     * Định dạng: +missionId(2B LE)
     *
     * @param missionId ID nhiệm vụ
     */
    fun sendRemoveMission(session: SessionSender, missionId: Int)

    /**
     * Gửi khởi tạo bước nhiệm vụ (S:024-006)
     *
     * Khi đăng nhập gửi xuống tất cả nhiệm vụ đang tiến hành, tương ứng MarkManager.InitMissionStep().
     * Định dạng: +count(4B LE) +<<index(1B) +missionId(2B LE) +step(1B)>>
     *
     * @param missions danh sách nhiệm vụ đang tiến hành (có thể trống)
     */
    fun sendInitMissionSteps(session: SessionSender, missions: List<MissionRecord>)

    /**
     * Gửi khởi tạo cờ nhiệm vụ (S:024-007)
     *
     * Khi đăng nhập gửi xuống tất cả cờ nhiệm vụ đã hoàn thành, tương ứng MarkManager.InitMissionFlag().
     * Định dạng: +count(2B LE) +<<bitIndex(2B LE) +bitValue(1B)>>
     *
     * @param flags danh sách cờ nhiệm vụ đã hoàn thành (có thể trống)
     */
    fun sendInitMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>)

    /**
     * Gửi thiết lập cờ nhiệm vụ (S:024-005)
     *
     * Khi nhiệm vụ hoàn thành gửi xuống, tương ứng MarkManager.SetMissionFlag().
     * Định dạng: +count(4B LE UInt32) <<+bitIndex(2B LE UInt16) +bitValue(1B Boolean)>>
     *
     * Lưu ý: định dạng khác với S:024-007!
     * - S:024-005: count=UInt32(4B), bitValue=Boolean(1B)
     * - S:024-007: count=UInt16(2B), bitValue=Byte(1B)
     *
     * @param flags danh sách cờ cần thiết lập
     */
    fun sendSetMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>)

    // ── Hệ thống cờ vĩnh viễn (mainKind=81) ──

    /**
     * Gửi khởi tạo cờ vĩnh viễn (S:081-002)
     *
     * Khi đăng nhập gửi xuống tất cả cờ vĩnh viễn, tương ứng BitFlag.Init().
     * Định dạng: +size(2B LE UInt16) +mảng byte cờ vĩnh viễn(size×1B)
     *
     * @param packedFlags mảng byte cờ vĩnh viễn sau khi đóng gói
     */
    fun sendInitBitFlags(session: SessionSender, packedFlags: ByteArray)

    /**
     * Gửi thay đổi cờ vĩnh viễn (S:081-001)
     *
     * Cập nhật cờ vĩnh viễn lúc vận hành, tương ứng BitFlag.Set().
     * Định dạng: +count(4B LE UInt32) <<+flagId(2B LE UInt16) +value(1B Boolean)>>
     *
     * @param changes danh sách (flagId, value)
     */
    fun sendBitFlagChanges(session: SessionSender, changes: List<Pair<Int, Boolean>>)

    // ── Liên quan túi đồ/vật phẩm (mainKind=23) ──

    /**
     * Gửi toàn bộ vật phẩm trong túi đồ (S:023-005)
     *
     * Định dạng: +refresh(1) +count(2) +<<slot(1) +ThingData(35)>>
     */
    fun sendAllBagItems(session: SessionSender, items: Map<Int, ThingData>, refresh: Boolean = false)

    /**
     * Gửi thiết lập vật phẩm túi đồ (S:023-008)
     *
     * Định dạng: +slot(1) +ThingData(35) +showMsg(1)
     */
    fun sendSetBagItem(session: SessionSender, slot: Int, thing: ThingData, showMsg: Boolean = true)

    /**
     * Gửi giảm vật phẩm túi đồ (S:023-009)
     *
     * Định dạng: +slot(1) +amount(4)
     */
    fun sendReduceBagItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * Gửi di chuyển vật phẩm túi đồ (S:023-010)
     *
     * Định dạng: +fromSlot(1) +amount(4) +toSlot(1)
     */
    fun sendMoveBagItem(session: SessionSender, fromSlot: Int, amount: Int, toSlot: Int)

    /**
     * Gửi toàn bộ trang bị (S:023-011)
     *
     * Định dạng: +count(1) +<<ThingData(35)>>
     * Client dùng ItemDef.fitType để xác định ô.
     */
    fun sendAllEquipment(session: SessionSender, equips: Map<Int, ThingData>)

    /**
     * Gửi xóa trang bị (S:023-012)
     *
     * Định dạng: +fitType(1)
     */
    fun sendDeleteEquip(session: SessionSender, fitType: Int)

    /**
     * Gửi sử dụng vật phẩm thành công (S:023-015)
     *
     * Định dạng: +itemId(2) +count(4) +showMsg(1)
     */
    fun sendUseItemSuccess(session: SessionSender, itemId: Int, count: Int, showMsg: Boolean = true)

    /**
     * Gửi tháo trang bị (S:023-016)
     *
     * Định dạng: +fitType(1) +bagSlot(1)
     */
    fun sendUnequip(session: SessionSender, fitType: Int, bagSlot: Int)

    /**
     * Gửi mặc trang bị (S:023-017)
     *
     * Định dạng: +bagSlot(1)
     */
    fun sendEquip(session: SessionSender, bagSlot: Int)

    /**
     * Gửi tướng tháo trang bị (xóa trực tiếp, dùng khi vứt) (S:023-021)
     *
     * Định dạng: +followSlot(1) +fitType(1)
     */
    fun sendPetEquipDelete(session: SessionSender, followSlot: Int, fitType: Int)

    /**
     * Gửi tướng tháo trang bị vào túi đồ (S:023-022)
     *
     * Định dạng: +followSlot(1) +fitType(1) +bagSlot(1)
     */
    fun sendPetUnequipToBag(session: SessionSender, followSlot: Int, fitType: Int, bagSlot: Int)

    /**
     * Gửi tướng mặc trang bị (S:023-023)
     *
     * Định dạng: +followSlot(1) +bagSlot(1)
     * Client nhận xong tự hoán đổi giữa ô túi đồ ↔ ô trang bị.
     */
    fun sendPetEquip(session: SessionSender, followSlot: Int, bagSlot: Int)

    /**
     * Gửi kết quả nhặt (S:023-025)
     *
     * Định dạng: +result(1) [0=thành công:+kind(2)+value(2)+x(2)+y(2), 1=không thể nhặt, 2=quá xa]
     */
    fun sendPickupResult(session: SessionSender, result: Int, kind: Int = 0, value: Int = 0, x: Int = 0, y: Int = 0)

    /**
     * Phát rộng thêm vật phẩm trong cảnh (S:023-003)
     *
     * Định dạng: +itemId(2) +x(2) +y(2) +isMe(1) +canPickPlayerId(8)
     *
     * @param canPickPlayerId ID người chơi được nhặt (0 = tất cả mọi người đều nhặt được)
     */
    fun sendAddGroundItem(session: SessionSender, itemId: Int, x: Int, y: Int, isMe: Int, canPickPlayerId: Long = 0L)

    /**
     * Phát rộng xóa vật phẩm trong cảnh (S:023-002)
     *
     * Định dạng: +index(2) +isMe(1)
     */
    fun sendRemoveGroundItem(session: SessionSender, index: Int, isMe: Boolean)

    /**
     * Phát rộng vứt tiền xuống cảnh (S:023-018)
     *
     * Định dạng: +amount(4) +x(2) +y(2) +isMe(1)
     */
    fun sendDropMoneyToGround(session: SessionSender, amount: Int, x: Int, y: Int, isMe: Boolean)

    // ── Giao dịch người chơi (mainKind=25) ──

    /**
     * Gửi bắt đầu giao dịch vật phẩm (S:025-001)
     *
     * Gửi cho bên được mời, client nhận xong mở UITrade.
     * Định dạng: +partnerId(8)
     */
    fun sendTradeStart(session: SessionSender, partnerId: Long)

    /**
     * Gửi hủy/hoàn thành giao dịch vật phẩm (S:025-002)
     *
     * reason đối chiếu nghiêm ngặt với protocal.lua:6189-6212 (TextData 40010~40019):
     * 0=hệ thống bận, 1=đối phương mất kết nối, 2=đối phương đang bận, 3=đối phương hủy,
     * 4=hai bên giao dịch hoàn thành, 5=khác cảnh, 6=không đủ tiền vàng, 7=quá nhiều vật phẩm, 8=đang chiến đấu, khác=hệ thống gián đoạn
     * Định dạng: +reason(1)
     */
    fun sendTradeCancel(session: SessionSender, reason: Int)

    /**
     * Gửi xác nhận giao dịch vật phẩm — hiển thị đề giá của đối phương (S:025-003)
     *
     * Sau khi hai bên gửi lên, server gửi cho mỗi bên nội dung đề giá của bên kia.
     * Định dạng: +money(4) +<<ThingData(35)>>
     */
    fun sendTradeConfirm(session: SessionSender, money: Int, items: List<ThingData>)

    /**
     * Gửi bắt đầu giao dịch tướng (S:025-010)
     *
     * Gửi cho bên được mời, client nhận xong mở UITrade chế độ tướng.
     * Định dạng: +partnerId(8)
     */
    fun sendPetTradeStart(session: SessionSender, partnerId: Long)

    /**
     * Gửi hủy/hoàn thành giao dịch tướng (S:025-011)
     *
     * reason 0~8 giống S:025-002; riêng của tướng: 9=dữ liệu đối chiếu có sai khác, 10=trên người đã đủ 4 tướng,
     * 11=trùng lữ quán, 12=lòng trung thành bằng 0, 13=trùng xe ngựa, 14=trên người có vật phẩm không thể chuyển nhượng
     * Định dạng: +reason(1)
     */
    fun sendPetTradeCancel(session: SessionSender, reason: Int)

    /**
     * Gửi xác nhận giao dịch tướng — hiển thị đề giá của đối phương (S:025-012)
     *
     * Định dạng: +money(4) +npcId(2) [+cấp độ(1) +MaxHp(2) +Hp(2) +MaxSp(2) +Sp(2)
     * +L(1)+tên(L) +lòng trung thành(1) +đan thăng hoa(1) +đan SP(1) +đan HP(1) +số trang bị(1) <<+ThingData(35)>>]
     * Khi pet null thì chỉ gửi money + npcId=0 (đối phương không ra tướng).
     */
    fun sendPetTradeConfirm(session: SessionSender, money: Int, pet: FollowNpcData?, maxHp: Int, maxSp: Int)

    // ── Liên quan tiền (mainKind=26) ──

    /**
     * Gửi khởi tạo tiền (S:026-004)
     *
     * Định dạng: +gold(4) +auctionGold(4)
     */
    fun sendInitMoney(session: SessionSender, gold: Int, auctionGold: Int)

    /**
     * Gửi tăng tiền (S:026-001)
     *
     * Định dạng: +amount(4)
     */
    fun sendAddMoney(session: SessionSender, amount: Int)

    /**
     * Gửi giảm tiền (S:026-002)
     *
     * Định dạng: +amount(4)
     */
    fun sendSubtractMoney(session: SessionSender, amount: Int)

    /**
     * Gửi dung lượng tiền đã đầy (S:026-003)
     *
     * Định dạng: +kind(1) [1=đầy tiền, 2=đầy tiền đấu giá]
     */
    fun sendMoneyFull(session: SessionSender, kind: Int)

    // ── NPC mua bán (mainKind=27) ──

    /**
     * Gửi kết quả mua đồ (S:027-001)
     *
     * Định dạng: +result(1)
     */
    fun sendBuyResult(session: SessionSender, result: Int)

    /**
     * Gửi kết quả bán đồ (S:027-002)
     *
     * Định dạng: +result(1) +count(1) +<<bagIndex(1)>>
     */
    fun sendSellResult(session: SessionSender, result: Int, bagIndices: List<Int>)

    /**
     * S:027-003 mở giao diện NPC bán đồ
     *
     * ```
     * [mainKind 1B] [subKind 1B] [compress 1B] [kind 1B]
     * ```
     * kind: 1=cửa hàng sự kiện 2=quán tạp hóa mang theo
     */
    fun sendOpenNpcStore(session: SessionSender, kind: Int)

    /**
     * Gửi dữ liệu bùa dịch chuyển đã nhớ (S:023-118)
     *
     * Gửi khi đăng nhập hoặc sau thao tác nhớ, client cập nhật UITeleport.flyPoints.
     *
     * ```
     * [mainKind=23 1B] [subKind=118 1B] [compress=0 1B]
     * [flyPointCount 1B] [count 1B]
     * <<[index 1B] [sceneId 2B LE] [x 2B LE] [y 2B LE]>>
     * ```
     *
     * @param session kết nối của người chơi
     * @param flyPointCount số bùa dịch chuyển nhớ bổ sung đã mở
     * @param points các điểm dịch chuyển đã nhớ Map<chỉ số(1-based), FlyPointRecord>
     */
    fun sendFlyPointData(session: SessionSender, flyPointCount: Int, points: Map<Int, FlyPointRecord>)

    // ── Cường hóa tướng (mainKind=74, 8, 15) ──

    /**
     * Gửi dữ liệu tạm khi thao tác tướng (S:008-011)
     *
     * Xem trước thuộc tính cường hóa/thoát thai hoán cốt, client lưu vào Role.rebornNpcId/rebornAttributes.
     * Định dạng: +loại thao tác(1) +ID tướng(2) +thuộc tính tướng(1)×6
     * Thứ tự dòng thuộc tính: int, atk, def, agi, hpx, spx
     *
     * @param kind 1=cường hóa, 2=thoát thai hoán cốt
     * @param gains giá trị tăng của sáu chỉ số [int, atk, def, agi, hpx, spx]
     */
    fun sendPetTempUpgradeData(session: SessionSender, kind: Int, npcId: Int, gains: IntArray)

    /**
     * Gửi cấp cường hóa tướng (S:015-020)
     *
     * Định dạng: +chỉ số tướng(1) +cấp cường hóa(1)
     */
    fun sendPetUpgradeLv(session: SessionSender, followIndex: Int, upgradeLv: Int)

    /**
     * Gửi kết quả cường hóa tướng (S:074-002 thành công / S:074-003 hủy)
     *
     * Cả hai đều payload rỗng, client xóa trạng thái tạm cường hóa.
     *
     * @param success true=S:074-002 cường hóa thành công, false=S:074-003 hủy
     */
    fun sendPetUpgradeResult(session: SessionSender, success: Boolean)

    // ── 介消 (mua bán qua bảng) (mainKind=84) ──

    /**
     * Gửi 介消 thất bại (S:084-001 / S:084-002 kết quả=0)
     *
     * Định dạng: +mã số 介消(2) +kết quả(1)=0 +nguyên nhân(1) +giá trị(4)
     * Mã nguyên nhân đối chiếu string.GetUiSellFail: 1=không có dữ liệu 介消, 5=không đủ tiền, 7=khấu trừ vật phẩm thất bại,
     * 11=đã đạt mức tối đa, 12=không đủ nguyên bảo, 15=không thể sử dụng, 17=cảnh hiện tại không thể dịch chuyển
     *
     * @param subKind 1=phản hồi truy vấn, 2=phản hồi kết quả
     * @param reason mã nguyên nhân thất bại
     * @param value giá trị kèm theo (khi nguyên nhân 2 thiếu cấp độ thì là cấp độ yêu cầu, còn lại 0)
     */
    fun sendUiSellFail(session: SessionSender, subKind: Int, sellId: Int, reason: Int, value: Long = 0)

    /**
     * Gửi báo giá tiền 介消 (S:084-001 kết quả 1/2/3)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1) +số tiền(4)
     *
     * @param currencyKind 1=nguyên bảo, 2=nguyên bảo khoá, 3=tiền game
     * @param cost số tiền tiêu phí
     */
    fun sendUiSellMoneyQuery(session: SessionSender, sellId: Int, currencyKind: Int, cost: Long)

    /**
     * Gửi báo giá vật phẩm 介消 (S:084-001 kết quả=255)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=255 +ID vật phẩm(2) +số lượng(2)
     * ⚠ protocal.lua chú thích ghi số lượng(1), nhưng client thực tế dùng ReadUInt16 — lấy mã đọc của Lua làm chuẩn
     */
    fun sendUiSellItemQuery(session: SessionSender, sellId: Int, itemId: Int, count: Int)

    /**
     * Gửi báo giá miễn phí 介消 (S:084-001 kết quả=254)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=254
     */
    fun sendUiSellFreeQuery(session: SessionSender, sellId: Int)

    /**
     * Gửi kết quả 介消 thành công (S:084-002)
     *
     * Định dạng: +số hiệu 介消(2) +kết quả(1)=1
     */
    fun sendUiSellSuccess(session: SessionSender, sellId: Int)

    // ── Điểm (mainKind=35) ──

    /**
     * Gửi điểm nguyên bảo còn lại (S:035-005)
     *
     * Định dạng: +điểm thực(4) +điểm ảo(4)
     * Client dùng Role.SetPoint(real) / Role.SetLockPoint(virtual) để cập nhật hiển thị.
     */
    fun sendPointBalance(session: SessionSender, pointReal: Int, pointVirtual: Int)

    /** S:035-050 DVT generic player currency. */
    fun sendPlayerCurrency(session: SessionSender, currencyKind: Int, value: Int)

    /** S:023-150 Doro draw results; one entry per egg/draw. */
    fun sendDoroResult(session: SessionSender, rewards: List<tw.tsm.domain.data.DoroReward>)

    /** S:066-003 Mall purchase result. */
    fun sendMallPurchaseResult(session: SessionSender, itemId: Int, result: Int)

    // ── Tiền Trang (mainKind=30) ──

    /**
     * Gửi tất cả vật phẩm Tiền Trang (S:030-001)
     *
     * Định dạng: <<+slot(1) +ThingData(35)>>
     */
    fun sendBankAllItems(session: SessionSender, items: Map<Int, ThingData>)

    /**
     * Gửi thiết lập vật phẩm Tiền Trang (S:030-004)
     *
     * Định dạng: +slot(1) +ThingData(35)
     */
    fun sendBankSetItem(session: SessionSender, slot: Int, thing: ThingData)

    /**
     * Gửi giảm vật phẩm Tiền Trang (S:030-005)
     *
     * Định dạng: +slot(1) +amount(4)
     */
    fun sendBankReduceItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * Gửi thao tác Tiền Trang thất bại (S:030-007)
     *
     * Định dạng: +failCode(1) [3=lưu vào thất bại, 13=Tiền Trang đã đầy]
     */
    fun sendBankFail(session: SessionSender, failCode: Int)

    /**
     * Gửi xoá sạch vật phẩm Tiền Trang (S:030-008)
     */
    fun sendBankClear(session: SessionSender)

    /**
     * Mở UI Tiền Trang (S:029-006)
     *
     * Server ra lệnh cho client mở UIBank.
     * Tương ứng protocal.lua protocolTable[29][6]
     */
    fun sendOpenBank(session: SessionSender)

    /**
     * Gửi cấp độ Tiền Trang (S:029-009)
     *
     * Tương ứng protocal.lua protocolTable[29][9] — Role.SetBankLv(level)
     *
     * @param level cấp độ Tiền Trang
     */
    fun sendBankLevel(session: SessionSender, level: Int)

    // ── Lữ quán (mainKind=31) ──

    /**
     * S:031-002 xác nhận chi phí lưu trú
     *
     * Client sau khi nhận hiển thị hộp thoại chi phí, người chơi xác nhận rồi gửi C:031-001 (InnHandler.handleRest).
     * Hệ thống sự kiện resultType=7 cls=7 pStyle=5 kích hoạt.
     */
    fun sendInnRestConfirm(session: SessionSender, cost: Int)

    /**
     * Mở UI UINpcInn (S:031-015 cấp độ + S:031-006 dữ liệu tướng + S:031-007 mở UI)
     *
     * Hệ thống sự kiện resultType=7 cls=7 pStyle=4 kích hoạt.
     * UINpcInn là UI hai tab dùng chung cho kho tướng + lưu trữ lữ quán.
     */
    fun sendOpenInn(session: SessionSender, innLevel: Int, innPets: Map<Int, FollowNpcData>)

    /**
     * S:122-001 thông tin kho tướng
     *
     * Phản hồi truy vấn C:122-001 của client (UINpcInn OnOpen tự động gửi).
     */
    fun sendOpenWarehouse(session: SessionSender, warehousePets: List<FollowNpcData>)

    // ── Bảo khố (mainKind=102) ──

    /**
     * Gửi tất cả vật phẩm bảo khố (S:102-001)
     *
     * Định dạng: <<+slot(1) +ThingData(35)>>
     */
    fun sendStorageAllItems(session: SessionSender, items: Map<Int, ThingData>)

    /**
     * Gửi thiết lập vật phẩm bảo khố (S:102-004)
     *
     * Định dạng: +slot(1) +ThingData(35)
     */
    fun sendStorageSetItem(session: SessionSender, slot: Int, thing: ThingData)

    /**
     * Gửi giảm vật phẩm bảo khố (S:102-005)
     *
     * Định dạng: +slot(1) +amount(4)
     */
    fun sendStorageReduceItem(session: SessionSender, slot: Int, amount: Int)

    /**
     * Gửi thao tác bảo khố thất bại (S:102-007)
     *
     * Định dạng: +failCode(1) [3=lưu vào thất bại, 13=bảo khố đã đầy, 14=vật phẩm không xếp chồng được]
     */
    fun sendStorageFail(session: SessionSender, failCode: Int)

    /**
     * Gửi xoá sạch vật phẩm bảo khố (S:102-008)
     */
    fun sendStorageClear(session: SessionSender)

    /**
     * Gửi dữ liệu đếm nhân vật (S:100-017)
     *
     * Dùng để báo cho client số ô túi đồ (Bag_1/2/3) và các giá trị đếm khác.
     * Công thức client: currentThingGrid = InitBagGrid(50) + Bag_1*5 + Bag_2*5 + Bag_3*5
     *
     * @param session session mục tiêu
     * @param counts ánh xạ chỉ số đếm → giá trị (ví dụ 103→0, 106→0, 124→0)
     */
    fun sendRoleCountData(session: SessionSender, counts: Map<Int, Int>)

    // ── Liên quan kỹ năng (mainKind=8) ──

    /**
     * Gửi dữ liệu kỹ năng nhân vật chính (S:008-013)
     *
     * Nguồn: protocal.lua protocolTable[8][13] → Role.ReceivePlayerSkillData
     * Định dạng: +element(1) +skillPoint(2LE) +skillCount(2LE) <<+skillId(2LE) +level(1)>>
     *
     * @param element nguyên tố nhân vật
     * @param skillPoint điểm kỹ năng khả dụng
     * @param skills ánh xạ skillId → level
     */
    fun sendPlayerSkillData(session: SessionSender, element: Int, skillPoint: Int, skills: Map<Int, Int>)

    /** S:008-012 kết quả thiết lập nguyên tố sáng/tối chuyển sinh 3.*/
    fun sendTurn3ElementResult(session: SessionSender, result: Int)

    /** S:023-077 thông tin thiên châu: số khả dụng + chỉ số đã kích hoạt.*/
    fun sendTurn2Balls(session: SessionSender, available: Int, active: Set<Int>)

    /** S:023-078 ô kỹ năng anh hùng chuyển sinh 2. Map key dùng ô UI 6..13.*/
    fun sendTurn2HeroSkills(session: SessionSender, heroSkills: Map<Int, Int>)

    /**
     * Gửi dữ liệu phím nóng (S:040-001)
     *
     * Nguồn: protocal.lua protocolTable[40][1] → Hotkey.ReceiveHotKeyData
     * Định dạng: đoạn PlayerMain [số trang(1)+số khoá(1)+<<ID(2)>>] + đoạn PlayerSub [số trang(1)+số khoá(1)+<<ID(2)>>]
     *
     * @param mainKeys danh sách ID phím tắt kỹ năng (PlayerMain)
     * @param subKeys danh sách ID phím tắt vật phẩm (PlayerSub)
     */
    fun sendHotkeyData(session: SessionSender, mainKeys: List<Int>, subKeys: List<Int>)

    /**
     * S:040-002 kết quả thiết lập phím tắt
     *
     * ```
     * [mainKind=40 1B] [subKind=2 1B] [compress=0 1B] [result 1B]
     * [nếu result=0] dataIndex(1B) + pageIndex(1B) + keyIndex(1B) + useId(2B LE)
     * ```
     * result: 0=thành công
     */
    fun sendHotkeySetResult(session: SessionSender, result: Int,
                            dataIndex: Int = 0, pageIndex: Int = 0,
                            keyIndex: Int = 0, useId: Int = 0)

    // ── Hệ thống tổ đội (mainKind=13) ──

    /**
     * Gửi yêu cầu lập tổ đội (S:013-001)
     *
     * Báo cho đội trưởng có người chơi yêu cầu gia nhập tổ đội.
     * Định dạng: +playerId(8)
     */
    fun sendPartyJoinRequest(session: SessionSender, requesterId: Long)

    /**
     * Gửi kết quả yêu cầu lập tổ đội (S:013-003)
     *
     * Báo phản hồi của đội trưởng cho bên yêu cầu.
     * Định dạng: +result(1) +nameLen(1) +name(Big5)
     */
    fun sendPartyJoinResult(session: SessionSender, result: Int, leaderName: String)

    /**
     * Gửi thông báo rời tổ đội (S:013-004)
     *
     * Định dạng: +playerId(8) +x(2) +y(2)
     */
    fun sendPartyLeave(session: SessionSender, playerId: Long, x: Int, y: Int)

    /**
     * Gửi thông báo gia nhập tổ đội (S:013-005)
     *
     * Định dạng: +leaderId(8) +playerId(8)
     */
    fun sendPartyMemberJoin(session: SessionSender, leaderId: Long, newMemberId: Long)

    /**
     * Gửi dữ liệu đầy đủ tổ đội (S:013-006)
     *
     * Đồng bộ khi đăng nhập hoặc gia nhập.
     * Định dạng: <<leaderId(8) +count(1) <<playerId(8)>>>>
     */
    fun sendPartyData(session: SessionSender, parties: Map<Long, List<Long>>)

    /**
     * Gửi thông báo thiết lập quân sư (S:013-007)
     *
     * Định dạng: +playerId(8)
     */
    fun sendPartySetAdviser(session: SessionSender, adviserId: Long)

    /**
     * Gửi thông báo huỷ quân sư (S:013-008)
     *
     * Định dạng: gói tin rỗng
     */
    fun sendPartyRemoveAdviser(session: SessionSender)

    /**
     * Gửi mời lập tổ đội (S:013-009)
     *
     * Định dạng: +playerId(8)
     */
    fun sendPartyInvite(session: SessionSender, inviterId: Long)

    /**
     * Gửi kết quả mời lập tổ đội (S:013-010)
     *
     * Định dạng: +result(1) +nameLen(1) +name(Big5)
     */
    fun sendPartyInviteResult(session: SessionSender, result: Int, targetName: String)

    /**
     * Gửi dữ liệu quân sư (S:013-011)
     *
     * Đồng bộ tất cả quân sư khi đăng nhập.
     * Định dạng: <<playerId(8)>>
     */
    fun sendPartyAdviserData(session: SessionSender, adviserIds: List<Long>)

    /**
     * Gửi tin nhắn tổ đội (S:013-013)
     *
     * Định dạng: +kind(1)
     * 0=thành công, 1=đội đó đầy, 2=đội hiện tại đầy, 3=đang trong sự kiện, 6=gải tán
     */
    fun sendPartyMessage(session: SessionSender, kind: Int)

    /**
     * Gửi thông báo đổi đội trưởng (S:013-014)
     *
     * Định dạng: +leaderId(8) <<playerId(8)>>
     */
    fun sendPartyChangeLeader(session: SessionSender, newLeaderId: Long, memberIds: List<Long>)

    /**
     * Gửi đổi trạng thái di chuyển tổ đội (S:013-015)
     *
     * Định dạng: +playerId(8) +status(1)
     */
    fun sendPartyMoveStatus(session: SessionSender, playerId: Long, status: Int)

    // ── Hệ thống tướng (mainKind=15, 19) ──

    /**
     * Gửi dữ liệu đầy đủ tướng tùy thân của nhân vật chính (S:015-008)
     *
     * Nguồn: protocal.lua protocolTable[15][8]
     * Gửi xuống dữ liệu đầy đủ của tất cả tướng tùy thân khi đăng nhập.
     */
    fun sendFollowNpcFullData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>)

    /**
     * Gửi thêm tướng tùy thân (S:015-001)
     *
     * Định dạng: +playerId(8) +followIndex(1) +npcId(4) +canGrow(1) +showMsg(1)
     */
    fun sendFollowNpcAdd(session: SessionSender, playerId: Long, slotIndex: Int, npcId: Int, canGrow: Int, showMsg: Boolean)

    /**
     * Gửi xóa tướng tùy thân (S:015-002)
     *
     * Định dạng: +playerId(8) +followIndex(1) +showMsg(1)
     */
    fun sendFollowNpcRemove(session: SessionSender, playerId: Long, slotIndex: Int, showMsg: Boolean)

    /**
     * Gửi dữ liệu tướng xe ngựa (S:015-010)
     *
     * Định dạng: <<+index(1) +npcId(2) +level(1) +hp(4) +nameLen(1) +name(Big5)>>
     */
    fun sendCartData(session: SessionSender, pets: List<tw.tsm.domain.valueobjects.FollowNpcData>)

    /**
     * Gửi xóa tướng xe ngựa (S:015-011)
     *
     * Định dạng: +index(1)
     */
    fun sendCartRemove(session: SessionSender, slotIndex: Int)

    /**
     * Đăng nhập/khởi tạo võ tướng xuất chiến (S:019-001).
     * DVT client protocolTable[19][1] gọi Role.SetFightNpc(UInt16).
     */
    fun sendFollowNpcDeployInit(session: SessionSender, npcId: Int)

    /**
     * Võ tướng xuất chiến runtime (S:019-004).
     * DVT client protocolTable[19][4] cũng gọi Role.SetFightNpc(UInt16).
     */
    fun sendFollowNpcDeploy(session: SessionSender, npcId: Int)

    /**
     * Gửi thu hồi tướng (S:019-002)
     *
     * Gói tin rỗng
     */
    fun sendFollowNpcRecall(session: SessionSender)

    // ── Sửa thuộc tính (mainKind=8) ──

    /**
     * Gửi sửa thuộc tính nhân vật chính (S:008-001)
     *
     * Nguồn: protocal.lua protocolTable[8][1]
     * Định dạng: +attrKind(1) +sign(1) +value(4LE) +arg(4LE)
     *
     * @param attrKind loại thuộc tính (EAttribute)
     * @param value giá trị thuộc tính (giá trị tuyệt đối)
     * @param isPositive true=dấu dương(1) false=dấu âm(2)
     * @param arg tham số bổ sung (thường là 0)
     */
    fun sendSetAttribute(session: SessionSender, attrKind: Int, value: Int, isPositive: Boolean, arg: Int = 0)

    /** S:008-002: FollowNpc(4), index UInt16, attribute Byte, sign Byte, value/arg Int32. */
    fun sendFollowNpcAttribute(session: SessionSender, slot: Int, attrKind: Int, value: Int, arg: Int = 0)

    /**
     * Gửi cập nhật chỉ số cộng thêm của trang bị (8 gói S:008-001 + MaxHP/HP/MaxSP/SP)
     *
     * Được gọi sau khi mặc/tháo trang bị. Lần lượt gửi giá trị mới của 8 thuộc tính trang bị,
     * Khi client nhận các giá trị như EquipMaxHp/EquipHpx sẽ tự động tính lại MaxHP.
     * Cuối cùng gửi bù giá trị tuyệt đối HP/SP để đảm bảo đồng bộ.
     *
     * @param session kết nối đích
     * @param bonus tổng chỉ số cộng thêm trang bị sau khi tính lại
     * @param maxHp MaxHP sau khi tính lại
     * @param maxSp MaxSP sau khi tính lại
     * @param hp HP sau khi clamp
     * @param sp SP sau khi clamp
     */
    fun sendEquipBonusUpdate(
        session: SessionSender,
        bonus: EquipBonusData,
        maxHp: Long,
        maxSp: Int,
        hp: Long,
        sp: Int
    )

    // ── Hệ thống chiến đấu (mainKind=11, 50, 51, 52, 53) ──

    /**
     * Gửi khởi tạo chiến đấu (S:011-250)
     *
     * Nguồn: protocal.lua protocolTable[11][250]
     * Định dạng: +terrain(1) +eventNo(2LE) +<<dữ liệu RoleAppear>>
     */
    fun sendBattleInit(session: SessionSender, terrain: Int, eventNo: Int, roleData: ByteArray)

    /**
     * S:011-004 Cập nhật loại chiến đấu của người chơi trong cảnh
     *
     * Nguồn: protocal.lua protocolTable[11][4]
     * Định dạng: <<+loại nhân vật(1) +ID người chơi(8 LE) +NPCID(2 LE) +loại chiến đấu(1)>>
     */
    fun sendSceneWarTypeUpdate(session: SessionSender, entries: ByteArray)

    /**
     * S:011-005 Thông tin nhân vật tham chiến (người chơi cũ trong chiến trường nhận nhân vật tham chiến mới)
     *
     * Nguồn: protocal.lua protocolTable[11][5]
     * Định dạng: <<+dữ liệu nhân vật chiến đấu(?)>> — cùng định dạng BattleRoleData
     */
    fun sendBattleJoinRoleAppear(session: SessionSender, roleData: ByteArray)

    /**
     * Gửi đếm ngược bắt đầu chiến đấu (S:052-001)
     *
     * Nguồn: protocal.lua protocolTable[52][1]
     * Định dạng: gói tin rỗng
     */
    fun sendBattleCountdown(session: SessionSender)

    /**
     * Gửi kết quả tấn công bằng kỹ năng (S:050-001)
     *
     * Nguồn: protocal.lua protocolTable[50][1]
     * Định dạng: dữ liệu kết quả tấn công đã được tuần tự hoá
     */
    fun sendBattleSkillResult(session: SessionSender, data: ByteArray)

    /**
     * Gửi sửa đổi thuộc tính tức thời trong chiến đấu (S:051-001)
     *
     * Nguồn: protocal.lua protocolTable[51][1] → FightManager.RevChangeAttrib
     * Định dạng: +isRevive(1) +<<col(1) +row(1) +attrKind(1) +value(4LE)>>
     * attrKind: EAttribute.HP=25, SP=26, v.v.
     */
    fun sendBattleAttrChange(session: SessionSender, data: ByteArray)

    /**
     * Gửi kết thúc chiến đấu (S:011-000)
     *
     * Nguồn: protocal.lua protocolTable[11][0]
     * Định dạng: +playerId(8LE) +npcIndex(2LE)
     */
    fun sendBattleEnd(session: SessionSender, playerId: Long, npcIndex: Int)

    /**
     * Gửi nhân vật rời chiến đấu (S:011-001)
     *
     * Nguồn: protocal.lua protocolTable[11][1]
     * Định dạng: +col(1) +row(1)
     */
    fun sendBattleExitFight(session: SessionSender, col: Int, row: Int)

    /**
     * Gửi hồi phục trạng thái trong chiến đấu (S:053-001)
     *
     * Định dạng: <<+col(1) +row(1) +statusKind(1) +skillId(2LE)>>
     */
    fun sendBattleRestoreStatus(session: SessionSender, data: ByteArray)

    /**
     * Gửi đánh bay trong chiến đấu (S:053-003)
     *
     * Kích hoạt khi sát thương một đòn >= maxHp. Client phát animation bay văng và xoá khỏi bàn cờ.
     *
     * Định dạng: +col(1) +row(1)
     */
    fun sendBattleFlyOut(session: SessionSender, col: Int, row: Int)

    /**
     * Gửi kết quả dùng vật phẩm trong chiến đấu (S:050-002)
     *
     * Định dạng: +col(1) +row(1) +itemId(2LE) +count(1) +tgtCol(1) +tgtRow(1)
     */
    fun sendBattleUseItem(session: SessionSender, col: Int, row: Int, itemId: Int, count: Int, tgtCol: Int, tgtRow: Int)

    /**
     * Gửi loại chiến trường (S:011-010)
     *
     * Định dạng: +warStyle(1) +rounds(2LE) +limitType(1) +limitSwitch(4LE)
     */
    fun sendBattleFieldType(session: SessionSender, warStyle: Int, rounds: Int, limitType: Int, limitSwitch: Int)

    // ── Thiết lập PK (mainKind=33) ──

    /**
     * Gửi phản hồi yêu cầu PK (S:033-001)
     *
     * Nguồn: protocal.lua protocolTable[33][1]
     * Định dạng: +kết quả(1)
     *
     * Mã kết quả:
     * 1=đối phương chưa bật công tắc PK, 2=đối phương chưa bật công tắc tham chiến, 3=đối phương đang hỏi đáp chiến đấu,
     * 4=chiến đấu đặc biệt không được tham chiến, 5=chiến cổng thành khác liên minh, 6=chiến cổng thành khác quân đoàn,
     * 7=đối phương đang nghỉ tại lữ quán, 8=cảnh này không được tham chiến, 9=liên minh không được PK, 10=trạng thái vô địch
     */
    fun sendPkResult(session: SessionSender, resultCode: Int)

    /**
     * Gửi thiết lập công tắc PVP (S:033-002)
     *
     * Nguồn: protocal.lua protocolTable[33][2]
     * Định dạng: +công tắc PK(1) +công tắc tham chiến(1)
     */
    fun sendPkSwitch(session: SessionSender, pkEnabled: Boolean, joinEnabled: Boolean)

    /**
     * Gửi thông báo lỗi chiến đấu (S:011-003)
     *
     * Nguồn: protocal.lua protocolTable[11][3]
     * Định dạng: +loại(1)
     */
    fun sendBattleError(session: SessionSender, errorKind: Int)

    // ── Hệ thống MapNpc (mainKind=22) ──

    /**
     * Gửi khởi tạo MapNpc (S:022-004)
     *
     * Nguồn: protocal.lua protocolTable[22][4]
     * Định dạng: +số lượng(2) <<+chỉ mục(2) +thứ tự hình(1) +loại chiến đấu(1) +vị trí x(2) +vị trí y(2) +loại hiển thị(1) +thời gian ẩn(4)>>
     *
     * Client nhận xong sẽ gọi MarkManager.ContinueNavigation(), tải cảnh bắt buộc phải có gói tin này.
     *
     * @param npcs danh sách bố trí NPC của cảnh này
     */
    fun sendMapNpcInit(session: SessionSender, npcs: List<EveNpcPlacement>)

    /**
     * Ghép gói tin di chuyển MapNpc (S:022-002)
     *
     * Nguồn: protocal.lua protocolTable[22][2]
     * Định dạng: +chỉ mục(2 UInt16) +toạ độ X(2 UInt16) +toạ độ Y(2 UInt16)
     *
     * Khi client nhận xong sẽ gọi `Role.mapNpcs[index]:Move(Vector2.New(x, y))`
     *
     * @return gói tin ByteArray đã ghép xong, dùng cho broadcastToMap
     */
    fun buildMapNpcMovePacket(npcIndex: Int, x: Int, y: Int): ByteArray

    // ── Rơi đồ trong chiến đấu (mainKind=53) ──

    /**
     * Gửi rơi đồ trong chiến đấu (S:053-004)
     *
     * Nguồn: protocal.lua protocolTable[53][4] → FightManager.RevFlyTreasure
     * Định dạng: +ID vật phẩm(2) +cột rơi đồ(1) +hàng rơi đồ(1) +cột nhận đồ(1) +hàng nhận đồ(1)
     *
     * @param itemId ID vật phẩm rơi
     * @param fromCol cột nguồn rơi đồ (vị trí kẻ địch)
     * @param fromRow hàng nguồn rơi đồ
     * @param toCol cột đích nhận đồ (vị trí người chơi)
     * @param toRow hàng đích nhận đồ
     */
    fun sendBattleDrop(session: SessionSender, itemId: Int, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int)

    // ── Hệ thống vật cưỡi (mainKind=79) ──

    /**
     * S:079-001 Dữ liệu vật cưỡi (gửi xuống khi đăng nhập)
     *
     * Định dạng: +cấp độ(1) +điểm(2LE)*6 +số lượng trang bị(1) <<+ThingData(35)>> +NPC ID hiện tại(2LE)
     */
    fun sendMountData(session: SessionSender, data: tw.tsm.domain.valueobjects.MountData)

    /**
     * S:079-002 Thiết lập cấp độ vật cưỡi
     *
     * Định dạng: +cấp độ(1)
     */
    fun sendMountLevel(session: SessionSender, level: Int)

    /**
     * S:079-003 Thiết lập điểm vật cưỡi
     *
     * Định dạng: +loại(1) +điểm(2LE)
     */
    fun sendMountAttributePoint(session: SessionSender, kind: Int, points: Int)

    /**
     * S:079-004 Mặc trang bị cho vật cưỡi
     *
     * Định dạng: +chỉ mục túi đồ(1)
     */
    fun sendMountEquip(session: SessionSender, bagIndex: Int)

    /**
     * S:079-005 Tháo trang bị vật cưỡi
     *
     * Định dạng: +vị trí trang bị(1) +chỉ mục túi đồ(1)
     */
    fun sendMountUnequip(session: SessionSender, fitType: Int, bagIndex: Int)

    /**
     * S:079-006 Chuyển đổi vật cưỡi
     *
     * Định dạng: +NPC ID(2LE)
     */
    fun sendMountSwitch(session: SessionSender, npcId: Int)

    /**
     * S:079-007 Thiết lập vật cưỡi của người chơi (dùng để phát rộng)
     *
     * Định dạng: +ID người chơi(8LE) +NPC ID(2LE)
     */
    fun sendPlayerMount(session: SessionSender, playerId: Long, npcId: Int)

    /**
     * S:079-008 Chỉ số vật cưỡi
     *
     * Định dạng: +số lượng(1) <<+loại(1) +dấu âm dương(1) +chỉ số(4LE) +tham số(4LE)>>
     */
    fun sendMountAttributes(session: SessionSender, attrs: List<MountAttribute>)

    // ── Hệ thống hộp cơ quan (mainKind=65) ──

    /**
     * S:065-001 Hộp cơ quan khởi động thành công
     *
     * Gói tin rỗng. Client nhận xong sẽ gọi MachineBox.SetAutoFight(true).
     */
    fun sendMacroBoxStart(session: SessionSender)

    /**
     * S:065-002 Hộp cơ quan tạm dừng
     *
     * Gói tin rỗng. Client nhận xong sẽ gọi MachineBox.SetAutoFight(false) + hiển thị chuỗi 20730.
     */
    fun sendMacroBoxPause(session: SessionSender)

    /**
     * S:065-003 Thêm thú cưng nhỏ của người chơi trong cảnh
     *
     * Định dạng: +roleId(8B LE) +kind(1B)
     */
    fun sendMiniPetAppear(session: SessionSender, roleId: Long, kind: Int)

    /**
     * S:065-004 Danh sách thú cưng nhỏ của người chơi cũ
     *
     * Định dạng: vòng lặp {roleId(8B LE) kind(1B)}, cho đến khi hết dữ liệu
     */
    fun sendMiniPetList(session: SessionSender, pets: Map<Long, Int>)

    /**
     * S:065-005 Mở giao diện hộp cơ quan
     *
     * Gói tin rỗng (client đã chú thích vô hiệu hoá tính năng này).
     */
    fun sendMacroBoxOpen(session: SessionSender)

    /**
     * S:065-006 Dữ liệu tinh thạch hộp cơ quan
     *
     * Định dạng: vòng lặp {index(1B) sparId(2B LE) count(2B LE)}
     */
    fun sendMacroBoxSparData(session: SessionSender, sparSlots: Map<Int, SparSlot>)

    /**
     * S:065-007 Thông báo hộp cơ quan
     *
     * Định dạng: +kind(1B) [1=chuỗi 80112, 2=chuỗi 80113]
     */
    fun sendMacroBoxMessage(session: SessionSender, kind: Int)

    /**
     * S:065-008 Mở rộng số lượng tinh thạch hộp cơ quan
     *
     * Định dạng: +count(1B) +showMsg(1B)
     */
    fun sendMacroBoxSparExtend(session: SessionSender, count: Int, showMsg: Boolean)

    /**
     * S:065-009 Thông báo gỡ tinh thạch hộp cơ quan
     *
     * Gói tin rỗng. Client hiển thị chuỗi 80292.
     */
    fun sendMacroBoxSparRemoveMsg(session: SessionSender)

    /**
     * S:065-010 Hộp cơ quan tiến hành tiếp tế
     *
     * Gửi sau khi chiến đấu kết thúc, báo client tiến hành tiếp tế hồi phục/HP/SP.
     * Định dạng: +checkItem(1B bool) [0=không kiểm tra về thành khi thiếu đồ hồi phục, 1=cần kiểm tra]
     */
    fun sendMacroBoxSupply(session: SessionSender, checkItem: Boolean)

    /**
     * S:065-011 Dữ liệu bình kinh nghiệm
     *
     * Định dạng: +priority(1B) + vòng lặp {index(1B) bottleId(2B LE) exp(4B LE)}
     */
    fun sendMacroBoxExpBottleData(session: SessionSender, priority: Int, bottles: Map<Int, ExpBottleSlot>)

    /**
     * S:065-012 Cập nhật bình kinh nghiệm
     *
     * Định dạng: +index(1B) +exp(4B LE)
     */
    fun sendMacroBoxUpdateExpBottle(session: SessionSender, index: Int, exp: Int)

    /**
     * S:065-013 thiết lập bình kinh nghiệm thành công
     *
     * Định dạng: +index(1B)
     */
    fun sendMacroBoxSetExpBottle(session: SessionSender, index: Int)

    /**
     * S:065-014 số lượng mở rộng bình kinh nghiệm của hộp cơ quan
     *
     * Định dạng: +count(1B) +showMsg(1B)
     */
    fun sendMacroBoxExpExtend(session: SessionSender, count: Int, showMsg: Boolean)

    // ──── Hệ thống thành tựu (mainKind=82) ────

    /**
     * S:082-001 phản hồi hoàn thành thành tựu
     *
     * Định dạng: +kết quả(1B) +ID thành tựu(2B LE)
     * Kết quả: 0=thành công, 1=không có dữ liệu, 2=đã hoàn thành, 3=điều kiện không đạt, 4=dữ liệu lỗi
     * Lưu ý: client luôn đọc result + id bất kể mã kết quả là gì
     */
    fun sendAchievementComplete(session: SessionSender, result: Int, achievementId: Int)

    /**
     * S:082-002 phản hồi nhận thưởng thành tựu
     *
     * Định dạng: +kết quả(1B) [+ID thành tựu(2B LE) (chỉ khi kết quả=0)]
     * Kết quả: 0=thành công, 1=thất bại
     */
    fun sendAchievementReward(session: SessionSender, result: Int, achievementId: Int)

    // ── Hệ thống thư (mainKind=83) ──

    /**
     * S:083-001 thêm thư mới
     *
     * Định dạng: +count(4B LE) <<+mailId(4B) +time(8B Double) +state(1B)
     *       +contentLen(2B LE) +content(Big5) +attachCount(1B)
     *       <<+kind(1B) +data(ThingData/NpcInfo)>>>>
     */
    fun sendMails(session: SessionSender, mails: List<MailRecord>)

    /**
     * S:083-002 xác nhận nhận thư
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailTakeConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-003 xác nhận xoá thư
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailDeleteConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-004 xác nhận thư đã đọc
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailReadConfirm(session: SessionSender, mailIds: List<Int>)

    /**
     * S:083-005 mã lỗi của thư
     *
     * Định dạng: +kind(1B)
     * 4=túi đồ đầy, 5=đã nhận, 6=đã xoá, 7=hết hạn, 8=đầy tiền, 9=túi đồ đang đóng
     */
    fun sendMailError(session: SessionSender, errorKind: Int)

    // ── Hệ thống bảng xếp hạng (mainKind=88) ──

    /**
     * S:088-001 dữ liệu bảng xếp hạng
     *
     * Định dạng: +leaderboardId(1B) +page(1B) +maxPage(1B) +myRank(1B)
     *       +infoCount(1B) <<+scoreParam(4B LE)>>
     *       +count(1B) <<+rank(1B) +state(1B) +roleId(8B LE) +nameLen(1B) +name(Big5)
     *       +level(1B) +element(1B) +turn3Element(1B) +turn(1B) +career(1B)
     *       +scoreCount(1B) <<+score(4B LE)>>>>
     */
    fun sendLeaderboardData(
        session: SessionSender,
        leaderboardId: Int, page: Int, maxPage: Int, myRank: Int,
        myScoreParams: List<Int>,
        entries: List<LeaderboardEntryDto>
    )

    // ── Hệ thống bạn bè (mainKind=14) ──

    /**
     * S:014-003 kết quả mời kết bạn
     *
     * Định dạng: +nameLen(1B) +name(Big5) +result(1B)
     */
    fun sendFriendInviteResult(session: SessionSender, name: String, result: Int)

    /**
     * S:014-004 thông báo xoá bạn bè
     *
     * Định dạng: +playerId(8B LE)
     */
    fun sendFriendRemove(session: SessionSender, playerId: Long)

    /**
     * S:014-005 thêm bạn bè (gửi xuống khi đăng nhập + thêm tức thời)
     *
     * Định dạng: +count(2B LE) <<+playerId(8B LE) +danh thiếp +trực tuyến(1B) +độ tin cậy(2B LE) +cờ đánh dấu(1B) +thời điểm kết bạn(8B) +thời điểm offline(8B)>>
     */
    fun sendFriendAdd(session: SessionSender, friends: List<FriendDataDto>)

    /**
     * S:014-006 hộp thư bạn bè đã đầy
     */
    fun sendFriendMailFull(session: SessionSender)

    /**
     * S:014-007 tin nhắn bạn bè
     *
     * Định dạng: +kind(1B) [1-7 các mã kết quả]
     */
    fun sendFriendMessage(session: SessionSender, kind: Int)

    /**
     * S:014-008 mời kết bạn
     *
     * Định dạng: +count(2B LE) <<+playerId(8B LE) +danh thiếp +msg(1B)>>
     */
    fun sendFriendInvites(session: SessionSender, invites: List<FriendInviteDto>)

    /**
     * S:014-009 xoá lời mời kết bạn
     *
     * Định dạng: +playerId(8B LE)
     */
    fun sendFriendRemoveInvite(session: SessionSender, playerId: Long)

    /**
     * S:014-012 cập nhật cờ chức năng bạn bè
     *
     * Định dạng: +count(1B) <<+playerId(8B LE) +flag(1B)>>
     */
    fun sendFriendFunctionFlags(session: SessionSender, updates: List<Pair<Long, Int>>)

    /**
     * S:014-013 cập nhật độ tin cậy bạn bè
     *
     * Định dạng: +playerId(8B LE) +friendly(2B LE)
     */
    fun sendFriendFriendly(session: SessionSender, playerId: Long, friendly: Int)

    /**
     * S:014-014 thông tin người chơi
     *
     * Định dạng: +playerId(8B) +danh thiếp +maxHp(4B) +maxSp(2B) +lục chỉ số(4B)×6
     *       +equipCount(1B) <<+ThingData>> +outfitCount(1B) <<+itemId(2B)>>
     *       +titleId(2B) +marriage(1B)
     */
    fun sendPlayerInfo(session: SessionSender, data: PlayerInfoDto)

    /**
     * S:014-015 kết quả nhận quà
     *
     * Định dạng: +result(1B) [1=thành công, 2-6 các mã thất bại]
     */
    fun sendGiftReceiveResult(session: SessionSender, result: Int)

    /**
     * S:014-016 cập nhật trạng thái trực tuyến của bạn bè
     *
     * Định dạng: +playerId(8B LE) +online(1B)
     */
    fun sendFriendOnlineStatus(session: SessionSender, playerId: Long, online: Boolean)

    /**
     * S:014-017 cập nhật số lượng bạn bè lịch sử tối đa
     *
     * Định dạng: +count(4B LE)
     */
    fun sendMaxRecordFriendCount(session: SessionSender, count: Int)

    // ──── RoleCount（mainKind=85）────

    /**
     * S:085-002 cập nhật bộ đếm nhân vật (nhiều bản ghi)
     *
     * Định dạng: +số lượng(2B LE) <<+chỉ số(2B LE) +giá trị(4B LE unsigned) +giới hạn trên(4B LE signed)>>
     */
    fun sendRoleCountUpdate(session: SessionSender, counts: Map<Int, Pair<Int, Int>>)
    /** S:023-069 đồng bộ ngoại hình chuyển nghề/chuyển sinh của người chơi với turn/career.*/
    fun sendPlayerCareerChange(
        session: SessionSender, playerId: Long, hair: Int,
        color1: Int, color2: Int, turn: Int, career: Int
    )

}

/**
 * Giá trị thuộc tính vật cưỡi — dùng cho S:079-008
 */
/**
 * DTO mục bảng xếp hạng — dùng cho S:088-001
 */
data class LeaderboardEntryDto(
    val rank: Int,
    /** 0=không đổi, 1=tăng, 2=giảm*/
    val state: Int,
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val scoreParams: List<Int>
)

/**
 * DTO dữ liệu bạn bè — dùng cho S:014-005
 */
data class FriendDataDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    val online: Boolean,
    val friendly: Int,
    val functionFlag: Int,
    val addTime: Double,
    val offlineTime: Double
)

/**
 * DTO lời mời kết bạn — dùng cho S:014-008
 */
data class FriendInviteDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int
)

/**
 * DTO thông tin người chơi — dùng cho S:014-014
 */
data class PlayerInfoDto(
    val roleId: Long,
    val name: String,
    val level: Int,
    val element: Int,
    val turn3Element: Int,
    val turn: Int,
    val career: Int,
    val sex: Int,
    val hair: Int,
    val color1: Int,
    val color2: Int,
    val maxHp: Long,
    val maxSp: Int,
    val intelligence: Int,
    val atk: Int,
    val def: Int,
    val hpx: Int,
    val spx: Int,
    val agi: Int,
    val equips: List<ThingData>,
    val outfitItemIds: List<Int> = emptyList(),
    val titleId: Int,
    val marriage: Int = 0
)

data class MountAttribute(
    /** Kiểu EAttribute*/
    val kind: Int,
    /** 1=dương, 2=âm*/
    val sign: Int,
    /** Giá trị*/
    val value: Int,
    /** Tham số*/
    val arg: Int = 0
)

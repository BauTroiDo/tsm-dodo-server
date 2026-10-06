package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveCondition

private val logger = KotlinLogging.logger {}

/**
 * Ảnh chụp nhanh trạng thái sự kiện người chơi — mọi dữ liệu cần cho đánh giá điều kiện
 *
 * Được thiết kế làm object giá trị bất biến, do bên gọi (NpcEventHandler) dựng rồi truyền vào.
 * Bản thân bộ đánh giá không truy vấn bất kỳ hệ thống bên ngoài nào.
 *
 * @param missionSteps   ID nhiệm vụ → bước hiện tại (dùng cho điều kiện pStyle=1/2)
 * @param missionFlags   ID cờ → giá trị (dùng cho điều kiện pStyle=3: bitId của nhiệm vụ đã hoàn thành)
 * @param bagItems       ID vật phẩm → số lượng sở hữu
 * @param storedItems    ID vật phẩm Tiền Trang/kho báu → số lượng sở hữu (dùng cho conditionClass=16)
 * @param bagUsedSlots   số ô đã dùng trong túi đồ (dùng cho conditionClass=7 param=2)
 * @param bagCapacity    dung lượng túi đồ hiện tại (bao gồm ô mở rộng RoleCount)
 * @param equipItems     tập hợp ID vật phẩm đã trang bị
 * @param sex            giới tính nhân vật
 * @param storyFaction   phe cốt truyện. DVT Eve/Talk xác nhận class7,param1,pStyle6 value=2 là nhánh Khăn Vàng/Hắc Sơn.
 *                       Server gốc chưa có persistence tương ứng nên offline mặc định 1 = phe thường.
 * @param level          cấp độ nhân vật
 * @param serverId       Server ID đã chọn khi đăng nhập (conditionClass=7 param=8)
 * @param realPoint      Point thực khả dụng (Role.GetPoint, conditionClass=7 param=19)
 * @param rebornCount    số lần chuyển sinh
 * @param isMounted      có đang cưỡi vật cưỡi hay không (dùng cho conditionClass=7 param=16)
 * @param lastSurfaceId  ID Surface hiển thị cuối cùng (dùng để so sánh conditionClass=10)
 * @param lastChoiceCode mã lựa chọn cuối cùng (dùng để so sánh conditionClass=10)
 * @param battleResult   kết quả chiến đấu (0=không 1=thắng 2=thua 3=chạy, dùng để so sánh conditionClass=8)
 * @param fightDataKey   FightData key của trận sự kiện vừa đánh; class8 parameter=0 là wildcard, khác 0 phải khớp key này.
 * @param completedEveCounts số sự kiện đã hoàn thành của cảnh này (eveNo → số lần hoàn thành, dùng để so sánh conditionClass=12)
 * @param currentEveNo  eveNo đang được đánh giá (dùng cho conditionClass=12 param=0)
 * @param followNpcIds   tập hợp NPC ID tướng mang theo (dùng để kiểm tra sở hữu tướng conditionClass=9)
 * @param innNpcIds      tập hợp NPC ID tướng lữ quán (dùng cho conditionClass=7 param=6 pStyle=3)
 * @param cartNpcIds     tập hợp NPC ID tướng xe ngựa (dùng cho conditionClass=7 param=6 pStyle=2)
 * @param roleCountValues số đếm thống kê nhân vật RoleCount (dùng cho một phần chỉ mục conditionClass=18)
 * @param skillLevels    ID kỹ năng → cấp kỹ năng (dùng để so sánh conditionClass=4)
 * @param gold           tiền vàng trên người (dùng cho điều kiện tiền conditionClass=5)
 * @param npcSecondReincarnationStatus mã trạng thái lối vào chuyển sinh lần 2 của tướng (0=có thể mở, -1=không có tướng đủ điều kiện)
 */
data class PlayerEventState(
    val missionSteps: Map<Int, Int> = emptyMap(),
    val dungeonMissionIds: Set<Int> = emptySet(),
    val missionFlags: Map<Int, Int> = emptyMap(),
    val bagItems: Map<Int, Int> = emptyMap(),
    val storedItems: Map<Int, Int> = emptyMap(),
    val bagUsedSlots: Int = 0,
    val bagCapacity: Int = 0,
    val equipItems: Set<Int> = emptySet(),
    val sex: Int = 0,
    val storyFaction: Int = 1,
    /** 5 điểm phe DVT theo thứ tự: Ngụy, Thục, Ngô, Khăn Vàng, Du Hiệp. */
    val moral: IntArray = intArrayOf(0, 0, 0, 0, 0),
    /** Điểm diệt địch; Mobile DVT tính Honor = Calc.GetHonor(KillNum) và hiện công thức là 1:1. */
    val killNum: Int = 0,
    /** Trạng thái mở hoạt động; offline mặc định mở khi scheduler gốc không có. */
    val activityOpen: Boolean? = null,
    /** ID quân đoàn hiện tại; server offline chưa có subsystem Quân đoàn nên mặc định 0. */
    val organizationId: Long = 0L,
    /** Thành trì 1..5 mà quân đoàn đang sở hữu; 0 = không sở hữu thành. */
    val organizationCityId: Int = 0,
    /** Chức vụ quân đoàn/thành: 1..3 theo DVT Eve; 0 = không có chức vụ. */
    val organizationRank: Int = 0,
    /** Số người trong tổ đội theo Team.GetMember(roleId,true); solo vẫn tính là 1. */
    val teamSize: Int = 1,
    /** ETeamState: 0=None, 1=Leader, 2=Adviser, 3=Member. */
    val teamState: Int = 0,
    /** Hai người trong tổ đội và khác giới, dùng cho Valentine/Kết hôn class12/param4. */
    val romancePairValid: Boolean = false,
    /**
     * Trạng thái quan hệ theo enum client EAffectionStatus:
     * 0=None, 1=Lovers, 2=Spouse. Offline chưa có subsystem nên mặc định None.
     */
    val affectionStatus: Int = 0,
    /** Số tháng từ mốc quan hệ/kết hôn; offline chưa có persistence nên mặc định 0. */
    val marriageMonths: Int = 0,
    /** Các slot con cái/nhận nuôi đang có (1..5). Offline mặc định rỗng. */
    val childSlots: Set<Int> = emptySet(),
    /** Hệ nhân vật DVT: 1..4. */
    val element: Int = 0,
    /** NPC ID biến hình hiện tại; DVT Role.player.npcId / S:005-005. */
    val transformNpcId: Int = 0,
    /** Nghề chuyển sinh DVT: 1..4, 0 = chưa có nghề. */
    val career: Int = 0,
    val level: Int = 0,
    val serverId: Int = -1,
    val realPoint: Int = -1,
    val rebornCount: Int = 0,
    /** Thiên Châu Nhị Chuyển đã kích hoạt, index DVT 1..12. */
    val activeTurn2Balls: Set<Int> = emptySet(),
    val isMounted: Boolean = false,
    val lastSurfaceId: Int = -1,
    val lastChoiceCode: Int = -1,
    val battleResult: Int = 0,
    val fightDataKey: Int = 0,
    val completedEveCounts: Map<Int, Int> = emptyMap(),
    val currentEveNo: Int = 0,
    val followNpcIds: Set<Int> = emptySet(),
    /** Cấp cao nhất của tướng mang theo theo npcId, dùng class9 pStyle1. */
    val followNpcLevels: Map<Int, Int> = emptyMap(),
    /** Trung thành cao nhất của tướng mang theo theo npcId, dùng class9 pStyle2 threshold. */
    val followNpcLoyalties: Map<Int, Int> = emptyMap(),
    /** NPC ID đang được chọn xuất chiến, dùng class9 pStyle3. */
    val deployedNpcIds: Set<Int> = emptySet(),
    val followPetCount: Int = 0,
    val maxFollowPets: Int = 4,
    val innNpcIds: Set<Int> = emptySet(),
    val cartNpcIds: Set<Int> = emptySet(),
    val roleCountValues: Map<Int, Int> = emptyMap(),
    val skillLevels: Map<Int, Int> = emptyMap(),
    val gold: Int = 0,
    val npcSecondReincarnationStatus: Int = -1,
)

/**
 * Bộ đánh giá điều kiện Eve — hàm thuần, không giữ bất kỳ trạng thái nào
 *
 * Nguồn gốc: client không đánh giá điều kiện (toàn bộ do server quyết định),
 * Ngữ nghĩa điều kiện được suy ra từ cách đặt tên trường của Eve_NpcEventData.lua + hành vi trò chơi.
 *
 * Đã xác minh/triển khai các class đang xuất hiện trong flow nhiệm vụ chính: 0,1,2,3,4,5,7,8,9,10,11,12,13,14,15,16,18.
 * Các class/shape chưa có bằng chứng DVT vẫn giữ nguyên nguyên tắc bảo thủ: trả false và ghi WARN, không tự mở quest.
 */
object EveConditionEvaluator {

    /**
     * Đánh giá một điều kiện đơn lẻ có thỏa mãn không
     *
     * @param cond  dữ liệu điều kiện Eve.emg
     * @param state ảnh chụp nhanh trạng thái hiện tại của người chơi
     * @return true = điều kiện thỏa mãn
     */
    fun evaluate(cond: EveCondition, state: PlayerEventState): Boolean {
        return when (cond.conditionClass) {
            0 -> true  // Không điều kiện, luôn khớp

            1 -> {
                // Kiểm tra sở hữu vật phẩm túi đồ: param=itemId
                // ops=2, val=N → qty > N (có vật phẩm, ví dụ val=0 → qty > 0, val=2 → qty > 2)
                // ops=1, val=N → qty < N (thiếu vật phẩm, ví dụ val=1 → qty < 1 tức qty == 0)
                // ops=0, val=N → qty == N
                // ops=5, val=0 → qty == 0 (không có vật phẩm)
                val qty = state.bagItems[cond.conditionParameter] ?: 0
                val result = when (cond.conditionOps) {
                    0 -> qty == cond.conditionValue
                    1 -> qty < cond.conditionValue
                    2 -> qty > cond.conditionValue
                    5 -> if (cond.conditionValue == 0) qty == 0 else qty >= cond.conditionValue
                    6 -> qty != cond.conditionValue
                    else -> compareStep(qty, cond.conditionOps, cond.conditionValue)
                }
                logger.debug { "class=1 itemId=${cond.conditionParameter} qty=$qty ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            2 -> {
                // Điều kiện nhiệm vụ: param=missionId, ops=so sánh, val=giá trị mục tiêu
                // pStyle=3 → đồng thời kiểm tra bước nhiệm vụ và cờ, lấy giá trị lớn hơn
                //   Nhiệm vụ đang tiến hành: step=1, flag=0 → actual=1
                //   Nhiệm vụ đã hoàn thành: step=0, flag=1 → actual=1
                //   Nhiệm vụ chưa bắt đầu: step=0, flag=0 → actual=0
                // pStyle=1/2 → chỉ kiểm tra bước nhiệm vụ
                val step = state.missionSteps[cond.conditionParameter] ?: 0
                val actual = if (cond.conditionParameterStyle == 3) {
                    val flag = state.missionFlags[cond.conditionParameter] ?: 0
                    maxOf(step, flag)
                } else {
                    step
                }
                val checkType = if (cond.conditionParameterStyle == 3) "step|flag" else "step"
                // Dungeon gates prove ops2 > (BaDauYeu step>0; treasure step>1),
                // ops5 == (treasure mutually exclusive steps3/4 and staged multiplayer).
                val result = if (cond.conditionParameter in state.dungeonMissionIds)
                    compareEveNumeric(actual, cond.conditionOps, cond.conditionValue)
                else compareStep(actual, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=2 missionId=${cond.conditionParameter} $checkType=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            3 -> {
                // Trạng thái có thể tương tác của vật thể cảnh: mẫu Eve cố định là pStyle=4/ops=5/value=0/subItem=0.
                val result = cond.conditionParameter > 0 &&
                    cond.conditionParameterStyle == 4 &&
                    cond.conditionOps == 5 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=3 điều kiện vật thể cảnh giới không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            4 -> {
                // Eve skill conditions use the Eve numeric comparator family, not mission-step ops.
                // Real DVT evidence:
                // - 14001 (maxLv=1): ops5/value0 = not learned, ops5/value1 = exactly Lv1.
                // - 14001/14004 (maxLv 1/10): ops4/value1 = learned / at least Lv1.
                // - 14005/14011 (maxLv=10): ops2/value9 = Lv10.
                // Reusing compareStep made ops4 mean > and permanently blocked maxLv1 quest skills
                // such as mission10143 (Thuật trinh thám -> Thuật đào tẩu).
                val skillLevel = state.skillLevels[cond.conditionParameter] ?: 0
                val result = compareEveNumeric(skillLevel, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=4 skillId=${cond.conditionParameter} level=$skillLevel ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            5 -> {
                // Điều kiện tiền Eve: mẫu phổ biến là ops=2, value=số tiền-1, biểu thị tiền vàng đạt ngưỡng khấu trừ.
                val result = evaluateMoneyCondition(state.gold, cond.conditionOps, cond.conditionValue)
                logger.debug { "class=5 gold=${state.gold} ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            7 -> {
                // Thuộc tính chung: param quyết định kiểu con
                val actual: Int? = when (cond.conditionParameter) {
                    0 -> {
                        // DVT class7/param0 bị overload theo ParameterStyle:
                        // pStyle1 = cấp nhân vật; pStyle2 = hệ nhân vật (1 Địa, 2 Thủy, 3 Hỏa, 4 Phong).
                        // pStyle3 = NPC ID biến hình hiện tại (Role.player.npcId / S:005-005).
                        when (cond.conditionParameterStyle) {
                            1 -> state.level
                            2 -> state.element
                            3 -> state.transformNpcId
                            else -> null
                        }
                    }
                    1 -> {
                        // DVT class7/param1 có hai họ shape:
                        // - pStyle 1..5 = Moral 5 phe. Eve/Talk/Npc_C xác nhận mapping đảo:
                        //   5 Ngụy, 4 Thục, 3 Ngô, 2 Khăn Vàng, 1 Du Hiệp.
                        // - pStyle 6 = phe cốt truyện riêng (Khăn Vàng/Hắc Sơn), giữ contract cũ.
                        when {
                            cond.conditionParameterStyle in 1..5 && cond.conditionSubItem == 0 -> {
                                val faction = 6 - cond.conditionParameterStyle
                                state.moral.getOrElse(faction - 1) { 0 }
                            }
                            cond.conditionParameterStyle == 6 && cond.conditionSubItem == 0 ->
                                state.storyFaction
                            else -> null
                        }
                    }
                    2 -> {
                        // Nhánh không gian túi đồ: pStyle=1 túi đồ đã đầy; pStyle=2 túi đồ còn chỗ; pStyle=4 so sánh số ô còn lại.
                        evaluateBagSpaceCondition(cond, state)
                    }
                    3 -> {
                        // Kiểm tra vật phẩm trang bị: val=itemId, đã trang bị trả về itemId, không trả về 0
                        if (cond.conditionValue in state.equipItems) cond.conditionValue else 0
                    }
                    4 -> {
                        // Kiểm tra vật phẩm ở Tiền Trang/Bảo Khố.
                        // DVT mission 11731 dùng duy nhất shape:
                        // pStyle=3, ops=6, value=0, subItem=itemId.
                        // Chain này đi cùng class1 (túi) + param3 (trang bị) để chống nhận
                        // trùng ấn nghề 23175..23178. Trả 0 khi đang có trong kho,
                        // 1 khi không có để ops=6/value=0 chỉ pass khi kho không chứa item.
                        if (
                            cond.conditionParameterStyle == 3 &&
                            cond.conditionOps == 6 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem > 0
                        ) {
                            if ((state.storedItems[cond.conditionSubItem] ?: 0) > 0) 0 else 1
                        } else null
                    }
                    5 -> {
                        // Nhánh không gian tướng: pStyle=1 tướng mang theo đã đầy (>=4); pStyle=2 tướng mang theo còn chỗ trống (<4); pStyle=4 số ô tướng còn lại
                        evaluatePetSpaceCondition(cond, state)
                    }
                    6 -> {
                        // Kiểm tra vị trí lưu tướng: pStyle=1 mang theo / 2 xe ngựa / 3 lữ quán, val=npcId
                        val npcId = cond.conditionValue
                        val npcSet = when (cond.conditionParameterStyle) {
                            1 -> state.followNpcIds
                            2 -> state.cartNpcIds
                            3 -> state.innNpcIds
                            else -> emptySet()
                        }
                        if (npcId in npcSet) npcId else 0
                    }
                    9 -> {
                        // Trạng thái chuyển sinh người chơi.
                        // Eve.emg chỉ có pStyle 1..4, ops=0/value=0/subItem=0:
                        //   1 = đã Nhất Chuyển, 2 = chưa Nhất Chuyển,
                        //   3 = đã Nhị Chuyển, 4 = chưa Nhị Chuyển.
                        // Dùng mốc đã đạt thay vì equality để Turn=3 vẫn được xem là đã qua Nhị Chuyển.
                        if (
                            cond.conditionParameterStyle in 1..4 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0
                        ) {
                            val matches = when (cond.conditionParameterStyle) {
                                1 -> state.rebornCount >= 1
                                2 -> state.rebornCount < 1
                                3 -> state.rebornCount >= 2
                                4 -> state.rebornCount < 2
                                else -> false
                            }
                            if (matches) 0 else 1
                        } else null
                    }
                    11 -> {
                        // Trạng thái hoàn tất 12 Thiên Châu Nhị Chuyển.
                        // Eve.emg chỉ có pStyle1/2, ops=0,value=0,subItem=0.
                        // Talk33147 xác nhận pStyle1 = "đã thu thập đủ 12 Thiên Châu";
                        // pStyle2 là nhánh còn thiếu / vẫn có thể tiếp tục đổi nguyên thạch.
                        if (
                            cond.conditionParameterStyle in 1..2 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0
                        ) {
                            val complete = (1..12).all { it in state.activeTurn2Balls }
                            val matches = if (cond.conditionParameterStyle == 1) complete else !complete
                            if (matches) 0 else 1
                        } else null
                    }
                    10 -> {
                        // DVT Honor condition.
                        // Role.ReceivePlayerData: Honor = Calc.GetHonor(KillNum);
                        // Calculator.lua hiện trả nguyên giá trị, nên Honor == KillNum.
                        // Eve.emg chỉ có shape pStyle=1/subItem=0 cho param10.
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionSubItem == 0
                        ) state.killNum else null
                    }
                    7 -> {
                        // Giới tính nhân vật. Eve.emg dùng pStyle=1 nam / 2 nữ,
                        // shape cố định ops=0,value=0,subItem=0; lời thoại cưới/Lễ Tình Yêu
                        // xác nhận trực tiếp hai nhánh Công tử/Cô nương.
                        if (
                            cond.conditionParameterStyle in 1..2 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0
                        ) {
                            if (state.sex == cond.conditionParameterStyle) 0 else 1
                        } else null
                    }
                    8 -> {
                        // DVT Server ID condition.
                        // Evidence:
                        // - Login packet stores Network.serverId / session.serverId.
                        // - Migrant.lua defines galaxy servers 90..99 (serverId > 100 => %100).
                        // - All 468 Eve.emg cls=7,param=8 samples use pStyle=1/subItem=0 and
                        //   values 1,2,96..104,196..199; arena scenes 54901..54904 are
                        //   exactly Migrant.IsArenaScene().
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionSubItem == 0 &&
                            state.serverId > 0
                        ) state.serverId else null
                    }
                    19 -> {
                        // DVT Point balance condition (Role.GetPoint / ECurrency.Gold).
                        // Toàn bộ Eve.emg chỉ có 6 bản ghi, tất cả ở sòng bạc chính 12180: pStyle=2, subItem=0,
                        // ops=5, ngưỡng 9/45/90 hoặc 10/50/100; cùng chain với cls=7 pStyle=3
                        // Mua trực tiếp 31171 x1/5/10, meanNo chính là giá Point tương ứng.
                        if (
                            cond.conditionParameterStyle == 2 &&
                            cond.conditionSubItem == 0 &&
                            state.realPoint >= 0
                        ) state.realPoint else null
                    }
                    15 -> {
                        // Quan hệ/hôn nhân.
                        // pStyle1: đã kết hôn (Spouse) — data dùng val=0.
                        // pStyle2: EAffectionStatus (0=None,1=Lovers,2=Spouse).
                        // pStyle3: số tháng quan hệ/hôn nhân, dùng các mốc 6/12.
                        when (cond.conditionParameterStyle) {
                            1 -> if (cond.conditionSubItem == 0) {
                                if (state.affectionStatus == 2) 0 else 1
                            } else null
                            2 -> if (cond.conditionSubItem == 0) state.affectionStatus else null
                            3 -> if (cond.conditionSubItem == 0) state.marriageMonths else null
                            else -> null
                        }
                    }
                    18 -> {
                        // Con cái / nhận nuôi.
                        // pStyle1 + subItem1..5: slot con cụ thể đã có.
                        // pStyle2/sub0: chưa có con.
                        when {
                            cond.conditionParameterStyle == 1 &&
                                cond.conditionOps == 0 &&
                                cond.conditionValue == 0 &&
                                cond.conditionSubItem in 1..5 ->
                                if (cond.conditionSubItem in state.childSlots) 0 else 1
                            cond.conditionParameterStyle == 2 &&
                                cond.conditionOps == 0 &&
                                cond.conditionValue == 0 &&
                                cond.conditionSubItem == 0 ->
                                if (state.childSlots.isEmpty()) 0 else 1
                            else -> null
                        }
                    }
                    14 -> {
                        // Nghề chuyển sinh. DVT data dùng pStyle 1..4 và reward/item
                        // Chiến Thần / Nhân Vương / Quỷ Mưu / Chiếm Thiên tương ứng.
                        if (
                            cond.conditionParameterStyle in 1..4 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0
                        ) {
                            if (state.career == cond.conditionParameterStyle) 0 else 1
                        } else null
                    }
                    16 -> {
                        // Trạng thái vật cưỡi.
                        // Eve.emg có đúng hai shape đối xứng, cùng pStyle=1/ops=0/value=0:
                        // - subItem=1: đang cưỡi; nhánh này dẫn Talk37776 "mời xuống ngựa".
                        // - subItem=2: không cưỡi; nhánh này nằm trong chain cho phép event tiếp tục.
                        if (
                            cond.conditionParameterStyle == 1 &&
                            cond.conditionOps == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem in 1..2
                        ) {
                            val matches = if (cond.conditionSubItem == 1) state.isMounted else !state.isMounted
                            if (matches) 0 else 1
                        } else null
                    }
                    else -> null
                }
                if (actual == null) {
                    logger.warn {
                        "conditionClass=7 chưa triển khai param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    return false
                }
                // Moral và Honor dùng bảng comparator riêng của Eve.
                // Dữ liệu ngưỡng chứng minh: 1:<, 2:>, 4:>=, 5:==, 6:!=.
                val useLevelComparator =
                    cond.conditionParameter == 0 && cond.conditionParameterStyle == 1
                val useElementComparator =
                    cond.conditionParameter == 0 && cond.conditionParameterStyle == 2
                val useTransformComparator =
                    cond.conditionParameter == 0 && cond.conditionParameterStyle == 3
                val useEveNumeric = (
                    cond.conditionParameter == 1 &&
                        cond.conditionParameterStyle in 1..5
                    ) || (
                    cond.conditionParameter == 10 &&
                        cond.conditionParameterStyle == 1
                    ) || (
                    cond.conditionParameter == 15 &&
                        cond.conditionParameterStyle in 2..3
                    )
                val result = when {
                    useLevelComparator -> compareLevel(actual, cond.conditionOps, cond.conditionValue)
                    useElementComparator -> compareIdentity(actual, cond.conditionOps, cond.conditionValue)
                    useTransformComparator -> compareIdentity(actual, cond.conditionOps, cond.conditionValue)
                    useEveNumeric -> compareEveNumeric(actual, cond.conditionOps, cond.conditionValue)
                    else -> compareStep(actual, cond.conditionOps, cond.conditionValue)
                }
                logger.debug { "class=7 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                result
            }

            8 -> {
                // Kết quả chiến đấu: pStyle=1 thắng / 2 thua / 3 bỏ chạy.
                // DVT Eve.emg dùng conditionParameter làm FightData key: 2090/2107 mẫu
                // non-zero trùng trực tiếp resultMeanNo của resultType=3 trong cùng event.
                // parameter=0 là wildcard legacy; khác 0 phải đúng trận vừa đánh để không
                // chọn nhầm nhánh hậu chiến của một quái/cổng khác trong cùng event.
                val battleMatches = cond.conditionParameter == 0 ||
                    cond.conditionParameter == state.fightDataKey
                val result = battleMatches && cond.conditionParameterStyle == state.battleResult
                logger.debug {
                    "class=8 fightKey=${cond.conditionParameter}/${state.fightDataKey} " +
                        "pStyle=${cond.conditionParameterStyle} battleResult=${state.battleResult} → $result"
                }
                result
            }

            9 -> {
                if (cond.conditionParameter == 0) {
                    // Kiểm tra sở hữu tướng: conditionValue=npcId.
                    val npcId = cond.conditionValue
                    val actual = if (npcId in state.followNpcIds) npcId else 0
                    val result = compareStep(actual, cond.conditionOps, npcId)
                    logger.debug { "class=9 npcId=$npcId hasPet=${npcId in state.followNpcIds} ops=${cond.conditionOps} → $result" }
                    result
                } else {
                    evaluateFollowNpcCondition(cond, state)
                }
            }

            10 -> {
                // Chọn lựa chọn hội thoại: param=surfaceId, pStyle=choiceCode
                val result = state.lastSurfaceId == cond.conditionParameter &&
                             state.lastChoiceCode == cond.conditionParameterStyle
                logger.debug { "class=10 surfaceId=${cond.conditionParameter} code=${cond.conditionParameterStyle} lastSurface=${state.lastSurfaceId} lastCode=${state.lastChoiceCode} → $result" }
                result
            }

            11 -> {
                // Xác nhận luồng bà thu kinh: mẫu Eve cố định là param=0/pStyle=1/ops=0/value=0.
                val result = cond.conditionParameter == 0 &&
                    cond.conditionParameterStyle == 1 &&
                    cond.conditionOps == 0 &&
                    cond.conditionValue == 0 &&
                    cond.conditionSubItem == 0
                if (!result) {
                    logger.warn {
                        "conditionClass=11 điều kiện thu kinh không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                }
                result
            }

            12 -> {
                when (cond.conditionParameter) {
                    1 -> {
                        // Số người trong tổ đội. DVT Team.maxMember=5 và
                        // Team.GetMember(roleId,true) luôn tính cả bản thân; solo = 1.
                        if (cond.conditionParameterStyle != 1 || cond.conditionSubItem != 0) {
                            false
                        } else {
                            val result = compareStep(state.teamSize, cond.conditionOps, cond.conditionValue)
                            logger.debug { "class=12 param=1 teamSize=${state.teamSize} ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                            result
                        }
                    }

                    3 -> {
                        // Solo / đang có tổ đội. DVT chỉ dùng shape ps=0,val=0,sub=0,ops=0/1.
                        // Quest 11958 (Tôn Hoàn đại bại) yêu cầu đơn nhân và dùng ops=1.
                        if (
                            cond.conditionParameterStyle == 0 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0 &&
                            cond.conditionOps in 0..1
                        ) {
                            val isAlone = state.teamSize <= 1
                            if (cond.conditionOps == 1) isAlone else !isAlone
                        } else false
                    }

                    4 -> {
                        // Cặp đôi hợp lệ cho event Valentine/Kết hôn.
                        // DVT chỉ dùng ps=0,ops=5,val=0,sub=0; các chain sau đó tiếp tục
                        // kiểm tra đồ/vật phẩm của người còn lại qua class12/param0.
                        if (
                            cond.conditionParameterStyle == 0 &&
                            cond.conditionOps == 5 &&
                            cond.conditionValue == 0 &&
                            cond.conditionSubItem == 0
                        ) state.romancePairValid else false
                    }

                    0 -> {
                        // Legacy reverse: param0 từng được dùng như số lần hoàn thành eveNo.
                        // pStyle 0/1 giữ contract đã regression-test; pStyle 3/4/5/6
                        // mang semantics khác và không được nhầm thành completed-count.
                        if (cond.conditionParameterStyle !in 0..1 || cond.conditionSubItem != 0) {
                            logger.warn {
                                "conditionClass=12 param0 shape chưa xác minh pStyle=${cond.conditionParameterStyle} " +
                                    "ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                            }
                            false
                        } else {
                            val value = state.completedEveCounts[state.currentEveNo] ?: 0
                            val result = compareStep(value, cond.conditionOps, cond.conditionValue)
                            logger.debug { "class=12 param=0 eveNo=${state.currentEveNo} count=$value ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                            result
                        }
                    }

                    else -> {
                        logger.warn {
                            "conditionClass=12 chưa triển khai param=${cond.conditionParameter} " +
                                "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                                "val=${cond.conditionValue} → false"
                        }
                        false
                    }
                }
            }

            13 -> evaluateOrganizationCondition(cond, state)

            14 -> {
                // DVT class14 = trạng thái mở hoạt động của scene.
                // Talk 52514 = "Sự kiện chưa mở.".
                // Dữ liệu Eve dùng ops=1 cho nhánh hoạt động/reward và ops=2 cho nhánh chưa mở.
                // Chỉ scene có ActivityTime ID ánh xạ chắc chắn mới có activityOpen != null.
                val open = state.activityOpen
                if (
                    open == null ||
                    cond.conditionParameter != 0 ||
                    cond.conditionParameterStyle != 0 ||
                    cond.conditionValue != 0 ||
                    cond.conditionSubItem != 0
                ) {
                    logger.warn {
                        "conditionClass=14 chưa có mapping ActivityTime chắc chắn " +
                            "param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} " +
                            "ops=${cond.conditionOps} val=${cond.conditionValue} → false"
                    }
                    false
                } else {
                    when (cond.conditionOps) {
                        1 -> open
                        2 -> !open
                        else -> {
                            logger.warn { "conditionClass=14 ops=${cond.conditionOps} chưa xác minh → false" }
                            false
                        }
                    }
                }
            }

            15 -> evaluateReincarnationCondition(cond, state)

            16 -> {
                if (cond.conditionParameterStyle != 1 || cond.conditionSubItem != 0) {
                    logger.warn {
                        "conditionClass=16 hình dạng không xác định param=${cond.conditionParameter} " +
                            "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                            "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
                    }
                    return false
                }
                val qty = state.storedItems[cond.conditionParameter] ?: 0
                val result = compareStep(cond.conditionValue, cond.conditionOps, qty)
                logger.debug {
                    "class=16 storedItemId=${cond.conditionParameter} qty=$qty " +
                        "ops=${cond.conditionOps} val=${cond.conditionValue} → $result"
                }
                result
            }

            18 -> {
                if (!isKnownRoleCountIndex(cond.conditionParameter)) {
                    logger.warn { "conditionClass=18 chỉ số RoleCount không xác định param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                    false
                } else {
                    val actual = state.roleCountValues[cond.conditionParameter] ?: 0
                    val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
                    logger.debug { "class=18 roleCount=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
                    result
                }
            }

            else -> {
                // conditionClass chưa cài đặt — mức WARN để dễ chẩn đoán
                logger.warn { "Chưa triển khai conditionClass=${cond.conditionClass} param=${cond.conditionParameter} pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} val=${cond.conditionValue} → false" }
                false
            }
        }
    }

    /**
     * Class=13 — điều kiện Quân đoàn / Thành trì.
     *
     * DVT evidence:
     * - param0: NPC Di dân; chỉ người chơi không có Organization.Id mới được đi tiếp.
     * - param1/pStyle1/value1..5: 5 thành Đại Huyện/Chân Định/Lâm Truy/Lang Gia/Đại Mạc.
     *   ops5 = cùng thành, ops6 = khác thành.
     * - param2/pStyle1..3: quyền/chức vụ Quân đoàn trong thành.
     *
     * Offline server chưa có subsystem Quân đoàn nên state mặc định organizationId/city/rank = 0.
     * Nhánh "không thuộc quân đoàn thành này" vẫn chạy; đặc quyền xây dựng/kho bí đạo vẫn đóng.
     */
    private fun evaluateOrganizationCondition(
        cond: EveCondition,
        state: PlayerEventState,
    ): Boolean {
        return when (cond.conditionParameter) {
            0 -> {
                if (cond.conditionParameterStyle != 1 || cond.conditionSubItem != 0) {
                    false
                } else {
                    val noOrganization = if (state.organizationId == 0L) 1 else 0
                    when {
                        cond.conditionOps == 1 && cond.conditionValue == 1 -> noOrganization == 1
                        cond.conditionOps == 2 && cond.conditionValue == 0 -> noOrganization != 0
                        else -> false
                    }
                }
            }

            1 -> when (cond.conditionParameterStyle) {
                1 -> {
                    if (cond.conditionSubItem != 0 || cond.conditionValue !in 1..5) {
                        false
                    } else {
                        when (cond.conditionOps) {
                            5 -> state.organizationCityId == cond.conditionValue
                            6 -> state.organizationCityId != cond.conditionValue
                            else -> false
                        }
                    }
                }
                2 -> false
                else -> false
            }

            2 -> {
                if (
                    cond.conditionParameterStyle !in 1..3 ||
                    cond.conditionOps != 0 ||
                    cond.conditionValue != 0 ||
                    cond.conditionSubItem != 0
                ) {
                    false
                } else {
                    state.organizationRank == cond.conditionParameterStyle
                }
            }

            else -> false
        }
    }

    private fun isKnownRoleCountIndex(index: Int): Boolean =
        // 1=số đếm hội thoại Đào Viên Kết Nghĩa, 189=số đếm event bài tập hè (đầu ghi resultClass=14 đã xác định, xem docs/eve-gap-analysis.md)
        index in setOf(1, 100, 101, 102, 108, 109, 189, 192, 668, 822)

    private fun evaluateBagSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        if (state.bagCapacity <= 0) return null

        val freeSlots = (state.bagCapacity - state.bagUsedSlots).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    private fun evaluatePetSpaceCondition(cond: EveCondition, state: PlayerEventState): Int? {
        val maxPets = if (state.maxFollowPets > 0) state.maxFollowPets else PetSystem.MAX_FOLLOW
        val currentPets = if (state.followPetCount > 0) state.followPetCount else state.followNpcIds.size
        val freeSlots = (maxPets - currentPets).coerceAtLeast(0)
        return when (cond.conditionParameterStyle) {
            1 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots == 0) 0 else null
            2 -> if (cond.conditionOps == 0 && cond.conditionValue == 0 && freeSlots > 0) 0 else null
            4 -> freeSlots
            else -> null
        }
    }

    /**
     * Class9 condition trên tướng mang theo.
     *
     * Chỉ triển khai các shape đã xác minh từ toàn bộ Eve.emg:
     * - pStyle1/ops2: level > value (mốc 9/19/24/29/59 → cấp 10/20/25/30/60).
     * - pStyle2/ops2: loyalty > value, thường đi cặp với level.
     * - pStyle3/ops1/value0: tướng chưa được chọn xuất chiến; lời thoại Bạch Chí
     *   yêu cầu "hãy để ta xuất chiến" xác nhận trực tiếp semantics này.
     *
     * Các shape pStyle2 ops1/5 bị overload bởi quest legacy nên vẫn bảo thủ false.
     */
    private fun evaluateFollowNpcCondition(cond: EveCondition, state: PlayerEventState): Boolean {
        if (cond.conditionSubItem != 0) return false
        val npcId = cond.conditionParameter
        return when {
            cond.conditionParameterStyle == 1 && cond.conditionOps == 2 -> {
                val level = state.followNpcLevels[npcId] ?: return false
                level > cond.conditionValue
            }
            cond.conditionParameterStyle == 2 && cond.conditionOps == 2 -> {
                val loyalty = state.followNpcLoyalties[npcId] ?: return false
                loyalty > cond.conditionValue
            }
            cond.conditionParameterStyle == 3 &&
                cond.conditionOps == 1 &&
                cond.conditionValue == 0 -> {
                npcId in state.followNpcIds && npcId !in state.deployedNpcIds
            }
            else -> {
                logger.warn {
                    "conditionClass=9 shape chưa xác minh npcId=$npcId " +
                        "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                        "val=${cond.conditionValue} → false"
                }
                false
            }
        }
    }

    /**
     * Comparator định danh cho class7/param0:
     * - pStyle2 = hệ nhân vật (EElement 1..4)
     * - pStyle3 = NPC ID biến hình hiện tại
     *
     * Production Eve dùng ops5 cho "đúng ID" và ops6 cho "khác ID".
     */
    fun compareIdentity(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0, 5 -> actual == expected
        6 -> actual != expected
        else -> {
            logger.warn { "Identity ops=$ops chưa có bằng chứng actual=$actual expected=$expected → false" }
            false
        }
    }

    /** Comparator level của Eve.emg. */
    fun compareLevel(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0 -> actual == expected
        1 -> actual < expected
        2 -> actual > expected
        3 -> actual <= expected
        4 -> actual > expected
        5 -> actual >= expected
        6 -> actual != expected
        else -> {
            logger.warn { "Level ops=$ops chưa có bằng chứng actual=$actual expected=$expected → false" }
            false
        }
    }

    /** Comparator số riêng của Eve.emg, dùng cho Moral và Honor. */
    fun compareEveNumeric(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0 -> actual == expected
        1 -> actual < expected
        2 -> actual > expected
        4 -> actual >= expected
        5 -> actual == expected
        6 -> actual != expected
        else -> {
            logger.warn { "Eve numeric ops=$ops chưa có bằng chứng actual=$actual expected=$expected → false" }
            false
        }
    }

    /** Alias giữ tương thích test/code cũ của Moral. */
    fun compareMoral(actual: Int, ops: Int, expected: Int): Boolean =
        compareEveNumeric(actual, ops, expected)

    private fun evaluateMoneyCondition(gold: Int, ops: Int, value: Int): Boolean =
        when (ops) {
            1 -> gold == value
            2 -> gold > value
            4 -> gold >= value
            5 -> gold <= value
            6 -> gold != value
            else -> compareStep(gold, ops, value)
        }

    private fun evaluateReincarnationCondition(cond: EveCondition, state: PlayerEventState): Boolean {
        if (cond.conditionParameterStyle != 0 || cond.conditionSubItem != 0) {
            logger.warn {
                "conditionClass=15 điều kiện chuyển sinh chưa được xác nhận param=${cond.conditionParameter} " +
                    "pStyle=${cond.conditionParameterStyle} ops=${cond.conditionOps} " +
                    "val=${cond.conditionValue} sub=${cond.conditionSubItem} → false"
            }
            return false
        }

        val actual = when (cond.conditionParameter) {
            0 -> state.rebornCount
            1 -> state.npcSecondReincarnationStatus
            else -> null
        }
        if (actual == null) {
            logger.warn {
                "conditionClass=15 chưa triển khai param=${cond.conditionParameter} " +
                    "ops=${cond.conditionOps} val=${cond.conditionValue} → false"
            }
            return false
        }

        val result = compareStep(actual, cond.conditionOps, cond.conditionValue)
        logger.debug { "class=15 param=${cond.conditionParameter} actual=$actual ops=${cond.conditionOps} val=${cond.conditionValue} → $result" }
        return result
    }

    /**
     * Phép toán so sánh
     *
     * DVT Lua DungeonCriteriaData.CompareResult：
     * 1→== / 2→< / 3→<= / 4→> / 5→>= / 6→!=。
     * Eve.emg còn có nhiều dạng đẳng thức ops=0, vì vậy giữ lại 0→== dành riêng cho Eve.
     */
    fun compareStep(actual: Int, ops: Int, expected: Int): Boolean = when (ops) {
        0 -> actual == expected
        1 -> actual == expected
        2 -> actual < expected
        3 -> actual <= expected
        4 -> actual > expected
        5 -> actual >= expected
        6 -> actual != expected
        else -> {
            logger.warn { "ops=$ops không xác định actual=$actual expected=$expected → false" }
            false
        }
    }
}

package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.data.MarkDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.data.NpcEventData
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.MountConstants
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord

private val logger = KotlinLogging.logger {}

/**
 * Trình thực thi kết quả sự kiện Eve — Hiệu ứng phía máy chủ của resultClass
 *
 * Nhiệm vụ:
 * - resultClass=1 (Vật phẩm) Trao/Thu hồi
 * - resultClass=2 (Nhiệm vụ) Tăng giảm bước/Hoàn thành
 * - resultClass=3 (Đội ngũ NPC) Thêm vào/Tàng hình/Hiện hình
 * - resultClass=4 (Kỹ năng) Học/Nâng cấp
 * - resultClass=5 (Tiền) Thưởng/Trừ
 * - resultClass=7 (Thao tác người chơi) Thưởng tiền (pStyle=6 trao tiền vàng)
 * - resultClass=8 (Võ tướng thưởng) Thêm vào/Rời đi
 * - Tự động bắt võ tướng sau khi chiến thắng trận chiến sự kiện
 *
 * Được tách ra từ NpcEventHandler, do EveFlowController gọi khi resultType=0 và resultType=7.
 */
class EveResultExecutor(
    private val responseSender: ResponseSender,
    private val questSystem: QuestSystem,
    private val inventorySystem: InventorySystem,
    private val petSystem: PetSystem,
    private val skillSystem: SkillSystem,
    private val moneySystem: MoneySystem,
    private val npcDefs: Map<Int, NpcDef>,
    private val markDefs: Map<Int, MarkDef>,
    private val markFlagToGuideId: Map<Int, Int>,
    private val eveData: Map<Int, SceneEveData>,
    private val mountSystem: MountSystem,
    private val achievementSystem: AchievementSystem,
    private val bitFlagSystem: BitFlagSystem,
    private val roleCountSystem: RoleCountSystem,
    private val dailyProgressSystem: DailyProgressSystem? = null,
    private val pointSystem: PointSystem? = null,
    private val collectionSystem: CollectionSystem? = null,
    private val playerStateManager: PlayerStateManager? = null,
    private val characterRepository: CharacterRepository? = null,
    private val markStepMissionIds: Set<Int> = emptySet(),
) {
    private fun isDvtPackedMissionHint(result: EveResult): Boolean =
        EvePackedMissionHintPolicy.isNonMutatingHint(result, markDefs.keys)

    /** Hook cho subsystem đăng ký arena từ mission marker Eve. */
    var onMissionStarted: (suspend (GameSession, Int) -> Unit)? = null

    private companion object {
        // Chỉ dùng khi IOS_MarkStep_C.dat không khả dụng. Production bình thường dùng data-driven detection.
        val LEGACY_MARKER_MISSION_IDS = setOf(11804, 12283, 12285, 12396)
    }

    /**
     * Thực thi hiệu ứng phía máy chủ dựa theo resultClass
     *
     * Chỉ gọi khi resultType=0.
     */
    /** Check the selected chain before any exchanges or quest mutations occur. */
    suspend fun canExecuteResults(session: GameSession, results: List<EveResult>): Boolean {
        // The DVT second-generation general lifecycle is not implemented.
        // Guard the WHOLE remaining Eve chain so mission 11980 cannot advance
        // before its required child generation/management action silently skips.
        val childAction = if (DvtChildLifecycleGate.hasCandidateShape(results)) {
            DvtChildLifecycleGate.firstUnavailable(session.mapId, results)
        } else null
        if (childAction != null) {
            logger.warn {
                "Block unsupported DVT child lifecycle: map=${session.mapId} cid=${session.roleId} " +
                    "type=${childAction.resultType} class=${childAction.resultClass} " +
                    "style=${childAction.parameterStyle}"
            }
            responseSender.sendSystemMessage(
                session,
                "Chức năng nuôi dưỡng tướng thế hệ hai hiện chưa khả dụng. " +
                    "Nhiệm vụ 11980 được giữ nguyên, không tự tạo con hay phát thưởng."
            )
            return false
        }

        val unsupported = results.firstOrNull { r ->
            r.resultType in setOf(0, 7) && (
                r.resultClass in setOf(11, 13, 15) ||
                    (r.resultClass == 1 && EveResultClassPolicy.itemDelta(r.parameterStyle, r.resultValue) == null) ||
                    (r.resultClass == 2 && EveResultClassPolicy.missionOperation(r.parameterStyle, r.resultValue) == null) ||
                    (r.resultClass == 2 && !isDvtPackedMissionHint(r) &&
                        r.parameterStyle in setOf(1, 2) &&
                        (r.resultValue !in 0..255 || r.parameter <= 0))
                )
        }
        if (unsupported == null) {
            val missionResults = results.filter {
                it.resultType in setOf(0, 7) && it.resultClass == 2 &&
                    !isDvtPackedMissionHint(it)
            }
            if (missionResults.any { it.parameterStyle in setOf(1, 2) }) {
                val steps = questSystem.getMissions(session.roleId.toLong())
                    .associate { it.missionId to it.step }
                val rejected = EveMissionPreflightPolicy.validate(missionResults, steps)
                if (rejected != null) {
                    logger.warn {
                        "Refuse inconsistent quest chain before any effects: map=${session.mapId} " +
                            "cid=${session.roleId} mission=${rejected.missionId} reason=${rejected.reason}"
                    }
                    responseSender.sendSystemMessage(
                        session,
                        "Tiến độ nhiệm vụ không hợp lệ (ID ${rejected.missionId}). Đã dừng trước khi thay đổi nhiệm vụ hoặc phát thưởng."
                    )
                    return false
                }
            }
            // Check gold balance and cap before making any item/quest changes.
            // Type 3 battle or type 6 Surface starts an undecided future segment.
            val hasMoneyEffect = results.takeWhile { it.resultType !in setOf(3, 6) }
                .any { r ->
                    r.resultType in setOf(0, 7) &&
                        (r.resultClass == 5 ||
                            (r.resultClass == 7 && r.parameterStyle == 6 && r.resultValue > 0))
                }
            if (hasMoneyEffect) {
                val failure = EveQuestGoldPreflightPolicy.validate(
                    results, moneySystem.getGold(session.roleId.toLong())
                )
                if (failure != null) {
                    logger.warn {
                        "DVT quest gold preflight rejected: map=${session.mapId} " +
                            "cid=${session.roleId} reason=${failure.failure} " +
                            "required=${failure.required} available=${failure.available}"
                    }
                    responseSender.sendSystemMessage(
                        session,
                        "Không đủ vàng hoặc đã vượt giới hạn vàng cho chuỗi nhiệm vụ. " +
                            "Chưa thay đổi vật phẩm hay tiến độ."
                    )
                    return false
                }
            }
            // Combat and choices may supply/change items; validate the current segment only.
            val itemChanges = results.takeWhile { it.resultType !in setOf(3, 6) }
                .filter { it.resultType in setOf(0, 7) && it.resultClass == 1 }
                .map { it.parameter to EveResultClassPolicy.itemDelta(it.parameterStyle, it.resultValue)!! }
            if (itemChanges.size > 1 && !inventorySystem.canApplyQuestItemChanges(session.roleId.toLong(), itemChanges)) {
                responseSender.sendSystemMessage(session, "Chưa đủ đồ nộp hoặc chỗ trống để nhận toàn bộ thưởng nhiệm vụ. Đã dừng trước khi đổi đồ.")
                return false
            }
            val npcResults = results.filter { it.resultType in setOf(0, 7) &&
                (it.resultClass == 8 || (it.resultClass == 3 && it.parameterStyle == 1)) }
            if (npcResults.any { it.parameterStyle == 1 }) {
                val charId = session.roleId.toLong()
                val ids = petSystem.getFollowPets(charId).values.map { it.npcId }.toMutableList()
                val capacity = petSystem.getUnlockedFollowCapacity(charId)
                val storedIds = (petSystem.getCartPets(charId).values + petSystem.getInnPets(charId).values +
                    petSystem.getWarehousePets(charId)).map { it.npcId }.toSet()
                for (r in npcResults) {
                    if (r.resultClass == 8 && r.parameterStyle == 2) {
                        ids.remove(r.parameter)
                    } else if (r.parameterStyle == 1) {
                        val npcId = if (r.resultClass == 3) eveData[session.mapId]?.npcs?.get(r.parameter)?.npcId else r.parameter
                        // DVT EventHandler[0]/class3 keeps an escort already in the team.
                        if (r.resultClass == 3 && npcId != null && npcId in ids) continue
                        if (npcId == null || npcId !in npcDefs || npcId in ids || npcId in storedIds || ids.size >= capacity) {
                            responseSender.sendSystemMessage(session, "Chưa thể nhận võ tướng nhiệm vụ. Hãy kiểm tra ô võ tướng trống rồi thử lại.")
                            return false
                        }
                        ids.add(npcId)
                    }
                }
            }
            return true
        }
        logger.warn {
            "Dừng nhánh nhiệm vụ chưa hỗ trợ: map=${session.mapId} " +
                "class=${unsupported.resultClass} param=${unsupported.parameter} " +
                "style=${unsupported.parameterStyle} value=${unsupported.resultValue}"
        }
        if (unsupported.resultClass == 11) {
            // Source-verified DVT organization resource, NOT character gold.
            // Keep the WHOLE chain stopped before item/quest rewards while
            // organization ownership, treasury persistence and synchronization
            // are unavailable. Do not grant the other results partially.
            val resource = DvtOrgResourceResultPolicy.decode(
                unsupported.resultType,
                unsupported.resultClass,
                unsupported.parameter,
                unsupported.parameterStyle,
                unsupported.resultValue
            )
            logger.warn {
                "Chặn thưởng tài nguyên quân đoàn DVT do chưa có kho tổ chức: " +
                    "map=${session.mapId} cid=${session.roleId} " +
                    "resource=${resource?.kind} amount=${resource?.amount} " +
                    "style=${unsupported.parameterStyle}"
            }
            responseSender.sendSystemMessage(
                session,
                "Nhiệm vụ này có thưởng tài nguyên quân đoàn nhưng hệ thống kho quân đoàn " +
                    "chưa khả dụng. Đã dừng toàn bộ nhánh; không trừ vật phẩm, không chuyển bước hoặc phát thưởng thiếu."
            )
            return false
        }
        responseSender.sendSystemMessage(session, "Nhánh nhiệm vụ này chưa có cấu hình xử lý đầy đủ. Đã dừng xử lý; vui lòng chọn nhiệm vụ khác.")
        return false
    }

    suspend fun executeResultClass(session: GameSession, result: EveResult): Boolean {
        if (!canExecuteResults(session, listOf(result))) return false
        when (result.resultClass) {
            1 -> return handleItemResult(session, result)
            2 -> return handleMissionResult(session, result)
            3 -> return handleNpcResult(session, result)
            4 -> return handleSkillResult(session, result)
            5 -> return handleMoneyResult(session, result)
            7 -> return handlePlayerResult(session, result)
            8 -> return handleRewardNpcResult(session, result)
            14 -> handleRoleCountResult(session, result)
            10 -> {
                if (EveResultClassPolicy.isExplicitNoOp(result.resultClass, result.parameterStyle)) {
                    logger.debug { "${session.characterName} resultClass=10 pStyle=${result.parameterStyle} được chỉ định rõ là no-op (bỏ qua)" }
                } else {
                    logger.warn { "${session.characterName} Chưa xử lý resultClass=10 pStyle=${result.parameterStyle} " +
                            "type=${result.resultType} param=${result.parameter} val=${result.resultValue} meanNo=${result.resultMeanNo}" }
                }
            }
            else -> logger.warn { "${session.characterName} Chưa xử lý resultClass=${result.resultClass} " +
                    "type=${result.resultType} param=${result.parameter} pStyle=${result.parameterStyle} " +
                    "val=${result.resultValue} meanNo=${result.resultMeanNo}" }
        }
        return true
    }

    /**
     * Tự động bắt võ tướng sau khi chiến thắng trận chiến sự kiện
     *
     * Dành cho các trận chiến sự kiện không có chuỗi tiếp theo conditionClass=8 (ví dụ: hướng dẫn bắt Ba Đậu Yêu),
     * quét các điều kiện cls=9 trong sự kiện, tự động thêm NPC được tham chiếu vào đội ngũ võ tướng.
     */
    suspend fun autoCaptureFromEvent(session: GameSession, charId: Long, eventData: NpcEventData) {
        val expectedNpcIds = eventData.conditions
            // param != 0 compares attributes of a named NPC; value is not an NPC ID.
            .filter { it.conditionClass == 9 && it.conditionParameter == 0 && it.conditionParameterStyle == 1 }
            .map { it.conditionValue }
            .distinct()

        if (expectedNpcIds.isEmpty()) return

        val currentPets = petSystem.getFollowPets(charId).values.map { it.npcId }.toSet()

        for (npcId in expectedNpcIds) {
            if (npcId in currentPets) continue
            val npcDef = npcDefs[npcId]
            if (npcDef == null) {
                logger.warn { "autoCaptureFromEvent: npcDefs[$npcId] không tồn tại, không thể tự động bắt" }
                continue
            }
            val added = petSystem.addFollowNpc(charId, npcId, npcDef, canGrow = 1)
            if (added == null) {
                if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                    petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                        responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                    }
                }
                continue
            }
            val (slotIndex, pet) = added
            responseSender.sendFollowNpcAdd(session, session.playerId, slotIndex, npcId, pet.canGrow, showMsg = true)
            collectionSystem?.markWarriorOwned(charId, npcId, session)
            logger.info { "${session.characterName} Trận chiến sự kiện bắt được: npcId=$npcId → slot=$slotIndex" }
        }

        val updatedPets = petSystem.getFollowPets(charId)
        if (updatedPets.isNotEmpty()) {
            responseSender.sendFollowNpcFullData(session, updatedPets.values.toList())
        }
    }

    // ── Xử lý các resultClass riêng biệt ──

    /**
     * resultClass=5 (Tiền)
     *
     * Trong các mẫu Eve.emg, parameter cố định là 0, pStyle=1 là thêm, pStyle=2 là trừ.
     */
    private fun handleMoneyResult(session: GameSession, result: EveResult): Boolean {
        val charId = session.roleId.toLong()
        val operation = EveResultClassPolicy.moneyOperation(result.parameterStyle, result.resultValue)
            ?: return false
        val success = when (operation.kind) {
            EveMoneyResultKind.ADD -> moneySystem.addGold(charId, operation.amount)
            EveMoneyResultKind.SUBTRACT -> moneySystem.subtractGold(charId, operation.amount)
        }
        if (!success) {
            logger.warn { "${session.characterName} Giao dịch tiền nhiệm vụ thất bại: kind=${operation.kind} amount=${operation.amount}" }
            responseSender.sendSystemMessage(session, "Không thể thực hiện giao dịch tiền của nhiệm vụ. Hãy kiểm tra số vàng rồi thử lại.")
            return false
        }
        when (operation.kind) {
            EveMoneyResultKind.ADD -> responseSender.sendAddMoney(session, operation.amount)
            EveMoneyResultKind.SUBTRACT -> responseSender.sendSubtractMoney(session, operation.amount)
        }
        return true
    }

    /**
     * resultClass=14 (Tăng giảm bộ đếm RoleCount)
     *
     * Giám định mẫu Eve.emg (docs/eve-gap-analysis.md):
     * - param = Chỉ số RoleCount (vd: 1=Số lần đối thoại kết nghĩa vườn đào, 189=Số đếm hoạt động bài tập hè)
     * - pStyle=1 tăng val, pStyle=2 giảm val, pStyle=3 đưa về 0 (val=0)
     *
     * Thường dùng theo cặp với conditionClass=18 (So sánh RoleCount):
     * Ở cảnh 10951 eve=34 dùng tùy chọn +1/-1/Về 0 cho RoleCount[1], cond=18 chuyển đổi đối thoại dựa trên giá trị đó.
     */
    private fun handleRoleCountResult(session: GameSession, result: EveResult) {
        val charId = session.roleId.toLong()
        val countIndex = result.parameter

        val newValue = when (result.parameterStyle) {
            1 -> roleCountSystem.increment(charId, countIndex, result.resultValue)
            2 -> roleCountSystem.increment(charId, countIndex, -result.resultValue)
            3 -> {
                val max = roleCountSystem.getMaxValue(charId, countIndex)
                roleCountSystem.setValue(charId, countIndex, 0, max)
                0
            }
            else -> {
                logger.warn {
                    "${session.characterName} resultClass=14 Không rõ pStyle=${result.parameterStyle} " +
                            "param=$countIndex val=${result.resultValue}"
                }
                return
            }
        }

        val maxValue = roleCountSystem.getMaxValue(charId, countIndex)
        responseSender.sendRoleCountUpdate(session, mapOf(countIndex to (newValue to maxValue)))
        logger.info { "${session.characterName} Bộ đếm sự kiện thay đổi: RoleCount[$countIndex] = $newValue" }
    }

    /**
     * DVT resultClass=7 / pStyle=3 direct Point purchase.
     *
     * Scene 12180 encodes value = quantity*100000 + itemId and meanNo = Point price.
     * The paired conditionClass=7/param19 checks the same real Point balance first.
     */
    private suspend fun handleDirectPointPurchase(
        session: GameSession,
        purchase: EvePointPurchase
    ): Boolean {
        val points = pointSystem
        if (points == null) {
            logger.warn { "${session.characterName} Direct Point purchase: PointSystem chưa được nối" }
            return false
        }

        val charId = session.roleId.toLong()
        val currentPoint = points.getRealPoint(charId)
        if (currentPoint < purchase.price) {
            logger.warn {
                "${session.characterName} Direct Point purchase không đủ Point: " +
                    "có=$currentPoint cần=${purchase.price} item=${purchase.itemId} x${purchase.quantity}"
            }
            return false
        }

        if (!inventorySystem.canAddItemToBag(charId, purchase.itemId, purchase.quantity)) {
            logger.warn {
                "${session.characterName} Direct Point purchase thất bại: túi không đủ chỗ " +
                    "item=${purchase.itemId} x${purchase.quantity}"
            }
            return false
        }

        if (!points.subtractRealPoint(charId, purchase.price)) {
            logger.warn { "${session.characterName} Direct Point purchase: khấu trừ Point thất bại" }
            return false
        }

        val added = try {
            inventorySystem.addItemsToBagExact(charId, purchase.itemId, purchase.quantity)
        } catch (e: Exception) {
            points.addRealPoint(charId, purchase.price)
            logger.error(e) {
                "${session.characterName} Direct Point purchase lỗi khi thêm vật phẩm; đã hoàn Point"
            }
            return false
        }

        if (added == null) {
            points.addRealPoint(charId, purchase.price)
            logger.warn {
                "${session.characterName} Direct Point purchase bị race/túi thay đổi; đã hoàn ${purchase.price} Point"
            }
            return false
        }

        // Vật phẩm đã được InventorySystem ghi DB; lưu Point ngay để giảm cửa sổ lệch trạng thái khi server dừng đột ngột.
        try {
            points.savePoints(charId)
        } catch (e: Exception) {
            logger.error(e) {
                "${session.characterName} Direct Point purchase đã hoàn tất nhưng lưu Point tức thời thất bại; autosave sẽ thử lại"
            }
        }

        for ((slot, thing) in added) {
            responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
        }
        val (real, virtual) = points.getPoints(charId)
        responseSender.sendPointBalance(session, real, virtual)
        logger.info {
            "${session.characterName} Direct Point purchase: -${purchase.price} Point, " +
                "item=${purchase.itemId} x${purchase.quantity}, còn=$real"
        }
        return true
    }

    /**
     * Áp dụng resultType=0/resultClass=7/parameter=2 Moral delta.
     * DB là nguồn bền vững; chỉ cập nhật cache/runtime sau khi ghi DB thành công.
     */
    private suspend fun handleMoralDelta(session: GameSession, change: EveMoralDelta): Boolean {
        val states = playerStateManager ?: run {
            logger.warn { "Moral delta: PlayerStateManager chưa được nối" }
            return false
        }
        val repository = characterRepository ?: run {
            logger.warn { "Moral delta: CharacterRepository chưa được nối" }
            return false
        }
        val charId = session.roleId.toLong()
        if (states.getState(charId) == null) {
            logger.warn { session.characterName + " Moral delta: runtime player chưa khởi tạo" }
            return false
        }

        val current = states.getMoral(charId)[change.faction - 1]
        // S:005-003 truyền Moral bằng UInt16; không tự áp trần gameplay 5000 khi chưa có bằng chứng server DVT.
        val next = (current.toLong() + change.delta.toLong()).coerceIn(0L, 0xFFFFL).toInt()

        try {
            repository.updateMoral(charId, change.faction, next)
        } catch (e: Exception) {
            logger.error(e) {
                session.characterName + " Moral delta lưu DB thất bại: faction=" +
                    change.faction + " current=" + current + " delta=" + change.delta
            }
            return false
        }

        if (!states.setMoral(charId, change.faction, next)) {
            logger.error {
                session.characterName + " Moral delta: DB đã lưu nhưng runtime update thất bại, faction=" +
                    change.faction + " value=" + next
            }
            return false
        }

        // DVT protocal.lua S:008-014 gọi SetAttribute(Moral, absoluteValue, faction).
        responseSender.sendWorldAttribute(session, EAttribute.MORAL, next.toLong(), true, change.faction)
        logger.info {
            session.characterName + " Moral[" + change.faction + "] " + current + " -> " + next +
                " (delta=" + change.delta + ")"
        }
        return true
    }

    /**
     * resultClass=7 (Thao tác người chơi)
     *
     * Thống kê Eve.emg (resultType=7 cls=7):
     * - pStyle=1 (108 lần): Lần đầu=Mở Surface (do EveFlowController xử lý) / Sau khi chọn Surface=Mở bảo khố
     * - pStyle=2 (50 lần): Mở tiền trang → S:029-009 + S:029-006 + S:030-001
     * - pStyle=3 (166 lần): Mở cửa hàng NPC → S:027-003 (ENpcStore.Event=1)
     * - pStyle=4 (55 lần): Mở UINpcInn UI (Kho võ tướng + Lưu ở khách sạn) → S:031-015 + S:031-006 + S:031-007
     * - pStyle=5 (60 lần): Xác nhận nghỉ trọ (Hồi máu, mana) → S:031-002(cost) → C:031-001 → Trừ tiền+Hồi phục+Hoạt ảnh
     * - pStyle=6 (76 lần): Thưởng tiền vàng
     * - pStyle=10 (5 lần): Mở bảng xếp hạng Vô Giới → S:036-010
     * - pStyle=12 (1 lần): Giao diện chuyển sinh lần đầu của người chơi → S:036-011
     * - pStyle=13 (1 lần): Cửa hàng đổi điểm diệt địch → S:036-013
     * - pStyle=15 (1 lần): Lối vào duy trì thành trì Thủy Chiến → S:036-015
     * - pStyle=21 (1 lần): Giao diện chuyển sinh lần 2 của võ tướng → S:044-003
     * - pStyle=27/29 (Mỗi cái 11 lần type=7): Thông báo hoạt động tìm Ba Đậu Yêu ác quỷ → S:020-027 / S:020-029
     * - pStyle=30/32/35/39: Thông báo hoạt động không có payload → S:020-030/032/035/039
     */
    private suspend fun handlePlayerResult(session: GameSession, result: EveResult): Boolean {
        val charId = session.roleId.toLong()

        val pointPurchase = EveResultClassPolicy.directPointPurchase(
            parameterStyle = result.parameterStyle,
            parameter = result.parameter,
            resultValue = result.resultValue,
            resultMeanNo = result.resultMeanNo,
        )
        if (pointPurchase != null) {
            return handleDirectPointPurchase(session, pointPurchase)
        }

        val moralDelta = EveResultClassPolicy.moralDelta(
            resultType = result.resultType,
            parameter = result.parameter,
            parameterStyle = result.parameterStyle,
            resultValue = result.resultValue,
        )
        if (moralDelta != null) {
            return handleMoralDelta(session, moralDelta)
        }

        // Phòng ngừa lỗi các biến thể có mang giá trị (docs/eve-gap-analysis.md):
        // pStyle=1 + val=Mã số lôi đài (96~99/196~199) = Truyền tống đến lôi đài Vô Giới Đấu Trận, không phải mở bảo khố
        // pStyle=3 có value nhưng không đúng shape direct purchase đã chứng minh vẫn conservative skip.
        if (result.parameterStyle in setOf(1, 3) && result.resultValue != 0) {
            logger.warn {
                "${session.characterName} resultClass=7 pStyle=${result.parameterStyle} biến thể mang giá trị chưa được hiện thực: " +
                        "val=${result.resultValue} meanNo=${result.resultMeanNo} (Truyền tống lôi đài/Mua trực tiếp bằng Nguyên Bảo), bỏ qua"
            }
            return false
        }

        when (EveResultClassPolicy.playerOperation(result.resultType, result.parameterStyle)?.kind) {
            EvePlayerResultKind.OPEN_STORAGE -> {
                // Mở bảo khố — UIBank có 2 tab (Tiền trang+Bảo khố), cần gọi openBank để mở UI trước, sau đó mới nạp dữ liệu cho cả 2 tab
                responseSender.sendBankLevel(session, 0) // TODO: Lấy cấp độ thực tế từ dữ liệu nhân vật
                responseSender.sendOpenBank(session)      // S:029-006 → UI.Open(UIBank)
                // S:030-001/S:102-001 không có count; danh sách rỗng không tự xóa cache client.
                // Luôn clear trước khi gửi snapshot DB để reconnect/restart không giữ vật phẩm ma.
                responseSender.sendBankClear(session)
                responseSender.sendStorageClear(session)
                val bankItems = inventorySystem.getBank(charId)
                responseSender.sendBankAllItems(session, bankItems)
                val storageItems = inventorySystem.getStorage(charId)
                responseSender.sendStorageAllItems(session, storageItems)
                logger.info { "${session.characterName} Sự kiện mở bảo khố: ${storageItems.size} vật phẩm" }
            }
            EvePlayerResultKind.OPEN_BANK -> {
                // Mở tiền trang — Cần nạp dữ liệu cho cả 2 tab của UIBank
                responseSender.sendBankLevel(session, 0) // TODO: Lấy cấp độ thực tế từ dữ liệu nhân vật
                responseSender.sendOpenBank(session)
                responseSender.sendBankClear(session)
                responseSender.sendStorageClear(session)
                val bankItems = inventorySystem.getBank(charId)
                responseSender.sendBankAllItems(session, bankItems)
                val storageItems = inventorySystem.getStorage(charId)
                responseSender.sendStorageAllItems(session, storageItems)
                logger.info { "${session.characterName} Sự kiện mở tiền trang: ${bankItems.size} vật phẩm" }
            }
            EvePlayerResultKind.OPEN_NPC_STORE -> {
                // Mở cửa hàng NPC (Kích hoạt từ sự kiện)
                // kind=1 (ENpcStore.Event) → Client gọi UINpcStore.OnClose và gọi SetConduct(true) để tiến hành sự kiện
                responseSender.sendOpenNpcStore(session, 1)
                logger.info { "${session.characterName} Sự kiện mở cửa hàng" }
            }
            EvePlayerResultKind.OPEN_INN -> {
                // Mở UINpcInn UI (2 tab: Kho võ tướng + Lưu ở khách sạn)
                // Quy trình mở: S:031-015(Cấp độ) + S:031-006(Võ tướng khách sạn) + S:031-007(Mở UI)
                // Client trong OnOpen sẽ tự động gửi C:122-001 để truy vấn dữ liệu kho
                val innPets = petSystem.getInnPets(charId)
                responseSender.sendOpenInn(session, 1, innPets)
                logger.info { "${session.characterName} Sự kiện mở kho võ tướng" }
            }
            EvePlayerResultKind.INN_REST_CONFIRM -> {
                // Nghỉ trọ — Gửi hộp thoại xác nhận chi phí, client xác nhận xong C:031-001 → InnHandler.handleRest
                val cost = 100  // Tạm dùng giá trị cố định, sau này có thể tính dựa theo cấp độ khách sạn
                responseSender.sendInnRestConfirm(session, cost)
                logger.info { "${session.characterName} Sự kiện kích hoạt xác nhận nghỉ trọ: Chi phí=$cost" }
            }
            EvePlayerResultKind.ADD_MONEY -> {
                val amount = result.resultValue
                if (amount > 0) {
                    if (!moneySystem.addGold(charId, amount)) {
                        responseSender.sendSystemMessage(session, "Chưa thể nhận vàng thưởng; đã dừng nhiệm vụ để tránh mất thưởng.")
                        return false
                    }
                    responseSender.sendAddMoney(session, amount)
                    logger.info { "${session.characterName} Sự kiện thưởng tiền: $amount Vàng" }
                }
            }
            EvePlayerResultKind.OPEN_MESSAGE_BOARD -> {
                responseSender.sendOpenMessageBoard(session)
                logger.info { "Mở bảng nhắn từ Eve" }
            }
            EvePlayerResultKind.OPEN_GALAXY_LEADERBOARD -> {
                responseSender.sendOpenGalaxyLeaderboard(session)
                logger.info { "${session.characterName} Sự kiện mở bảng xếp hạng Vô Giới" }
            }
            EvePlayerResultKind.OPEN_PLAYER_REINCARNATION -> {
                responseSender.sendOpenPlayerReincarnation(session)
                logger.info { "${session.characterName} Sự kiện mở giao diện chuyển sinh người chơi" }
            }
            EvePlayerResultKind.OPEN_KILL_POINT_SHOP -> {
                responseSender.sendOpenKillPointShop(session)
                logger.info { "${session.characterName} Sự kiện mở cửa hàng đổi điểm diệt địch" }
            }
            EvePlayerResultKind.OPEN_WATER_WAR_ORG_MANAGE -> {
                responseSender.sendOpenWaterWarOrgManage(session)
                logger.info { "${session.characterName} Sự kiện mở lối vào duy trì thành trì Thủy Chiến" }
            }
            EvePlayerResultKind.OPEN_NPC_SECOND_REINCARNATION -> {
                responseSender.sendOpenNpcSecondReincarnation(session)
                logger.info { "${session.characterName} Sự kiện mở giao diện chuyển sinh lần 2 của võ tướng" }
            }
            EvePlayerResultKind.SEND_BADO_ERROR_MESSAGE -> {
                responseSender.sendBadoErrorMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo lỗi Ba Đậu Yêu ác quỷ" }
            }
            EvePlayerResultKind.SEND_BADO_START_MESSAGE -> {
                responseSender.sendBadoStartMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo bắt đầu sự kiện Ba Đậu Yêu ác quỷ" }
            }
            EvePlayerResultKind.SEND_CHRISTMAS_EVENT_MESSAGE -> {
                responseSender.sendChristmasEventMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo hoạt động lễ Giáng Sinh" }
            }
            EvePlayerResultKind.SEND_ARENA_ERROR_MESSAGE -> {
                responseSender.sendArenaErrorMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo lỗi Lôi đài" }
            }
            EvePlayerResultKind.SEND_LANTERN_ERROR_MESSAGE -> {
                responseSender.sendLanternErrorMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo lỗi Tết Nguyên Tiêu" }
            }
            EvePlayerResultKind.SEND_UNKNOWN_ACTIVITY_MESSAGE -> {
                responseSender.sendUnknownActivityMessage(session)
                logger.info { "${session.characterName} Sự kiện gửi thông báo hoạt động chưa rõ" }
            }
            null -> logger.debug {
                "${session.characterName} resultClass=7 type=${result.resultType} pStyle=${result.parameterStyle} (Chưa được hiện thực)"
            }
        }
        return true
    }

    /**
     * Xử lý bước resultClass=2 (Nhiệm vụ/Đánh dấu)
     *
     * - parameterStyle=1 (Thêm) / 2 (Giảm) / 3 (Hoàn thành: Xóa + Thiết lập cờ)
     */
    private suspend fun handleMissionResult(session: GameSession, result: EveResult): Boolean {
        // DVT native Lua ignores an ADD_STEP whose delta cannot fit a mission byte.
        // Packed mission-2 hint values encode a category and the real mission id.
        // Neither persist a mission-2 step nor send a truncated S024 packet.
        if (isDvtPackedMissionHint(result)) {
            logger.debug {
                "Eve DVT packed hint (no quest mutation): scene=" + session.mapId +
                    " category=" + (result.resultValue / 100_000) +
                    " mission=" + (result.resultValue % 100_000)
            }
            return true
        }
        val charId    = session.roleId.toLong()
        val missionId = result.parameter
        val operation = EveResultClassPolicy.missionOperation(result.parameterStyle, result.resultValue)
        logger.info { "${session.characterName} Tiến độ nhiệm vụ: mission=$missionId pStyle=${result.parameterStyle} value=${result.resultValue}" }
        when (operation?.kind) {
            EveMissionResultKind.ADD_STEP -> {
                val delta = operation.value
                val prevStep = questSystem.addMissionStep(charId, missionId, delta)
                    ?: run {
                        responseSender.sendSystemMessage(session, "Không thể tăng bước nhiệm vụ; đã dừng để tránh sai tiến độ.")
                        return false
                    }
                responseSender.sendAddMissionStep(session, missionId, delta)
                if (prevStep == 0) {
                    onMissionStarted?.invoke(session, missionId)
                    // DVT marker mission: có MarkDef.bitId nhưng không hề có MarkStep.
                    // Eve vẫn ADD_STEP +1 rồi các event kế tiếp kiểm pStyle=3 flag==1.
                    // Nhận diện theo IOS_Mark_C + IOS_MarkStep_C thay vì danh sách ID cứng.
                    if (isMarkerMission(missionId)) {
                        val bitId = markDefs[missionId]?.bitId ?: 0
                        if (bitId > 0) {
                            questSystem.setFlag(charId, bitId, 1)
                            responseSender.sendSetMissionFlags(
                                session, listOf(MissionFlagRecord(bitId, 1))
                            )
                            logger.info {
                                "${session.characterName} Kích hoạt marker mission=$missionId bitId=$bitId"
                            }
                        }
                    }

                    // Hoàn thành hướng dẫn thú cưỡi → Tự động mở khóa ngựa trắng
                    if (missionId == MountConstants.MOUNT_TUTORIAL_MISSION_ID) {
                        val flagId = mountSystem.useCollectionItem(charId, MountConstants.DEFAULT_MOUNT_NPC_ID)
                        if (flagId != null) {
                            responseSender.sendBitFlagChanges(session, listOf(flagId to true))
                            dailyProgressSystem?.incrementRoleCount(
                                session,
                                DailyProgressSystem.MOUNTS,
                            )
                            val mountData = mountSystem.getCache(charId)
                            if (mountData != null && mountData.currentNpcId > 0) {
                                responseSender.sendMountSwitch(session, mountData.currentNpcId)
                                session.mountNpcId = mountData.currentNpcId
                            }
                            logger.info { "${session.characterName} Hoàn thành hướng dẫn thú cưỡi → Mở khóa ngựa trắng flagId=$flagId" }
                        }
                    }
                }
            }
            EveMissionResultKind.DEC_STEP -> {
                val delta = operation.value
                val decResult = questSystem.decMissionStep(charId, missionId, delta)
                responseSender.sendDecMissionStep(session, missionId, delta)
                if (decResult.completed && decResult.bitId != 0) {
                    responseSender.sendSetMissionFlags(
                        session, listOf(MissionFlagRecord(decResult.bitId, 1))
                    )
                    logger.info { "${session.characterName} Nhiệm vụ hoàn thành: missionId=$missionId bitId=${decResult.bitId}" }
                }
            }
            EveMissionResultKind.COMPLETE_MISSION -> {
                val completeResult = questSystem.completeMission(charId, missionId)
                responseSender.sendRemoveMission(session, missionId)
                if (completeResult.bitId != 0) {
                    responseSender.sendSetMissionFlags(
                        session, listOf(MissionFlagRecord(completeResult.bitId, 1))
                    )
                    logger.info { "${session.characterName} Nhiệm vụ hoàn thành (pStyle=3): missionId=$missionId bitId=${completeResult.bitId}" }
                }
            }
            EveMissionResultKind.SET_FLAG -> {
                val step = EveResultClassPolicy.dungeonStepAssignment(markDefs[missionId]?.kind ?: 0, operation.value)
                if (step != null) {
                    questSystem.setMissionStep(charId, missionId, step)
                    responseSender.sendRemoveMission(session, missionId)
                    responseSender.sendAddMissionStep(session, missionId, step)
                } else if (operation.value > 0) {
                    val bitId = operation.value
                    questSystem.setFlag(charId, bitId, 1)
                    responseSender.sendSetMissionFlags(session, listOf(MissionFlagRecord(bitId, 1)))
                    logger.info { "${session.characterName} Thiết lập cờ nhiệm vụ trực tiếp: missionId=$missionId bitId=$bitId" }
                } else {
                    logger.warn { "${session.characterName} resultClass=2 pStyle=4 bitId không hợp lệ: missionId=$missionId value=${operation.value}" }
                }
            }
            EveMissionResultKind.REMOVE_MISSION -> {
                questSystem.removeMission(charId, missionId)
                responseSender.sendRemoveMission(session, missionId)
                logger.info { "${session.characterName} Xóa nhiệm vụ (không đặt cờ): missionId=$missionId" }
            }
            null -> logger.warn { "Không rõ parameterStyle=${result.parameterStyle}, missionId=$missionId" }
        }

        // Chủ động kiểm tra thành tựu sau khi tiến hành nhiệm vụ (Do server điều khiển)
        // Giải quyết vấn đề điều kiện MissionStep của client không thể phát hiện sau khi nhiệm vụ đã bị xóa
        val newlyCompleted = achievementSystem.checkAndComplete(charId)
        if (newlyCompleted.isNotEmpty()) {
            val flags = bitFlagSystem.getSetFlagIds(charId)
            val packedFlags = bitFlagSystem.packFlags(flags)
            responseSender.sendInitBitFlags(session, packedFlags)
            for (achievementId in newlyCompleted) {
                responseSender.sendAchievementComplete(session, 0, achievementId)
            }
        }
        return true
    }

    private fun isMarkerMission(missionId: Int): Boolean {
        val bitId = markDefs[missionId]?.bitId ?: 0
        if (bitId <= 0) return false
        return if (markStepMissionIds.isNotEmpty()) {
            missionId !in markStepMissionIds
        } else {
            missionId in LEGACY_MARKER_MISSION_IDS
        }
    }

    /**
     * resultClass=1 (Vật phẩm) Trao/Thu hồi
     */
    private suspend fun handleItemResult(session: GameSession, result: EveResult): Boolean {
        val charId = session.roleId.toLong()
        val itemId = result.parameter
        val delta = EveResultClassPolicy.itemDelta(result.parameterStyle, result.resultValue)
            ?: return false
        when {
            delta > 0 -> {
                val added = inventorySystem.addItemsToBagExact(charId, itemId, delta)
                if (added == null) {
                    responseSender.sendSystemMessage(session, "Không thể nhận đủ vật phẩm nhiệm vụ. Hãy chừa chỗ trong túi rồi thử lại.")
                    logger.warn { "Dừng sự kiện: không trao đủ itemId=$itemId x$delta charId=$charId" }
                    return false
                }
                for ((slot, thing) in added) {
                    responseSender.sendSetBagItem(session, slot, thing, showMsg = true)
                }
                logger.info { "${session.characterName} Sự kiện trao vật phẩm: itemId=$itemId x$delta slots=${added.map { it.first }}" }
            }
            delta < 0 -> {
                var remaining = -delta
                val consumptions = mutableListOf<Pair<Int, Int>>()
                for ((slot, thing) in inventorySystem.getBag(charId).toSortedMap()) {
                    if (thing.itemId != itemId || thing.quantity <= 0 || remaining <= 0) continue
                    val qty = minOf(remaining, thing.quantity)
                    consumptions += slot to qty
                    remaining -= qty
                }
                if (remaining > 0 || !inventorySystem.removeBagItemsExact(charId, consumptions)) {
                    responseSender.sendSystemMessage(session, "Chưa đủ vật phẩm cần nộp cho nhiệm vụ.")
                    return false
                }
                for ((slot, qty) in consumptions) {
                    responseSender.sendReduceBagItem(session, slot, qty)
                }
                logger.info { "${session.characterName} Sự kiện thu hồi vật phẩm: itemId=$itemId x${-delta}" }
            }
        }
        return true
    }

    /**
     * Xử lý resultClass=3 (Thao tác đội ngũ NPC)
     */
    private suspend fun handleNpcResult(session: GameSession, result: EveResult): Boolean {
        when (result.parameterStyle) {
            1 -> {
                val charId = session.roleId.toLong()
                val npcClickId = result.parameter
                val npcPlacement = eveData[session.mapId]?.npcs?.get(npcClickId)
                if (npcPlacement == null) {
                    logger.warn { "${session.characterName} resultClass=3 pStyle=1: Không tìm thấy dữ liệu sắp xếp NPC của clickId=$npcClickId" }
                    return false
                }
                val npcId = npcPlacement.npcId
                if (petSystem.getFollowPets(charId).values.any { it.npcId == npcId }) {
                    return true
                }
                val npcDef = npcDefs[npcId]
                if (npcDef == null) {
                    logger.warn { "${session.characterName} resultClass=3 pStyle=1: Không tìm thấy định nghĩa NPC của npcId=$npcId" }
                    return false
                }
                // canGrow được đọc từ roleStatus của việc bố trí NPC
                // canGrow=0: NPC đi cùng (Tên màu cam, không thể xuất chiến/cất giữ thủ công)
                // canGrow=1: Võ tướng có thể trưởng thành (Tên màu xanh, có thể xuất chiến/cất vào khách sạn)
                val canGrow = npcPlacement.canGrow
                val added = petSystem.addFollowNpc(charId, npcId, npcDef, canGrow = canGrow)
                if (added != null) {
                    val (slotIndex, pet) = added
                    responseSender.sendFollowNpcAdd(session, session.playerId, slotIndex, npcId, pet.canGrow, showMsg = true)
                    responseSender.sendFollowNpcFullData(session, petSystem.getFollowPets(charId).values.toList())
                    if (canGrow > 0) collectionSystem?.markWarriorOwned(charId, npcId, session)
                    logger.info { "${session.characterName} Sự kiện NPC tham gia: npcId=$npcId (${npcDef.name}) canGrow=$canGrow → slot=$slotIndex" }
                } else {
                    if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                        petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                            responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                        }
                    }
                    logger.warn { "${session.characterName} Võ tướng đã đầy, không thể thêm npcId=$npcId" }
                    return false
                }
            }
            3, 4, 5 -> logger.debug { "${session.characterName} resultClass=3 pStyle=${result.parameterStyle} (Client xử lý)" }
            else -> logger.debug { "${session.characterName} resultClass=3 Không rõ pStyle=${result.parameterStyle}" }
        }
        return true
    }

    /**
     * Xử lý resultClass=8 (Võ tướng thưởng thêm vào/rời đi)
     */
    private suspend fun handleRewardNpcResult(session: GameSession, result: EveResult): Boolean {
        val charId = session.roleId.toLong()
        val npcId = result.parameter
        when (result.parameterStyle) {
            1 -> {
                val npcDef = npcDefs[npcId]
                if (npcDef == null) {
                    logger.warn { "${session.characterName} resultClass=8 pStyle=1: Không tìm thấy npcId=$npcId" }
                    return false
                }
                val added = petSystem.addFollowNpc(charId, npcId, npcDef, canGrow = 1)
                if (added != null) {
                    val (slotIndex, pet) = added
                    responseSender.sendFollowNpcAdd(session, session.playerId, slotIndex, npcId, pet.canGrow, showMsg = true)
                    responseSender.sendFollowNpcFullData(session, petSystem.getFollowPets(charId).values.toList())
                    collectionSystem?.markWarriorOwned(charId, npcId, session)
                    logger.info { "${session.characterName} Võ tướng thưởng tham gia: npcId=$npcId (${npcDef.name}) → slot=$slotIndex" }
                } else {
                    if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                        petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                            responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                        }
                    }
                    logger.warn { "${session.characterName} Võ tướng đã đầy, không thể thêm võ tướng thưởng npcId=$npcId" }
                    return false
                }
            }
            2 -> {
                val slotIndex = petSystem.removeFollowNpcByNpcId(charId, npcId)
                if (slotIndex >= 0) {
                    responseSender.sendFollowNpcRemove(session, session.playerId, slotIndex, showMsg = true)
                    logger.info { "${session.characterName} Võ tướng rời đi: npcId=$npcId slot=$slotIndex" }
                } else {
                    logger.debug { "${session.characterName} resultClass=8 pStyle=2: Không tìm thấy npcId=$npcId" }
                }
            }
            else -> logger.debug { "${session.characterName} resultClass=8 Không rõ pStyle=${result.parameterStyle}" }
        }
        return true
    }

    /**
     * Xử lý resultClass=4 (Tặng kỹ năng)
     */
    private fun handleSkillResult(session: GameSession, result: EveResult): Boolean {
        val charId = session.roleId.toLong()
        val skillId = result.parameter
        val level = result.resultValue.coerceAtLeast(1)

        val sc = skillSystem.getCache(charId)
        if (sc == null) {
            logger.warn { "${session.characterName} Tặng kỹ năng thất bại: SkillCache không tồn tại" }
            return false
        }

        val currentLevel = sc.skills[skillId] ?: 0
        if (level > currentLevel) {
            sc.skills[skillId] = level
            sc.dirty = true
            responseSender.sendPlayerSkillData(session, sc.element, sc.skillPoint, sc.skills)
            logger.info { "${session.characterName} Sự kiện tặng kỹ năng: skillId=$skillId level=$level" }
        } else {
            logger.debug { "${session.characterName} Đã có kỹ năng này: skillId=$skillId current=$currentLevel ≥ target=$level" }
        }
        return true
    }
}

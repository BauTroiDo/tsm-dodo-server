package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.InnResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.MoneySystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler lữ quán (mainKind=31)
 *
 * C:031-001 Xác nhận nghỉ trọ
 * C:031-002 Nhận tướng
 * C:031-003 Cất tướng
 * C:031-004 Đổi tướng
 * C:031-010 Đóng UI lữ quán (client thông báo)
 */
class InnHandler(
    private val petSystem: PetSystem,
    private val moneySystem: MoneySystem,
    private val responseSender: ResponseSender,
    private val innResponseSender: InnResponseSender,
    private val characterRepository: CharacterRepository,
    private val dispatchSystem: tw.tsm.gamelogic.systems.DispatchSystem? = null,
) : PacketHandler {
    override val mainKind: Int = Opcodes.INN

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleRest(session)
            2 -> handleRetrieve(session, data)
            3 -> handleStore(session, data)
            4 -> handleSwap(session, data)
            10 -> {
                // C:031-010 Đóng UI lữ quán theo người — client thông báo, không cần xử lý
                logger.debug { "${session.characterName} đóng UI quán trọ" }
            }
            else -> logger.warn { "Sub-protocol quán trọ chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:031-001 Xác nhận nghỉ trọ
     *
     * Client gửi sau khi bấm xác nhận nghỉ trọ (gói trống).
     * Khấu trừ tiền → hồi HP toàn đội → gửi animation → gửi kết quả.
     */
    private suspend fun handleRest(session: GameSession) {
        val charId = session.roleId.toLong()
        // Phí nghỉ trọ (tạm dùng giá trị cố định, thực tế nên tính từ cấp lữ quán)
        val cost = 100

        // Khấu trừ tiền
        val (gold, _) = moneySystem.getMoney(charId)
        if (gold < cost) {
            innResponseSender.sendInnRestResult(session, 1)  // Không đủ tiền
            return
        }

        moneySystem.addGold(charId, -cost)
        responseSender.sendAddMoney(session, -cost)

        // Hồi HP + SP toàn đội
        petSystem.restoreAllFollowHpSp(charId)

        // Animation nghỉ ngơi (1=lữ quán)
        innResponseSender.sendInnRestAnimation(session, 1)

        // Nghỉ trọ thành công
        innResponseSender.sendInnRestResult(session, 0)

        // Nghỉ trọ thành công → đặt điểm ghi nhớ tại vị trí hiện tại
        characterRepository.updateRecordPoint(charId, session.mapId, session.x, session.y)

        logger.info { "${session.characterName} nghỉ lại thành công, chi phí=$cost, điểm kiểm tra=map${session.mapId}(${session.x},${session.y})" }
    }

    /**
     * C:031-002 Nhận tướng
     *
     * Định dạng: innSlot(1B)
     */
    private suspend fun handleRetrieve(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val innSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        val followSlot = if (dispatchSystem != null) {
            dispatchSystem.withAvailableInnPet(charId, innSlot) { petSystem.retrieveFromInn(charId, innSlot) }
                ?: run {
                    responseSender.sendSystemMessage(session, "Võ tướng đang Điều phái, chưa thể lấy khỏi lữ quán.")
                    innResponseSender.sendInnRetrieveAnimStop(session)
                    return
                }
        } else petSystem.retrieveFromInn(charId, innSlot)
        if (followSlot < 0) {
            if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                    responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                }
            }
            logger.warn { "${session.characterName} nhận tướng từ quán trọ thất bại: innSlot=$innSlot" }
            return
        }

        // Dừng animation nhận
        innResponseSender.sendInnRetrieveAnimStop(session)
        // Xoá khỏi lữ quán
        innResponseSender.sendInnDeleteNpc(session, innSlot)
        // Gửi xuống dữ liệu tướng theo người mới
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }

        logger.info { "${session.characterName} nhận tướng từ quán trọ: inn=$innSlot → follow=$followSlot" }
    }

    /**
     * C:031-003 Cất tướng
     *
     * Định dạng: followSlot(1B)
     */
    private suspend fun handleStore(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        val innSlot = petSystem.storeToInn(charId, followSlot)
        if (innSlot < 0) {
            logger.warn { "${session.characterName} gửi tướng vào quán trọ thất bại: followSlot=$followSlot" }
            return
        }

        // Dừng animation cất tướng
        innResponseSender.sendInnStoreAnimStop(session)
        // Gói tin xác nhận
        innResponseSender.sendInnStoreConfirm(session, innSlot, followSlot)
        // Gỡ khỏi theo người (S:015-011)
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, false)
        // Gửi bổ sung danh sách tướng lữ quán đầy đủ, đảm bảo UI đồng bộ
        val innPets = petSystem.getInnPets(charId)
        innResponseSender.sendInnNpcData(session, innPets, dispatchSystem?.queryDispatches(charId)?.map { it.innPetIndex }?.toSet().orEmpty())

        logger.info { "${session.characterName} gửi tướng vào quán trọ: follow=$followSlot → inn=$innSlot" }
    }

    /**
     * C:031-004 Đổi tướng
     *
     * Định dạng: innSlot(1B) + followSlot(1B)
     */
    private suspend fun handleSwap(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val innSlot = reader.readUByte()
        val followSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        val swapped = if (dispatchSystem != null) {
            dispatchSystem.withAvailableInnPet(charId, innSlot) { petSystem.swapInnAndFollow(charId, innSlot, followSlot) }
                ?: run {
                    responseSender.sendSystemMessage(session, "Võ tướng đang Điều phái, chưa thể đổi tướng.")
                    innResponseSender.sendInnSwapAnimStop(session)
                    return
                }
        } else petSystem.swapInnAndFollow(charId, innSlot, followSlot)
        if (!swapped) {
            logger.warn { "${session.characterName} trao đổi với quán trọ thất bại: inn=$innSlot follow=$followSlot" }
            return
        }

        // Dừng animation đổi
        innResponseSender.sendInnSwapAnimStop(session)

        // Cập nhật phía lữ quán
        val innPets = petSystem.getInnPets(charId)
        innResponseSender.sendInnNpcData(session, innPets, dispatchSystem?.queryDispatches(charId)?.map { it.innPetIndex }?.toSet().orEmpty())

        // Cập nhật phía theo người
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }

        logger.info { "${session.characterName} trao đổi với quán trọ: inn=$innSlot ↔ follow=$followSlot" }
    }

    /**
     * Mở UI lữ quán (cho hệ thống sự kiện gọi)
     *
     * Gửi dữ liệu tướng lữ quán + cấp lữ quán cho client.
     */
    suspend fun openInn(session: GameSession) {
        val charId = session.roleId.toLong()
        val innPets = petSystem.getInnPets(charId)

        // Cấp lữ quán (tạm dùng 1, sau này có thể đọc từ dữ liệu nhân vật)
        innResponseSender.sendInnLevel(session, 1)
        // Gửi xuống tất cả tướng lữ quán
        innResponseSender.sendInnNpcData(session, innPets, dispatchSystem?.queryDispatches(charId)?.map { it.innPetIndex }?.toSet().orEmpty())
    }
}

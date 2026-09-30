package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.InnResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Handler kho tướng (mainKind=122)
 *
 * C:122-001 truy vấn kho tướng
 * C:122-002 lưu vào kho tướng
 * C:122-003 lấy tướng ra
 */
class WarriorRepositoryHandler(
    private val petSystem: PetSystem,
    private val responseSender: ResponseSender,
    private val innResponseSender: InnResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.WARRIOR_REPO

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleQuery(session)
            2 -> handleStore(session, data)
            3 -> handleRetrieve(session, data)
            else -> logger.warn { "Sub-protocol kho tướng chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:122-001 truy vấn kho tướng
     *
     * gói tin rỗng — client gửi khi mở UI kho tướng
     */
    private fun handleQuery(session: GameSession) {
        val charId = session.roleId.toLong()
        val pets = petSystem.getWarehousePets(charId)
        innResponseSender.sendWarehouseData(session, pets)
        logger.debug { "${session.characterName} tra cứu kho tướng: ${pets.size} vị" }
    }

    /**
     * C:122-002 lưu vào kho tướng
     *
     * Định dạng: followSlot(1B) + npcId(2B LE)
     */
    private fun handleStore(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val npcId = reader.readUShortLE()
        val charId = session.roleId.toLong()

        // kiểm tra hợp lệ npcId khớp với followSlot
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet == null || pet.npcId != npcId) {
            innResponseSender.sendWarehouseStoreResult(session, 255)  // thất bại
            return
        }

        if (!petSystem.storeToWarehouse(charId, followSlot)) {
            // kho đã đầy
            innResponseSender.sendWarehouseStoreResult(session, 2)
            return
        }

        // thành công
        innResponseSender.sendWarehouseStoreResult(session, 1)
        // gỡ khỏi tướng tuỳ thân
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, false)
        // Lua DVT S:122-002 chỉ gọi UpdateUI(), không tự thêm vào Inn.warriorRepository.
        // Phải gửi lại S:122-001 để client rebuild cột "Tướng đã gửi" ngay trong UI đang mở.
        innResponseSender.sendWarehouseData(session, petSystem.getWarehousePets(charId))

        logger.info { "${session.characterName} gửi vào kho tướng: follow=$followSlot npcId=$npcId" }
    }

    /**
     * C:122-003 lấy tướng ra
     *
     * Định dạng: followSlot(1B) + npcId(2B LE)
     * Lưu ý: followSlot là ô đích mà client mong muốn (server tự phân bổ)
     */
    private fun handleRetrieve(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val _followSlot = reader.readUByte()  // client cung cấp, server bỏ qua
        val npcId = reader.readUShortLE()
        val charId = session.roleId.toLong()

        val followSlot = petSystem.retrieveFromWarehouse(charId, npcId)
        if (followSlot < 0) {
            if (!petSystem.hasFreeUnlockedFollowSlot(charId)) {
                petSystem.getNextFollowUnlockLevel(charId)?.let { level ->
                    responseSender.sendSystemMessage(session, "Ô võ tướng tiếp theo mở ở Lv$level.")
                }
            }
            innResponseSender.sendWarehouseRetrieveResult(session, 255)  // thất bại
            return
        }

        // thành công
        innResponseSender.sendWarehouseRetrieveResult(session, 1)
        // gửi xuống tướng tuỳ thân mới
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }
        // Cùng contract với chiều cất: S:122-003 chỉ UpdateUI(), không tự xoá
        // Inn.warriorRepository. Refresh S:122-001 để cột kho không giữ dữ liệu cũ.
        innResponseSender.sendWarehouseData(session, petSystem.getWarehousePets(charId))

        logger.info { "${session.characterName} lấy từ kho tướng: npcId=$npcId → follow=$followSlot" }
    }
}

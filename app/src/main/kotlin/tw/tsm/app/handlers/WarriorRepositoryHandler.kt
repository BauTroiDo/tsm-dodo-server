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
 * 武將倉庫 Handler（mainKind=122）
 *
 * C:122-001 查詢武將倉庫
 * C:122-002 存入武將倉庫
 * C:122-003 取出武將
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
            else -> logger.warn { "未處理的武將倉庫子協定：subKind=$subKind" }
        }
    }

    /**
     * C:122-001 查詢武將倉庫
     *
     * 空包 — 客戶端開啟武將倉庫 UI 時發送
     */
    private fun handleQuery(session: GameSession) {
        val charId = session.roleId.toLong()
        val pets = petSystem.getWarehousePets(charId)
        innResponseSender.sendWarehouseData(session, pets)
        logger.debug { "${session.characterName} 查詢武將倉庫：${pets.size} 隻" }
    }

    /**
     * C:122-002 存入武將倉庫
     *
     * 格式：followSlot(1B) + npcId(2B LE)
     */
    private fun handleStore(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val npcId = reader.readUShortLE()
        val charId = session.roleId.toLong()

        // 驗證 npcId 與 followSlot 一致
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet == null || pet.npcId != npcId) {
            innResponseSender.sendWarehouseStoreResult(session, 255)  // 失敗
            return
        }

        if (!petSystem.storeToWarehouse(charId, followSlot)) {
            // 倉庫已滿
            innResponseSender.sendWarehouseStoreResult(session, 2)
            return
        }

        // 成功
        innResponseSender.sendWarehouseStoreResult(session, 1)
        // 從隨身移除
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, false)

        logger.info { "${session.characterName} 存入武將倉庫：follow=$followSlot npcId=$npcId" }
    }

    /**
     * C:122-003 取出武將
     *
     * 格式：followSlot(1B) + npcId(2B LE)
     * 注意：followSlot 為客戶端期望的目標槽位（伺服器自行分配）
     */
    private fun handleRetrieve(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val _followSlot = reader.readUByte()  // 客戶端提供，伺服器忽略
        val npcId = reader.readUShortLE()
        val charId = session.roleId.toLong()

        val followSlot = petSystem.retrieveFromWarehouse(charId, npcId)
        if (followSlot < 0) {
            innResponseSender.sendWarehouseRetrieveResult(session, 255)  // 失敗
            return
        }

        // 成功
        innResponseSender.sendWarehouseRetrieveResult(session, 1)
        // 下發新隨身武將
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }

        logger.info { "${session.characterName} 取出武將倉庫：npcId=$npcId → follow=$followSlot" }
    }
}

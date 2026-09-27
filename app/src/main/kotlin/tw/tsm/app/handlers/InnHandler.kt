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
 * 客棧 Handler（mainKind=31）
 *
 * C:031-001 住宿確認
 * C:031-002 領武將
 * C:031-003 存武將
 * C:031-004 交換武將
 * C:031-010 關閉客棧 UI（客戶端通知）
 */
class InnHandler(
    private val petSystem: PetSystem,
    private val moneySystem: MoneySystem,
    private val responseSender: ResponseSender,
    private val innResponseSender: InnResponseSender,
    private val characterRepository: CharacterRepository
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
                // C:031-010 關閉隨身客棧 UI — 客戶端通知，無需處理
                logger.debug { "${session.characterName} 關閉客棧 UI" }
            }
            else -> logger.warn { "未處理的客棧子協定：subKind=$subKind" }
        }
    }

    /**
     * C:031-001 住宿確認
     *
     * 客戶端點擊確認住宿後發送（空包）。
     * 扣金錢 → 回復全隊 HP → 送動畫 → 送結果。
     */
    private suspend fun handleRest(session: GameSession) {
        val charId = session.roleId.toLong()
        // 住宿費用（暫用固定值，實際應從客棧等級計算）
        val cost = 100

        // 扣金錢
        val (gold, _) = moneySystem.getMoney(charId)
        if (gold < cost) {
            innResponseSender.sendInnRestResult(session, 1)  // 金錢不足
            return
        }

        moneySystem.addGold(charId, -cost)
        responseSender.sendAddMoney(session, -cost)

        // 回復全隊 HP + SP
        petSystem.restoreAllFollowHpSp(charId)

        // 休息動畫（1=客棧）
        innResponseSender.sendInnRestAnimation(session, 1)

        // 住宿成功
        innResponseSender.sendInnRestResult(session, 0)

        // 住宿成功 → 設定記錄點為當前位置
        characterRepository.updateRecordPoint(charId, session.mapId, session.x, session.y)

        logger.info { "${session.characterName} 住宿成功，費用=$cost，記錄點=map${session.mapId}(${session.x},${session.y})" }
    }

    /**
     * C:031-002 領武將
     *
     * 格式：innSlot(1B)
     */
    private fun handleRetrieve(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val innSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        val followSlot = petSystem.retrieveFromInn(charId, innSlot)
        if (followSlot < 0) {
            logger.warn { "${session.characterName} 客棧領武將失敗：innSlot=$innSlot" }
            return
        }

        // 停止領取動畫
        innResponseSender.sendInnRetrieveAnimStop(session)
        // 從客棧刪除
        innResponseSender.sendInnDeleteNpc(session, innSlot)
        // 下發新隨身武將資料
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }

        logger.info { "${session.characterName} 客棧領武將：inn=$innSlot → follow=$followSlot" }
    }

    /**
     * C:031-003 存武將
     *
     * 格式：followSlot(1B)
     */
    private fun handleStore(session: GameSession, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val followSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        val innSlot = petSystem.storeToInn(charId, followSlot)
        if (innSlot < 0) {
            logger.warn { "${session.characterName} 客棧存武將失敗：followSlot=$followSlot" }
            return
        }

        // 停止存武將動畫
        innResponseSender.sendInnStoreAnimStop(session)
        // 確認封包
        innResponseSender.sendInnStoreConfirm(session, innSlot, followSlot)
        // 從隨身移除（S:015-011）
        responseSender.sendFollowNpcRemove(session, session.playerId, followSlot, false)
        // 補發完整客棧武將列表，確保 UI 同步
        val innPets = petSystem.getInnPets(charId)
        innResponseSender.sendInnNpcData(session, innPets)

        logger.info { "${session.characterName} 客棧存武將：follow=$followSlot → inn=$innSlot" }
    }

    /**
     * C:031-004 交換武將
     *
     * 格式：innSlot(1B) + followSlot(1B)
     */
    private fun handleSwap(session: GameSession, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val innSlot = reader.readUByte()
        val followSlot = reader.readUByte()
        val charId = session.roleId.toLong()

        if (!petSystem.swapInnAndFollow(charId, innSlot, followSlot)) {
            logger.warn { "${session.characterName} 客棧交換失敗：inn=$innSlot follow=$followSlot" }
            return
        }

        // 停止交換動畫
        innResponseSender.sendInnSwapAnimStop(session)

        // 更新客棧端
        val innPets = petSystem.getInnPets(charId)
        innResponseSender.sendInnNpcData(session, innPets)

        // 更新隨身端
        val pet = petSystem.getFollowNpc(charId, followSlot)
        if (pet != null) {
            responseSender.sendFollowNpcFullData(session, listOf(pet))
        }

        logger.info { "${session.characterName} 客棧交換：inn=$innSlot ↔ follow=$followSlot" }
    }

    /**
     * 開啟客棧 UI（供事件系統呼叫）
     *
     * 發送客棧武將資料 + 客棧等級給客戶端。
     */
    fun openInn(session: GameSession) {
        val charId = session.roleId.toLong()
        val innPets = petSystem.getInnPets(charId)

        // 客棧等級（暫用 1，後續可從角色資料讀取）
        innResponseSender.sendInnLevel(session, 1)
        // 下發所有客棧武將
        innResponseSender.sendInnNpcData(session, innPets)
    }
}

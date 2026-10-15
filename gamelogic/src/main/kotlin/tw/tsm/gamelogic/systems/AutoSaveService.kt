package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.tsm.domain.interfaces.repositories.CharacterRepository

private val logger = KotlinLogging.logger {}

/**
 * Dịch vụ tự động lưu định kỳ
 *
 * Tự động lưu trạng thái runtime của tất cả người chơi đang online vào DB mỗi [INTERVAL_MS] mili-giây,
 * để ngăn ngừa việc mất dữ liệu khi máy chủ khởi động lại hoặc dừng bất thường.
 *
 * Nội dung lưu: Vị trí, trạng thái nhân vật (Cấp độ/EXP/Lục vi [6 thuộc tính cơ bản]), vật phẩm, tiền, kỹ năng, võ tướng, thú cưỡi.
 */
class AutoSaveService(
    scope: CoroutineScope,
    private val worldManager: WorldManager,
    private val characterRepository: CharacterRepository,
    private val playerStateManager: PlayerStateManager,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val skillSystem: SkillSystem,
    private val petSystem: PetSystem,
    private val mountSystem: MountSystem,
    private val roleCountSystem: RoleCountSystem,
    private val questSystem: QuestSystem? = null,
    private val responseSender: tw.tsm.domain.interfaces.ResponseSender? = null,
    private val houseVisits: HouseVisitSystem? = null,
    private val miningSystem: MiningSystem? = null,
) {
    companion object {
        /** Khoảng thời gian tự động lưu. 30 giây để giới hạn mất dữ liệu khi tiến trình/máy chủ bị reset đột ngột. */
        const val INTERVAL_MS = 30L * 1000
    }

    init {
        scope.launch {
            // Trì hoãn một khoảng thời gian trước khi bắt đầu lần lưu đầu tiên, tránh việc thực thi ngay khi máy chủ vừa khởi động
            delay(INTERVAL_MS)
            while (isActive) {
                try {
                    saveAllNow()
                } catch (e: Exception) {
                    logger.error(e) { "Lỗi tự động lưu" }
                }
                delay(INTERVAL_MS)
            }
        }
        logger.info { "Dịch vụ tự động lưu đã khởi động (mỗi ${INTERVAL_MS / 1000} giây)" }
    }

    /**
     * Lưu dữ liệu của tất cả người chơi đang online
     */
    suspend fun saveAllNow() {
        val sessions = worldManager.getOnlineSessions()
        if (sessions.isEmpty()) return

        val start = System.currentTimeMillis()
        var count = 0

        for (session in sessions) {
            val charId = session.roleId.toLong()
            if (charId <= 0) continue

            try {
                for (missionId in questSystem?.refreshDungeonDaily(charId).orEmpty()) {
                    responseSender?.sendRemoveMission(session, missionId)
                }
                // Vị trí
                val stable = houseVisits?.persistedLocation(session)
                val saveMap = stable?.mapId ?: session.mapId
                if (saveMap > 0) {
                    characterRepository.updatePosition(charId, saveMap,
                        stable?.x ?: session.x, stable?.y ?: session.y)
                }

                // Trạng thái nhân vật (Cấp độ/EXP/Điểm thuộc tính/Điểm kỹ năng/Lục vi)
                val state = playerStateManager.getState(charId)
                if (state != null) {
                    characterRepository.updatePlayerState(
                        charId, state.exp, state.level,
                        state.attrPoint, state.skillPoint,
                        state.intelligence, state.atk, state.def, state.agi, state.hpx, state.spx
                    )
                }

                // Vật phẩm/Tiền/Nguyên bảo/Kỹ năng/Võ tướng/Thú cưỡi
                // PRIVATE DVT mine economy: every autosave tick settles online miners.
                // TS wallet S035-050 is pushed only after the durable SQL award.
                val mineView = miningSystem?.getView(charId)
                mineView?.tsBalanceUpdate?.let { balance ->
                    responseSender?.sendPlayerCurrency(session, 8, balance)
                }
                inventorySystem.save(charId)
                moneySystem.saveMoney(charId)
                pointSystem.savePoints(charId)
                skillSystem.save(charId)
                // fightNpcId nằm ở bảng characters, không nằm trong petRepository.
                // Checkpoint riêng để crash/mất kết nối vẫn giữ đúng tướng đang xuất chiến.
                val fightNpcId = petSystem.getCache(charId)?.fightNpcId ?: 0
                characterRepository.updateFightNpcId(charId, fightNpcId)
                petSystem.save(charId)
                mountSystem.save(charId)
                roleCountSystem.saveCounts(charId)
                count++
            } catch (e: Exception) {
                logger.error(e) { "Tự động lưu thất bại: charId=$charId" }
            }
        }

        val elapsed = System.currentTimeMillis() - start
        logger.info { "Tự động lưu hoàn tất: $count người chơi (${elapsed}ms)" }
        if (elapsed > 1000) logger.warn { "Tự động lưu quá chậm (${elapsed}ms), hãy kiểm tra nút thắt cổ chai (bottleneck)" }
    }
}
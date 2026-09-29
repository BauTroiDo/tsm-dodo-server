package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.*

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý mất kết nối (mainKind = 0)
 *
 * Nguồn: protocal.lua protocolTable[0]
 * C→S 0x00-0x00: client thông báo server sắp mất kết nối
 */
class DisconnectHandler(
    private val worldManager: WorldManager,
    private val inventorySystem: InventorySystem,
    private val moneySystem: MoneySystem,
    private val pointSystem: PointSystem,
    private val playerCurrencySystem: PlayerCurrencySystem,
    private val characterRepository: CharacterRepository,
    private val skillSystem: SkillSystem,
    private val petSystem: PetSystem,
    private val tradeSystem: TradeSystem,
    private val battleManager: BattleManager,
    private val partySystem: PartySystem,
    private val playerStateManager: PlayerStateManager,
    private val eveStateMachine: EveEventStateMachine,
    private val mountSystem: MountSystem,
    private val macroBoxSystem: MacroBoxSystem,
    private val roleCountSystem: RoleCountSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.DISCONNECT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        val characterId = session.roleId.toLong()
        logger.info { "Nhận thông báo ngắt kết nối: ${session.characterName} (roleId=$characterId)" }

        // Dọn dẹp trạng thái trò chơi (giao dịch/chiến đấu/tổ đội/sự kiện/hộp cơ quan)
        tradeSystem.cancelTradeOnDisconnect(session.playerId)
        battleManager.handleDisconnect(session.playerId)
        partySystem.handleDisconnect(session.playerId)
        eveStateMachine.clear(characterId)
        macroBoxSystem.cleanup(characterId)

        // Chụp snapshot vị trí trước, rồi loại bỏ (leaveMap sẽ đặt session.mapId về 0)
        val mapId = session.mapId
        val x = session.x
        val y = session.y

        // Gỡ người chơi khỏi thế giới
        worldManager.removePlayer(session)

        // Lưu dữ liệu nhân vật vào DB (mỗi mục try-catch riêng, đảm bảo một mục lỗi không ảnh hưởng việc lưu các mục khác)
        if (characterId > 0) {
            try { characterRepository.updatePosition(characterId, mapId, x, y) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (vị trí): charId=$characterId" } }

            // Trạng thái runtime đầy đủ (exp/level/attrPoint/skillPoint/6 chỉ số)
            try {
                val state = playerStateManager.getState(characterId)
                if (state != null) {
                    characterRepository.updatePlayerState(
                        characterId, state.exp, state.level,
                        state.attrPoint, state.skillPoint,
                        state.intelligence, state.atk, state.def, state.agi, state.hpx, state.spx
                    )
                }
            }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (trạng thái nhân vật): charId=$characterId" } }

            // Xoá trạng thái runtime (sau khi đọc state)
            playerStateManager.removePlayer(characterId)

            try { inventorySystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (vật phẩm): charId=$characterId" } }

            try { moneySystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (tiền vàng): charId=$characterId" } }

            try { pointSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (nguyên bảo): charId=$characterId" } }

            // DVT currencies 4..16 are persisted on every mutation; disconnect only drops cache.
            playerCurrencySystem.unload(characterId)

            try { skillSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (kỹ năng): charId=$characterId" } }

            try {
                val fightNpcId = petSystem.getCache(characterId)?.fightNpcId ?: 0
                characterRepository.updateFightNpcId(characterId, fightNpcId)
            }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (tướng xuất chiến): charId=$characterId" } }

            try { petSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (tướng): charId=$characterId" } }

            try { mountSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (vật cưỡi): charId=$characterId" } }

            try { roleCountSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "Lưu ngắt kết nối thất bại (bộ đếm): charId=$characterId" } }
        }

        // Đóng kết nối
        session.close()
    }
}

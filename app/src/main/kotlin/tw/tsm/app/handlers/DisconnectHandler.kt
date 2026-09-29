package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.*

private val logger = KotlinLogging.logger {}

/**
 * 斷線處理器（mainKind = 0）
 *
 * 來源：protocal.lua protocolTable[0]
 * C→S 0x00-0x00：客戶端通知伺服器即將斷線
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
        logger.info { "收到斷線通知：${session.characterName} (roleId=$characterId)" }

        // 清理遊戲狀態（交易/戰鬥/隊伍/事件/機關盒）
        tradeSystem.cancelTradeOnDisconnect(session.playerId)
        battleManager.handleDisconnect(session.playerId)
        partySystem.handleDisconnect(session.playerId)
        eveStateMachine.clear(characterId)
        macroBoxSystem.cleanup(characterId)

        // 先快照位置，再移除（leaveMap 會把 session.mapId 清為 0）
        val mapId = session.mapId
        val x = session.x
        val y = session.y

        // 從世界移除玩家
        worldManager.removePlayer(session)

        // 儲存角色資料到 DB（每項獨立 try-catch，確保一項失敗不影響其他項保存）
        if (characterId > 0) {
            try { characterRepository.updatePosition(characterId, mapId, x, y) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（位置）：charId=$characterId" } }

            // 完整運行時狀態（exp/level/attrPoint/skillPoint/六圍）
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
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（角色狀態）：charId=$characterId" } }

            // 清除運行時狀態（在讀取 state 之後）
            playerStateManager.removePlayer(characterId)

            try { inventorySystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（物品）：charId=$characterId" } }

            try { moneySystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（金錢）：charId=$characterId" } }

            try { pointSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（元寶）：charId=$characterId" } }

            // DVT currencies 4..16 are persisted on every mutation; disconnect only drops cache.
            playerCurrencySystem.unload(characterId)

            try { skillSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（技能）：charId=$characterId" } }

            try {
                val fightNpcId = petSystem.getCache(characterId)?.fightNpcId ?: 0
                characterRepository.updateFightNpcId(characterId, fightNpcId)
            }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（出戰武將）：charId=$characterId" } }

            try { petSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（武將）：charId=$characterId" } }

            try { mountSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（坐騎）：charId=$characterId" } }

            try { roleCountSystem.saveAndUnload(characterId) }
            catch (e: Exception) { logger.error(e) { "斷線存檔失敗（計數器）：charId=$characterId" } }
        }

        // 關閉連線
        session.close()
    }
}

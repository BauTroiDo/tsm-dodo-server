package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 技能升級處理器（mainKind = 28）
 *
 * C:028-001 玩家技能升級：<<+skillId(2LE) +level(1)>>
 * C:028-002 武將技能升級：+followIndex(1) +skillId(2LE) +level(1) （暫不實作）
 */
class SkillUpgradeHandler(
    private val skillSystem: SkillSystem,
    private val responseSender: ResponseSender,
    private val playerStateManager: PlayerStateManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.SKILL_UPGRADE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        when (subKind) {
            1 -> handlePlayerSkillUpgrade(session, characterId, data)
            2 -> {
                // TODO：武將技能升級（等武將系統完成）
                logger.trace { "武將技能升級 暫不處理" }
            }
            else -> logger.warn { "未處理的技能升級 subKind=$subKind" }
        }
    }

    /**
     * C:028-001 玩家技能升級
     *
     * 封包格式：<<+skillId(2LE) +level(1)>>
     * 可一次升多個技能（迴圈讀到結束）
     */
    private suspend fun handlePlayerSkillUpgrade(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.isEmpty()) return

        val reader = PacketReader(data)
        val upgrades = mutableListOf<Pair<Int, Int>>()

        while (reader.remaining >= 3) {
            val skillId = reader.readUShortLE()
            val level = reader.readUByte()
            upgrades.add(skillId to level)
        }

        if (upgrades.isEmpty()) return

        logger.info { "${session.characterName} 請求升級技能：$upgrades" }

        val results = skillSystem.upgradeSkills(characterId, upgrades)
        // Luon luon gui lai toan bo du lieu ky nang de dong bo chinh xac cho client
        val sc = skillSystem.getCache(characterId)
        if (sc != null) {
            playerStateManager.getState(characterId)?.let { state ->
                synchronized(state) {
                    state.skillPoint = sc.skillPoint
                }
            }
            responseSender.sendPlayerSkillData(session, sc.element, sc.skillPoint, sc.skills)
        }
    }
}

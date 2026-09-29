package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.gamelogic.systems.PetSkillSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý nâng cấp kỹ năng (mainKind = 28)
 *
 * C:028-001 Nâng cấp kỹ năng người chơi: <<+skillId(2LE) +level(1)>>
 * C:028-002 Nâng cấp kỹ năng tướng: +followIndex(1) +<<skillId(2LE) +level(1)>>
 */
class SkillUpgradeHandler(
    private val skillSystem: SkillSystem,
    private val responseSender: ResponseSender,
    private val playerStateManager: PlayerStateManager,
    private val petSkillSystem: PetSkillSystem,
    private val dailyProgressSystem: DailyProgressSystem? = null,
) : PacketHandler {

    override val mainKind: Int = Opcodes.SKILL_UPGRADE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        when (subKind) {
            1 -> handlePlayerSkillUpgrade(session, characterId, data)
            2 -> handlePetSkillUpgrade(session, characterId, data)
            5 -> handleTurn2SkillUpgrade(session, characterId, data)
            else -> logger.warn { "Nâng cấp kỹ năng chưa xử lý subKind=$subKind" }
        }
    }

    private fun handleTurn2SkillUpgrade(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 12) return
        val reader = PacketReader(data)

        val ballCount = reader.readUIntLE()
        if (ballCount > 12 || reader.remaining < ballCount.toInt() + 8) return
        val balls = MutableList(ballCount.toInt()) { reader.readUByte() }

        if (reader.remaining < 4) return
        val skillCount = reader.readUIntLE()
        if (skillCount > 64 || reader.remaining < skillCount.toInt() * 3 + 4) return
        val upgrades = MutableList(skillCount.toInt()) { reader.readUShortLE() to reader.readUByte() }

        if (reader.remaining < 4) return
        val slotCount = reader.readUIntLE()
        if (slotCount > 8 || reader.remaining != slotCount.toInt() * 3) return
        val heroSlots = MutableList(slotCount.toInt()) { reader.readUByte() to reader.readUShortLE() }

        val state = playerStateManager.getState(characterId) ?: return
        val beforeSkillPoint = skillSystem.getCache(characterId)?.skillPoint ?: state.skillPoint
        val ok = skillSystem.upgradeTurn2(
            characterId = characterId,
            addBalls = balls,
            upgrades = upgrades,
            heroSlots = heroSlots,
            playerTurn = state.turn,
            career = state.career
        )
        val sc = skillSystem.getCache(characterId) ?: return
        synchronized(state) { state.skillPoint = sc.skillPoint }
        val spentSkillPoint = (beforeSkillPoint - sc.skillPoint).coerceAtLeast(0)
        if (spentSkillPoint > 0) {
            dailyProgressSystem?.incrementRoleCount(
                session,
                DailyProgressSystem.SKILL_POINT,
                spentSkillPoint,
            )
        }

        // Dù thành công hay bị từ chối đều trả về snapshot uy quyền, tránh client giữ lại trạng thái tạm cục bộ.
        responseSender.sendPlayerSkillData(session, sc.element, sc.skillPoint, sc.skills)
        responseSender.sendTurn2Balls(session, sc.availableTurn2Balls, sc.activeTurn2Balls)
        responseSender.sendTurn2HeroSkills(session, sc.heroSkills)
        logger.info { "${session.characterName} C28:5 Turn2 ok=$ok balls=$balls skills=$upgrades hero=$heroSlots" }
    }

    private fun handlePetSkillUpgrade(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size !in 4..10 || (data.size - 1) % 3 != 0) return
        val reader = PacketReader(data)
        val slot = reader.readUByte()
        if (slot !in 1..4) return
        val upgrades = mutableListOf<Pair<Int, Int>>()
        while (reader.remaining > 0) upgrades += reader.readUShortLE() to reader.readUByte()
        val snapshot = petSkillSystem.upgrade(characterId, slot, upgrades) ?: return
        // protocal.lua[8][2] -> Role.SetAttribute(FollowNpc, slot, ...).
        // Send authoritative values even after a rejected/duplicate request.
        for ((id, level) in snapshot.skills) {
            responseSender.sendFollowNpcAttribute(session, slot, 201, level, id)
        }
        responseSender.sendFollowNpcAttribute(session, slot, 37, snapshot.pet.skillPoint)
    }

    /**
     * C:028-001 Nâng cấp kỹ năng người chơi
     *
     * Định dạng gói tin: <<+skillId(2LE) +level(1)>>
     * Có thể nâng nhiều kỹ năng cùng lúc (vòng lặp đọc đến hết)
     */
    private suspend fun handlePlayerSkillUpgrade(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.isEmpty() || data.size % 3 != 0) return

        val reader = PacketReader(data)
        val upgrades = mutableListOf<Pair<Int, Int>>()

        while (reader.remaining >= 3) {
            val skillId = reader.readUShortLE()
            val level = reader.readUByte()
            upgrades.add(skillId to level)
        }

        if (upgrades.isEmpty()) return

        logger.info { "${session.characterName} yêu cầu nâng cấp kỹ năng: $upgrades" }

        val state = playerStateManager.getState(characterId) ?: return
        val playerTurn = state.turn
        val beforeSkillPoint = skillSystem.getCache(characterId)?.skillPoint ?: state.skillPoint
        skillSystem.upgradeSkills(characterId, upgrades, playerTurn)
        // Luon luon gui lai toan bo du lieu ky nang de dong bo chinh xac cho client
        val sc = skillSystem.getCache(characterId)
        if (sc != null) {
            playerStateManager.getState(characterId)?.let { state ->
                synchronized(state) {
                    state.skillPoint = sc.skillPoint
                }
            }
            responseSender.sendPlayerSkillData(session, sc.element, sc.skillPoint, sc.skills)
            val spentSkillPoint = (beforeSkillPoint - sc.skillPoint).coerceAtLeast(0)
            if (spentSkillPoint > 0) {
                dailyProgressSystem?.incrementRoleCount(
                    session,
                    DailyProgressSystem.SKILL_POINT,
                    spentSkillPoint,
                )
            }
        }
    }
}
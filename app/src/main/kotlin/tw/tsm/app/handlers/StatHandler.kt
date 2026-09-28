package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.SkillRepository
import tw.tsm.gamelogic.systems.DailyProgressSystem
import tw.tsm.gamelogic.systems.PlayerStateManager
import tw.tsm.gamelogic.systems.SkillSystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 屬性配點處理器（mainKind = 8）
 *
 * 子 opcode：
 * - C:008-001 屬性配點（Status.lua → 加點按鈕）
 *
 * 📖 協定來源：protocal.lua protocolTable[8][1]
 * 📖 封包格式：+charIndex(2) +attrKind(1) +addPoints(4) +reserved(1)
 */
class StatHandler(
    private val playerStateManager: PlayerStateManager,
    private val characterRepository: CharacterRepository,
    private val responseSender: ResponseSender,
    private val statCalc: StatCalculator,
    private val skillSystem: SkillSystem,
    private val skillRepository: SkillRepository,
    private val dailyProgressSystem: DailyProgressSystem? = null,
) : PacketHandler {

    override val mainKind: Int = Opcodes.BATTLE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            1 -> handleAllocateStat(session, data)
            12 -> handleTurn3Element(session, data)
            13 -> handleRequestSkillData(session)
            else -> logger.trace { "未處理的 C:008-$subKind" }
        }
    }

    /** C:008-012 第一次選擇三轉光/暗元素：payload element(Byte). */
    private suspend fun handleTurn3Element(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME || data.size != 1) return
        val charId = session.roleId.toLong()
        val player = playerStateManager.getState(charId) ?: return
        val element = data[0].toInt() and 0xFF
        // UISkillTree only exposes Light/Dark. Element enum values are grounded by skill data/UI as 7/8.
        if (element !in 7..8 || player.turn < 3) {
            responseSender.sendTurn3ElementResult(session, 1)
            return
        }
        val current = skillRepository.findTurn3State(charId)
        if (current.element != 0) {
            responseSender.sendTurn3ElementResult(session, 1)
            return
        }
        skillRepository.saveTurn3State(charId, current.copy(element = element))
        session.turn3Element = element
        responseSender.sendSetAttribute(session, EAttribute.TURN3_ELEMENT, element, true)
        responseSender.sendTurn3ElementResult(session, 0)
        logger.info { "${session.characterName} selected Turn3 element=$element" }
    }

    private fun handleRequestSkillData(session: GameSession) {
        val charId = session.roleId.toLong()
        val sc = skillSystem.getCache(charId)
        if (sc != null) {
            responseSender.sendPlayerSkillData(session, sc.element, sc.skillPoint, sc.skills)
        }
    }

    private suspend fun handleAllocateTurn3Stat(
        session: GameSession,
        charId: Long,
        state: tw.tsm.gamelogic.systems.PlayerState,
        attrKind: Int,
        addPoints: Int
    ) {
        val turn3Kind = when (attrKind) {
            EAttribute.INT -> EAttribute.TURN3_INT
            EAttribute.ATK -> EAttribute.TURN3_ATK
            EAttribute.DEF -> EAttribute.TURN3_DEF
            EAttribute.AGI -> EAttribute.TURN3_AGI
            EAttribute.HPX -> EAttribute.TURN3_HPX
            EAttribute.SPX -> EAttribute.TURN3_SPX
            else -> return
        }
        val current = skillRepository.findTurn3State(charId)
        val old = when (attrKind) {
            EAttribute.INT -> current.intelligence
            EAttribute.ATK -> current.atk
            EAttribute.DEF -> current.def
            EAttribute.AGI -> current.agi
            EAttribute.HPX -> current.hpx
            EAttribute.SPX -> current.spx
            else -> 0
        }
        synchronized(state) {
            if (state.attrPoint < addPoints || old + addPoints > 999) return
            state.attrPoint -= addPoints
        }
        val updated = when (attrKind) {
            EAttribute.INT -> current.copy(intelligence = old + addPoints)
            EAttribute.ATK -> current.copy(atk = old + addPoints)
            EAttribute.DEF -> current.copy(def = old + addPoints)
            EAttribute.AGI -> current.copy(agi = old + addPoints)
            EAttribute.HPX -> current.copy(hpx = old + addPoints)
            EAttribute.SPX -> current.copy(spx = old + addPoints)
            else -> current
        }
        skillRepository.saveTurn3State(charId, updated)
        synchronized(state) {
            when (attrKind) {
                EAttribute.INT -> state.turn3Int = updated.intelligence
                EAttribute.ATK -> state.turn3Atk = updated.atk
                EAttribute.DEF -> state.turn3Def = updated.def
                EAttribute.AGI -> state.turn3Agi = updated.agi
                EAttribute.HPX -> state.turn3Hpx = updated.hpx
                EAttribute.SPX -> state.turn3Spx = updated.spx
            }
        }
        characterRepository.updateAttrPoint(charId, state.attrPoint)
        dailyProgressSystem?.incrementRoleCount(
            session,
            DailyProgressSystem.ATTRIBUTE_POINT,
            addPoints,
        )
        responseSender.sendSetAttribute(session, turn3Kind, old + addPoints, true)
        responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, state.attrPoint, true)

        val turn3Lv = (state.level - 200).coerceAtLeast(0)
        if (attrKind == EAttribute.HPX) {
            val newMax = statCalc.calculateMaxHp(
                level = 200, hpx = state.hpx + state.equipHpx + turn3Lv.coerceAtMost(70) + updated.hpx,
                equipMaxHp = state.equipMaxHp, extraHp = 0,
                turn = state.turn, career = state.career, turn3Lv = turn3Lv
            )
            playerStateManager.updateMaxHp(charId, newMax)?.let { (maxHp, hp) ->
                responseSender.sendSetAttribute(session, EAttribute.MAX_HP, maxHp.toInt(), true)
                responseSender.sendSetAttribute(session, EAttribute.HP, hp.toInt(), true)
            }
        } else if (attrKind == EAttribute.SPX) {
            val newMax = statCalc.calculateMaxSp(
                level = 200, spx = state.spx + state.equipSpx + turn3Lv + updated.spx,
                equipMaxSp = state.equipMaxSp, extraSp = 0,
                turn = state.turn, career = state.career, turn3Lv = turn3Lv
            )
            playerStateManager.updateMaxSp(charId, newMax)?.let { (maxSp, sp) ->
                responseSender.sendSetAttribute(session, EAttribute.MAX_SP, maxSp, true)
                responseSender.sendSetAttribute(session, EAttribute.SP, sp, true)
            }
        }
    }

    /**
     * 處理屬性配點 (C:008-001)
     *
     * 封包格式：+charIndex(2 UInt16 LE) +attrKind(1) +addPoints(4 Int32 LE) +reserved(1)
     * attrKind: 27=Int, 28=Atk, 29=Def, 30=Agi, 31=Hpx, 32=Spx
     */
    private suspend fun handleAllocateStat(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) {
            logger.warn { "配點：未進入遊戲 state=${session.state}" }
            return
        }

        val reader = PacketReader(data)
        val charIndex = reader.readUShortLE()  // 角色索引（目前只有單角色，忽略）
        val attrKind = reader.readUByte()
        val addPoints = reader.readIntLE()
        if (reader.isReadable) reader.readUByte()  // reserved

        if (addPoints <= 0) return

        val charId = session.roleId.toLong()

        logger.debug { "C:008-001 配點：charId=$charId attrKind=$attrKind addPoints=$addPoints" }

        val currentState = playerStateManager.getState(charId) ?: return
        if (currentState.turn >= 3) {
            handleAllocateTurn3Stat(session, charId, currentState, attrKind, addPoints)
            return
        }

        // PlayerStateManager 處理驗證 + 更新記憶體狀態
        val result = playerStateManager.allocateStat(charId, attrKind, addPoints)
        if (result == null) {
            logger.warn { "配點被拒絕：charId=$charId attrKind=$attrKind addPoints=$addPoints" }
            return
        }

        // 持久化到 DB
        characterRepository.addStatPoint(charId, result.dbColumn, addPoints, result.newAttrPoint)
        dailyProgressSystem?.incrementRoleCount(
            session,
            DailyProgressSystem.ATTRIBUTE_POINT,
            addPoints,
        )

        // S:008-001 回應 — 屬性新值
        responseSender.sendSetAttribute(session, attrKind, result.newValue, isPositive = true)

        // S:008-001 回應 — 剩餘屬性點數
        responseSender.sendSetAttribute(session, EAttribute.ATTR_POINT, result.newAttrPoint, isPositive = true)

        // 如果配的是 HPX 或 SPX，需要重算 MaxHP/MaxSP（透過 PlayerStateManager 保護 synchronized）
        val state = playerStateManager.getState(charId)
        if (state != null && (attrKind == EAttribute.HPX || attrKind == EAttribute.SPX)) {
            if (attrKind == EAttribute.HPX) {
                val newMaxHp = statCalc.calculateMaxHp(
                    level = state.level, hpx = state.hpx,
                    equipMaxHp = state.equipMaxHp, extraHp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                val (maxHp, hp) = playerStateManager.updateMaxHp(charId, newMaxHp) ?: return
                responseSender.sendSetAttribute(session, EAttribute.MAX_HP, maxHp.toInt(), isPositive = true)
                responseSender.sendSetAttribute(session, EAttribute.HP, hp.toInt(), isPositive = true)
            } else {
                val newMaxSp = statCalc.calculateMaxSp(
                    level = state.level, spx = state.spx,
                    equipMaxSp = state.equipMaxSp, extraSp = 0,
                    turn = state.turn, career = state.career, turn3Lv = 0
                )
                val (maxSp, sp) = playerStateManager.updateMaxSp(charId, newMaxSp) ?: return
                responseSender.sendSetAttribute(session, EAttribute.MAX_SP, maxSp, isPositive = true)
                responseSender.sendSetAttribute(session, EAttribute.SP, sp, isPositive = true)
            }
        }
    }
}

package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.battle.BattleAction
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * 戰鬥技能指令處理器（mainKind = 50）
 *
 * 來源：protocal.lua protocolTable[50]
 *
 * C→S 格式：
 * - subKind 1（使用特技）：+srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +skillId(2LE) +chk1(1) +chk2(1)
 * - subKind 2（使用道具）：+srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +itemId(2LE) +chk1(1) +chk2(1)
 */
class BattleCommandHandler(
    private val battleManager: BattleManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.BATTLE_COMMAND

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        if (!battleManager.isInBattle(session.playerId)) {
            logger.warn { "${session.characterName} 不在戰鬥中卻發送戰鬥指令 subKind=$subKind" }
            return
        }

        when (subKind) {
            1 -> handleUseSkill(session, data)
            2 -> handleUseItem(session, data)
            else -> logger.warn { "未處理的戰鬥指令子協定：subKind=$subKind" }
        }
    }

    /**
     * C:050-001 使用特技
     *
     * 格式：+srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +skillId(2LE) +chk1(1) +chk2(1)
     */
    private fun handleUseSkill(session: GameSession, data: ByteArray) {
        if (data.size < 8) return

        val reader = PacketReader(data)
        val srcCol = reader.readUByte()       // 發招者列
        val srcRow = reader.readUByte()       // 發招者行
        val tgtCol = reader.readUByte()       // 目標列
        val tgtRow = reader.readUByte()       // 目標行
        val skillId = reader.readUShortLE()   // 技能 ID
        // chk1, chk2 為校驗碼，忽略

        // 判斷行動種類
        val actionKind = when {
            skillId in BattleConstants.DEFEND_SKILL_IDS -> {
                logger.debug { "${session.characterName} 戰鬥防禦" }
                BattleAction.KIND_DEFEND
            }
            skillId in BattleConstants.ESCAPE_SKILL_IDS -> {
                logger.debug { "${session.characterName} 戰鬥逃跑：skillId=$skillId" }
                BattleAction.KIND_ESCAPE
            }
            skillId in BattleConstants.CATCH_SKILL_IDS -> {
                logger.debug { "${session.characterName} 戰鬥捕捉：skillId=$skillId 目標($tgtCol,$tgtRow)" }
                BattleAction.KIND_CATCH
            }
            else -> {
                logger.debug { "${session.characterName} 戰鬥使用特技：skillId=$skillId 目標($tgtCol,$tgtRow)" }
                BattleAction.KIND_SKILL
            }
        }

        battleManager.handlePlayerAction(
            session.playerId, srcCol, srcRow,
            BattleAction(actionKind, skillId, tgtCol, tgtRow)
        )

        // S:053-005 確認動作設定完成 — 客戶端收到後切換到下一個可控角色
        sendActSetOK(session, srcCol, srcRow)
    }

    /**
     * C:050-002 使用道具
     *
     * 格式：+srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +itemId(2LE) +chk1(1) +chk2(1)
     */
    private fun handleUseItem(session: GameSession, data: ByteArray) {
        if (data.size < 8) return

        val reader = PacketReader(data)
        val srcCol = reader.readUByte()       // 發招者列
        val srcRow = reader.readUByte()       // 發招者行
        val tgtCol = reader.readUByte()       // 目標列
        val tgtRow = reader.readUByte()       // 目標行
        val itemId = reader.readUShortLE()

        logger.debug { "${session.characterName} 戰鬥使用道具：itemId=$itemId 目標($tgtCol,$tgtRow)" }

        battleManager.handlePlayerAction(
            session.playerId, srcCol, srcRow,
            BattleAction(BattleAction.KIND_ITEM, itemId, tgtCol, tgtRow)
        )

        // S:053-005 確認動作設定完成
        sendActSetOK(session, srcCol, srcRow)
    }

    /**
     * 發送 S:053-005 動作設定完成
     *
     * 通知客戶端此角色的行動已被伺服器確認，可切換到下一個可控角色。
     */
    private fun sendActSetOK(session: GameSession, col: Int, row: Int) {
        val writer = PacketWriter(5)
            .writeByte(Opcodes.BATTLE_STATUS)  // mainKind = 53
            .writeByte(5)                      // subKind = ActSetOK
            .writeByte(0)    // compress
            .writeByte(col)
            .writeByte(row)
        session.send(writer.toByteArray())
    }
}

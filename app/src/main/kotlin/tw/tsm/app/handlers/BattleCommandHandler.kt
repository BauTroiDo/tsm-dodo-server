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
 * Bộ xử lý lệnh kỹ năng chiến đấu (mainKind = 50)
 *
 * Nguồn: protocal.lua protocolTable[50]
 *
 * Định dạng C→S:
 * - subKind 1 (dùng kỹ năng đặc biệt): +srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +skillId(2LE) +chk1(1) +chk2(1)
 * - subKind 2 (dùng vật phẩm): +srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +itemId(2LE) +chk1(1) +chk2(1)
 */
class BattleCommandHandler(
    private val battleManager: BattleManager
) : PacketHandler {

    override val mainKind: Int = Opcodes.BATTLE_COMMAND

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        if (!battleManager.isInBattle(session.playerId)) {
            logger.warn { "${session.characterName} không trong trận chiến mà gửi lệnh chiến đấu subKind=$subKind" }
            return
        }

        when (subKind) {
            1 -> handleUseSkill(session, data)
            2 -> handleUseItem(session, data)
            else -> logger.warn { "Sub-protocol lệnh chiến đấu chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:050-001 dùng kỹ năng đặc biệt
     *
     * Định dạng: +srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +skillId(2LE) +chk1(1) +chk2(1)
     */
    private fun handleUseSkill(session: GameSession, data: ByteArray) {
        if (data.size < 8) return

        val reader = PacketReader(data)
        val srcCol = reader.readUByte()       // Cột của người ra chiêu
        val srcRow = reader.readUByte()       // Hàng của người ra chiêu
        val tgtCol = reader.readUByte()       // Cột mục tiêu
        val tgtRow = reader.readUByte()       // Hàng mục tiêu
        val skillId = reader.readUShortLE()   // ID kỹ năng
        // chk1, chk2 là mã kiểm tra, bỏ qua

        // Xác định loại hành động
        val actionKind = when {
            skillId in BattleConstants.DEFEND_SKILL_IDS -> {
                logger.debug { "${session.characterName} phòng thủ trong trận" }
                BattleAction.KIND_DEFEND
            }
            skillId in BattleConstants.ESCAPE_SKILL_IDS -> {
                logger.debug { "${session.characterName} chạy trốn trong trận: skillId=$skillId" }
                BattleAction.KIND_ESCAPE
            }
            skillId in BattleConstants.CATCH_COMMAND_SKILL_IDS -> {
                logger.debug { "${session.characterName} bắt mục tiêu trong trận: skillId=$skillId mục tiêu($tgtCol,$tgtRow)" }
                BattleAction.KIND_CATCH
            }
            else -> {
                logger.debug { "${session.characterName} dùng kỹ năng đặc biệt: skillId=$skillId mục tiêu($tgtCol,$tgtRow)" }
                BattleAction.KIND_SKILL
            }
        }

        val accepted = battleManager.handlePlayerAction(
            session.playerId, srcCol, srcRow,
            BattleAction(actionKind, skillId, tgtCol, tgtRow)
        )

        // Chỉ ACK hành động mà battle authoritative đã chấp nhận.
        if (accepted) sendActSetOK(session, srcCol, srcRow)
    }

    /**
     * C:050-002 dùng vật phẩm
     *
     * Định dạng: +srcCol(1) +srcRow(1) +tgtCol(1) +tgtRow(1) +itemId(2LE) +chk1(1) +chk2(1)
     */
    private fun handleUseItem(session: GameSession, data: ByteArray) {
        if (data.size < 8) return

        val reader = PacketReader(data)
        val srcCol = reader.readUByte()       // Cột của người ra chiêu
        val srcRow = reader.readUByte()       // Hàng của người ra chiêu
        val tgtCol = reader.readUByte()       // Cột mục tiêu
        val tgtRow = reader.readUByte()       // Hàng mục tiêu
        val itemId = reader.readUShortLE()

        logger.debug { "${session.characterName} dùng vật phẩm trong trận: itemId=$itemId mục tiêu($tgtCol,$tgtRow)" }

        val accepted = battleManager.handlePlayerAction(
            session.playerId, srcCol, srcRow,
            BattleAction(BattleAction.KIND_ITEM, itemId, tgtCol, tgtRow)
        )

        // S:053-005 chỉ xác nhận khi action thực sự được ghi vào battle.
        if (accepted) sendActSetOK(session, srcCol, srcRow)
    }

    /**
     * Gửi S:053-005 hoàn tất thiết lập động tác
     *
     * Thông báo cho client hành động của nhân vật này đã được server xác nhận, có thể chuyển sang nhân vật điều khiển được tiếp theo.
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

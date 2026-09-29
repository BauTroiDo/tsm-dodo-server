package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EWar
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.constants.PkResultCode
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.BattleManager
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý quản lý chiến đấu (mainKind = 11)
 *
 * Nguồn: protocal.lua protocolTable[11]
 *
 * Định dạng C→S:
 * - subKind 1 (kết thúc xem trận): không có dữ liệu
 * - subKind 2 (PK/tham chiến): +warType(1) +playerId(8) +index(2)
 *   warType=2(PkPlayers) → khởi tạo PK mới
 *   warType=5(Join) → tham gia vào trận chiến của mục tiêu
 * - subKind 3 (tham chiến): +playerId(8) +index(2)
 * - subKind 6 (cầu cứu): không có dữ liệu
 * - subKind 8 (PK công thành): +warType(1) +playerId(8) +index(1)
 * - subKind 9 (rời khỏi xem trận thành): không có dữ liệu
 * - subKind 10 (PK chiến trường sinh tồn): +warType(1) +playerId(8)
 */
class BattleManageHandler(
    private val battleManager: BattleManager,
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.BATTLE_MANAGE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> {
                // C:011-001 kết thúc xem trận — không có dữ liệu
                logger.trace { "${session.characterName} kết thúc xem trận" }
                // TODO: hệ thống xem trận
            }
            2 -> handleBattleRequest(session, data)
            3 -> {
                // C:011-003 tham chiến — +playerId(8) +index(2)
                logger.trace { "${session.characterName} xin tham chiến (protocol cũ) — TODO" }
            }
            6 -> {
                // C:011-006 cầu cứu — không có dữ liệu
                logger.trace { "${session.characterName} cầu cứu trong trận — TODO" }
            }
            8 -> {
                // C:011-008 PK công thành — TODO
                logger.trace { "${session.characterName} PK công thành — TODO" }
            }
            9 -> {
                // C:011-009 rời khỏi xem trận thành — TODO
                logger.trace { "${session.characterName} rời khỏi xem thành chiến — TODO" }
            }
            10 -> {
                // C:011-010 PK chiến trường sinh tồn — TODO
                logger.trace { "${session.characterName} PK chiến trường sinh tồn — TODO" }
            }
            else -> logger.warn { "Sub-protocol quản lý chiến đấu chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:011-002 — yêu cầu PK/tham chiến
     *
     * Định dạng: +warType(1) +targetPlayerId(8) +index(2)
     *
     * Client gửi các warType khác nhau tuỳ theo trạng thái mục tiêu:
     * - warType=2 (PkPlayers): mục tiêu không trong trận chiến → khởi tạo PK mới
     * - warType=5 (Join): mục tiêu đang trong trận chiến → tham chiến (gia nhập phe của họ)
     *
     * Nguồn: RoleController.lua:2760-2767 (PK)/ 2826-2888 (tham chiến)
     */
    private suspend fun handleBattleRequest(session: GameSession, data: ByteArray) {
        if (data.size < 9) return
        val reader = PacketReader(data)
        val warType = reader.readUByte()
        val targetPlayerId = reader.readULongLE()

        when (warType) {
            EWar.PK_PLAYERS -> handlePkPlayers(session, targetPlayerId)
            EWar.JOIN -> handleJoinBattle(session, targetPlayerId)
            EWar.PK_MAP_NPC -> {
                val npcId = targetPlayerId.toInt()
                val placementIdx = if (reader.remaining >= 2) reader.readUShortLE() else 0
                logger.info { "${session.characterName} mở PK với MapNpc npcId=$npcId idx=$placementIdx" }
                battleManager.createMapNpcBattle(session, npcId, placementIdx)
            }
            else -> logger.warn { "${session.characterName} chưa xử lý C:011-002 warType=$warType" }
        }
    }

    /**
     * Khởi tạo PK — warType=2 (PkPlayers)
     *
     * Quy trình kiểm tra hợp lệ:
     * 1. Người khởi tạo chưa trong trận chiến
     * 2. Mục tiêu tồn tại và cùng trên một bản đồ
     * 3. Mục tiêu chưa trong trận chiến
     * 4. Mục tiêu đã bật công tắc PK
     * 5. Sau khi đạt thì tạo trận chiến PVP
     */
    private suspend fun handlePkPlayers(session: GameSession, targetPlayerId: Long) {
        logger.info { "${session.characterName} mở PK → mục tiêu $targetPlayerId" }

        // Kiểm tra hợp lệ: người khởi tạo không trong trận chiến
        if (battleManager.isInBattle(session.playerId)) {
            logger.debug { "${session.characterName} đang trong trận chiến, không thể mở PK" }
            return
        }

        // Kiểm tra hợp lệ: mục tiêu tồn tại
        val target = worldManager.getPlayerById(targetPlayerId)
        if (target == null) {
            responseSender.sendPkResult(session, PkResultCode.SPECIAL_BATTLE)
            return
        }

        // Kiểm tra hợp lệ: cùng bản đồ
        if (session.mapId != target.mapId) {
            responseSender.sendPkResult(session, PkResultCode.SCENE_NO_PK)
            return
        }

        // Kiểm tra hợp lệ: mục tiêu không trong trận chiến
        if (battleManager.isInBattle(target.playerId)) {
            val battle = battleManager.getPlayerBattle(target.playerId)
            val resultCode = when (battle?.warType) {
                EWar.BE_QUESTION -> PkResultCode.IN_QUESTION
                else -> PkResultCode.SPECIAL_BATTLE
            }
            responseSender.sendPkResult(session, resultCode)
            return
        }

        // Kiểm tra hợp lệ: công tắc PK của mục tiêu
        if (!target.pkEnabled) {
            responseSender.sendPkResult(session, PkResultCode.PK_OFF)
            return
        }

        // Tạo trận chiến PVP
        battleManager.createPvpBattle(session, target)
    }

    /**
     * Tham chiến — warType=5 (Join)
     *
     * Client dùng EInteractive.PlayOne để kích hoạt khi war của mục tiêu != None.
     * Người tham gia được xếp vào cùng phe với mục tiêu.
     *
     * Quy trình kiểm tra hợp lệ:
     * 1. Người tham gia không trong trận chiến
     * 2. Mục tiêu tồn tại
     * 3. Mục tiêu đang trong trận chiến
     * 4. Loại trận chiến cho phép tham chiến
     * 5. Tổ đội còn chỗ trống
     * 6. Gia nhập trận chiến
     */
    private suspend fun handleJoinBattle(session: GameSession, targetPlayerId: Long) {
        logger.info { "${session.characterName} xin tham chiến → mục tiêu $targetPlayerId" }

        // Kiểm tra hợp lệ: người tham gia không trong trận chiến
        if (battleManager.isInBattle(session.playerId)) {
            logger.debug { "${session.characterName} đang trong trận chiến, không thể tham chiến" }
            return
        }

        // Kiểm tra hợp lệ: mục tiêu tồn tại
        val target = worldManager.getPlayerById(targetPlayerId)
        if (target == null) {
            logger.debug { "Mục tiêu tham chiến $targetPlayerId không trực tuyến" }
            return
        }

        // Kiểm tra hợp lệ: mục tiêu đang trong trận chiến
        val battle = battleManager.getPlayerBattle(target.playerId)
        if (battle == null) {
            logger.debug { "Mục tiêu ${target.characterName} không ở trong trận chiến" }
            return
        }

        // Kiểm tra hợp lệ: loại trận chiến cho phép tham chiến
        when (battle.warType) {
            EWar.BE_QUESTION -> {
                responseSender.sendPkResult(session, PkResultCode.IN_QUESTION)
                return
            }
            EWar.PK_PLAYERS, EWar.EVENT, EWar.PK_MAP_NPC -> {
                // Cho phép tham chiến
            }
            else -> {
                responseSender.sendPkResult(session, PkResultCode.SPECIAL_BATTLE)
                return
            }
        }

        // Kiểm tra hợp lệ: trạng thái trận chiến cho phép gia nhập (đã kết thúc thì không được)
        if (battle.state == tw.tsm.gamelogic.battle.BattleInstance.State.FINISHED) {
            logger.debug { "Trận ${battle.battleId} đã kết thúc, không thể tham chiến" }
            return
        }

        // Tham chiến
        val success = battleManager.joinBattle(session, battle, target.playerId)
        if (!success) {
            // Đủ người rồi → S:011-003 failCode=1
            responseSender.sendBattleError(session, 1)
        }
    }
}

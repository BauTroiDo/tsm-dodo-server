package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.DispatchCompleteResult
import tw.tsm.domain.constants.DispatchStartResult
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.DispatchSaveData
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.gamelogic.systems.DispatchSystem
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * Handler hệ thống phái cử (mainKind = 86)
 *
 * Nguồn: protocal.lua protocolTable[86] (dòng 13352-13453)
 *
 * Định dạng C→S:
 * - subKind 1 (tra cứu dữ liệu phái cử): không có dữ liệu
 * - subKind 2 (phái tướng xuất phát): +chỉ số tướng lữ quán(1)
 * - subKind 5 (hoàn thành phái cử): +chỉ số thanh tiến độ(1)
 */
class DispatchHandler(
    private val dispatchSystem: DispatchSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.DISPATCH

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleQuery(session)
            2 -> handleStart(session, data)
            5 -> handleComplete(session, data)
            else -> logger.warn { "Sub-protocol phái đi chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:086-001 Tra cứu dữ liệu phái cử
     *
     * Trả về: S:086-003 (dữ liệu phái cử hiện có) + S:086-004 (hiệu quả) + S:086-006 (số lượng tối đa) + S:086-001 (mở UI)
     */
    private suspend fun handleQuery(session: GameSession) {
        val charId = session.roleId.toLong()
        logger.trace { "${session.characterName} tra cứu dữ liệu phái đi" }

        // Tải các phái cử đang tiến hành
        val dispatches = dispatchSystem.queryDispatches(charId)

        // S:086-003 cập nhật dữ liệu phái cử
        if (dispatches.isNotEmpty()) {
            sendDispatchData(session, dispatches)
        }

        // S:086-004 cập nhật hiệu quả phái cử (sinh ngẫu nhiên)
        val (effect1, effect2) = dispatchSystem.randomEffectIndices()
        sendDispatchEffect(session, 0, effect1, effect2)

        // S:086-006 cập nhật số lượng phái cử tối đa
        sendBarCount(session, DispatchSystem.MAX_SLIDER_COUNT)

        // S:086-001 tín hiệu mở UI (gửi cuối cùng)
        sendOpenUI(session)
    }

    /**
     * C:086-002 Phái tướng xuất phát
     *
     * Định dạng: +chỉ số tướng lữ quán(1)
     */
    private suspend fun handleStart(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val innPetIndex = reader.readUByte()
        val charId = session.roleId.toLong()

        logger.trace { "${session.characterName} phái tướng xuất phát: innIndex=$innPetIndex" }

        // Lấy thời gian server hiện tại (OLE Automation Date)
        val currentTime = getCurrentOaDate()

        // Hiệu quả ngẫu nhiên
        val (effect1, effect2) = dispatchSystem.randomEffectIndices()

        val (resultCode, saveData) = dispatchSystem.startDispatch(
            characterId = charId,
            innPetIndex = innPetIndex,
            currentTime = currentTime,
            effectIndex1 = effect1,
            effectIndex2 = effect2
        )

        // S:086-002 kết quả phái cử
        sendDispatchResult(session, resultCode)

        // Khi thành công cập nhật dữ liệu phái cử
        if (resultCode == DispatchStartResult.SUCCESS && saveData != null) {
            sendDispatchData(session, listOf(saveData))
        }
    }

    /**
     * C:086-005 Hoàn thành phái cử (nhận thưởng)
     *
     * Định dạng: +chỉ số thanh tiến độ(1)
     */
    private suspend fun handleComplete(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val slotIndex = reader.readUByte()
        val charId = session.roleId.toLong()

        logger.trace { "${session.characterName} hoàn thành phái đi: slot=$slotIndex" }

        val currentTime = getCurrentOaDate()

        val (resultCode, _) = dispatchSystem.completeDispatch(
            characterId = charId,
            slotIndex = slotIndex,
            currentTime = currentTime
        )

        // S:086-005 kết quả nhận thưởng
        sendCompleteResult(session, resultCode)

        // Sau khi thành công gửi lại dữ liệu (xoá slot đã hoàn thành)
        if (resultCode == DispatchCompleteResult.SUCCESS) {
            val remaining = dispatchSystem.queryDispatches(charId)
            sendDispatchData(session, remaining)
        }
    }

    // ── Ghép gói tin S→C ──

    /** S:086-001 Trả về dữ liệu tướng hoàn tất (tín hiệu mở UI)*/
    private fun sendOpenUI(session: GameSession) {
        val packet = PacketWriter(3)
            .writeByte(Opcodes.DISPATCH).writeByte(1).writeByte(0)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-002 Kết quả phái tướng*/
    private fun sendDispatchResult(session: GameSession, resultCode: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(2).writeByte(0)
            .writeByte(resultCode)
            .toByteArray()
        session.send(packet)
    }

    /**
     * S:086-003 Cập nhật dữ liệu phái cử
     *
     * Định dạng: +số lượng(1) + N×[chỉ số thanh tiến độ(1) + thời gian bắt đầu(8) + thời gian kết thúc(8) + loại phái cử(1) + chỉ số tướng lữ quán(1) + chỉ số hiệu quả 1(1) + chỉ số hiệu quả 2(1)]
     */
    private fun sendDispatchData(session: GameSession, dispatches: List<DispatchSaveData>) {
        val packetSize = 3 + 1 + dispatches.size * 21 // header(3) + count(1) + N * 21
        val writer = PacketWriter(packetSize)
            .writeByte(Opcodes.DISPATCH).writeByte(3).writeByte(0)
            .writeByte(dispatches.size)

        for (d in dispatches) {
            writer.writeByte(d.slotIndex)
            writer.writeDoubleLE(d.startTime)
            writer.writeDoubleLE(d.endTime)
            writer.writeByte(d.dispatchKind)
            writer.writeByte(d.innPetIndex)
            writer.writeByte(d.effectIndex1)
            writer.writeByte(d.effectIndex2)
        }

        session.send(writer.toByteArray())
    }

    /** S:086-004 Cập nhật hiệu quả phái cử*/
    private fun sendDispatchEffect(session: GameSession, dispatchKind: Int, effect1: Int, effect2: Int) {
        val packet = PacketWriter(6)
            .writeByte(Opcodes.DISPATCH).writeByte(4).writeByte(0)
            .writeByte(dispatchKind)
            .writeByte(effect1)
            .writeByte(effect2)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-005 Kết quả nhận thưởng phái cử*/
    private fun sendCompleteResult(session: GameSession, resultCode: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(5).writeByte(0)
            .writeByte(resultCode)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-006 Cập nhật số lượng phái cử tối đa*/
    private fun sendBarCount(session: GameSession, count: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(6).writeByte(0)
            .writeByte(count)
            .toByteArray()
        session.send(packet)
    }

    /** Lấy thời gian server hiện tại (OLE Automation Date)*/
    private fun getCurrentOaDate(): Double {
        // OLE Automation Date：1899-12-30 00:00:00 = 0.0
        // Java epoch (1970-01-01) = 25569.0 in OA
        val javaMillis = System.currentTimeMillis()
        return (javaMillis / 86_400_000.0) + 25569.0
    }
}

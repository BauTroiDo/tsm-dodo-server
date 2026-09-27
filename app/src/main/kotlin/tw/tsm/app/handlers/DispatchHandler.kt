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
 * 派遣系統 Handler（mainKind = 86）
 *
 * 來源：protocal.lua protocolTable[86]（第 13352-13453 行）
 *
 * C→S 格式：
 * - subKind 1（查詢派遣資料）：無資料
 * - subKind 2（派遣武將出發）：+客棧武將索引(1)
 * - subKind 5（完成派遣）：+進度條索引(1)
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
            else -> logger.warn { "未處理的派遣子協定：subKind=$subKind" }
        }
    }

    /**
     * C:086-001 查詢派遣資料
     *
     * 回傳：S:086-003（現有派遣資料）+ S:086-004（效果）+ S:086-006（最大數量）+ S:086-001（UI 打開）
     */
    private suspend fun handleQuery(session: GameSession) {
        val charId = session.roleId.toLong()
        logger.trace { "${session.characterName} 查詢派遣資料" }

        // 載入進行中的派遣
        val dispatches = dispatchSystem.queryDispatches(charId)

        // S:086-003 更新派遣資料
        if (dispatches.isNotEmpty()) {
            sendDispatchData(session, dispatches)
        }

        // S:086-004 更新派遣效果（隨機產生）
        val (effect1, effect2) = dispatchSystem.randomEffectIndices()
        sendDispatchEffect(session, 0, effect1, effect2)

        // S:086-006 更新派遣最大數量
        sendBarCount(session, DispatchSystem.MAX_SLIDER_COUNT)

        // S:086-001 UI 打開信號（最後發送）
        sendOpenUI(session)
    }

    /**
     * C:086-002 派遣武將出發
     *
     * 格式：+客棧武將索引(1)
     */
    private suspend fun handleStart(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val innPetIndex = reader.readUByte()
        val charId = session.roleId.toLong()

        logger.trace { "${session.characterName} 派遣武將出發：innIndex=$innPetIndex" }

        // 取得目前伺服器時間（OLE Automation Date）
        val currentTime = getCurrentOaDate()

        // 隨機效果
        val (effect1, effect2) = dispatchSystem.randomEffectIndices()

        val (resultCode, saveData) = dispatchSystem.startDispatch(
            characterId = charId,
            innPetIndex = innPetIndex,
            currentTime = currentTime,
            effectIndex1 = effect1,
            effectIndex2 = effect2
        )

        // S:086-002 派遣結果
        sendDispatchResult(session, resultCode)

        // 成功時更新派遣資料
        if (resultCode == DispatchStartResult.SUCCESS && saveData != null) {
            sendDispatchData(session, listOf(saveData))
        }
    }

    /**
     * C:086-005 完成派遣（領取獎勵）
     *
     * 格式：+進度條索引(1)
     */
    private suspend fun handleComplete(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) return
        val reader = PacketReader(data)
        val slotIndex = reader.readUByte()
        val charId = session.roleId.toLong()

        logger.trace { "${session.characterName} 完成派遣：slot=$slotIndex" }

        val currentTime = getCurrentOaDate()

        val (resultCode, _) = dispatchSystem.completeDispatch(
            characterId = charId,
            slotIndex = slotIndex,
            currentTime = currentTime
        )

        // S:086-005 領獎結果
        sendCompleteResult(session, resultCode)

        // 成功後重新發送資料（刪除已完成的 slot）
        if (resultCode == DispatchCompleteResult.SUCCESS) {
            val remaining = dispatchSystem.queryDispatches(charId)
            sendDispatchData(session, remaining)
        }
    }

    // ── S→C 封包組裝 ──

    /** S:086-001 回傳武將資料完成（UI 打開信號） */
    private fun sendOpenUI(session: GameSession) {
        val packet = PacketWriter(3)
            .writeByte(Opcodes.DISPATCH).writeByte(1).writeByte(0)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-002 派遣武將結果 */
    private fun sendDispatchResult(session: GameSession, resultCode: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(2).writeByte(0)
            .writeByte(resultCode)
            .toByteArray()
        session.send(packet)
    }

    /**
     * S:086-003 更新派遣資料
     *
     * 格式：+數量(1) + N×[進度條索引(1) + 開始時間(8) + 結束時間(8) + 派遣種類(1) + 客棧武將索引(1) + 效果索引1(1) + 效果索引2(1)]
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

    /** S:086-004 更新派遣效果 */
    private fun sendDispatchEffect(session: GameSession, dispatchKind: Int, effect1: Int, effect2: Int) {
        val packet = PacketWriter(6)
            .writeByte(Opcodes.DISPATCH).writeByte(4).writeByte(0)
            .writeByte(dispatchKind)
            .writeByte(effect1)
            .writeByte(effect2)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-005 領取派遣獎勵結果 */
    private fun sendCompleteResult(session: GameSession, resultCode: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(5).writeByte(0)
            .writeByte(resultCode)
            .toByteArray()
        session.send(packet)
    }

    /** S:086-006 更新派遣最大數量 */
    private fun sendBarCount(session: GameSession, count: Int) {
        val packet = PacketWriter(4)
            .writeByte(Opcodes.DISPATCH).writeByte(6).writeByte(0)
            .writeByte(count)
            .toByteArray()
        session.send(packet)
    }

    /** 取得目前伺服器時間（OLE Automation Date） */
    private fun getCurrentOaDate(): Double {
        // OLE Automation Date：1899-12-30 00:00:00 = 0.0
        // Java epoch (1970-01-01) = 25569.0 in OA
        val javaMillis = System.currentTimeMillis()
        return (javaMillis / 86_400_000.0) + 25569.0
    }
}

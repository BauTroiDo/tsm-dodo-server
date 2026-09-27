package tw.tsm.app.response

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.EveResult
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MissionFlagRecord
import tw.tsm.domain.interfaces.repositories.MissionRecord
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * NPC 事件/任務/永標相關封包序列化
 *
 * mainKind = 20（NPC 事件）, 24（任務）, 81（永標）
 */
class NpcEventResponseSender {

    /** S:020-007 事件換場景通知 */
    fun sendEventChangeScene(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(7).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:020-001 一般事件結果
     *
     * 格式（15 bytes LE）：
     * +resultDiagnosis(1) +resultGroupNo(2) +resultNo(1) +resultType(1)
     * +resultClass(1) +parameter(2) +parameterStyle(1) +resultValue(4) +resultMeanNo(2)
     */
    fun sendCommonEvent(session: SessionSender, result: EveResult) {
        logger.debug { "發送 S:020-001：type=${result.resultType} class=${result.resultClass} " +
            "groupNo=${result.resultGroupNo} no=${result.resultNo} param=${result.parameter} " +
            "style=${result.parameterStyle} value=${result.resultValue} meanNo=${result.resultMeanNo}" }
        session.send(
            PacketWriter(18)
                .writeByte(Opcodes.NPC_EVENT).writeByte(1).writeByte(0)
                .writeByte(0)                            // resultDiagnosis = 0
                .writeShortLE(result.resultGroupNo)
                .writeByte(result.resultNo)
                .writeByte(result.resultType)
                .writeByte(result.resultClass)
                .writeShortLE(result.parameter)
                .writeByte(result.parameterStyle)
                .writeIntLE(result.resultValue)
                .writeShortLE(result.resultMeanNo)
                .toByteArray()
        )
    }

    /**
     * S:020-008 事件結束
     *
     * 格式：+endKind(1)
     */
    fun sendEventEnd(session: SessionSender, endKind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_EVENT).writeByte(8).writeByte(0)
                .writeByte(endKind)
                .toByteArray()
        )
    }

    /**
     * S:036-011 開啟玩家第一次轉生介面
     *
     * 客戶端固定執行 UI.Open(UITurn, ETrunMode.Reincarnation, 1)，封包本體無資料。
     */
    fun sendOpenPlayerReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(11).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:036-013 開啟殺敵點兌換商店
     *
     * 客戶端依 protocolTable[36][13] 自行用 giftDot > 0 的道具組出兌換清單，封包本身無 payload。
     */
    fun sendOpenKillPointShop(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(13).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:036-015 開啟水戰奉邑介面或續行事件
     *
     * 客戶端水戰 UI 已停用，Lua 只呼叫 EventManager.SetConduct(true)，封包本身無 payload。
     */
    fun sendOpenWaterWarOrgManage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(15).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:044-003 開啟武將二轉介面
     *
     * 客戶端固定執行 UI.Open(UINpcTurn, 2)，封包本體無資料。
     */
    fun sendOpenNpcSecondReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(3).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:044-002 開啟武將轉生（一轉）介面
     *
     * 客戶端固定執行 UI.Open(UINpcTurn, 1)，封包本體無資料。
     */
    fun sendOpenNpcReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(2).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-027 尋找惡魔巴豆妖錯誤訊息 */
    fun sendBadoErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(27).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-029 尋找惡魔巴豆妖開始訊息 */
    fun sendBadoStartMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(29).writeByte(0)
                .toByteArray()
        )
    }

    /** S:036-010 開啟無界排行榜 */
    fun sendOpenGalaxyLeaderboard(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(10).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-030 聖誕節活動訊息 */
    fun sendChristmasEventMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(30).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-032 擂台賽錯誤訊息 */
    fun sendArenaErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(32).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-035 元宵節錯誤訊息 */
    fun sendLanternErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(35).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-039 未知活動訊息 */
    fun sendUnknownActivityMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(39).writeByte(0)
                .toByteArray()
        )
    }

    // ── 任務系統 (mainKind=24) ──

    /** S:024-001 新增任務步驟 */
    fun sendAddMissionStep(session: SessionSender, missionId: Int, step: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(1).writeByte(0)
                .writeShortLE(missionId)
                .writeByte(step)
                .toByteArray()
        )
    }

    /** S:024-002 減少任務步驟 */
    fun sendDecMissionStep(session: SessionSender, missionId: Int, step: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(2).writeByte(0)
                .writeShortLE(missionId)
                .writeByte(step)
                .toByteArray()
        )
    }

    /** S:024-004 刪除任務 */
    fun sendRemoveMission(session: SessionSender, missionId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(4).writeByte(0)
                .writeShortLE(missionId)
                .toByteArray()
        )
    }

    /**
     * S:024-006 初始化任務步驟（登入時）
     *
     * 格式：+count(4B LE) +<<index(1B) +missionId(2B LE) +step(1B)>>
     */
    fun sendInitMissionSteps(session: SessionSender, missions: List<MissionRecord>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MARK).writeByte(6).writeByte(0)
            .writeIntLE(missions.size)
        missions.forEachIndexed { index, m ->
            writer.writeByte(index)
            writer.writeShortLE(m.missionId)
            writer.writeByte(m.step)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:024-007 初始化任務標記（登入時）
     *
     * 客戶端 InitMissionFlag 格式：+count(2B LE) +<<byteIndex(2B LE) +byteValue(1B)>>
     */
    fun sendInitMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>) {
        val byteMap = mutableMapOf<Int, Int>()
        for (f in flags) {
            if (f.bitValue != 0) {
                val byteIndex = (f.bitIndex - 1) / 8 + 1
                val bitMask = 1 shl ((f.bitIndex - 1) % 8)
                byteMap[byteIndex] = (byteMap[byteIndex] ?: 0) or bitMask
            }
        }

        val writer = PacketWriter()
            .writeByte(Opcodes.MARK).writeByte(7).writeByte(0)
            .writeShortLE(byteMap.size)
        for ((index, value) in byteMap) {
            writer.writeShortLE(index)
            writer.writeByte(value)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:024-005 設定任務標記
     *
     * 格式：+count(4B LE UInt32) <<+bitIndex(2B LE UInt16) +bitValue(1B Boolean)>>
     * 注意：count 用 UInt32（4B），與 S:024-007（count=UInt16）格式不同！
     */
    fun sendSetMissionFlags(session: SessionSender, flags: List<MissionFlagRecord>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MARK).writeByte(5).writeByte(0)
            .writeIntLE(flags.size)
        for (f in flags) {
            writer.writeShortLE(f.bitIndex)
            writer.writeBoolean(f.bitValue != 0)
        }
        session.send(writer.toByteArray())
    }

    // ── 永標系統 (mainKind=81) ──

    /**
     * S:081-002 初始化永標
     *
     * 格式：+size(2B LE UInt16) +永標字節陣列(size×1B)
     */
    fun sendInitBitFlags(session: SessionSender, packedFlags: ByteArray) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FOREVER_FLAG).writeByte(2).writeByte(0)
            .writeShortLE(packedFlags.size)
            .writeBytes(packedFlags)
        session.send(writer.toByteArray())
    }

    /**
     * S:081-001 永標變更
     *
     * 格式：+count(4B LE UInt32) <<+flagId(2B LE UInt16) +value(1B Boolean)>>
     */
    fun sendBitFlagChanges(session: SessionSender, changes: List<Pair<Int, Boolean>>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FOREVER_FLAG).writeByte(1).writeByte(0)
            .writeIntLE(changes.size)
        for ((flagId, value) in changes) {
            writer.writeShortLE(flagId)
            writer.writeBoolean(value)
        }
        session.send(writer.toByteArray())
    }
}

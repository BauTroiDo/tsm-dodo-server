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
 * Tuần tự hoá gói tin liên quan sự kiện NPC/nhiệm vụ/cờ vĩnh viễn
 *
 * mainKind = 20（sự kiện NPC）, 24（nhiệm vụ）, 81（cờ vĩnh viễn）
 */
class NpcEventResponseSender {

    /** S:020-007 Thông báo sự kiện đổi cảnh*/
    fun sendEventChangeScene(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(7).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:020-001 Kết quả sự kiện thông thường
     *
     * Định dạng (15 bytes LE):
     * +resultDiagnosis(1) +resultGroupNo(2) +resultNo(1) +resultType(1)
     * +resultClass(1) +parameter(2) +parameterStyle(1) +resultValue(4) +resultMeanNo(2)
     */
    fun sendCommonEvent(session: SessionSender, result: EveResult) {
        logger.debug { "Gửi S:020-001: type=${result.resultType} class=${result.resultClass} " +
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
     * S:020-008 Kết thúc sự kiện
     *
     * Định dạng: +endKind(1)
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
     * S:036-011 Mở giao diện chuyển sinh lần đầu của người chơi
     *
     * Client luôn thực thi UI.Open(UITurn, ETrunMode.Reincarnation, 1)，bản thân gói tin không có dữ liệu.
     */
    fun sendOpenPlayerReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(11).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:036-013 Mở cửa hàng đổi điểm diệt địch
     *
     * Client theo protocolTable[36][13] tự dùng vật phẩm có giftDot > 0 để dựng danh sách đổi thưởng, bản thân gói tin không có payload.
     */
    fun sendOpenKillPointShop(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(13).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:036-015 Mở giao diện thuỷ chiến phụng ấp hoặc tiếp tục sự kiện
     *
     * UI thuỷ chiến của client đã bị vô hiệu, Lua chỉ gọi EventManager.SetConduct(true)，bản thân gói tin không có payload.
     */
    fun sendOpenWaterWarOrgManage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(15).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:044-003 Mở giao diện chuyển sinh lần 2 của tướng
     *
     * Client luôn thực thi UI.Open(UINpcTurn, 2)，bản thân gói tin không có dữ liệu.
     */
    fun sendOpenNpcSecondReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(3).writeByte(0)
                .toByteArray()
        )
    }

    /**
     * S:044-002 Mở giao diện chuyển sinh tướng（chuyển sinh lần 1）
     *
     * Client luôn thực thi UI.Open(UINpcTurn, 1)，bản thân gói tin không có dữ liệu.
     */
    fun sendOpenNpcReincarnation(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_REINCARNATION).writeByte(2).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-027 Thông báo lỗi tìm ác ma Ba Đậu Yêu*/
    fun sendBadoErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(27).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-029 Thông báo bắt đầu tìm ác ma Ba Đậu Yêu*/
    fun sendBadoStartMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(29).writeByte(0)
                .toByteArray()
        )
    }

    /** S:036-010 Mở bảng xếp hạng Vô Giới*/
    fun sendOpenGalaxyLeaderboard(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.CHALLENGE).writeByte(10).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-030 Thông báo sự kiện lễ Giáng sinh*/
    fun sendChristmasEventMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(30).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-032 Thông báo lỗi giải đấu đài*/
    fun sendArenaErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(32).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-035 Thông báo lỗi lễ Nguyên Tiêu*/
    fun sendLanternErrorMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(35).writeByte(0)
                .toByteArray()
        )
    }

    /** S:020-039 Thông báo sự kiện không xác định*/
    fun sendUnknownActivityMessage(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.NPC_EVENT).writeByte(39).writeByte(0)
                .toByteArray()
        )
    }

    // ── Hệ thống nhiệm vụ (mainKind=24) ──

    /** S:024-001 Thêm bước nhiệm vụ*/
    fun sendAddMissionStep(session: SessionSender, missionId: Int, step: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(1).writeByte(0)
                .writeShortLE(missionId)
                .writeByte(step)
                .toByteArray()
        )
    }

    /** S:024-002 Giảm bước nhiệm vụ*/
    fun sendDecMissionStep(session: SessionSender, missionId: Int, step: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(2).writeByte(0)
                .writeShortLE(missionId)
                .writeByte(step)
                .toByteArray()
        )
    }

    /** S:024-004 Xoá nhiệm vụ*/
    fun sendRemoveMission(session: SessionSender, missionId: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MARK).writeByte(4).writeByte(0)
                .writeShortLE(missionId)
                .toByteArray()
        )
    }

    /**
     * S:024-006 Khởi tạo bước nhiệm vụ（khi đăng nhập）
     *
     * Định dạng: +count(4B LE) +<<index(1B) +missionId(2B LE) +step(1B)>>
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
     * S:024-007 Khởi tạo cờ đánh dấu nhiệm vụ（khi đăng nhập）
     *
     * Định dạng InitMissionFlag của client: +count(2B LE) +<<byteIndex(2B LE) +byteValue(1B)>>
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
     * S:024-005 Đặt cờ đánh dấu nhiệm vụ
     *
     * Định dạng: +count(4B LE UInt32) <<+bitIndex(2B LE UInt16) +bitValue(1B Boolean)>>
     * Lưu ý: count dùng UInt32（4B），khác định dạng với S:024-007（count=UInt16）!
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

    // ── Hệ thống cờ vĩnh viễn (mainKind=81) ──

    /**
     * S:081-002 Khởi tạo cờ vĩnh viễn
     *
     * Định dạng: +size(2B LE UInt16) +mảng byte cờ vĩnh viễn(size×1B)
     */
    fun sendInitBitFlags(session: SessionSender, packedFlags: ByteArray) {
        val writer = PacketWriter()
            .writeByte(Opcodes.FOREVER_FLAG).writeByte(2).writeByte(0)
            .writeShortLE(packedFlags.size)
            .writeBytes(packedFlags)
        session.send(writer.toByteArray())
    }

    /**
     * S:081-001 Thay đổi cờ vĩnh viễn
     *
     * Định dạng: +count(4B LE UInt32) <<+flagId(2B LE UInt16) +value(1B Boolean)>>
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

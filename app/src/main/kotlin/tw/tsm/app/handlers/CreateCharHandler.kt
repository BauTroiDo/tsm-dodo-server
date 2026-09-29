package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterCreateData
import tw.tsm.domain.interfaces.repositories.CharacterRepository

import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý tạo nhân vật (mainKind = 9)
 *
 * opcode con:
 * - C:009-001 tạo nhân vật mới
 * - C:009-002 kiểm tra tên có khả dụng không
 *
 * 📖 Nguồn giao thức: protocal.lua protocolTable[9]
 * 📖 Ghép gói tin: UICreateRole.lua OnClick_Next
 */
class CreateCharHandler(
    private val characterRepository: CharacterRepository,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.CREATE_CHAR

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        // Phải đã đăng nhập
        if (session.state != SessionState.LOGGED_IN) {
            logger.warn { "Thử tạo nhân vật khi chưa đăng nhập: state=${session.state}" }
            return
        }

        when (subKind) {
            1 -> handleCreateChar(session, data)
            2 -> handleCheckName(session, data)
            else -> logger.warn { "Sub-protocol tạo nhân vật chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * Xử lý tạo nhân vật mới (C:009-001)
     *
     * Định dạng gói tin (thứ tự ghi thực tế của UICreateRole.lua OnClick_Next):
     * +họ tên(ByteL+Big5) +giới tính(1) +0(1) +kiểu tóc(1) +0(1)
     * +bảng màu 1(Int32) +bảng màu 2(Int32) +thuộc tính(1)
     * +sáu chỉ số(1)*6 +cờ riêng tư(1)
     *
     * ⚠️ Lưu ý: thứ tự gói tin thực tế là «ưu tiên tên»,
     *    Khác với «ưu tiên giới tính» trong chú thích của protocal.lua!
     */
    private suspend fun handleCreateChar(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)

        // Tên (1 byte byte-length + UTF-16LE; căn chỉnh VTC ByteBuffer.WriteStringWithByteL)
        val name = reader.readUnicodeWithByteLen()

        // Kiểm tra hợp lệ tên
        if (!isValidName(name)) {
            logger.warn { "Tên nhân vật không hợp lệ: '$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_ILLEGAL)
            return
        }

        // Giới tính (1=nam, 2=nữ)
        val sex = reader.readUByte()
        reader.readUByte()    // Điền 0 (ảnh mặt, cố định 0)

        // Kiểu tóc
        val hair = reader.readUByte()
        reader.readUByte()    // Điền 0 (dáng người, cố định 0)

        // Bảng màu (Int32 LE)
        val color1 = reader.readIntLE()
        val color2 = reader.readIntLE()

        // Thuộc tính ngũ hành (1-4)
        val element = reader.readUByte()

        // Phân điểm sáu chỉ số (theo thứ tự UICreateRole.lua attributeValue[1~6])
        val int_ = reader.readUByte()   // [1] trí lực
        val str = reader.readUByte()    // [2] tấn công
        val vit = reader.readUByte()    // [3] phòng thủ
        val spr = reader.readUByte()    // [4] biên độ Hp
        val men = reader.readUByte()    // [5] biên độ Sp
        val dex = reader.readUByte()    // [6] nhanh nhẹn

        // Cờ riêng tư (bỏ qua)
        if (reader.isReadable) {
            reader.readUByte()
        }

        // ── Kiểm tra hợp lệ phía server ──

        // Kiểm tra hợp lệ giới tính (1=nam, 2=nữ)
        if (sex !in 1..2) {
            logger.warn { "Giới tính không hợp lệ: $sex" }
            return
        }

        // Kiểm tra hợp lệ kiểu tóc (chưa đổi: nam 0-4, nữ 0-6)
        val maxHair = if (sex == 1) MAX_HAIR_MALE else MAX_HAIR_FEMALE
        if (hair !in 0 until maxHair) {
            logger.warn { "Kiểu tóc không hợp lệ: hair=$hair sex=$sex (tối đa=$maxHair)" }
            return
        }

        // Kiểm tra hợp lệ ngũ hành (1-4, UI client chỉ có 4 lựa chọn)
        if (element !in 1..4) {
            logger.warn { "Thuộc tính không hợp lệ: $element" }
            return
        }

        // Tổng điểm phân bổ sáu chỉ số phải đúng bằng 6 (phải chia hết toàn bộ điểm ban đầu)
        val totalStats = str + dex + vit + int_ + men + spr
        if (totalStats != MAX_INITIAL_STAT_POINTS) {
            logger.warn { "Phân điểm không đúng: $totalStats ≠ $MAX_INITIAL_STAT_POINTS" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_OTHER_ERROR)
            return
        }

        // Mỗi thuộc tính không được vượt quá 6
        if (listOf(str, dex, vit, int_, men, spr).any { it > MAX_INITIAL_STAT_POINTS }) {
            logger.warn { "Một chỉ số phân điểm vượt giới hạn" }
            return
        }

        // Kiểm tra tên trùng
        if (characterRepository.isNameTaken(name)) {
            logger.info { "Tên nhân vật bị trùng: '$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_DUPLICATE)
            return
        }

        // ── Tạo nhân vật ──
        val createData = CharacterCreateData(
            name = name,
            sex = sex,
            hair = hair,
            color1 = color1,
            color2 = color2,
            element = element,
            str = str,
            dex = dex,
            vit = vit,
            int_ = int_,
            men = men,
            spr = spr
        )

        try {
            val characterId = characterRepository.create(session.accountId, createData)
            session.characterName = name
            session.roleId = characterId.toInt()

            logger.info { "Tạo nhân vật thành công: '$name' (id=$characterId) tài khoản=${session.accountId}" }

            // S:009-001 tạo nhân vật thành công
            responseSender.sendCreateCharSuccess(session)
        } catch (e: Exception) {
            logger.error(e) { "Tạo nhân vật thất bại: '$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_OTHER_ERROR)
        }
    }

    /**
     * Xử lý kiểm tra tên (C:009-002)
     *
     * Định dạng gói tin: +tên(ByteL+UTF-16LE；VTC encoding_Unicode)
     */
    private suspend fun handleCheckName(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val name = reader.readUnicodeWithByteLen()

        val result = when {
            !isValidName(name) -> NAME_RESULT_ILLEGAL
            characterRepository.isNameTaken(name) -> NAME_RESULT_DUPLICATE
            else -> NAME_RESULT_AVAILABLE
        }

        logger.info { "Kiểm tra tên: '$name' → result=$result" }
        responseSender.sendCheckNameResult(session, result)
    }

    /**
     * Kiểm tra hợp lệ tên nhân vật
     *
     * Quy tắc (nguồn: UICreateRole.lua + string.lua CheckName):
     * - Không được để trống
     * - Không vượt quá giới hạn bytes sau khi chuyển mã Big5 hoặc UTF-8
     * - Không được chứa ký tự khoảng trắng
     *
     * ⚠️ Logic chỉ-Big5 ban đầu đã nới lỏng, chuyển sang hỗ trợ UTF-8 (tương thích tên tiếng Anh và đa ngôn ngữ).
     *    Lý do: client chuyển mã bằng Big5, nhưng Big5 không biểu thị được Unicode ngoài ký tự tiếng Trung
     *    (như tiếng Việt có dấu), dẫn đến tên bị từ chối. Đổi sang UTF-8 fallback để tương thích mọi ngôn ngữ.
     */
    private fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false

        // Không cho phép khoảng trắng
        if (name.contains(' ') || name.contains('\t')) return false

        // Thử chuyển mã Big5 (tương thích client gốc)
        val big5Bytes = try {
            name.toByteArray(charset("Big5"))
        } catch (_: Exception) {
            null
        }

        // Nếu Big5 mã hoá được và độ dài hợp lệ thì cho qua trực tiếp
        if (big5Bytes != null && big5Bytes.isNotEmpty() && big5Bytes.size <= MAX_NAME_LENGTH_BIG5) {
            return true
        }

        // Chuyển mã Big5 thất bại (có ký tự ngoài Big5), đổi sang kiểm tra độ dài UTF-8
        val utf8Bytes = name.toByteArray(Charsets.UTF_8)
        if (utf8Bytes.isEmpty() || utf8Bytes.size > MAX_NAME_LENGTH_UTF8) return false

        return true
    }

    companion object {
        /** Độ dài tối đa tên theo Big5*/
        const val MAX_NAME_LENGTH_BIG5 = 14

        /** Độ dài tối đa tên theo UTF-8 (UTF-8 mỗi ký tự tối đa 4 bytes, cho phép tên tiếng Anh dài hơn)*/
        const val MAX_NAME_LENGTH_UTF8 = 14

        /** Giới hạn điểm phân bổ ban đầu*/
        const val MAX_INITIAL_STAT_POINTS = 6

        /** Giới hạn kiểu tóc (chưa chuyển nghề, nguồn: Role.hairStyleCount[0])*/
        const val MAX_HAIR_MALE = 5    // Nam: 0-4
        const val MAX_HAIR_FEMALE = 7  // Nữ: 0-6

        /** Mã kết quả tên*/
        const val NAME_RESULT_AVAILABLE = 0
        const val NAME_RESULT_DUPLICATE = 1
        const val NAME_RESULT_ILLEGAL = 2
        const val NAME_RESULT_OTHER_ERROR = 3
    }
}


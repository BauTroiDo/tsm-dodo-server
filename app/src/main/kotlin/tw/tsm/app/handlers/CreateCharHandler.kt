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
 * 創角處理器（mainKind = 9）
 *
 * 子 opcode：
 * - C:009-001 建立新角色
 * - C:009-002 檢查名字是否可用
 *
 * 📖 協定來源：protocal.lua protocolTable[9]
 * 📖 封包組裝：UICreateRole.lua OnClick_Next
 */
class CreateCharHandler(
    private val characterRepository: CharacterRepository,
    private val responseSender: ResponseSender
) : PacketHandler {

    override val mainKind: Int = Opcodes.CREATE_CHAR

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        // 必須已登入
        if (session.state != SessionState.LOGGED_IN) {
            logger.warn { "未登入就嘗試創角：state=${session.state}" }
            return
        }

        when (subKind) {
            1 -> handleCreateChar(session, data)
            2 -> handleCheckName(session, data)
            else -> logger.warn { "未處理的創角子協定：subKind=$subKind" }
        }
    }

    /**
     * 處理建立新角色 (C:009-001)
     *
     * 封包格式（UICreateRole.lua OnClick_Next 實際寫入順序）：
     * +姓名(ByteL+Big5) +性別(1) +0(1) +髮型(1) +0(1)
     * +色盤一(Int32) +色盤二(Int32) +屬性(1)
     * +六圍(1)*6 +隱私旗標(1)
     *
     * ⚠️ 注意：實際封包順序是「姓名優先」，
     *    與 protocal.lua 註解的「性別優先」不同！
     */
    private suspend fun handleCreateChar(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)

        // 姓名（1 byte 長度 + Big5 字串）
        val name = reader.readUtf8WithByteLen()

        // 驗證名稱
        if (!isValidName(name)) {
            logger.warn { "角色名稱非法：'$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_ILLEGAL)
            return
        }

        // 性別（1=男, 2=女）
        val sex = reader.readUByte()
        reader.readUByte()    // 填充 0（臉圖，固定 0）

        // 髮型
        val hair = reader.readUByte()
        reader.readUByte()    // 填充 0（體型，固定 0）

        // 色盤（Int32 LE）
        val color1 = reader.readIntLE()
        val color2 = reader.readIntLE()

        // 五行屬性（1-4）
        val element = reader.readUByte()

        // 六圍配點（對照 UICreateRole.lua attributeValue[1~6] 順序）
        val int_ = reader.readUByte()   // [1] 知力
        val str = reader.readUByte()    // [2] 攻擊力
        val vit = reader.readUByte()    // [3] 防禦力
        val spr = reader.readUByte()    // [4] Hp幅度
        val men = reader.readUByte()    // [5] Sp幅度
        val dex = reader.readUByte()    // [6] 敏捷

        // 隱私旗標（忽略）
        if (reader.isReadable) {
            reader.readUByte()
        }

        // ── 伺服器端驗證 ──

        // 性別驗證（1=男, 2=女）
        if (sex !in 1..2) {
            logger.warn { "無效性別：$sex" }
            return
        }

        // 髮型驗證（未轉：男 0-4, 女 0-6）
        val maxHair = if (sex == 1) MAX_HAIR_MALE else MAX_HAIR_FEMALE
        if (hair !in 0 until maxHair) {
            logger.warn { "無效髮型：hair=$hair sex=$sex (上限=$maxHair)" }
            return
        }

        // 五行驗證（1-4，客戶端 UI 只有 4 個選項）
        if (element !in 1..4) {
            logger.warn { "無效屬性：$element" }
            return
        }

        // 六圍配點總和必須剛好等於 6（必須分配完所有初始點數）
        val totalStats = str + dex + vit + int_ + men + spr
        if (totalStats != MAX_INITIAL_STAT_POINTS) {
            logger.warn { "配點不正確：$totalStats ≠ $MAX_INITIAL_STAT_POINTS" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_OTHER_ERROR)
            return
        }

        // 每個屬性不能超過 6
        if (listOf(str, dex, vit, int_, men, spr).any { it > MAX_INITIAL_STAT_POINTS }) {
            logger.warn { "單項配點超過上限" }
            return
        }

        // 名稱重複檢查
        if (characterRepository.isNameTaken(name)) {
            logger.info { "角色名稱重複：'$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_DUPLICATE)
            return
        }

        // ── 建立角色 ──
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

            logger.info { "角色建立成功：'$name' (id=$characterId) 帳號=${session.accountId}" }

            // S:009-001 創角成功
            responseSender.sendCreateCharSuccess(session)
        } catch (e: Exception) {
            logger.error(e) { "角色建立失敗：'$name'" }
            responseSender.sendCheckNameResult(session, NAME_RESULT_OTHER_ERROR)
        }
    }

    /**
     * 處理檢查名字 (C:009-002)
     *
     * 封包格式：+名字(ByteL+Big5)
     */
    private suspend fun handleCheckName(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val name = reader.readUtf8WithByteLen()

        val result = when {
            !isValidName(name) -> NAME_RESULT_ILLEGAL
            characterRepository.isNameTaken(name) -> NAME_RESULT_DUPLICATE
            else -> NAME_RESULT_AVAILABLE
        }

        logger.info { "檢查名字：'$name' → result=$result" }
        responseSender.sendCheckNameResult(session, result)
    }

    /**
     * 驗證角色名稱是否合法
     *
     * 規則（來源：UICreateRole.lua + string.lua CheckName）：
     * - 不得為空
     * - Big5 或 UTF-8 跨碼後不超過限制 bytes
     * - 不得包含空白字元
     *
     * ⚠️ 原 Big5-only 邏輯已放寬，改為支援 UTF-8（兼容英文及多語系名稱）。
     *    原因：客戶端以 Big5 跨碼，但 Big5 無法表示中文字元以外的 Unicode
     *    （如越南文帶調符號），導致名稱被拒。改用 UTF-8 fallback 兼容所有語言。
     */
    private fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false

        // 不允許空白
        if (name.contains(' ') || name.contains('\t')) return false

        // 嘗試 Big5 跨碼（原版客戶端兼容）
        val big5Bytes = try {
            name.toByteArray(charset("Big5"))
        } catch (_: Exception) {
            null
        }

        // 若 Big5 可編碼且長度合法，直接通過
        if (big5Bytes != null && big5Bytes.isNotEmpty() && big5Bytes.size <= MAX_NAME_LENGTH_BIG5) {
            return true
        }

        // Big5 跨碼失敗（含非 Big5 字元），改用 UTF-8 長度檢查
        val utf8Bytes = name.toByteArray(Charsets.UTF_8)
        if (utf8Bytes.isEmpty() || utf8Bytes.size > MAX_NAME_LENGTH_UTF8) return false

        return true
    }

    companion object {
        /** 名字 Big5 最大長度 */
        const val MAX_NAME_LENGTH_BIG5 = 14

        /** 名字 UTF-8 最大長度（UTF-8 每字最多 4 bytes，允許較長英文名） */
        const val MAX_NAME_LENGTH_UTF8 = 14

        /** 初始配點上限 */
        const val MAX_INITIAL_STAT_POINTS = 6

        /** 髮型上限（未轉職，來源：Role.hairStyleCount[0]） */
        const val MAX_HAIR_MALE = 5    // 男：0-4
        const val MAX_HAIR_FEMALE = 7  // 女：0-6

        /** 名字結果碼 */
        const val NAME_RESULT_AVAILABLE = 0
        const val NAME_RESULT_DUPLICATE = 1
        const val NAME_RESULT_ILLEGAL = 2
        const val NAME_RESULT_OTHER_ERROR = 3
    }
}


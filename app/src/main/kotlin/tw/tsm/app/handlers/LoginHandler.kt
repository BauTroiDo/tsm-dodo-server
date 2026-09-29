package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.CLIENT_VERSION
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.LoginKind
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.loadFromRecord
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.AuthResult
import tw.tsm.gamelogic.systems.AuthSystem
import tw.tsm.gamelogic.systems.LoginService
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader
import tw.tsm.protocol.PacketWriter

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý đăng nhập (mainKind = 1)
 *
 * opcode con:
 * - C:001-000 yêu cầu đăng nhập
 * - C:001-001 kết nối lại
 */
class LoginHandler(
    private val authSystem: AuthSystem,
    private val loginService: LoginService,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager
) : PacketHandler {
    override val mainKind: Int = Opcodes.LOGIN

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            0 -> handleLogin(session, data)
            1 -> handleReconnect(session, data)
            16 -> handleServerTimeQuery(session)
            else -> logger.warn { "Sub-protocol đăng nhập chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * Xử lý yêu cầu đăng nhập (C:001-000)
     *
     * Định dạng gói tin (từ Network.lua OnConnect):
     * +Version(UInt16) +ServerId(UInt16) +ConnectCode(UInt32) +LoginKind(Byte)
     * LoginKind=1(tài khoản mật khẩu): +L(Byte)+Account(Big5) +L(Byte)+Password(Big5)
     * LoginKind=2(khách vãng lai): (không có trường bổ sung)
     * LoginKind=3/7(FB/Apple)：+L(UInt16)+Account +L(UInt16)+Token
     * LoginKind=255(Vô Giới): +L(Byte)+Account +L(Byte)+Password +RoleId(Int64) +SN(UInt32)
     */
    private suspend fun handleLogin(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)

        val version = reader.readUShortLE()
        val serverId = reader.readUShortLE()
        val connectCode = reader.readUIntLE()
        val loginKindCode = reader.readUByte()

        // kiểm tra phiên bản
        if (version != CLIENT_VERSION) {
            logger.warn { "Lệch phiên bản: client=$version server=$CLIENT_VERSION" }
            responseSender.sendLoginResult(session, resultCode = RESULT_VERSION_MISMATCH)
            return
        }

        val loginKind = LoginKind.fromCode(loginKindCode)
        if (loginKind == null) {
            logger.warn { "Cách đăng nhập không xác định: $loginKindCode" }
            responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            return
        }

        session.serverId = serverId
        session.loginKind = loginKind
        session.state = SessionState.AUTHENTICATING

        logger.info { "Yêu cầu đăng nhập: serverId=$serverId kind=$loginKind connectCode=$connectCode" }

        when (loginKind) {
            LoginKind.ACC_PWD -> {
                val account = reader.readBig5WithByteLen()
                val password = reader.readBig5WithByteLen()
                handleAccountLogin(session, account, password)
            }
            LoginKind.GUEST -> {
                handleGuestLogin(session)
            }
            LoginKind.FACEBOOK, LoginKind.APPLE -> {
                val userId = reader.readBig5WithShortLen()   // tiền tố độ dài 2B
                val token = reader.readBig5WithShortLen()
                logger.info { "Đăng nhập bên thứ ba: kind=$loginKind userId=$userId" }
                // TODO: xác thực đăng nhập bên thứ ba
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
            LoginKind.UNBOUNDED -> {
                val account = reader.readBig5WithByteLen()
                val password = reader.readBig5WithByteLen()
                val roleId = reader.readULongLE()
                val sn = reader.readUIntLE()
                logger.info { "Đăng nhập Vô Giới: account=$account roleId=$roleId sn=$sn" }
                // TODO: logic server Vô Giới
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
            LoginKind.GOOGLE -> {
                logger.info { "Đăng nhập Google (client chưa triển khai)" }
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
        }
    }

    /**
     * Đăng nhập tài khoản mật khẩu
     */
    private suspend fun handleAccountLogin(session: GameSession, account: String, password: String) {
        logger.info { "Đăng nhập tài khoản/mật khẩu: $account" }

        val result = authSystem.authenticate(account, password)
        when (result) {
            is AuthResult.Success -> {
                session.gmLevel = result.gmLevel
                completeLogin(session, result.accountId, account, password)
            }
            is AuthResult.NewAccount -> {
                completeLogin(session, result.accountId, account, password)
            }
            is AuthResult.WrongPassword -> {
                responseSender.sendLoginResult(session, resultCode = RESULT_WRONG_PASSWORD)
            }
            is AuthResult.Suspended -> {
                responseSender.sendLoginResult(session, resultCode = RESULT_SUSPENDED)
            }
        }
    }

    /**
     * Đăng nhập khách vãng lai
     */
    private suspend fun handleGuestLogin(session: GameSession) {
        logger.info { "Đăng nhập khách" }

        val result = authSystem.authenticateGuest()
        when (result) {
            is AuthResult.NewAccount -> {
                completeLogin(session, result.accountId, "Guest_${result.accountId}", "guest")
            }
            else -> {
                responseSender.sendLoginResult(session, resultCode = RESULT_SYSTEM_ERROR)
            }
        }
    }

    /**
     * Đăng nhập thành công — thiết lập session và phân luồng theo trạng thái nhân vật
     *
     * - đã có nhân vật: S:001-002 → chạy thẳng trình tự vào game bằng nhân vật (bỏ qua UICreateRole)
     * - chưa có nhân vật: S:001-002 + S:001-003 → mở UICreateRole
     */
    private suspend fun completeLogin(session: GameSession, accountId: Long, account: String, password: String) {
        session.accountId = accountId
        session.account = account
        session.dbId = 1       // chế độ một server
        session.gsId = 1
        session.playerId = accountId

        // đá session cũ cùng tài khoản (chặn người chơi ảo)
        val oldSession = worldManager.getPlayerById(accountId)
        if (oldSession != null && oldSession !== session) {
            logger.warn { "Tài khoản $account đã trực tuyến, đá kết nối cũ" }
            worldManager.removePlayer(oldSession)
            oldSession.close()
        }

        session.connectCode = System.currentTimeMillis()
        session.state = SessionState.LOGGED_IN

        val serverTime = LoginService.currentServerTime()

        // S:001-002 kết quả đăng nhập (thành công) — luôn gửi
        responseSender.sendLoginResult(
            session = session,
            resultCode = 0,
            dbId = session.dbId,
            gsId = session.gsId,
            playerId = session.playerId,
            serverTime = serverTime,
            account = account,
            password = "",  // không trả về mật khẩu dạng plaintext
            accountKind = 0
        )

        // tra xem đã có nhân vật chưa
        val characters = characterRepository.findByAccountId(accountId)
        if (characters.isNotEmpty()) {
            // đã có nhân vật: vào game luôn (bỏ qua S:001-003 / UICreateRole)
            val record = characters.first()
            session.loadFromRecord(record)

            logger.info { "Đã có nhân vật, vào thẳng: '${record.name}' (id=${record.id}) tài khoản=$account" }

            loginService.executeCharacterEnterSequence(
                session = session,
                playerId = session.playerId,
                record = record,
                serverId = session.serverId,
                connectCode = session.connectCode
            )
            session.loginFinished = true
        } else {
            // chưa có nhân vật: gửi S:001-003 để mở UI tạo nhân vật
            responseSender.sendLoginSuccess(session, needChangePassword = false)
            logger.info { "Tài khoản mới, mở tạo nhân vật: $account (id=$accountId)" }
        }
    }

    /**
     * C:001-016 <tra thời gian Server>
     *
     * DVT client opens time-sensitive UIs (UIJiugongge/UIActivity/BattlePass/...)
     * by first requesting the current server clock. protocal.lua[1][16] then
     * feeds this value into CGTimer.SetServerTime().
     */
    private suspend fun handleServerTimeQuery(session: GameSession) {
        // UIJiugongge.OnOpen() sends C:001-016 and immediately starts its timer.
        // Put S:077 on the wire first, then update CGTimer with S:001-016, and finally
        // re-push S:091-001 so an UI that already InvaildSet() can register UpdateTime again.
        loginService.refreshDailyActivityTime(session)
        session.send(
            PacketWriter()
                .writeByte(Opcodes.LOGIN)
                .writeByte(16)
                .writeByte(0)
                .writeDoubleLE(LoginService.currentServerTime())
                .toByteArray()
        )
        loginService.refreshDailySnapshot(session)
    }

    /**
     * Xử lý kết nối lại (C:001-001)
     */
    private suspend fun handleReconnect(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val connectCode = reader.readUIntLE()
        logger.info { "Yêu cầu kết nối lại: connectCode=$connectCode" }
        // TODO: cài đặt logic kết nối lại
    }

    companion object {
        const val RESULT_SUCCESS = 0
        const val RESULT_WRONG_PASSWORD = 1
        const val RESULT_DB_ERROR = 2
        const val RESULT_SERVER_ERROR = 3
        const val RESULT_SUSPENDED = 10
        const val RESULT_VERSION_MISMATCH = 6
        const val RESULT_LOGIN_KIND_ERROR = 8
        const val RESULT_SYSTEM_ERROR = 17
    }
}

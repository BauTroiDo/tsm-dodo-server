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

private val logger = KotlinLogging.logger {}

/**
 * 登入處理器（mainKind = 1）
 *
 * 子 opcode：
 * - C:001-000 登入請求
 * - C:001-001 重連
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
            else -> logger.warn { "未處理的登入子協定：subKind=$subKind" }
        }
    }

    /**
     * 處理登入請求 (C:001-000)
     *
     * 封包格式（來自 Network.lua OnConnect）：
     * +Version(UInt16) +ServerId(UInt16) +ConnectCode(UInt32) +LoginKind(Byte)
     * LoginKind=1(帳密)：+L(Byte)+Account(Big5) +L(Byte)+Password(Big5)
     * LoginKind=2(遊客)：（無額外欄位）
     * LoginKind=3/7(FB/Apple)：+L(UInt16)+Account +L(UInt16)+Token
     * LoginKind=255(無界)：+L(Byte)+Account +L(Byte)+Password +RoleId(Int64) +SN(UInt32)
     */
    private suspend fun handleLogin(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)

        val version = reader.readUShortLE()
        val serverId = reader.readUShortLE()
        val connectCode = reader.readUIntLE()
        val loginKindCode = reader.readUByte()

        // 版本檢查
        if (version != CLIENT_VERSION) {
            logger.warn { "版本不符：客戶端=$version 伺服器=$CLIENT_VERSION" }
            responseSender.sendLoginResult(session, resultCode = RESULT_VERSION_MISMATCH)
            return
        }

        val loginKind = LoginKind.fromCode(loginKindCode)
        if (loginKind == null) {
            logger.warn { "未知登入方式：$loginKindCode" }
            responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            return
        }

        session.serverId = serverId
        session.loginKind = loginKind
        session.state = SessionState.AUTHENTICATING

        logger.info { "登入請求：serverId=$serverId kind=$loginKind connectCode=$connectCode" }

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
                val userId = reader.readBig5WithShortLen()   // 2B 長度前綴
                val token = reader.readBig5WithShortLen()
                logger.info { "第三方登入：kind=$loginKind userId=$userId" }
                // TODO：第三方登入驗證
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
            LoginKind.UNBOUNDED -> {
                val account = reader.readBig5WithByteLen()
                val password = reader.readBig5WithByteLen()
                val roleId = reader.readULongLE()
                val sn = reader.readUIntLE()
                logger.info { "無界登入：account=$account roleId=$roleId sn=$sn" }
                // TODO：無界伺服器邏輯
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
            LoginKind.GOOGLE -> {
                logger.info { "Google 登入（客戶端未實作）" }
                responseSender.sendLoginResult(session, resultCode = RESULT_LOGIN_KIND_ERROR)
            }
        }
    }

    /**
     * 帳密登入
     */
    private suspend fun handleAccountLogin(session: GameSession, account: String, password: String) {
        logger.info { "帳密登入：$account" }

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
     * 遊客登入
     */
    private suspend fun handleGuestLogin(session: GameSession) {
        logger.info { "遊客登入" }

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
     * 登入成功 — 設定 session 並根據角色狀態分流
     *
     * - 已有角色：S:001-002 → 直接執行角色進入序列（跳過 UICreateRole）
     * - 無角色：S:001-002 + S:001-003 → 開啟 UICreateRole
     */
    private suspend fun completeLogin(session: GameSession, accountId: Long, account: String, password: String) {
        session.accountId = accountId
        session.account = account
        session.dbId = 1       // 單服模式
        session.gsId = 1
        session.playerId = accountId

        // 踢掉同帳號舊 session（防止幽靈玩家）
        val oldSession = worldManager.getPlayerById(accountId)
        if (oldSession != null && oldSession !== session) {
            logger.warn { "帳號 $account 已在線，踢掉舊連線" }
            worldManager.removePlayer(oldSession)
            oldSession.close()
        }

        session.connectCode = System.currentTimeMillis()
        session.state = SessionState.LOGGED_IN

        val serverTime = LoginService.currentServerTime()

        // S:001-002 登入結果（成功）— 固定發送
        responseSender.sendLoginResult(
            session = session,
            resultCode = 0,
            dbId = session.dbId,
            gsId = session.gsId,
            playerId = session.playerId,
            serverTime = serverTime,
            account = account,
            password = "",  // 不回傳明文密碼
            accountKind = 0
        )

        // 查詢是否已有角色
        val characters = characterRepository.findByAccountId(accountId)
        if (characters.isNotEmpty()) {
            // 已有角色：直接進入遊戲（跳過 S:001-003 / UICreateRole）
            val record = characters.first()
            session.loadFromRecord(record)

            logger.info { "已有角色，直接進入：'${record.name}' (id=${record.id}) 帳號=$account" }

            loginService.executeCharacterEnterSequence(
                session = session,
                playerId = session.playerId,
                record = record,
                serverId = session.serverId,
                connectCode = session.connectCode
            )
            session.loginFinished = true
        } else {
            // 無角色：發送 S:001-003 開啟創角 UI
            responseSender.sendLoginSuccess(session, needChangePassword = false)
            logger.info { "新帳號，開啟創角：$account (id=$accountId)" }
        }
    }

    /**
     * 處理重連 (C:001-001)
     */
    private suspend fun handleReconnect(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val connectCode = reader.readUIntLE()
        logger.info { "重連請求：connectCode=$connectCode" }
        // TODO：實作重連邏輯
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

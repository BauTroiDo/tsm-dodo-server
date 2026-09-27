package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.loadFromRecord
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.LoginService

private val logger = KotlinLogging.logger {}

/**
 * 選角處理器（mainKind = 3）
 *
 * 客戶端在兩種情境下發送 C:003：
 * 1. 創角成功（S:009-001）後自動發送
 * 2. 返回玩家在 UICreateRole 點擊進入遊戲
 *
 * 收到 C:003 後，載入角色資料並執行角色進入序列。
 *
 * 📖 協定來源：protocal.lua protocolTable[9][1] → Network.Send(3)
 */
class SelectCharHandler(
    private val characterRepository: CharacterRepository,
    private val loginService: LoginService
) : PacketHandler {

    override val mainKind: Int = Opcodes.CHARACTER

    /**
     * 處理選角請求 (C:003)
     *
     * C:003 是空封包，無 subKind 和資料。
     * 手遊版每帳號一個角色，直接載入第一個。
     */
    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        // 必須已登入
        if (session.state != SessionState.LOGGED_IN) {
            logger.warn { "非法選角：state=${session.state}" }
            return
        }

        // 載入帳號下的角色
        val characters = characterRepository.findByAccountId(session.accountId)
        if (characters.isEmpty()) {
            logger.warn { "帳號 ${session.accountId} 沒有角色，無法選角" }
            return
        }

        // 手遊版取第一個角色
        val record = characters.first()

        // 設定 session 角色資訊 + 外觀（供 S:003-000 他人可見）
        session.loadFromRecord(record)

        logger.info { "選角成功：'${record.name}' (id=${record.id}) 帳號=${session.accountId}" }

        // 階段二：角色進入遊戲世界
        loginService.executeCharacterEnterSequence(
            session = session,
            playerId = session.playerId,
            record = record,
            serverId = session.serverId,
            connectCode = session.connectCode
        )

        session.loginFinished = true
    }
}

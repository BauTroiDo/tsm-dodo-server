package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.loadFromRecord
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.LoginService
import tw.tsm.gamelogic.systems.PokeSystem

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý chọn nhân vật (mainKind = 3)
 *
 * Client gửi C:003 trong hai trường hợp:
 * 1. Tự động gửi sau khi tạo nhân vật thành công (S:009-001)
 * 2. Người chơi quay lại và bấm vào trò chơi trong UICreateRole
 *
 * Sau khi nhận C:003, nạp dữ liệu nhân vật rồi chạy trình tự nhân vật gia nhập.
 *
 * 📖 Nguồn giao thức: protocal.lua protocolTable[9][1] → Network.Send(3)
 */
class SelectCharHandler(
    private val characterRepository: CharacterRepository,
    private val loginService: LoginService,
    private val pokeSystem: PokeSystem? = null,
) : PacketHandler {

    override val mainKind: Int = Opcodes.CHARACTER

    /**
     * Xử lý yêu cầu chọn nhân vật (C:003)
     *
     * C:003 là gói tin trống, không có subKind và dữ liệu.
     * Bản mobile mỗi tài khoản một nhân vật, nạp thẳng nhân vật đầu tiên.
     */
    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        // Phải đã đăng nhập
        if (session.state != SessionState.LOGGED_IN) {
            logger.warn { "Chọn nhân vật không hợp lệ: state=${session.state}" }
            return
        }

        // Nạp các nhân vật thuộc tài khoản
        val characters = characterRepository.findByAccountId(session.accountId)
        if (characters.isEmpty()) {
            logger.warn { "Tài khoản ${session.accountId} không có nhân vật, không thể chọn" }
            return
        }

        // Bản mobile lấy nhân vật đầu tiên
        val record = characters.first()

        // Thiết lập thông tin nhân vật + ngoại hình trong session (để S:003-000 người khác nhìn thấy)
        session.loadFromRecord(record)

        logger.info { "Chọn nhân vật thành công: '${record.name}' (id=${record.id}) tài khoản=${session.accountId}" }

        // Giai đoạn hai: nhân vật gia nhập thế giới trò chơi
        loginService.executeCharacterEnterSequence(
            session = session,
            playerId = session.playerId,
            record = record,
            serverId = session.serverId,
            connectCode = session.connectCode
        )

        pokeSystem?.let { system ->
            val state = system.open(record.id)
            PokePackets.sendSnapshot(session, system, state)
        }

        session.loginFinished = true
    }
}

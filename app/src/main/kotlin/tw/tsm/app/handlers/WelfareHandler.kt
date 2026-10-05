package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.protocol.PacketWriter

private val welfareLogger = KotlinLogging.logger {}

/**
 * DVT phúc lợi / nút "Quà" trên HUD (protocolTable[87]).
 *
 * Function.Use(EFunction.Welfare) không mở UI ngay. Client gửi C:087-003 và chỉ
 * mở UILoginAward sau khi nhận S:087-003 trong UILoginAward.ReciveExpData().
 *
 * Server offline hiện chưa triển khai tích luỹ EXP offline, vì vậy trả snapshot
 * hợp lệ rỗng (count=0). Không tạo giả multiplier hay chi phí.
 */
class WelfareHandler : PacketHandler {
    override val mainKind: Int = Opcodes.WELFARE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            3 -> handleOfflineExpData(session, data)
            else -> welfareLogger.warn {
                "Welfare chưa hỗ trợ subKind=$subKind (${data.size} bytes)"
            }
        }
    }

    private fun handleOfflineExpData(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty()) {
            welfareLogger.warn {
                "C:087-003 phải không có payload, nhận ${data.size} bytes"
            }
            return
        }

        // S:087-003 = count(U8) + count * [rate(U16) + money(U32)]
        // count=0 là snapshot rỗng hợp lệ và đủ để client mở UILoginAward.
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.WELFARE)
                .writeByte(3)
                .writeByte(0)
                .writeByte(0)
                .toByteArray()
        )
    }
}

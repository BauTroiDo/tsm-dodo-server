package tw.tsm.network

import io.netty.channel.Channel
import tw.tsm.domain.enums.LoginKind
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.protocol.FrameEncoder

/**
 * Hiện thực GameSession — Tương ứng với mỗi kết nối TCP
 *
 * Nắm giữ tham chiếu tới Netty Channel, chịu trách nhiệm gửi gói tin (packet)
 */
class GameSessionImpl(private val channel: Channel) : GameSession {

    // ── SessionIdentity ──
    override var account: String = ""
    override var loginKind: LoginKind = LoginKind.ACC_PWD
    override var accountId: Long = 0
    override var serverId: Int = 0
    override var dbId: Int = 0
    override var gsId: Int = 0
    override var connectCode: Long = 0
    override var gmLevel: Int = 0

    // ── SessionStatus ──
    override var state: SessionState = SessionState.CONNECTED
    override var loginFinished: Boolean = false

    // ── SessionCharacter ──
    override var playerId: Long = 0
    override var roleId: Int = 0
    override var characterName: String = ""

    // ── SessionWorld ──
    override var mapId: Int = 0
    override var x: Int = 0
    override var y: Int = 0
    override var direction: Int = 0
    override var lastPositionSave: Long = 0L
    override var lastDoorWarpTime: Long = 0L
    override var lastDoorWarpMapId: Int = 0

    // ── SessionAppearance ──
    override var sex: Int = 0
    override var element: Int = 0
    override var level: Int = 0
    override var turn: Int = 0
    override var career: Int = 0
    override var turn3Element: Int = 0
    override var hair: Int = 0
    override var face: Int = 0
    override var color1: Int = 0
    override var color2: Int = 0
    override var equipItemIds: IntArray = intArrayOf()
    override var titleId: Int = 0

    // ── SessionBattle ──
    override var battleId: Long = 0

    // ── SessionPk ──
    override var pkEnabled: Boolean = false
    override var joinEnabled: Boolean = false

    // ── SessionMount ──
    override var mountNpcId: Int = 0
    override var mountLevel: Int = 1

    // ── SessionSender ──

    /**
     * Gửi gói tin đến Client
     *
     * @param data Dữ liệu gói tin hoàn chỉnh (Bao gồm mainKind+subKind+compress header)
     *             FrameEncoder sẽ tự động thêm tiền tố độ dài 4 bytes
     */
    override fun send(data: ByteArray) {
        if (channel.isActive) {
            channel.writeAndFlush(data)
        }
    }

    /** Đóng kết nối */
    override fun close() {
        channel.close()
    }

    /** Lấy IP của Client */
    val remoteAddress: String
        get() = channel.remoteAddress()?.toString() ?: "unknown"

    override fun toString(): String =
        "Session(account=$account, playerId=$playerId, state=$state)"
}
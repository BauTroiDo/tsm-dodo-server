package tw.tsm.domain.interfaces

/**
 * Interface xử lý gói tin
 *
 * Mỗi mainKind tương ứng một triển khai PacketHandler.
 * Bên trong dùng `when(subKind)` để phân phối cấp hai.
 */
interface PacketHandler {
    /** mainKind tương ứng của handler này*/
    val mainKind: Int

    /**
     * Xử lý gói tin nhận được
     *
     * @param session phiên kết nối của bên gửi
     * @param subKind opcode con
     * @param data dữ liệu gói tin (không gồm 3 bytes header)
     */
    suspend fun handle(session: GameSession, subKind: Int, data: ByteArray)
}

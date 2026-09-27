package tw.tsm.domain.interfaces

/**
 * 封包處理器介面
 *
 * 每個 mainKind 對應一個 PacketHandler 實作。
 * 內部用 `when(subKind)` 做二級分發。
 */
interface PacketHandler {
    /** 此 handler 對應的 mainKind */
    val mainKind: Int

    /**
     * 處理收到的封包
     *
     * @param session 發送方的連線階段
     * @param subKind 子 opcode
     * @param data 封包資料（不含 header 的 3 bytes）
     */
    suspend fun handle(session: GameSession, subKind: Int, data: ByteArray)
}

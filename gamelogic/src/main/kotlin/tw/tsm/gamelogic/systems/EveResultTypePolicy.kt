package tw.tsm.gamelogic.systems

/**
 * Chiến lược phân luồng resultType của Eve.
 *
 * Client `EventHandler.lua` chỉ định nghĩa từ 0..6.
 * 7 là quy trình đặc biệt phía máy chủ (server); 8, 9, 10 xuất hiện trong Eve.emg, nhưng client không có handler,
 * nếu gửi trực tiếp S:020-001 thì sẽ chỉ bị client bỏ qua, do đó phía server cần chủ động bỏ qua và tiếp tục tiến hành sự kiện.
 */
object EveResultTypePolicy {
    private val clientHandlerTypes = setOf(0, 1, 2, 3, 4, 5, 6)
    private val silentSkipTypes = setOf(8, 9, 10)

    fun hasClientEventHandler(resultType: Int): Boolean =
        resultType in clientHandlerTypes

    fun isServerOnly(resultType: Int): Boolean =
        resultType == 7

    fun shouldSkipWithoutClientHandler(resultType: Int): Boolean =
        resultType in silentSkipTypes
}
package tw.tsm.domain.data

/**
 * UI 介面消費（介消）定義 — 對應 `UISell_C.dat` / `UISellData.lua`
 *
 * 介消 = 客戶端各 UI 內建的小額消費入口（擴充格數、購買次數、立即完成等），
 * 由 `UISell.Launch(sellId)` 觸發 C:084-001 查詢 → C:084-002 使用。
 *
 * @property rolecountId 限購次數 RoleCount 索引（0 = 無限購顯示）
 * @property moneyStringId 金錢消費確認字串 ID（TextData，0 = 無）
 * @property itemStringId 道具消費確認字串 ID（TextData，0 = 無 → 不支援道具路徑）
 * @property customStringId 自訂失敗訊息字串 ID（失敗原因 11 用，0 = 無）
 */
data class UISellDef(
    val rolecountId: Int,
    val moneyStringId: Long,
    val itemStringId: Long,
    val customStringId: Long
)

/**
 * UI 介面消費公式定義 — 對應 `UISell_Formula_C.dat`
 *
 * 此檔客戶端不讀取（DataManager 無 ReadData），為伺服器端費用表。
 * 格式（逆向自二進位）：count(4B) + 每筆 29B = id(U16) + kind(U8) + value(U32) + params(11×U16)。
 *
 * 已確認語意（對照 UI 字串與客戶端行為）：
 * - sellId=7 記憶傳送：kind=1, value=2 → 無傳送符時花費 2 元寶前往
 * - sellId=8 記憶傳送點擴充：kind=6, value=59, params=[59,59,59,59] → 第 1~5 格各 59 元寶
 * - kind=6 的共同特徵為「遞進價格表」（value=第 1 次價格，params=後續價格）
 *
 * TODO：其餘 kind（12/14/15/16/17/18/19/20）語意未完全確認，待各 sellId 實作時逐一驗證
 *
 * @property kind 公式種類（語意見上）
 * @property value 基礎數值（多數情況 = 首次價格，元寶）
 * @property params 參數列表（11 欄，遞進價格或公式參數）
 */
data class UISellFormulaDef(
    val kind: Int,
    val value: Long,
    val params: List<Int>
)

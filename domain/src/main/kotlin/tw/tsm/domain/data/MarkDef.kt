package tw.tsm.domain.data

/**
 * 任務標記定義 — 對應 Mark_C.dat 的 MarkData.ReadInfo()
 *
 * 來源：`Mark_C.dat`
 * 伺服器主要需要 [bitId] 和 [kind] 欄位；name/description 為日誌用途。
 *
 * @param name        任務名稱（UTF-16LE）
 * @param kind        類別（0:無 1:主線 2:支線 3:指引 4:單人副本 5:多人副本 6:其他 7:每日清 8:日清不秀）
 * @param bitId       完成標記位元索引（0 = 無完成標記）
 * @param gainWay     獲取方式（0:觸發者 1:觸發者及隊員）
 * @param description 說明（UTF-16LE）
 */
data class MarkDef(
    val name: String,
    val kind: Int,
    val bitId: Int,
    val gainWay: Int,
    val description: String
)

/**
 * 減少任務步驟的結果 — [tw.tsm.gamelogic.systems.QuestSystem.decMissionStep] 回傳
 *
 * 用於通知呼叫者任務是否因步驟歸零而完成，以及對應的完成標記 bitId。
 *
 * @param completed 任務是否完成（步驟歸零）
 * @param bitId     完成標記 bitId（0 = 無需設定標記）
 */
data class DecMissionResult(
    val completed: Boolean,
    val bitId: Int
)

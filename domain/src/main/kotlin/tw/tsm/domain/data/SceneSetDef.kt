package tw.tsm.domain.data

/**
 * 場景設定定義 — 對應 `SceneData.ReadSceneSetData(reader)`
 *
 * 來源：`SceneSet_C.dat`
 */
data class SceneSetDef(
    /** 場景名稱 ID（string.Get 對照用） */
    val nameId: Long,
    /** 副本種類（0=無, 1=靜態, 2=動態） */
    val kind: Int,
    /** 最大人數 */
    val maxPlayer: Int,
    /** 限制條件 1（不可丟棄/使用道具/卷軸/符咒/安全交易/交易武將/設攤/快遞） */
    val limit1: Int,
    /** 限制條件 2（不可 PK NPC/PK 玩家/參戰/騎馬/機關盒/跟隨武將/組隊/強制 PK） */
    val limit2: Int,
    /** 場景特效（0=無, 1=落葉, 2=雪花, 4=雷雨, 8=黑暗, 16=流星, 32=下雨） */
    val effect: Int,
    /** 建議最低等級 */
    val minLv: Int,
    /** 建議最高等級 */
    val maxLv: Int,
    /** 顯示條件（0=無限制, 1=不顯示自身武將, 2=不顯示他人武將, 4=不顯示其他玩家） */
    val viewSetting: Int,
    /** 子 ID */
    val subId: Int,
    /** 限制條件 3（0=無限制, 1=死亡不減少經驗值） */
    val limit3: Int
)

package tw.tsm.domain.data

/**
 * 派遣任務定義
 *
 * 來源：DispatchData.lua → DispatchData.New(reader)
 * .dat 格式：mainKind(1) + subKind(1) + minLv(1) + awardId(2) + expKind(1) + exp(4)
 *
 * @param minLv 等級下限
 * @param awardId 獎勵索引
 * @param expKind 經驗值種類（0=固定值, 1=千分比）
 * @param exp 派遣經驗值
 */
data class DispatchDef(
    val minLv: Int,
    val awardId: Int,
    val expKind: Int,
    val exp: Long
)

/**
 * 派遣獎勵加成效果定義
 *
 * 來源：DispatchData.lua → DispatchBonusData.New(reader)
 * .dat 格式：id(1) + conditionKind(1) + conditionValue(1) + effectIndex(1) + effectKind(1) + effectValue(2) + effectContent(4)
 *
 * @param conditionKind 條件種類（1=陣營, 2=屬性）
 * @param conditionValue 條件數值
 * @param effectIndex 效果影響獎勵索引
 * @param effectKind 效果種類
 * @param effectValue 效果數值
 * @param effectContent 效果說明字串 ID
 */
data class DispatchBonusDef(
    val conditionKind: Int,
    val conditionValue: Int,
    val effectIndex: Int,
    val effectKind: Int,
    val effectValue: Int,
    val effectContent: Long
)

/**
 * 派遣進行中的資料（DB 記錄 / 封包傳輸用）
 */
data class DispatchSaveData(
    val slotIndex: Int,
    val startTime: Double,
    val endTime: Double,
    val dispatchKind: Int,
    val innPetIndex: Int,
    val effectIndex1: Int = 0,
    val effectIndex2: Int = 0
)

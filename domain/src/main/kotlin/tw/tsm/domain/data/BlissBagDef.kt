package tw.tsm.domain.data

/**
 * 福袋獎勵項目
 *
 * 來源：BlissBagData.lua — BlissBagData.New()
 */
data class BlissBagItem(
    val itemId: Int,    // 獎勵物品 ID
    val count: Int,     // 數量
    val pr: Int,        // 機率權重
    val kind: Int       // 種類分組（開袋時按 kind 分組各抽一個）
)

/**
 * 福袋定義（key = 福袋物品 ID）
 *
 * 來源：DataManager.OnLoadBlissBagData — BlissBag_C.dat
 *
 * @param bagItemId 此福袋的物品 ID
 * @param kindCount 開袋時獲得幾種物品（= items 中最大的 kind 值）
 * @param items 可能掉落的獎勵列表
 */
data class BlissBagDef(
    val bagItemId: Int,
    val kindCount: Int,
    val items: List<BlissBagItem>
)

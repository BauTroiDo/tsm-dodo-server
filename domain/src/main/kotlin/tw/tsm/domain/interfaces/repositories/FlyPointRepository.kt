package tw.tsm.domain.interfaces.repositories

/**
 * 記憶傳送點記錄
 */
data class FlyPointRecord(
    val index: Int,
    val sceneId: Int,
    val x: Int,
    val y: Int
)

/**
 * 記憶傳送符資料存取介面
 */
interface FlyPointRepository {

    /**
     * 取得角色所有記憶傳送點
     *
     * @param charId 角色 ID
     * @return Map<索引(1-based), FlyPointRecord>
     */
    suspend fun getAll(charId: Long): Map<Int, FlyPointRecord>

    /**
     * 儲存/更新記憶傳送點
     *
     * @param charId 角色 ID
     * @param index 傳送點索引（1-based）
     * @param sceneId 場景 ID
     * @param x X 座標
     * @param y Y 座標
     */
    suspend fun save(charId: Long, index: Int, sceneId: Int, x: Int, y: Int)
}

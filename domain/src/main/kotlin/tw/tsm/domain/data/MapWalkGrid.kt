package tw.tsm.domain.data

/** Lua MapData.New (column-major), MapManager.IsObstacle/GetBlock/GetPosition. */
class MapWalkGrid(val width: Int, val height: Int, private val blocks: ByteArray) {
    init { require(width > 0 && height > 0 && blocks.size.toLong() == width.toLong() * height) }
    fun isWalkable(x: Int, y: Int): Boolean = x in 1..width && y in 1..height &&
        (blocks[(x - 1) * height + y - 1].toInt() and 5) == 0
    fun isWalkablePixel(x: Int, y: Int): Boolean =
        isWalkable(kotlin.math.ceil(x / 20.0).toInt(), kotlin.math.ceil(y / 20.0).toInt())
    fun nearestWalkable(x: Int, y: Int): Pair<Int, Int>? {
        val gx = kotlin.math.ceil(x / 20.0).toInt().coerceIn(1, width)
        val gy = kotlin.math.ceil(y / 20.0).toInt().coerceIn(1, height)
        // Same square-ring search/order as Lua GetStartEmptyBlock; cell centers avoid grid edges.
        for (distance in 0..maxOf(width, height)) {
            for (dx in -distance..distance) for (dy in -distance..distance) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != distance) continue
                if (isWalkable(gx + dx, gy + dy)) return (gx + dx) * 20 - 10 to (gy + dy) * 20 - 10
            }
        }
        return null
    }
}

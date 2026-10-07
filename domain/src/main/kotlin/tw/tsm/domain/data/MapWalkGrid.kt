package tw.tsm.domain.data

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil

/** Lua MapData.New (column-major), MapManager.IsObstacle/GetBlock/GetPosition. */
class MapWalkGrid(val width: Int, val height: Int, private val blocks: ByteArray) {
    init { require(width > 0 && height > 0 && blocks.size.toLong() == width.toLong() * height) }

    fun isWalkable(x: Int, y: Int): Boolean = x in 1..width && y in 1..height &&
        (blocks[(x - 1) * height + y - 1].toInt() and 5) == 0

    fun isWalkablePixel(x: Int, y: Int): Boolean =
        isWalkable(ceil(x / BLOCK_UNIT.toDouble()).toInt(), ceil(y / BLOCK_UNIT.toDouble()).toInt())

    fun nearestWalkable(x: Int, y: Int): Pair<Int, Int>? {
        val (gx, gy) = pixelToGrid(x, y)
        // Same square-ring search/order as Lua GetStartEmptyBlock; cell centers avoid grid edges.
        for (distance in 0..maxOf(width, height)) {
            for (dx in -distance..distance) for (dy in -distance..distance) {
                if (maxOf(abs(dx), abs(dy)) != distance) continue
                if (isWalkable(gx + dx, gy + dy)) return gridCenter(gx + dx, gy + dy)
            }
        }
        return null
    }

    /**
     * A* path trên Ground.mmg walk-grid. Trả về tâm pixel của từng ô sau ô bắt đầu.
     * Diagonal chỉ hợp lệ khi cả hai cạnh vuông góc đều đi được, tránh cắt xuyên góc tường.
     */
    fun isSegmentWalkablePixels(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        val (sx, sy) = pixelToGrid(startX, startY)
        val (ex, ey) = pixelToGrid(endX, endY)
        var x = sx
        var y = sy
        val dx = abs(ex - sx)
        val dy = abs(ey - sy)
        val stepX = if (sx < ex) 1 else if (sx > ex) -1 else 0
        val stepY = if (sy < ey) 1 else if (sy > ey) -1 else 0
        var err = dx - dy

        while (true) {
            if (!isWalkable(x, y)) return false
            if (x == ex && y == ey) return true

            val e2 = err * 2
            val oldX = x
            val oldY = y
            if (e2 > -dy) {
                err -= dy
                x += stepX
            }
            if (e2 < dx) {
                err += dx
                y += stepY
            }

            if (x != oldX && y != oldY) {
                if (!isWalkable(x, oldY) || !isWalkable(oldX, y)) return false
            }
        }
    }

    fun findPathPixels(
        startX: Int,
        startY: Int,
        targetX: Int,
        targetY: Int,
        maxVisited: Int = 12_000
    ): List<Pair<Int, Int>> {
        val safeStart = nearestWalkable(startX, startY) ?: return emptyList()
        val safeTarget = nearestWalkable(targetX, targetY) ?: return emptyList()
        val start = pixelToGrid(safeStart.first, safeStart.second)
        val target = pixelToGrid(safeTarget.first, safeTarget.second)
        if (start == target) return emptyList()

        data class Node(val x: Int, val y: Int, val g: Int, val f: Int)
        fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
        fun heuristic(x: Int, y: Int): Int {
            val dx = abs(target.first - x)
            val dy = abs(target.second - y)
            return 10 * (dx + dy) - 6 * minOf(dx, dy)
        }

        val open = PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.g })
        val bestG = HashMap<Long, Int>()
        val parent = HashMap<Long, Long>()
        val startKey = key(start.first, start.second)
        val targetKey = key(target.first, target.second)
        bestG[startKey] = 0
        open.add(Node(start.first, start.second, 0, heuristic(start.first, start.second)))

        val dirs = arrayOf(
            intArrayOf(1, 0, 10), intArrayOf(-1, 0, 10),
            intArrayOf(0, 1, 10), intArrayOf(0, -1, 10),
            intArrayOf(1, 1, 14), intArrayOf(1, -1, 14),
            intArrayOf(-1, 1, 14), intArrayOf(-1, -1, 14)
        )

        var visited = 0
        while (open.isNotEmpty() && visited++ < maxVisited) {
            val cur = open.poll()
            val curKey = key(cur.x, cur.y)
            if (cur.g != bestG[curKey]) continue
            if (curKey == targetKey) {
                val cells = ArrayList<Pair<Int, Int>>()
                var k = targetKey
                while (k != startKey) {
                    val gx = (k shr 32).toInt()
                    val gy = k.toInt()
                    cells.add(gridCenter(gx, gy))
                    k = parent[k] ?: return emptyList()
                }
                cells.reverse()
                return cells
            }

            for (d in dirs) {
                val nx = cur.x + d[0]
                val ny = cur.y + d[1]
                if (!isWalkable(nx, ny)) continue

                val diagonal = d[0] != 0 && d[1] != 0
                if (diagonal && (!isWalkable(cur.x + d[0], cur.y) || !isWalkable(cur.x, cur.y + d[1]))) {
                    continue
                }

                val ng = cur.g + d[2]
                val nk = key(nx, ny)
                if (ng >= (bestG[nk] ?: Int.MAX_VALUE)) continue
                bestG[nk] = ng
                parent[nk] = curKey
                open.add(Node(nx, ny, ng, ng + heuristic(nx, ny)))
            }
        }
        return emptyList()
    }

    private fun pixelToGrid(x: Int, y: Int): Pair<Int, Int> =
        ceil(x.coerceAtLeast(1) / BLOCK_UNIT.toDouble()).toInt().coerceIn(1, width) to
            ceil(y.coerceAtLeast(1) / BLOCK_UNIT.toDouble()).toInt().coerceIn(1, height)

    private fun gridCenter(gx: Int, gy: Int): Pair<Int, Int> =
        gx * BLOCK_UNIT - BLOCK_UNIT / 2 to gy * BLOCK_UNIT - BLOCK_UNIT / 2

    companion object {
        private const val BLOCK_UNIT = 20
    }
}

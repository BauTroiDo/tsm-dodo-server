package tw.tsm.tests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.tsm.domain.data.MapWalkGrid

class MapWalkGridPathTest {
    @Test
    fun `A star routes around blocked wall without crossing it`() {
        val width = 6
        val height = 6
        val blocks = ByteArray(width * height)

        fun block(gx: Int, gy: Int) {
            blocks[(gx - 1) * height + gy - 1] = 1
        }

        // Vertical wall at x=3 with one legal gap at y=6.
        for (gy in 1..5) block(3, gy)

        val grid = MapWalkGrid(width, height, blocks)
        val path = grid.findPathPixels(30, 30, 110, 30)

        assertTrue(path.isNotEmpty())
        assertTrue(path.all { (x, y) -> grid.isWalkablePixel(x, y) })
        assertTrue(path.any { (_, y) -> y >= 110 }, "path must go through the legal wall gap")
        assertFalse(path.any { (x, y) -> x in 41..60 && y < 110 }, "path must never cross blocked wall cells")
    }

    @Test
    fun `nearest walkable snaps blocked pixel to legal cell`() {
        val blocks = ByteArray(9)
        blocks[(2 - 1) * 3 + 2 - 1] = 1
        val grid = MapWalkGrid(3, 3, blocks)

        val snapped = grid.nearestWalkable(30, 30)
        assertTrue(snapped != null)
        assertTrue(grid.isWalkablePixel(snapped!!.first, snapped.second))
    }

    @Test
    fun `line of sight segment respects wall and open corridor`() {
        val width = 5
        val height = 5
        val blocks = ByteArray(width * height)
        fun block(gx: Int, gy: Int) {
            blocks[(gx - 1) * height + gy - 1] = 1
        }

        block(3, 2)
        block(3, 3)
        val grid = MapWalkGrid(width, height, blocks)

        assertFalse(grid.isSegmentWalkablePixels(10, 50, 90, 50), "straight shortcut through wall must fail")
        assertTrue(grid.isSegmentWalkablePixels(10, 90, 90, 90), "open corridor should allow smoothing")
    }

    @Test
    fun `diagonal cannot cut through blocked corner`() {
        val width = 3
        val height = 3
        val blocks = ByteArray(width * height)
        fun block(gx: Int, gy: Int) {
            blocks[(gx - 1) * height + gy - 1] = 1
        }

        block(2, 1)
        block(1, 2)
        val grid = MapWalkGrid(width, height, blocks)

        val path = grid.findPathPixels(10, 10, 50, 50)
        assertTrue(path.isEmpty(), "sealed corner must not be crossed diagonally")
    }
}

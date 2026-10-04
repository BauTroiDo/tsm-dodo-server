package tw.tsm.gamelogic.systems

import tw.tsm.domain.data.EveNpcPlacement

/** DVT Role.MapNpcAppear starts motionType=4 locally; hidden roles pause UpdateMove.
 * S:022-002 forcibly reveals a role and replaces its movement, so patrol must not
 * send that packet for these paths. Their phase differs per client/visibility.
 */
object NpcClientPath {
    fun ownsMovement(npc: EveNpcPlacement): Boolean =
        npc.isHide && npc.motionType == 4 && npc.traceRadius == 0 && npc.motionNodes.size > 2

    /** Bound clicks to the actual Eve route, including the initial spawn segment.
     * Lua uses nodes[1..count]; node[0] is the rectangle size, not a waypoint.
     */
    fun isWithinRoute(npc: EveNpcPlacement, x: Int, y: Int): Boolean {
        if (!ownsMovement(npc)) return false
        val points = listOf(npc.x.toDouble() to npc.y.toDouble()) +
            npc.motionNodes.drop(1).map { (gx, gy) -> (gx - 1) * 20.0 to (gy - 1) * 20.0 }
        return points.zipWithNext().any { (a, b) ->
            val dx = b.first - a.first
            val dy = b.second - a.second
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else
                (((x - a.first) * dx + (y - a.second) * dy) / lengthSq).coerceIn(0.0, 1.0)
            val offsetX = x - (a.first + t * dx)
            val offsetY = y - (a.second + t * dy)
            offsetX * offsetX + offsetY * offsetY <= 200.0 * 200.0
        }
    }
}

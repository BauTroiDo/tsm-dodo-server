package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.tsm.domain.data.EveNpcPlacement
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.ResponseSender
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống NPC tuần tra và gặp quái
 *
 * Phụ trách hai chức năng cốt lõi:
 * 1. **NPC tuần tra di chuyển**: mỗi ~2700ms NPC nhảy tới vị trí ngẫu nhiên mới, phát rộng qua S:022-002
 *    Client nhận được sẽ tự phát animation đi lại.
 * 2. **Máy chủ phát hiện va chạm**: mỗi tick kiểm tra khoảng cách giữa NPC có traceRadius > 0 với người chơi,
 *    Khi khoảng cách ≤ traceRadius sẽ kích hoạt chuỗi sự kiện whenHappen[3]=ServerBump.
 *
 * Tham khảo logic KERO TSNpcOnMapWalk.cs (tick mỗi 900ms, di chuyển mỗi 3 lần tick).
 *
 * Hệ toạ độ: tất cả dùng **toạ độ pixel** (khớp với S:022-002).
 * - npc.x/y (EveNpcPlacement.position) = toạ độ pixel
 * - motionNodes[0] = (rộng, cao) đơn vị ô (kích thước khung biên)
 * - motionNodes[1] = (X, Y) đơn vị ô (góc trên trái khung biên)
 * - motionNodes[2+] = (X, Y) đơn vị ô (điểm đường dẫn)
 * - ô→pixel: pixel = (grid - 1) * BLOCK_UNIT
 *
 * Định nghĩa motionType (Eve_NpcData.lua):
 * - 1 = đứng yên (khi traceRadius > 0 sẽ đi lại ngẫu nhiên trong bán kính + truy kích người chơi)
 * - 2 = đi lại ngẫu nhiên (nhảy ngẫu nhiên trong hình chữ nhật tuần tra)
 * - 3 = đi lại ngẫu nhiên trong phạm vi (giống 2)
 * - 4 = đi theo đường dẫn waypoint
 */
class NpcPatrolSystem(
    private val worldManager: WorldManager,
    private val responseSender: ResponseSender,
    private val eveData: Map<Int, SceneEveData>,
    private val scope: CoroutineScope,
    private val eveTriggerRouter: EveTriggerRouter
) {
    /** mapId → (npcClickId → NpcPatrolState) */
    private val mapNpcStates = ConcurrentHashMap<Int, ConcurrentHashMap<Int, NpcPatrolState>>()

    init {
        eveTriggerRouter.npcPositionProvider = { mapId, npcId -> getNpcPosition(mapId, npcId) }
    }

    /** Lấy vị trí tuần tra hiện tại của NPC (toạ độ pixel)*/
    fun getNpcPosition(mapId: Int, npcId: Int): Pair<Int, Int>? {
        val state = mapNpcStates[mapId]?.get(npcId) ?: return null
        return Pair(state.currentX, state.currentY)
    }

    /**
     * Khởi chạy coroutine tick tuần tra
     *
     * Mỗi TICK_INTERVAL_MS ms quét tất cả bản đồ đang hoạt động, cập nhật vị trí NPC và phát rộng.
     */
    fun start() {
        scope.launch {
            logger.info { "Hệ thống tuần tra NPC khởi động (tick=${TICK_INTERVAL_MS}ms, mỗi ${TICKS_PER_MOVE} lần di chuyển)" }
            while (true) {
                try {
                    tick()
                } catch (e: Exception) {
                    logger.error(e) { "tick tuần tra NPC gặp lỗi" }
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * Bật tuần tra NPC của bản đồ (gọi khi người chơi đầu tiên tiến vào)
     */
    fun activateMap(mapId: Int) {
        if (mapNpcStates.containsKey(mapId)) return

        val sceneEve = eveData[mapId] ?: return

        // Chẩn đoán: hiển thị phân bố motionType + traceRadius của NPC bản đồ này
        val allNpcs = sceneEve.npcs.values.filter { !it.close }
        val motionTypeCounts = allNpcs.groupBy { it.motionType }.mapValues { it.value.size }
        val traceNpcCount = allNpcs.count { it.traceRadius > 0 }
        logger.info { "Phân bố NPC trên bản đồ $mapId: motionType=$motionTypeCounts, traceRadius>0=$traceNpcCount (tổng ${allNpcs.size})" }

        // NPC có thể di chuyển = motionType != 1 (dạng tuần tra) OR traceRadius > 0 (dạng truy kích)
        // Tham khảo: MapNpcManager.lua → v.motionType ~= 1 or v.traceRadius > 0
        val movableNpcs = allNpcs.filter { it.motionType != MOTION_STAND || it.traceRadius > 0 }
        if (movableNpcs.isEmpty()) {
            logger.info { "Bản đồ $mapId không có NPC di chuyển được, bỏ qua" }
            return
        }

        val states = ConcurrentHashMap<Int, NpcPatrolState>()
        for (npc in movableNpcs) {
            states[npc.id] = createNpcState(npc)
        }

        mapNpcStates[mapId] = states
        val patrolCount = states.values.count { it.placement.motionType != MOTION_STAND }
        val traceCount = states.values.count { it.placement.traceRadius > 0 }
        logger.info { "Bật tuần tra NPC trên bản đồ $mapId: ${states.size} (tuần tra=$patrolCount, truy kích=$traceCount)" }
    }

    /**
     * Tắt tuần tra NPC của bản đồ (gọi khi người chơi cuối cùng rời đi)
     */
    fun deactivateMap(mapId: Int) {
        mapNpcStates.remove(mapId)
        logger.debug { "Tắt tuần tra NPC trên bản đồ $mapId" }
    }

    // ══════════════════════════════════════════════
    //  Logic nội bộ
    // ══════════════════════════════════════════════

    /** Thời gian log chẩn đoán lần trước*/
    private var lastDiagnosticTime = 0L

    /** Mỗi tick cập nhật NPC của tất cả bản đồ hoạt động*/
    private fun tick() {
        val now = System.currentTimeMillis()

        // Xuất thống kê tuần tra mỗi 30 giây
        if (now - lastDiagnosticTime > 30_000) {
            lastDiagnosticTime = now
            val totalNpcs = mapNpcStates.values.sumOf { it.size }
            if (totalNpcs > 0) {
                logger.info { "tick tuần tra: ${mapNpcStates.size} bản đồ, $totalNpcs NPC" }
            }
        }

        for ((mapId, states) in mapNpcStates) {
            val players = worldManager.getPlayersInAllInstances(mapId)
            if (players.isEmpty()) continue

            for ((_, state) in states) {
                // Bỏ qua khi đang hồi kỹ (hồi kỹ ~27 giây sau khi gặp quái)
                if (now < state.cooldownUntil) continue

                val npc = state.placement

                // ── Di chuyển tuần tra: di chuyển mỗi TICKS_PER_MOVE lần tick ──
                state.tickCounter++
                if (state.tickCounter >= TICKS_PER_MOVE) {
                    state.tickCounter = 0
                    moveNpcToNextTarget(mapId, state, npc)
                }

                // ── Phát hiện gặp quái: khi traceRadius > 0 kiểm tra mỗi tick ──
                if (npc.traceRadius > 0) {
                    checkEncounter(mapId, state, npc, players, now)
                }
            }
        }
    }

    /** Tạo trạng thái tuần tra NPC (toạ độ đều là pixel)*/
    private fun createNpcState(npc: EveNpcPlacement): NpcPatrolState {
        // Vị trí ban đầu = npc.x, npc.y (trường position, toạ độ pixel)
        val startX = npc.x
        val startY = npc.y

        // Tính hình chữ nhật tuần tra (toạ độ pixel)
        val (rectX, rectY, rectW, rectH) = calculateMotionRect(npc)

        // motionType=4 điểm đường dẫn: ô→pixel (bỏ qua [0]=khung biên, [1]=điểm bắt đầu)
        val pathNodes = if (npc.motionType == MOTION_PATH && npc.motionNodes.size > 2) {
            npc.motionNodes.drop(2).map { (gx, gy) ->
                Pair((gx - 1) * BLOCK_UNIT, (gy - 1) * BLOCK_UNIT)
            }
        } else {
            emptyList()
        }

        return NpcPatrolState(
            currentX = startX, currentY = startY,
            homeX = startX, homeY = startY,
            motionRectX = rectX, motionRectY = rectY,
            motionRectW = rectW, motionRectH = rectH,
            pathNodesPixel = pathNodes,
            cyclesRemaining = if (npc.motionCycleNum > 0) npc.motionCycleNum else Int.MAX_VALUE,
            // Trễ ngẫu nhiên ban đầu, tránh tất cả NPC khởi động cùng lúc
            cooldownUntil = System.currentTimeMillis() + Random.nextLong(500, 3000),
            placement = npc
        )
    }

    /**
     * Tính hình chữ nhật tuần tra (toạ độ pixel)
     *
     * motionNodes[0] = (rộng, cao) đơn vị ô
     * motionNodes[1] = (X góc trên trái, Y góc trên trái) đơn vị ô
     */
    private fun calculateMotionRect(npc: EveNpcPlacement): MotionRect {
        return if (npc.motionNodes.size >= 2) {
            val w = npc.motionNodes[0].first * BLOCK_UNIT
            val h = npc.motionNodes[0].second * BLOCK_UNIT
            val x = (npc.motionNodes[1].first - 1) * BLOCK_UNIT
            val y = (npc.motionNodes[1].second - 1) * BLOCK_UNIT
            MotionRect(x, y, w.coerceAtLeast(BLOCK_UNIT), h.coerceAtLeast(BLOCK_UNIT))
        } else if (npc.traceRadius > 0) {
            // NPC dạng truy kích không có motionNodes: lấy vị trí ban đầu làm tâm, traceRadius làm phạm vi
            MotionRect(
                npc.x - npc.traceRadius, npc.y - npc.traceRadius,
                npc.traceRadius * 2, npc.traceRadius * 2
            )
        } else {
            // Phạm vi mặc định
            MotionRect(
                npc.x - DEFAULT_PATROL_RANGE_PX, npc.y - DEFAULT_PATROL_RANGE_PX,
                DEFAULT_PATROL_RANGE_PX * 2, DEFAULT_PATROL_RANGE_PX * 2
            )
        }
    }

    /** Di chuyển NPC đến vị trí đích tiếp theo và phát rộng*/
    private fun moveNpcToNextTarget(mapId: Int, state: NpcPatrolState, npc: EveNpcPlacement) {
        val newPos = calculateNextTarget(state, npc) ?: return

        state.currentX = newPos.first
        state.currentY = newPos.second
        broadcastMove(mapId, npc.id, state.currentX, state.currentY)
    }

    /**
     * Tính đích di chuyển tiếp theo của NPC (toạ độ pixel)
     *
     * @return (pixelX, pixelY) hoặc null (không di chuyển)
     */
    private fun calculateNextTarget(state: NpcPatrolState, npc: EveNpcPlacement): Pair<Int, Int>? {
        return when (npc.motionType) {
            MOTION_STAND -> {
                // motionType=1 + traceRadius > 0: đi lại ngẫu nhiên trong bán kính truy kích (hành vi KERO)
                if (npc.traceRadius > 0) {
                    val r = npc.traceRadius
                    val rx = state.homeX + Random.nextInt(-r, r + 1)
                    val ry = state.homeY + Random.nextInt(-r, r + 1)
                    Pair(rx, ry)
                } else {
                    null // Chỉ đứng yên
                }
            }

            MOTION_RANDOM, MOTION_RANGE -> {
                // Nhảy ngẫu nhiên trong hình chữ nhật tuần tra (toạ độ pixel)
                val rx = state.motionRectX + Random.nextInt(state.motionRectW.coerceAtLeast(1))
                val ry = state.motionRectY + Random.nextInt(state.motionRectH.coerceAtLeast(1))
                Pair(rx, ry)
            }

            MOTION_PATH -> {
                // Đi theo đường dẫn waypoint (đã chuyển trước sang toạ độ pixel)
                val pathNodes = state.pathNodesPixel
                if (pathNodes.isEmpty()) return null

                val nextIndex = state.currentNodeIndex + state.pathDirection
                if (nextIndex < 0 || nextIndex >= pathNodes.size) {
                    when (npc.motionBack) {
                        2 -> {
                            // Vòng lặp ngược
                            state.pathDirection = -state.pathDirection
                            state.cyclesRemaining--
                            if (state.cyclesRemaining <= 0) return null
                            val ri = state.currentNodeIndex + state.pathDirection
                            if (ri < 0 || ri >= pathNodes.size) return null
                            state.currentNodeIndex = ri
                        }
                        else -> {
                            // Quay về điểm bắt đầu
                            state.cyclesRemaining--
                            if (state.cyclesRemaining <= 0) return null
                            state.currentNodeIndex = 0
                        }
                    }
                } else {
                    state.currentNodeIndex = nextIndex
                }

                pathNodes[state.currentNodeIndex]
            }

            else -> null
        }
    }

    /**
     * Kiểm tra NPC dạng truy kích có người chơi trong bán kính truy kích không
     *
     * Tham khảo KERO TSNpcOnMapWalk.cs: khoảng cách ≤ coord → kích hoạt chiến đấu.
     * Phiên bản mobile dùng chuỗi sự kiện whenHappen[3]=ServerBump.
     */
    private fun checkEncounter(
        mapId: Int, state: NpcPatrolState, npc: EveNpcPlacement,
        players: Collection<GameSession>, now: Long
    ) {
        val radiusSq = npc.traceRadius.toLong() * npc.traceRadius

        for (player in players) {
            if (!player.loginFinished) continue

            val dx = (player.x - state.currentX).toLong()
            val dy = (player.y - state.currentY).toLong()
            val distSq = dx * dx + dy * dy
            if (distSq > radiusSq) continue

            // Người chơi trong phạm vi truy kích!
            logger.debug { "NPC ${npc.id}(npcId=${npc.npcId}) phát hiện người chơi ${player.characterName} trong phạm vi truy kích map=$mapId" }

            // NPC nhảy đến gần người chơi (hành vi KERO)
            state.currentX = player.x
            state.currentY = player.y
            broadcastMove(mapId, npc.id, state.currentX, state.currentY)

            // Đặt hồi kỹ (KERO delay=10 ≈ 27 giây)
            state.cooldownUntil = now + ENCOUNTER_COOLDOWN_MS

            // Kích hoạt chuỗi sự kiện ServerBump (không chặn)
            val session = player
            val npcClickId = npc.id
            scope.launch {
                try {
                    eveTriggerRouter.triggerServerBump(session, npcClickId)
                } catch (e: Exception) {
                    logger.error(e) { "ServerBump kích hoạt thất bại npcId=${npc.npcId} clickId=$npcClickId" }
                }
            }
            break // Mỗi tick mỗi NPC chỉ truy kích một người chơi
        }
    }

    /** Phát rộng gói tin di chuyển NPC S:022-002*/
    private fun broadcastMove(mapId: Int, npcId: Int, x: Int, y: Int) {
        val packet = responseSender.buildMapNpcMovePacket(npcId, x, y)
        worldManager.broadcastToAllInstancesOfMap(mapId, packet)
    }

    companion object {
        /** Khoảng cách tick (ms), khớp với KERO*/
        private const val TICK_INTERVAL_MS = 900L

        /** Di chuyển mỗi N lần tick (3 × 900ms = 2700ms)*/
        private const val TICKS_PER_MOVE = 3

        /** Hồi kỹ gặp quái (ms): KERO delay=10, khoảng 27 giây; tránh cùng một NPC truy kích lặp lại gặp quái quá nhanh*/
        private const val ENCOUNTER_COOLDOWN_MS = 27_000L

        /** pixel/ô*/
        private const val BLOCK_UNIT = 20

        /** Phạm vi tuần tra pixel mặc định (khi không có motionNodes và không có traceRadius)*/
        private const val DEFAULT_PATROL_RANGE_PX = 100

        /** Hằng số motionType*/
        private const val MOTION_STAND = 1
        private const val MOTION_RANDOM = 2
        private const val MOTION_RANGE = 3
        private const val MOTION_PATH = 4
    }
}

/** Hình chữ nhật tuần tra (toạ độ pixel)*/
private data class MotionRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Trạng thái runtime tuần tra NPC
 *
 * Toạ độ đều là toạ độ pixel, tương ứng trực tiếp với S:022-002.
 */
data class NpcPatrolState(
    /** Toạ độ pixel hiện tại*/
    var currentX: Int,
    var currentY: Int,
    /** Toạ độ pixel ban đầu (tâm của đi lại ngẫu nhiên)*/
    val homeX: Int,
    val homeY: Int,
    /** Hình chữ nhật tuần tra (toạ độ pixel, tính trước từ motionNodes)*/
    val motionRectX: Int,
    val motionRectY: Int,
    val motionRectW: Int,
    val motionRectH: Int,
    /** motionType=4 điểm đường dẫn (chuyển trước sang toạ độ pixel)*/
    val pathNodesPixel: List<Pair<Int, Int>> = emptyList(),
    /** Chỉ mục node hiện tại trên đường dẫn*/
    var currentNodeIndex: Int = 0,
    /** Hướng đi đường dẫn: +1 thuận / -1 nghịch*/
    var pathDirection: Int = 1,
    /** Hồi kỹ đến timestamp này (hồi kỹ sau khi gặp quái + trễ ban đầu)*/
    var cooldownUntil: Long = 0,
    /** Số vòng lặp còn lại (đi theo đường dẫn motionType=4)*/
    var cyclesRemaining: Int = Int.MAX_VALUE,
    /** Bộ đếm tick (di chuyển mỗi TICKS_PER_MOVE lần tick)*/
    var tickCounter: Int = 0,
    /** Dữ liệu bố trí NPC tương ứng*/
    val placement: EveNpcPlacement
)

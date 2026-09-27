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
 * NPC 巡邏與遇怪系統
 *
 * 負責兩個核心功能：
 * 1. **NPC 巡邏移動**：每 ~2700ms NPC 跳到新隨機位置，透過 S:022-002 廣播
 *    客戶端收到後自行播放走動動畫。
 * 2. **伺服器碰撞偵測**：每 tick 檢查 traceRadius > 0 的 NPC 與玩家的距離，
 *    當距離 ≤ traceRadius 時觸發 whenHappen[3]=ServerBump 事件鏈。
 *
 * 參考 KERO TSNpcOnMapWalk.cs 邏輯（每 900ms tick，3 次 tick 移動一次）。
 *
 * 座標系統：全部使用**像素座標**（與 S:022-002 一致）。
 * - npc.x/y（EveNpcPlacement.position）= 像素座標
 * - motionNodes[0] = (寬, 高) 格子單位（邊界框尺寸）
 * - motionNodes[1] = (X, Y) 格子單位（邊界框左上角）
 * - motionNodes[2+] = (X, Y) 格子單位（路徑點）
 * - 格子→像素：pixel = (grid - 1) * BLOCK_UNIT
 *
 * motionType 定義（Eve_NpcData.lua）：
 * - 1 = 站立（traceRadius > 0 時在半徑內隨機遊走 + 追擊玩家）
 * - 2 = 隨機行走（在巡邏矩形內隨機跳）
 * - 3 = 範圍內隨機遊走（同 2）
 * - 4 = 依路點路徑行走
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

    /** 取得 NPC 當前巡邏位置（像素座標） */
    fun getNpcPosition(mapId: Int, npcId: Int): Pair<Int, Int>? {
        val state = mapNpcStates[mapId]?.get(npcId) ?: return null
        return Pair(state.currentX, state.currentY)
    }

    /**
     * 啟動巡邏 tick 協程
     *
     * 每 TICK_INTERVAL_MS 毫秒掃描所有活躍地圖，更新 NPC 位置並廣播。
     */
    fun start() {
        scope.launch {
            logger.info { "NPC 巡邏系統啟動（tick=${TICK_INTERVAL_MS}ms，每 ${TICKS_PER_MOVE} 次移動）" }
            while (true) {
                try {
                    tick()
                } catch (e: Exception) {
                    logger.error(e) { "NPC 巡邏 tick 異常" }
                }
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * 啟用地圖的 NPC 巡邏（第一個玩家進入時呼叫）
     */
    fun activateMap(mapId: Int) {
        if (mapNpcStates.containsKey(mapId)) return

        val sceneEve = eveData[mapId] ?: return

        // 診斷：顯示此地圖 NPC motionType + traceRadius 分佈
        val allNpcs = sceneEve.npcs.values.filter { !it.close }
        val motionTypeCounts = allNpcs.groupBy { it.motionType }.mapValues { it.value.size }
        val traceNpcCount = allNpcs.count { it.traceRadius > 0 }
        logger.info { "地圖 $mapId NPC 分佈：motionType=$motionTypeCounts，traceRadius>0=$traceNpcCount（共 ${allNpcs.size} 個）" }

        // 可移動 NPC = motionType != 1（巡邏型）OR traceRadius > 0（追擊型）
        // 參考：MapNpcManager.lua → v.motionType ~= 1 or v.traceRadius > 0
        val movableNpcs = allNpcs.filter { it.motionType != MOTION_STAND || it.traceRadius > 0 }
        if (movableNpcs.isEmpty()) {
            logger.info { "地圖 $mapId 無可移動 NPC，跳過" }
            return
        }

        val states = ConcurrentHashMap<Int, NpcPatrolState>()
        for (npc in movableNpcs) {
            states[npc.id] = createNpcState(npc)
        }

        mapNpcStates[mapId] = states
        val patrolCount = states.values.count { it.placement.motionType != MOTION_STAND }
        val traceCount = states.values.count { it.placement.traceRadius > 0 }
        logger.info { "地圖 $mapId 啟用 NPC 巡邏：${states.size} 個（巡邏=$patrolCount，追擊=$traceCount）" }
    }

    /**
     * 停用地圖的 NPC 巡邏（最後一個玩家離開時呼叫）
     */
    fun deactivateMap(mapId: Int) {
        mapNpcStates.remove(mapId)
        logger.debug { "地圖 $mapId 停用 NPC 巡邏" }
    }

    // ══════════════════════════════════════════════
    //  內部邏輯
    // ══════════════════════════════════════════════

    /** 上次診斷日誌時間 */
    private var lastDiagnosticTime = 0L

    /** 每次 tick 更新所有活躍地圖的 NPC */
    private fun tick() {
        val now = System.currentTimeMillis()

        // 每 30 秒輸出一次巡邏統計
        if (now - lastDiagnosticTime > 30_000) {
            lastDiagnosticTime = now
            val totalNpcs = mapNpcStates.values.sumOf { it.size }
            if (totalNpcs > 0) {
                logger.info { "巡邏 tick：${mapNpcStates.size} 張地圖，$totalNpcs 個 NPC" }
            }
        }

        for ((mapId, states) in mapNpcStates) {
            val players = worldManager.getPlayersInMap(mapId)
            if (players.isEmpty()) continue

            for ((_, state) in states) {
                // 冷卻中跳過（遇怪後 ~27 秒冷卻）
                if (now < state.cooldownUntil) continue

                val npc = state.placement

                // ── 巡邏移動：每 TICKS_PER_MOVE 次 tick 移動一次 ──
                state.tickCounter++
                if (state.tickCounter >= TICKS_PER_MOVE) {
                    state.tickCounter = 0
                    moveNpcToNextTarget(mapId, state, npc)
                }

                // ── 遇怪偵測：traceRadius > 0 時每次 tick 檢查 ──
                if (npc.traceRadius > 0) {
                    checkEncounter(mapId, state, npc, players, now)
                }
            }
        }
    }

    /** 建立 NPC 巡邏狀態（座標全為像素） */
    private fun createNpcState(npc: EveNpcPlacement): NpcPatrolState {
        // 初始位置 = npc.x, npc.y（position 欄位，像素座標）
        val startX = npc.x
        val startY = npc.y

        // 計算巡邏矩形（像素座標）
        val (rectX, rectY, rectW, rectH) = calculateMotionRect(npc)

        // motionType=4 路徑點：格子→像素（跳過 [0]=邊界框、[1]=起點）
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
            // 初始隨機延遲，避免所有 NPC 同時啟動
            cooldownUntil = System.currentTimeMillis() + Random.nextLong(500, 3000),
            placement = npc
        )
    }

    /**
     * 計算巡邏矩形（像素座標）
     *
     * motionNodes[0] = (寬, 高) 格子單位
     * motionNodes[1] = (左上角X, 左上角Y) 格子單位
     */
    private fun calculateMotionRect(npc: EveNpcPlacement): MotionRect {
        return if (npc.motionNodes.size >= 2) {
            val w = npc.motionNodes[0].first * BLOCK_UNIT
            val h = npc.motionNodes[0].second * BLOCK_UNIT
            val x = (npc.motionNodes[1].first - 1) * BLOCK_UNIT
            val y = (npc.motionNodes[1].second - 1) * BLOCK_UNIT
            MotionRect(x, y, w.coerceAtLeast(BLOCK_UNIT), h.coerceAtLeast(BLOCK_UNIT))
        } else if (npc.traceRadius > 0) {
            // 追擊型 NPC 無 motionNodes：以初始位置為中心，traceRadius 為範圍
            MotionRect(
                npc.x - npc.traceRadius, npc.y - npc.traceRadius,
                npc.traceRadius * 2, npc.traceRadius * 2
            )
        } else {
            // 預設範圍
            MotionRect(
                npc.x - DEFAULT_PATROL_RANGE_PX, npc.y - DEFAULT_PATROL_RANGE_PX,
                DEFAULT_PATROL_RANGE_PX * 2, DEFAULT_PATROL_RANGE_PX * 2
            )
        }
    }

    /** 移動 NPC 到下一個目標位置並廣播 */
    private fun moveNpcToNextTarget(mapId: Int, state: NpcPatrolState, npc: EveNpcPlacement) {
        val newPos = calculateNextTarget(state, npc) ?: return

        state.currentX = newPos.first
        state.currentY = newPos.second
        broadcastMove(mapId, npc.id, state.currentX, state.currentY)
    }

    /**
     * 計算 NPC 下一個移動目標（像素座標）
     *
     * @return (pixelX, pixelY) 或 null（不移動）
     */
    private fun calculateNextTarget(state: NpcPatrolState, npc: EveNpcPlacement): Pair<Int, Int>? {
        return when (npc.motionType) {
            MOTION_STAND -> {
                // motionType=1 + traceRadius > 0：在追擊半徑內隨機遊走（KERO 行為）
                if (npc.traceRadius > 0) {
                    val r = npc.traceRadius
                    val rx = state.homeX + Random.nextInt(-r, r + 1)
                    val ry = state.homeY + Random.nextInt(-r, r + 1)
                    Pair(rx, ry)
                } else {
                    null // 純站立
                }
            }

            MOTION_RANDOM, MOTION_RANGE -> {
                // 在巡邏矩形內隨機跳（像素座標）
                val rx = state.motionRectX + Random.nextInt(state.motionRectW.coerceAtLeast(1))
                val ry = state.motionRectY + Random.nextInt(state.motionRectH.coerceAtLeast(1))
                Pair(rx, ry)
            }

            MOTION_PATH -> {
                // 依路點路徑行走（已預轉像素座標）
                val pathNodes = state.pathNodesPixel
                if (pathNodes.isEmpty()) return null

                val nextIndex = state.currentNodeIndex + state.pathDirection
                if (nextIndex < 0 || nextIndex >= pathNodes.size) {
                    when (npc.motionBack) {
                        2 -> {
                            // 反向循環
                            state.pathDirection = -state.pathDirection
                            state.cyclesRemaining--
                            if (state.cyclesRemaining <= 0) return null
                            val ri = state.currentNodeIndex + state.pathDirection
                            if (ri < 0 || ri >= pathNodes.size) return null
                            state.currentNodeIndex = ri
                        }
                        else -> {
                            // 回到起點
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
     * 檢查追擊型 NPC 是否有玩家在追擊半徑內
     *
     * 參考 KERO TSNpcOnMapWalk.cs：距離 ≤ coord → 觸發戰鬥。
     * 手遊版使用 whenHappen[3]=ServerBump 事件鏈。
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

            // 玩家在追擊範圍內！
            logger.debug { "NPC ${npc.id}(npcId=${npc.npcId}) 偵測到玩家 ${player.characterName} 在追擊範圍內 map=$mapId" }

            // NPC 跳到玩家附近（KERO 行為）
            state.currentX = player.x
            state.currentY = player.y
            broadcastMove(mapId, npc.id, state.currentX, state.currentY)

            // 設定冷卻（KERO delay=10 ≈ 27 秒）
            state.cooldownUntil = now + ENCOUNTER_COOLDOWN_MS

            // 觸發 ServerBump 事件鏈（非阻塞）
            val session = player
            val npcClickId = npc.id
            scope.launch {
                try {
                    eveTriggerRouter.triggerServerBump(session, npcClickId)
                } catch (e: Exception) {
                    logger.error(e) { "ServerBump 觸發失敗 npcId=${npc.npcId} clickId=$npcClickId" }
                }
            }
            break // 每次 tick 每個 NPC 只追擊一個玩家
        }
    }

    /** 廣播 S:022-002 NPC 移動封包 */
    private fun broadcastMove(mapId: Int, npcId: Int, x: Int, y: Int) {
        val packet = responseSender.buildMapNpcMovePacket(npcId, x, y)
        worldManager.broadcastToMap(mapId, packet)
    }

    companion object {
        /** Tick 間隔（毫秒），與 KERO 一致 */
        private const val TICK_INTERVAL_MS = 900L

        /** 每 N 次 tick 移動一次（3 × 900ms = 2700ms） */
        private const val TICKS_PER_MOVE = 3

        /** 遇怪冷卻（毫秒），調整為 6 秒，避免遇怪後怪物長時間僵死不動 */
        private const val ENCOUNTER_COOLDOWN_MS = 6_000L

        /** 像素/格 */
        private const val BLOCK_UNIT = 20

        /** 預設巡邏範圍像素（無 motionNodes 且無 traceRadius 時） */
        private const val DEFAULT_PATROL_RANGE_PX = 100

        /** motionType 常數 */
        private const val MOTION_STAND = 1
        private const val MOTION_RANDOM = 2
        private const val MOTION_RANGE = 3
        private const val MOTION_PATH = 4
    }
}

/** 巡邏矩形（像素座標） */
private data class MotionRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * NPC 巡邏運行時狀態
 *
 * 座標全為像素座標，與 S:022-002 直接對應。
 */
data class NpcPatrolState(
    /** 當前像素座標 */
    var currentX: Int,
    var currentY: Int,
    /** 初始像素座標（隨機走的中心點） */
    val homeX: Int,
    val homeY: Int,
    /** 巡邏矩形（像素座標，由 motionNodes 預計算） */
    val motionRectX: Int,
    val motionRectY: Int,
    val motionRectW: Int,
    val motionRectH: Int,
    /** motionType=4 路徑點（預轉像素座標） */
    val pathNodesPixel: List<Pair<Int, Int>> = emptyList(),
    /** 路徑走當前 node 索引 */
    var currentNodeIndex: Int = 0,
    /** 路徑走方向：+1 正向 / -1 反向 */
    var pathDirection: Int = 1,
    /** 冷卻到此時間戳（遇怪後冷卻 + 初始延遲） */
    var cooldownUntil: Long = 0,
    /** 剩餘循環次數（motionType=4 路徑走） */
    var cyclesRemaining: Int = Int.MAX_VALUE,
    /** tick 計數器（每 TICKS_PER_MOVE 次 tick 移動一次） */
    var tickCounter: Int = 0,
    /** 對應的 NPC 擺放資料 */
    val placement: EveNpcPlacement
)

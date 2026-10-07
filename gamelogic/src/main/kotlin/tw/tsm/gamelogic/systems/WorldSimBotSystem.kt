package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.MapWalkGrid
import tw.tsm.domain.data.SceneEveData
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.protocol.PacketWriter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.random.Random

private val simBotLogger = KotlinLogging.logger {}

class WorldSimBotSystem(
    private val worldManager: WorldManager,
    private val scope: CoroutineScope,
    itemDefs: Map<Int, ItemDef>,
    private val eveData: Map<Int, SceneEveData>,
    private val mapWalkGrids: Map<Int, MapWalkGrid>,
) {
    private data class ScopeKey(val mapId: Int, val instanceId: Int)
    private enum class Behavior { FARMER, SOCIAL, IDLER }
    private enum class Purpose { ROAM, NPC, DOOR, FARM }
    private data class SimBot(
        val id: Long,
        val name: String,
        val sex: Int,
        val element: Int,
        val level: Int,
        val hair: Int,
        val color1: Int,
        val color2: Int,
        val equips: IntArray,
        val petNpcId: Int,
        val behavior: Behavior,
        val originX: Int,
        val originY: Int,
        val bornAt: Long,
        val retireAt: Long,
        var x: Int,
        var y: Int,
        var targetX: Int,
        var targetY: Int,
        var idleUntil: Long,
        var purpose: Purpose,
        val path: ArrayDeque<Pair<Int, Int>>,
        var direction: Int,
    )
    private data class Population(
        val bots: MutableList<SimBot>,
        var nextChatAt: Long,
    )

    private val nextId = AtomicLong(8_000_000_000L)
    private val populations = ConcurrentHashMap<ScopeKey, Population>()
    private val seen = ConcurrentHashMap<Long, MutableSet<Long>>()
    private val outfits = buildOutfits(itemDefs)

    private val names = listOf(
        "Vân Mộng", "Dạ Phong", "Tử Yên", "Bạch Vũ", "Thiên Lam", "Hàn Nguyệt",
        "Mộc Linh", "Xích Viêm", "Ngân Hà", "Thanh Trúc", "Lạc Thần", "Phong Ảnh",
        "Tuyết Nhi", "Long Kiếm", "Huyền Vũ", "Mộng Dao", "Thiên Vũ", "Cô Phong",
        "Lam Kiếm", "Băng Vân", "Hoa Vũ", "Nguyệt Ảnh", "Tử Long", "Phi Tuyết"
    )
    private val chatLines = listOf(
        "Có ai đi phó bản không?",
        "Đang kiếm tổ đội đây.",
        "Hôm nay farm khá ổn.",
        "Ai cần hỗ trợ cứ gọi nhé.",
        "Đi dạo một vòng rồi luyện cấp.",
        "Chợ hôm nay đông ghê.",
        "Chuẩn bị đi săn boss thôi.",
        "Có ai đang làm nhiệm vụ gần đây không?"
    )

    fun start() {
        scope.launch {
            simBotLogger.info { "WorldSimBot collision-safe khởi động: Ground.mmg A* + behavior + companion" }
            while (isActive) {
                try {
                    tick()
                } catch (t: Throwable) {
                    simBotLogger.error(t) { "WorldSimBot tick lỗi" }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val realSessions = worldManager.getOnlineSessions()
            .filter { it.loginFinished && it.mapId > 0 && it.instanceId == 0 && it.battleId == 0L }

        val activeScopes = realSessions.groupBy { ScopeKey(it.mapId, it.instanceId) }
        populations.keys.filter { it !in activeScopes.keys }.forEach { populations.remove(it) }

        val activePlayerIds = realSessions.mapTo(hashSetOf()) { it.playerId }
        seen.keys.filter { it !in activePlayerIds }.forEach { seen.remove(it) }

        for ((key, sessions) in activeScopes) {
            val anchor = sessions.first()
            val sceneData = eveData[key.mapId]
            val walkGrid = mapWalkGrids[key.mapId]
            val desired = if (walkGrid != null) desiredCount(sessions.size) else 0
            val population = populations.computeIfAbsent(key) {
                Population(mutableListOf(), System.currentTimeMillis() + Random.nextLong(35_000L, 70_000L))
            }
            reapExpiredBots(key, population)
            ensurePopulation(population, anchor, desired, sceneData, walkGrid)

            for (session in sessions) {
                val known = seen.computeIfAbsent(session.playerId) { ConcurrentHashMap.newKeySet<Long>() }
                for (bot in population.bots) {
                    if (known.add(bot.id)) {
                        session.send(buildAppearPacket(bot, key.mapId))
                        if (bot.petNpcId > 0) session.send(buildPetAppearPacket(bot))
                    }
                }
            }

            if (walkGrid != null) moveSomeBots(key, population, sceneData, walkGrid)
            maybeChat(key, population)
        }
    }

    private fun ensurePopulation(
        population: Population,
        anchor: GameSession,
        desired: Int,
        sceneData: SceneEveData?,
        walkGrid: MapWalkGrid?
    ) {
        while (population.bots.size < desired) {
            val index = population.bots.size
            val (dx, dy) = SPREAD_OFFSETS[index % SPREAD_OFFSETS.size]
            val requestedX = (anchor.x + dx).coerceIn(0, 65535)
            val requestedY = (anchor.y + dy).coerceIn(0, 65535)
            val safeSpawn = walkGrid?.nearestWalkable(requestedX, requestedY)
                ?: walkGrid?.nearestWalkable(anchor.x, anchor.y)
                ?: (anchor.x to anchor.y)
            val x = safeSpawn.first
            val y = safeSpawn.second
            val id = nextId.getAndIncrement()
            val nameIndex = ((id xor anchor.mapId.toLong()).absoluteValue % names.size).toInt()
            val profile = (id % 4).toInt()
            val behavior = when (index % 5) {
                0, 1 -> Behavior.FARMER
                2 -> Behavior.SOCIAL
                else -> Behavior.IDLER
            }
            population.bots += SimBot(
                id = id,
                name = names[nameIndex],
                sex = if (profile % 2 == 0) 1 else 2,
                element = (profile % 4) + 1,
                level = (anchor.level + (profile - 1) * 3).coerceIn(1, 200),
                hair = (anchor.hair + profile + 1) and 0xFF,
                color1 = anchor.color1 xor (0x00040810 * (profile + 1)),
                color2 = anchor.color2 xor (0x00100408 * (profile + 1)),
                equips = outfits.getOrElse(profile) { intArrayOf() },
                petNpcId = if (index % 3 == 0 || index % 5 == 1) WORLD_PETS[(profile + index) % WORLD_PETS.size] else 0,
                behavior = behavior,
                originX = x,
                originY = y,
                bornAt = System.currentTimeMillis(),
                retireAt = System.currentTimeMillis() + Random.nextLong(180_000L, 420_000L),
                x = x,
                y = y,
                targetX = (x + Random.nextInt(-260, 261)).coerceIn(0, 65535),
                targetY = (y + Random.nextInt(-260, 261)).coerceIn(0, 65535),
                idleUntil = System.currentTimeMillis() + Random.nextLong(2_000L, 12_000L),
                purpose = Purpose.ROAM,
                path = ArrayDeque(),
                direction = profile % 8,
            ).also { bot -> if (walkGrid != null) chooseNextDestination(bot, population, sceneData, walkGrid) }
        }
        while (population.bots.size > desired) {
            val removed = population.bots.removeLast()
            seen.values.forEach { it.remove(removed.id) }
            if (removed.petNpcId > 0) {
                worldManager.broadcastToMap(anchor.mapId, buildPetDisappearPacket(removed.id), instanceId = 0)
            }
            worldManager.broadcastToMap(anchor.mapId, buildDisappearPacket(removed.id), instanceId = 0)
        }
    }

    private fun reapExpiredBots(key: ScopeKey, population: Population) {
        val now = System.currentTimeMillis()
        val iterator = population.bots.iterator()
        while (iterator.hasNext()) {
            val bot = iterator.next()
            if (now < bot.retireAt) continue
            if (bot.petNpcId > 0) {
                worldManager.broadcastToMap(key.mapId, buildPetDisappearPacket(bot.id), instanceId = key.instanceId)
            }
            worldManager.broadcastToMap(key.mapId, buildDisappearPacket(bot.id), instanceId = key.instanceId)
            seen.values.forEach { it.remove(bot.id) }
            iterator.remove()
        }
    }

    private fun moveSomeBots(
        key: ScopeKey,
        population: Population,
        sceneData: SceneEveData?,
        walkGrid: MapWalkGrid
    ) {
        if (population.bots.isEmpty()) return
        val now = System.currentTimeMillis()
        val movers = population.bots.shuffled().take(minOf(5, population.bots.size))
        for (bot in movers) {
            if (now < bot.idleUntil) continue

            if (bot.path.isEmpty()) {
                chooseNextDestination(bot, population, sceneData, walkGrid)
                continue
            }

            val next = bot.path.removeFirst()
            if (!walkGrid.isWalkablePixel(next.first, next.second)) {
                bot.path.clear()
                chooseNextDestination(bot, population, sceneData, walkGrid)
                continue
            }
            val dx = next.first - bot.x
            val dy = next.second - bot.y
            bot.x = next.first
            bot.y = next.second
            bot.direction = directionFromDelta(dx, dy)
            worldManager.broadcastToMap(key.mapId, buildMovePacket(bot), instanceId = key.instanceId)
        }
    }

    private fun chooseNextDestination(
        bot: SimBot,
        population: Population,
        sceneData: SceneEveData?,
        walkGrid: MapWalkGrid
    ) {
        val now = System.currentTimeMillis()
        val npcPois = sceneData?.npcs?.values
            ?.filter { !it.close && !it.isHide && it.x > 0 && it.y > 0 }
            ?.map { it.x to it.y }
            .orEmpty()
        val doorPois = sceneData?.doors?.values
            ?.filter { it.x > 0 && it.y > 0 }
            ?.map { it.x to it.y }
            .orEmpty()
        val farmPois = buildList {
            sceneData?.goods?.values?.filter { it.x > 0 && it.y > 0 }?.forEach { add(it.x to it.y) }
            sceneData?.mines?.values
                ?.filter { it.sizeKind == 0 && it.gridWidth > 0 && it.gridHeight > 0 }
                ?.forEach {
                    add(
                        ((it.gridX + it.gridWidth / 2) * 20) to
                            ((it.gridY + it.gridHeight / 2) * 20)
                    )
                }
        }

        fun setTarget(point: Pair<Int, Int>, purpose: Purpose, restMs: LongRange) {
            bot.targetX = (point.first + Random.nextInt(-45, 46)).coerceIn(0, 65535)
            bot.targetY = (point.second + Random.nextInt(-45, 46)).coerceIn(0, 65535)
            bot.purpose = purpose
            bot.idleUntil = now + Random.nextLong(restMs.first, restMs.last + 1)
        }

        when (bot.behavior) {
            Behavior.FARMER -> {
                when {
                    farmPois.isNotEmpty() && Random.nextInt(100) < 65 ->
                        setTarget(farmPois.random(), Purpose.FARM, 2_000L..7_000L)
                    doorPois.isNotEmpty() && Random.nextInt(100) < 35 ->
                        setTarget(doorPois.random(), Purpose.DOOR, 1_000L..4_000L)
                    else -> {
                        bot.targetX = (bot.originX + Random.nextInt(-760, 761)).coerceIn(0, 65535)
                        bot.targetY = (bot.originY + Random.nextInt(-760, 761)).coerceIn(0, 65535)
                        bot.purpose = Purpose.ROAM
                        bot.idleUntil = now + Random.nextLong(1_000L, 5_000L)
                    }
                }
            }
            Behavior.SOCIAL -> {
                when {
                    npcPois.isNotEmpty() && Random.nextInt(100) < 70 ->
                        setTarget(npcPois.random(), Purpose.NPC, 6_000L..18_000L)
                    else -> {
                        val other = population.bots.filter { it.id != bot.id }.randomOrNull()
                        if (other != null) {
                            setTarget(other.x to other.y, Purpose.NPC, 4_000L..12_000L)
                        } else {
                            bot.targetX = (bot.originX + Random.nextInt(-320, 321)).coerceIn(0, 65535)
                            bot.targetY = (bot.originY + Random.nextInt(-320, 321)).coerceIn(0, 65535)
                            bot.purpose = Purpose.ROAM
                            bot.idleUntil = now + Random.nextLong(4_000L, 12_000L)
                        }
                    }
                }
            }
            Behavior.IDLER -> {
                when {
                    npcPois.isNotEmpty() && Random.nextInt(100) < 55 ->
                        setTarget(npcPois.random(), Purpose.NPC, 15_000L..35_000L)
                    doorPois.isNotEmpty() && Random.nextInt(100) < 20 ->
                        setTarget(doorPois.random(), Purpose.DOOR, 8_000L..20_000L)
                    else -> {
                        bot.targetX = (bot.originX + Random.nextInt(-160, 161)).coerceIn(0, 65535)
                        bot.targetY = (bot.originY + Random.nextInt(-160, 161)).coerceIn(0, 65535)
                        bot.purpose = Purpose.ROAM
                        bot.idleUntil = now + Random.nextLong(14_000L, 38_000L)
                    }
                }
            }
        }

        val safeTarget = walkGrid.nearestWalkable(bot.targetX, bot.targetY)
        if (safeTarget == null) {
            bot.path.clear()
            bot.targetX = bot.x
            bot.targetY = bot.y
            bot.idleUntil = now + 2_000L
            return
        }

        bot.targetX = safeTarget.first
        bot.targetY = safeTarget.second
        bot.path.clear()
        val planned = walkGrid.findPathPixels(bot.x, bot.y, bot.targetX, bot.targetY)
        if (planned.isEmpty() && (bot.x != bot.targetX || bot.y != bot.targetY)) {
            bot.targetX = bot.x
            bot.targetY = bot.y
            bot.idleUntil = now + 2_000L
            return
        }
        planned.forEach { bot.path.addLast(it) }
    }

    private fun directionFromDelta(dx: Int, dy: Int): Int = when {
        kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx > 0 -> 2
        kotlin.math.abs(dx) > kotlin.math.abs(dy) && dx < 0 -> 6
        dy > 0 -> 4
        dy < 0 -> 0
        else -> 0
    }

    private fun maybeChat(key: ScopeKey, population: Population) {
        val now = System.currentTimeMillis()
        if (population.bots.isEmpty() || now < population.nextChatAt) return
        val bot = population.bots.random()
        val line = when (bot.purpose) {
            Purpose.NPC -> listOf("Mua xong rồi, đi tiếp thôi.", "NPC này có nhiệm vụ à?", "Để xem có gì hữu ích.").random()
            Purpose.DOOR -> listOf("Qua khu bên kia xem nào.", "Đổi bãi farm thôi.", "Đi tiếp nhé.").random()
            Purpose.FARM -> listOf("Farm thêm chút nữa.", "Bãi này quái ổn đấy.", "Kiếm thêm ít đồ rồi về.").random()
            Purpose.ROAM -> chatLines.random()
        }
        worldManager.broadcastToMap(key.mapId, buildChatPacket(bot, line), instanceId = key.instanceId)
        population.nextChatAt = now + Random.nextLong(45_000L, 95_000L)
    }

    private fun desiredCount(realCount: Int): Int = when {
        realCount <= 0 -> 0
        realCount == 1 -> 8
        realCount <= 3 -> 10
        else -> 12
    }

    private fun buildAppearPacket(bot: SimBot, mapId: Int): ByteArray {
        val w = PacketWriter(128)
            .writeByte(Opcodes.CHARACTER).writeByte(0).writeByte(0)
            .writeLongLE(bot.id)
            .writeByte(bot.sex).writeByte(bot.element).writeByte(0).writeByte(bot.level)
            .writeByte(0).writeUIntLE(0).writeShortLE(0)
            .writeShortLE(mapId).writeShortLE(bot.x).writeShortLE(bot.y)
            .writeByte(0).writeByte(bot.hair).writeByte(0)
            .writeIntLE(bot.color1).writeIntLE(bot.color2)
            .writeShortLE(0).writeByte(0).writeByte(1).writeByte(0).writeByte(1)
            .writeByte(0).writeByte(0)
            .writeBig5WithByteLen(bot.name)
            .writeShortLE(0).writeShortLE(0).writeByte(0)
        w.writeByte(bot.equips.size)
        bot.equips.forEach { w.writeShortLE(it) }
        w.writeShortLE(0).writeUIntLE(0).writeByte(0)
        return w.toByteArray()
    }

    private fun buildPetAppearPacket(bot: SimBot): ByteArray =
        PacketWriter(15)
            .writeByte(Opcodes.FOLLOW_NPC).writeByte(1).writeByte(0)
            .writeLongLE(bot.id).writeByte(0)
            .writeIntLE(bot.petNpcId).writeByte(0).writeBoolean(false)
            .toByteArray()

    private fun buildPetDisappearPacket(botId: Long): ByteArray =
        PacketWriter(13)
            .writeByte(Opcodes.FOLLOW_NPC).writeByte(2).writeByte(0)
            .writeLongLE(botId).writeByte(0).writeBoolean(false)
            .toByteArray()

    private fun buildMovePacket(bot: SimBot): ByteArray =
        PacketWriter(16)
            .writeByte(Opcodes.MOVE).writeByte(1).writeByte(0)
            .writeLongLE(bot.id).writeByte(bot.direction)
            .writeShortLE(bot.x).writeShortLE(bot.y)
            .toByteArray()

    private fun buildDisappearPacket(botId: Long): ByteArray =
        PacketWriter(11)
            .writeByte(Opcodes.LOGIN).writeByte(1).writeByte(0)
            .writeLongLE(botId)
            .toByteArray()

    private fun buildChatPacket(bot: SimBot, content: String): ByteArray =
        PacketWriter()
            .writeByte(Opcodes.CHAT).writeByte(ChatChannel.SCENE.code).writeByte(0)
            .writeLongLE(bot.id).writeShortLE(0)
            .writeBig5WithByteLen(bot.name)
            .writeUnicodeWithByteLen(content)
            .writeByte(0).writeByte(0)
            .toByteArray()

    private fun buildOutfits(itemDefs: Map<Int, ItemDef>): List<IntArray> {
        val bySlot = (1..5).associateWith { fit ->
            itemDefs.asSequence()
                .filter { (_, d) -> d.fitType == fit && d.openUsed == 0 && d.malePicId > 0 && d.femalePicId > 0 }
                .map { it.key }
                .sorted()
                .toList()
        }
        if (bySlot.values.any { it.size < 4 }) return List(4) { intArrayOf() }
        return List(4) { profile ->
            IntArray(5) { slot ->
                val pool = bySlot.getValue(slot + 1)
                pool[(profile * (pool.size / 4).coerceAtLeast(1) + slot * 7) % pool.size]
            }
        }
    }

    companion object {
        private val WORLD_PETS = intArrayOf(40428, 40429, 40430, 40431)
        private val SPREAD_OFFSETS = listOf(
            -520 to -220, 460 to 180, -360 to 410, 580 to -360,
            -180 to -560, 260 to 520, 720 to 80, -700 to 120,
            90 to -720, -80 to 680, 840 to 340, -820 to -320,
        )
        const val TICK_MS = 1_200L
    }
}

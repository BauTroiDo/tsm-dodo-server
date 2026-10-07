package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.enums.ChatChannel
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.protocol.PacketWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.random.Random

private val simBotLogger = KotlinLogging.logger {}

class WorldSimBotSystem(
    private val worldManager: WorldManager,
    private val scope: CoroutineScope,
    itemDefs: Map<Int, ItemDef>,
) {
    private data class ScopeKey(val mapId: Int, val instanceId: Int)
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
        val originX: Int,
        val originY: Int,
        var x: Int,
        var y: Int,
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
            simBotLogger.info { "WorldSimBot tầng 1 khởi động" }
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
            val desired = desiredCount(sessions.size)
            val population = populations.computeIfAbsent(key) {
                Population(mutableListOf(), System.currentTimeMillis() + Random.nextLong(35_000L, 70_000L))
            }
            ensurePopulation(population, anchor, desired)

            for (session in sessions) {
                val known = seen.computeIfAbsent(session.playerId) { ConcurrentHashMap.newKeySet<Long>() }
                for (bot in population.bots) {
                    if (known.add(bot.id)) session.send(buildAppearPacket(bot, key.mapId))
                }
            }

            moveSomeBots(key, population)
            maybeChat(key, population)
        }
    }

    private fun ensurePopulation(population: Population, anchor: GameSession, desired: Int) {
        while (population.bots.size < desired) {
            val index = population.bots.size
            val dx = ((index % 4) - 1) * 45
            val dy = ((index / 4) * 55) + 30
            val x = (anchor.x + dx).coerceIn(0, 65535)
            val y = (anchor.y + dy).coerceIn(0, 65535)
            val id = nextId.getAndIncrement()
            val nameIndex = ((id xor anchor.mapId.toLong()).absoluteValue % names.size).toInt()
            val profile = (id % 4).toInt()
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
                originX = x,
                originY = y,
                x = x,
                y = y,
                direction = profile % 8,
            )
        }
        while (population.bots.size > desired) {
            val removed = population.bots.removeLast()
            seen.values.forEach { it.remove(removed.id) }
            worldManager.broadcastToMap(anchor.mapId, buildDisappearPacket(removed.id), instanceId = 0)
        }
    }

    private fun moveSomeBots(key: ScopeKey, population: Population) {
        if (population.bots.isEmpty()) return
        val count = minOf(2, population.bots.size)
        repeat(count) {
            val bot = population.bots[Random.nextInt(population.bots.size)]
            val nx = (bot.x + Random.nextInt(-36, 37)).coerceIn(bot.originX - 140, bot.originX + 140).coerceAtLeast(0)
            val ny = (bot.y + Random.nextInt(-36, 37)).coerceIn(bot.originY - 140, bot.originY + 140).coerceAtLeast(0)
            bot.x = nx
            bot.y = ny
            bot.direction = Random.nextInt(0, 8)
            worldManager.broadcastToMap(key.mapId, buildMovePacket(bot), instanceId = key.instanceId)
        }
    }

    private fun maybeChat(key: ScopeKey, population: Population) {
        val now = System.currentTimeMillis()
        if (population.bots.isEmpty() || now < population.nextChatAt) return
        val bot = population.bots.random()
        val line = chatLines.random()
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
        const val TICK_MS = 4_000L
    }
}

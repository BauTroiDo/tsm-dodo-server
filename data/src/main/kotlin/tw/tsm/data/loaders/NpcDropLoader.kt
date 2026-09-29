package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import tw.tsm.domain.data.NpcDropDef
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Loader bảng rơi đồ của NPC
 *
 * Đọc định nghĩa rơi đồ của NPC từ `gamedata/npc_drops.json`.
 * Định dạng JSON:
 * ```json
 * {
 *   "1001": [
 *     { "itemId": 100, "rate": 50, "minQty": 1, "maxQty": 3 }
 *   ]
 * }
 * ```
 */
object NpcDropLoader {

    /**
     * Đọc bảng rơi đồ của NPC
     *
     * @param path đường dẫn tới npc_drops.json
     * @return ánh xạ npcId → danh sách vật rơi
     */
    fun load(path: Path): Map<Int, List<NpcDropDef>> {
        if (!Files.exists(path)) {
            logger.warn { "Bảng rơi đồ của NPC không tồn tại: $path, dùng bảng rơi trống" }
            return emptyMap()
        }

        val text = Files.readString(path)
        val json = Json.parseToJsonElement(text).jsonObject
        val result = mutableMapOf<Int, List<NpcDropDef>>()

        for ((npcIdStr, dropsElement) in json) {
            val npcId = npcIdStr.toIntOrNull()
            if (npcId == null) {
                logger.warn { "key bảng rơi đồ của NPC không phải số: $npcIdStr, bỏ qua" }
                continue
            }

            val drops = dropsElement.jsonArray.map { entry ->
                val obj = entry.jsonObject
                NpcDropDef(
                    itemId = obj["itemId"]!!.jsonPrimitive.int,
                    rate = obj["rate"]!!.jsonPrimitive.int,
                    minQty = obj["minQty"]?.jsonPrimitive?.intOrNull ?: 1,
                    maxQty = obj["maxQty"]?.jsonPrimitive?.intOrNull ?: 1
                )
            }
            result[npcId] = drops
        }

        logger.info { "Nạp bảng rơi đồ NPC xong: ${result.size} loại NPC, tổng ${result.values.sumOf { it.size }} dòng rơi đồ" }
        return result
    }
}

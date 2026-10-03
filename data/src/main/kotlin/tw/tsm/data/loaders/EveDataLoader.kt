package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader
import tw.tsm.domain.data.*

private val logger = KotlinLogging.logger {}

/**
 * Trình nạp dữ liệu sự kiện Eve.emg — tương ứng logic đọc Eve.emg của client trong `DataManager.lua`
 *
 * Định dạng container (đảo ngược từ DataManager.lua):
 * ```
 * [UInt16 LE] số lượng cảnh (ví dụ 3823)
 * Mỗi mục thư mục cảnh 32 bytes:
 *   [1B] độ dài tên (ví dụ 9)
 *   [23B] tên ("10701.eve\0...", cố định 23 bytes)
 *   [Int32 LE] offset tuyệt đối của dữ liệu cảnh (từ đầu file)
 *   [Int32 LE] kích thước dữ liệu cảnh (bytes)
 * ```
 *
 * ID cảnh = tên bỏ hậu tố ".eve" rồi chuyển thành số nguyên ("10701.eve" → 10701)
 *
 * Định dạng dữ liệu mỗi cảnh (EventData.lua):
 * ```
 * [Int32]  số lượng NpcData  + NpcData[]
 * [UInt16] số lượng GoodsData + GoodsData[]   ← phân tích
 * [UInt16] số lượng DoorData  + DoorData[]    ← bỏ qua
 * [UInt16] số lượng MineData  + MineData[]    ← bỏ qua
 * [UInt16] số lượng SurfaceData + SurfaceData[] ← bỏ qua
 * [UInt16] số lượng SceneInfoData + SceneInfoData[] ← bỏ qua
 * [UInt16] số lượng GroupData  + GroupData[]  ← phân tích (lựa chọn phân nhóm kết quả)
 * [UInt16] số lượng NpcEventData + NpcEventData[]  ← phân tích
 * [UInt16] số lượng FightData + FightData[]   ← phân tích (cấu hình phe địch của chiến đấu sự kiện)
 * ```
 */
object EveDataLoader {

    /**
     * Nạp Eve.emg và trả về ánh xạ ID cảnh → [SceneEveData]
     *
     * @param data nội dung nhị phân đầy đủ của Eve.emg
     */
    fun load(data: ByteArray): Map<Int, SceneEveData> {
        val reader = DatReader(data)

        // ── Đọc thư mục cảnh ──
        val sceneCount = reader.readUInt16()
        logger.debug { "Eve.emg: số cảnh = $sceneCount" }

        data class SceneEntry(val sceneId: Int, val position: Int, val size: Int)

        val entries = ArrayList<SceneEntry>(sceneCount)
        repeat(sceneCount) {
            val nameLen = reader.readByte()          // [1B] Độ dài tên
            val nameBytes = reader.readBytes(23)     // [23B] Trường tên cố định
            val position = reader.readInt32()        // [4B] Offset tuyệt đối
            val size = reader.readInt32()            // [4B] Kích thước

            // Lấy phần tên hợp lệ (nameLen bytes), bỏ ".eve"
            val name = String(nameBytes, 0, nameLen.coerceAtMost(23), Charsets.US_ASCII)
            val sceneId = name.removeSuffix(".eve").toIntOrNull() ?: return@repeat

            if (position > 0 && size > 0) {
                entries.add(SceneEntry(sceneId, position, size))
            }
        }

        // ── Parse từng cảnh ──
        val result = HashMap<Int, SceneEveData>(entries.size)
        var parseErrors = 0

        for (entry in entries) {
            try {
                // DataManager.lua dòng 773: position = ReadInt32() + 103
                // 103 bytes đầu của block cảnh là phần header, EventData.New đọc từ +103
                reader.seek(entry.position + 103)
                val sceneData = parseSceneData(reader)
                if (sceneData.npcs.isNotEmpty() || sceneData.doors.isNotEmpty() || sceneData.npcEvents.isNotEmpty() || sceneData.goods.isNotEmpty()) {
                    result[entry.sceneId] = sceneData
                }
            } catch (e: Exception) {
                parseErrors++
                if (parseErrors <= 5) {
                    logger.warn { "Parse cảnh ${entry.sceneId} thất bại (offset=${entry.position}): ${e.message}" }
                }
            }
        }

        if (parseErrors > 5) {
            logger.warn { "... tổng cộng $parseErrors cảnh parse thất bại (chỉ hiển thị 5 cái đầu)" }
        }

        return result
    }

    // ── Parse dữ liệu cảnh ──

    /**
     * Phân tích dữ liệu sự kiện của một cảnh duy nhất
     */
    private fun parseSceneData(reader: DatReader): SceneEveData {
        // [1] NpcData（Int32 count）
        val npcs = parseNpcDataSection(reader)

        // [2] GoodsData (UInt16 count, cố định 13B/record)
        val goods = parseGoodsSection(reader)

        // [3] DoorData (UInt16 count, độ dài biến thiên)
        val doors = parseDoorSection(reader)

        // [4] MineData (UInt16 count, độ dài biến thiên) — server-triggered encounter zones
        val mines = parseMineSection(reader)

        // [5] SurfaceData (UInt16 count, độ dài biến thiên)
        val surfaceDatas = parseSurfaceSection(reader)

        // [6] SceneInfoData (UInt16 count, cố định 39B/record) — đích dịch chuyển của Door
        val sceneInfos = parseSceneInfoSection(reader)

        // [7] GroupData (UInt16 count, độ dài biến thiên) — chọn nhóm kết quả
        val groupDatas = parseGroupSection(reader)

        // [8] NpcEventData (UInt16 count) ← parse
        val npcEvents = parseNpcEventSection(reader)

        // [9] FightData (UInt16 count) ← parse (cấu hình phe địch trong chiến đấu sự kiện)
        val fightDatas = parseFightDataSection(reader)

        return SceneEveData(npcs, doors, npcEvents, sceneInfos, fightDatas, surfaceDatas, groupDatas, mines, goods)
    }

    // ────────────────────────────────────────────
    // Parse NpcData (Eve_NpcData.lua)
    // ────────────────────────────────────────────

    /**
     * Phân tích section NpcData
     *
     * Thứ tự trường đối chiếu nghiêm ngặt Eve_NpcData.lua (đối chiếu 1:1 từng dòng):
     * ```
     * id(UInt16) npcId(UInt16)
     * eventCount(UInt16) events[n](Byte each)
     * saleKindCount(Byte) saleKinds[n](Byte each)
     * motionNodeCount(Byte) motionNodes[0..n]{Int32 Int32} (tổng cộng n+1)
     * motionType(Byte) motionBack(Byte) motionCycleNum(Byte) direction(Byte)
     * motionSuspendMS(UInt16) motionSpeedLv(Byte)
     * roleGrid{Int32×4} moveOffsetGrid{Int32×2} position{Int32×2}
     * roleStatus{isHide(1) isVisible(1) rideNpcId(UInt16) canGrow(1) isLie(1)}
     * innerNode{Int32×4} outerNode{Int32×4}
     * traceSpeedLv(Byte) traceRadius(UInt16) close(Byte)
     * ```
     */
    private fun parseNpcDataSection(reader: DatReader): Map<Int, EveNpcPlacement> {
        val count = reader.readInt32()   // NpcData dùng Int32
        if (count <= 0) return emptyMap()

        val result = LinkedHashMap<Int, EveNpcPlacement>(count)

        repeat(count) {
            val id = reader.readUInt16()
            val npcId = reader.readUInt16()

            // eventCount: Lua ReadUInt16 (không phải Byte)
            val eventCount = reader.readUInt16()
            val events = ArrayList<Int>(eventCount)
            // events[n]: Lua ReadByte (không phải UInt16)
            repeat(eventCount) { events.add(reader.readByte()) }

            val saleKindCount = reader.readByte()
            repeat(saleKindCount) { reader.readByte() }

            // Điểm tuần tra: Lua for i=0, motionNodeCount → tổng cộng motionNodeCount+1 nút
            val motionNodeCount = reader.readByte()
            val motionNodes = ArrayList<Pair<Int, Int>>(motionNodeCount + 1)
            repeat(motionNodeCount + 1) {
                val nx = reader.readInt32()
                val ny = reader.readInt32()
                motionNodes.add(Pair(nx, ny))
            }

            val motionType = reader.readByte()
            val motionBack = reader.readByte()
            val motionCycleNum = reader.readByte()
            val direction = reader.readByte()
            val motionSuspendMS = reader.readUInt16()
            val motionSpeedLv = reader.readByte()

            // roleGrid: 4 × Int32 = 16B (không dùng)
            repeat(4) { reader.readInt32() }

            // moveOffsetGrid: 2 × Int32 = 8B (không dùng)
            repeat(2) { reader.readInt32() }

            // position: 2 × Int32 = 8B (toạ độ NPC)
            val posX = reader.readInt32()
            val posY = reader.readInt32()

            // roleStatus（6B）：isHide(1) + isVisible(1) + rideNpcId(2) + canGrow(1) + isLie(1)
            val isHide = reader.readByte() != 0
            val isVisible = reader.readByte() != 0
            reader.readUInt16()     // rideNpcId
            val canGrow = reader.readByte()  // canGrow
            reader.readByte()       // isLie

            // innerNode: 4 × Int32 = 16B (không dùng)
            repeat(4) { reader.readInt32() }

            // outerNode: 4 × Int32 = 16B (không dùng)
            repeat(4) { reader.readInt32() }

            reader.readByte()       // traceSpeedLv (không dùng)
            val traceRadius = reader.readUInt16()
            val close = reader.readByte() != 0

            if (id > 0) {
                result[id] = EveNpcPlacement(
                    id = id, npcId = npcId, events = events,
                    x = posX, y = posY, close = close,
                    motionType = motionType, motionBack = motionBack,
                    motionCycleNum = motionCycleNum, direction = direction,
                    motionSuspendMS = motionSuspendMS, motionSpeedLv = motionSpeedLv,
                    motionNodes = motionNodes, traceRadius = traceRadius,
                    canGrow = canGrow, isHide = isHide, isVisible = isVisible
                )
            }
        }

        return result
    }

    // ────────────────────────────────────────────
    // Phần Skip
    // ────────────────────────────────────────────

    /**
     * Bỏ qua section GoodsData — Eve_GoodsData.lua
     *
     * Cố định 13B/record: NO(1)+DBID(2)+PosX(4)+PosY(4)+HideTime(2)
     */
    private fun parseGoodsSection(reader: DatReader): Map<Int, EveGoodsPlacement> {
        val count = reader.readUInt16()
        return buildMap {
            repeat(count) {
                val id = reader.readByte()
                val itemId = reader.readUInt16()
                val x = reader.readInt32()
                val y = reader.readInt32()
                val hideTime = reader.readUInt16()
                put(id, EveGoodsPlacement(id, itemId, x, y, hideTime))
            }
        }
    }

    /**
     * Phân tích section DoorData — Eve_DoorData.lua
     *
     * Độ dài thay đổi: id(2)+eventCount(2)+events[n](n×1)+grid(16)+imageKind(1)+pos(4)+close(1)
     *
     * Door và NPC chia sẻ cùng một bảng NpcEventData (tham chiếu eveNo),
     * Nhưng mỗi loại có không gian ID độc lập (Door.id ≠ NPC.clickId).
     *
     * @return ánh xạ doorId → [EveDoorPlacement]
     */
    private fun parseDoorSection(reader: DatReader): Map<Int, EveDoorPlacement> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveDoorPlacement>(count)
        repeat(count) {
            val id = reader.readUInt16()
            val eventCount = reader.readUInt16()
            val events = ArrayList<Int>(eventCount)
            repeat(eventCount) { events.add(reader.readByte()) }
            // grid: 4 × Int32 (gridX, gridY, gridWidth, gridHeight) — vùng va chạm của cửa (đơn vị ô lưới)
            val gridX = reader.readInt32()
            val gridY = reader.readInt32()
            val gridW = reader.readInt32()
            val gridH = reader.readInt32()
            reader.readByte()               // imageKind
            reader.readUInt16()             // imgInfo.position.x (toạ độ hiển thị ảnh, không phải toạ độ va chạm)
            reader.readUInt16()             // imgInfo.position.y
            // Toạ độ pixel tâm cửa = tâm grid × BLOCK_UNIT(20), khớp với client DoorManager.lua
            val doorX = (gridX + gridW / 2) * 20
            val doorY = (gridY + gridH / 2) * 20
            reader.readByte()               // close（Boolean）

            if (id > 0) {
                result[id] = EveDoorPlacement(id = id, events = events, x = doorX, y = doorY)
            }
        }
        return result
    }

    /**
     * Phân tích section MineData — Eve_MineData.lua
     *
     * DVT client định nghĩa EEventTrigger.Mine=7 và ghi rõ do server kích hoạt.
     * Độ dài thay đổi: id(2)+eventCount(2)+events[n](n×1)+grid(16)+sizeKind(1)
     */
    private fun parseMineSection(reader: DatReader): Map<Int, EveMinePlacement> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveMinePlacement>(count)
        repeat(count) {
            val id = reader.readUInt16()
            val eventCount = reader.readUInt16()
            val events = ArrayList<Int>(eventCount)
            repeat(eventCount) { events.add(reader.readByte()) }
            val gridX = reader.readInt32()
            val gridY = reader.readInt32()
            val gridWidth = reader.readInt32()
            val gridHeight = reader.readInt32()
            val sizeKind = reader.readByte()

            if (id > 0) {
                result[id] = EveMinePlacement(
                    id = id,
                    events = events,
                    gridX = gridX,
                    gridY = gridY,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    sizeKind = sizeKind
                )
            }
        }
        return result
    }

    /**
     * Phân tích section SurfaceData — Eve_SurfaceData.lua
     *
     * Độ dài thay đổi: id(2)+unused(2)+sentenceCount(1)+optionIndex(1)+optionCount(1)+optionMode(1)+sentences[n](n×5)
     * Mỗi sentence: dataId(2)+dataKind(1)+style(1)+canCut(1)
     *
     * Căn chỉnh Lua: bỏ qua các câu có style=3 && dataKind=1 && dataId=20393 (Eve_SurfaceData.lua dòng 27-31)
     *
     * @return ánh xạ id → [EveSurfaceData]
     */
    private fun parseSurfaceSection(reader: DatReader): Map<Int, EveSurfaceData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveSurfaceData>(count)
        repeat(count) {
            val id = reader.readUInt16()
            reader.readUInt16()              // Trường bãi bỏ (số lần liên kết, không còn dùng)
            val sentenceCount = reader.readByte()
            val optionIndex = reader.readByte()
            val optionCount = reader.readByte()
            val optionMode = reader.readByte()

            val sentences = LinkedHashMap<Int, EveSentence>()
            var ignoreCount = 0
            repeat(sentenceCount) { i ->
                val dataId = reader.readUInt16()
                val dataKind = reader.readByte()
                val style = reader.readByte()
                val canCut = reader.readBoolean()

                // Căn theo Lua: bỏ qua style=3 && dataKind=1 && dataId=20393
                if (style == 3 && dataKind == 1 && dataId == 20393) {
                    ignoreCount++
                } else {
                    sentences[i + 1] = EveSentence(dataId, dataKind, style, canCut)
                }
            }

            val adjustedCount = sentenceCount - ignoreCount
            if (id > 0) {
                result[id] = EveSurfaceData(id, adjustedCount, optionIndex, optionCount, optionMode, sentences)
            }
        }

        logger.debug { "SurfaceData: nạp xong ${result.size} dữ liệu tương tác" }
        return result
    }

    /**
     * Phân tích section SceneInfoData — Eve_SceneInfoData.lua
     *
     * Cố định 39B/record:
     * EveNo(2)+BackGroundNo(2)+EveFileName(2)+PlayerAppearX(4)+PlayerAppearY(4)+
     * Direction(1)+AreaName[21](21)+LinkCount(2)+SceneEffect(1)
     *
     * Khi resultType=2 (Door dịch chuyển), result.parameter = giá khoá EveNo của SceneInfo.
     *
     * @return ánh xạ eveNo → [EveSceneInfo]
     */
    private fun parseSceneInfoSection(reader: DatReader): Map<Int, EveSceneInfo> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveSceneInfo>(count)
        repeat(count) {
            val eveNo = reader.readUInt16()
            val backgroundNo = reader.readUInt16()
            reader.readUInt16()         // eveFileName (server không cần)
            val playerAppearX = reader.readInt32()
            val playerAppearY = reader.readInt32()
            val direction = reader.readByte()
            reader.readBytes(21)        // areaName (server không cần)
            reader.readUInt16()         // linkCount
            reader.readByte()           // sceneEffect

            if (eveNo > 0) {
                result[eveNo] = EveSceneInfo(eveNo, backgroundNo, playerAppearX, playerAppearY, direction)
            }
        }
        return result
    }

    /**
     * Phân tích section GroupData — Eve_GroupData.lua
     *
     * Cơ chế phân nhóm kết quả: khi EveResult.resultGroupNo > 0 thì tham chiếu bảng này.
     * Server dựa vào ProbabilityRateAy chọn một resultNo từ MemberNoAy.
     *
     * Độ dài thay đổi: EveNo(2)+EventNo(2)+ConditionNo(1)+UseMode(1)+MemberNum(1)+
     * MemberNoAy[n](n×1)+ProbabilityRateAy[n](n×1)+PickMember(1)
     *
     * @return ánh xạ eveNo → [EveGroupData]
     */
    private fun parseGroupSection(reader: DatReader): Map<Int, EveGroupData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveGroupData>(count)
        repeat(count) {
            val eveNo = reader.readUInt16()
            val eventNo = reader.readUInt16()
            val conditionNo = reader.readByte()
            val useMode = reader.readByte()
            val memberNum = reader.readByte()
            val memberNoAy = (1..memberNum).map { reader.readByte() }
            val probabilityRateAy = (1..memberNum).map { reader.readByte() }
            val pickMember = reader.readByte()

            if (eveNo > 0) {
                result[eveNo] = EveGroupData(
                    eveNo = eveNo,
                    eventNo = eventNo,
                    conditionNo = conditionNo,
                    useMode = useMode,
                    memberNoAy = memberNoAy,
                    probabilityRateAy = probabilityRateAy,
                    pickMember = pickMember
                )
            }
        }

        logger.debug { "GroupData: nạp xong ${result.size} dữ liệu nhóm" }
        return result
    }

    // ────────────────────────────────────────────
    // Parse NpcEventData (Eve_NpcEventData.lua)
    // ────────────────────────────────────────────

    /**
     * Phân tích section NpcEventData
     *
     * Thứ tự trường đối chiếu nghiêm ngặt Eve_NpcEventData.lua:
     * ```
     * EveNo(2) + EventName[9](9) + WhenHappen(4×Boolean)
     * ConditionArrayNum(1) + ConditionArray[]:
     *   CondtionNo(1) + ConditionClass(1) + Parameter(2) + ParameterStyle(1)
     *   + ConditionOps(1) + ConditionValue(4) + CTimeValue(8) + ToResult(1)
     *   + AndNum(1) + ConditionSubItem(2) + ResultArrayNum(1) + ResultArray[]:
     *     ResultGroupNo(2)+ResultNo(1)+ResultType(1)+ResultClass(1)
     *     +Parameter(2)+ParamterStyle(1)+ResultValue(4)+ResultMeanNo(2)
     * ```
     */
    private fun parseNpcEventSection(reader: DatReader): Map<Int, NpcEventData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, NpcEventData>(count)

        repeat(count) {
            val eveNo = reader.readUInt16()
            reader.readBytes(9)              // EventName[9]
            // WhenHappen (4 × 1B Boolean): cờ thời điểm kích hoạt
            val onClickObj  = reader.readByte() != 0   // [0] OnClickObject（ClickNpc）
            val onStrokeObj = reader.readByte() != 0   // [1] OnStrokeObject（BumpNpc）
            val onIntoArea  = reader.readByte() != 0   // [2] OnIntoObjectArea（ScanNpc）
            val onSerStroke = reader.readByte() != 0   // [3] OnSerStrokeObject (va chạm phía server)
            val whenHappen  = listOf(onClickObj, onStrokeObj, onIntoArea, onSerStroke)

            val conditionCount = reader.readByte()
            val conditions = ArrayList<EveCondition>(conditionCount)

            repeat(conditionCount) {
                val conditionNo = reader.readByte()
                val conditionClass = reader.readByte()
                val conditionParameter = reader.readUInt16()  // Parameter (khi conditionClass=2 thì là ID nhiệm vụ)
                val conditionParameterStyle = reader.readByte()  // ParameterStyle
                val conditionOps = reader.readByte()
                val conditionValue = reader.readInt32()
                reader.readDouble()              // CTimeValue（8B Double）
                val toResult = reader.readByte()  // ToResult (nếu điều kiện này thỏa thì nhảy đến kết quả nào)
                val andNum = reader.readByte()    // AndNum (>0 có nghĩa là AND với N điều kiện phía sau)
                val conditionSubItem = reader.readUInt16()  // ConditionSubItem

                val resultCount = reader.readByte()
                val results = ArrayList<EveResult>(resultCount)

                repeat(resultCount) {
                    val resultGroupNo = reader.readUInt16()
                    val resultNo = reader.readByte()
                    val resultType = reader.readByte()
                    val resultClass = reader.readByte()
                    val parameter = reader.readUInt16()
                    val parameterStyle = reader.readByte()
                    val resultValue = reader.readInt32()
                    val resultMeanNo = reader.readUInt16()

                    results.add(EveResult(
                        resultGroupNo = resultGroupNo,
                        resultNo = resultNo,
                        resultType = resultType,
                        resultClass = resultClass,
                        parameter = parameter,
                        parameterStyle = parameterStyle,
                        resultValue = resultValue,
                        resultMeanNo = resultMeanNo
                    ))
                }

                conditions.add(EveCondition(
                    conditionNo = conditionNo,
                    conditionClass = conditionClass,
                    conditionParameter = conditionParameter,
                    conditionParameterStyle = conditionParameterStyle,
                    conditionOps = conditionOps,
                    conditionValue = conditionValue,
                    conditionSubItem = conditionSubItem,
                    toResult = toResult,
                    andNum = andNum,
                    results = results
                ))
            }

            if (eveNo > 0) {
                result[eveNo] = NpcEventData(eveNo = eveNo, whenHappen = whenHappen, conditions = conditions)
            }
        }

        return result
    }

    // ────────────────────────────────────────────
    // Parse FightData (Eve_FightData.lua)
    // ────────────────────────────────────────────

    /**
     * Phân tích section FightData
     *
     * Thứ tự trường đối chiếu nghiêm ngặt Eve_FightData.lua FightData.New():
     * ```
     * EveNo(2) + FightType(1) + BKMusic(1) + LinkToResult(1)
     * + LeftEnemyArrayNum(2) + LeftEnemy[]{NO(2)+NPCID(2)+LocationPos(1)+AI(1)}
     * + RightEnemyArrayNum(2) + RightEnemy[]{như trên}
     * + FightEventArrayNum(2) + FightEvent[]{EventNo(2)+EventName[9]+WinConditions[]}
     * + FightLimit(4)
     * ```
     *
     * @return ánh xạ eveNo → [EveFightData]
     */
    private fun parseFightDataSection(reader: DatReader): Map<Int, EveFightData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveFightData>(count)

        repeat(count) {
            val eveNo = reader.readUInt16()
            val fightType = reader.readByte()
            reader.readByte()                   // BKMusic (dùng cho client, server không cần)
            val linkToResult = reader.readByte()

            // LeftEnemyAy (nhóm kẻ địch chính)
            val leftCount = reader.readUInt16()
            val leftEnemies = ArrayList<EveFightEnemy>(leftCount)
            repeat(leftCount) {
                val no = reader.readUInt16()
                val npcId = reader.readUInt16()
                val locationPos = reader.readByte()
                val ai = reader.readByte()
                leftEnemies.add(EveFightEnemy(no, npcId, locationPos, ai))
            }

            // RightEnemyAy (nhóm phụ)
            val rightCount = reader.readUInt16()
            val rightEnemies = ArrayList<EveFightEnemy>(rightCount)
            repeat(rightCount) {
                val no = reader.readUInt16()
                val npcId = reader.readUInt16()
                val locationPos = reader.readByte()
                val ai = reader.readByte()
                rightEnemies.add(EveFightEnemy(no, npcId, locationPos, ai))
            }

            // FightEventAy (điều kiện sự kiện chiến đấu — bỏ qua, server hiện không cần)
            val fightEventCount = reader.readUInt16()
            repeat(fightEventCount) {
                reader.readUInt16()             // EventNo
                reader.readBytes(9)             // EventName[9]
                val winCondCount = reader.readByte()
                repeat(winCondCount) {
                    reader.readByte()           // ConditionNo
                    reader.readByte()           // ConditionClass
                    reader.readUInt16()         // Parameter
                    reader.readByte()           // ParameterStyle
                    reader.readInt32()          // ConditionValue
                    reader.readByte()           // AndNum
                    reader.readUInt16()         // LeaderNo
                    reader.readByte()           // WinConditionOps
                }
            }

            val fightLimit = reader.readUInt32().toLong()

            if (eveNo > 0 && (leftEnemies.isNotEmpty() || rightEnemies.isNotEmpty())) {
                result[eveNo] = EveFightData(
                    eveNo = eveNo,
                    fightType = fightType,
                    linkToResult = linkToResult,
                    leftEnemies = leftEnemies,
                    rightEnemies = rightEnemies,
                    fightLimit = fightLimit
                )
            }
        }

        logger.debug { "FightData: nạp xong cấu hình ${result.size} trận chiến" }
        return result
    }

    // ────────────────────────────────────────────
    // Phân tích thống kê conditionClass (chạy một lần lúc khởi động)
    // ────────────────────────────────────────────

    // TODO: phân tích thống kê conditionClass (chưa cài đặt)
}

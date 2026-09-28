package tw.tsm.data.loaders

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.data.DatReader
import tw.tsm.domain.data.*

private val logger = KotlinLogging.logger {}

/**
 * Eve.emg 事件資料載入器 — 對應客戶端 `DataManager.lua` 的 Eve.emg 讀取邏輯
 *
 * 容器格式（DataManager.lua 逆向）：
 * ```
 * [UInt16 LE] 場景數量（如 3823）
 * 每個場景目錄項 32 bytes：
 *   [1B] 名稱長度（如 9）
 *   [23B] 名稱（"10701.eve\0..."，固定 23 bytes）
 *   [Int32 LE] 場景資料的絕對偏移（從檔案起始）
 *   [Int32 LE] 場景資料大小（bytes）
 * ```
 *
 * 場景 ID = 名稱去掉 ".eve" 後綴後轉整數（"10701.eve" → 10701）
 *
 * 每個場景資料格式（EventData.lua）：
 * ```
 * [Int32]  NpcData 數量  + NpcData[]
 * [UInt16] GoodsData 數量 + GoodsData[]   ← 跳過
 * [UInt16] DoorData 數量  + DoorData[]    ← 跳過
 * [UInt16] MineData 數量  + MineData[]    ← 跳過
 * [UInt16] SurfaceData 數量 + SurfaceData[] ← 跳過
 * [UInt16] SceneInfoData 數量 + SceneInfoData[] ← 跳過
 * [UInt16] GroupData 數量  + GroupData[]  ← 解析（結果分組選擇）
 * [UInt16] NpcEventData 數量 + NpcEventData[]  ← 解析
 * [UInt16] FightData 數量 + FightData[]   ← 解析（事件戰鬥敵方配置）
 * ```
 */
object EveDataLoader {

    /**
     * 載入 Eve.emg 並回傳場景 ID → [SceneEveData] 映射
     *
     * @param data Eve.emg 完整二進位內容
     */
    fun load(data: ByteArray): Map<Int, SceneEveData> {
        val reader = DatReader(data)

        // ── 讀取場景目錄 ──
        val sceneCount = reader.readUInt16()
        logger.debug { "Eve.emg：場景數量 = $sceneCount" }

        data class SceneEntry(val sceneId: Int, val position: Int, val size: Int)

        val entries = ArrayList<SceneEntry>(sceneCount)
        repeat(sceneCount) {
            val nameLen = reader.readByte()          // [1B] 名稱長度
            val nameBytes = reader.readBytes(23)     // [23B] 固定名稱欄位
            val position = reader.readInt32()        // [4B] 絕對偏移
            val size = reader.readInt32()            // [4B] 大小

            // 取有效名稱部分（nameLen 個 bytes），去掉 ".eve"
            val name = String(nameBytes, 0, nameLen.coerceAtMost(23), Charsets.US_ASCII)
            val sceneId = name.removeSuffix(".eve").toIntOrNull() ?: return@repeat

            if (position > 0 && size > 0) {
                entries.add(SceneEntry(sceneId, position, size))
            }
        }

        // ── 解析每個場景 ──
        val result = HashMap<Int, SceneEveData>(entries.size)
        var parseErrors = 0

        for (entry in entries) {
            try {
                // DataManager.lua 第 773 行：position = ReadInt32() + 103
                // 場景 block 前 103 bytes 是標頭，EventData.New 從 +103 開始讀
                reader.seek(entry.position + 103)
                val sceneData = parseSceneData(reader)
                if (sceneData.npcs.isNotEmpty() || sceneData.doors.isNotEmpty() || sceneData.npcEvents.isNotEmpty()) {
                    result[entry.sceneId] = sceneData
                }
            } catch (e: Exception) {
                parseErrors++
                if (parseErrors <= 5) {
                    logger.warn { "場景 ${entry.sceneId} 解析失敗（offset=${entry.position}）：${e.message}" }
                }
            }
        }

        if (parseErrors > 5) {
            logger.warn { "... 共 $parseErrors 個場景解析失敗（只顯示前 5 個）" }
        }

        return result
    }

    // ── 場景資料解析 ──

    /**
     * 解析單一場景的事件資料
     */
    private fun parseSceneData(reader: DatReader): SceneEveData {
        // [1] NpcData（Int32 count）
        val npcs = parseNpcDataSection(reader)

        // [2] GoodsData（UInt16 count，固定 13B/record）
        skipGoodsSection(reader)

        // [3] DoorData（UInt16 count，可變長）
        val doors = parseDoorSection(reader)

        // [4] MineData（UInt16 count，可變長）— server-triggered encounter zones
        val mines = parseMineSection(reader)

        // [5] SurfaceData（UInt16 count，可變長）
        val surfaceDatas = parseSurfaceSection(reader)

        // [6] SceneInfoData（UInt16 count，固定 39B/record）— Door 傳送目的地
        val sceneInfos = parseSceneInfoSection(reader)

        // [7] GroupData（UInt16 count，可變長）— 結果分組選擇
        val groupDatas = parseGroupSection(reader)

        // [8] NpcEventData（UInt16 count）← 解析
        val npcEvents = parseNpcEventSection(reader)

        // [9] FightData（UInt16 count）← 解析（事件戰鬥敵方配置）
        val fightDatas = parseFightDataSection(reader)

        return SceneEveData(npcs, doors, npcEvents, sceneInfos, fightDatas, surfaceDatas, groupDatas, mines)
    }

    // ────────────────────────────────────────────
    // NpcData 解析（Eve_NpcData.lua）
    // ────────────────────────────────────────────

    /**
     * 解析 NpcData 區段
     *
     * 欄位順序嚴格對照 Eve_NpcData.lua（逐行 1:1 對應）：
     * ```
     * id(UInt16) npcId(UInt16)
     * eventCount(UInt16) events[n](Byte each)
     * saleKindCount(Byte) saleKinds[n](Byte each)
     * motionNodeCount(Byte) motionNodes[0..n]{Int32 Int32}（共 n+1 個）
     * motionType(Byte) motionBack(Byte) motionCycleNum(Byte) direction(Byte)
     * motionSuspendMS(UInt16) motionSpeedLv(Byte)
     * roleGrid{Int32×4} moveOffsetGrid{Int32×2} position{Int32×2}
     * roleStatus{isHide(1) isVisible(1) rideNpcId(UInt16) canGrow(1) isLie(1)}
     * innerNode{Int32×4} outerNode{Int32×4}
     * traceSpeedLv(Byte) traceRadius(UInt16) close(Byte)
     * ```
     */
    private fun parseNpcDataSection(reader: DatReader): Map<Int, EveNpcPlacement> {
        val count = reader.readInt32()   // NpcData 用 Int32
        if (count <= 0) return emptyMap()

        val result = LinkedHashMap<Int, EveNpcPlacement>(count)

        repeat(count) {
            val id = reader.readUInt16()
            val npcId = reader.readUInt16()

            // eventCount：Lua ReadUInt16（不是 Byte）
            val eventCount = reader.readUInt16()
            val events = ArrayList<Int>(eventCount)
            // events[n]：Lua ReadByte（不是 UInt16）
            repeat(eventCount) { events.add(reader.readByte()) }

            val saleKindCount = reader.readByte()
            repeat(saleKindCount) { reader.readByte() }

            // 巡邏路點：Lua for i=0, motionNodeCount → 共 motionNodeCount+1 個節點
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

            // roleGrid：4 × Int32 = 16B（不使用）
            repeat(4) { reader.readInt32() }

            // moveOffsetGrid：2 × Int32 = 8B（不使用）
            repeat(2) { reader.readInt32() }

            // position：2 × Int32 = 8B（NPC 座標）
            val posX = reader.readInt32()
            val posY = reader.readInt32()

            // roleStatus（6B）：isHide(1) + isVisible(1) + rideNpcId(2) + canGrow(1) + isLie(1)
            reader.readByte()       // isHide
            reader.readByte()       // isVisible
            reader.readUInt16()     // rideNpcId
            val canGrow = reader.readByte()  // canGrow
            reader.readByte()       // isLie

            // innerNode：4 × Int32 = 16B（不使用）
            repeat(4) { reader.readInt32() }

            // outerNode：4 × Int32 = 16B（不使用）
            repeat(4) { reader.readInt32() }

            reader.readByte()       // traceSpeedLv（不使用）
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
                    canGrow = canGrow
                )
            }
        }

        return result
    }

    // ────────────────────────────────────────────
    // Skip 區段
    // ────────────────────────────────────────────

    /**
     * 跳過 GoodsData 區段 — Eve_GoodsData.lua
     *
     * 固定 13B/record：NO(1)+DBID(2)+PosX(4)+PosY(4)+HideTime(2)
     */
    private fun skipGoodsSection(reader: DatReader) {
        val count = reader.readUInt16()
        repeat(count) { reader.readBytes(13) }
    }

    /**
     * 解析 DoorData 區段 — Eve_DoorData.lua
     *
     * 可變長：id(2)+eventCount(2)+events[n](n×1)+grid(16)+imageKind(1)+pos(4)+close(1)
     *
     * Door 和 NPC 共享同一個 NpcEventData 表（eveNo 引用），
     * 但各自有獨立的 ID 空間（Door.id ≠ NPC.clickId）。
     *
     * @return doorId → [EveDoorPlacement] 映射
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
            // grid：4 × Int32（gridX, gridY, gridWidth, gridHeight）— 門碰撞區域（格子單位）
            val gridX = reader.readInt32()
            val gridY = reader.readInt32()
            val gridW = reader.readInt32()
            val gridH = reader.readInt32()
            reader.readByte()               // imageKind
            reader.readUInt16()             // imgInfo.position.x（圖片顯示座標，非碰撞座標）
            reader.readUInt16()             // imgInfo.position.y
            // 門中心像素座標 = grid 中心 × BLOCK_UNIT(20)，與客戶端 DoorManager.lua 一致
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
     * 解析 MineData 區段 — Eve_MineData.lua
     *
     * DVT client 定義 EEventTrigger.Mine=7 並註明由 server kích hoạt.
     * 可變長：id(2)+eventCount(2)+events[n](n×1)+grid(16)+sizeKind(1)
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
     * 解析 SurfaceData 區段 — Eve_SurfaceData.lua
     *
     * 可變長：id(2)+unused(2)+sentenceCount(1)+optionIndex(1)+optionCount(1)+optionMode(1)+sentences[n](n×5)
     * 每個 sentence：dataId(2)+dataKind(1)+style(1)+canCut(1)
     *
     * 對齊 Lua：跳過 style=3 && dataKind=1 && dataId=20393 的句子（Eve_SurfaceData.lua 第 27-31 行）
     *
     * @return id → [EveSurfaceData] 映射
     */
    private fun parseSurfaceSection(reader: DatReader): Map<Int, EveSurfaceData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveSurfaceData>(count)
        repeat(count) {
            val id = reader.readUInt16()
            reader.readUInt16()              // 廢棄欄位（關聯次數，沒有在用）
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

                // 對齊 Lua：跳過 style=3 && dataKind=1 && dataId=20393
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

        logger.debug { "SurfaceData：${result.size} 筆互動資料載入完成" }
        return result
    }

    /**
     * 解析 SceneInfoData 區段 — Eve_SceneInfoData.lua
     *
     * 固定 39B/record：
     * EveNo(2)+BackGroundNo(2)+EveFileName(2)+PlayerAppearX(4)+PlayerAppearY(4)+
     * Direction(1)+AreaName[21](21)+LinkCount(2)+SceneEffect(1)
     *
     * resultType=2（Door 傳送）時，result.parameter = SceneInfo 的 EveNo 鍵值。
     *
     * @return eveNo → [EveSceneInfo] 映射
     */
    private fun parseSceneInfoSection(reader: DatReader): Map<Int, EveSceneInfo> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveSceneInfo>(count)
        repeat(count) {
            val eveNo = reader.readUInt16()
            val backgroundNo = reader.readUInt16()
            reader.readUInt16()         // eveFileName（伺服器不需要）
            val playerAppearX = reader.readInt32()
            val playerAppearY = reader.readInt32()
            val direction = reader.readByte()
            reader.readBytes(21)        // areaName（伺服器不需要）
            reader.readUInt16()         // linkCount
            reader.readByte()           // sceneEffect

            if (eveNo > 0) {
                result[eveNo] = EveSceneInfo(eveNo, backgroundNo, playerAppearX, playerAppearY, direction)
            }
        }
        return result
    }

    /**
     * 解析 GroupData 區段 — Eve_GroupData.lua
     *
     * 結果分組機制：EveResult.resultGroupNo > 0 時引用此表。
     * 伺服器依 ProbabilityRateAy 從 MemberNoAy 中選一個 resultNo。
     *
     * 可變長：EveNo(2)+EventNo(2)+ConditionNo(1)+UseMode(1)+MemberNum(1)+
     * MemberNoAy[n](n×1)+ProbabilityRateAy[n](n×1)+PickMember(1)
     *
     * @return eveNo → [EveGroupData] 映射
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

        logger.debug { "GroupData：${result.size} 筆分組資料載入完成" }
        return result
    }

    // ────────────────────────────────────────────
    // NpcEventData 解析（Eve_NpcEventData.lua）
    // ────────────────────────────────────────────

    /**
     * 解析 NpcEventData 區段
     *
     * 欄位順序嚴格對照 Eve_NpcEventData.lua：
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
            // WhenHappen（4 × 1B Boolean）：觸發時機旗標
            val onClickObj  = reader.readByte() != 0   // [0] OnClickObject（ClickNpc）
            val onStrokeObj = reader.readByte() != 0   // [1] OnStrokeObject（BumpNpc）
            val onIntoArea  = reader.readByte() != 0   // [2] OnIntoObjectArea（ScanNpc）
            val onSerStroke = reader.readByte() != 0   // [3] OnSerStrokeObject（伺服器碰撞）
            val whenHappen  = listOf(onClickObj, onStrokeObj, onIntoArea, onSerStroke)

            val conditionCount = reader.readByte()
            val conditions = ArrayList<EveCondition>(conditionCount)

            repeat(conditionCount) {
                val conditionNo = reader.readByte()
                val conditionClass = reader.readByte()
                val conditionParameter = reader.readUInt16()  // Parameter（conditionClass=2 時為任務 ID）
                val conditionParameterStyle = reader.readByte()  // ParameterStyle
                val conditionOps = reader.readByte()
                val conditionValue = reader.readInt32()
                reader.readDouble()              // CTimeValue（8B Double）
                val toResult = reader.readByte()  // ToResult（若此條件符合，跳到哪個結果）
                val andNum = reader.readByte()    // AndNum（>0 表示與後 N 個條件 AND）
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
    // FightData 解析（Eve_FightData.lua）
    // ────────────────────────────────────────────

    /**
     * 解析 FightData 區段
     *
     * 欄位順序嚴格對照 Eve_FightData.lua FightData.New()：
     * ```
     * EveNo(2) + FightType(1) + BKMusic(1) + LinkToResult(1)
     * + LeftEnemyArrayNum(2) + LeftEnemy[]{NO(2)+NPCID(2)+LocationPos(1)+AI(1)}
     * + RightEnemyArrayNum(2) + RightEnemy[]{同上}
     * + FightEventArrayNum(2) + FightEvent[]{EventNo(2)+EventName[9]+WinConditions[]}
     * + FightLimit(4)
     * ```
     *
     * @return eveNo → [EveFightData] 映射
     */
    private fun parseFightDataSection(reader: DatReader): Map<Int, EveFightData> {
        val count = reader.readUInt16()
        if (count == 0) return emptyMap()

        val result = LinkedHashMap<Int, EveFightData>(count)

        repeat(count) {
            val eveNo = reader.readUInt16()
            val fightType = reader.readByte()
            reader.readByte()                   // BKMusic（客戶端用，伺服器不需要）
            val linkToResult = reader.readByte()

            // LeftEnemyAy（主要敵方群組）
            val leftCount = reader.readUInt16()
            val leftEnemies = ArrayList<EveFightEnemy>(leftCount)
            repeat(leftCount) {
                val no = reader.readUInt16()
                val npcId = reader.readUInt16()
                val locationPos = reader.readByte()
                val ai = reader.readByte()
                leftEnemies.add(EveFightEnemy(no, npcId, locationPos, ai))
            }

            // RightEnemyAy（次要群組）
            val rightCount = reader.readUInt16()
            val rightEnemies = ArrayList<EveFightEnemy>(rightCount)
            repeat(rightCount) {
                val no = reader.readUInt16()
                val npcId = reader.readUInt16()
                val locationPos = reader.readByte()
                val ai = reader.readByte()
                rightEnemies.add(EveFightEnemy(no, npcId, locationPos, ai))
            }

            // FightEventAy（戰鬥事件條件 — 跳過，伺服器目前不需要）
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

        logger.debug { "FightData：${result.size} 場戰鬥配置載入完成" }
        return result
    }

    // ────────────────────────────────────────────
    // conditionClass 統計分析（啟動時一次性）
    // ────────────────────────────────────────────

    // TODO：conditionClass 統計分析（未實作）
}

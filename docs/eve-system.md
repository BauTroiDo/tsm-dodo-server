# Tài liệu kỹ thuật hệ thống sự kiện Eve

> Tham khảo kỹ thuật đầy đủ về hội thoại NPC / thúc đẩy nhiệm vụ / kích hoạt sự kiện của tsm_dodo

---

## 1. Tổng quan hệ thống

### Sơ đồ quan hệ thành phần

```
Client C:020-{1~8}
        │
        ▼
┌──────────────────────────────────┐
│        NpcEventHandler           │  ← Lối vào tầng app, định tuyến 7 loại kích hoạt
│  (mainKind=20, sub=1~9)          │
└──────┬───────────────────────────┘
       │
       │ buildPlayerState()
       ▼
┌────────────────┐  ┌─────────────────┐  ┌─────────────────────┐
│ QuestSystem    │  │ InventorySystem │  │ PetSystem           │
│ (nhiệm vụ/cờ)  │  │ (túi/trang bị)  │  │ (sở hữu tướng)      │
└────────────────┘  └─────────────────┘  └─────────────────────┘
       │                    │                      │
       └────────────────────┼──────────────────────┘
                            ▼
                   PlayerEventState (snapshot)
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
     EveConditionEvaluator  │    EveChainResolver
     (đánh giá điều kiện)   │    (gom chuỗi AND + xếp 4 tầng)
                            │
                            ▼
                   EveEventStateMachine
                   (vòng đời Session + theo dõi hoàn thành)
                            │
                            ▼
                   dispatchResult()
                   (phân luồng resultType → S:020-001/007/008)
```

### Danh sách tệp

| Tệp | Mô-đun | Nhiệm vụ |
|------|------|------|
| `app/.../handlers/NpcEventHandler.kt` | app | Định tuyến gói tin C→S, phân phát kết quả, xử lý nhiệm vụ/vật phẩm/tướng |
| `gamelogic/.../systems/EveConditionEvaluator.kt` | gamelogic | Đánh giá điều kiện kiểu hàm thuần túy(đã quan sát 18 loại conditionClass, trường hợp chưa xác nhận thận trọng trả false) |
| `gamelogic/.../systems/EveChainResolver.kt` | gamelogic | Gom chuỗi AND + khớp theo sắp xếp bốn tầng |
| `gamelogic/.../systems/EveEventStateMachine.kt` | gamelogic | Quản lý Session sự kiện, theo dõi hoàn thành, điền sẵn |
| `gamelogic/.../systems/QuestSystem.kt` | gamelogic | Tăng giảm bước nhiệm vụ, cờ hoàn thành |
| `domain/.../data/EveData.kt` | domain | 15 data class sự kiện |
| `domain/.../data/MarkDef.kt` | domain | Định nghĩa nhiệm vụ Mark_C.dat |
| `data/.../loaders/EveDataLoader.kt` | data | Bộ nạp nhị phân Eve.emg |
| `persistence/.../tables/CharacterMissions.kt` | persistence | Bảng DB nhiệm vụ đang tiến hành |
| `persistence/.../tables/CharacterMissionFlags.kt` | persistence | Bảng DB cờ hoàn thành |

---

## 2. Cấu trúc dữ liệu

### 2.1 Data Class lõi(domain/data/EveData.kt)

#### EveResult — Kết quả sự kiện(ứng với gói tin S:020-001 15 bytes)

| Trường | Kiểu | Diễn giải |
|------|------|------|
| resultGroupNo | Int (2B) | Số nhóm kết quả(>0 tham chiếu phép chọn xác suất của GroupData) |
| resultNo | Int (1B) | Số thứ tự kết quả(đối tượng GroupData chọn) |
| resultType | Int (1B) | Loại kết quả: 0=hành động 1=hội thoại 2=cửa 3=chiến đấu 5=hoạt ảnh 6=tương tác 9=NPC hành động |
| resultClass | Int (1B) | Phân loại kết quả: 1=vật phẩm 2=nhiệm vụ 3=tổ đội NPC 4=kỹ năng 7=người chơi 8=tướng thưởng |
| parameter | Int (2B) | Tham số(missionId / itemId / npcClickId / sceneInfoKey) |
| parameterStyle | Int (1B) | Kiểu tham số(1=tăng 2=giảm/thu hồi 3=hoàn thành) |
| resultValue | Int (4B) | Giá trị(lượng tăng bước / số lượng vật phẩm / cấp kỹ năng) |
| resultMeanNo | Int (2B) | ID hội thoại / fightDataKey / surfaceId |

#### EveCondition — Điều kiện sự kiện

| Trường | Kiểu | Diễn giải |
|------|------|------|
| conditionNo | Int (1B) | Số thứ tự điều kiện |
| conditionClass | Int (1B) | Phân loại điều kiện(§3 trình bày chi tiết) |
| conditionParameter | Int (2B) | Tham số điều kiện(missionId / itemId / chỉ số thuộc tính) |
| conditionParameterStyle | Int (1B) | Kiểu tham số(class=2 pStyle=3 ngữ nghĩa đặc biệt) |
| conditionOps | Int (1B) | Toán tử so sánh(§3.2 trình bày chi tiết) |
| conditionValue | Int (4B) | Giá trị so sánh |
| conditionSubItem | Int (2B) | Hạng mục con |
| toResult | Int (1B) | Vị trí nhảy đến kết quả(dùng cho chuỗi AND) |
| andNum | Int (1B) | Độ dài chuỗi AND(§4.1 trình bày chi tiết) |
| results | List\<EveResult\> | Danh sách kết quả đính kèm điều kiện này |

#### NpcEventData — Sự kiện NPC

| Trường | Diễn giải |
|------|------|
| eveNo | Mã sự kiện |
| whenHappen | Thời điểm kích hoạt [0]=ClickNpc [1]=BumpNpc [2]=ScanNpc [3]=ServerBump |
| conditions | Danh sách điều kiện(đánh giá theo thứ tự) |

#### Bố trí cảnh

| Class | Diễn giải |
|-------|------|
| EveNpcPlacement | Bố trí NPC(id/npcId/events[]/x/y/close) |
| EveDoorPlacement | Bố trí điểm dịch chuyển(id/events[]/x/y) |

#### Thông tin cảnh

| Class | Diễn giải |
|-------|------|
| EveSceneInfo | Đích đến của Door(backgroundNo/playerAppearX/Y/direction) |
| EveFightData | Cấu hình chiến đấu(leftEnemies[]/rightEnemies[]/fightType/fightLimit) |
| EveFightEnemy | Cấu hình địch(npcId/locationPos/ai) |
| EveSurfaceData | Định nghĩa tương tác(sentences[]/optionIndex/optionCount/optionMode) |
| EveSentence | Câu(dataId/dataKind/style/canCut) |
| EveGroupData | Nhóm kết quả(memberNoAy/probabilityRateAy/pickMember/useMode) |

#### Tập hợp dữ liệu cảnh

```kotlin
data class SceneEveData(
    val npcs: Map<Int, EveNpcPlacement>,        // clickId → NPC
    val doors: Map<Int, EveDoorPlacement>,       // doorId → Door
    val npcEvents: Map<Int, NpcEventData>,       // eveNo → sự kiện
    val sceneInfos: Map<Int, EveSceneInfo>,      // eveNo → thông tin cảnh
    val fightDatas: Map<Int, EveFightData>,      // eveNo → cấu hình chiến đấu
    val surfaceDatas: Map<Int, EveSurfaceData>,  // id → Surface
    val groupDatas: Map<Int, EveGroupData>       // eveNo → nhóm
)
```

### 2.2 Định dạng nhị phân Eve.emg

#### Cấu trúc container

```
[UInt16 LE] Số lượng cảnh(ví dụ 3823)
Mục lục 32B của mỗi cảnh:
  [1B]  Độ dài tên
  [23B] Trường tên cố định
  [4B]  Offset tuyệt đối
  [4B]  Kích thước
```

#### Dữ liệu cảnh(sau phần header +103 bytes của offset)

Phân tích tuần tự 9 khu đoạn:

| # | Khu đoạn | Định dạng |
|---|------|------|
| 1 | NpcData | Int32 count → mỗi bản ghi: id(2B) npcId(2B) eventCount(1B) events[](2B each) x(4B) y(4B) close(1B) |
| 2 | GoodsData | Int32 count → mỗi bản ghi 13B(bỏ qua) |
| 3 | DoorData | Int32 count → mỗi bản ghi: id(2B) eventCount(1B) events[](2B each) x(2B) y(2B) |
| 4 | MineData | Int32 count → độ dài biến thiên(bỏ qua) |
| 5 | SurfaceData | UInt16 count → mỗi bản ghi: id(2B) sentenceCount(1B) sentences[]... optionIndex(1B) optionCount(1B) optionMode(1B) |
| 6 | SceneInfoData | UInt16 count → mỗi bản ghi 39B cố định: eveNo(2B) name(9B) bgNo(2B) x(2B) y(2B) direction(1B)... |
| 7 | GroupData | UInt16 count → mỗi bản ghi dài biến thiên |
| 8 | NpcEventData | UInt16 count → mỗi bản ghi: eveNo(2B) name(9B) whenHappen(4×1B) condCount(1B) conditions[]... |
| 9 | FightData | UInt16 count → mỗi bản ghi dài biến thiên |

#### Cấu trúc bên trong điều kiện/kết quả của NpcEventData

```
Condition:
  ConditionNo(1B) Class(1B) Parameter(2B) ParameterStyle(1B)
  Ops(1B) Value(4B) CTimeValue(8B) ToResult(1B) AndNum(1B) SubItem(2B)
  ResultCount(1B) → Result[]:

Result:
  GroupNo(2B) ResultNo(1B) Type(1B) Class(1B)
  Param(2B) PStyle(1B) Value(4B) MeanNo(2B)
```

---

## 3. Hệ thống điều kiện(EveConditionEvaluator)

### 3.1 Logic đánh giá conditionClass

| Class | Tên | Chiều so sánh | Logic đánh giá |
|-------|------|---------|---------|
| **0** | Không điều kiện | — | Luôn `true` |
| **1** | Vật phẩm túi đồ | **Ngược** | `compareStep(conditionValue, ops, bagItems[param] ?: 0)` |
| **2** | Bước nhiệm vụ | Thuận | pStyle=3 → `max(step, flag)`; còn lại → `step`; rồi `compareStep(actual, ops, val)` |
| **3** | Trạng thái có thể tương tác của đối tượng cảnh | Trực tiếp | Chỉ hỗ trợ hình dạng cố định đã quan sát `pStyle=4,ops=5,value=0,subItem=0` |
| **4** | Kỹ năng | Thuận | `skillLevels[param] ?: 0`; `compareStep(actual, ops, val)` |
| **5** | Tiền vàng | Tùy chỉnh | `gold`; `ops=2,value=金額-1` có nghĩa là tiền vàng đạt ngưỡng, `ops=4` thận trọng coi là ít nhất đạt ngưỡng |
| **7** | Thuộc tính/trạng thái | Thuận | Đã hỗ trợ param=0(cấp độ) / 2(không gian túi đồ: pStyle=1 đầy, pStyle=2 còn chỗ trống, pStyle=4 số ô còn lại) / 3(trang bị: có→itemId, không→0) / 5(giới tính: pStyle=1/2) / 6(vị trí tướng) / 16(đang cưỡi thì phải xuống ngựa); param khác thận trọng `false` |
| **8** | Kết quả chiến đấu | Trực tiếp | `pStyle == battleResult`(1=thắng 2=thua 3=bỏ chạy) |
| **9** | Sở hữu tướng | Thuận | Khi `param=0` dùng `conditionValue` làm npcId để kiểm tra tướng mang theo; `param!=0` là trạng thái tướng nâng cao, tạm thời thận trọng `false` |
| **10** | Lựa chọn hội thoại | Trực tiếp | `lastSurfaceId == param && lastChoiceCode == pStyle` |
| **11** | Xác nhận quy trình thu kinh | Trực tiếp | Chỉ hỗ trợ hình dạng cố định đã quan sát `param=0,pStyle=1,ops=0,value=0,subItem=0` |
| **12** | Bộ đếm sự kiện cảnh | Thuận | Đã hỗ trợ `param=0` → số lần hoàn thành eveNo hiện tại; param khác tạm thời thận trọng `false` |
| **13** | Quân đoàn/thành trì/di dân | — | Cần hệ thống quân đoàn, thành trì hoặc di dân; hiện tại thận trọng trả về `false` |
| **14** | Chưa xác minh | — | Không được dùng trực tiếp làm RoleCount; hiện tại thận trọng trả về `false` |
| **15** | Chuyển sinh người chơi | Thuận | Đã hỗ trợ `param=0,pStyle=0,subItem=0` → `rebornCount`; `param=1` → mã trạng thái cửa vào nhị chuyển tướng |
| **16** | Vật phẩm kho ngoài túi đồ | Ngược | `pStyle=1,subItem=0` → kiểm tra số lượng vật phẩm ở Tiền Trang + kho báu; không được dùng lại class1 của túi đồ |
| **17** | Hệ thống con cái | — | Con cái tồn tại, cấp độ hoặc trạng thái xuất chiến; cần hệ thống con cái, hiện tại thận trọng `false` |
| **18** | Ứng viên RoleCount | Thuận | Chỉ chỉ số RoleCount đã biết mới đọc `roleCountValues`; khác thận trọng `false` |
| 6 | Chưa cài đặt | — | Thận trọng trả về `false` |

#### Giải thích so sánh ngược

Hiện tại chỉ Class 1 dùng `compareStep(conditionValue, ops, actualValue)` thay vì chiều thuận `compareStep(actualValue, ops, conditionValue)`.

Cách dùng điển hình:
- **Class=1**(vật phẩm túi đồ): `val=0 ops=2` → `0 < qty` → có vật phẩm này
- **Class=1**: `val=0 ops=5` → `0 >= qty` → không có vật phẩm này(qty=0)
- **Class=12**(bộ đếm sự kiện)đã đổi sang so sánh thuận: `count=0 ops=2 val=1` → `0 < 1` → chỉ đạt khi chưa hoàn thành

#### Class=3 trạng thái có thể tương tác của đối tượng cảnh

Trong `Eve.emg`, `conditionClass=3` có tổng cộng 783 bản ghi, toàn bộ là
`pStyle=4, ops=5, value=0, subItem=0`, thường gặp sau khi nhấp đối tượng cảnh
Tặng vật phẩm hoặc tiền vàng, kèm theo hiệu ứng đối tượng cảnh phía client của `resultClass=3 pStyle=5`.
Hiện tại server chỉ trả về `true` cho hình dạng cố định này, các hình dạng class3 khác vẫn thận trọng `false`.

#### Class=7 param=2 Không gian túi đồ

Trong `Eve.emg` có tổng cộng 2972 mục `conditionClass=7, param=2`,
Chủ yếu chia thành:

- `pStyle=1, ops=0, value=0`: túi đồ đã đầy, thường dẫn tới hội thoại"Túi đồ đã đầy".
- `pStyle=2, ops=0, value=0`: túi đồ còn chỗ trống, thường dẫn tới quy trình tặng vật phẩm hoặc nhiệm vụ bình thường.
- `pStyle=4`: So sánh `ops/value` theo số ô còn lại của túi đồ.

Dung lượng túi đồ căn chỉnh theo Lua `Item.RefreshRoleCount()`:
`50 + (RoleCount[103] + RoleCount[106] + RoleCount[124]) * 5`,
Giới hạn trên dùng `ItemConstants.MAX_BAG_SLOTS`.

#### Class=7 param=16 Trạng thái cưỡi thú

Trong `Eve.emg` có tổng cộng 77 mục `conditionClass=7, param=16`,
Toàn bộ là `pStyle=1, ops=0, value=0`, trong đó `subItem=1` luôn nối
Talk 37776"Xin lỗi, bạn xuống ngựa giúp được không?". Server chỉ xét đạt khi người chơi `session.mountNpcId != 0`
và `subItem=1`; `subItem=2` trong mẫu là điều kiện kết quả rỗng, hiện giữ `false` thận trọng.

#### Class=11 Xác nhận quy trình thu kinh

Trong `Eve.emg` có tổng cộng 76 mục `conditionClass=11`, tất cả tập trung ở sự kiện bà thu kinh,
Hình dạng cố định là `param=0, pStyle=1, ops=0, value=0, subItem=0`.
Điều kiện này nằm sau bước chọn Surface, kết quả tiếp theo gồm Talk 10476"★&@#!%......"
cùng kết quả hoàn thành nhiệm vụ. Server chỉ trả `true` cho hình dạng cố định này, còn các hình dạng class11
khác vẫn giữ `false` thận trọng.

#### Class=15 Lối vào chuyển sinh người chơi / tướng

Trong `Eve.emg` có 4 mục `conditionClass=15`. Lối vào chuyển sinh người chơi ở cảnh 55002 dùng:

- `param=0, ops=6, value=0`: khi người chơi đã chuyển sinh, nối Talk giải thích chuyển sinh.
- `param=0, ops=5, value=0`: khi người chơi chưa chuyển sinh, nối `resultType=7/resultClass=7/pStyle=12`.

`pStyle=12` tương ứng Lua `protocolTable[36][11]`, server gửi `S:036-011`
để client thực thi `UI.Open(UITurn, ETrunMode.Reincarnation, 1)`.

Lối vào chuyển sinh lần 2 của tướng ở cảnh 59102 dùng `param=1`, mã trạng thái do Lua
`UINpcTurn.CheckTurn(currentTurn=2)` suy ra:

- Cấp độ tướng đồng hành `>= 60`
- Lòng trung thành `>= 100`
- Trong túi đồ có `item.kind == 51`
- `attr1.kind == 67 && attr1.value == npcId`
- `attr2.kind == 67`

Phía server dùng `npcSecondReincarnationStatus` để biểu thị trạng thái lối vào:

- `0`: ít nhất một tướng đồng hành đủ điều kiện, `ops=5,value=0` mở UI.
- `-1`: không có tướng đủ điều kiện, `ops=6,value=0` hiển thị Talk giải thích.

`pStyle=21` tương ứng Lua `protocolTable[44][3]`, server gửi `S:044-003`
để client thực thi `UI.Open(UINpcTurn, 2)`.

### 3.2 Toán tử compareStep

| ops | Ý nghĩa | Biểu thức |
|-----|------|--------|
| 0 | Bằng(quy ước Eve) | `actual == expected` |
| 1 | Bằng | `actual == expected` |
| 2 | Nhỏ hơn | `actual < expected` |
| 3 | Nhỏ hơn hoặc bằng | `actual <= expected` |
| 4 | Lớn hơn | `actual > expected` |
| 5 | Lớn hơn hoặc bằng | `actual >= expected` |
| 6 | Không bằng | `actual != expected` |

> Lưu ý: ops=0 trong hệ thống Eve là"Bằng", nhưng trong DungeonCriteriaData là `false`.

### 3.3 Ngữ nghĩa đặc biệt Class=2(pStyle=3)

```
pStyle=1/2: chỉ nhìn missionSteps[missionId]
pStyle=3:  actual = max(missionSteps[missionId], missionFlags[missionId])
```

Điều này giúp nhiệm vụ đã hoàn thành(step=0, flag=1)vẫn qua được điều kiện `actual >= 1`.

---

## 4. Phân giải chuỗi AND(EveChainResolver)

### 4.1 buildChains — Gom nhóm chuỗi AND

Trường `andNum` định nghĩa tổng độ dài chuỗi(bao gồm chính điều kiện khởi đầu):

| andNum | Ý nghĩa |
|--------|------|
| 0 | Thuộc chuỗi trước(không nên xuất hiện ở điểm khởi đầu, xử lý biên thành 1) |
| 1 | Điều kiện độc lập |
| 2 | Điều kiện này + điều kiện kế tiếp = AND 2 điều kiện |
| N | Điều kiện này + N-1 điều kiện phía sau = AND N điều kiện |

Ví dụ: `[andNum=2, 0, 2, 0, 1, 1]` → `[[0,1], [2,3], [4], [5]]`

```kotlin
fun buildChains(conditions: List<EveCondition>): List<List<EveCondition>> {
    val chains = mutableListOf<List<EveCondition>>()
    var i = 0
    while (i < conditions.size) {
        val chainLen = conditions[i].andNum.coerceAtLeast(1)
        val chainEnd = minOf(i + chainLen, conditions.size)
        chains.add(conditions.subList(i, chainEnd))
        i = chainEnd
    }
    return chains
}
```

### 4.2 resolve — Khớp theo xếp hạng bốn tầng

Với mọi chuỗi AND khớp, chọn tốt nhất theo thứ tự ưu tiên sau:

| Tầng | Căn cứ xếp hạng | Càng lớn càng tốt | Lý do |
|----|---------|---------|------|
| 1 | **stepScore** — conditionValue cao nhất trong các điều kiện cls=2 | ✓ | Ngưỡng step càng cao = giai đoạn nhiệm vụ càng chính xác |
| 2 | **Độ dài chuỗi** — chain.size | ✓ | Càng nhiều ràng buộc = khớp càng chính xác |
| 3 | **Số lượng results** — head.results.size | ✓ | Handler chính(hội thoại đầy đủ + action)vs fallback(chỉ lời chào) |
| 4 | **Xuất hiện trước** — thứ tự dữ liệu | — | Ưu tiên theo thiết kế |

```kotlin
val stepScore = chain.filter { it.conditionClass == 2 }.maxOfOrNull { it.conditionValue } ?: 0
val isBetter = stepScore > bestStepScore ||
    (stepScore == bestStepScore && chain.size > bestLength) ||
    (stepScore == bestStepScore && chain.size == bestLength && resultCount > bestResultCount)
```

### 4.3 chainFilter — Lọc bổ sung khi đánh giá lại

| Cảnh | chainFilter |
|------|------------|
| Kích hoạt lần đầu | null(không lọc) |
| Sau khi chọn Surface(020-009) | `chain.any { it.conditionClass == 10 }` |
| Sau khi chiến đấu kết thúc | `chain.any { it.conditionClass == 8 }` |

---

## 5. Máy trạng thái sự kiện(EveEventStateMachine)

### 5.1 EventSession

```kotlin
data class EventSession(
    val mapId: Int,                  // Cảnh kích hoạt
    val eveNo: Int,                  // Số hiệu sự kiện(0=Fallback Talk)
    val npcClickId: Int,             // clickId NPC kích hoạt
    val results: List<EveResult>,    // Hàng đợi kết quả
    var currentIndex: Int = 0,       // index sẽ gửi lần tiếp theo
    var lastSurfaceId: Int = -1,     // Surface ID cuối cùng(dùng cho class=10)
    var lastChoiceCode: Int = -1,    // Mã lựa chọn cuối cùng(dùng cho class=10)
    var battleResult: Int = 0,       // Kết quả chiến đấu 0/1/2/3(dùng cho class=8)
    var awaitingChoice: Boolean = false,  // Chờ 020-009
    var awaitingBattle: Boolean = false,  // Chờ chiến đấu kết thúc
    val pendingGuideFlags: MutableList<MissionFlagRecord> = mutableListOf()
)
```

### 5.2 Vòng đời

```
activate()         → Khởi động(ghi đè Session cũ)
advance()          → Lấy result kế tiếp, tăng index
replaceResults()   → Thay hàng đợi results(sau khi Surface/Battle đánh giá lại)
complete()         → Hoàn thành bình thường(ghi eveNo vào completedEvents)
clear()            → Buộc xóa(mất kết nối/lỗi bất thường, không ghi nhận hoàn thành)
```

### 5.3 Theo dõi hoàn thành(completedEvents)

```
Cấu trúc: charId → (mapId → Set<eveNo>)
Công dụng: conditionClass=12 param=0 → dùng cho completedEveNos.size
Hạn chế: thuần bộ nhớ, đăng lại/khởi động lại sẽ về 0 → cần nạp trước để bù
```

- `complete()` ghi eveNo(eveNo=0 không tính)
- `clearSceneEvents()` xóa bản ghi của cảnh chỉ định(gọi khi vào cảnh)
- `getCompletedCount()` / `getCompletedEveNos()` truy vấn

### 5.4 Thuật toán nạp trước(estimateCompletedEvents)

Giải quyết vấn đề completedEveNos về 0 sau khi đăng lại. Khi `buildPlayerState()` suy ra từ tiến độ nhiệm vụ:

```
Với mỗi sự kiện trong bản đồ:
  Với mỗi chuỗi AND:
    Tìm điều kiện cls=2 → tính threshold(bước kích hoạt tối thiểu)
    Tìm kết quả resultClass=2 pStyle=1 cùng missionId → cộng dồn increment
    Nếu currentStep >= threshold + increment → sự kiện đã hoàn thành

    Hoặc: kết quả cls=2 có pStyle=3 và missionFlags[missionId] > 0 → sự kiện đã hoàn thành
```

**Tính threshold**:

| ops | threshold |
|-----|-----------|
| 0, 1 (==) | conditionValue |
| 5 (>=) | conditionValue |
| 4 (>) | conditionValue + 1 |
| 2, 3 (<, <=) | 0 |

**Ví dụ**(10801, missionId=M, currentStep=2):

| eveNo | threshold | increment | threshold+increment | currentStep>=? | Kết quả |
|-------|-----------|-----------|---------------------|---------------|------|
| 2 | 0 (step==0) | 1 | 1 | 2 >= 1 ✓ | completed |
| 1 | 1 (step>=1) | 1 | 2 | 2 >= 2 ✓ | completed |
| 4 | 2 (step>=2) | 1 | 3 | 2 >= 3 ✗ | not completed |

→ completedEveNos = {1, 2}(count=2)

---

## 6. Phân phát kết quả(dispatchResult)

### 6.1 Xử lý resultType

| Type | Tên | Hành vi server |
|------|------|-----------|
| **0** | Hành động | Gửi S:020-001 + chạy logic server theo resultClass(§6.2) |
| **1** | Hội thoại | Gửi S:020-001(client hiển thị hộp thoại Talk) |
| **2** | Cửa dịch chuyển | `handleDoorWarp()` — Quy trình dịch chuyển đầy đủ(§7) |
| **3** | Chiến đấu | `handleBattleTrigger()` — Khởi động PvE(§8) |
| **5** | Hoạt hình | Gửi S:020-001(client phát hoạt hình) |
| **6** | Tương tác | Ghi lastSurfaceId + đặt awaitingChoice → gửi S:020-001 |
| **7** | Server đặc biệt | Không gửi client; server phân luồng theo resultClass/pStyle |
| **8** | Không handler | Bỏ qua im lặng + tự động advance(client không có EventHandler[8]) |
| **9** | Không handler | Bỏ qua im lặng + tự động advance(client không có EventHandler[9]) |
| **10** | Không handler | Bỏ qua im lặng + tự động advance(client không có EventHandler[10]) |

### 6.2 Logic server của resultClass(chỉ khi type=0)

| Class | Tên | pStyle | Hành vi |
|-------|------|--------|------|
| **1** | Vật phẩm | 0=tăng giảm theo dấu của resultValue | `+n` cho / `-n` thu lại, dùng cho trao đổi vật phẩm |
|       |      | 1=tăng/giảm theo dấu của resultValue | `+n` cho / `-n` thu hồi |
|       |      | 2=thu hồi | `inventorySystem.removeFromBag()` → S:015-022 |
| **2** | nhiệm vụ | 1=tăng bước | `questSystem.addMissionStep()` → S:024-001 |
|       |      | 2=giảm bước | `questSystem.decMissionStep()` → S:024-002 (về 0 = hoàn thành) |
|       |      | 3=hoàn thành nhiệm vụ | `questSystem.completeMission()` → S:024-004 + S:024-005 |
|       |      | 4=đặt cờ trực tiếp | `resultValue` là `Mark_C.dat.bitId` → S:024-005 |
|       |      | 5=xoá nhiệm vụ | xoá nhiệm vụ đang chạy, không đặt cờ hoàn thành |
| **3** | tổ đội NPC | 1=thêm vào | `petSystem.addFollowNpc()` → S:016-004 + S:016-005 |
|       |         | 3=ẩn hình / 4=hiện ra / 5=đổi thứ tự ảnh | client tự xử lý |
| **4** | kỹ năng | 1=học/nâng cấp | `skillSystem` cập nhật → S:021-000 |
| **5** | tiền vàng | 1=thêm / 2=khấu trừ | `MoneySystem` cập nhật → S:023-002 / S:023-003 |
| **7** | người chơi | type=7 pStyle=1 | mở kho báu |
|       |      | type=7 pStyle=2 | mở Tiền Trang |
|       |      | type=7 pStyle=3 | mở cửa hàng bán của NPC |
|       |      | type=7 pStyle=4 | mở kho tướng ở lữ quán |
|       |      | type=7 pStyle=5 | xác nhận nghỉ trọ |
|       |      | pStyle=6 | thưởng tiền vàng; xử lý được cả type=0/type=7 |
|       |      | type=7 pStyle=10 | mở bảng xếp hạng Vô Giới → S:036-010 |
|       |      | type=7 pStyle=12 | mở giao diện chuyển sinh của người chơi → S:036-011 |
|       |      | type=7 pStyle=13 | mở cửa hàng đổi điểm diệt địch → S:036-013 |
|       |      | type=7 pStyle=15 | lối vào tiếp tục thủy chiến Phụng Ấp → S:036-015 |
|       |      | type=7 pStyle=21 | mở giao diện chuyển sinh lần 2 của tướng → S:044-003 |
|       |      | type=7 pStyle=27 | thông báo lỗi tìm Ác Ma Ba Đậu Yêu → S:020-027 |
|       |      | type=7 pStyle=29 | thông báo bắt đầu tìm Ác Ma Ba Đậu Yêu → S:020-029 |
|       |      | type=7 pStyle=30 | thông tin sự kiện Giáng Sinh → S:020-030 |
|       |      | type=7 pStyle=32 | thông báo lỗi đấu đài → S:020-032 |
|       |      | type=7 pStyle=35 | thông báo lỗi Tết Nguyên Tiêu → S:020-035 |
|       |      | type=7 pStyle=39 | thông tin sự kiện chưa xác định → S:020-039 |
|       |      | type=0 pStyle=1..5 | chưa xác minh, thận trọng nên không kích hoạt UI đặc biệt |
| **8** | tướng thưởng | 1=thêm vào(canGrow=1) | `petSystem.addFollowNpc()` → S:016-004 + S:016-005 |
|       |      | 2=rời đi | `petSystem.removeFollowNpcByNpcId()` → S:016-006 |
| **10** | marker quy trình | 1/2 | no-op rõ ràng, tự động đẩy tới |

### 6.3 Cơ chế trì hoãn cờ kích hoạt hướng dẫn

Khi resultClass=2 pStyle=1 đẩy nhiệm vụ từ step=0 lên step=1:
1. Kiểm tra `markDefs[missionId].bitId` có hướng dẫn tương ứng (`markFlagToGuideId`) hay không
2. Nếu có → đặt cờ + tạm lưu vào `EventSession.pendingGuideFlags`
3. Khi sự kiện kết thúc (`completeAndEnd`) gửi S:024-005 hàng loạt
4. Bảo đảm UITeach của client không bị UI sự kiện chặn

### 6.4 GroupData chọn theo xác suất

Khi `resultGroupNo > 0` thì tham chiếu GroupData:

```
1. Thu thập tất cả groupNo khác nhau
2. Mỗi group thực hiện một lần chọn (selectMember):
   - pickMember > 0 → lựa chọn mặc định
   - chỉ một thành viên → trả về trực tiếp
   - trọng số đều bằng 0 → giữ tất cả (-1)
   - bình thường → ngẫu nhiên gia quyền theo probabilityRateAy
3. Lọc results: giữ groupNo=0 + giữ resultNo được chọn
```

---

## 7. Quy trình dịch chuyển của Door (handleDoorWarp)

```
1. Gửi S:020-001 (resultType=2 kết quả)
2. Tra sceneInfos[result.parameter] → lấy cảnh/toạ độ đích
3. Gửi S:020-007 (EventChangeScene)
4. worldManager.leaveMap() → worldManager.enterMap()
5. characterRepository.updatePosition() (ghi bền)
6. Gửi S:012-000 (người chơi dịch chuyển tức thời)
7. Gửi S:007-000 (chuyển cảnh)
8. Gửi S:022-004 (MapNpc khởi tạo)
9. Ghi nhận lastDoorWarpMapId = bản đồ cũ, lastDoorWarpTime = now
10. completeAndEnd()
```

### Cơ chế hồi kỹ của MeetDoor

**Mục đích**: chặn việc bật lại ngay khi điểm đến nằm trên cửa về (cửa bounce-back).

```kotlin
if (subKind == 8 &&
    session.mapId == session.lastDoorWarpMapId &&    // ← chỉ chặn khi cùng bản đồ
    System.currentTimeMillis() - session.lastDoorWarpTime < 2000L) {
    // bỏ qua
}
```

- **cùng bản đồ**: hồi kỹ 2 giây sau khi dịch chuyển (chống bật lại)
- **khác bản đồ**: không bị hồi kỹ ảnh hưởng (MeetDoor 10801→10851 vẫn kích hoạt bình thường)

---

## 8. Quy trình chiến đấu (handleBattleTrigger)

```
1. Tra fightDatas[result.resultMeanNo] → lấy cấu hình phe địch
2. Gửi S:020-001 (resultType=3, client SetSession(true))
3. stateMachine.setAwaitingBattle(true)
4. battleManager.createPveBattle()

── chờ chiến đấu kết thúc bất đồng bộ ──

5. callback của BattleManager → resumeAfterBattle(playerWin)
6. Đánh giá lại: chainFilter = { chain.any { it.conditionClass == 8 } }
   - khớp → replaceResults → tiếp tục phân phát
   - không khớp + thắng → autoCaptureFromEvent() + handleNextEvent()
   - không khớp + thua → sự kiện kết thúc
```

### autoCaptureFromEvent

Với trận chiến sự kiện không có chuỗi tiếp theo class=8 (như hướng dẫn bắt tướng):
- quét các npcId mà điều kiện cls=9 trong sự kiện tham chiếu
- tự động thêm vào tổ đội tướng

---

## 9. Hệ thống nhiệm vụ (QuestSystem)

### 9.1 Giao diện thao tác

| phương thức | Lua tương ứng | hành vi |
|------|---------|------|
| `addMissionStep(charId, missionId, delta)` | MarkManager.AddMissionStep | cộng dồn bước, chưa tồn tại thì tạo mới |
| `decMissionStep(charId, missionId, delta)` | MarkManager.DecMissionStep | giảm bước, về 0 = hoàn thành |
| `completeMission(charId, missionId)` | pStyle=3 | hoàn thành trực tiếp (xoá + cờ) |
| `removeMission(charId, missionId)` | — | xoá nhiệm vụ đang chạy |
| `setFlag(charId, bitIndex, bitValue)` | — | đặt cờ hoàn thành |
| `getMissions(charId)` | — | truy vấn nhiệm vụ đang chạy |
| `getFlags(charId)` | — | truy vấn cờ hoàn thành |

### 9.2 Quy trình hoàn thành nhiệm vụ

```
decMissionStep:
  current=0 → không kích hoạt hoàn thành (nhiệm vụ không tồn tại)
  current-delta=0 → removeMission + setFlag(bitId, 1) → completed=true
  current-delta>0 → upsertMission(newStep) → completed=false
```

### 9.3 Cấu trúc bảng lưu bền

**CharacterMissions** (khóa chính: charId + missionId)

| Trường | Kiểu | Diễn giải |
|------|------|------|
| charId | Long | ID nhân vật |
| missionId | Int | ID nhiệm vụ |
| step | Int (default=1) | bước hiện tại |

**CharacterMissionFlags** (khóa chính: charId + bitIndex)

| Trường | Kiểu | Diễn giải |
|------|------|------|
| charId | Long | ID nhân vật |
| bitIndex | Int | Mark_C.dat bitId |
| bitValue | Int (default=1) | giá trị cờ |

### 9.4 Chuỗi truy vấn cờ

```
MarkDef: missionId → bitId
DB:      charId + bitId → bitValue
State:   missionFlags[missionId] = bitValue (buildPlayerState khởi tạo)
```

---

## 10. Sơ đồ quy trình lõi

### 10.1 Sự kiện kích hoạt→hoàn thành

```
Client C:020-{1~8}  (triggerID)
       │
       ├── subKind=4/8 (Door) ─────→ doors[triggerID].events → resolve
       ├── subKind=2 (BumpNpc) ────→ WhenHappen[1] filter → resolve
       ├── subKind=3 (ScanNpc) ────→ WhenHappen[2] filter → resolve
       └── subKind=1 (ClickNpc) ──→ npcs[triggerID].events → resolve
                                     ├── thất bại → BumpNpc fallback
                                     └── thất bại → findFallbackTalk
       │
       ▼ resolveAndActivate()
  buildPlayerState() → EveChainResolver.resolve() → applyGroupData()
       │
       ▼ stateMachine.activate() + advance()
  dispatchResult(first result)
       │
       ├── type=0/1/5 → sendCommonEvent + server logic
       │   └── Client 020-006 → advance() → dispatch next
       │
       ├── type=2 → handleDoorWarp → complete
       │
       ├── type=3 → handleBattleTrigger → await
       │   └── BattleManager callback → resumeAfterBattle → re-evaluate
       │
       ├── type=6 → awaitingChoice=true
       │   └── Client 020-009 → handleEventChoice → re-evaluate
       │
       └── type=8/9/10 → bỏ qua im lặng → auto advance
       │
       ▼ hàng đợi results trống
  completeAndEnd() → sendEventEnd + pending guide flags
```

### 10.2 Quy trình chọn Surface

```
Server: type=6 → setLastSurfaceId + setAwaitingChoice → S:020-001
Client: hiển thị menu → chọn → C:020-009 (code)
Server: setLastChoiceCode(code)
        → buildPlayerState (gồm lastSurfaceId + lastChoiceCode)
        → EveChainResolver.resolve(chainFilter = cls=10)
        → replaceResults → advance → dispatch
```

Bảng đối chiếu mã lựa chọn:
| mã | ý nghĩa |
|---|------|
| 20 | Yes |
| 21 | No |
| 30~39 | Option 1~10 |
| 40 | Cancel |
| 61+ | Option 11+ |

### 10.3 Quy trình khôi phục chiến đấu

```
Server: type=3 → setAwaitingBattle → S:020-001 → createPveBattle
Client: vào chiến đấu
BattleManager: callback → resumeAfterBattle(playerWin)
Server: setBattleResult(1=thắng/2=thua)
        → buildPlayerState (gồm battleResult)
        → EveChainResolver.resolve(chainFilter = cls=8)
        ├── khớp → replaceResults → dispatch
        ├── không khớp + thắng → autoCaptureFromEvent + handleNextEvent
        └── không khớp + thua → clear + sendEventEnd
```

---

## 11. Đối chiếu gói tin C→S / S→C

### C→S(mainKind=20)

| SubKind | tên | dữ liệu |
|---------|------|------|
| 1 | ClickNpc | triggerID (2B LE) |
| 2 | BumpNpc | triggerID (2B LE) |
| 3 | ScanNpc | triggerID (2B LE) |
| 4 | ClickDoor | triggerID (2B LE) |
| 5 | NotInUse | triggerID (2B LE) |
| 6 | NextEvent | trống |
| 7 | Mine | triggerID (2B LE) |
| 8 | MeetDoor | triggerID (2B LE) |
| 9 | EventChoice | code (1B) |

### S→C

| Main-Sub | tên | công dụng |
|----------|------|------|
| 020-001 | CommonEvent | gửi EveResult (15B) |
| 020-007 | EventChangeScene | header chuyển cảnh |
| 020-008 | EventEnd | sự kiện kết thúc |
| 012-000 | PlayerTeleport | Dịch chuyển tức thời |
| 007-000 | SceneChange | Chuyển cảnh |
| 022-004 | MapNpcInit | Khởi tạo bố trí NPC |
| 024-001 | AddMissionStep | Tăng bước nhiệm vụ |
| 024-002 | DecMissionStep | Giảm bước nhiệm vụ |
| 024-004 | RemoveMission | Xoá nhiệm vụ |
| 024-005 | SetMissionFlags | Đặt cờ nhiệm vụ |
| 015-003 | SetBagItem | Cấp vật phẩm |
| 015-022 | ReduceBagItem | Thu hồi vật phẩm |
| 016-004 | FollowNpcAdd | Thêm tướng |
| 016-005 | FollowNpcFullData | Dữ liệu đầy đủ của tướng |
| 016-006 | FollowNpcRemove | Tướng rời đi |
| 021-000 | PlayerSkillData | Dữ liệu kỹ năng |

---

## 12. Các case biên đã biết và cách giải quyết

### 12.1 completedEveNos trở về 0 khi đăng nhập lại

**Vấn đề**: completedEvents thuần bộ nhớ, sau khi đăng nhập lại/khởi động lại thì cls=12 count=0.
**Cách giải quyết**: `estimateCompletedEvents()` mồi sẵn theo tiến độ nhiệm vụ trong `buildPlayerState()`.

### 12.2 Cooldown MeetDoor giữa các bản đồ

**Vấn đề**: cooldown toàn cục 2 giây chặn MeetDoor giữa các bản đồ.
**Cách giải quyết**: thêm trường `lastDoorWarpMapId`, cooldown chỉ có hiệu lực trong cùng bản đồ.

### 12.3 Chuỗi AND khớp đồng điểm

**Vấn đề**: khi stepScore và độ dài chuỗi bằng nhau, câu chào fallback ghi đè handler chính.
**Cách giải quyết**: tiebreaker lớp thứ ba — số results càng nhiều = handler chính.

### 12.4 Fallback Talk

Khi toàn bộ sự kiện NPC của ClickNpc đều không khớp:
1. Thử sự kiện BumpNpc (WhenHappen[1]) có chứa Talk
2. Thử Talk đầu tiên trong sự kiện NPC có resultType=1
3. Không có cái nào → sendEventEnd

### 12.5 type=8/9/10 không handler, bỏ qua âm thầm

Client: `EventHandler.lua` chỉ định nghĩa 0..6. resultType=8/9/10 sẽ xuất hiện trong Eve.emg,
nhưng client không có handler tương ứng; server bỏ qua âm thầm và tự đẩy tới result tiếp theo.

### 12.6 Trì hoãn cờ kích hoạt hướng dẫn

Cờ hướng dẫn kích hoạt khi nhiệm vụ chuyển step=0 → step=1 (S:024-005) được hoãn gửi đến sau khi sự kiện kết thúc, tránh UITeach bị UI sự kiện chặn.

### 12.7 020-006 khi đang chiến đấu

Khi `awaitingBattle=true` bỏ qua 020-006 (NextEvent), tránh vô tình đẩy hoặc hoàn thành sự kiện khi đang chiến đấu.

### 12.8 GroupData toàn bộ trọng số bằng 0

Khi `probabilityRateAy` toàn 0 thì trả về `-1` (giữ toàn bộ thành viên), có nghĩa designer không chỉ định xác suất, các kết quả này là một khối.

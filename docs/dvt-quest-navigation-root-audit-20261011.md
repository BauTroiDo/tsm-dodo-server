# DVT 1.15 — kiểm tra lỗi “Đi đến” / nhiệm vụ kẹt / map chưa mở (2026-10-11)

## Nguồn đối chiếu

- `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115\_lua_client_decrypted` và bản Lua AES giải mã **trực tiếp từ client staging đang chạy**.
- `Data/MarkData.lua`, `Logic/MarkManager.lua`, `Logic/SceneManager.lua`, `Logic/DoorManager.lua`, `Logic/DataManager.lua` trong bản DVT.
- Server Kotlin: `MarkStepDatLoader`, `GameDataManager`, `QuestSystem`, `EveResultExecutor`, `EveTriggerRouter`, `NpcEventHandler`, `TeleportHandler`.
- Server data: `File.dat/Data-ios/IOS_Mark_C.dat`, `IOS_MarkStep_C.dat` và `IOS_DoorGroupData.dat`; client staging có `StreamingAssets/Data/MarkStep_C.dat`.

## 1. Nguyên nhân gốc “Đi đến” không chạy

`MarkData.ReadStep` đặt `condition.areaId=0` và `endAreaId=0` vì .dat **không chứa trường areaId**. `MarkManager.Navigation` chuyển nguyên 0 vào `MarkManager.StartNavigation`, rồi `SceneManager.GetPath(sceneId, areaId, ...)`. Hàm này lập tức `return` nếu `areaId==0`. Trong cùng map, điều kiện tự nói chuyện NPC cũng thất bại vì `SceneManager.areaId` thường >0 nhưng `navigationAreaId==0`.

Vì vậy lỗi này **bắt nguồn ở Lua client**, không phải thiếu gói lệnh Kotlin. Tự tìm đường là `SceneManager.GetPath -> DoorGroupData -> Role.player:Move`; khi tới NPC, `OnInteractive(EInteractive.Trig)` kích hoạt các gói tin C:020 để Kotlin xử lý Eve. Kotlin không thể đơn phương khiến nút “Đi đến” bắt đầu đi nếu client trả về trước khi tính đường.

Đồ thị `IOS_DoorGroupData.dat` server: **3.749 scene, 8.457 liên kết, 46 scene đa khu vực**; phần lớn cổng nối khu vực 1. Không đổi mọi areaId=0 thành 1 cứng cho mọi map. Client fix lấy khu vực từ đồ thị cổng khi đang ở map khác, dùng `DoorManager.GetArea` với tọa độ thế giới khi đã vào map đích và đồng bộ offset bản đồ.

Các file AES mã hóa đã được vá và giải mã kiểm tra vòng lại ở cả:
- `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\AssetBundles\Lua\Logic\MarkManager.lua`
- `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\tsnpc_Data\StreamingAssets\Lua\Logic\MarkManager.lua`

Bản trước sửa ở `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\_quest_navigation_backup_20261011`; mã giải mã để rà soát ở `D:\Games\Ts-Offline\TSMobileOnLine\_quest_navigation_fix_20261011`. GitHub version của Lua cũng được cập nhật trong PR này.

## 2. Ca cụ thể trong ảnh: “Khổng Phương đưa Lê”

`Mark_C` ghi `10074` bitId=0 và `10075` bitId=47. `MarkManager.UpdateShowMission` hiển thị **10075** khi nhiệm vụ thực tế **10074** có bước đang hoạt động; `MarkManager.GetMission(10075)` trả nhiệm vụ đang hoạt động 10074 nếu bitId=0. Không được tự tạo một nhiệm vụ 10075 riêng để “sửa” nút UI.

| Bước thực tế | Điều kiện | Đích điều hướng chính xác |
|---|---|---|
| 10074 bước 1, chưa có item | `30002` thiếu 1/1 | scene 11101, (310,400), NPC event 1 |
| 10074 bước 1, đã đủ item | `30002` có 1/1 | scene 11012, (550,450), NPC event 1 |
| 10074 bước 2 | quay lại báo cáo | scene 11101, (310,400), NPC event 1 |

Hai scene này đều chỉ thuộc khu vực 1 trong `IOS_DoorGroupData.dat`. Có các liên kết 11101→11012 và 11012→11101. Ảnh cho thấy item **1/1**, nên nhấn “Đi đến” phải dùng mục tiêu scene 11012.

## 3. Server Kotlin: khoảng trống dữ liệu

Trước sửa, `MarkStepDatLoader.loadMissionIds` đọc toàn bộ `IOS_MarkStep_C.dat` nhưng chỉ trả `Set<missionId>`. Server mất bước, từng điều kiện, tọa độ, NPC/cổng và điểm trả nhiệm vụ đối với các chức năng kiểm tra parity. Điều này **không trực tiếp gây lỗi tìm đường**, vì client có tập MarkStep của riêng nó, nhưng cản trở xác minh các lỗi “tới nơi không qua bước” và kiểm tra sai địa điểm.

PR này bổ sung `MarkStepDef`, `MarkNavigationTarget`, `MarkStepRequirement`, `MarkStepDatLoader.loadSteps`, `GameDataManager.markSteps`, và `QuestNavigationPolicy`, đối chiếu logic chọn điều kiện chưa đạt của `MarkManager.Navigation`, gồm alias 10075→10074 và tình trạng item 30002. Không tự động dịch chuyển hoặc thay đổi DB của người chơi.

Server hiện có **3.448 Mark_C records và 4.505 MarkStep records (984 nhiệm vụ có bước)**. Có **269 bước với endTarget trống hoặc (0,0)**; chưa được xác định đều là lỗi, vì nhiều bước không yêu cầu điểm hoàn thành. Không tự gán tọa độ hoặc NPC cho các bước này.

## 4. Những lớp lỗi phải rà tiếp

1. Khi đã tới NPC/cửa: đối chiếu `IOS_MarkStep_C` eventKind/eventId, NPC/door entry trong `Eve.emg`, C:020-001/004, `EveTriggerRouter` và `EveConditionResolver`. Kiểm tra `resultClass=2` ADD_STEP/DEC_STEP/COMPLETE và gói S:024 đồng bộ client, cùng `matchedConditionNo` của phiên Eve.
2. Map/world/warp chưa mở: đối chiếu `MarkManager.flags`, `UIWorldMap`, `TeleportHandler.validateWarpMark`, `BitFlagSystem`, packet S:024-005/007, và điều kiện cửa `EveDoorWarpService`. Không mở tất cả map bằng GM bypass hoặc tự nâng cờ.
3. Hệ thống Eve còn chặn có chủ đích các hiệu ứng chưa giải mã chắc chắn: `resultClass` 11/13/15, một số `resultClass=1/pStyle=3`, `conditionClass=17`, và kiểm tra active-time. Không được bịa hành vi phần thưởng hay tự nhảy bước.
4. Kiểm tra sự khác biệt **client MarkStep** và **server IOS_MarkStep** cho toàn bộ 4.505 record, chứ không dựa vào text mô tả; đặc biệt có hai byte MarkStep đã chỉnh ở bản dev từ ngày 10/10.
5. Nếu xử lý đa bước trong Eve fail closed sau khi đã giữ claim, kiểm tra `/claims` trước khi thử lại; không tự xóa claim hoặc cấp bù từ phỏng đoán.

## 5. Full client/server binary parity (checked byte-by-byte)

Two distinct 4,505-row `MarkStep_C.dat` sources were parsed independently, preserving strings and all navigation/condition fields:

- 306 rows differ **before client repair**: 299 changed *description/endGuide* text only (Vietnamese data localization), 7 changed *end navigation targets*; **no rows differed in condition kinds, required item/NPC quantities, or teleport/checkTeam flags**.
- Two real door mismatches were previously corrected on the Kotlin server but not in staging client: `11566/step2` points at Door 7 in scene 25021 (actual Door IDs only 1/2/3), and `11934/step4` points at Door 5 in scene 12431 (actual doors only 1/2/3). Their correct target is Door 3; **2 bytes were patched** in staging client MarkStep.
- Five `11900` rescue-Trương-Bảo steps (1,2,3,4,12) had **no destination in the original client**, but the server already contains valid per-step scene coordinates and NPC/door IDs in scenes 12861, 12581, 12584, 12589, 12242. Each referenced scene exists and its **client/server Eve block is identical**; the server-side read-only target audit found none missing. **39 nonzero bytes** across these five end targets were filled in client only.
- After this **7-target** surgical client fix, *all navigation end targets* match the server's 4,505 rows, while the 299 translated text rows remain untouched. The patch script checks expected targets and refuses unknown differences: `tools/repair_dvt_client_markstep_20261011.py`.
- Original client MarkStep backup: `D:\\Games\\Ts-Offline\\TSNPCVN_9PET_STAGING\\_quest_navigation_backup_20261011\\tsnpc_Data\\StreamingAssets\\Data\\MarkStep_C.dat`. An intermediate backup preserves the state after the two door IDs. Latest client MarkStep SHA-256: `e17ac359391fdaf0d4c57a51876acfb106749768a2e21dfbb1326e732c85f33f`.

Eve.emg client/server both index **3,823 scenes**, of which **27 scene blocks differ**, but **10 differ only in 103-byte header** and 17 have changes in the payload. In particular quest target **scene 11012 has identical NPC/event payload** and only differs in header bytes; scene 11101 is completely identical. There is **no basis to overwrite this Eve data** for mission 10074/10075. The 17 payload variants need a separate event-effect-level audit before any change.

Read-only server NPC/door cross-reference scan (`tools/audit_dvt_quest_navigation.py`) checked **5,716 located target references** against `Eve.emg`, across 1,626 scene blocks: 3,644 NPC targets, 1,244 door targets, and **11 unresolvable references** (1 NPC, 10 doors). These involve `11190, 11908, 12438, 12682`; they have not been auto-corrected, because the original DVT scripts may route some triggers differently. Full report: `C:\\Server-Game\\tsm_dodo\\backups\\quest_nav_audit_20261011\\targets.json`.

## 6. Kotlin C:068 Thiên Hành Dị Năng kiểm tra sai bảng cờ mở khóa

DVT `UITeleport.SetupSkyPointData` dùng `CheckFlag(MarkManager.flags, markDatas[warp.mark].bitId)`. Client `MarkManager.flags` được đồng bộ bằng S:024 và lưu trong Kotlin `character_mission_flags`, thông qua `QuestRepository` / `QuestSystem.hasMissionFlag(charId, missionId)`.

Trước sửa, `TeleportHandler.validateWarpMark` lại truy vấn `BitFlagSystem.getSetFlagIds`, tức `character_bit_flags` — cờ vĩnh viễn `foreverFlags` dành cho hướng dẫn/card/mount, **không phải** cờ hoàn thành nhiệm vụ. Người chơi hoàn thành quest nhưng không có foreverFlag tương ứng sẽ bị từ chối C:068-001 dù client đã mở mốc.

PR sửa `TeleportHandler` inject `QuestSystem` và `WarpMarkUnlockPolicy.isUnlocked` dùng đúng `QuestSystem.hasMissionFlag` cho Mark bitId>0. Giữ nguyên mốc không có mark, điều kiện chống teleport trong battle/party, map 10701 và GM bypass có chủ đích. Regression test xác minh có/không có MissionFlag, mốc miễn điều kiện và Mark ID không tồn tại. Không được đánh đồng tính năng unlock điểm teleport (C:068) với điều kiện mở cửa Eve (C:020) hoặc overlay minimap trong client.

## 7. Nhiệm vụ trong ảnh: NPC và Eve resultClass=2 đã đối chiếu

`gamedata/Eve.emg` scene 11012 NPC event-id 1 là NPC 11002 (tại 590,520), chứa event list [8,12,15]. Nhánh scene11012 event8 có điều kiện nhiệm vụ thực 10074 pStyle1/step1 và kết quả `resultClass=2,param=10074,pStyle=1,value=1` (ADD_STEP). Scene 11101 NPC-id1 là NPC 15083, event2 có các điều kiện 10074/10075 và chuỗi complete/return. Đây là bằng chứng đường NPC→Eve→tăng bước đã có trong dữ liệu; chưa thể khẳng định từng nhánh lựa chọn thông qua đủ điều kiện thời gian/nghề/pet/party nếu chưa đối chiếu state người chơi. Không tự thêm gói packet hoặc tự cấp phần thưởng vào đường NPC này.

## Giới hạn xác nhận

- Đây là đối chiếu mã nguồn gốc, phân tích dữ liệu đường đi và kiểm tra mã hóa sau patch, **chưa có kiểm thử tương tác client thật** sau khi vá.
- Không sửa tùy tiện NPC/Eve hoặc reset tiến độ nhân vật. Bản server Kotlin trong PR cần qua CI, backup/hotfix preservation trước khi triển khai.

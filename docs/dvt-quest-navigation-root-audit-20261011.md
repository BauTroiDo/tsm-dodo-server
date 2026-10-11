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

## Giới hạn xác nhận

- Đây là đối chiếu mã nguồn gốc, phân tích dữ liệu đường đi và kiểm tra mã hóa sau patch, **chưa có kiểm thử tương tác client thật** sau khi vá.
- Không sửa tùy tiện NPC/Eve hoặc reset tiến độ nhân vật. Bản server Kotlin trong PR cần qua CI, backup/hotfix preservation trước khi triển khai.

# Audit chính tuyến DVT PC — kiểm tra dữ liệu và cơ chế server (2026-10-10)

## Nguồn đúng phiên bản

Rà soát **dữ liệu thực tế trên game server đang chạy**, không dùng Eve của TS Classic hoặc CDN Android:

- `gamedata/Eve.emg` SHA256 `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910` (khớp bản DVT PC).
- `File.dat/Data-ios/IOS_Mark_C.dat` SHA256 `6101d852713ce7a7445b4f1e25ce29d139756c9d33bd61853bcadcb529f0c07c`.
- `File.dat/Data-ios/IOS_MarkStep_C.dat` SHA256 `7b43b993ee4b765870faf8d38ea2ce3b9d2bfdb810221030d213197f7ac8f720`.

Bộ phân tích Kotlin/JUnit dùng `EveDataLoader`, `MarkDatLoader`, `DatReader`, chạy offline, không đọc/ghi nhân vật. Báo cáo chi tiết được lưu tại `backups/quest_mainline_audit_20261010/mainline_report.md` trên game server; đây không phải thay đổi live.

## Kết quả

| Kiểm tra | Số lượng |
|---|---:|
| Mark kind=1 (chính tuyến) | 1251 |
| Mainline có MarkStep thật | 400 |
| Tổng bản ghi MarkStep chính tuyến | 2523 |
| Mainline có MarkStep và gate nhưng không có effect nào thay đổi chính nhiệm vụ đó | 0 |
| Mainline có MarkStep và effect nhưng không có gate của chính nhiệm vụ đó | 0 |
| MarkStep không có gate `missionStep == N` chính xác | 6 |
| MarkStep tham chiếu scene không tồn tại | 0 |
| MarkStep endEventKind=Door(2) trỏ tới doorId không tồn tại theo Eve đã nạp | 2 |
| Gate của mainline liên kết trực tiếp với resultClass11/13/15 chưa hỗ trợ hoặc conditionClass17 | 0 theo heuristic đã dùng |

**Không suy ra tất cả 1.251 nhiệm vụ chạy E2E.** 851 mainline không có MarkStep là kiểu marker/đánh dấu hoặc nhiệm vụ không có chỉ dẫn, cần phân loại theo bitId và các tuyến phụ thuộc. 19 mission có gate nhưng không có own-effect và 85 ngược lại thuộc nhóm không có MarkStep, chưa chứng minh bị kẹt.

Sáu bước không có **gate so sánh bằng**:
- mission 10092 step2 scene15301
- mission 10096 step3 scene15012
- mission 10300 step7 scene13003
- mission 11120 step1 và step2 scene12705
- mission 12130 step61 scene14636

Ví dụ mission 11120 dùng điều kiện **step > 0**, không phải thiếu bước. Không được sửa hàng loạt ops.

Hai chỉ dẫn di chuyển đến Door chưa xuất hiện ở Eve:
- mission **11566 step2**: scene25021, `endEventKind=2`, doorId7.
- mission **11934 step4**: scene12431, `endEventKind=2`, doorId5.

DVT Lua `Data/MarkData.lua` có `ENavigationEvent.Door = 2`, `MarkManager.CheckNavigationDoor` đối chiếu `DoorManager.doors[eventId]`. Cần so sánh MarkStep của client chạy thật, vị trí tọa độ và cửa trên bản đồ trước khi đổi chỉ dẫn, không tạo Door/teleport giả.

## Lỗi cơ chế được sửa trong PR cùng đợt

`EveResultExecutor.canExecuteResults` cũ chỉ giả lập toàn chuỗi mission nếu có **ADD_STEP**. Khi chỉ có **DEC_STEP** mà mission không còn trong DB, `QuestSystem.decMissionStep` không làm gì nhưng `EveResultExecutor.handleMissionResult` vẫn gửi gói `S024` giảm bước, gây lệch client/server; sau đó các effect tiếp theo vẫn có thể chạy.

Bổ sung `EveMissionPreflightPolicy`:
- Diễn giải toàn bộ chuỗi ADD/DEC/COMPLETE/REMOVE theo thứ tự trên bản sao bước nhiệm vụ.
- Chặn khi giảm bước dương từ một mission chưa tồn tại; chặn khi cộng tràn giới hạn 255; không gửi packet hoặc mutate kết quả của nhánh đã bị từ chối.
- Cho phép tăng rồi giảm hợp lệ trong một chuỗi và thao tác DEC 0 không gây thay đổi.
- 7 test hồi quy `EveMainlineDecrementPreflightRegressionTest`.
- Không tự sửa dữ liệu tiến độ người chơi đang có.

## Hướng rà soát tiếp theo

1. Lần theo tuyến DVT từ 10821 → 10853 → 12001/12003 và các scene/mission theo MarkStep, replay toàn bộ path **bắt đầu/tiến bước/lựa chọn/battle win-loss/item hand-in/complete** trên một nhân vật thử mới, không dùng reset step của nhân vật thật.
2. Đối chiếu 2 navigation Door sai; xác minh file MarkStep đúng phiên bản PC và tọa độ/nav target.
3. Soát 6 step không có gate bằng, phân biệt compare range và các chain do MarkManager điều kiện phụ.
4. Kiểm tra chuỗi hiệu ứng từng event để tìm **mission bị mutate trước khi reward/battle/warp thất bại**, tiến tới hỗ trợ transaction/compensation.
5. Ghi các trap thực tế theo scene/eve/condition/MarkStep + log khi client gửi C020, viết regression trước khi deploy mỗi patch.
6. Tiếp tục phân loại class13/11 và condition17 theo DVT; không giả mã thưởng của event hoạt động để mở sai nhiệm vụ.

## Tính an toàn

Server live đang có nhiều file local uncommitted từ các phiên trước. Không `git pull`, `reset`, `checkout` trên live. Mọi thay đổi được thử trong Git worktree riêng, backup MySQL/source và vá chọn lọc, restart đúng một JVM game, xác nhận log, port và cột DB nếu có.

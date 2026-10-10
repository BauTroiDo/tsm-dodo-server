# TS Đa Vũ Trụ PC — Khôi phục trận gặp quái map Dị giới TS (49942)

**Ngày: 10/10/2026 (UTC+7). Trạng thái: đã triển khai live; còn cần người chơi xác nhận trận đánh qua client.**

## Nguồn xác minh

Người chơi báo `Dịch Chuyển → Giới hạn Khu luyện công → Dị giới TS (lòng vũ)` không có quái.

- File client PC `File.dat/Data-ios/IOS_LimitTimeDungeon_C.dat` và `AssetBundles/Data/IOS_LimitTimeDungeon_C.dat`: đúng một dòng dungeon index1, `sceneId=49942`, `missionId=13137`, `limitIndex=27`, `limitTime=120 phút`, `limitLevel=0`.
- Kiểm tra **trực tiếp Eve.emg của Windows server**, map49942 có `NPC count=0`, 1 Door, 2 MineData, 270 FightData và 6 NpcEventData. Điều này **không phải map spawn quái đứng tĩnh**; nó dùng **trận gặp quái ngẫu nhiên khi người chơi di chuyển**.
- `MineData[1]` có `sizeKind=1` (toàn bản đồ), `events=[4,2,3,6]`; `MineData[2]` có vùng 1 ô và không có event (không làm mất MineData toàn map ở vị trí khác).
- Đọc bằng `EveDataLoader` trong bài kiểm tra chẩn đoán thực tế: bốn events có tổng **60 điều kiện** cho nhiệm vụ 13137, `conditionClass=2`, `ops=5 (=)`, các bước chính xác **1..60**; toàn bộ kết quả là `resultType=3` (chiến đấu), tham chiếu FightData có quái thật. **Bước 0 không có sự kiện chiến đấu tương ứng**.
- Luồng vào map trước sửa: `LimitTimeDungeonHandler.enter` xác minh level/time nhưng **bỏ qua `LimitTimeDungeonDef.missionId`**, nên người chơi không có mission13137 sẽ vào map mà Mine encounter không tìm được trận. Bài chẩn đoán tạm `Map49942DiagnosticTest.kt` đã được **xóa khỏi live repository sau khi kiểm tra**, không để lại file nháp.

## Code đã sửa và push

**[PR #14](https://github.com/BauTroiDo/tsm-dodo-server/pull/14)** đã merge vào GitHub `main`, squash commit `f2001c4de3cb6b8c5b43e56848da18ad25647187`. CI run cuối `38006745367` kết thúc **SUCCESS**.

Chỉ triển khai 5 file:
1. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/QuestSystem.kt`: thêm `ensureMissionStarted` dưới khóa theo charId, chỉ bắt đầu step1 nếu chưa có; không đặt lại bước cũ 1..60.
2. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LimitTimeDungeonSystem.kt`: khởi tạo `missionId` từ dữ liệu gốc khi vào map và khi `onSceneReady` sau reconnect, gửi `S024-001` nếu mới tạo.
3. `app/src/main/kotlin/tw/tsm/app/handlers/LimitTimeDungeonHandler.kt`: xác minh điểm vào và mission đã lưu trước khi gửi thành công `S097-001`; nếu DB lỗi, không cho dịch chuyển vô ích.
4. `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: bắt buộc DI `QuestSystem` vào `LimitTimeDungeonSystem`.
5. `tests/src/test/kotlin/tw/tsm/tests/LimitTraining49942EncounterRegressionTest.kt`: 5 hồi quy nguồn dữ liệu thật, packet S024, không reset tiến độ, lỗi DB và mission 13137.

Không sửa `Eve.emg`/dữ liệu quái, không tạo NPC giả, không reset quest của nhân vật.

## Triển khai Windows server

- Host `192.168.1.69`, source `C:\Server-Game\tsm_dodo`.
- 4 file gốc trên server được đối chiếu Git blob SHA với GitHub `main` trước PR14, đều khớp. File test mới chưa tồn tại. Tải và xác minh SHA của 5 file từ GitHub PR14 trước khi chép chọn lọc qua SSH, đọc lại để xác nhận byte.
- Sao lưu 4 file gốc và manifest tại `C:\Server-Game\tsm_dodo\backups\LimitTraining49942_before_PR14_20261010`. Log kiểm thử `full_test.log`.
- Không dùng `git pull`, reset, checkout làm mất các sửa đổi chưa commit của quest, pet, UI, Tinh Linh và Tiệm.
- `.\gradlew.bat :tests:test --offline --console=plain` tại server Windows: **BUILD SUCCESSFUL in 1m58s**. Kết quả XML: **840 tests, 0 failures, 0 errors, 5 skipped, 157 suites**.
- Restart bằng Windows Scheduled Task `TSM_DODO_ONESHOT`: /End, xác minh port không listen, /Run. Log lúc **07:01:53.240 ngày 10/10/2026**: `NettyGameServer - Máy chủ khởi động: 0.0.0.0:6613`; `Main - Máy chủ đã sẵn sàng, đang chờ kết nối...`. PID lúc xác minh **10252**.

## Test gameplay còn cần

1. Thoát/đăng nhập lại client PC `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING`, mở `Dịch Chuyển → Giới hạn Khu luyện công`, vào map duy nhất 49942.
2. **Không tìm quái đứng sẵn**. Đi bộ qua nhiều ô trong map để kích hoạt MineData toàn map, tối thiểu khoảng **200px quãng di chuyển** (10 ô 20px); hệ thống Mine hiện dùng pacing 200px và cooldown 27 giây sau trận. Tránh đứng yên hoặc dùng chuyển map tức thì thay vì walk.
3. Nếu không gặp trận, xem `logs/runtime-production.log`: có `Initialized DVT training encounter stage: ... mission=13137 step=1`, sau đó `Mine kích hoạt: map=49942` / `Mine encounter: map=49942`. Nếu có step đã lưu >60, hiện hệ thống **không tự reset về1** để bảo vệ tiến độ; cần chẩn đoán riêng.
4. Kiểm thử độ khó/quái và tiến trình nhiệm vụ, phần thưởng, timer 120 phút; chưa thể coi E2E hoàn tất nếu người chơi chưa đi vào map thực.
5. Không tự ý cấp tiền/vật phẩm, không đổi mission step của người chơi đã có, không giảm thời gian luyện công; khi cần sửa mã thì tiếp tục commit/push GitHub và sao lưu server.

**Giới hạn xác thực**: Đã xác minh dữ liệu Eve, mã khởi tạo nhiệm vụ, CI, 840 unit/integration tests và startup cổng6613. **Chưa quan sát trực tiếp trận đánh phát sinh từ client**.

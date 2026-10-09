# TS Đa Vũ Trụ PC: triển khai sửa vé Tinh Linh ItemUse_239 lên server — 10/10/2026

## Trạng thái đã xác minh

**Đã triển khai lên server Windows** thay đổi từ [PR #11](https://github.com/BauTroiDo/tsm-dodo-server/pull/11), squash commit `bfcc7dbb0c6b7e6571f062f549ff4fb91c02494a`. PR #11 có GitHub Actions run `37997462931` **SUCCESS** trước khi triển khai.

- Máy chủ: `192.168.1.69`, thư mục `C:\Server-Game\tsm_dodo`, game `6613`.
- Truy cập qua SSH trực tiếp cấu hình đã được chủ server ủy quyền; alias OpenSSH `tsm-prod` có thể gặp lỗi 255 tùy máy vì credentials.
- Đã đọc GitHub connector theo **đúng merge SHA PR #11**, lưu 7 file vào staging trên máy Windows `C:\Users\QT\AppData\Local\Temp\tsm_elf_token_pr11_20261010`; **Git blob SHA của cả 7 file đều khớp 100%**. Không dựa vào tên file hay bản draft.
- So sánh **năm** file đã có trên server với SHA trước PR #11; hai file mới không tồn tại; nếu có chênh lệch sẽ dừng chứ không ghi đè. Đã sao lưu nguyên vẹn năm file cũ + manifest trước khi chuyển cả 7 file; sau mỗi thao tác ghi đọc lại nội dung để đối chiếu SHA.
- Backup trên server: `C:\Server-Game\tsm_dodo\backups\ElfToken239_before_PR11_20261010`. Có `manifest.json` và `full_test.log`.
- **Không git reset/pull/checkout toàn bộ repo**; các sửa đổi local khác về quest/pet/shop được giữ nguyên.

## File đã chuyển và xác minh

1. `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
2. `app/src/main/kotlin/tw/tsm/app/handlers/ElfHandler.kt`
3. `app/src/main/kotlin/tw/tsm/app/handlers/ElfTokenUnlockService.kt` (mới)
4. `app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt`
5. `domain/src/main/kotlin/tw/tsm/domain/constants/EItemUseKind.kt`
6. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt`
7. `tests/src/test/kotlin/tw/tsm/tests/ElfTokenUnlockRegressionTest.kt` (mới)

## Kiểm thử và restart

- Tại Windows server: `.\gradlew.bat :tests:test --offline --console=plain` → **GRADLE_EXIT=0, BUILD SUCCESSFUL in 1m 43s**.
- Đọc `tests/build/test-results/test/TEST-*.xml`: **833 tests, 0 failures, 0 errors, 5 skipped, 156 suites**.
- `ElfTokenUnlockRegressionTest.xml`: **6 tests, 0 failures**.
- `schtasks.exe /End /TN TSM_DODO_ONESHOT` kết thúc tiến trình game cũ; kiểm tra cổng `6613` không còn listen.
- `schtasks.exe /Run /TN TSM_DODO_ONESHOT`, task báo Running; log `logs/runtime-production.log` xác nhận `NettyGameServer - Máy chủ khởi động: 0.0.0.0:6613` vào **05:23:52.813 ngày 10/10/2026 (UTC+7)**; PID mới **1348** (ở thời điểm kiểm tra). Không thấy ERROR/Exception mới trong đoạn log startup đã kiểm tra.
- **Chưa kiểm chứng thực tế dùng vé 319xx với nhân vật test trong client**. Đây là giới hạn xác thực, không được tuyên bố đã test giao dịch trực tiếp.

## Logic đúng của DVT PC

- Các túi `34613`, `34743`, `34695`, `34428` quay thưởng theo `IOS_BlissBag_C.dat`, tỷ lệ trúng vé Tinh Linh của từng túi thông thường là **200/10000 = 2%**. Mở túi chỉ nhận ngẫu nhiên vật phẩm: không phải mua 1 túi là chắc chắn nhận Tinh Linh.
- Log nhân vật `charId=31` ngày 10/10/2026: `34613 x1 → 46580 x1`; `34743 x3 → 46580 x1, 46497 x2`. Những phần thưởng này không phải vé Tinh Linh; **không có bằng chứng người chơi đã bị mất Tinh Linh ở hai lượt mở này**.
- Khi nhận được vé Tinh Linh có specialAbility `239`, ví dụ `31912` = Bạch Ngọc (`elfNo=12`), dùng **một vé** sẽ lưu `BitFlag=6092` vào database, trừ đúng một vé, gửi client cập nhật cờ và S107-001, S107-006 để giao diện `UIElf` nhận.
- Vé 31901..31924 tương ứng 24 Tinh Linh trong file Elf_C. Các vé `31925..31928` không có Elf trong catalog hiện tại nên bản vá **giữ nguyên, không tiêu**. Nếu đã sở hữu, vé trùng cũng không tự tiêu. Khi DB/bag lỗi, có rollback/bảo toàn.
- Không cấp miễn phí Tinh Linh hay tiền cho nhân vật live; không thay RNG cơ bản.

## Việc làm tiếp

1. Người chơi thoát vào game lại qua client `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING` (để làm mới các cờ và dữ liệu).
2. Trên **nhân vật thử nghiệm** có sẵn vé `31912` hợp lệ, dùng đúng một lần: kiểm tra túi giảm 1, flag6092 lưu, client nhận cập nhật, vào UI Tinh Linh thấy Bạch Ngọc; relog vẫn còn.
3. Thử vé trùng, vé 31925, túi đầy, và hành vi của client ở các màn khác. Nếu có lỗi, tra `logs/runtime-production.log` quanh thông báo `Elf ticket redeemed` / `Elf ownership DB persistence failed` / `Elf token consume failed`.
4. Nếu người chơi chỉ mở túi 346xx mà không trúng 319xx, **không** chẩn đoán là lỗi hiển thị Tinh Linh nữa, vì đó là loot chance 2%.
5. Bảo mật: file log startup hiện có thể xuất lộ biến môi trường chứa mật khẩu DB. Không gửi nguyên log, cần lọc hoặc ẩn giá trị bí mật khi chia sẻ ra ngoài; cân nhắc bỏ in `JAVA_TOOL_OPTIONS` nhạy cảm và đổi thông tin đăng nhập DB nếu đã bị lộ.

Tài liệu trước: [hướng dẫn lỗi và bản sửa PR11](pc-dvt-elf-token-239-handoff-20261010.md), [triển khai Tiệm](pc-gs206-shop-quota-live-deploy-20261010.md), [backend Elf 107](pc-dvt-elf-107-deploy-20261010.md).

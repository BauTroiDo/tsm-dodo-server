# TS Đa Vũ Trụ PC — Khôi phục mua túi Tinh Linh trong Tiệm (10/10/2026)

## Trạng thái triển khai

- **Đã sửa và merge GitHub**: PR [#8](https://github.com/BauTroiDo/tsm-dodo-server/pull/8), squash commit `4b6b7f93405c1e38035f48b24b98eaabbb087078` trong `main`.
- **Đã cập nhật chọn lọc server Windows qua SSH**: `C:\Server-Game\tsm_dodo`, máy `192.168.1.69`.
- **Đã khởi động lại production** bằng `schtasks /End /TN TSM_DODO_ONESHOT` rồi `schtasks /Run /TN TSM_DODO_ONESHOT`.
- Server mới **PID 10700**, lắng nghe `0.0.0.0:6613`. Log `logs\runtime-production.log` lúc **04:12:39.742, 10/10/2026 UTC+7** xác nhận server đã sẵn sàng; dữ liệu PC được nạp, không thấy lỗi khởi động trong những dòng đã kiểm tra.
- **CHƯA xác nhận mua túi thành công trong giao diện game sau restart**. Người chơi cần tự thử và gửi phản hồi/log để chốt lỗi.

## Nguyên nhân đã đối chiếu với log server

User gửi ảnh Tiệm ở danh mục Tinh Linh. Khi mua túi giá 19, client hiển thị chung `Tiền bạc không đủ` dù người chơi có tiền.

Log thực tế từ `MallHandler` trước bản sửa:
`Từ chối Mall C66-1 ngoài scope Doro: store=1 item=34695 count=1 free=0`,
tương tự `item=34428`. Không phải lỗi thiếu tiền; backend cũ chỉ cho phép `storeKind=5` (Doro), trong khi Tiệm thường là `storeKind=1`.

Bảng PC `IOS_GS_C.dat`: các túi Tinh Linh `itemId=34695`, `34428` có giá `saleCredit=19`, `currencyKind=1` (Point/Kim Nguyên Bảo); `mainKind=1`, `subKind=3`.

## Thay đổi kỹ thuật

- `app/src/main/kotlin/tw/tsm/app/handlers/MallHandler.kt`: thêm xử lý C66-1 `storeKind=1` mua đồ thường, kiểm tra đúng dòng bảng GS, index trang, `itemId`, giá, cờ bán và giới hạn. Kiểm tra tiền và túi trước khi giao, rollback thanh toán khi thất bại. Đồ `saleKind != 0` và đồ có quota/day/event chưa có cơ chế riêng thì bị từ chối, không cho vượt quota.
- `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: tiêm `GameDataManager.goodsSaleDefs` vào MallHandler. Không đổi nhánh Doro.
- `tests/src/test/kotlin/tw/tsm/tests/MallStandardShopPurchaseRegressionTest.kt`: test túi giá 19, giá giả, chỉ số hàng giả, không đủ điểm, hàng quota chưa hỗ trợ. Đã sửa mock typed `PointSystem.getPoints` sau khi CI lần 1 bắt lỗi trong unit test.

## Kiểm thử

- CI GitHub cuối trên commit `81320ea02cc2fa14822a5d9230d0d344b17b2cad`: workflow run `37991516394`, **SUCCESS**. Bản source merge PR #8 chính là source được CI xác nhận.
- Đã thực hiện test trên Windows server trước restart:
  - Test chuyên biệt `MallStandardShopPurchaseRegressionTest`: **BUILD SUCCESSFUL** (38s).
  - Toàn bộ `gradlew.bat :tests:test --offline --console=plain`: **BUILD SUCCESSFUL** (56s).
  - XML: **821 tests, 0 failures, 0 errors, 5 skipped**.

## Sao lưu và bảo toàn dữ liệu

- `C:\Server-Game\tsm_dodo\backups\Mall66_before_PR8_localdiag_20261010`: sao lưu nguyên bản `MallHandler.kt`, `ServerModule.kt`, manifest; có log test `target_test.log`, `target_test_after_mock.log`, `full_test_after_mock.log`.
- Chỉ triển khai 2 Kotlin production files và 1 Kotlin test mới, không chạy `git reset` hoặc `git pull` đè working tree. Server có nhiều thay đổi quest, pet 019-008, Elf 107 chưa commit local cần giữ nguyên.
- **KHÔNG** thay số dư, cấp vật phẩm, hoặc dùng dữ liệu nhân vật để test tự động.

## Việc cần làm tiếp trong phiên chat sau

1. Người chơi mở client PC `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING`, đăng nhập Tiệm → Tinh Linh, thử mua **một túi mã 34695 hoặc 34428** giá 19 Kim Nguyên Bảo.
2. Xác minh trừ **đúng 19 Point**, nhận **đúng 1 túi**, mở túi và thử chức năng Tinh Linh (opcode 107) đã triển khai trước đó.
3. Nếu thất bại, đọc `Player.log` (client) và `logs\runtime-production.log` (server), đối chiếu S/C66-1 và lý do server; không mặc định là thiếu bạc.
4. Nếu mua thành công nhưng mở túi không ra Tinh Linh, điều tra xử lý item box, phát thưởng và cờ sở hữu Elf — vấn đề khác với Mall purchase.
5. Mọi sửa mã nguồn về sau phải **commit/push lên GitHub** `BauTroiDo/tsm-dodo-server` sau khi kiểm thử, sao lưu trước khi thay server. Tài liệu bàn giao Elf 107: [pc-dvt-elf-107-deploy-20261010.md](pc-dvt-elf-107-deploy-20261010.md).

Lưu ý: đã xác minh hoạt động backend startup, CI và unit tests, **chưa chứng thực giao dịch thật trong client**.

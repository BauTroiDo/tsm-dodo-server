# TS Đa Vũ Trụ PC — Túi Tinh Linh / vé mở khóa, 10/10/2026

> **CẬP NHẬT TRIỂN KHAI 10/10/2026:** PR #11 đã được triển khai lên Windows server lúc 05:23 (UTC+7), cổng 6613 hoạt động (PID 1348 tại thời điểm kiểm tra). Kiểm thử trên server: **833 tests, 0 failures, 0 errors, 5 skipped**, gồm 6 test ElfTokenUnlockRegressionTest. Bản sao lưu: `C:\\Server-Game\\tsm_dodo\\backups\\ElfToken239_before_PR11_20261010`. Chi tiết: [báo cáo triển khai live PR #11](pc-dvt-elf-token-239-live-deploy-20261010.md). Các ghi chú "chưa deploy" bên dưới chỉ phản ánh trạng thái ban đầu trước đợt triển khai này. **Chưa kiểm chứng thao tác dùng vé trong client với nhân vật test.**


## Kết quả thực tế và nguồn gốc vấn đề

Người chơi báo mua túi ở mục Tinh Linh, mở ra nhưng không thấy Tinh Linh. Đối chiếu `logs/runtime-production.log` của Windows server `192.168.1.69`, nhân vật `charId=31`:

- **04:56:15** mua `item=34613 x1` hết `19 Point` (Kim Nguyên Bảo), **04:56:28** mở và nhận `46580 x1`.
- **04:57:55** mua `item=34743 x3` hết `57 Point`, **04:58:02** mở và nhận `46580 x1` và `46497 x2`.
- `46580` là **Túi văn chương ĐPT** và `46497` là **Tùy Thân Tạp Hóa**, không phải token Tinh Linh. Hai lần quay kể trên thực sự **không trúng Tinh Linh**, không có bằng chứng token 319xx bị mất trong hai giao dịch này.
- Dữ liệu PC chính xác `AssetBundles/Data/IOS_BlissBag_C.dat`: mỗi túi `34613` (Bạch Ngọc), `34743` (Phúc Đức Chánh Thần), `34695` (Mi Lộc), `34428` (Thủy Vân Huyễn Cơ) có 9 phần thưởng thuộc nhóm kind1 tổng xác suất trọng số **10000**. Mỗi phần thưởng Tinh Linh tương ứng `31912`, `31923`, `31921`, `31902` có trọng số **200/10000=2%**. Tỷ lệ còn lại nhận vật phẩm thường. **Không thay xác suất gốc**, không cấp Tinh Linh miễn phí cho nhân vật thật.
- PC `AssetBundles/Data/IOS_Item_C.dat` có **28 token ItemUse_239**: `31901..31928`; PC `IOS_Elf_C.dat` có đúng **24 Tinh Linh thực** (`no=1..24`) + default `no=999`. Vé `31925..31928` không có định nghĩa Elf trong catalog hiện tại nên phải từ chối sử dụng mà **không tiêu vật phẩm**.
- Quy tắc ánh xạ `item=31900+elfNo`. Ví dụ vé `31912` Bạch Ngọc → `elfNo=12` → quyền sở hữu `BitFlag=6092` theo `IOS_Elf_C.dat`. Client `UIElf.GetTotalElfCount` chỉ hiện sở hữu nếu `BitFlag.Get(elfData.flag)` là true, và mỗi lần mở giao diện gửi `C107-005`.
- Backend `ItemUseProcessor` trước đây **không có nhánh ItemUse_239**, nên nếu người chơi từng dùng vé 319xx thì nhánh dùng item generic có thể tiêu vé mà không ghi BitFlag. Hai giao dịch ghi log phía trên **chưa hề trúng vé**, nên không được quy rằng đã mất Tinh Linh.

## Bản sửa mã nguồn đã push, nhưng chưa triển khai live

**GitHub [PR #11](https://github.com/BauTroiDo/tsm-dodo-server/pull/11) đã merge vào main**, squash commit `bfcc7dbb0c6b7e6571f062f549ff4fb91c02494a`; GitHub Actions `37997462931` **SUCCESS**.

7 file sửa:
- `app/src/main/kotlin/tw/tsm/app/handlers/ElfTokenUnlockService.kt` mới: khóa theo charId, kiểm tra token 31901..31924 và `specialAbility=239`, xác minh cờ sở hữu, persist BitFlag, trừ đúng 1 vé, nếu lỗi trừ thì rollback cờ; không tiêu vé duplicate hay vé 31925..31928.
- `app/src/main/kotlin/tw/tsm/app/handlers/ItemHandler.kt`: chuyển ItemUse_239 vào ElfTokenUnlockService trước generic handling.
- `app/src/main/kotlin/tw/tsm/app/handlers/ElfHandler.kt`: bổ sung `ElfPackets.acquired` (S107-001 no UInt16).
- `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: DI đăng ký service trong ItemHandler.
- `domain/src/main/kotlin/tw/tsm/domain/constants/EItemUseKind.kt`: `ELF_TOKEN=239`.
- `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/ItemUseProcessor.kt`: từ chối ItemUse_239 qua generic path (không tiêu vé khi chưa mở khóa).
- `tests/src/test/kotlin/tw/tsm/tests/ElfTokenUnlockRegressionTest.kt`: 6 regression tests về mở khóa, báo UI, duplicate, no25, DB fail, item rollback và quantity.

**QUAN TRỌNG: CHƯA CÀI VÀ CHƯA RESTART server Windows cho PR #11.** Quá trình chuẩn bị file staging tại client PC `C:\Users\QT\AppData\Local\Temp\tsm_elf_token_pr11_20261010` được 5 file đầu tiên, nhưng lần ghi `ItemUseProcessor.kt` bị cơ chế an toàn công cụ từ chối. Không cố gắng lách chặn, không triển khai bản vá thiếu file. Máy chủ vẫn dùng code từ PR #9, chạy trên port 6613 theo kiểm tra trước đó. Không có sửa đổi live từ PR #11.

## Việc còn lại

1. Qua cách làm được cho phép, lấy đúng bảy file từ commit `bfcc7db...`, kiểm tra checksum file GitHub đối chiếu working tree server, sao lưu trước (server có >170 thay đổi chưa commit về quest, pet, UI, shop). **Không reset hoặc pull đè server**.
2. Chạy `.\gradlew.bat :tests:test --offline --console=plain` và bộ test ElfTokenUnlockRegressionTest tại `C:\Server-Game\tsm_dodo`.
3. Nếu đạt, restart bằng `schtasks /End /TN TSM_DODO_ONESHOT` rồi `/Run`, kiểm tra `logs\runtime-production.log` và cổng `6613`.
4. Thử **trên nhân vật test** có vé `31912` hợp lệ, dùng một vé và quan sát S081 BitFlag `6092`, S107-001/006, túi mất đúng một vé, relog rồi kiểm tra Bạch Ngọc hiện trong mục Tinh Linh. Kiểm tra cả duplicate và token 31925 không bị trừ.
5. Với nhân vật thật `charId=31`, **không bồi hoàn hay cấp Elf nếu không trúng**. Nếu có chứng cứ một token 319xx đã bị tiêu trong một thời điểm khác, cần log/giao dịch để đối soát độc lập trước khi bồi hoàn.
6. Mọi sửa code mới phải push lên repo `BauTroiDo/tsm-dodo-server`, cập nhật báo cáo tại đây.

Các báo cáo khác: [triển khai Mall 206](pc-gs206-shop-quota-live-deploy-20261010.md), [backend Elf 107](pc-dvt-elf-107-deploy-20261010.md).

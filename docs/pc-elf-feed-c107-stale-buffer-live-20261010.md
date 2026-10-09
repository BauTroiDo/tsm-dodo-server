# TS Đa Vũ Trụ PC — Fix UI Tinh Linh báo "Chưa nhận tinh linh này" khi Cho ăn

Ngày triển khai: **10/10/2026 (UTC+7)**. **ĐÃ TRIỂN KHAI VÀ RESTART LIVE**.

## Triệu chứng và truy nguyên

Người chơi đã mở khóa 2 Tinh Linh qua vé `31911` (Elf no11 flag6089) và `31923` (Elf no23 flag6118); server log xác nhận `Elf ticket redeemed`. Client `UIElf` đếm **2 Tinh Linh đã sở hữu** qua `BitFlag.Get(elfData.flag)`, nhưng khi chọn Mặc Mặc rồi bấm **Cho ăn** lại hiển thị `string.Get(23759)` — "Chưa nhận tinh linh này".

Đã đối chiếu Lua gốc `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115\_lua_client_decrypted\UI\UIElf.lua`:
- `UIElf.OnOpen()` đặt `elfSave={}`, gửi `Network.Send(107,5,sendBuffer)`, **không gọi `sendBuffer:Clear()`**.
- `UIElf.OnClick_Feed()` chỉ vào nhánh báo chưa nhận nếu `elfSave[currentElfNo]==nil`, tức là **thiếu snapshot cấp/EXP từ S107-005** chứ không phải chưa sở hữu cờ BitFlag.
- Server `ElfHandler.handle()` trước sửa chỉ chạy `query(session,cid)` trong nhánh `5 -> if (data.isEmpty())...`. Nếu Lua gửi kèm byte dư từ shared sendBuffer, server từ chối trả snapshot; client đã xóa elfSave sẽ không có dữ liệu.
- Đây là cơ chế đủ để giải thích triệu chứng đã thấy; chưa có packet capture trực tiếp từ client xác nhận kích thước chính xác payload dư.

## Bản sửa

[PR #13](https://github.com/BauTroiDo/tsm-dodo-server/pull/13) đã merge vào GitHub `main`, squash commit `31fb309bce88f2923a4c63ca6831d757d46680d5`. GitHub Actions run `38003314480` kết thúc `SUCCESS`.

Chỉ thay hai file:

1. `app/src/main/kotlin/tw/tsm/app/handlers/ElfHandler.kt`:
   - C107-005 là truy vấn **read-only, không có đối số**, luôn trả S107-005 snapshot bất kể payload client kèm byte dư.
   - Vẫn bắt buộc session `IN_GAME`, `roleId>0`, `battleId=0`. Các lệnh C107-002/003/004 giữ nguyên kiểm tra chặt.
   - Chỉ debug log kích thước payload dư, không log byte nhạy cảm.
2. `tests/src/test/kotlin/tw/tsm/tests/ElfProtocolDvtRegressionTest.kt`:
   - Kiểm thử query rỗng và query chứa byte dư cùng trả snapshot của Elf 11 và 23 (Lv1 mặc định), độc lập với quyền sở hữu trong BitFlag.
   - Thêm kiểm thử không phản hồi query nếu chưa vào `IN_GAME`.

**Chưa thay client Unity/AssetBundles**, vì backend đã được bổ sung khả năng tương thích với Lua DVT hiện tại. Nếu vẫn lỗi sau triển khai, cần packet trace C107-005 + kiểm tra quy trình tải `elfSave` và xem có cần sửa trực tiếp UI Lua phía client.

## Triển khai Windows live

- Host: `192.168.1.69`, thư mục `C:\Server-Game\tsm_dodo`, scheduled task `TSM_DODO_ONESHOT`.
- Hai source file lấy từ GitHub branch PR #13 rồi xác nhận Git blob SHA:
  - `ElfHandler.kt`: trước `a0f88f1979a0e3daa0e0ac37e3b03c503c9909f2`, sau `c7648cd6edc07f9a12b3d736975974eb083e488e`.
  - `ElfProtocolDvtRegressionTest.kt`: trước `23ebfe522d4587fa3eb1bc47230e65a13e03b934`, sau `8f3bfa9e4efb28919a46da202256276c089e3e45`.
- Đối chiếu SHA của source server trước khi ghi, sao lưu nguyên file và manifest tại:
  `C:\Server-Game\tsm_dodo\backups\ElfC107_Snapshot_before_PR13_20261010`.
  Log Gradle: `full_test.log`.
- Chép **đúng hai file**, đọc lại so checksum; **không `git pull`, reset, checkout** hoặc ghi đè các thay đổi chưa commit về quest/pet/shop/Elf.
- Trên chính Windows server: `.\gradlew.bat :tests:test --offline --console=plain` → **BUILD SUCCESSFUL in 1m22s**. JUnit XML: **835 tests, 0 failures, 0 errors, 5 skipped, 156 suites**.
- GitHub CI PR #13 cũng **SUCCESS** trước khi merge.
- Đã `schtasks /End /TN TSM_DODO_ONESHOT`, xác nhận port6613 ngừng listen (việc dừng mất khoảng 1 phút), sau đó `schtasks /Run /TN TSM_DODO_ONESHOT`.
- Startup mới: **06:20:53.207 ngày 10/10/2026**, `tw.tsm.network.NettyGameServer - Máy chủ khởi động: 0.0.0.0:6613`, `tw.tsm.app.Main - Máy chủ đã sẵn sàng, đang chờ kết nối...`; PID tại lần kiểm tra: **7016**, cổng 6613 LISTEN. Không có dòng ERROR/Exception trong phần startup đã lọc.

## Cần người chơi xác nhận

1. Thoát game / đăng nhập lại client `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING` để yêu cầu snapshot mới.
2. Mở mục **Tinh Linh → Mặc Mặc** (Elf 11) rồi kiểm tra có hiển thị `Lv: 1` và EXP `0/...` hay không; chọn **Phúc Đức Chánh Thần** (Elf23) và kiểm tra tương tự.
3. Chỉ bấm **Cho ăn** khi có vật phẩm nuôi EXP tương ứng `46037`, `46038`, `46234`; xác nhận lựa chọn số lượng, trừ đúng số item, tăng EXP, relog còn progress.
4. Nếu vẫn hiện thông báo chưa nhận, cần đọc lại log server/packet response S107-005 và làm rõ phần client `UIElf.RecElfSave` có nhận được snapshot hay không. Tránh tự cấp vật phẩm, đổi số dư nhân vật sống hoặc thay tỷ lệ quay thưởng.
5. Khi cần sửa mã mới, tạo PR/CI, triển khai chọn lọc có backup và cập nhật báo cáo GitHub.

**Giới hạn xác minh:** Đã kiểm chứng build, tests và server runtime; **chưa xác nhận thao tác Cho ăn thực tế trong game client**.

Các tài liệu nền: [mở khóa vé Tinh Linh PR11](pc-dvt-elf-token-239-live-deploy-20261010.md), [Tiệm GS206](pc-gs206-shop-quota-live-deploy-20261010.md).

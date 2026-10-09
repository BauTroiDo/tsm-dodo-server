# TS Đa Vũ Trụ PC — bàn giao triển khai Tinh Linh (107), 10/10/2026

## Trạng thái chính xác

- **Đã đưa lên GitHub**: repo `BauTroiDo/tsm-dodo-server`, PR [#6](https://github.com/BauTroiDo/tsm-dodo-server/pull/6), squash-merge commit `6583a30c9475fb5dcf242881657c01620c793f41` trên `main`. GitHub Actions CI (test + build) **SUCCESS**.
- **Đã triển khai trên máy chủ game**: dùng kết nối SSH Paramiko với known_hosts nghiêm ngặt tới `192.168.1.69`, user Administrator. Repo local server: `C:\Server-Game\tsm_dodo`.
- **Đã sao lưu trước khi thay** tại `C:\Server-Game\tsm_dodo\backups\Elf107_before_6583a30_20261010`, trong đó có hai file Kotlin gốc, `deploy_manifest.json`, log test.
- **Đã triển khai chọn lọc đúng 7 file từ GitHub**; không reset/pull toàn bộ repository. Danh sách:
  - `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`
  - `persistence/src/main/kotlin/tw/tsm/persistence/DatabaseFactory.kt`
  - `app/src/main/kotlin/tw/tsm/app/handlers/ElfHandler.kt`
  - `domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/ElfRepository.kt`
  - `persistence/src/main/kotlin/tw/tsm/persistence/repositories/ElfRepositoryImpl.kt`
  - `persistence/src/main/kotlin/tw/tsm/persistence/tables/CharacterElves.kt`
  - `tests/src/test/kotlin/tw/tsm/tests/ElfProtocolDvtRegressionTest.kt`
- `ServerModule.kt` và `DatabaseFactory.kt` trước khi áp dụng trùng chính xác nội dung GitHub trước PR #6 (Git blob `8436373060a32264f01992674debc5dee7b6ba23`, `33b81b150f27022e3346db972fe08778a1f2ad01`). Các file mới đều chưa tồn tại trước khi triển khai. Nội dung 7 file được kiểm chứng Git blob và đọc lại sau khi chép qua SSH.
- **Tất cả kiểm thử local server đạt**: `gradlew.bat :tests:test --offline --console=plain` hoàn thành `BUILD SUCCESSFUL`. Phân tích XML: **815 tests, 0 failures, 0 errors, 5 skipped, 153 suites**. Bài `ElfProtocolDvtRegressionTest` chạy thành công. Log: `backups\Elf107_before_6583a30_20261010\elf_targeted_test.log` và `elf_full_test.log`.
- **Đã restart production bằng scheduled task** `TSM_DODO_ONESHOT`: `schtasks.exe /End /TN TSM_DODO_ONESHOT`, sau đó `schtasks.exe /Run /TN TSM_DODO_ONESHOT`. Server mới khởi động lúc **03:00:40 (UTC+7, 10/10/2026)**, Java **PID 5456**, lắng nghe `0.0.0.0:6613`. Log `logs\runtime-production.log` có `Máy chủ đã sẵn sàng`, không có lỗi startup mới trong phần đã kiểm tra; Eve nạp đúng PC `gamedata\Eve.emg`. Không đảm bảo PID không đổi ở các thời điểm sau.
- **Chưa có xác nhận thao tác trực tiếp trong client**. Chưa đo khả năng lưu bảng MySQL bằng SQL trực tiếp: thao tác kiểm tra DB credential bị công cụ an toàn chặn; KHÔNG tự lách chặn. Khởi tạo `DatabaseFactory` thành công trong startup log, nhưng cần kiểm tra trong game để xác nhận dữ liệu thực tế.

## Đối chiếu client PC DVT

- Client PC đang dùng `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING`; Lua tham chiếu `D:\Games\Ts-Offline\TSMobileOnLine\_native_dump_dvt115\_lua_client_decrypted\UI\UIElf.lua`.
- **Tinh Linh = `UIElf`, opcode 107**, KHÔNG phải Hồn (`UISoul`, opcode 120).
- C107-005 truy vấn, C107-002 xuất chiến, C107-003 thu hồi, C107-004 cho ăn. Server trả S107-002/003/004/005/006 theo bộ giải mã client.
- Dữ liệu PC `File.dat\Data\elf_C.dat`: 25 dòng, 24 Tinh Linh + mục trống/mặc định số 999. Mở khóa qua cờ sở hữu riêng, không cấp Tinh Linh miễn phí. Bảng EXP `File.dat\Data-ios\IOS_ElfExp_C.dat` (20 mốc) đã tồn tại trên server và trùng checksum client PC `AssetBundles\Data\IOS_ElfExp_C.dat`, không cần thay.
- Backend thêm hai bảng `character_elf_profiles` và `character_elf_progress`; `DatabaseFactory` gọi `SchemaUtils.createMissingTablesAndColumns` lúc khởi động. Vẫn cần thử tạo/lưu một bản ghi thật trong game.
- Cho ăn: cần có vật phẩm đúng loại trong túi, kiểm tra số lượng trước khi trừ và trả trạng thái. Chỉ kiểm thử bằng vật phẩm thử và tài khoản thử; không cấp tài nguyên hoặc sửa nhân vật đang chơi khi chưa xác minh.

## Cần tiếp tục phiên chat sau

1. Mở **Tinh Linh** trong client PC; xem danh sách theo cờ sở hữu.
2. Với một nhân vật **đã mở khóa Tinh Linh hợp lệ**, thử `Xuất chiến`, `Thu hồi`, đăng xuất/đăng nhập lại và xác nhận trạng thái lưu được.
3. Với đồ ăn Tinh Linh hợp lệ, sao lưu dữ liệu test char rồi cho ăn đúng **1 đơn vị**, kiểm tra túi đồ, EXP/cấp và độ bền qua relog; kiểm tra cả trường hợp vật phẩm thiếu hoặc không sở hữu Tinh Linh.
4. Nếu UI không thay đổi, thu thập `Player.log` client và `logs\runtime-production.log` server đối chiếu S/C107. **Đừng nhầm sang pet 019-008** đã sửa trước đó, người dùng đã xác nhận hoạt động.
5. Bảo toàn tất cả bản sửa PC quest/DVT, Eve, pet, UI đã có. Tại thời điểm kiểm tra trước triển khai server có **162 thay đổi Git chưa commit**. Trên server vẫn giữ base Git local `f6c228b40b2d84d7617a6dd06e1ee020e9573542`, các file Tinh Linh thuộc working tree; **tuyệt đối không git reset, pull --rebase hay checkout đè**. GitHub main đã chứa PR #6.
6. Người dùng yêu cầu **mọi sửa đổi mã nguồn tiếp theo phải được commit/push lên GitHub** `BauTroiDo/tsm-dodo-server` và cập nhật tài liệu bàn giao cho chat sau, không chỉ sửa live. Không push secret, credential, backup DB hay các file nhạy cảm.
7. SSH alias `tsm-prod` qua OpenSSH từ PC trước đó trả code 255, nhưng SSH với host/username/key tương đương bằng Paramiko trên PC hoạt động. Helper có sẵn: `C:\Users\QT\AppData\Local\Temp\tsm_quest_remote.py` hỗ trợ dạng `PS:<powershell command>`; xác minh host key bằng `C:\Users\QT\.ssh\known_hosts`.

## Lưu ý phương án rollback

Các file gốc cần hoàn tác (hai file sửa hiện hữu) ở backup nêu trên; 5 file Kotlin thêm mới có thể tạm đưa ra khỏi source tree. Sau đó **kiểm thử và restart theo scheduler**. Đừng tự rollback nếu server hiện đã có thay đổi kế tiếp cần giữ.

Tài liệu này được lưu trên GitHub để phiên chat tiếp theo có thể truy cập và tiếp tục từ trạng thái thật, thay cho lời hứa rằng bộ nhớ hội thoại đã tự cập nhật.

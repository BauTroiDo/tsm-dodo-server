# TS Đa Vũ Trụ PC — Triển khai quota cửa hàng GS_C (10/10/2026)

## Trạng thái xác minh

**Đã triển khai thành công trên server Windows** mã nguồn của [PR #9](https://github.com/BauTroiDo/tsm-dodo-server/pull/9), merge commit `0ae55348d4179f20ec2fbeaf38d55117a06af9e7`. Trước đó PR #9 đã hoàn tất GitHub Actions CI (`BUILD SUCCESSFUL`).

- Host: `192.168.1.69`; server code: `C:\Server-Game\tsm_dodo`.
- Đã kiểm tra đủ **9 file Kotlin** của PR #9 so với Git blob SHA; so sánh 4 file gốc trên server khớp bản GitHub trước khi áp dụng. Chép chọn lọc 9 file qua kết nối SSH đã cấp quyền, đọc lại file và đối chiếu nội dung.
- Sao lưu các file gốc và manifest tại `C:\Server-Game\tsm_dodo\backups\Mall206_before_PR9_20261010`. Log chạy Gradle: `test_after_stage.log`.
- **Không `git pull`, `git checkout` hay `git reset` ghi đè server.** Working tree có 171 thay đổi chưa commit trước triển khai, chủ yếu là những sửa đổi quest/pet/UI đã được người dùng kiểm tra.
- Chạy `.\gradlew.bat :tests:test --offline --console=plain` trên chính server: **BUILD SUCCESSFUL (1m40s)**; tổng XML kết quả **827 tests, 0 failures, 0 errors, 5 skipped, 155 suites**. File `TEST-tw.tsm.tests.MallGs206CatalogQuotaRegressionTest.xml` đã xuất hiện.
- Dùng scheduled task `TSM_DODO_ONESHOT`: /End rồi /Run. Server mới lắng nghe `0.0.0.0:6613` lúc **04:43:12 ngày 10/10/2026 (UTC+7)**, Java **PID 12644** (chỉ đúng tại thời điểm kiểm tra); `logs\runtime-production.log` thông báo **máy chủ đã sẵn sàng**, không thấy lỗi `ERROR`/`Exception` trong các dòng startup đã kiểm tra.
- **Chưa thử mua hàng trực tiếp trong client sau lần restart mới** và **chưa kiểm chứng dữ liệu MySQL bằng truy vấn SQL độc lập**. Startup hoàn tất bình thường là tín hiệu tích cực nhưng không thay thế kiểm tra lưu quota thực tế.

## Phạm vi 206 dòng dữ liệu PC

Theo `IOS_GS_C.dat` (client PC, không lấy Android làm chuẩn):
- `storeKind=1` **138 hàng Tiệm thường**: 120 `saleKind=0`; 18 `saleKind=1` giới hạn lượt mua theo ngày. Thanh toán dựa trên `currencyKind`, gồm Point/KNB, bạc game và các tiền chuyên dụng được server hỗ trợ.
- `storeKind=5` **32 hàng Doro**: giữ thưởng/giao thức cũ và thêm theo dõi giới hạn cho 2 hàng `saleKind=1`.
- `storeKind=2` **32 hàng** và `storeKind=4` **4 hàng** là kênh mua IAP/nạp tiền (`currencyKind=0`). **Không** cấp item qua C66-1 miễn phí hay bằng cách bỏ qua xác thực receipt. Hệ thống thanh toán thực cho 36 hàng này vẫn cần thiết kế riêng.

PR #9 bổ sung bảng `character_mall_quotas`, logic `MallQuotaSystem` tính kỳ, tính lượt, kiểm tra hạn mức, đồng bộ `RoleCount` khi đăng nhập và khóa yêu cầu mua đồng thời theo nhân vật. Đã có các bài kiểm thử hồi quy. Điều này chưa đảm bảo transaction tuyệt đối xuyên tiền, vật phẩm và quota khi tiến trình crash đúng thời điểm giữa các hệ thống; cần đối soát giao dịch nếu xuất hiện bất thường.

## Việc kiểm tra trong game tiếp theo

1. Khởi động lại client PC `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING`, đăng nhập.
2. Vào Tiệm, mua 1 túi Tinh Linh giá 19 Kim Nguyên Bảo (ID trong dữ liệu có `34695` và `34428`), xác nhận trừ đúng 19 và thêm đúng 1 túi.
3. Mua 1 hàng thường thanh toán bằng bạc và 1 hàng currency đặc biệt trên nhân vật **thử nghiệm** có sẵn số dư hợp lệ; không phát tiền hoặc tự tạo đồ cho nhân vật live.
4. Kiểm tra mặt hàng giới hạn theo ngày: mua một lần, xem `RoleCount`/UI cập nhật, cố mua quá số lượt để chắc chắn bị chặn, đăng xuất/đăng nhập để đảm bảo hạn mức còn giữ.
5. Với Doro, kiểm tra giới hạn và thưởng nhận được. Nhóm nạp tiền (IAP store=2/4) vẫn phụ thuộc receipt/payment và **chưa được tuyên bố là xử lý xong**.
6. Nếu phát sinh lỗi, xem `logs\runtime-production.log` + client `Player.log` quanh giao dịch `C:066-001`/phản hồi.

## An toàn và lưu ý

- SSH alias `tsm-prod` khi gọi OpenSSH từ máy client tại thời điểm này vẫn trả `255`. Kết nối SSH trực tiếp tới cùng host bằng cấu hình khóa máy client với xác minh host key hoạt động và đã dùng để triển khai. Không cần đổi quyền GitHub.
- Log khởi động hiện có thể in biến môi trường Java chứa thông tin đăng nhập DB; khi thu thập/log chia sẻ phải **ẩn mật khẩu và các chuỗi `JAVA_TOOL_OPTIONS` nhạy cảm**, không copy nguyên log vào GitHub.
- GitHub main đã chứa PR #9 và tài liệu này; các sửa đổi mã nguồn tiếp theo phải kiểm thử rồi commit/push về `BauTroiDo/tsm-dodo-server`.

Các tài liệu liên quan: [rà soát PR #9](pc-gs206-shop-quota-handoff-20261010.md), [mua túi Tinh Linh PR #8](pc-mall-shop-elf-bag-fix-20261010.md), [backend Tinh Linh](pc-dvt-elf-107-deploy-20261010.md).

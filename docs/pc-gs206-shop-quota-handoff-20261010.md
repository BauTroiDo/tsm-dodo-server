# TS Đa Vũ Trụ PC — Bàn giao rà soát 206 mặt hàng GS_C, 10/10/2026

## Trạng thái thật
- GitHub: **đã merge** PR [#9](https://github.com/BauTroiDo/tsm-dodo-server/pull/9) vào `main`, commit `0ae55348d4179f20ec2fbeaf38d55117a06af9e7`.
- CI: workflow `37993945929`, GitHub Actions **SUCCESS**, `BUILD SUCCESSFUL in 2m 14s`, bao gồm kiểm thử `MallGs206CatalogQuotaRegressionTest`.
- **CHƯA DEPLOY LÊN SERVER GAME.** Công cụ an toàn từ chối thao tác chép mã nguồn qua SSH/SFTP; không thử vượt chặn. Server vẫn dùng bản sau PR #8 (commit `4b6b7f93405c1e38035f48b24b98eaabbb087078`) khi kiểm tra trước đó, PID 10700/cổng 6613; PID tại lần đọc sau có thể thay đổi.
- PC client: `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING`, Windows server: `192.168.1.69`, thư mục `C:\Server-Game\tsm_dodo`; người dùng ủy quyền sử dụng SSH alias `tsm-prod`, GitHub repo `BauTroiDo/tsm-dodo-server`.
- Working tree server có **171 thay đổi chưa commit** trước khi tiến hành. KHÔNG dùng `git reset`, `git checkout`, `git pull` ghi đè local: nhiều bản sửa quest, pet, Tinh Linh đã chạy ổn.
- Đã tải nội dung 9 file của PR #9 về PC dưới `C:\Users\QT\AppData\Local\Temp\tsm_gs206_quota_patch_20261010`. File manifest.json lưu đường dẫn tương ứng và Git blob SHA, chỉ là bản staging; **không có backup mới và không chép live** do lần gọi công cụ bị chặn.

## Khảo sát bảng 206 dòng (`IOS_GS_C.dat` đúng nguồn PC)
- `storeKind=1`: **138 dòng**, bao gồm **120 không giới hạn** (saleKind=0) và **18 giới hạn theo ngày** (saleKind=1, `flag` RoleCount + `dayCount`). Loại tiền tệ: 115 dòng kind=1 Point, 14 dòng kind=3 bạc trò chơi, 9 dòng kind=8 tiền chuyên dụng.
- `storeKind=5` Doro: **32 dòng**, **30 saleKind=0 + 2 saleKind=1**. Cơ chế phân phối thưởng theo pool đã có từ trước, PR #9 bổ sung quota cho hai gói giới hạn.
- `storeKind=2`: **32 dòng**, `storeKind=4`: **4 dòng**. Cả **36 dòng có currencyKind=0**, thuộc **luồng IAP/gói nạp** trong Lua DVT, không được phát vật phẩm qua C66-1 thông thường nếu chưa xác thực giao dịch bên ngoài.
- `EStoreKind` gốc: `Mall=1, NTD=2, Cash=3, NTD2=4, Doro=5, Organization=6, PVP=7`. Source Lua `_native_dump_dvt115\_lua_client_decrypted\Data\GoodsItemData.lua`; UI `UIMall.lua`, `Mall.lua`.
- Nguyên nhân lịch sử lỗi mua túi Tinh Linh: MallHandler từng chỉ cho store5, từ chối C66-1 store1 và gửi client lỗi chung “Tiền bạc không đủ”. PR #8 đã giải quyết hàng thường nhưng chưa có quota.

## PR #9 gồm 9 file
1. `app/src/main/kotlin/tw/tsm/app/handlers/MallHandler.kt` (khóa theo nhân vật, dự trữ/hủy quota trước/sau thanh toán và phát đồ; giữ Doro)
2. `app/src/main/kotlin/tw/tsm/app/ServerModule.kt` (DI MallQuotaRepository/MallQuotaSystem)
3. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/LoginService.kt` (làm mới các RoleCount kỳ hết hạn trước S85-001)
4. `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/MallQuotaSystem.kt` (logic kỳ ngày/tuần/tháng/vĩnh viễn, reserve, rollback và complete)
5. `domain/src/main/kotlin/tw/tsm/domain/interfaces/repositories/MallQuotaRepository.kt`
6. `persistence/src/main/kotlin/tw/tsm/persistence/repositories/MallQuotaRepositoryImpl.kt`
7. `persistence/src/main/kotlin/tw/tsm/persistence/tables/CharacterMallQuotas.kt` (bảng mới)
8. `persistence/src/main/kotlin/tw/tsm/persistence/DatabaseFactory.kt` (tự tạo bảng qua Exposed)
9. `tests/src/test/kotlin/tw/tsm/tests/MallGs206CatalogQuotaRegressionTest.kt` (kiểm kê đủ 206, 20 dòng giới hạn, vòng đời quota và RoleCount)

## Cần làm tiếp nếu có công cụ được phép triển khai
1. Đối chiếu bốn file đang tồn tại trên Windows server khớp với bản main trước PR #9 (blob): `MallHandler.kt=ccdda75abbb38d910a58b6440225b7ce79bf3d62`, `ServerModule.kt=c985ada4ad884f883269223ae46709550a0e39fa`, `LoginService.kt=c59c656a0bfec6c478976b6e45d016c5149ae81f`, `DatabaseFactory.kt=6b26355968ad46d22186a98eea41f8eea58dcddf`. Nếu khác, cần review/merge, không đè.
2. Sao lưu bốn file gốc + trạng thái DB. Chỉ thêm bốn file mới `MallQuotaRepository.kt`, `MallQuotaSystem.kt`, `MallQuotaRepositoryImpl.kt`, `CharacterMallQuotas.kt`; cộng test mới `MallGs206CatalogQuotaRegressionTest.kt`; và cập nhật đúng bốn file cũ. Backup trước.
3. Chạy targeted và full `gradlew.bat :tests:test --offline --console=plain` trên server. Chỉ khi qua mới restart qua task `TSM_DODO_ONESHOT`, kiểm tra `runtime-production.log`, cổng 6613 và bảng `character_mall_quotas`.
4. Test bằng nhân vật phụ: shop không giới hạn, item giá KNB (store1), item dùng bạc, item dùng currency8, item giới hạn 1 ngày, trường hợp hết tiền/túi đầy/2 request đồng thời, relog và đổi ngày (có đồng hồ kiểm thử). Với Doro, test cả thành công, hạn mức và reward order.
5. IAP 36 gói: cần tích hợp kênh xác thực receipt thanh toán phù hợp với môi trường PC; **không** âm thầm coi currencyKind=0 là giá 0 và cấp đồ miễn phí. Các gói này không được tính là “mua thành công” khi chưa có giải pháp payment.
6. Cân nhắc harden tiếp: mô hình reservation/payment/bag gồm nhiều hệ thống lưu trữ, nên chưa phải một transaction DB thật sự xuyên toàn bộ thao tác. Nếu crash đúng lúc, cần đối soát giao dịch. Bản PR #9 đã giảm rủi ro nhờ quota persisted + compensation, nhưng chưa chứng minh atomicity đầy đủ.
7. Push/commit mọi sửa mới và cập nhật báo cáo GitHub cho phiên chat sau, theo yêu cầu người dùng.

Tuyệt đối không tự cấp tiền/vật phẩm vào nhân vật đang chơi để kiểm thử. Đừng tuyên bố hoàn thành live khi chỉ mới merge GitHub.

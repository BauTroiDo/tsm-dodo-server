# DVT Kotlin Eve — kiểm tra trước giao dịch vàng trong chuỗi kết quả (2026-10-10)

## Nguyên nhân gốc trong server Kotlin

`EveResultExecutor.canExecuteResults` có bước preflight cho nhiệm vụ, vật phẩm và tướng nhưng chưa mô phỏng **cả chuỗi thay đổi vàng**. Trong khi `EveFlowController.dispatchResult` xử lý các kết quả lần lượt:

- `resultType=0/7, resultClass=5, pStyle1`: cộng vàng.
- `resultType=0/7, resultClass=5, pStyle2`: trừ vàng.
- `resultClass=7, pStyle6, resultValue>0`: thưởng vàng theo chính `EveResultClassPolicy.playerOperation` hiện hành.

`MoneySystem.addGold` có thể từ chối khi vượt `ItemConstants.MAX_GOLD = 999,999,999`; `subtractGold` từ chối khi thiếu vàng. Nếu các result trước đó đã tăng nhiệm vụ, trao/thu vật phẩm mà cuối chuỗi mới gặp lỗi vàng, sự kiện bị hủy với trạng thái đã thay đổi một phần. Đây là lỗi transaction boundary **thuộc Kotlin server**, không sửa Lua/client hoặc Eve.emg.

## Sửa theo ngữ nghĩa DVT đã triển khai

Thêm `EveQuestGoldPreflightPolicy.validate(results, initialGold)`:

1. Mô phỏng kết quả cộng/trừ vàng **theo đúng thứ tự** trên bản sao số dư `Long`, không thay đổi tiền thật.
2. Từ chối ngay nếu bất kỳ thao tác nào thiếu vàng, vượt trần hoặc `pStyle` tiền không hợp lệ.
3. Nhận diện phần thưởng `resultClass7/pStyle6` từ chính bảng operation Kotlin đã đối chiếu DVT; không thay đổi contract gói mạng/giá trị.
4. Dừng mô phỏng tại `resultType3` (battle) hoặc `resultType6` (Surface) vì phần sau phải đợi người chơi đánh/chọn để xác định.
5. Tích hợp vào `EveResultExecutor.canExecuteResults` **trước mọi item/quest/reward mutation**. Trường hợp từ chối: hủy event và báo thiếu/vượt hạn mức, không cấp phát một phần.

## Kiểm thử

`EveQuestGoldPreflightRegressionTest` có 7 ca:

- Thiếu vàng cho khoản trừ sau kết quả tăng bước.
- Mô phỏng nhiều lần cộng/trừ theo thứ tự và phát hiện số dư âm giữa chuỗi.
- Vượt trần vàng ở kết quả sau nhiệm vụ.
- Kiểm tra vàng thưởng `resultClass7/pStyle6`.
- Mã tiền không hợp lệ.
- Không dự đoán branch sau battle/Surface.
- **Integration test với EveFlowController**: kết quả đầu thu vật phẩm, tiếp theo tăng mission, sau đó trừ vàng quá số dư; xác nhận stateMachine.clear nhưng `inventory.removeBagItemsExact`, `questSystem.addMissionStep`, `moneySystem.subtractGold` không được gọi.

Full `./gradlew.bat :tests:test --offline --console=plain` trên worktree cô lập Windows: **BUILD SUCCESSFUL**; 7/7 test mới PASS.

## Giới hạn được công khai

Đây là **preflight phòng ngừa lỗi**, không phải transaction ACID của nhiều repository. Nếu số dư/thùng đồ thay đổi từ luồng cạnh tranh **sau** khi preflight nhưng trước mutation, rủi ro vẫn tồn tại. Không có DB migration, không thay đổi progress nhân vật, không tạo reward phỏng đoán. Muốn bảo đảm tuyệt đối cần thiết kế transaction/compensation chung Money+Inventory+Quest theo runtime cache và database.

Trước triển khai: kiểm tra PR CI, sao lưu SQL + live EveResultExecutor, áp dụng **diff chọn lọc** vì production có các sửa đổi local pStyle3 và các fix nhiệm vụ khác; chạy test live; restart duy nhất JVM game port6613 bằng launcher WMI có log riêng và xác nhận sẵn sàng. Không git pull/reset trên live.

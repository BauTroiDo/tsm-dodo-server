# Eve DVT — bền vững hóa chặn phát thưởng sau crash (giai đoạn 1)

## Bằng chứng lỗi và mục tiêu

- Sau PR #35, `EveFlowController.dispatchResult` vẫn thực thi nhiều hiệu ứng trên `InventorySystem`, `QuestSystem`, `MoneySystem`, `PetSystem` ... **trước** khi `completeEventInternal` ghi EveCount.
- PR #35 chỉ chặn lặp bằng **RAM** nếu thao tác cuối không được MySQL xác nhận. JVM chết trước lúc `completeEventInternal` thì dấu chặn đó cũng biến mất.
- Một lần phát thưởng không thể “exactly once” nếu ghi database và gửi packet cho client không có cùng cơ chế idempotency/transaction. Không tự nhận đã khắc phục triệt để.

## Primitive trong PR này — chưa được gắn vào flow live

`character_eve_effect_claims` giữ một bản ghi cho bộ khóa `(char_id, map_id, eve_no)`, gồm `run_token` (duy nhất trong lượt Eve) và `reserved_at_epoch_ms`.

- `reserve`: insert-if-absent qua khóa chính MySQL/H2; **cùng token** có thể tiếp tục nhiều result; token khác không được “cướp” claim còn mở.
- `unresolvedForCharacter`: đọc để kiểm toán hoặc đối soát, không tự dọn theo ngày/đăng nhập.
- `settle`: kiểm tra đúng token dưới khóa `SELECT ... FOR UPDATE`, tăng EveCount bằng SQL UPSERT, đọc count và xóa claim trong **cùng transaction**. Gọi lại bằng token cũ không tăng lần thứ hai.
- Nếu JVM chết sau khi phần thưởng đã được trao nhưng trước settlement, claim còn nguyên để không khởi chạy một run khác và phát thưởng lại.
- Nếu crash ngay sau reserve nhưng trước reward, claim vẫn giữ: đây là cố ý **fail closed**, cần xác minh trước khi cho thử lại.

## Việc bắt buộc trước khi bật cho người chơi

1. Sinh token ổn định cho mỗi `EventSession`, mang qua `copy()`, choice, battle, warp và auto-chain; không dùng cùng token cho Eve thực sự khác.
2. Xác định đầy đủ các `EveResult` có thay đổi trạng thái dựa trên DVT Lua/IL2CPP và chính mã `EveResultExecutor`. **Phải reserve trước hiệu ứng đầu tiên**, kể cả thưởng vàng/nhiệm vụ đến sau hội thoại hay sau battle.
3. Cắt mọi đường chạy thưởng khi reserve thất bại/claim thuộc token khác; người chơi nhận thông báo để admin xử lý, không clear claim.
4. Chỉ `settle` sau tất cả hiệu ứng thành công; thay thế đúng đường `insertCompletedEventAndGetCount` và chặn count tăng hai lần. Eve chỉ Talk, NPC dịch vụ, và sự kiện `eveNo=0` phải giữ semantics DVT.
5. Xác minh hành vi khi disconnect, scene reset, hủy Surface, thua battle, quà đã trao nhưng người chơi chưa bấm Next, và MySQL unavailable.
6. Viết test fault injection cho từng ranh giới: crash sau reserve, sau gold/item/quest effect, trước settlement, commit mất ACK, đăng nhập lại, nhiều gói 020-006/009 và double callback battle.
7. Có công cụ admin đối soát read-only, ghi rõ người chơi/scene/Eve/claim age và snapshot vật phẩm, tiền, nhiệm vụ, EveCount; chỉ giải phóng claim sau xác minh. **Tuyệt đối không tự xóa claim sau timeout**.

## Giới hạn

PR này mới bổ sung *database schema, storage API, H2 integration tests*. Chưa thay đổi luồng phát thưởng. Không merge/deploy live như một bản “sửa xong chống double reward” khi chưa có phần (1–7). Bảng mới được đăng ký trong DatabaseFactory nhưng chưa có đường gọi ở gameplay.

Khả năng tái chạy các Eve được phép lặp lại (farm, phục vụ NPC, hoạt động theo ngày) phải được chứng minh trước khi kích hoạt reservation. Nếu một quy trình bị đóng băng bởi claim tồn đọng, cần đối soát, không cho phát lại tự động.

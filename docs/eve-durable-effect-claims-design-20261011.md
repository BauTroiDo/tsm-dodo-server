# Eve DVT — giữ quyền phát thưởng bền vững (PR #36 thử nghiệm)

## Bằng chứng lỗi và mục tiêu

- Sau PR #35, `EveFlowController.dispatchResult` vẫn thực thi nhiều hiệu ứng trên `InventorySystem`, `QuestSystem`, `MoneySystem`, `PetSystem` ... **trước** khi `completeEventInternal` ghi EveCount.
- PR #35 chỉ chặn lặp bằng **RAM** nếu thao tác cuối không được MySQL xác nhận. JVM chết trước lúc `completeEventInternal` thì dấu chặn đó cũng biến mất.
- Một lần phát thưởng không thể “exactly once” nếu ghi database và gửi packet cho client không có cùng cơ chế idempotency/transaction. Không tự nhận đã khắc phục triệt để.

## Primitive trong PR này — đã nối vào flow ở nhánh thử nghiệm, chưa triển khai live

`character_eve_effect_claims` giữ một bản ghi cho bộ khóa `(char_id, map_id, eve_no)`, gồm `run_token` (duy nhất trong lượt Eve) và `reserved_at_epoch_ms`.

`character_eve_effect_settlements` giữ **biên nhận vĩnh viễn** theo khóa `(char_id, run_token)` sau khi chốt xong, kèm map/Eve/count/thời điểm. Không được dùng lại một `run_token` đã xuất hiện trong bảng này, kể cả sau khi bản ghi claim được xóa.

- `reserve`: insert-if-absent qua khóa chính MySQL/H2; **cùng token** có thể tiếp tục nhiều result; token khác không được “cướp” claim còn mở.
- `unresolvedForCharacter`: đọc để kiểm toán hoặc đối soát, không tự dọn theo ngày/đăng nhập.
- `settle`: kiểm tra đúng token dưới khóa `SELECT ... FOR UPDATE`, tăng EveCount bằng SQL UPSERT, đọc count, ghi biên nhận settled token và xóa claim trong **cùng transaction**. Gọi lại bằng token cũ không tăng lần thứ hai. Trường hợp commit đã thành công nhưng mất ACK vẫn bị chặn tái chạy bằng token cũ.
- Nếu JVM chết sau khi phần thưởng đã được trao nhưng trước settlement, claim còn nguyên để không khởi chạy một run khác và phát thưởng lại. Nếu JVM chết ngay sau settlement, biên nhận còn nguyên dù claim đã xóa.
- Nếu crash ngay sau reserve nhưng trước reward, claim vẫn giữ: đây là cố ý **fail closed**, cần xác minh trước khi cho thử lại.

## Trạng thái kết nối gameplay trong PR #36

- `EventSession` mang `runToken` UUID và `effectClaimed` xuyên suốt `copy()`, lựa chọn, chiến đấu, chuyển cảnh. Các lượt Eve mới dùng token mới.
- `EveEffectClaimPolicy` phân loại các hiệu ứng server có thể thay đổi vật phẩm, nhiệm vụ, kỹ năng, tiền, võ tướng, Moral, Point và RoleCount; bỏ qua Talk, Surface và giao diện dịch vụ lặp lại. `resultClass=3/pStyle=1` là thu phục NPC, **không** phải animation thuần.
- `EveFlowController` gọi `reserve` trước `EveResultExecutor` và một số đường đặc biệt (tân thủ/mở mission, auto-capture sau thắng trận). Gói `020-006` bị chặn trong lúc giữ quyền DB đang chờ; claim thất bại thì dừng hiệu ứng, không tăng EveCount.
- Nếu có claim, khi hoàn thành gọi `settle` để cập nhật EveCount, ghi biên nhận, giải phóng claim trong giao dịch; nếu không có hiệu ứng được giữ quyền thì dùng hành vi EveCount cũ.
- `ServerModule` đăng ký repository cho production, nhưng chỉ tồn tại trên nhánh PR này. Không có thay đổi server live.

## Việc bắt buộc trước khi bật cho người chơi

1. **Đã có:** sinh token riêng cho mỗi `EventSession`, truyền qua `copy()`, choice, battle, warp và auto-chain; test UUID và CAS.
2. **Còn audit:** xác nhận toàn bộ `EveResult` thay đổi trạng thái theo DVT Lua/IL2CPP và mã hiện tại. Hệ thống có đường thưởng thuộc `BattleManager`, callback dungeon sau hoàn thành, và những `resultClass` chưa hỗ trợ (11/13/15) nằm ngoài chính sách hiện tại. Cần đối chiếu đường phát thưởng trước hiệu ứng đầu tiên ở những subsystem này.
3. **Đã có ở EveFlowController:** dừng phần thưởng trên các nhánh được nhận diện khi reserve thất bại/claim thuộc token khác; người chơi được báo để admin xử lý, không xóa claim.
4. **Đã có nhưng cần thêm test:** `settle` của event có claim đi sau các hiệu ứng được dispatch; Talk-only/NPC dịch vụ/eveNo=0 tiếp tục dùng luồng cũ. Cần kiểm tra nhánh hủy, dịch chuyển, thưởng battle/dungeon riêng.
5. Xác minh hành vi khi disconnect, scene reset, hủy Surface, thua battle, quà đã trao nhưng người chơi chưa bấm Next, và MySQL unavailable.
6. Viết test fault injection cho từng ranh giới: crash sau reserve, sau gold/item/quest effect, trước settlement, commit mất ACK, đăng nhập lại, nhiều gói 020-006/009 và double callback battle.
7. Có công cụ admin đối soát read-only, ghi rõ người chơi/scene/Eve/claim age và snapshot vật phẩm, tiền, nhiệm vụ, EveCount; chỉ giải phóng claim sau xác minh. **Tuyệt đối không tự xóa claim sau timeout**.

## Giới hạn

PR này đã bổ sung *hai bảng, storage API, gameplay wiring có điều kiện và regression*. **Chưa có admin reconciliation UI, chưa fault-inject đầy đủ tất cả subsystem và chưa đối chiếu mọi DVT effect**. Không merge/deploy live như một bản đảm bảo đúng tuyệt đối khi các điều kiện còn mở. Bảng chỉ tạo trên server live nếu triển khai code mới và DatabaseFactory khởi chạy.

Khả năng tái chạy các Eve được phép lặp lại (farm, phục vụ NPC, hoạt động theo ngày) phải được chứng minh trước khi kích hoạt reservation. Nếu một quy trình bị đóng băng bởi claim tồn đọng, cần đối soát, không cho phát lại tự động.

# Bước 9 — Turn2 career state integrity

## Turn2 Ball source audit

Client Lua xác nhận:
- `Role.playerData.turn2BallCount` khởi tạo 0.
- S23:77 ghi đè số Thiên Châu khả dụng và danh sách 12 nút đã kích hoạt.
- C28:5 gửi các Ball mới kích hoạt về server và UI trừ từ `turn2BallCount`.
- Không thấy client tự tính/cấp Ball từ level, turn hoặc career.

Do chưa xác định được Eve resultClass/quest reward authoritative dùng để cấp Ball, server chưa thêm công thức giả.

## Đổi Career và skill nghề cũ

C23:76 trước Bước 9 đổi `career` nhưng giữ lại skill Turn2/hero của nghề cũ trong `SkillCache` và DB. Điều này tạo state không hợp lệ.

Nay khi đổi nghề thành công:
1. validate mission + token như Bước 8;
2. tiêu token;
3. persist career mới;
4. xóa toàn bộ base Turn2 career skills và hero skill IDs khỏi skill map;
5. xóa hero slot selection;
6. persist skill/Turn2 state ngay;
7. cập nhật runtime career và gửi S23:69.

Các Ball đã kích hoạt được giữ nguyên vì Lua lưu `turn2Balls` độc lập với career. Không reset available/active Ball nếu chưa có bằng chứng protocol/data yêu cầu.

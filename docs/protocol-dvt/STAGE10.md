# Bước 10 — Rebirth semantics audit

## Bằng chứng từ client

`UITurn.lua` chỉ đóng vai trò chọn ngoại hình/nghề và gửi:
- C23:46 turn 1: hair + color1 + color2
- C23:54 turn 2: hair + color1 + color2 + career
- C23:81 turn 3: hair + color1 + color2 + career

Không có logic client trong UITurn tự reset level, EXP, stat point hoặc skill point.

`Role.lua` xác nhận:
- playerMaxLv = 200
- playerTurn3MaxLv = 50
- level tổng hợp dùng trong một số kiểm tra = Turn * 200 + Lv
- Turn >= 3 có Turn3Exp/Turn3 stats riêng trong protocol player data.

## Kết luận triển khai

Không tự động reset level/EXP/stat/skill khi nhận C23:46/54/81 ở giai đoạn này. Các thay đổi đó phải được chứng minh từ server/event/protocol gốc; tự đặt reset có nguy cơ phá character progression.

Bước 10 giữ strict progression `state.turn + 1 == targetTurn`, packet size và career domain đã có.

## Khoảng trống quan trọng tiếp theo

Third rebirth chưa được model đúng: client có `Turn3Element`, `Turn3Exp`, `Turn3Int/Atk/Def/Agi/Hpx/Spx` và skill list riêng. Server hiện mới lưu `turn=3` trên CharacterRecord. Đây là mục tiêu của bước kế tiếp; không nhét dữ liệu Turn3 vào các cột stat/EXP thường.

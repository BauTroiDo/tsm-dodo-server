# Bước 6 — Player Rebirth / Career

Nguồn Lua: `lua/UI/UITurn.lua`.

## Wire đã xác nhận

- C23:46 lần chuyển sinh 1: hair(Byte) + color1(Int32 LE) + color2(Int32 LE)
- C23:54 lần chuyển sinh 2: cùng payload + career(Byte)
- C23:81 lần chuyển sinh 3: cùng payload + career(Byte)
- Career hợp lệ trong UI: 1..4.

## Vá server

- ItemHandler xử lý 23:46, 23:54, 23:81.
- Bắt buộc tiến triển tuần tự: currentTurn + 1 == targetTurn.
- Kiểm tra đúng kích thước packet và career 1..4 cho turn 2/3.
- Lưu hair/color1/color2 và turn/career vào characters.
- Cập nhật PlayerState runtime.
- Gửi lại S3:0 authoritative sau khi chuyển sinh.
- S3:0 PlayerAppear gửi record.turn và record.career, không còn hard-code 0.

## Chưa tự suy đoán

Lua UI được mở từ event/quest và có các điều kiện nghiệp vụ nằm ngoài `UITurn.OnClick_Yes`.
Server hiện chưa tái dựng đầy đủ điều kiện quest/item để được phép chuyển sinh, nên bước này không giả định level, item hay mission ID cho player rebirth.

Career-change item C23:76 có mission-flag gating riêng (11487/11463/11491/11489) và chưa được mở ở bước này cho tới khi server-side mission/item consumption được đối chiếu đầy đủ.

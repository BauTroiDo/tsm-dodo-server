# Bước 13 — Turn3 element selection

Nguồn: `lua/UI/UISkillTree.lua` và `lua/Common/protocal.lua`.

## Chọn hệ Tam Chuyển lần đầu

Client chỉ cho chọn khi `Turn3Element == 0`:
`Network.Send(8, 12, payload[element Byte])`.

S8:12 client đọc đúng một byte `result`.

Server nay xử lý C8:12:
- IN_GAME;
- payload đúng 1 byte;
- nhân vật Turn >= 3;
- element chỉ Light/Dark (7/8 theo UI career display/skill element domain);
- persisted Turn3 element hiện tại phải bằng 0;
- thành công lưu vào `character_turn3_states` và trả result=0;
- request không hợp lệ/chọn lại trả result=1.

Đổi Light/Dark sau lần đầu không dùng C8:12; Lua gọi `UISell.Launch(58, ..., targetElement)`, nên phải đi qua shop/sell flow riêng.

## Light/Dark skills

UI xác nhận Light/Dark tab chỉ xuất hiện Turn >= 3. Các skill 220xx/230xx không dùng skill point thông thường; unlock đi qua `UISell.Launch(57, ..., skillId)`. Vì vậy không cho C28:1 học các skill này.

## EXP

`Role.lua` xác nhận khi Turn >= 3, thống kê EXP dựa trên `Turn3Exp`, không phải `Exp` thường. Công thức level/progression server-side vẫn chưa đủ bằng chứng; chưa chuyển battle EXP sang Turn3Exp trong bước này để tránh tạo progression sai.

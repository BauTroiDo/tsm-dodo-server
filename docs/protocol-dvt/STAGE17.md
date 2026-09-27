# Bước 17 — Turn3 stat allocation

Nguồn client: `UIStatus.lua`, `RoleController.lua`, `Calculator.lua`.

## Wire

UI Tam Chuyển vẫn dùng C8:1 với attrKind cơ bản 27..32 và cùng `AttrPoint`. Client không gửi attr 81..86 khi bấm cộng điểm.

Tuy nhiên S8:1 có thể cập nhật trực tiếp:
- 81 Turn3Int
- 82 Turn3Atk
- 83 Turn3Def
- 84 Turn3Hpx
- 85 Turn3Spx
- 86 Turn3Agi

UIStatus cộng các Turn3 stat này vào phần bonus hiển thị.

## Server

Khi Turn < 3: C8:1 giữ logic cũ, cộng vào base stats.

Khi Turn >= 3:
- validate attr 27..32, điểm dương, AttrPoint đủ, Turn3 stat <= 999;
- trừ chung `AttrPoint`;
- persist giá trị vào `Turn3State`, không sửa base stat;
- persist AttrPoint còn lại trong Characters;
- trả S8:1 bằng attr 81..86 để client cập nhật đúng `turn3Info`.

Turn3Hpx/Turn3Spx trigger tính lại MaxHP/MaxSP. Calculator.lua xác nhận:
- Turn3 Hpx được cộng vào hpx, ngoài bonus tự động theo Turn3Lv;
- Turn3 Spx được cộng vào spx, ngoài bonus tự động Turn3Lv.

## Combat stat

UIStatus xác nhận Turn3Int/Atk/Def/Agi là bonus cộng thêm. Việc đưa bốn stat này vào battle snapshot/damage calculation cần audit battle actor construction riêng; không trộn chúng vào base persisted stats.

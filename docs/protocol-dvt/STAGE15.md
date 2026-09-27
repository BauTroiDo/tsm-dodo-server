# Bước 15 — Tách battle EXP của Tam Chuyển

Nguồn authoritative: `lua/Controller/RoleController.lua`.

`EAttribute` client:
- Turn3Lv = 252
- Turn3Exp = 253
- Turn3Element = 254

Quan trọng hơn, `RoleController:SetAttribute(EAttribute.Exp, value)` có nhánh:
- nếu Player/Players và Turn == 3: ghi `data.turn3Info.exp = value`;
- ngược lại: ghi EXP thường.

Vì vậy wire battle EXP vẫn dùng attr 36. Không được gửi 253 thay cho flow EXP bình thường.

## Sửa server

Trước bước này `BattleManager.distributeExp` luôn:
- cộng `PlayerState.exp`;
- chạy level-up thường;
- cộng skill point/attribute point thường.

Điều đó sai với Turn3.

Nay khi `state.turn >= 3`:
1. load persisted `Turn3State`;
2. cộng battle reward vào `Turn3State.exp`;
3. save `character_turn3_states`;
4. gửi S8:1 với `EAttribute.EXP (36)`, client tự route sang `turn3Info.exp`;
5. không chạy level-up/skill point/attr point thường.

Turn < 3 giữ nguyên progression cũ.

## Chưa làm level-up Turn3

Chưa có đủ bằng chứng cho bảng EXP/cách tăng Turn3Lv 0..50 và cách cấp Turn3 stat. Do đó bước này cố ý chỉ sửa ownership của EXP, chưa tự đặt công thức level-up.

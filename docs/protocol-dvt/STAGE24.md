# Bước 24 — Đồng bộ HP/SP khi Tam Chuyển lên cấp

## Đối chiếu Calculator.lua

Khi turn >= 3:
- level dùng cho công thức cơ sở bị clamp về Role.playerMaxLv = 200;
- turn3Lv = level thực - 200;
- HPX của player cộng min(turn3Lv, 70) và Turn3Hpx;
- SPX của player cộng turn3Lv và Turn3Spx;
- các bonus HP theo mốc >70, >90, >140 nằm trong công thức nhưng cap hiện tại Lv250 => turn3Lv tối đa 50, nên chưa kích hoạt.

PlayerDataDto và StatHandler đã truyền:
- HPX = base + equip + min(turn3Lv,70) + Turn3Hpx;
- SPX = base + equip + turn3Lv + Turn3Spx;
- level=200, turn3Lv riêng.

Không có double-count trong StatSystem ở phạm vi Lv200..250.

## Lỗi runtime đã vá

BattleManager khi Turn3 lên cấp trước đây chỉ:
- persist level;
- cập nhật state/session level;
- gửi LV.

MaxHP/MaxSP vì thế giữ giá trị cấp cũ cho tới relog.

Sau vá, mỗi lần Turn3 tăng level:
1. tính turn3Lv mới;
2. tính lại MaxHP với base level 200 và HPX growth đúng Lua;
3. tính lại MaxSP với base level 200 và SPX growth đúng Lua;
4. cập nhật PlayerStateManager;
5. gửi MAX_HP/HP và MAX_SP/SP cho client.

updateMaxHp/updateMaxSp giữ current HP/SP hợp lệ thay vì tự giả định level-up phải full heal.

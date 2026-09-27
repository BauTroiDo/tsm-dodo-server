# Bước 16 — Turn3 level progression 201..250

Nguồn client authoritative:

`Role.lua`:
- `playerMaxLv = 200`
- `playerTurn3MaxLv = 50`

`Calculator.lua`:
- khi Turn >= 3: `turn3Lv = level - Role.playerMaxLv`, sau đó base level được clamp về 200 cho HP/SP.
- `Calc.GetUpExp(level, 3)` có công thức riêng cho level Tam Chuyển:
  - level <= 0 -> 1
  - còn lại dùng công thức pow/mod60/div60 hiện có.
- `StatSystem.calculateUpExp(..., 3)` đã port công thức này.

Kết luận: level hiển thị/persist runtime của Tam Chuyển là Lv 201..250; Turn3Lv là phần chênh `Lv - 200`, không phải một level độc lập trong S5:3.

## Battle progression

Khi Turn >= 3:
1. battle EXP cộng vào persisted `Turn3State.exp`;
2. dùng `calculateUpExp(level - 200, 3)`;
3. trừ EXP và tăng ordinary `Lv` tối đa 250;
4. gửi S8:1 EXP(36), client route vào Turn3Exp;
5. nếu lên cấp, gửi S8:1 LV(35).

Không cấp skill point/attr point thường cho level Tam Chuyển.

## Chưa triển khai stat Tam Chuyển

Client có Turn3Int/Atk/Def/Agi/Hpx/Spx riêng, nhưng chưa tìm được bằng chứng rằng mỗi Turn3 level tự cộng stat nào hoặc cấp bao nhiêu điểm phân phối. Bước này không tự đặt quy tắc đó.

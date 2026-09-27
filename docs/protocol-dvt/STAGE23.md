# Bước 23 — Turn3 EXP khớp Calculator.lua

## Bằng chứng client

UIStatus khi turn >= 3:
- level = level - Role.playerMaxLv;
- exp = Turn3Exp;
- maxValue = Calc.GetUpExp(level + 1, turn);
- value = exp - Calc.GetNeedTExp(level, turn).

Calc.GetNeedTExp(level, turn) cộng GetUpExp(i, turn) từ i=1..level.

Do đó Turn3Exp là EXP tích lũy toàn bộ nhánh Tam Chuyển, không phải phần dư của cấp hiện tại.

## Lỗi cũ

BattleManager:
- Lv200 gọi GetUpExp(0,3)=1 nên 1 EXP có thể lên Lv201;
- sau khi lên cấp trừ EXP requirement khỏi Turn3State.exp, làm client trừ cumulative threshold lần nữa;
- StatSystem làm tròn từng multiplier khác với Lua.

## Vá

- Lv200 -> Lv201 so với cumulative GetNeedTExp(1,3).
- Lv201 -> Lv202 so với GetNeedTExp(2,3), v.v.
- Turn3State.exp luôn giữ cumulative EXP; không trừ khi level up.
- gửi EAttribute.EXP bằng cumulative Turn3Exp.
- công thức GetUpExp turn=3 khớp thứ tự round của Lua:
  round(round(pow(mod60,3.85)) * pow(div60+1.2,1.6)
      + 1400000 * pow(div60+0.5,2.6)).
- cap vẫn Lv250.

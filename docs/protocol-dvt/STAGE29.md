# Bước 29 — Quy tắc requireSp=1

## Bằng chứng Lua

SkillData.lua ghi rõ:

`if self.requireSp == 1 then -- 規則 若填消耗1點SP=消耗全部`

Nghĩa là dữ liệu skill có requireSp=1 dùng giá trị 1 như sentinel: skill tiêu toàn bộ SP hiện tại, không phải chỉ 1 SP.

Client khi chọn skill vẫn kiểm tra `currentSp < requireSp`, nên với sentinel 1 cần ít nhất 1 SP để dùng.

## Lỗi server

BattleSkillResolver trước đây:
- kiểm tra attacker.sp < skill.requireSp;
- sau đó trừ chính skill.requireSp.

Vì vậy requireSp=1 chỉ bị trừ 1 SP.

## Vá

Resolver giữ kiểm tra tối thiểu theo requireSp như client.
Khi resolve:
- requireSp <= 0: không tốn SP;
- requireSp == 1: spConsumed = toàn bộ SP hiện tại, SP còn 0;
- requireSp > 1: trừ đúng giá trị data.

BattleInstance pre-ACK check hiện đã tương thích: requireSp=1 yêu cầu ít nhất 1 SP.

Chưa suy đoán thêm damage/effect của các skill đặc biệt nếu Lua chỉ thể hiện animation/status client-side.

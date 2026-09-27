# Bước 28 — Skill level và phạm vi battle

## Bằng chứng Lua

SkillData:GetFightArea(skillLv) dùng fightWayGrowType.

fightWayGrowType=1:
- Lv1 => area 1
- Lv2..3 => area 2
- Lv4..6 => area 5
- Lv7..9 => area 6
- Lv10 => area 8

fightWayGrowType=2:
- Lv1..3 => area 3
- Lv4..6 => area 5
- Lv7..9 => area 6
- Lv10 => area 8

Client lấy skillLv bằng role:GetSkillLv(skillId), clamp 1..10.

## Lỗi server

BattleFighter trước đây chỉ snapshot IntArray skill IDs.
BattleSkillResolver luôn dùng SkillDef.fightArea cố định, nên bỏ qua fightWayGrowType và skill level.

## Vá

BattleFighter thêm skillLevels: Map<Int,Int>.
BattleManager merge level từ:
- SkillSystem live cache cho normal/Turn1/Turn2;
- Turn3 repository cho Quang/Ám.

BattleSkillResolver tính fightArea từ skill level theo đúng bảng Lua trước khi resolveTargets và trước khi trả S50:1 metadata.

Chưa áp dụng skill level vào numerical/damage vì chưa có bằng chứng Lua cho công thức tăng numerical theo level; không suy đoán.

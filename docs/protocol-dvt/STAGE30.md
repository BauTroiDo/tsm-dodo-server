# Bước 30 — Sửa mapping SkillData.hitStatus

## Bằng chứng nguồn client

lua/Data/SkillData.lua định nghĩa trường hitStatus đọc từ Skill_C.dat:

- 0 = bình thường
- 1 = băng phong (冰封)
- 2 = toàn phong / whirlwind (旋風)
- 3 = thụ tinh / clump (樹精)

Đây là mã của trường dữ liệu SkillData, không phải ID của enum trạng thái runtime server.

## Lỗi server

BattleDamageCalc trước đây gọi:

`BattleStatus.fromId(skill.hitStatus)`

Trong BattleStatus server:
- 1 = POOR
- 2 = GOD
- 3 = SPELL
- ICE_BOUND = 6
- WHIRL_WIND = 5
- CLUMP = 7

Do đó SkillData hitStatus 1/2/3 bị biến thành ba trạng thái hoàn toàn sai.

## Vá

Mapping tại biên SkillData:
- hitStatus 1 -> BattleStatus.ICE_BOUND
- hitStatus 2 -> BattleStatus.WHIRL_WIND
- hitStatus 3 -> BattleStatus.CLUMP
- giá trị khác -> NONE

Chỉ set statusRounds khi mapping tạo ra status hợp lệ.

Không đổi ID của BattleStatus vì enum đó còn được dùng cho runtime/protocol khác; sửa tại nơi diễn giải SkillData tránh phá các call site hiện có.

## Audit Quang/Ám

Các file animation như WTiger/Basaltic/LightHealth/LightDisStatus không tự quyết định damage/state authoritative; chúng chủ yếu diễn S50 battle result. Vì vậy không suy ngược gameplay effect từ animation. Các status đặc biệt ngoài trường hitStatus cần thêm bằng chứng packet/server trước khi triển khai.

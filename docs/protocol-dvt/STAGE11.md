# Bước 11 — Turn3 persistence foundation

Nguồn wire: `lua/Logic/Role.lua -> Role.ReceivePlayerData`.

Sau danh sách skill thường, S5:3 chứa đúng thứ tự:
- Turn3Element: Byte
- Turn3Exp: Int64
- Turn3Int: UInt16
- Turn3Atk: UInt16
- Turn3Def: UInt16
- Turn3Agi: UInt16
- Turn3Hpx: UInt16
- Turn3Spx: UInt16
- Turn3 skill count: UInt16
- mỗi skill: skillId UInt16 + level Byte

Client cũng xác nhận Turn >= 3 dùng `Turn3Exp` khi thống kê EXP và có trần mở rộng 50 level trên nền playerMaxLv=200.

## Persistence

Tạo `character_turn3_states`:
`character_id, element, exp, int_, atk, def, agi, hpx, spx`.

Tạo `character_turn3_skills`:
`character_id, skill_id, level`.

Hai bảng được thêm vào `SchemaUtils.createMissingTablesAndColumns`.

Chưa tự sinh Turn3 state hoặc skill khi C23:81. Bước này chỉ dựng schema đúng wire; trigger, element lựa chọn, EXP và skill progression cần tiếp tục đối chiếu protocol/Eve trước khi mutation.

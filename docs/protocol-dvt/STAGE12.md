# Bước 12 — Turn3 repository và login sync

## Repository

`SkillRepository` có `Turn3State` độc lập:
- element
- exp
- int/atk/def/agi/hpx/spx
- skills(skillId -> level)

`SkillRepositoryImpl` đọc/ghi `character_turn3_states` và `character_turn3_skills`. Save state thay toàn bộ snapshot trong một Exposed transaction.

## Login

`LoginService` đọc `findTurn3State(record.id)` trước khi dựng S5:3.

`PlayerDataDto.fromCharacterRecord(..., turn3)` ánh xạ state đã persist vào các field Turn3 mà `LoginResponseSender.sendPlayerData` vốn đã serialize đúng thứ tự client `Role.ReceivePlayerData` đọc.

Nhân vật chưa có row Turn3 nhận state 0 mặc định, giữ tương thích với nhân vật cũ.

## Chưa làm

Chưa tự tạo/cấp Turn3 element, EXP, stat hay skill khi C23:81. Bước tiếp theo phải xác định mutation/progression từ Lua/Eve trước.

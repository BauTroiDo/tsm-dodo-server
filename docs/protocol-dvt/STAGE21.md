# Bước 21 — Chặn C28:1 bypass skill Quang/Ám

## Bằng chứng client

UISkillTree.AddSkillLv:
- nếu currentTag == ESkillTag.LightDark thì return ngay;
- nút Add/Sub cũng bị ẩn cho Hero và LightDark;
- vì vậy skill Quang/Ám không được nâng bằng C28:1.

UISkillTree.UnLockSkill:
- skill đúng Turn3Element mở khóa qua UISell.Launch(57,...).

Danh sách Light/Dark:
- Light: 22001,22002,22003,22004,22007,22010,22015,22016,22011
- Dark: 23001,23002,23003,23004,23009,23010,23015,23016,23011

22011/23011 là summon đặc biệt:
- client TransferSummonID kiểm tra trang bị đặc biệt 23361/23367 cho Light và 23362/23368 cho Dark;
- vẫn thuộc LightDark tab nên cũng không được nâng bằng C28:1.

## Vá server

SkillSystem.upgradeSkills từ chối toàn bộ 18 Light/Dark skill nếu chúng xuất hiện trong C28:1.

Mục tiêu:
- client giả mạo không thể đưa Turn3 skill vào character_skills;
- không trừ skill point;
- không tạo hai nguồn persistence xung đột giữa character_skills và character_turn3_skills;
- UISell57 vẫn là đường unlock authoritative.

# Bước 25 — Khóa summon Quang/Ám phía battle

## Bằng chứng Lua

UISkillTree.TransferSummonID:
- 22011 (Quang) chỉ giữ nguyên khi:
  - Equip_Spec là 23361 hoặc 23367;
  - element của skill khớp Turn3Element;
  - GetSkillLv(22011) > 0.
- 23011 (Ám) tương tự với Equip_Spec 23362 hoặc 23368.
- Nếu sai điều kiện client đổi về 14077.

Server EquipSlot xác nhận SPECIAL có fitType=6, tương ứng Equip_Spec.

## Vá authoritative

BattleFighter snapshot thêm:
- turn3Element;
- specialEquipItemId lấy chính xác equips[6].

Khi nhận action skill 22011/23011, BattleInstance từ chối nếu:
- không phải human player;
- skill chưa nằm trong learned skills;
- 22011 nhưng Turn3Element != 7 hoặc SPECIAL không phải 23361/23367;
- 23011 nhưng Turn3Element != 8 hoặc SPECIAL không phải 23362/23368.

Kiểm tra learned-skill chung của Bước 22 vẫn giữ nguyên.

Điểm quan trọng: không chấp nhận summon item chỉ vì item xuất hiện ở một slot khác; phải là slot SPECIAL như Lua.

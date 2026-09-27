# Bước 22 — Authoritative learned-skill validation in battle

## Phát hiện

C50:1 mang skillId do client gửi. Trước bước này server chỉ kiểm tra người gửi sở hữu fighter, sau đó đưa skillId thẳng vào BattleSkillResolver. Player BattleFighter không chứa skills đã học.

Hệ quả: client sửa packet có thể yêu cầu skill tồn tại trong SkillDef dù nhân vật chưa học.

## Vá

- Khi tạo player fighter, snapshot skill đã học gồm:
  - SkillSystem cache (skill thường/Turn1/Turn2);
  - Turn3State.skills (Light/Dark unlock).
- BattleInstance.setPlayerAction từ chối KIND_SKILL nếu skill không nằm trong fighter.skills.
- Các action hệ thống vẫn được miễn kiểm tra learned list:
  - hand fight;
  - defend;
  - escape;
  - catch.

## Light/Dark summon

Lua UISkillTree.TransferSummonID xác nhận:
- 22011 Light cần special equip 23361 hoặc 23367, đúng Turn3Element và skillLv > 0;
- 23011 Dark cần special equip 23362 hoặc 23368, đúng Turn3Element và skillLv > 0;
- nếu không đạt client chuyển về 14077.

Bước này khóa nền tảng learned-skill authoritative. Điều kiện special-equip riêng của 22011/23011 tiếp tục được audit ở bước sau trước khi áp server rule, để không suy diễn sai slot/equipment semantics.

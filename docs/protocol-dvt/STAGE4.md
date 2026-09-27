# Bước 4 — khóa điều kiện học skill Turn1

Nguồn Lua đối chiếu giữ nguyên commit `59c2e0a81c3d4443aa1aad3584d216096256cf16`.

## Bằng chứng Lua

- `lua/UI/UISkillTree.lua`: tab Turn1 chỉ xuất hiện khi `EAttribute.Turn >= 1`.
- `UISkillTree.AddSkillLv`: bốn skill gốc Turn1 có điều kiện riêng và dùng `GetSkillLv(..., true)`, tức chỉ xét skill đã học trước batch:
  - 10020: một trong 10013, 10012, 10011, 10015, 10014, 10009.
  - 11020: một trong 11015, 11014, 11013, 11006.
  - 12020: một trong 12015, 12014, 12013.
  - 13019: một trong 13014, 13013, 13009.
- `lua/Data/SkillData.lua`: `turnKind` là trường chuyển sinh (0 chưa chuyển sinh, 1 đã chuyển sinh).

## Vá server

- `SkillSystem.upgradeSkills` nhận `playerTurn`.
- Khi học mới skill có `turnKind > 0`, server từ chối nếu `playerTurn < turnKind`.
- Bốn skill gốc Turn1 kiểm tra endpoint của nhánh cơ bản từ state đã học trước batch.
- Validation vẫn chạy trước mutation nên batch lỗi không trừ điểm/không học dở.
- `SkillUpgradeHandler` lấy `turn` từ `PlayerStateManager` và truyền vào `SkillSystem`.

## Test hồi quy

Bổ sung test cho:
1. Chưa chuyển sinh không học được skill chuyển sinh.
2. Đủ turn thì qua điều kiện turn.
3. Skill gốc Turn1 thiếu nhánh cơ bản bị từ chối, không mất điểm.
4. Có một endpoint đã học thì được phép.
5. Không thể nhét endpoint và skill gốc Turn1 vào cùng batch để lách `GetSkillLv(..., true)`.
6. Fixture batch cũ khai báo `turnKind = 0` rõ ràng.

## Chưa vá trong bước này

- `limitLv`: SkillData mô tả là giới hạn cấp nhưng `UISkillTree.AddSkillLv` không kiểm trực tiếp; cần thêm bằng chứng server/protocol trước khi enforce.
- C28:5 / Turn2 ball / Hero: cần model và persistence cho `turn2Balls`, career và hero-slot state; không suy đoán.
- Light/Dark: UI không cho cộng điểm bằng flow hiện tại.
- Chưa chạy Gradle trong môi trường ChatGPT hiện tại; cần build/test trên server thật hoặc Astra/Work khi quota trở lại.

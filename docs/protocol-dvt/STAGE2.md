# Bước tiếp theo: kỹ năng Đa Vũ Trụ

Tiếp tục trên commit Lua `59c2e0a81c3d4443aa1aad3584d216096256cf16`. Không sửa Lua, không sửa kỳ vọng test EVE, chưa commit/push. Bản vá bàn giao gộp cả bước audit trước và bước kỹ năng này.

## Đã thực hiện

- C28:2 trước đây chỉ log TODO; nay đọc Byte follow slot + các cặp UInt16 skillId/Byte targetLevel. Chỉ xử lý khi session IN_GAME và pet thuộc nhân vật hiện tại.
- Với pet thường không cầm vũ khí, kiểm tra danh sách ba kỹ năng của NPC, cấp tối đa, điểm đủ, kỹ năng cấm, prerequisite và toàn bộ batch trước khi cập nhật. Batch không hợp lệ không trừ điểm; gửi lại mức/điểm hiện tại khi có snapshot. Replayed target không trừ điểm lần hai. Phụ thuộc giữa các kỹ năng trong cùng batch được giải không phụ thuộc thứ tự gửi; chu trình chưa có kỹ năng đã học bị từ chối.
- Điểm đầu tiên khác hệ 1..4 tốn gấp đôi learnPoint; các cấp tiếp theo chỉ tốn levelUpPoint. Sửa cùng quy tắc ở kỹ năng người chơi. Prerequisite chỉ xét lúc học lần đầu và chỉ cần một prerequisite hợp lệ đã học.
- Lưu các mức skill1/2/3 và skillPoint qua dirty cache và PetRepository hiện có. Test kiểm chứng saveAndUnload → repository boundary → load lại; đây không phải kiểm thử SQL Server thật.
- Thêm S8:2: humanKind=4, slot UInt16, attribute Byte, sign=1, value Int32, arg Int32. Attribute 201 mang level tuyệt đối và skillId; attribute 37 mang số điểm còn lại. Kiểm tra bytes bằng JDK ByteBuffer độc lập.
- C28:1 bị cắt dở một record nay bị từ chối toàn bộ, tránh nâng một phần do payload lỗi.

## Bằng chứng Lua

- `lua/UI/UISkillTree.lua:1318-1380`: cấm kỹ năng summon/đặc biệt/passive/chuyên vũ.
- `lua/UI/UISkillTree.lua:1471-1520`: prerequisite ANY đã học; giá điểm đầu khác hệ và giá cấp sau.
- `lua/UI/UISkillTree.lua:1734-1770`: SendNpcSkill ghi slot và target-level records.
- `lua/Logic/Fight/Skill.lua:703`: danh sách summon.
- `lua/Common/protocal.lua:1317`: S8:2 human, index, attribute, sign, value, arg.
- `lua/Logic/Role.lua:1742` và `lua/Controller/RoleController.lua:5341`: định tuyến FollowNpc của chủ sở hữu và cập nhật level theo ID kỹ năng.
- `lua/Logic/ExclusiveWeapon.lua:463`: kỹ năng chuyên vũ phụ thuộc vũ khí trang bị và bảng dữ liệu chưa được server nạp.

## Giới hạn giữ rõ

C28:2 vẫn PARTIAL: pet có soul hoặc có vũ khí ở equip slot 3 bị từ chối, kể cả vũ khí thường, cho tới khi có dữ liệu phân biệt và xử lý kỹ năng chuyên vũ/soul đúng. Không phát thông báo thành công giả cho các trường hợp này. Kỹ năng player vẫn còn gap điều kiện cây kỹ năng/chuyển sinh và thứ tự prerequisite trong batch; chưa tuyên bố MATCH toàn bộ C28:1. S8:2 chỉ bổ sung nhánh thuộc tính pet, chưa audit mọi human/attribute.

EVE: Lua Eve_NpcEventData đọc dữ liệu điều kiện, chưa tìm thấy evaluator chứng minh đầy đủ bảng toán tử và ý nghĩa class12/16. Bảng so sánh của DungeonCriteriaData thuộc subsystem khác, chưa đủ căn cứ áp thẳng cho EVE. Giữ nguyên 8 lỗi baseline và ghi nhận cần đối chiếu thêm. S13:15 vẫn không có callback Lua; chưa có bằng chứng opcode thay thế.

## Kiểm thử thực tế

JDK 21.0.5, Gradle wrapper 8.13. Toàn bộ `test build --continue`: **151 test, 143 pass, 8 fail** (cùng 8 test EveConditionEvaluatorTest baseline). **38/38 test DVT pass**, trong đó **19 test thêm ở bước này**. Riêng `:app:build` cùng nhóm test DVT: **BUILD SUCCESSFUL**. Python inventory: **3/3 pass**. Không chạy client thật hoặc SQL Server integration.

Inventory hiện tại: **1.902 dòng = 11 MATCH / 334 PARTIAL / 1.556 MISSING / 1 MISMATCH**. C28:2 và S8:2 chuyển MISSING → PARTIAL; số MISSING là cặp protocol, không phải số subsystem cần viết. Nguồn Lua vẫn 792 file và unresolved debug call giữ nguyên.

## File thay đổi thêm trong bước này

| File | Nội dung |
|---|---|
| `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/PetSkillSystem.kt` (mới) | Validation, cost, prerequisite, batch mutation vào cache pet |
| `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/SkillSystem.kt` | Sửa chi phí/prerequisite theo Lua |
| `app/src/main/kotlin/tw/tsm/app/handlers/SkillUpgradeHandler.kt` | C28:2 hoạt động thật, state/length guard, resync pet |
| `app/src/main/kotlin/tw/tsm/app/response/BattleResponseSender.kt` | Serializer S8:2 |
| `domain/src/main/kotlin/tw/tsm/domain/interfaces/ResponseSender.kt` | Hợp đồng gửi thuộc tính pet |
| `app/src/main/kotlin/tw/tsm/app/ResponseSenderImpl.kt` | Delegate serializer thật |
| `app/src/main/kotlin/tw/tsm/app/ServerModule.kt` | Đăng ký PetSkillSystem và dependency handler |
| `tests/src/test/kotlin/tw/tsm/tests/DvtPetSkillTest.kt` (mới) | 10 test luật, batch, ownership, persistence boundary |
| `tests/src/test/kotlin/tw/tsm/tests/DvtSkillWireTest.kt` (mới) | 5 test parser, malformed/state guard, bytes S8:2 |
| `tests/src/test/kotlin/tw/tsm/tests/DvtSkillRulesTest.kt` (mới) | 4 test giá điểm/prerequisite người chơi |
| `tools/protocol_reviews.json`, `tools/test_protocol_inventory.py` | Cập nhật review và kiểm thử phân loại |
| `docs/protocol-dvt/README.md`, `inventory.csv`, `inventory.json`, `STAGE2.md` (mới) | Kết quả và tài liệu hiện tại |

Danh sách đầy đủ cả hai bước trong `delivery-manifest.json`; `changed-files.zip` chứa tất cả file thay đổi, `changes.patch` áp lên commit nguồn nêu trên. Kết quả XML full suite và focused được giữ riêng trong `test-results.zip`. Log `dvt-stage2-full-build.log` ghi thất bại full suite; `dvt-stage2-focused.log` ghi build ứng dụng và 38 test DVT thành công.

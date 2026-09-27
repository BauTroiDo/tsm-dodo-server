# Bước 3 — vá tiếp quanh Lua Đa Vũ Trụ

Nguồn Lua giữ nguyên commit `59c2e0a81c3d4443aa1aad3584d216096256cf16`. Chưa commit/push. Bản vá bàn giao gộp cả ba bước.

## Các lỗi đã sửa

1. **C28:1 phụ thuộc thứ tự packet:** Lua `UISkillTree.lua:1724-1729` gửi bằng `pairs`, còn server xử lý tuần tự. Nay giải prerequisite trong cả batch; thứ tự đảo vẫn học đúng. Chu trình chưa có điểm khởi đầu bị từ chối.
2. **Trừ điểm một phần khi batch lỗi:** kiểm tra toàn bộ trước mutation; không trừ điểm nếu có skill không tồn tại, vượt cấp, trùng ID, thiếu điểm hoặc prerequisite. Target đã đạt không bị tính tiền lại. Đây là quy tắc giao dịch phía server để tránh trạng thái học dở, không phải khẳng định Lua quy định cách server xử lý packet sai.
3. **Mua kỹ năng bị Lua cấm:** chặn 14026/14077 và sáu summon ID theo `UISkillTree.AddSkillLv` và `Skill.IsSummonSkill`; chặn hệ đối nghịch 1↔3, 2↔4 theo `UISkillTree.CheckElement:1274`. Chi phí dùng Long khi tính, từ chối dữ liệu giá âm để không cộng ngược điểm do overflow.
4. **Rút pet về bị client đặt dữ liệu mặc định:** S15:1 gọi `Role.FollowNpcAppear` không kèm data, đặt cấp 1/điểm 0/kỹ năng mặc định (`Role.lua:975-1080`). Nay trả S15:8 với nguyên dữ liệu pet đã lưu, gồm cấp, điểm, kỹ năng, trang bị. Không sửa Lua.
5. **Đổi pet cùng slot còn trạng thái cũ:** gửi S15:2 xóa role cũ rồi S15:8 đúng pet thay thế; không gửi lại mọi pet không liên quan. Cart record được cập nhật bằng S15:10.
6. **Xe ngựa luôn có 4 ô:** theo `Cart.GetCartSize` (`lua/Logic/Cart.lua:114-163`), kiểm tra 0 ô trước chuyển sinh, 2 ô lần một, 4 ô từ lần hai; giới hạn cả chọn ô gửi vào và ô rút/đổi.
7. **Payload pet không đủ/thừa byte:** C15:2/10/11 cần đúng 1 byte, C15:12 đúng 2 byte; slot trong 1..4. C19:1 đúng 2 byte NPC ID, C19:2 rỗng. Chỉ cho xử lý trong IN_GAME.

## Kiểm thử

- Thêm **13 test**: 7 trong DvtSkillBatchTest, 6 trong DvtCartTransitionTest. Test chuyển pet dùng PetSystem thật và repository giả lập tại biên lưu dữ liệu.
- **51/51 test DVT pass**; `:app:build` thành công. Log: `dvt-stage3-focused.log`.
- Full `test build --continue`: **164 test, 156 pass, 8 fail**; so sánh chính xác tên lỗi với bước 2 xác nhận cùng 8 lỗi EVE baseline. Log: `dvt-stage3-full-build.log`.
- Python inventory **3/3 pass**; diff check và kiểm tra Lua không đổi đều pass. Patch áp thử lên snapshot commit nguồn và so nội dung file sau áp.

## File sửa ở bước này

- `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/SkillSystem.kt`: batch, prerequisite, eligibility cơ bản, giá điểm.
- `gamelogic/src/main/kotlin/tw/tsm/gamelogic/systems/PetSystem.kt`: giới hạn chọn slot xe ngựa theo capacity truyền vào.
- `app/src/main/kotlin/tw/tsm/app/handlers/FollowNpcHandler.kt`: full pet data, swap, capacity và packet/state guard.
- `app/src/main/kotlin/tw/tsm/app/handlers/FollowNpcDeployHandler.kt`: packet/state guard.
- `app/src/main/kotlin/tw/tsm/app/ServerModule.kt`: inject PlayerStateManager vào FollowNpcHandler.
- Mới: `tests/src/test/kotlin/tw/tsm/tests/DvtSkillBatchTest.kt`, `DvtCartTransitionTest.kt`.
- `tools/protocol_reviews.json`, `docs/protocol-dvt/inventory.csv`, `inventory.json`, `README.md` và `STAGE3.md` mới.

## Chưa hoàn tất

Inventory giữ **11 MATCH / 334 PARTIAL / 1556 MISSING / 1 MISMATCH**; các sửa trên hoàn thiện route đã tồn tại nên không làm tăng giả số MATCH. Kỹ năng nâng cao còn cần điều kiện cây/chuyển sinh; pet soul/chuyên vũ vẫn bị chặn ở luồng nâng skill; broadcast pet sang người chơi khác và hạn chế khi đang chiến đấu cần audit tiếp. Còn S13:15 không có callback Lua và 8 lỗi EVE chưa đủ bằng chứng để sửa semantics. Chưa chạy client thật hoặc SQL Server integration.

`delivery-manifest.json` liệt kê toàn bộ file trong patch gộp; `changed-files.zip` chứa nội dung, `test-results.zip` chứa XML full/focused của bước này.

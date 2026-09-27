# Bước 5 — C28:5 / Turn2 / Hero

Nguồn Lua đối chiếu: `lua/UI/UISkillTree.lua`, `lua/Common/protocal.lua`.

## Đã vá

- C28:5 được parse đúng 3 đoạn UInt32-count: thiên châu, skill target, hero-slot mapping.
- C28:1 không còn có thể dùng để bypass các skill Turn2/Hero.
- Turn2 chỉ nhận khi `turn >= 2` và `career in 1..4`.
- Career base skill được khóa đúng theo 4 bảng Lua.
- Thiên châu 1..12 kiểm tra skill đường dẫn tương ứng, số thiên châu khả dụng và chống trùng.
- Hero slot 6..13 chỉ nhận các Hero skill ID trong bốn mảng Lua, một slot/một skill chỉ gán một lần.
- Batch C28:5 validate trước khi commit state phụ; packet sai được resync authoritative.
- S23:77 và S23:78 được gửi khi login và sau C28:5.
- Thêm persistence `character_turn2_skills`; SchemaUtils tự tạo bảng/cột thiếu.
- S3:0 không còn hard-code turn/career = 0; turn/career lấy từ CharacterRecord.
- Thêm cột `characters.career` mặc định 0.
- Thêm regression tests `DvtTurn2SkillTest`.

## Wire evidence

Lua C28:5:
1. UInt32 ballCount + ballIndex(Byte)*
2. UInt32 skillCount + skillId(UInt16 LE)+level(Byte)*
3. UInt32 slotCount + slotIndex(Byte)+skillId(UInt16 LE)*

Lua server sync:
- S23:77: availableBallCount(Byte) + activeBallIndex(Byte)*
- S23:78: wireIndex(Byte)+skillId(UInt16 LE)*; client chuyển thành UI slot bằng `index + 5`.

## Chưa tự suy đoán

Nguồn cấp `turn2BallCount` và flow chuyển nghề/career chưa được server cũ tái dựng đầy đủ. Bước này chỉ lưu/tiêu thụ/sync số thiên châu đã có; không tự cấp miễn phí.

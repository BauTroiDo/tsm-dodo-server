# Bước 8 — Career change item

Đối chiếu `lua/Logic/Item.lua` và `lua/UI/UITurn.lua`.

## C23:76

Payload: `bagIndex(UInt16 LE) + career(Byte)`.

Server nay chỉ đổi nghề khi đồng thời:
- nhân vật đang IN_GAME;
- packet đúng 3 byte;
- career đích 1..4 và khác nghề hiện tại;
- Turn >= 2;
- bagIndex tồn tại, quantity > 0;
- Item_C.dat của vật phẩm có `specialAbility = 208`;
- mission 11495 đã hoàn thành;
- mission nghề đích đã hoàn thành:
  - 1 XiaKe: 11487
  - 2 BaWang: 11463
  - 3 XianShi: 11491
  - 4 DiXian: 11489.

`QuestSystem.hasMissionFlag()` tra `missionId -> MarkDef.bitId` rồi kiểm tra flag DB, tương đương `MarkManager.GetMissionFlag(missionId)`.

Tất cả validation chạy trước khi tiêu vật phẩm. Thành công mới trừ 1 token, persist career, cập nhật runtime và gửi S23:69.

## Turn2 Ball

Chưa thêm công thức cấp Ball. Lua hiện xác nhận state/sử dụng nhưng chưa cho thấy nguồn authoritative cấp `turn2BallCount`; tiếp tục giữ nguyên nguyên tắc không đoán.

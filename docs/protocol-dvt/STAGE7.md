# Bước 7 — Rebirth synchronization and stat correctness

Nguồn Lua đối chiếu: `lua/Common/protocal.lua`, `lua/UI/UITurn.lua`, `lua/UI/UISkillTree.lua`.

## S23:69 được xác nhận

Lua nhận:

`S:023-069 +playerId(Int64) +hair(Byte) +color1(Int32) +color2(Int32) +turn(Byte) +career(Byte)`

Sau đó client cập nhật `headStyle`, hai bảng màu, `turn`, `career`, gọi `UpdateAll()`, cập nhật cart và UI chính.

Server nay dùng packet chuyên dụng này sau C23:46/54/81 thay vì lạm dụng S3:0.

## Stat sau chuyển sinh

`PlayerDataDto.fromCharacterRecord` trước đây hard-code `turn=0, career=0` khi tính MaxHP/MaxSP. Nay truyền `record.turn` và `record.career`.

Đồng thời DTO nay lấy `record.skillPoint` thay vì để mặc định 0.

## Turn2 Ball

Lua chỉ cho thấy S23:77 là state authoritative: một byte số Ball khả dụng + danh sách index Ball đã kích hoạt. C28:5 tiêu Ball khi học.

Chưa tìm thấy bằng chứng trong Lua rằng client tự tính/cấp số Ball. Vì vậy server không tự bịa công thức cấp Ball ở bước này.

## C23:76

C23:76 phía client là yêu cầu dùng vật phẩm đổi nghề (bagIndex UInt16 + career Byte), nhưng S23:76 là packet khác: kết quả mua virtual item. Việc trùng subkind hai chiều là hợp lệ.

UI đổi nghề kiểm tra mission flags:
- XiaKe: 11487
- BaWang: 11463
- XianShi: 11491
- DiXian: 11489

Chưa mở server-side C23:76 cho tới khi xác nhận được inventory item semantics và mission flag persistence/validation, để tránh đổi nghề miễn phí hoặc bỏ qua quest.

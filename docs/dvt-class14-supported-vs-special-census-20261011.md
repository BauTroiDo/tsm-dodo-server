# Rà soát chính xác 279 điều kiện thời gian DVT `class14` — 2026-10-11

## Bằng chứng từ DVT gốc

Theo client `lua/Data/Eve/Eve_NpcEventData.lua`, sau
`condition.ConditionValue = reader:ReadInt32()`, client đọc
`condition.CTimeValue = reader:ReadDouble()` — **8 byte double**.
Công cụ cũ `tools/eve_tool.py` từng bỏ qua 8 byte này, vì vậy
`tools/audit_dvt_quest_blockers_20261011.py` đánh dấu sai tất cả
279 `conditionClass=14` là chưa được hỗ trợ.

Đã đối chiếu Eve gốc SHA-256
`03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`:

| Phân loại | Số điều kiện | Dữ liệu & trạng thái |
| --- | ---: | --- |
| So sánh giờ ngày OLE Automation | **274** | param0, pStyle0, ops 1–4, val0, subItem0, CTimeValue hữu hạn dương; đã có evaluator Kotlin so sánh với `eveNowOaDate` |
| Điều kiện xã hội đặc biệt | **5** | param2, pStyle1–4, ops0, CTimeValue = `37931.6033664236` — chưa có ngữ nghĩa thực thi tin cậy |

Năm điều kiện đặc biệt **đều ở map12001, Eve64,
NPC22063**, số thứ tự 3,5,7,9,13. Lua
`Logic/Social.lua` dùng `Social.PlayDeliver(22063, 1)` và
`PlayDeliver(22063, 2)` trong `Social.SetFunctionFlag`,
liên quan đến quà tặng/thông báo của hệ xã hội. Vì vậy không được
đánh đồng năm điều kiện với giới hạn thời gian thông thường, cũng
không mở giả toàn bộ bằng `true`.

**Lưu ý:** 274 trường hợp chỉ được xác nhận có **shape được server hỗ trợ**,
không bảo đảm mọi sự kiện hiện vẫn nằm trong khung giờ cho phép.
Các mốc OLE trong dữ liệu game có thể đã qua, nhưng không được tự
dịch chuyển ngày để bỏ qua giới hạn sự kiện.

## Những thay đổi

- `tools/eve_tool.py`: đọc `CTimeValue` bằng
  `struct.unpack("<d", read_bytes(8))` và giữ `sub_item` trong mỗi
  bản ghi điều kiện; không sửa bytes Eve hay thay giao thức.
- `tools/audit_dvt_quest_blockers_20261011.py`: vẫn đếm
  `cond_14 = 279`, nhưng thêm
  `cond_14_supported_ole_time = 274` và
  `cond_14_unverified_special_time = 5`. Chỉ đưa năm
  điều kiện chưa rõ vào bảng blocker. Không thay đổi cách lọc
  `class13` và `class17`.
- `tools/test_dvt_quest_blockers_20261011.py`: kiểm thử nguồn
  dữ liệu, chính xác 5 bản ghi chưa rõ, `CTimeValue` của Eve64
  và 274 điều kiện đã có evaluator.

Do loại bỏ cảnh báo giả của `class14`, số **scene có ít nhất
một blocker trong kiểm toán** giảm từ **85 xuống 47**. Danh sách
**MarkStep mission IDs *có liên hệ theo cùng sự kiện*** giảm từ
**82 xuống 2** (10852 và 12164). Đây là **số liệu phân loại
blocker**, không phải kết luận chỉ còn hai nhiệm vụ hỏng hoặc
274 sự kiện đã PASS E2E. Những lỗi bên ngoài danh mục vẫn có thể
tồn tại.

## Triển khai

Đây là cập nhật **công cụ kiểm toán/kiểm thử**, không phải thay đổi
logic gameplay. Server dev đang có evaluator thời gian OLE,
và 5 điều kiện đặc biệt đang fail-closed. Không cần restart,
không sửa item/mission/DB. Khi làm tiếp, ưu tiên blocker thực
sự của class13, class11, các bước thiếu waypoint và kiểm thử
NPC bằng client; giữ nguyên ưu tiên hoàn thành nhiệm vụ trước
Gia Viên, Định hôn và con cái.

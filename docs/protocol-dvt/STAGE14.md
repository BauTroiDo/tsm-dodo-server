# Bước 14 — UISell 57/58 cho Tam Chuyển

Nguồn client:
- `UISkillTree.UnLockSkill` gọi `UISell.Launch(57, ..., skillId)`.
- `OnClickChangeBtn` đảo Light/Dark rồi gọi `UISell.Launch(58, ..., targetElement)`.
- `UISell.Launch` encode sellId UInt16 rồi từng arg UInt16.
- query/use dùng mainKind 84 và payment quote từ UISell formula server.

## sellId 57

Server báo giá từ `UISell_Formula_C.dat` và trước khi trừ tiền kiểm tra:
- Turn >= 3;
- đúng một skillId;
- skill thuộc whitelist Light 220xx hoặc Dark 230xx;
- skill element phải trùng persisted Turn3Element;
- skill chưa unlock.

Sau payment, lưu skill level 1 vào Turn3State và S84:2 success.

## sellId 58

Server báo giá từ formula data, validate Turn >= 3, target 7/8, đã chọn hệ trước đó và target khác current. Sau payment:
- đổi Turn3Element;
- loại các Turn3 skill thuộc hệ cũ;
- S84:2 success.

Việc loại skill hệ cũ là invariant server để tránh persisted skill trái hệ mà UI sẽ khóa. Nếu dữ liệu gốc sau này chứng minh đổi hệ giữ skill cũ, quy tắc này cần điều chỉnh.

## Chưa suy đoán

Formula kind 57/58 chưa được gán semantic mới; chi phí lấy trực tiếp `formula.value` và currency KNB như query Money hiện tại. Không hard-code giá.

# Đồng bộ bản vá đang chạy: vật phẩm nhiệm vụ `resultClass1/pStyle3` — 2026-10-11

## Nguyên nhân quan trọng

Khi triển khai phần `class11`, phát hiện trực tiếp bản
`C:\\Server-Game\\tsm_dodo\\gamelogic\\...\\EveResultExecutor.kt`
có thêm bản vá vật phẩm nhiệm vụ `pStyle=3` **chưa được push lên nhánh
PR #41**. Nếu triển khai nguyên tệp theo GitHub thì các sửa đổi về nộp
vật phẩm này bị mất.

Đã tải tệp live xuống và so sánh từng dòng với GitHub. Khác biệt chính
xác gồm: `isClearAllQuestItem`, `isBoundedQuestItem`, preflight dùng
giỏ hàng ảo theo thứ tự kết quả, `handleItemResult` thu hồi nhiều
stack bằng `inventorySystem.removeBagItemsExact` và gửi giảm item
đúng slot. Không có thay đổi ngoài nhóm pStyle3.

## Bằng chứng Eve DVT

Archive Eve đang chạy chứa đúng **55** kết quả
`resultType0/class1/pStyle3`, phân bố:

- **44** kết quả `resultValue=0`: xóa tất cả stack của **đúng itemId**
  sự kiện trong túi.
- **11** kết quả `resultValue>0`: giới hạn số lượng cần nộp/thu hồi.
- Các map tiêu biểu: `54944` có 24 kết quả, `49984` có 8.
- Dữ liệu **không** có pStyle3 với số lượng âm hoặc `parameter=0`.
- Mã khác không được xóa và vật phẩm ở kho khác không được xem là trong
  túi sự kiện.

## Đồng bộ chính xác

Bản DEV build thành công khi giữ nguyên logic này và chèn thông báo
`class11`, hash SHA-256 của tệp JVM source live:
`ce91eb0d63413ebd475807f5ceb6b00049c3640f8761743e8c03a2500b759d3a`.

Đã đưa lại chính xác các byte **đang chạy** của
`EveResultExecutor.kt` về repository GitHub PR #41. CI có
`test_dvt_pstyle3_live_quest_parity_20261011.py`: khóa 55 mẫu Eve và
sự tồn tại của hai nhánh xử lý đa stack. Việc đồng bộ này **không làm
thay đổi runtime đang chạy**; đây là chống tái phát lỗi khi checkout
GitHub để deploy trong tương lai.

Lưu ý: preflight và thay đổi vật phẩm trong event có nhiều hiệu ứng
cần thử client E2E thêm để bảo đảm tính nguyên tử của cả chuỗi. Không
tuyên bố toàn bộ 55 event đã PASS chỉ từ kiểm tra code.

## Trạng thái nhiệm vụ và tiếp tục

- **35 class11** đã giải được chính xác Gold/Grain/Mineral/Wood/Cloth
  của quân đoàn, nhưng vẫn chặn cấp do chưa có tổ chức/treasury thực.
- **89 class13** còn thiếu quy tắc ánh xạ phần thưởng.
- **279 conditionClass14** và 8 bước thiếu waypoint vẫn cần xử lý.
- Tiến độ bất thường cũ 10120/3, 10164/3, 12280/6 được phục hồi
  về bước hợp lệ với backup; vẫn cần xác nhận trực tiếp bằng client.

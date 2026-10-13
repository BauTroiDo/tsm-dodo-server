# DVT 12162 — Phát hiện nguồn Nồi Đất thực và bảo vệ giao dịch mở túi (2026-10-12)

## Bằng chứng mới

Đã đọc trực tiếp `File.dat/Data-ios/IOS_BlissBag_C.dat` (37.257 dòng × 13 byte, header 4 byte), đối chiếu thứ tự `BlissBagData.New` trong Lua DVT và `BlissBagDatLoader` trong Kotlin. Vật phẩm cần nhận là **32043 Nồi Đất**, KHÁC `32015 Nồi Đất Nhỏ` (không được đánh đồng theo tên giống nhau).

| Túi DVT | Tên trong Item_C.dat | Nhóm chứa item32043 | Trọng số trong nhóm | Cơ hội khi mở một túi |
|---|---|---|---|---|
| 46285 | Túi dùng Hương dũng | kind3 | 2.500/10.000 | **25% để nhận 1 Nồi Đất** |
| 32919 | Túi quà đền bù 1 | kind1 | 10.000/10.000 | 100% để nhận 1 |
| 32920 | Túi quà đền bù 2 | kind1 | 10.000/10.000 | 100% để nhận 1 |

Túi `46285` còn trao `26460×25` từ nhóm 1, `26461×25` từ nhóm 2 và nhóm 3 quay ngẫu nhiên 1 trong `49501`, `32043`, `49001`, `50001` với trọng số mỗi món 2500. **25% không có nghĩa rằng mở 4 túi chắc chắn ra 1 Nồi Đất.**

Quét 3.823 scene Eve DVT thấy nhiều kết quả `resultClass1/param46285/value>0` từ các nhiệm vụ thực; ví dụ `scene11301/Eve3/condition2` có điều kiện nhiệm vụ **10120 bước 2**, NPC **14216 placement8 (470,380)**, thưởng **1 túi 46285** rồi hoàn thành nhiệm vụ. Các scene khác như 12820/Eve6 của chuỗi 10143 cũng có thưởng túi này. Không tìm thấy `32919/32920` trong Eve thưởng trực tiếp hay bảng rơi hiện tại, nên KHÔNG phát túi đền bù tùy tiện.

Nguồn đối chứng độc lập: hướng dẫn PlayPark TS Online Mobile tiếng Thái (30/05/2019) mô tả nhiệm vụ vượt cầu dùng **15 Nồi Đất**, lấy từ gói **hương dũng/lính thôn** hoặc rơi từ quái luyện cấp thông thường (https://www.online-station.net/mobile-game/247437). DVT `scene63000/NPC27012` cũng thu 15 item32043, hỗ trợ nhận diện đúng vật phẩm. Thông tin năm 2019 **không xác minh NPC nào và tỷ lệ rơi** trong DVT 1.15; `gamedata/npc_drops.json` hiện không chứa item32043.

## Lỗi Kotlin vừa được phát hiện

`ItemHandler.handleUseItem()` cũ trừ item nguồn bằng `removeFromBag()`, gửi thành công, rồi lần lượt gọi `addItemsToBag()`. Nếu túi đầy, `addItemsToBag` trả về danh sách rỗng hoặc thêm một phần, nhưng vật phẩm mở đã bị trừ: **mất thưởng không thể phục hồi**, kể cả Nồi Đất cần cho quest12162.

## Cách sửa không phát thưởng giả

- `GiftBagAtomicPlanner`: mô phỏng toàn bộ các nhóm thưởng đã được `ItemUseProcessor` quay theo đúng dữ liệu, tiêu hao source stack trong *bản sao* trước, gộp stack và phân bố các ô; nếu thiếu một ô hoặc itemDef, trả null **không đụng hành trang**.
- `InventorySystem.openGiftBagAtomic()`: lưu toàn bộ thay đổi slot trong **một giao dịch** qua `inventoryRepo.saveItemsAtomic` trước khi cập nhật cache.
- `ItemHandler` chỉ gửi S-response "đã dùng" sau khi giao dịch hoàn tất. Nếu thiếu ô hoặc transaction lỗi, nguồn túi còn nguyên và người chơi không nhận thông báo thành công giả.
- Không thay đổi RNG, bảng xác suất, shop, quái rơi, Eve, mission flags hoặc giá trị item. Không tự cấp 30 Nồi Đất.
- Regression `DvtClayPotGiftBagAtomicTest`: bảng gốc 3 túi, 25/100% chance, 32015 ≠ 32043, hành trang đầy, túi còn stack, ba nhóm thưởng, stack Nồi Đất 29→30, invalid rewards.

## Giới hạn còn lại

Nguồn túi 46285 là nguồn **có thể lấy được từ một số nhiệm vụ**, nhưng không bảo đảm tự farm vô hạn để có đủ 30 Nồi Đất. Hệ quái rơi **vẫn thiếu item32043** vì chưa có bảng tỷ lệ DVT gốc để xác nhận NPC/rate. Sau khi kiểm thử CI, cần kiểm tra mở nhiều Túi Hương Dũng trong game bằng nhân vật thử và kiểm tra việc nhận/không nhận Nồi Đất khi hết ô. Nhánh `resultClass13/value40` của Ngọc Thố vẫn giữ fail-closed.

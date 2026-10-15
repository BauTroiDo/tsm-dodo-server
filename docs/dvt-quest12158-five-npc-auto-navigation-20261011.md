# Khôi phục năm waypoint NPC nhiệm vụ 12158 — 2026-10-11

## Lỗi thực tế

Nhiệm vụ **12158 — thông đạo Lăng Tần tìm năm dụng cụ** có
năm bước liên tục với `IOS_MarkStep_C.dat` gốc chứa
`conditions[0].target=(0,0,0,0,0)` và `end=(0,0,0,0,0)`.
Bấm vào chỉ dẫn không có điểm đích. Không được tự chọn một
map chung vì năm vật phẩm nằm ở năm scene khác nhau.

Đối chiếu Eve gốc `gamedata/Eve.emg` SHA
`03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`,
mỗi NPC thực tế có nhánh `resultClass1/pStyle1/value1`
cấp **chính vật phẩm được yêu cầu** và đi kèm
`resultClass2` tăng bước 12158:

| Bước | Vật phẩm | Scene | NPC ID | Chỉ số NPC (placement) | Vị trí X,Y |
|---|---:|---:|---:|---:|---|
| 1 | 29372 | 14673 | 40113 | 2 | 1390, 180 |
| 2 | 29373 | 14671 | 40114 | 2 | 1390, 200 |
| 3 | 29374 | 14674 | 40115 | 1 | 1390, 200 |
| 4 | 29375 | 14680 | 40116 | 2 | 1390, 200 |
| 5 | 29376 | 14678 | 40117 | 1 | 1390, 180 |

Cấu trúc nhị phân xác nhận từ `lua/Data/MarkData.lua`
`MarkData.ReadStep`: mỗi waypoint 8 byte
`<HHHBB>` = scene, x, y, eventKind, eventId.
Dùng `eventKind=1` và chỉ số NPC placement thật. Có
**344** điều kiện thu thập vật phẩm khác đã sử dụng đúng
mẫu NPC này; không chọn `eventKind=2` (cửa).

## Bản vá giới hạn

`tools/patch_dvt_12158_npc_navigation_20261011.py` được thiết kế
cho **hai phiên bản byte khác nhau**:
- Repository/server MarkStep: **892.087 byte**, SHA ban đầu
  `cd5d4dfe175ae12fb71a4ad6c52f17d447335944d6efdfb269652534dbf3815f`,
  sau sửa SHA `642a8e85c5ceabc9f4a59d97cefa96ed5c8839ff843322d234ef9768a24c2689`.
- Client staging `D:\Games\Ts-Offline\TSNPCVN_9PET_STAGING\AssetBundles\Data\IOS_MarkStep_C.dat`:
  **928.425 byte**, SHA ban đầu
  `d239860783645d1e966346f5685a39442207b08ebdd09794b453219c91d89218`,
  sau sửa SHA `e62963dee3af68824741979372e18e0a2d4884ef2a439787d1beb56218cbb7a3`.

Mỗi bản chỉ có **35 byte thực tế đổi giá trị** nằm trong
**năm vùng 8 byte**, không sửa text, điều kiện số lượng,
tên/item ID, vị trí END, nhiệm vụ, Eve hay DB.
Trước khi ghi, script đối chiếu đúng nguồn, 5 NPC/scene/vị trí
và kết quả phát item; từ chối một phiên bản file chưa biết,
sao lưu byte-for-byte và ghi atomically, gọi lại không nhân đôi.

Đã đưa file repository sau sửa vào GitHub PR #41; khi áp dụng
client staging và server dev phải chạy patch trên **từng file
riêng**, không copy file 892KB từ server lên file client 928KB.

## Kiểm thử

`tools/test_dvt_12158_npc_navigation_20261011.py` đối chiếu
cả năm MarkStep với Eve, đảm bảo tính idempotent và
không tham chiếu tới NPC/door/scene không tồn tại.
Bộ kiểm toán toàn bộ sau sửa có **5.721 vị trí có tọa độ**
(tăng 5), **91** mục tiêu điều kiện chưa có vị trí (giảm 5),
và chỉ còn **3 bước** không có bất kỳ đích có tọa độ nào:
`10852/1` (Ngọc Thố rải nhiều điểm),
`12162/1` (thu thập 30 nồi đất, chưa xác định nguồn mua),
`12509/8` (mua nhiều loại thực phẩm ở nhiều thành).

**Cần kiểm thử người dùng thực tế:** bấm nhiệm vụ 12158
từng bước ở client và xác nhận pathfinding, nhận vật phẩm,
tăng bước đúng NPC. Source+CI chỉ xác nhận đích có cơ sở,
không thể chứng minh đường đi runtime hoàn toàn không bị chặn.

Không thay đổi Gia Viên, Định hôn, nhận con. Ưu tiên hoàn thành nhiệm vụ.

# Nhiệm vụ DVT 10852/1 — Khôi phục điều hướng vào Ngọc Thố (2026-10-12)

## Đối chiếu DVT gốc

Tuyến nhiệm vụ **10852, bước 1** ghi: “Đến Quảng hàn động bắt thỏ ngọc cho Thôn Trưởng thôn Phùng Lai”, chỉ dẫn “Thông qua Ngô Cương”. Điều kiện MarkStep là `kind=1, NPC38022, quantity=1`, nhưng tọa độ điều kiện và END đều bằng 0.

Dữ liệu `gamedata/Eve.emg` thực xác nhận **18 con thỏ `NPC38022`** có điều kiện `conditionClass2/mission10852/step0` và nhánh `resultType3` khởi tạo chiến đấu, nằm ở bốn map `10982`, `10983`, `10984`, `10985`. Có những NPC cũng dùng template `38022` ở event khác nhưng **không** thuộc nhiệm vụ này, không được chọn nhầm.

**Cửa vào xác minh được:**

- Map `11021`, NPC `15153`, placement `7`, Eve11 có điều kiện đang làm `10852/step1` mở menu (`resultType6`).
- Trong cùng Eve11, nhánh lựa chọn `conditionClass10/param7/pStyle30` gửi `resultType2/class7/parameter5`.
- `sceneInfo5` gốc của map11021 chuyển tới **map10984**, điểm xuất hiện **(3780,2820)**.
- Map10984 có đúng bốn con `NPC38022` thỏa điều kiện chiến đấu của nhiệm vụ. Gần điểm xuất hiện nhất là **placement3**, tọa độ **(3710,2300)**, cách điểm xuất hiện khoảng 525 đơn vị; ba con còn lại cách trên 2.000 đơn vị. Không cần tự chế cửa, teleport hay điều kiện nhiệm vụ.

## Thay đổi chính xác

`tools/patch_dvt_10852_rabbit_cave_waypoint_20261012.py` xác minh toàn bộ nguồn NPC, hai nhánh menu+warp, số lượng thỏ, khoảng cách, MarkStep đúng điều kiện, và chỉ sửa **một field waypoint 8 byte `<HHHBB>`**: `(10984,3710,2300,1,3)`. END giữ nguyên 0; các map/người bán/NPC thỏ còn lại không thay đổi.

Hai edition phải được vá **riêng**, không được copy trọn file:
- Repository / server MarkStep 892087 byte, SHA cũ `8f37189491c21ab6f59973ae9a86dbdb279b4276485ef420a85b0bcd0b7bc115`, mới `6e01ccb0fc06944911bfe5ee159bc6e261fbd794af081cfe5fb868d489388958`.
- Client staging MarkStep 928425 byte, SHA cũ `da1eefd8914cfd13fe251c8397add11102dfee305fb4ed92a854b13faabd51fc`, mới `25f279da092c06e8159fb22790b21c5b80106258a633074de242bdfc1c753cd0`.

Tool chỉ chấp nhận đúng SHA ban đầu, tạo bản sao lưu byte-for-byte, ghi atomically và chạy lặp không sửa lại.

## Kiểm thử và giới hạn

- `tools/test_dvt_10852_rabbit_cave_navigation_20261012.py` chốt menu Ngô Cương, 18/4 bản gốc, con thỏ gần cửa nhất, đích MarkStep và tính idempotent.
- Bộ kiểm thử rộng kiểm tra đủ 4.505 bản ghi không có đích trỏ tới NPC/scene/door không tồn tại. `located 5724→5725`, `unlocated_condition_targets 88→87`.
- **Còn đúng 1 bước** không có tọa độ điều kiện/END: `12162/1`, yêu cầu 30 nồi đất item32043.
- Dữ liệu `gamedata/npc_drops.json` **không chứa item32043**. Quét toàn bộ 3.823 scene Eve chưa thấy `Goods`, `Surface` mua bán hoặc hiệu ứng cấp trực tiếp nào có item32043. `12808/NPC15050` **thu 30**, `63000/NPC27012` **thu 15**; chưa chứng minh nguồn rơi/đúc hợp lệ, nên không giả NPC bán hay tự phát vật phẩm.
- Nhiệm vụ 10852 có nhánh `resultClass13` chưa xác minh khi trả nhiệm vụ; **sửa dẫn đường không đồng nghĩa trả thưởng và hoàn thành đã PASS**. Cần thử client staging sau khi khởi động lại để xác minh tự chạy qua portal Ngô Cương và tương tác Ngọc Thố.

Vẫn ưu tiên xử lý đúng nhiệm vụ DVT; chưa chuyển sang Gia Viên, Định hôn, nhận con.

# DVT quest 12162/1 — nguồn Nồi Đất còn thiếu, khôi phục đích trả có chứng cứ (2026-10-12)

## Dữ liệu DVT thực

MarkStep 12162/step1: "Tìm 30 cái nồi đất đưa cho Bắc Đẩu tinh quân ở rừng Trác Quận". Điều kiện thứ nhất là `kind=3, itemId=32043, count=30`; cả target của điều kiện và END ban đầu đều là 0. Nếu người chơi tự có đủ 30 nồi, hàm client MarkManager.Navigation sẽ chọn END, nhưng END bằng 0 nên không thể tự tìm đường đến NPC trả.

Eve.active 12808, NPC15050 placement10 tại (310,360), gắn Eve13. Nhánh Eve13/condition6-8 có:
- mission12162 đang ở stage1;
- có hơn 29 item32043, tức đủ 30;
- lựa chọn xác nhận thật của NPC;
- chuỗi result: `class1/item32043/pStyle1/value=-30`, cấp `item26416 x5`, hoàn thành mission12162 (`class2/pStyle3`) và thêm mission12163 (`class2/pStyle1`).

Đây là NPC **thu Nồi Đất**, tuyệt đối **không phải** nguồn phát/mua/rơi Nồi Đất. Không sửa Eve, tiền, inventory, flag hay trạng thái nhân vật.

## Thay đổi dữ liệu đúng một field

Script `tools/patch_dvt_12162_clay_pot_turnin_20261012.py` xác minh thực tế NPC, eventId, tọa độ, mission và đủ bốn Eve result; kiểm tra shape MarkStep, SHA edition, offset động, rồi chỉ ghi 8 byte `END <HHHBB>` thành `(12808,310,360,1,10)`. Điều kiện nhận item vẫn phải là `(0,0,0,0,0)` cho đến khi có bằng chứng về nơi lấy. Ghi dữ liệu chỉ trong `--apply`, sao lưu bản gốc và atomically replace, chạy lại idempotent.

- Repo/server edition: SHA trước `6e01ccb0fc06944911bfe5ee159bc6e261fbd794af081cfe5fb868d489388958` → sau `e0278071b4f12f75d058d51661e743ad0c42c83ab6a08af2bc610ba4f7e02074`.
- Staging client edition: SHA trước `25f279da092c06e8159fb22790b21c5b80106258a633074de242bdfc1c753cd0` → sau `b7af396c575d50b18cb3408c8e476e91fa7a543c573256d3240ce52f69c8770c`.
- Byte window của hai edition: 716446..716453. Không copy nguyên file sang edition khác vì nội dung text khác.

## Điều đã / chưa được giải quyết

- Từ khi đã có 30 Nồi Đất, client có vị trí NPC trả nhiệm vụ thật, thay vì END=0.
- Navigation auditor: `located targets 5725→5726`, `blank END 269→268`, `unlocated individual objective conditions vẫn 87` (trong đó có item32043/quest12162).
- Nguồn nhận item32043 **chưa được xác minh**: không có trong `gamedata/npc_drops.json`, NPC merchant Surface/Goods hoặc Eve reward cấp trực tiếp. Item32043 kind23/restrict15 loại khỏi output generic CompoundSystem (bit8 không được hợp thành). Nguồn PC tham khảo có hệ thống drop thường/hiếm gồm 6 ô; không thể mặc định NPC IDs và drop rates giống DVT.
- Các điều kiện giờ thật trong Eve13, lựa chọn xác nhận, bag space, chuỗi hoàn tất vẫn phải được kiểm thử trên nhân vật/client. Script không tuyên bố đã hoàn thành quest E2E hoặc tự rơi vật phẩm.
- Chưa giải mã resultClass13/value40 của quest10852; không có thay đổi gì với quest này.

## Kiểm chứng

`python -m unittest discover -s tools -p test_dvt_12162_clay_pot_turnin_20261012.py -v` chốt NPC trả, 30 Nồi Đất, toàn bộ effect và bộ MarkStep; đồng thời kiểm tra chạy lại không ghi dữ liệu. Chạy cùng các test DVT navigation, nguồn thưởng và blocker cho tổng 23 bài test đều PASS trên checkout Windows. GitHub CI và trạng thái dev sẽ được ghi riêng khi xác minh; không đánh đồng unit test với E2E.

**Ưu tiên tiếp theo:** truy bảng rơi item32043 thực qua nguồn server TS PC, DVT data độc lập và dữ liệu map; không thêm drop tạm hoặc tạo NPC giả.

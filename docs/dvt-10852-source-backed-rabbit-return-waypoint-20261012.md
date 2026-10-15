# Quest 10852/1 — khôi phục END trả Ngọc Thố về Ngô Cương (12-10-2026)

## Nguyên nhân xác minh

- Trong `lua/Logic/MarkManager.lua`, `Navigation()` chọn `step.endSceneId/endPosition/endEventId` trước, rồi kiểm `condition.kind == 1` bằng `Role.GetFollowNpc(Role.playerId, condition.id) ~= nil`.
- Khi chưa sở hữu NPC38022, client đi tới con Ngọc Thố hợp lệ tại map10984, placement3, (3710,2300), theo bản vá 10852 trước.
- Sau khi bắt được NPC38022 (trong danh sách tướng theo), điều kiện hoàn thành và client chuyển sang END; **END ban đầu là (0,0,0,0,0)** nên client chỉ hiện thông báo không xác định đường đi và hủy dẫn đường.
- Eve gốc map11021 có NPC **15153 Ngô Cương**, placement **7**, tọa độ **(190,180)**, gắn Eve11. Nhánh điều kiện #5 kiểm tra mission10852/step1; #6 kiểm tra sở hữu tướng NPC38022; #7 kiểm tra điều kiện bên cạnh. Kết quả chuỗi #5 có `class13/pStyle1/value40`, `class8/pStyle2/NPC38022/value1`, hoàn thành 10852 và mở 10853. Vì vậy **đây đúng NPC trả**, không tự tạo NPC hoặc chuyển sang trưởng thôn chưa được xác minh.

## Bản vá

- Script `tools/patch_dvt_10852_rabbit_return_waypoint_20261012.py` giữ nguyên 4505 MarkStep, các condition, và tất cả thông tin nhiệm vụ khác; chỉ sửa END của 10852/1 thành **`(11021,190,180,1,7)`** (scene, x, y, eventKind, placementId).
- Script xác minh chính xác Eve11 và các effect nguyên bản, kiểm SHA edition đã cho phép, kiểm cửa sổ 8 byte, tạo backup trước khi ghi, chạy lặp idempotent. Vùng END tại offset 215667..215674; chỉ 6 byte thực sự khác.
- Repo/server edition 892087 byte SHA **`e0278071b4f12f75d058d51661e743ad0c42c83ab6a08af2bc610ba4f7e02074`** → **`c06c8f2179b78033faac137cd7803d4c054f5f151a8f54df1f013f321972739e`**.
- Client edition 928425 byte SHA **`b7af396c575d50b18cb3408c8e476e91fa7a543c573256d3240ce52f69c8770c`** → **`a810858dc35924ef19e537a6e73f8a684f9433420cd80a7b4aa1b6fea89df80c`**. Phải vá riêng `AssetBundles/Data/IOS_MarkStep_C.dat` và `tsnpc_Data/StreamingAssets/Data/MarkStep_C.dat`, không chép bản 892087 byte sang client 928425 byte.

## Giới hạn cần giữ

**Không khẳng định hoàn tất nhiệm vụ 10852.** `EveResultExecutor.canExecuteResults` vẫn chặn `resultClass13/value40`, vì chưa xác minh cách ghi điểm/nhận thưởng/tiến độ của nó. Bản vá này chỉ giải quyết việc nhấn nhiệm vụ khi **đã bắt được Ngọc Thố** không thể tự tìm đường về NPC Ngô Cương. Không phát quà giả, không bỏ qua resultClass13, không sửa inventory, DB hay Eve.

Kotlin tests và Python tests/CI sẽ kiểm tra thêm nguồn NPC, bit nhánh kết quả, SHA MarkStep, không có đích trỏ scene/NPC/door không tồn tại, và client Lua vẫn dùng `Role.GetFollowNpc` để quyết định END. Khi kiểm thử thực tế, cần bắt NPC38022, nhấn tên nhiệm vụ từ Quảng Hàn động, xác minh đến Ngô Cương map11021 rồi kiểm tra lỗi `class13` ở lời thoại hoàn thành theo tình trạng hiện hữu.

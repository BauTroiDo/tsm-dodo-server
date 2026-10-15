# Ngăn nhiệm vụ 10120/10164 bị tăng quá bước NPC trả — 2026-10-11

## Nguồn đối chiếu

Dữ liệu chính thức của client và server DVT đang dùng trong PR #41:
- `gamedata/Eve.emg`, SHA-256 `03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`.
- `File.dat/Data-ios/IOS_MarkStep_C.dat`, SHA-256 `cd5d4dfe175ae12fb71a4ad6c52f17d447335944d6efdfb269652534dbf3815f` (đã sửa 10 cửa sai).

**Nhiệm vụ 10120 – Hắc Sơn:** MarkStep chỉ có bước 1 và 2. Cửa tại scene 11801, Eve16/Eve18, có chuỗi kết quả sau chiến đấu `resultClass=2/parameterStyle=1/value=1`. NPC quan phủ **14216** ở scene **11301/Eve3/condition2** kiểm tra `10120 == 2` để trả nhiệm vụ, trao `46285`, khởi tạo `10124` và `10121`, hoàn tất `10120` bằng `parameterStyle=3`. Bị tăng lặp lên bước 3 đồng nghĩa NPC không còn khớp điều kiện trả.

**Nhiệm vụ 10164 – Tế Đàn:** MarkStep chỉ có bước 1 và 2. Cửa scene **12012/Eve19, Eve20** có nhánh tăng `+1`. NPC **20001** tại **12012/Eve21** có điều kiện kiểm tra bước 2 và nhánh kết thúc (`Eve21/condition8`) trao `18004`, hoàn tất 10164, bắt đầu 10165. Bước 3 không có MarkStep và không phải trạng thái được NPC nhận.

Cả **10120/3** và **10164/3** cùng tồn tại trên **một nhân vật dev cấp 65 tại scene 11012**, có lịch sử Eve tương đối dài. Có khả năng một nhánh sau chiến đấu hoặc cửa dịch chuyển bị kích hoạt lặp; điều này là giả thuyết từ cấu trúc Eve, chưa phải sự kiện được xác thực bằng log tại thời điểm phát sinh.

## Sửa mã

- Thêm `DvtQuestStepBoundaryPolicy` cho đúng hai mã `10120`, `10164`, đồng thời giữ nguyên chặn bước 6–10 cho tuyến tân thủ `12280`. Không áp dụng ngưỡng chung cho tất cả nhiệm vụ: nhiệm vụ marker/dungeon có thể hợp lệ với bước ngoài MarkStep.
- `EveMissionPreflightPolicy` từ chối **toàn bộ chuỗi Eve** nếu ADD vượt bước 2 của hai nhiệm vụ này, trước khi áp dụng thay đổi túi đồ, vàng hay tiến độ kế tiếp. Trả mã `UNDEFINED_DVT_QUEST_STEP`.
- `QuestSystem.addMissionStep` kiểm tra lại dưới mutex riêng cho từng nhân vật trước khi ghi DB, ngăn trường hợp trạng thái thay đổi giữa preflight và khi thực hiện.
- `DvtTerminalTurnInStepRegressionTest` bảo vệ dữ liệu Eve/MarkStep, quyền tăng 1→2, chặn 2→3, xử lý trước khi phát thưởng và kiểm tra repository không bị ghi khi xảy ra lặp. Kiểm thử tuyến `12280` vẫn được duy trì.

## Phân biệt triển khai và phục hồi dữ liệu

**GitHub/CI không đồng nghĩa với triển khai lên máy Windows.** Desktop Commander đã đạt hạn mức tháng trong lượt này và thông báo không gọi lại hay kết nối lại, vì thế phần runtime mới **chưa được triển khai lên DEV**. Giữ nguyên bản đang chạy PID11808/port6613 theo lần kiểm tra gần nhất; không khẳng định PID hiện tại nếu chưa kiểm tra lại.

**Không có thay đổi MySQL trong lượt này.** Không tự hạ `10120/3` hoặc `10164/3` về bước 2 vì cần kiểm tra xem vật phẩm `46285`, `18004` và cờ hoàn thành/nhiệm vụ kế tiếp đã được nhận chưa. Việc phục hồi sau này phải dựa vào một profile xác minh riêng, bản sao lưu và SQL compare-and-set, không tái phát thưởng ngoài Eve gốc.

## Tiếp theo

1. Khi công cụ điều khiển Windows có lại hạn mức, kiểm tra CI xanh, sao lưu và áp bản vá runtime đến `C:\Server-Game\tsm_dodo`, build `:app:installDist` và restart game. Không ghi đè code live đang dirty; dùng patch có `git apply --check`.
2. Kiểm tra **chỉ đọc** hồ sơ cấp65: mission flags của 10120/10164, tồn kho/đồ trang bị item46285 và18004, nhiệm vụ10121/10124/10165, các sự kiện đã chạy trong 11301/12012, và trạng thái kết nối trước khi cân nhắc phục hồi. Nếu phù hợp mới sao lưu và sửa một cách có điều kiện.
3. Rà thêm hồ sơ `12280/6` tại scene12003, rồi tiếp tục 89 hiệu ứng thưởng `class13`, 35 tài nguyên `class11`, 279 điều kiện `class14` và 8 bước không có tọa độ.
4. Chỉ quay lại Gia Viên/Định hôn/nhận con sau khi hệ thống nhiệm vụ đã được kiểm tra và khắc phục.

# DVT chính tuyến: kết quả nhiệm vụ ghép mã (2026-10-10)

## Nguyên nhân gây kẹt

Từ dữ liệu `gamedata/Eve.emg` PC đang sử dụng trên live, bộ quét 54.920 chuỗi Eve / 10.192 chuỗi liên quan tới 400 nhiệm vụ chính tuyến có MarkStep phát hiện **20 nhánh** mang kết quả:

- `resultType=0, resultClass=2, parameter=2, pStyle=1`
- `resultValue = markerCategory * 100000 + targetMissionId` (ví dụ `612130` trong nhiệm vụ `12130`, `5411488` trong `11488`).

Các nhánh gặp ở mission `12080, 11488, 11432, 11462, 11490, 11486, 11900, 11896, 11894, 12066, 12130, 12082, 12132`; một số nhánh lặp theo mốc cấp/bước.

Ví dụ map `14636`/Eve `8`: điều kiện `15..19` có hiệu ứng tăng mission `12130` một bước, sau đó là `resultClass=2, parameter=2, pStyle=1, value=612130`. Bộ kiểm tra cũ của server chặn toàn chuỗi vì 612130 vượt giới hạn bước byte 255; nhiệm vụ hợp lệ không tiến được.

## Nguồn hành vi gốc DVT

`_native_dump_dvt115/_lua_client_decrypted/Logic/Event/EventHandler.lua`, nhánh resultClass=2, pStyle=1:

```lua
if (MaxMissionQuant - MarkManager.missions[eventInfo.parameter].step) >= eventInfo.resultValue then
  MarkManager.missions[eventInfo.parameter].step = MarkManager.missions[eventInfo.parameter].step + eventInfo.resultValue;
end
```

`MarkManager.AddMissionStep` đặt ngưỡng 255. Giá trị quá lớn **không làm đổi bước**, không phải lệnh tăng bước hợp lệ. Vì thế server phải coi các mã ghép có cấu trúc rõ ràng trên mission 2 là *hiệu ứng không làm thay đổi nhiệm vụ*, thay vì dừng cả chuỗi hoặc phát gói S024 sai/tràn byte.

## Bản vá

`EvePackedMissionHintPolicy` chỉ nhận diện:
- resultType trong `0,7`, resultClass `2`, parameter `2`, pStyle `1`;
- resultValue lớn hơn 255, category = value / 100000 thuộc `1..99`;
- missionId = value % 100000 phải **tồn tại trong MarkData thực tế**.

Preflight loại các hint không thay đổi bước khỏi danh sách cộng/trừ nhiệm vụ; executor bỏ qua đúng kết quả đó và **không ghi DB / không gửi S024**, nhưng vẫn tiếp tục event, các item/võ tướng/phần thưởng sau đó được bảo vệ bằng kiểm tra cũ. Mọi phép tăng bước thật không hợp lệ vẫn bị từ chối.

## Kiểm thử

- Unit: nhận diện `612130`, `5411488` và từ chối biến thể sai dạng/sai mission.
- Data-driven: kiểm tra **5 điều kiện** ghép mã `612130` của scene 14636/eve8; phân biệt nhánh thứ sáu tăng 12130 nhưng không ghép mã.
- Preflight: nhiệm vụ 12130 được phép tiến một bước khi hint được bỏ qua; overflow thật vẫn bị chặn.
- Test tổng repository + production cần PASS trước deploy; sau deploy phải dùng **nhân vật test** đến scene14636/eve8 để xác nhận bước không kẹt, không tự thay progress người chơi thật.

Báo cáo chi tiết định lượng và hai navigation Door nghi vấn ở `C:\Server-Game\tsm_dodo\backups\quest_mainline_audit_20261010\flow_deep_audit.md`. Không tự sửa MarkStep/door hướng dẫn khi chưa xác minh dữ liệu client thực.

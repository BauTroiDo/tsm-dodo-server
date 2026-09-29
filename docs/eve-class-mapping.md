# Phân tích phủ ngữ nghĩa Eve Class

Ngày: 2026-05-06

Tài liệu này ghi lại `Eve.emg` cùng các trường `conditionClass`, `resultType`, `resultClass`
đối chiếu ngữ nghĩa và trạng thái phủ hiện tại của server. Lua và dữ liệu Eve là nguồn chân lý duy nhất;
Ngữ nghĩa chưa được Lua hoặc dữ liệu thực chứng minh thì không được hardcode thành logic chính thức.

## Nguồn

- `gamedata/Eve.emg`
- `TSClient/TSMonline_TW/lua_decrypted/Data/Eve/Eve_NpcEventData.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/Event/EventHandler.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/Event/EventManager.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/CheckCondition.lua`
- `TSClient/TSMonline_TW/lua_decrypted/Logic/RoleCount.lua`
- `docs/eve-system.md`
- `gamelogic/.../EveConditionEvaluator.kt`
- `gamelogic/.../EveResultExecutor.kt`
- `gamelogic/.../EveFlowController.kt`

## Cơ sở phân tích

- Số thư mục Scene: 3823
- Scene phân tích thành công: 3823
- Lỗi phân tích: 0
- `conditionClass` đã quan sát: 0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
- `resultType` đã quan sát: 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
- `resultClass` đã quan sát: 0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15

## Thang độ tin cậy

- Đã xác nhận: Lua có handler trực tiếp, hoặc gói tin và flow thực tế hiện tại đã chạy thông.
- Suy luận tin cậy cao: tên trường Lua, ngữ cảnh sự kiện, phân bố tham số nhất quán, nhưng vẫn cần test chốt.
- Suy luận tin cậy trung bình: có mẫu dữ liệu nhưng thiếu ngữ nghĩa thực thi đầy đủ trên client.
- Chưa xác minh: chưa được cài đặt thành hiệu ứng chính thức, chỉ thêm chẩn đoán hoặc no-op rõ ràng.

## Hiệu chỉnh quan trọng

1. `conditionClass=14` tài liệu và code hiện coi là RoleCount, nhận định này cần hạ bậc.
   Trong `Eve.emg` class14 gần như dồn ở `param=0, pStyle=0, value=0`,
   không khớp phân bố index của `RoleCount.lua`.
2. `conditionClass=18` có ví dụ `param=668`, khớp với
   dòng `RoleCount.lua` có `ERoleCount.ReUseAct_1 = 668`. RoleCount nhiều khả năng nằm ở
   class18, hoặc ít nhất một số ngữ cảnh của class18 có dùng RoleCount.
3. `CheckCondition.lua` có `ECondition.RoleCount=14` thuộc hệ điều kiện chung, không thể trực tiếp
   1:1 áp dụng cho `conditionClass` của Eve. Trong dữ liệu, Eve class1 là điều kiện vật phẩm,
   nhưng `CheckCondition.ECondition.Gold=1`, hai bên đã rõ ràng không nhất quán.
4. `EventHandler.lua` chỉ định nghĩa từ `EventHandler[0]` đến `[6]`. `resultType=8/9/10`
   tuy tồn tại trong dữ liệu Eve nhưng client không có handler cùng tên; server không được gửi mù quáng cho client.
5. `conditionClass=7, param=2` không được coi là số lần chuyển sinh. Mẫu thực tế chủ yếu là không gian túi đồ:
   `pStyle=1` nối hội thoại túi đồ đầy, `pStyle=2` nối flow cấp vật phẩm hoặc tương tác bình thường.
   Dung lượng túi đồ phải dùng `RoleCount[103/106/124]` căn theo Lua `Item.RefreshRoleCount()`.

## Bảng phủ conditionClass

| Class | Số dòng | Trạng thái | Nhận định ngữ nghĩa | Xử lý tiếp |
| --- | ---: | --- | --- | --- |
| 0 | 17857 | Đã xác nhận | Không điều kiện | Giữ true |
| 1 | 4704 | Đã xác nhận | Số lượng vật phẩm sở hữu, `param=itemId` | Bổ sung test phân bố pStyle |
| 2 | 29381 | Một phần | Bước nhiệm vụ hoặc cờ nhiệm vụ | Bổ sung pStyle 4/10; xác nhận gộp flag pStyle 3 |
| 3 | 783 | Tin cậy cao | Trạng thái tương tác được của vật thể trong cảnh; dữ liệu cố định toàn bộ `pStyle=4,ops=5,value=0,subItem=0`, thường nối `resultClass=3 pStyle=5` | Đã bổ sung hình cố định, hình khác bảo thủ false |
| 4 | 36 | Tin cậy cao | Điều kiện kỹ năng, `param=skillId` | Cài đợt đầu, đọc cấp kỹ năng từ `SkillSystem` |
| 5 | 142 | Tin cậy cao | Điều kiện tiền, `param=0/pStyle=1`; phổ biến `ops=2,value=tiền-1` có nghĩa vàng trên người đủ | Đã nối `PlayerEventState.gold`, một số `ops=4` bảo thủ coi là ít nhất đạt ngưỡng |
| 7 | 8531 | Một phần | Trạng thái người chơi, cấp độ, không gian túi đồ, trang bị, giới tính, tướng đang có, trạng thái cưỡi; param chưa biết không được nhân `ops=0,value=0` mà thông nhầm | Đã bổ sung param=2 không gian túi đồ (gồm pStyle=4 số ô còn lại), param=5 giới tính, param=16 cưỡi/xuống ngựa; tiếp tục bổ nhánh param 8/12/18 |
| 8 | 6242 | Đã xác nhận | Nhánh kết quả chiến đấu | Giữ so sánh battleResult |
| 9 | 2102 | Một phần | `param=0` là sở hữu tướng theo người; `param!=0` nghi là điều kiện cấp, vị trí hoặc trạng thái tướng, không dùng `value=0` mà thông nhầm | Bổ sung pStyle 1/2/3 và param khác 0 |
| 10 | 6055 | Đã xác nhận | Nhánh lựa chọn Surface, `param=surfaceId`, `pStyle=choiceCode` | Giữ hiện trạng và bổ sung regression |
| 11 | 76 | Tin cậy cao | Xác nhận flow bà giải kinh; dữ liệu cố định `param=0,pStyle=1,ops=0,value=0` | Đã bổ sung hình cố định, hình khác bảo thủ false |
| 12 | 662 | Một phần | `param=0` là số lần hoàn thành eveNo hiện tại; `param!=0` chưa xác nhận, không dùng giá trị 0 mà thông nhầm | Bổ sung param 1/2/3/4 và pStyle 3/5/6 |
| 13 | 99 | Đã xác định nhưng chưa cài đặt | Điều kiện thân phận quân đoàn, thành trì, di dân; mẫu tập trung ở cơ sở thành trì, quân đoàn trưởng/phó, thành viên quân đoàn trong thành và tư cách di dân | Cần có hệ quân đoàn/thành trì/di dân trước, tạm bảo thủ false |
| 14 | 291 | Tin cậy trung bình | Chưa xác nhận là RoleCount; đa số là điều kiện giá trị 0 | Trước hết sửa tài liệu và chẩn đoán, không gắn nhãn đã xác nhận |
| 15 | 4 | Đã hỗ trợ | `param=0` là số lần chuyển sinh của người chơi, cảnh 55002 dùng `ops=6,value=0` chặn người đã chuyển sinh, `ops=5,value=0` mở giao diện chuyển sinh; `param=1` là mã trạng thái cổng chuyển sinh lần 2 của tướng, theo Lua `UINpcTurn.CheckTurn(currentTurn=2)` kiểm tra cấp/trung thành của tướng theo người và vật phẩm chuyển sinh lần 2 | `param=0` tương ứng `rebornCount`; `param=1` tương ứng `npcSecondReincarnationStatus` (0=mở được, -1=hiển thị hướng dẫn) |
| 16 | 9 | Tin cậy cao | Kiểm tra vật phẩm kho ngoài túi đồ; `pStyle=1,subItem=0` kiểm tra số lượng Tiền Trang + kho báu, bổ trợ với kiểm tra túi đồ class1 | Hình dạng pStyle/subItem khác vẫn bảo thủ false |
| 17 | 16 | Đã xác định nhưng chưa cài đặt | Điều kiện hệ con cái, gồm sự tồn tại, cấp độ của con và nhánh con ra trận | Cần hệ con cái trước, tạm bảo thủ false |
| 18 | 11 | Tin cậy cao nhưng cần phân luồng | Một số mẫu giống RoleCount; cũng có nhánh param thấp | Chỉ cài đặt index RoleCount đã được chứng minh |

## Bảng phủ resultType

| Type | Số dòng | Handler client | Trạng thái | Xử lý tiếp |
| --- | ---: | --- | --- | --- |
| 0 | 55156 | Có | Đã xác nhận | Server thực thi hiệu ứng resultClass |
| 1 | 70455 | Có | Đã xác nhận | Talk, gửi S:020-001 |
| 2 | 10058 | Có | Đã xác nhận | Door, theo flow dịch chuyển |
| 3 | 23504 | Có | Đã xác nhận | Battle, chờ kết quả chiến đấu |
| 4 | 211 | Có | Một phần | NPC di chuyển, client xử lý là chính |
| 5 | 230 | Có | Một phần | Movie, client xử lý là chính |
| 6 | 2617 | Có | Đã xác nhận | Surface, chờ lựa chọn |
| 7 | 727 | Không handler trực tiếp | Server phân luồng đặc biệt | Giữ ngữ nghĩa rõ ràng theo từng pStyle |
| 8 | 226 | Không | Bỏ qua âm thầm | Không gửi client; tự advance |
| 9 | 1611 | Không | Bỏ qua âm thầm | Không gửi client; tự advance |
| 10 | 115 | Không | Bỏ qua âm thầm | Không gửi client; tự advance |

## Bảng phủ resultClass

| Class | Số dòng | Trạng thái | Nhận định ngữ nghĩa | Xử lý tiếp |
| --- | ---: | --- | --- | --- |
| 0 | 23734 | Đã xác nhận | Không có hiệu ứng server | Giữ tự động đẩy tiếp |
| 1 | 8248 | Một phần | Cấp hoặc khấu trừ vật phẩm; pStyle 0/1 tăng giảm theo dấu của resultValue, pStyle 2 khấu trừ rõ ràng | pStyle 3 vẫn chưa xác minh |
| 2 | 11852 | Một phần | Bước, hoàn thành, cờ nhiệm vụ; pStyle 4 đặt trực tiếp bitId chỉ định qua resultValue, pStyle 5 chỉ xoá nhiệm vụ | pStyle 0 một dòng dữ liệu bất thường, chưa xác minh |
| 3 | 90514 | Một phần | Đội NPC, hiện/ẩn, di chuyển hoặc diễn xuất trên client | Bổ sung pStyle 0/2/6 kết hợp type |
| 4 | 34 | Đã xác nhận | Học hoặc nâng cấp kỹ năng | Bổ sung test tương ứng conditionClass=4 |
| 5 | 402 | Tin cậy cao | Tăng giảm tiền, `param=0`; pStyle=1 tăng, pStyle=2 khấu trừ | Đã nối `MoneySystem` và gói tin tiền |
| 7 | 28125 | Một phần | Người chơi, UI, cửa hàng, lữ quán, ngân hàng, tiền; UI đặc biệt chỉ cho phép `resultType=7`, `pStyle=6` là thưởng tiền, `pStyle=10` là bảng xếp hạng vô giới `S:036-010`, `pStyle=12` là UI chuyển sinh người chơi `S:036-011`, `pStyle=13` là cửa hàng đổi điểm diệt địch `S:036-013`, `pStyle=15` là cổng tiếp nối thuỷ chiến phụng ấp `S:036-015`, `pStyle=21` là UI chuyển sinh lần 2 của tướng `S:044-003`, `pStyle=27/29/30/32/35/39` là tin hoạt động không payload | pStyle từ 7 trở lên vẫn cần đối chiếu từng cái với Lua/giao thức |
| 8 | 485 | Đã xác nhận | Tướng thưởng thêm vào hoặc rời đi | Giữ hiện trạng và bổ sung test nhánh lỗi |
| 10 | 1337 | no-op rõ ràng | type=0, param/value/meanNo đều bằng 0, pStyle chỉ thấy 1/2; client không có nhánh | Server no-op âm thầm rồi tự đẩy tiếp |
| 11 | 35 | Chưa xác minh | Hiệu ứng đặc biệt hiếm | Xây bộ ví dụ |
| 12 | 21 | Chưa xác minh | Nghi là cha mẹ-con hoặc NPC đặc biệt | Đối chiếu nhánh showRole của Talk |
| 13 | 89 | Chưa xác minh | Hiệu ứng đặc biệt hiếm | Xây bộ ví dụ |
| 14 | 33 | Chưa xác minh | Hiệu ứng đặc biệt hiếm | Xây bộ ví dụ |
| 15 | 1 | Chưa xác minh | Một hiệu ứng đặc biệt | Truy riêng ngữ cảnh sự kiện |

## Thứ tự ưu tiên

1. Trước tiên tạo bộ ví dụ và test phủ cho Eve class, tránh bỏ sót các trường đã quan sát nhưng chưa phân loại.
2. Cài đặt các nhóm tin cậy cao và không làm thay đổi flow nhiều:
   `conditionClass=4` điều kiện kỹ năng, bổ sung pStyle đã biết của `resultClass=1/2`,
   `resultType=8/9/10` chiến lược skip rõ ràng.
3. Sửa logic nhận diện RoleCount:
   class14 không còn bị đánh dấu là RoleCount đã xác nhận; class18 chỉ khi tham số trùng
   chỉ mục đã biết trong `RoleCount.lua` mới đọc RoleCount.
4. Tập trung xử lý các khoảng trống ảnh hưởng lớn:
   `resultClass=10`,`resultClass=5`,`conditionClass=5/13/16/17/18`.
5. Lập mẫu sự kiện tối thiểu cho mỗi lớp chờ xác minh:
   sceneId, eveNo, conditionNo, resultGroupNo, đối chiếu Lua, hành vi dự kiến.

## Tiêu chí nghiệm thu

- Mỗi `conditionClass` thực sự xuất hiện trong `Eve.emg` đều phải có:
  logic đã cài đặt, no-op rõ ràng, hoặc mục chẩn đoán chờ xác minh.
- Mỗi `resultType/resultClass` thực sự xuất hiện trong `Eve.emg` đều phải có:
  hiệu ứng server, chuyển tiếp client, skip rõ ràng, hoặc mục chẩn đoán chờ xác minh.
- Luồng nhiệm vụ chính thông thường không được xuất hiện WARN của lớp chưa phân loại.
- Ngữ nghĩa chưa xác minh không được theo kiểu "đoán là qua" mà cho điều kiện trả về true.
- Mỗi lần thêm ngữ nghĩa phải có test mẫu Eve hoặc test đơn vị hàm thuần túy.

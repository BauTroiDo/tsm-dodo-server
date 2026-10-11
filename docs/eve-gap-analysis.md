# Báo cáo phân tích toàn bộ khoảng trống của Eve.emg

> Ngày tạo: 2026-06-12. Công cụ phân tích: `tools/eve_analyze.py` (phản chiếu logic phân tích của EveDataLoader.kt).
> Tỉ lệ phân tích thành công 3823/3823 cảnh (100%), tổng 25457 sự kiện / 77001 điều kiện / 164910 kết quả.

## 1. Phương pháp phân tích

1. Dùng Python tái hiện phân tích nhị phân của `EveDataLoader.kt`, quét toàn bộ Eve.emg
2. Thống kê phân bố sử dụng conditionClass / resultClass / (resultType, resultClass, pStyle)
3. Đối chiếu với mặt hiện hỗ trợ của server (EveConditionEvaluator / EveResultExecutor / EveResultClassPolicy)
4. Với các khoảng trống, dump ngữ cảnh sự kiện đầy đủ (chuỗi điều kiện + chuỗi kết quả + văn bản Talk), dùng nội dung hội thoại Talk_C.dat để giám định ngữ nghĩa

**Nguồn văn bản Talk**: `CompreseData/Talk_C.dat` — count(4) + mỗi bản ghi id(U16) + len(U16) + UTF-16LE.
(Lưu ý: resultMeanNo của hội thoại sự kiện tra Talk_C.dat, không phải TextData_C.dat)

## 2. Khoảng trống mặt điều kiện (conditionClass)

| Class | Số bản ghi | Số cảnh | Kết quả giám định | Độ tin cậy |
|------:|-----:|------:|---------|------|
| **13** | 99 | 17 | Trạng thái thành trì quân đoàn: param=1 đối chiếu quyền sở hữu thành (val = số hiệu thành 1~5), param=2 người chơi có phải chính/phó đoàn trưởng không (nhánh pStyle=1/2/3) | Cao (sự kiện xây cơ sở tại lữ quán năm thành lớn 11031/11041/12071/12081/19031) |
| **14** | 291 | 47 | Hiện là stub (đồng loạt trả false); param=0 hình dạng nghi là bộ đếm giai đoạn hoạt động/trạng thái đã nhận, ngữ nghĩa chờ xác minh | Thấp |
| **17** | 16 | 2 | Trạng thái tướng thế hệ hai (con nhân vật), thực tế ở **12721 và 12722**, param=0/2/3 với 6 dạng; cần ChildData 93 byte, S:069, trạng thái ra trận/cấp 10. Chưa có subsystem nên giữ fail-closed. Xem `docs/dvt-child-quest-11980-20261011.md`. | Cao đối với nhiệm vụ 11980/11981 |

> Cải chính: class=16 (đối chiếu số lượng vật phẩm ký gửi storedItems) server **đã hỗ trợ**, bản đầu liệt kê nhầm là chưa hỗ trợ.

### param chưa hỗ trợ của conditionClass=7 (thuộc tính/trạng thái)

Hiện hỗ trợ param=0(cấp độ)/2(chỗ trống túi đồ)/3(trang bị)/5(giới tính)/6(vị trí tướng)/8(Server ID)/16(cưỡi thú)/19(Point thực/Role.GetPoint). Chưa hỗ trợ:

| param | Số bản ghi | param | Số bản ghi |
|------:|-----:|------:|-----:|
| 1 | 106 | 10 | 176 |
| 4 | 8 | 11 | 10 |
| 7 | 54 | 12 | 97 |
| 14 | 32 | 18 (liên động subItem, dùng cho nhận con nuôi) | 38 |
| 9 | 41 | 15 | 20 |

> **param=8 đã giám định: Server ID (468 bản ghi)**.
> authoritative Lua: gói đăng nhập ghi `aSID` vào `Network.serverId`; `Migrant.lua` định nghĩa Server Ngân Hà là 90..99, và khi serverId>100 thì dùng `%100` để nhận diện, đồng thời định nghĩa rõ 54901..54904 là Arena scene.
> Phân bố toàn bộ của Eve.emg khớp hoàn toàn với điều này: `val=96~99/196~199` tập trung tại 54901..54904; ngoài ra còn có điều kiện phân luồng Server với `val=1/2/100~104` và `ops=1/2/5`.
> Server đã giữ trực tiếp `session.serverId` của login request, vì vậy conditionClass=7/param=8 đọc giá trị này và áp dụng các DVT comparison operators sẵn có; pStyle/subItem chưa biết vẫn thận trọng trả false.
>
> **param=19 đã giám định: Point thực khả dụng (`Role.GetPoint()`, 6 bản ghi)**. Toàn bộ Eve.emg chỉ xuất hiện ở scene 12180 sòng bài chính, cố định `pStyle=2/subItem=0/ops=5`; ngưỡng nhánh serverId=100 là 9/45/90, ngưỡng nhánh thường là 10/50/100. Mỗi ngưỡng đều ghép cặp với kết quả mua trực tiếp resultClass=7/pStyle=3 cùng chain: `131171/531171/1031171` giải mã thành item31171 ×1/5/10, `resultMeanNo` đúng bằng giá Point tương ứng. Lua `Role.GetPoint()` là real Point, tách biệt với lockPoint; `UISlotMachine` với item31171 cũng mua trực tiếp bằng `Role.GetPoint()` + `ECurrency.Gold`. Kotlin vì vậy đọc `PointSystem.getRealPoint()`; shape chưa biết vẫn false.
> Các param còn lại (1/4/7/9/10/11/12/14/15) ngữ nghĩa chưa rõ; khi đánh giá thận trọng trả `false`, nhánh tương ứng không đi tới được.

## 3. Khoảng trống mặt kết quả (resultClass, chỉ type=0/7 cần server thực thi)

| Class | Số bản ghi | Kết quả giám định | Độ tin cậy | Ví dụ |
|------:|-----:|---------|------|------|
| **11** | 35 | Tăng giảm tài nguyên quân đoàn/thành trì: pStyle=1~5 ứng với năm loại tài nguyên (nông nghiệp/chăn nuôi/khoáng sản/lâm nghiệp/tiền quỹ, đối chiếu giá trị bốn ngành thành trì S:041-011), val = lượng | Trung cao | 11572/26522/26525/57523, val=50~1000 |
| **12** | 21 | Toàn bộ gắn trên type=1 (Talk), server không cần thực thi | Cao | 12721/12722 |
| **13** | 89 | **Phát gói thưởng hoạt động**: val = mã phần thưởng (12/13/40/49/52/69~78), thường gắn nhóm xác suất resultGroupNo. Đã bác bỏ giả thuyết bản đầu "介消 sellId" (val 78 > giới hạn trên UISell 69; hội thoại Talk đều thuộc loại NPC hoạt động kiểu "đây là phần thưởng cho bạn": thưởng ải quan 13096,đổi đèn lồng 34288,huy hiệu sử thi 52570,bài tập nghỉ hè 58400,Lễ hội Halloween 23419,ngọc thỏ 15361). **Chưa xác định được bảng thưởng ứng với mã phần thưởng** (đoán là dữ liệu phía server, client không có bảng này) | Cao (ngữ nghĩa)/ Thấp (bảng thưởng) | 12003 eve=24 bước nhiệm vụ N → thưởng 67+N |
| **14** | 33 | **Tăng giảm bộ đếm RoleCount (đã cài đặt)**: param = chỉ mục bộ đếm, pStyle=1 cộng val / 2 trừ val / 3 đưa về 0; ghép cặp với conditionClass=18 (đọc bộ đếm). Ví dụ: 10951 lựa chọn hội thoại Đào Viên kết nghĩa ±RoleCount[1], 12002 hoạt động bài tập nghỉ hè RoleCount[189] đồng bộ số lượng quà +1/+10/+20 | Cao | 12002 eve=28 param=189 |
| **15** | 1 | Ví dụ đơn lẻ (10821 eve=3 param=1) | Thấp | — |

## 4. Bảng giám định pStyle chưa ánh xạ của resultClass=7 (thao tác người chơi)

Chỉ liệt kê các mục chưa ánh xạ của type=0/7 (server cần gửi gói), đã giám định theo nội dung hội thoại Talk:

| pStyle | Số bản ghi | Chức năng giám định | Gói tin tương ứng | Độ tin cậy | Bằng chứng |
|-------:|-----:|---------|---------|------|------|
| **7** | 11 | Bảng nhắn (nhánh xem) | S:036-003 mở bảng nhắn / S:036-004 mọi tin nhắn | Trung cao | 10951 eve=8 lựa chọn 31→7, lựa chọn 30→8 |
| **8** | 7 | Bảng nhắn (mở, gửi trước phần lễ nghi Talk 19032) | S:036-003/004 | Cao | Talk 19032 "lễ nghi bảng nhắn...mỗi ngày ba tin" |
| **14** | 2 | Tặng lễ kim tại nhà cử hành hôn lễ | S:045-005 mở giao diện phát lễ kim | Cao | 12911 nhà cử hành hôn lễ, Talk 13894 giải thích lễ kim |
| **17** | 1 | Hoạt động vấn đáp (hỏi đáp cơ chế) | S:020-017 sự kiện hỏi câu hỏi (đoán) | Thấp | 12003 eve=32 kích phát không điều kiện |
| **20** | 1 | Nơi đổi thưởng chiến công | S:036-013 mở giao diện mua bán chiến công (giống pStyle=13?) | Trung | 12711, Talk 28300 "nơi đổi thưởng chiến công" |
| **22** | 2 | Máy quay trứng | S:023-107 giao diện quay trứng | Cao | 12180, Talk 33108~33113 giải thích quay trứng |
| **33** | 10 | Công tắc hoạt động của cảnh tỉ võ ([20][33] công tắc hoạt động năm mới?) | Chờ xác minh | Thấp | 49403~49449 cảnh tỉ võ eve=7 |
| **34** | 20 | Giao diện xây cơ sở thành trì quân đoàn | S:036-021 nâng cấp cửa hàng thành trì (UICityStation) (đoán) | Trung | lữ quán năm thành lớn, Talk 37733~37736 xây cơ sở |
| **36** | 1 | Nhận con nuôi (tướng thế hệ hai) | S:036-023 giao diện nuôi dưỡng tướng thế hệ hai (client đã tắt Se_ChildForm) | Trung cao | 12721 Phục Hy, Talk 42858 nhận nuôi |
| **38** | 1 | Nhận quà lễ tết | Chờ xác minh | Thấp | 12001 eve=64, Talk 45697~45699 quà |
| 9/11/16 và các mục còn lại | <10 | Chủ yếu gắn trên type=8/10 (client không có handler, hiện đã bỏ qua tĩnh), không cần xử lý | — | — |

> Ngoài ra: type=7 cls=7 pStyle=31/32 xuất hiện đồng thời ở cảnh tỉ võ 49403~49449, pStyle=32 đã ánh xạ thành thông báo lỗi đấu đài.

### 4.1 "biến thể kèm giá trị" của pStyle đã ánh xạ (rủi ro kích hoạt nhầm, đã thêm chống nhầm)

- **pStyle=1 + val≠0**: val = số hiệu đài (99/98/97, 199/198/197) = dịch chuyển đấu đài Vô Giới,
  không phải "mở kho báu/điểm ghi". Cảnh 12003 eve=31/35 pháp sư dịch chuyển Vô Giới, lựa chọn đài 1/2/3.
- **pStyle=3 + val≠0**: shape mua trực tiếp đã xác nhận là `parameter=0`, `val=數量×100000+物品ID`, `meanNo=實 Point 價格`. Sáu bản ghi của scene12180 là 31171×1/5/10, giá server100 9/45/90, giá server thường 10/50/100. Server đã cài đặt shape chính xác này: kiểm tra/khấu `PointSystem.realPoint`, sau khi kiểm tra dung lượng đầy đủ thì thêm item, đồng bộ S35-5 Point; các shape pStyle=3 kèm giá trị khác vẫn conservative skip.
- **pStyle=1 + val≠0** vẫn chỉ làm chống nhầm skip, subsystem dịch chuyển đấu đài Vô Giới chưa cài đặt.

## 5. Kết luận quan trọng: lối vào Eve của tuyến nuôi dưỡng tướng

**Trong Eve.emg không tồn tại lối mở các giao diện sau đây** (xác nhận bằng quét toàn bộ):

- Giao diện chuyển sinh lần 1 của tướng S:044-002 (`UI.Open(UINpcTurn, 1)`)
- Giao diện kỹ năng đặc biệt của tướng S:036-012 (cần kèm danh sách chỉ mục tướng có thể học)
- Giao diện chuyển sinh lần 3 của người chơi S:036-014

Ý nghĩa: các tính năng này không do sự kiện NPC kích phát trực tiếp, có thể do **script hội thoại của hệ thống nhiệm vụ (mainKind=47)**,
**hệ thống hoạt động** hoặc backend vận hành gửi xuống động. Khi cài đặt hệ thống nhiệm vụ cần quay lại xác minh.
→ TODO để lại trong PetRebornHandler trước đây "giao diện chuyển sinh lần 1 Eve pStyle chờ kiểm chứng" có thể đóng: **đáp án là không có**,
Lệnh GM `/武將轉生 [1|2]` là lối vào duy nhất hiện tại.

## 6. resultType 8/9/10 (client không có handler)

| type | Số bản ghi | Xử lý hiện hành |
|-----:|-----:|---------|
| 8 | 226 | Bỏ qua tĩnh (EveResultTypePolicy) |
| 9 | 1611 | Bỏ qua tĩnh |
| 10 | 115 | Bỏ qua tĩnh |

type=9 số lượng lớn (chủ yếu cls=3 pStyle=1/2), phần lớn là biến thể thao tác hỗ trợ hình ảnh của NPC; client EventHandler chỉ định nghĩa 0~6, bỏ qua là đúng.

## 7. Thứ tự ưu tiên cài đặt đề xuất

1. ~~resultClass=14 (tăng giảm RoleCount)~~ — **đã cài đặt** (EveResultExecutor.handleRoleCountResult)
2. ~~conditionClass=7 param=8~~ — **đã giám định Server ID + runtime PASS**; ~~param19~~ — **đã giám định real Point + build/test PASS, chờ runtime**
3. **resultClass=13 (gói thưởng hoạt động)** — 89 bản ghi, ngữ nghĩa đã định nhưng chưa xác định được bảng mã phần thưởng; khi làm hệ thống hoạt động ưu tiên xác định vị trí trước (ứng viên: dữ liệu phía server bị thiếu, cần suy ngược từ bản PC hoặc mẫu gói tin)
4. **Bảng nhắn (pStyle=7/8 + mainKind=36-001~007)** — hệ thống nhỏ hoàn chỉnh, tính năng cộng đồng
5. **Lễ kim hôn nhân (pStyle=14 + S:045-005)** — một phần của hệ thống hôn nhân (mainKind=45), nên làm cùng hệ thống hôn nhân
6. **Tuyến thành trì quân đoàn (conditionClass=13 + resultClass=11 + pStyle=34)** — phụ thuộc hệ thống quân đoàn (mainKind=39/41/43), quy mô lớn, khi làm quân đoàn thì xử lý luôn
7. Máy quay trứng (pStyle=22)/ con nuôi (pStyle=36, giao diện client đã tắt)/ loại tin tức hoạt động — ưu tiên thấp

## 8. Công cụ

- `tools/eve_analyze.py` — thống kê toàn bộ (phân bố conditionClass/resultClass/pStyle + ví dụ chưa ánh xạ), xuất ra `_eve_report.txt`
- `tools/eve_focus.py` — dump ngữ cảnh đầy đủ của sự kiện khoảng trống (chuỗi điều kiện + chuỗi kết quả), xuất ra `_eve_focus.txt`
- Chạy: `python tools/eve_analyze.py` (đường dẫn hardcode ở đầu script, cần thì điều chỉnh)

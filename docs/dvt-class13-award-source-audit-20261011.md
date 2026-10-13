# Đối chiếu `resultClass13` với nguồn thưởng DVT — 2026-10-11

## Kết quả có thể kiểm chứng

Quét toàn bộ `gamedata/Eve.emg` đang chạy (SHA-256
`03c84cb565d4bdd10b7506a4d04a918ae590737f80a3e7e4fd37782b9c40b910`)
xác nhận **89** hiệu ứng `resultClass=13` ở 25 scene, tất cả `resultType=0`
và `parameter=0`. Có **82** `resultValue` khác nhau, và phân bố
`parameterStyle` là **1:82**, **2:7**. Riêng scene12003 chứa **55**
hiệu ứng; nhiều nhánh Eve24–26,42,85–87 gắn các mã 1–101 với nhiệm vụ
10714/10715.

## Đã kiểm tra ứng viên `IOS_ActivityAward_C.dat`

File thật nằm ở `File.dat/Data-ios/IOS_ActivityAward_C.dat`, 2.866 byte,
SHA-256 `c5a015cbfd2b9e62ba2977dcdba44cd9d6f608d76156fff791068af1c67f27ac`.
Có `uint32le(159)` rồi chính xác **159 dòng × 18 byte**. Kiểu nhị phân
có thể đọc chính xác mà chưa đặt tên ngữ nghĩa:

```python
struct.Struct("<HHBIBHHI")  # 2+2+1+4+1+2+2+4 = 18 bytes
# Row 0 = (1, 101, 1, 2, 4, 68, 1, 21197)
```

Trong toàn bộ 159 dòng, **trường ushort đầu tiên chỉ có 25 giá trị**
khác nhau, và **61/82 mã class13 không trùng với bất kỳ giá trị nào** ở
trường đó. Trường ushort thứ hai cũng chỉ có 25 giá trị; **81/82 mã
class13 không trùng**. Vì vậy **không có cơ sở** để đọc trực tiếp dòng có
ID bằng `resultValue` rồi trao vật phẩm cho nhân vật.

Sự giống nhau về tên file `ActivityAward` không chứng minh bảng đó là
bảng thực thi của `resultClass13`. Các trường nhị phân phía trên
được mô tả bằng **kích thước / giá trị**, không tự gán kiểu “mã vật phẩm”,
“gói thưởng”, “số lượng” hoặc “đã lĩnh”.

## Những dấu hiệu giao thức

Bản Lua DVT `_native_dump_dvt115/_lua_client_decrypted/Logic/Event/EventHandler.lua`
chỉ thực hiện các nhánh client của `resultType=0` như class1/2/3/8;
**không có nhánh thực thi class13**. Như vậy cần tra tiếp nguồn logic
server hoặc giao thức độc lập trước khi định nghĩa side effect; Lua
không xác nhận cách cấp vật phẩm tại đây.

Bảy mã có `pStyle=2` chỉ nằm trong một số scene muộn
12926,12928,12933,12934,12936,12937,12939, nên không được mặc định
rằng pStyle2 là thưởng cùng hình dạng pStyle1.

## Truy thêm ngữ cảnh các mã ở map12003

Đối chiếu `File.dat/Data-ios/IOS_Mark_C.dat` xác nhận các nhiệm vụ
**10714** và **10715** đều mang tên **“Thách Thức đấu Lôi đài”**,
lần lượt là mark đang tiến hành và mark theo dõi khiêu chiến. Nhánh
`resultClass13` các giá trị 69–101 thuộc các Eve24–26 và Eve42 ở scene
**12003**, do **NPC16027**, `npc index14`, tại vị trí `530,840`
thực thi. Trong cùng chuỗi, hiệu ứng class13 đi trước thao tác tăng
`10715`. Điều này cho thấy mã class13 có liên hệ với tiến độ/thưởng
**đấu trường**, không có chứng cứ rằng chúng là khóa trực tiếp vào
`IOS_ActivityAward_C.dat`.

Các hiệu ứng `pStyle=2` ở scene12926/12928/12933/12934/12936/
12937/12939 liên quan tới `10690` (“Tháp Thử Luyện”) và các nhiệm vụ
phụ bản tương ứng. Không được mặc định `pStyle=2` có cùng tác dụng với
`pStyle=1`, hoặc sửa tất cả class13 bằng một hàm cộng vật phẩm.

Tại scene11021/Eve11, mã `class13/pStyle1/value40` đứng trong chuỗi
nhận/trả nhiệm vụ bắt thỏ ngọc `10852`, kèm kết quả thay đổi tướng theo
người. Như vậy class13 có mặt cả trong nhiệm vụ phụ tuyến, không chỉ
đấu trường. **Chưa khẳng định ngữ nghĩa thực thi** từ ngữ cảnh riêng lẻ.

## Thay đổi trong PR #41

- `tools/audit_dvt_class13_award_sources_20261011.py`: công cụ
  **chỉ đọc** duyệt các record gốc, đối chiếu 89 Eve instructions, in
  danh sách mã thiếu ánh xạ và SHA để theo dõi.
- `tools/test_dvt_class13_award_sources_20261011.py`: kiểm thử chốt
  159 record/18 byte, đúng 89 instructions/82 codes, pStyle1/2,
  55 ở scene12003 và 61 mã không khớp bảng ActivityAward.
- CI kiểm toán dữ liệu chạy regression này cùng các bài test quest khác.

**Không sửa `EveResultExecutor`, không nới `canExecuteResults`,
không thay đổi tiền, item, `character_missions` hoặc trạng thái lĩnh
thưởng của người chơi.** Chưa thể gọi class13 là đã hoàn thành.

## Việc tiếp tục

1. Truy được đúng source server/client nhận mã class13 (hoặc công thức
   ánh xạ qua bảng trung gian); giải mã cả pStyle1 và pStyle2.
2. Xác minh phần thưởng, điều kiện lĩnh một lần và giao dịch idempotent;
   bổ sung test item & mission+reward preflight, thực hiện trên test character.
3. Chỉ sau khi PASS các bước trên mới bổ sung handler class13 và deploy.
4. Tiếp tục 35 class11, 279 class14, nhiệm vụ thiếu waypoint trong khi
   vẫn ưu tiên quest trước con/Gia Viên/Định hôn.

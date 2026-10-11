# Sửa điều hướng 3 điểm bán hàng nhiệm vụ DVT 12509/8 — 2026-10-11

## Truy nguồn

Dữ liệu MarkStep gốc của nhiệm vụ **12509, bước 8** ghi rõ cần mua
ba vật phẩm ở ba nơi, nhưng **cả ba điều kiện đồ vật lẫn END đều
có tọa độ (0,0,0,0,0)**. Không được đặt cả ba mục tiêu thành một
NPC, vì cửa hàng thực sự bán từng loại đồ ở những scene riêng.

Đối chiếu Lua gốc `Data/Eve/Eve_SurfaceData.lua` (SurfaceData.New)
và `Data/Eve/Eve_NpcEventData.lua`, kiểm tra sự kiện mở cửa hàng
`resultType=6`, `resultMeanNo` đúng Surface và
`Surface.optionMode=1` (mua bán). Từng Surface có
`sentence.dataKind=2` (Item), `sentence.style=3` (ListItem),
`sentence.dataId` đúng itemId.

| Điều kiện | Vật phẩm / lượng | Scene | NPC thực / placement | (X,Y) | Merchant Surface |
| --- | --- | --- | --- | --- | --- |
| 1 | 26106 × 1 | 13242 | NPC16015, placement1 | 510,400 | Surface2; quán Hứa Xương (đối chiếu tên NPC/chuỗi quest Tào Hồng) |
| 2 | 26013 × 5 | 13241 | NPC16016, placement1 | 350,400 | Surface2; quán trọ Lạc Dương (MarkStep nhiệm vụ11518 cùng scene ghi trực tiếp) |
| 3 | 26016 × 5 | 12002 | NPC16017, placement4 | 470,600 | Surface2; Trác Quận (MarkStep 10001), nằm trong các thành mà mô tả12509 yêu cầu |

Ba NPC đều trực tiếp mở Surface chính xác và đều có bán
vật phẩm tương ứng. Một số vật phẩm cũng được bán ở cửa hàng khác,
nhưng một nguồn NPC có thể mua hợp lệ cho **mỗi điều kiện** là đủ
để làm đích điều hướng; không tuyên bố đây là cửa hàng duy nhất.
Vẫn giữ nguyên END không có tọa độ vì không có chứng cứ
đích trả chung trong bước này.

## Công cụ và chỉnh sửa nhị phân

Bổ sung vào `tools/eve_tool.py` parser byte-for-byte từ Lua
`Eve_GoodsData.lua` và `Eve_SurfaceData.lua`, thay vì bỏ qua:
GoodsData 13 byte mỗi record; Surface có metadata, sentence dataId,
dataKind, style, canCut.

`tools/patch_dvt_12509_christmas_vendors_20261011.py` đọc MarkStep
`<HHHBB>`, tra cứu offset theo missionId/step/condition_index thay
vì offset cứng; kiểm tra nguồn Eve, đúng itemId/số lượng/NPC/vị trí,
nguồn file SHA được phép, chỉ sửa **ba vùng waypoint 8 byte**
(24 byte thay đổi thực tế). Sao lưu file trước khi thay đổi,
ghi atomically, lặp lại không tạo tác dụng phụ. Hai edition riêng:
- Repo/dev `File.dat/Data-ios/IOS_MarkStep_C.dat` cũ SHA
  `642a8e85c5ceabc9f4a59d97cefa96ed5c8839ff843322d234ef9768a24c2689`
  → mới `8f37189491c21ab6f59973ae9a86dbdb279b4276485ef420a85b0bcd0b7bc115`.
- Client staging `AssetBundles/Data/IOS_MarkStep_C.dat` cũ SHA
  `e62963dee3af68824741979372e18e0a2d4884ef2a439787d1beb56218cbb7a3`
  → mới `da1eefd8914cfd13fe251c8397add11102dfee305fb4ed92a854b13faabd51fc`.

Không copy file server qua file client vì edition 892.087 và
928.425 byte có văn bản khác nhau. Không sửa DB, inventory hay
trạng thái nhiệm vụ.

## Nhiệm vụ 12162/1: 30 nồi đất ID32043

Eve gốc có hai sự kiện **nộp** nồi đất: scene12808,
NPC15050 thu 30 cái; scene63000 NPC27012 thu 15 cái cho
nhiệm vụ khác. Không có một kết quả Eve nào **cấp** item32043.
Quét đủ 3.823 scene theo đúng GoodsData và SurfaceData từ
Lua: **0 world Goods và 0 merchant Surface** có item32043.
Vì vậy chưa đủ cơ sở để đặt NPC mua/nhận 30 nồi đất;
nguồn rơi quái và các bảng random drop nằm ngoài hai loại
dữ liệu này vẫn cần điều tra.

## Kiểm tra và mức độ đã xử lý

- `tools/test_dvt_12509_christmas_vendors_20261011.py` khẳng định
  3 × nguồn NPC/mặt hàng và chống sửa nhầm.
- 3 kiểm thử mới, 7 kiểm thử tổng và 3 kiểm thử 12158 trước đó
  PASS trên Windows.
- Có vị trí điều hướng tăng từ **5721 lên 5724**,
  điều kiện không có vị trí giảm **91 → 88**.
  Chỉ còn **2 bước** có yêu cầu thu thập nhưng không có
  bất kỳ waypoint: `10852/1` (thỏ Ngọc nhiều NPC) và
  `12162/1` (30 nồi đất chưa có nguồn rơi xác thực).

Chưa thể kết luận tự đi bộ qua mọi map, mua thành công hay chuyển
bước đúng chỉ từ kiểm thử data. Cần bật client staging sau khi vá,
bấm 3 mục tiêu riêng và xác nhận thực tế.

Tiếp tục ưu tiên hoàn thành lỗi nhiệm vụ, chưa quay lại
Gia Viên, Định hôn hay nhận con.

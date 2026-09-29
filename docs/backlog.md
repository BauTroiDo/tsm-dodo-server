# Danh sách hệ thống chờ phát triển (Backlog)

> Chuẩn kiểm kê: client game mobile `protocal.lua` tổng cộng 107 mainKind.
> Hiện trạng server: 37 đã có C→S Handler, khoảng 7 thuần S→C đã có đầu gửi, khoảng 63 hoàn toàn chưa cài đặt.
> Ngày cập nhật: 2026-06-12 (commit kiểm kê: d090488)

## Đề xuất thứ tự ưu tiên

Lấy "người chơi vào game là sẽ gặp" làm chuẩn:

1. **Tuyến nuôi dưỡng tướng** (74 cường hóa / 44 chuyển sinh — khớp nối gần nhất với PetSystem hiện có)
2. **Phó bản** (47 — vòng lặp game cốt lõi)
3. **Quân đoàn** (39 — quy mô lớn nhất, nền tảng xã hội)
4. **Hoạt động/phúc lợi** (36 + 87 + 80 — tính năng giữ chân người chơi)
5. Nhóm mini game và battle royale để sau cùng

---

## Hệ thống chưa cài đặt (theo nhóm)

### Hệ thống cốt lõi cỡ lớn

| mainKind | Hệ thống | Số gói con | Ghi chú |
|---|---|---|---|
| 39 | Quân đoàn (công hội) | 71 | Hệ thống đơn lẻ lớn nhất toàn dự án |
| 37 | Đoàn P | 6 | Phụ thuộc quân đoàn |
| 41 | Công thành chiến | 23 | Phụ thuộc quân đoàn |
| 43 | Đồng minh | 8 | Phụ thuộc quân đoàn |
| 47 | Phó bản | 21 | Vòng lặp cốt lõi |
| 97 | Phó bản giới hạn thời gian | 1 | Phụ thuộc phó bản |
| 63 | Hệ thống chiến dịch | 15 | |
| 48 | Tầng địa vĩnh hằng | 4 | Phó bản kiểu leo tháp |
| 36 | Giao diện khởi động SERVER (hoạt động) | 25 | Lối vào UI hoạt động/nạp tiền |
| 35 | Tài khoản (nạp tiền/thẻ điểm) | 13 | S:035-005 đã cài đặt |
| 87 | Phúc lợi (điểm danh/gói quà) | 10 | |
| 45 | Hệ thống kết hôn | 16 | |
| 110 | Battle Royale | 14 | |
| 95 | Album sưu tập | 11 | |
| 66 | Quy đổi quà ảo | 10 | |

### Tuyến nuôi dưỡng tướng

| mainKind | Hệ thống | Số gói tin con |
|---|---|---|
| 74 | Tăng cường tướng | 2 |
| 44 | Chuyển sinh tướng | 3 |
| 69 | Tướng thế hệ hai | 11 |
| 76 | Kéo EXP tướng | 5 |
| 112 | Hệ thống khế ước | 2 |
| 121 | Trang bị tướng (trang bị/tháo) | 8 |
| 42 | Đặt lại thuộc tính kỹ năng | 4 |

### Chiến đấu nâng cao

| mainKind | Hệ thống | Số gói tin con |
|---|---|---|
| 53 | Quân sư hồi MP trong chiến đấu | 18 |
| 51 | Dùng vật phẩm trong chiến đấu | 1 |
| 52 | Đổi chỉ số tức thì trong chiến đấu | 2 |
| 54 | Thông tin Buff bổ sung | 1 |

### PVP / Chiến sự kiện

| mainKind | Hệ thống | Số gói tin con |
|---|---|---|
| 73 | Chiến Boss thế giới | 4 |
| 99 | Đối chiến dữ liệu (PVP) | 6 |
| 64 | Hệ thống thủy chiến | 3 |
| 70 | Đua thuyền rồng | 8 |
| 61 | Xổ số | 10 |

### Nhóm mini-game cá cược

| mainKind | Hệ thống | Số gói tin con |
|---|---|---|
| 57 | Trò chơi nhỏ | 3 |
| 58 | So lớn nhỏ | 1 |
| 59 | Xúc xắc | 3 |
| 60 | Bàn bài | 4 |
| 67 | Bùa đổi vận đổi tên | 2 |
| 72 | Tiểu Mary | 6 |
| 89 | Lò luyện | 3 |
| 90 | Thẻ quy đổi | 2 |
| 91 | Khung cửu cung | 4 |
| 92 | Trò chọc lỗ | 3 |
| 93 | Cờ tỷ phú | 2 |
| 98 | prize | 4 |

### Đời sống / Vận hành

| mainKind | Hệ thống | Số gói tin con |
|---|---|---|
| 105 | Tặng quà | 9 |
| 106 | Nhà nhỏ (gia viên) | 14 |
| 107 | Tinh linh nhỏ | 6 |
| 96 | Đấu giá | 6 |
| 75 | Hệ thống VIP | 2 |
| 78 | Mệnh bàn thất tinh | 1 |
| 80 | Thưởng đăng nhập hằng ngày | 2 |
| 71 | Chống nghiện | 2 |
| 46 | Di dân | 3 |
| 55 | Mã quán net | 2 |
| 123 | Bí bảo | 3 |
| 124 | Mẫu vận hành | 8 |
| 126 | Bảng xếp hạng theo nhóm | 2 |
| 199 | Trung tâm chăm sóc khách hàng | 2 |
| 16 | Đố vui trí tuệ (GM) | 26 |
| 101 | SERVER xóa dữ liệu | 3 |
| 56 / 62 / 77 | (protocal.lua không có tiêu đề) | 1/2/6 |

---

## Đã cài đặt nhưng bên trong vẫn còn thiếu sót

- **mainKind=23 vật phẩm**: client có 111 gói tin con, ItemHandler chỉ phủ một phần (chưa làm tăng cường/kảm nạm/chuyên vũ/thạch linh)
- **UISell 介消 (84)**: trong 69 sellId đã cài đặt 6 (1/2/3/7/8/56), còn lại trả về "không sử dụng được"
  - Chờ nối: 4 không gian tinh thạch hộp cơ quan, 5/6/9 liên quan phái đi, 52 không gian bình EXP, 53/61 nhóm giới hạn mua (cần reset sang ngày mới)
- **TODO trong code khoảng 41 chỗ**: tập trung ở BattleManageHandler(11), TradeHandler(7), ChatHandler(4)
- **Giao diện nạp tiền S:035-006** chưa cài đặt

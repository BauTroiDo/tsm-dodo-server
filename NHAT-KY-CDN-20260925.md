# Nhật ký sửa lỗi CDN — 25/09/2026

Phạm vi xác nhận: lỗi cập nhật SaveDataError/Disk full và lỗi nạp bundle sau cập nhật. Client đã tới màn hình đăng nhập lúc khoảng 02:16; chưa khẳng định toàn bộ nhiệm vụ/gameplay hoặc mọi phiên bản client đều tương thích.

Nguyên nhân được đối chiếu bằng mã nguồn, file và log client:
1. Bộ nén Python luôn gắn cờ compressed, kể cả khi dữ liệu nén dài hơn dữ liệu gốc. LZ4Stream của client từ chối khối này. Element02 dừng ở 6.000.000 byte. Sửa thuật toán để khối không nén được lưu nguyên bản với cờ phù hợp; bộ đọc từ chối file cắt cụt và kích thước không hợp lệ.
2. Android/Resources chứa UnityFS đã giải nén lấy từ client, nhưng kênh tải yêu cầu lớp LZ4Stream bên ngoài. Log chứng minh LZ4Stream.AcquireNextChunk báo "Chunks with multiple passes are not supported"; tải lại vẫn tạo 7 file rỗng. Đã đóng đúng lớp vận chuyển cho 2.409 bundle, giữ nguyên dữ liệu UnityFS bên trong.
3. Manifest có CRC cũ của font, common0_bod040, common0_bod152; 2 bundle có kích thước cũ. Đã tính lại CRC dữ liệu UnityFS giải nén và kích thước thực.

Thay đổi trên máy chủ C:\Server-Game\tsm_dodo:
- lz4_packed.py: sửa bộ đóng/đọc LZ4 dùng bởi dat_packer và công cụ quản trị. Nếu công cụ quản trị đang mở, mở lại công cụ trước lần đóng gói tiếp theo để nạp module mới; không cần khởi động lại game server.
- ResourcesPacked: sửa Element02, jmg16, jmg33, jmg38 và hai manifest liên quan. Nội dung giải nén/MD5 không đổi.
- Android/Resources: đóng lớp LZ4 cho toàn bộ 2.409 bundle, sửa metadata ResourceList.dat.
- cdn_server.py: sửa đường dẫn thư mục không tồn tại 2..4.76 thành 2.4.76; không đổi phiên bản GAME_VERSION.
- prepare_unity_cdn_20260925.py: kiểm tra/chuẩn hóa bundle và manifest trước khi xuất bản; kiểm tra tất cả đầu vào trước khi ghi và sao lưu file thay đổi. Chạy tại thư mục máy chủ: python prepare_unity_cdn_20260925.py
- unity_integrity.py: kiểm tra CRC, kích thước và lớp nén của toàn bộ bundle.
- MuChecksumTool: không chấp nhận khối LZ4 có kích thước nén lớn hơn khối gốc. Build Release thành công, các kiểm thử chặn gói lỗi thực tế đều đạt.

Kiểm chứng:
- Quét lại mọi manifest 4 cột trong ResourcesPacked: không còn lỗi block, MD5, dung lượng.
- Quét 2.409 bundle Unity: không còn lỗi lớp vận chuyển, CRC hoặc dung lượng.
- Kiểm thử nén/giải nén: dữ liệu không nén được, nhiều block, hai chế độ nén, các gói gốc, header cắt cụt và gói lỗi thực tế.
- Client tải CDN thật: giải nén tới jmg43; common0 đầy đủ 11.258.096 byte, font 94.475 byte; tới màn hình đăng nhập.
- Chỉ vô hiệu hóa thông tin cache của 7 bundle hỏng để client tải lại; bản cache và file rỗng được giữ ở /sdcard/TS-lz4-root-backup-0207. Không xóa dữ liệu tài khoản/nhân vật.

Sao lưu trên máy chủ:
- backups\lz4-root-fix-20260925-020704
- backups\unity-transport-20260925-021419

Lưu ý cho lần cập nhật sau: dữ liệu lấy từ cache client không phải luôn là gói CDN. Giữ bản raw riêng; chuẩn hóa bằng công cụ trên rồi chạy kiểm tra trước khi sử dụng. Không nhập gói raw UnityFS trực tiếp trở lại CDN.

Nguồn định dạng: https://github.com/MiloszKrajewski/lz4net/blob/master/src/LZ4/LZ4Stream.cs

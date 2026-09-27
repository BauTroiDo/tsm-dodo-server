# Kiểm tra cấu hình đăng nhập — 25/09/2026

Đã xác nhận và sửa lỗi định tuyến: Network.Initialize trong Lua/Logic/Network.lua của CDN đặt game server là 117.18.127.169:6613, trong khi CDN và server cần kiểm thử là 192.168.1.69. Log máy chủ nội bộ không ghi nhận các lần đăng nhập trong khoảng thời gian người dùng báo lỗi. Đã sửa về 192.168.1.69:6613, mã hóa lại và cập nhật MD5 LuaScripts.dat.

GustLogin.dat chỉ là cờ hiển thị nút Guest: chuỗi "1" bật, giá trị khác hoặc thiếu tắt. Vì vậy lỗi 404 không phải nguyên nhân chặn đăng nhập ID/mật khẩu. Đã thêm nội dung "0", giữ nguyên trạng thái tắt Guest, trả HTTP 200.

Kiểm chứng: GET /tsr/2.7/GustLogin.dat trả 200 và đúng một byte 0. GET Network.lua trả MD5 FBCC4D129636DF3EE695A900674CC3E3. Log Android lúc 02:28:06 xác nhận tải lại Network.lua. Không đổi cơ chế xác thực hoặc trả thành công giả. Chưa xác nhận đăng nhập tài khoản thành công; chờ người dùng kiểm thử.

Sao lưu: C:\Server-Game\tsm_dodo\backups\login-route-20260925-022803.

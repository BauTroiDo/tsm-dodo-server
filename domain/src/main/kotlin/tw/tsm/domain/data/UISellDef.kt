package tw.tsm.domain.data

/**
 * Định nghĩa tiêu thụ trên UI (介消) — tương ứng `UISell_C.dat` / `UISellData.lua`
 *
 * 介消 = cổng tiêu thụ nhỏ tích hợp sẵn trong các UI của client (mở rộng số ô, số lần mua, hoàn tất ngay),
 * `UISell.Launch(sellId)` kích hoạt truy vấn C:084-001 → sử dụng C:084-002.
 *
 * @property rolecountId chỉ mục RoleCount của số lần mua giới hạn (0 = hiển thị mua không giới hạn)
 * @property moneyStringId ID chuỗi xác nhận tiêu tiền (TextData, 0 = không có)
 * @property itemStringId ID chuỗi xác nhận tiêu vật phẩm (TextData, 0 = không có → không hỗ trợ đường dẫn vật phẩm)
 * @property customStringId ID chuỗi thông báo thất bại tùy chỉnh (dùng cho nguyên nhân lỗi 11, 0 = không có)
 */
data class UISellDef(
    val rolecountId: Int,
    val moneyStringId: Long,
    val itemStringId: Long,
    val customStringId: Long
)

/**
 * Định nghĩa công thức tiêu thụ trên UI — tương ứng `UISell_Formula_C.dat`
 *
 * Client không đọc file này (DataManager không có ReadData), đây là bảng phí phía server.
 * Định dạng (reverse từ binary): count(4B) + mỗi bản ghi 29B = id(U16) + kind(U8) + value(U32) + params(11×U16).
 *
 * Ngữ nghĩa đã xác nhận (đối chiếu chuỗi UI và hành vi client):
 * - sellId=7 dịch chuyển ký ức: kind=1, value=2 → không có bùa dịch chuyển thì tốn 2 nguyên bảo để đi
 * - sellId=8 mở rộng điểm dịch chuyển ký ức: kind=6, value=59, params=[59,59,59,59] → mỗi ô 1~5 giá 59 nguyên bảo
 * - Đặc điểm chung của kind=6 là 'bảng giá tăng dần' (value=giá lần 1, params=giá các lần sau)
 *
 * TODO: ngữ nghĩa các kind còn lại (12/14/15/16/17/18/19/20) chưa xác nhận đầy đủ, sẽ kiểm chứng dần khi cài đặt từng sellId
 *
 * @property kind loại công thức (ngữ nghĩa xem phía trên)
 * @property value giá trị cơ bản (phần lớn = giá lần đầu, nguyên bảo)
 * @property params danh sách tham số (11 cột, giá tăng dần hoặc tham số công thức)
 */
data class UISellFormulaDef(
    val kind: Int,
    val value: Long,
    val params: List<Int>
)

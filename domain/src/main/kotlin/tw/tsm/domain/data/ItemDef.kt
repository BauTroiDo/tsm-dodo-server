package tw.tsm.domain.data

/**
 * Định nghĩa vật phẩm — tương ứng `ItemData.New(reader)`
 *
 * Nguồn: `Item_C.dat`
 * Thứ tự trường đối chiếu nghiêm ngặt dòng 300-385 của ItemData.lua (tổng cộng 54 trường + name + description).
 */
data class ItemDef(
    /** [1] Tên (UTF-16LE)*/
    val name: String,
    /** [2] Loại (EItemKind)*/
    val kind: Int,
    /** [4] ID biểu tượng*/
    val iconId: Int,
    /** [5] Số hình thân nam*/
    val malePicId: Int,
    /** [6] Số hình thân nữ*/
    val femalePicId: Int,
    /** [7-9] Thuộc tính 1 (kind, attrItem, value)*/
    val attr1Kind: Int,
    val attr1Item: Int,
    val attr1Value: Int,
    /** [10-12] Thuộc tính 2 (kind, attrItem, value)*/
    val attr2Kind: Int,
    val attr2Item: Int,
    val attr2Value: Int,
    /** [13] Chất liệu*/
    val material: Int,
    /** [14] Cấp*/
    val level: Int,
    /** [15] Kiểu trang bị (EItemFitType)*/
    val fitType: Int,
    /** [16] Tác dụng đặc biệt*/
    val specialAbility: Int,
    /** [17-24] Bảng màu (nam 4 + nữ 4)*/
    val maleColorTints: IntArray,
    val femaleColorTints: IntArray,
    /** [25] Có mở hay không (0=mở, 1=không)*/
    val openUsed: Int,
    /** [26] Giới hạn cấp*/
    val needLv: Int,
    /** [27] Giá*/
    val price: Int,
    /** [28] Giá bán*/
    val sellPrice: Int,
    /** [29] Giới hạn giới tính (1=nam, 2=nữ)*/
    val gender: Int,
    /** [30] Giới hạn xử lý*/
    val restrict: Int,
    /** [31] Số lượng phát hành*/
    val threshold: Int,
    /** [32] Thuộc tính (0=không, 1-8)*/
    val element: Int,
    /** [33] Giá trị thuộc tính*/
    val elementValue: Int,
    /** [34] ID kỹ năng tham khảo*/
    val skillLink: Int,
    /** [35] Giới hạn chuyển sinh*/
    val turn: Int,
    /** [36] Điểm đổi chiến công*/
    val giftDot: Int,
    /** [37] Dự phòng 2*/
    val spare2: Int,
    /** [38] Dự phòng 3*/
    val spare3: Int,
    /** [39] Giới hạn xử lý 2*/
    val restrict2: Int,
    /** [40] ID bộ trang bị*/
    val suitId: Int,
    /** [41] Dự phòng 5*/
    val spare5: Int,
    /** [42] Sử dụng trực tiếp (0=không, 1=có)*/
    val directUse: Int,
    /** [43] Chỉ mục RoleCount*/
    val roleCountIndex: Int,
    /** [44] Giá trị RoleCount*/
    val roleCountValue: Int,
    /** [45] Sắp xếp*/
    val sort: Int,
    /** [46] Công tắc trang bị nam*/
    val maleEquipSwitch: Int,
    /** [47] Công tắc trang bị nữ*/
    val femaleEquipSwitch: Int,
    /** [48] Lý do đóng nút sử dụng*/
    val btnState: Int,
    /** [49] Hiển thị độ bền*/
    val durable: Int,
    /** [50] Loại lò luyện*/
    val furnaceKind: Int,
    /** [51] Số lượng mảnh vỡ lò luyện*/
    val furnaceCount: Long,
    /** [52] Chất lượng (0=trắng, 1=xanh lá, 2=xanh dương, 3=tím, 4=đỏ)*/
    val quality: Int,
    /** [53] Trang chính đấu giá*/
    val auctionTag: Int,
    /** [54] Phân loại con đấu giá*/
    val auctionSubTag: Int,
    /** Mô tả (UTF-16LE)*/
    val description: String
)

package tw.tsm.domain.data

/**
 * Định nghĩa thành tựu — tương ứng `AchievementData.lua`
 *
 * Nguồn: `AchievementData_C.dat`
 * Mỗi bản ghi dài cố định 37 bytes.
 *
 * name / content / channelContent là ID bảng chuỗi, chỉ dành cho UI, server không cần lưu.
 *
 * @param id             ID thành tựu
 * @param mainTag        phân loại chính
 * @param subTag         phân loại phụ
 * @param sortId         sắp xếp
 * @param showKind       loại hiển thị
 * @param score          điểm tích luỹ thành tựu
 * @param conditionKind  loại điều kiện (tương ứng liệt kê ECondition)
 * @param conditionKindValue tham số phụ của điều kiện (như chỉ mục RoleCount, số lần chuyển sinh)
 * @param conditionOpr   toán tử (1=bằng, 2=lớn hơn, 3=lớn hơn hoặc bằng, 4=nhỏ hơn, 5=nhỏ hơn hoặc bằng, 6=khác)
 * @param conditionValue giá trị cần đạt
 * @param itemId         ID vật phẩm thưởng (0 = không thưởng)
 * @param itemCount      số lượng vật phẩm thưởng
 * @param completeFlag   ID cờ vĩnh viễn khi hoàn thành (BitFlag)
 * @param getFlag        ID cờ vĩnh viễn khi nhận thưởng (BitFlag)
 * @param channel        kênh phát rộng (tương ứng EChannel)
 * @param channelContent nội dung phát rộng (ID bảng chuỗi)
 */
data class AchievementDef(
    val id: Int,
    val mainTag: Int,
    val subTag: Int,
    val sortId: Int,
    val showKind: Int,
    val score: Int,
    val conditionKind: Int,
    val conditionKindValue: Long,
    val conditionOpr: Int,
    val conditionValue: Long,
    val itemId: Int,
    val itemCount: Int,
    val completeFlag: Int,
    val getFlag: Int,
    val channel: Int,
    val channelContent: Long,
)

package tw.tsm.domain.data

/**
 * Định nghĩa kỹ năng — tương ứng `SkillData.New(reader)`
 *
 * Nguồn: `Skill_C.dat`
 * Thứ tự trường đối chiếu nghiêm ngặt với dòng 4-63 của SkillData.lua.
 */
data class SkillDef(
    /** Tên kỹ năng (UTF-16LE)*/
    val name: String,
    /** chủng loại*/
    val kind: Int,
    /** tiêu hao SP*/
    val requireSp: Int,
    /** nguyên tố (1=đất, 2=nước, 3=lửa, 4=gió)*/
    val element: Int,
    /** chỉ số (ảnh hưởng thuộc tính)*/
    val numerical: Long,
    /** loại thuộc tính*/
    val attribute: Int,
    /** Cấp*/
    val level: Int,
    /** cách đánh (0=đơn mục tiêu không dịch chuyển, 1=đánh nhiều mục tiêu, 2=có dịch chuyển)*/
    val fightWay: Int,
    /** phạm vi (1=một người, 2=hai người cùng cột, 3=ba người cùng hàng, 4=ba người kiểu nhảy, 5=năm người hình chữ thập, 6=sáu người, 7=năm người cùng hàng, 8=toàn thể)*/
    val fightArea: Int,
    /** hiệp (0=none, 1-3=số hiệp bị định thân, 4=duy trì ba hiệp)*/
    val round: Int,
    /** số giây*/
    val spendSecond: Int,
    /** trạng thái (0=bình thường, 1=đóng băng, 2=lốc xoáy, 3=yêu cây)*/
    val hitStatus: Int,
    /** tấn công chia mấy đòn*/
    val howMuchTimes: Int,
    /** giới hạn cấp độ (từ cấp này trở lên mới học được)*/
    val limitLv: Int,
    /** số điểm cần để học lần đầu*/
    val learnPoint: Int,
    /** số điểm cần cho mỗi cấp*/
    val levelUpPoint: Int,
    /** cấp độ tối đa*/
    val maxLv: Int,
    /** danh sách ID kỹ năng tiền đề*/
    val preSkills: List<Int>,
    /** loại tấn công*/
    val atkKind: Int,
    /** loại chuyển sinh (0=chưa chuyển sinh, 1=đã chuyển sinh)*/
    val turnKind: Int,
    /** giới hạn học*/
    val learnLimit: Int,
    /** giới hạn sử dụng*/
    val useLimit: Int,
    /** loại tăng trưởng của cách đánh*/
    val fightWayGrowType: Int,
    /** mô tả kỹ năng (UTF-16LE)*/
    val description: String
)

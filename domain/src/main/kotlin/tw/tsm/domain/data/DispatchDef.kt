package tw.tsm.domain.data

/**
 * Định nghĩa nhiệm vụ phái đi
 *
 * Nguồn: DispatchData.lua → DispatchData.New(reader)
 * Định dạng .dat: mainKind(1) + subKind(1) + minLv(1) + awardId(2) + expKind(1) + exp(4)
 *
 * @param minLv cấp độ tối thiểu
 * @param awardId chỉ mục thưởng
 * @param expKind loại kinh nghiệm (0=giá trị cố định, 1=phần nghìn)
 * @param exp kinh nghiệm khi phái đi
 */
data class DispatchDef(
    val minLv: Int,
    val awardId: Int,
    val expKind: Int,
    val exp: Long
)

/**
 * Định nghĩa hiệu ứng cộng thưởng khi phái đi
 *
 * Nguồn: DispatchData.lua → DispatchBonusData.New(reader)
 * Định dạng .dat: id(1) + conditionKind(1) + conditionValue(1) + effectIndex(1) + effectKind(1) + effectValue(2) + effectContent(4)
 *
 * @param conditionKind loại điều kiện (1=phe đảng, 2=thuộc tính)
 * @param conditionValue giá trị điều kiện
 * @param effectIndex chỉ mục thưởng bị hiệu ứng ảnh hưởng
 * @param effectKind loại hiệu ứng
 * @param effectValue giá trị hiệu ứng
 * @param effectContent ID chuỗi mô tả hiệu ứng
 */
data class DispatchBonusDef(
    val conditionKind: Int,
    val conditionValue: Int,
    val effectIndex: Int,
    val effectKind: Int,
    val effectValue: Int,
    val effectContent: Long
)

/**
 * Dữ liệu đang phái đi (dùng để ghi DB / truyền gói tin)
 */
data class DispatchSaveData(
    val slotIndex: Int,
    val startTime: Double,
    val endTime: Double,
    val dispatchKind: Int,
    val innPetIndex: Int,
    val effectIndex1: Int = 0,
    val effectIndex2: Int = 0
)

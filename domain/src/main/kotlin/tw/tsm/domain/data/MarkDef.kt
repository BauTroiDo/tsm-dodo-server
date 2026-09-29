package tw.tsm.domain.data

/**
 * Định nghĩa cờ đánh dấu nhiệm vụ — tương ứng MarkData.ReadInfo() của Mark_C.dat
 *
 * Nguồn: `Mark_C.dat`
 * Server chủ yếu cần trường [bitId] và [kind]; name/description dùng cho log.
 *
 * @param name        tên nhiệm vụ (UTF-16LE)
 * @param kind        loại (0:không 1:dòng chính 2:dòng phụ 3:hướng dẫn 4:phó bản đơn 5:phó bản nhóm 6:khác 7:làm mới hằng ngày 8:làm mới hằng ngày không hiển thị)
 * @param bitId       chỉ số bit của cờ hoàn thành (0 = không có cờ hoàn thành)
 * @param gainWay     cách nhận (0:người kích hoạt 1:người kích hoạt và thành viên tổ đội)
 * @param description mô tả (UTF-16LE)
 */
data class MarkDef(
    val name: String,
    val kind: Int,
    val bitId: Int,
    val gainWay: Int,
    val description: String
)

/**
 * Kết quả giảm bước nhiệm vụ — giá trị trả về của [tw.tsm.gamelogic.systems.QuestSystem.decMissionStep]
 *
 * Dùng để thông báo cho nơi gọi biết nhiệm vụ có hoàn thành do bước về 0 hay không, cùng bitId cờ hoàn thành tương ứng.
 *
 * @param completed nhiệm vụ đã hoàn thành hay chưa (bước về 0)
 * @param bitId     bitId cờ hoàn thành (0 = không cần đặt cờ)
 */
data class DecMissionResult(
    val completed: Boolean,
    val bitId: Int
)

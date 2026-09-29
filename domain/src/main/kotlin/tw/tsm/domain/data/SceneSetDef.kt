package tw.tsm.domain.data

/**
 * định nghĩa cấu hình cảnh — tương ứng `SceneData.ReadSceneSetData(reader)`
 *
 * nguồn: `SceneSet_C.dat`
 */
data class SceneSetDef(
    /** ID tên cảnh (dùng để đối chiếu với string.Get)*/
    val nameId: Long,
    /** loại Dungeon (0=không, 1=tĩnh, 2=động)*/
    val kind: Int,
    /** số người tối đa*/
    val maxPlayer: Int,
    /** điều kiện giới hạn 1 (không được vứt bỏ / dùng vật phẩm / dùng cuộn trục / dùng phù chú / giao dịch an toàn / giao dịch tướng / mở sạp / chuyển phát nhanh)*/
    val limit1: Int,
    /** điều kiện giới hạn 2 (không được PK NPC / PK người chơi / tham chiến / cưỡi ngựa / hộp cơ quan / đi theo tướng / lập tổ đội / cưỡng chế PK)*/
    val limit2: Int,
    /** hiệu ứng cảnh (0=không, 1=lá rụng, 2=tuyết rơi, 4=bão kèm sấm, 8=bóng tối, 16=sao băng, 32=mưa)*/
    val effect: Int,
    /** cấp độ tối thiểu khuyên dùng*/
    val minLv: Int,
    /** cấp độ tối đa khuyên dùng*/
    val maxLv: Int,
    /** điều kiện hiển thị (0=không giới hạn, 1=không hiển thị tướng của mình, 2=không hiển thị tướng người khác, 4=không hiển thị người chơi khác)*/
    val viewSetting: Int,
    /** ID con*/
    val subId: Int,
    /** điều kiện giới hạn 3 (0=không giới hạn, 1=chết không mất EXP)*/
    val limit3: Int
)

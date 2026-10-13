package tw.tsm.domain.valueobjects

import java.util.UUID

/**
 * Đối tượng giá trị dữ liệu tướng
 *
 * Tương ứng định dạng gửi xuống khi đăng nhập của S:015-008.
 * Nguồn: Role.lua FollowNpcAppear()
 */
data class FollowNpcData(
    /** Chỉ số đi theo (1-4)*/
    val slotIndex: Int,
    /** NPC ID */
    val npcId: Int,
    /** Loại lưu trữ: 1=mang theo người, 2=xe ngựa, 3=lữ quán, 4=kho tướng*/
    val storageType: Int = 1,
    /** Tên võ tướng; S:015-008 của client TSNPCVN đọc bằng UTF-16LE. */
    val name: String = "",
    /** Cấp*/
    val level: Int = 1,
    /** Kinh nghiệm*/
    val exp: Long = 0,
    /** Lượng máu*/
    val hp: Long = 0,
    /** Ma lực*/
    val sp: Int = 0,
    /** Tấn công*/
    val atk: Int = 0,
    /** Phòng thủ*/
    val def: Int = 0,
    /** Nhanh nhẹn*/
    val agi: Int = 0,
    /** Trí tuệ*/
    val intelligence: Int = 0,
    /** Mức tăng HP*/
    val hpx: Int = 0,
    /** Mức tăng SP*/
    val spx: Int = 0,
    /** Số lần tử trận*/
    val deathCount: Int = 0,
    /** Lòng trung thành*/
    val loyalty: Int = 60,
    /** Điểm kỹ năng*/
    val skillPoint: Int = 0,
    /** Cấp kỹ năng 1*/
    val skill1Lv: Int = 0,
    /** Cấp kỹ năng 2*/
    val skill2Lv: Int = 0,
    /** Cấp kỹ năng 3*/
    val skill3Lv: Int = 0,
    /** Loại phát triển: 0=không thể phát triển, 1=có thể phát triển*/
    val canGrow: Int = 1,
    /** ID hồn ngọc*/
    val soulId: Int = 0,
    /** Cấp cường hóa*/
    val upgradeLv: Int = 0,
    /** Số lần dùng đan thăng hoa*/
    val ascendPillCount: Int = 0,
    /** Có dùng được kỹ năng đặc hữu hay không*/
    val canUseSpecialSkill: Boolean = false,
    /** Số lượng đan HP*/
    val hpPillCount: Int = 0,
    /** Số lượng đan SP*/
    val spPillCount: Int = 0,
    /** Cấp kỹ năng 4 (dùng để tuần tự hoá kho tướng)*/
    val skill4Lv: Int = 0,
    /** Mức tăng ma lực (dùng để tuần tự hoá kho tướng)*/
    val conjureGrow: Int = 0,
    /** Số lần chuyển sinh*/
    val turnKind: Int = 0,
    /** Trạng thái sống sót (0=bình thường, 1=tử trận)*/
    val surplus: Int = 0,
    /** Mốc thời gian nâng cấp (OLE Date)*/
    val lvUpTime: Double = 0.0,
    /**
     * Marker dữ liệu pet đã dùng seed LV1 đúng chuẩn DVT.
     *
     * Cột này chỉ dùng để migrate dữ liệu legacy được tạo trước bản sửa pet lifecycle.
     * Pet mới mặc định true; record cũ sau khi thêm cột DB sẽ mặc định false.
     */
    val seedFixed: Boolean = true,
    /** Multi-pet extension: this carried pet is selected for battle deployment. */
    val isDeployed: Boolean = false,
    /**
     * Trang bị tướng (fitType 1 mũ/2 áo/3 vũ khí/4 hộ uyển/5 giày/6 đặc biệt → ThingData)
     *
     * Dùng chung mô hình trang bị 6 ô với người chơi (Item.lua MaxEquip=6, EItemFitType).
     * Trang bị đi theo bản thân tướng: vào xe ngựa/lữ quán/kho không cởi trang bị.
     */
    val equips: Map<Int, ThingData> = emptyMap(),
    /**
     * Server-only immutable identity of this *individual* general, independent
     * of npcId (template), slotIndex (roster position) and database row id.
     * New captures get a fresh UUID; all moves/rebirth/trade must retain it.
     * Not sent as part of the original DVT S015 binary packet.
     */
    val instanceId: String = UUID.randomUUID().toString(),
)

package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.EquipBonusData
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Quản lý thuộc tính runtime của người chơi
 *
 * Theo dõi các thuộc tính liên quan đến chiến đấu như HP/SP/maxHP/maxSP thời gian thực của người chơi đang online.
 * Được khởi tạo từ PlayerDataDto khi đăng nhập, và xóa khi đăng xuất.
 *
 * Các thuộc tính này sẽ được đọc và ghi bởi hệ thống sử dụng vật phẩm, hệ thống chiến đấu, hệ thống buff, v.v.
 */
class PlayerStateManager {

    /** Trạng thái người chơi online (characterId → PlayerState) */
    private val states = ConcurrentHashMap<Long, PlayerState>()

    /**
     * Khởi tạo trạng thái người chơi (được gọi khi chọn nhân vật vào game)
     */
    fun initPlayer(
        characterId: Long,
        hp: Long,
        sp: Int,
        maxHp: Long,
        maxSp: Int,
        level: Int,
        atk: Int,
        def: Int,
        agi: Int,
        intelligence: Int,
        hpx: Int,
        spx: Int,
        element: Int,
        turn: Int = 0,
        career: Int = 0,
        attrPoint: Int = 0,
        skillPoint: Int = 0,
        equipBonus: EquipBonusData = EquipBonusData.EMPTY,
        turn3Int: Int = 0,
        turn3Atk: Int = 0,
        turn3Def: Int = 0,
        turn3Agi: Int = 0,
        turn3Hpx: Int = 0,
        turn3Spx: Int = 0
    ) {
        states[characterId] = PlayerState(
            hp = hp, sp = sp, maxHp = maxHp, maxSp = maxSp,
            level = level, atk = atk, def = def, agi = agi,
            intelligence = intelligence, hpx = hpx, spx = spx,
            element = element, turn = turn, career = career,
            attrPoint = attrPoint, skillPoint = skillPoint,
            equipMaxHp = equipBonus.equipMaxHp, equipMaxSp = equipBonus.equipMaxSp,
            equipAtk = equipBonus.equipAtk, equipDef = equipBonus.equipDef,
            equipInt = equipBonus.equipInt, equipAgi = equipBonus.equipAgi,
            equipHpx = equipBonus.equipHpx, equipSpx = equipBonus.equipSpx,
            turn3Int = turn3Int, turn3Atk = turn3Atk, turn3Def = turn3Def,
            turn3Agi = turn3Agi, turn3Hpx = turn3Hpx, turn3Spx = turn3Spx
        )
        logger.debug { "Khởi tạo trạng thái người chơi: characterId=$characterId hp=$hp/$maxHp sp=$sp/$maxSp lv=$level equipBonus=$equipBonus" }
    }

    /** Lấy trạng thái người chơi (trả về null nếu không tồn tại) */
    fun getState(characterId: Long): PlayerState? = states[characterId]

    /** Xóa trạng thái người chơi (được gọi khi đăng xuất/ngắt kết nối) */
    fun removePlayer(characterId: Long) {
        states.remove(characterId)
    }

    /**
     * Sửa đổi HP (tự động kẹp giá trị (clamp) trong khoảng 0~maxHp, được bảo vệ bởi synchronized tránh xung đột đồng thời)
     *
     * @return Lượng thay đổi thực tế (dương = hồi phục, âm = mất máu)
     */
    fun modifyHp(characterId: Long, delta: Long): Long {
        val state = states[characterId] ?: return 0
        synchronized(state) {
            val oldHp = state.hp
            state.hp = (state.hp + delta).coerceIn(0, state.maxHp)
            return state.hp - oldHp
        }
    }

    /**
     * Sửa đổi SP (tự động kẹp giá trị trong khoảng 0~maxSp, được bảo vệ bởi synchronized tránh xung đột đồng thời)
     *
     * @return Lượng thay đổi thực tế
     */
    fun modifySp(characterId: Long, delta: Int): Int {
        val state = states[characterId] ?: return 0
        synchronized(state) {
            val oldSp = state.sp
            state.sp = (state.sp + delta).coerceIn(0, state.maxSp)
            return state.sp - oldSp
        }
    }

    /**
     * Thiết lập trực tiếp HP (dùng cho tổng kết trận chiến, được bảo vệ bởi synchronized)
     */
    fun setHp(characterId: Long, hp: Long) {
        val state = states[characterId] ?: return
        synchronized(state) {
            state.hp = hp.coerceIn(0, state.maxHp)
        }
    }

    /**
     * Thiết lập trực tiếp SP (dùng cho tổng kết trận chiến, được bảo vệ bởi synchronized)
     */
    fun setSp(characterId: Long, sp: Int) {
        val state = states[characterId] ?: return
        synchronized(state) {
            state.sp = sp.coerceIn(0, state.maxSp)
        }
    }

    /**
     * Thiết lập HP đầy bình (được bảo vệ bởi synchronized)
     *
     * @return Lượng hồi phục
     */
    fun fullHp(characterId: Long): Long {
        val state = states[characterId] ?: return 0
        synchronized(state) {
            val recovered = state.maxHp - state.hp
            state.hp = state.maxHp
            return recovered
        }
    }

    /**
     * Thiết lập SP đầy bình (được bảo vệ bởi synchronized)
     *
     * @return Lượng hồi phục
     */
    fun fullSp(characterId: Long): Int {
        val state = states[characterId] ?: return 0
        synchronized(state) {
            val recovered = state.maxSp - state.sp
            state.sp = state.maxSp
            return recovered
        }
    }

    /**
     * Cập nhật MaxHP và đồng thời điều chỉnh lại HP (được bảo vệ bởi synchronized)
     *
     * @return Pair(newMaxHp, newHp), trả về null nếu thất bại
     */
    fun updateMaxHp(characterId: Long, newMaxHp: Long): Pair<Long, Long>? {
        val state = states[characterId] ?: return null
        synchronized(state) {
            val oldMaxHp = state.maxHp
            state.maxHp = newMaxHp
            state.hp = (state.hp + (newMaxHp - oldMaxHp)).coerceIn(0, newMaxHp)
            return Pair(state.maxHp, state.hp)
        }
    }

    /**
     * Cập nhật MaxSP và đồng thời điều chỉnh lại SP (được bảo vệ bởi synchronized)
     *
     * @return Pair(newMaxSp, newSp), trả về null nếu thất bại
     */
    fun updateMaxSp(characterId: Long, newMaxSp: Int): Pair<Int, Int>? {
        val state = states[characterId] ?: return null
        synchronized(state) {
            val oldMaxSp = state.maxSp
            state.maxSp = newMaxSp
            state.sp = (state.sp + (newMaxSp - oldMaxSp)).coerceIn(0, newMaxSp)
            return Pair(state.maxSp, state.sp)
        }
    }

    /**
     * Phân bổ điểm thuộc tính — Tiêu hao attrPoint để tăng các chỉ số lục vi được chỉ định
     *
     * @param characterId ID nhân vật
     * @param attrKind Hằng số EAttribute (27-32)
     * @param addPoints Số điểm muốn cộng thêm (phải > 0)
     * @return Kết quả phân bổ điểm, trả về null nếu thất bại
     */
    fun allocateStat(characterId: Long, attrKind: Int, addPoints: Int): StatAllocResult? {
        val state = states[characterId] ?: return null

        // Số điểm phải là số dương
        if (addPoints <= 0) return null

        // Lấy ánh xạ trường tương ứng
        val mapping = ATTR_MAPPINGS[attrKind]
        if (mapping == null) {
            logger.warn { "Phân bổ điểm thất bại: attrKind=$attrKind không hợp lệ" }
            return null
        }

        // synchronized bảo vệ tính nguyên tử đọc-kiểm tra-ghi, ngăn chặn lỗi TOCTOU khi click nhanh liên tục
        synchronized(state) {
            // Điểm còn lại không đủ
            if (state.attrPoint < addPoints) {
                logger.warn { "Phân bổ điểm thất bại: attrPoint=${state.attrPoint} < addPoints=$addPoints" }
                return null
            }

            val oldValue = mapping.getter(state)

            // Giới hạn một mục tối đa 999
            if (oldValue + addPoints > MAX_STAT_POINT) {
                logger.warn { "Phân bổ điểm thất bại: ${mapping.dbColumn} vượt quá giới hạn ($oldValue + $addPoints >$MAX_STAT_POINT)" }
                return null
            }

            // Thực hiện phân bổ điểm
            val newValue = oldValue + addPoints
            mapping.setter(state, newValue)
            state.attrPoint -= addPoints

            logger.info { "Phân bổ điểm thành công: charId=$characterId ${mapping.dbColumn}$oldValue→$newValue attrPoint=${state.attrPoint}" }

            return StatAllocResult(
                dbColumn = mapping.dbColumn,
                newValue = newValue,
                newAttrPoint = state.attrPoint
            )
        }
    }

    /**
     * Cập nhật gia từ trang bị và tính toán lại MaxHP/MaxSP
     *
     * Được gọi sau khi mặc hoặc cởi trang bị. Tham khảo logic `refreshBonus()` của tskero:
     * Ghi vào 8 trường thuộc tính trang bị → Tính toán lại MaxHP/MaxSP → Kẹp giá trị (clamp) HP/SP.
     *
     * @param characterId ID nhân vật
     * @param bonus Tổng giá trị gia tăng từ trang bị đã được tính toán
     * @param statCalc Bộ tính toán thuộc tính (dùng để tính lại MaxHP/MaxSP)
     * @return Kết quả cập nhật (bao gồm MaxHP/MaxSP/HP/SP mới), trả về null nếu thất bại
     */
    fun updateEquipBonus(
        characterId: Long,
        bonus: EquipBonusData,
        statCalc: StatCalculator
    ): EquipBonusUpdateResult? {
        val state = states[characterId] ?: return null
        synchronized(state) {
            // Ghi vào 8 trường gia tăng từ trang bị
            state.equipMaxHp = bonus.equipMaxHp
            state.equipMaxSp = bonus.equipMaxSp
            state.equipAtk = bonus.equipAtk
            state.equipDef = bonus.equipDef
            state.equipInt = bonus.equipInt
            state.equipAgi = bonus.equipAgi
            state.equipHpx = bonus.equipHpx
            state.equipSpx = bonus.equipSpx

            // Tính toán lại MaxHP (hpx đã bao gồm gia tăng từ trang bị)
            val totalHpx = state.hpx + state.equipHpx
            val newMaxHp = statCalc.calculateMaxHp(
                level = state.level, hpx = totalHpx,
                equipMaxHp = state.equipMaxHp, extraHp = 0,
                turn = state.turn, career = state.career, turn3Lv = 0
            )
            val oldMaxHp = state.maxHp
            state.maxHp = newMaxHp
            state.hp = (state.hp + (newMaxHp - oldMaxHp)).coerceIn(0, newMaxHp)

            // Tính toán lại MaxSP
            val totalSpx = state.spx + state.equipSpx
            val newMaxSp = statCalc.calculateMaxSp(
                level = state.level, spx = totalSpx,
                equipMaxSp = state.equipMaxSp, extraSp = 0,
                turn = state.turn, career = state.career, turn3Lv = 0
            )
            val oldMaxSp = state.maxSp
            state.maxSp = newMaxSp
            state.sp = (state.sp + (newMaxSp - oldMaxSp)).coerceIn(0, newMaxSp)

            return EquipBonusUpdateResult(
                bonus = bonus,
                newMaxHp = newMaxHp, newMaxSp = newMaxSp,
                newHp = state.hp, newSp = state.sp
            )
        }
    }

    companion object {
        /** Giới hạn một thuộc tính đơn lẻ (Status.lua dòng 6) */
        const val MAX_STAT_POINT = 999

        /** Ánh xạ từ EAttribute → Trường trong PlayerState & Cột trong DB */
        private val ATTR_MAPPINGS = mapOf(
            EAttribute.INT to AttrMapping("int_", { it.intelligence }, { s, v -> s.intelligence = v }),
            EAttribute.ATK to AttrMapping("str", { it.atk }, { s, v -> s.atk = v }),
            EAttribute.DEF to AttrMapping("vit", { it.def }, { s, v -> s.def = v }),
            EAttribute.AGI to AttrMapping("dex", { it.agi }, { s, v -> s.agi = v }),
            EAttribute.HPX to AttrMapping("spr", { it.hpx }, { s, v -> s.hpx = v }),
            EAttribute.SPX to AttrMapping("men", { it.spx }, { s, v -> s.spx = v })
        )
    }
}

/**
 * Trạng thái runtime của người chơi
 *
 * Theo dõi các thuộc tính thời gian thực trong thời gian online. Tất cả các trường đều có thể thay đổi, được quản lý bởi PlayerStateManager.
 */
data class PlayerState(
    var hp: Long,
    var sp: Int,
    var maxHp: Long,
    var maxSp: Int,
    var level: Int,
    var atk: Int,
    var def: Int,
    var agi: Int,
    var intelligence: Int,
    var hpx: Int,
    var spx: Int,
    var element: Int,
    var turn: Int = 0,
    var career: Int = 0,
    var exp: Long = 0,
    /** Hệ số nhân kinh nghiệm (Mặc định 1.0, đạo cụ Phúc thần có thể làm tăng lên) */
    var expMultiplier: Double = 1.0,
    /** Thời gian hết hạn không gặp địch (System.currentTimeMillis, 0=không hiệu lực) */
    var noEncounterUntil: Long = 0,
    /** Điểm thuộc tính có thể sử dụng */
    var attrPoint: Int = 0,
    /** Điểm kỹ năng có thể sử dụng */
    var skillPoint: Int = 0,
    var turn3Int: Int = 0,
    var turn3Atk: Int = 0,
    var turn3Def: Int = 0,
    var turn3Agi: Int = 0,
    var turn3Hpx: Int = 0,
    var turn3Spx: Int = 0,

    // ── Gia tăng từ trang bị (Tham khảo tskero TSCharacter.atk2/def2/mag2/agi2/hp2/sp2) ──
    /** Max HP được nâng cao bởi trang bị */
    var equipMaxHp: Int = 0,
    /** Max SP được nâng cao bởi trang bị */
    var equipMaxSp: Int = 0,
    /** Điểm công kích gia tăng từ trang bị */
    var equipAtk: Int = 0,
    /** Điểm phòng thủ gia tăng từ trang bị */
    var equipDef: Int = 0,
    /** Điểm trí lực gia tăng từ trang bị */
    var equipInt: Int = 0,
    /** Điểm nhanh nhẹn gia tăng từ trang bị */
    var equipAgi: Int = 0,
    /** Gia tăng HPX từ trang bị */
    var equipHpx: Int = 0,
    /** Gia tăng SPX từ trang bị */
    var equipSpx: Int = 0
)

/**
 * Kết quả phân bổ điểm thuộc tính
 */
data class StatAllocResult(
    /** Tên cột tương ứng trong DB */
    val dbColumn: String,
    /** Giá trị mới sau khi phân bổ */
    val newValue: Int,
    /** Điểm thuộc tính còn lại */
    val newAttrPoint: Int
)

/**
 * Ánh xạ EAttribute → Trường trong PlayerState
 */
data class AttrMapping(
    val dbColumn: String,
    val getter: (PlayerState) -> Int,
    val setter: (PlayerState, Int) -> Unit
)

/**
 * Kết quả cập nhật gia tăng từ trang bị
 *
 * Được trả về sau khi mặc/cởi trang bị, chứa giá trị gia tăng trang bị mới và MaxHP/MaxSP/HP/SP sau khi tính toán lại.
 */
data class EquipBonusUpdateResult(
    /** Tổng giá trị gia tăng từ trang bị */
    val bonus: EquipBonusData,
    /** MaxHP sau khi tính lại */
    val newMaxHp: Long,
    /** MaxSP sau khi tính lại */
    val newMaxSp: Int,
    /** HP sau khi kẹp giá trị (clamp) */
    val newHp: Long,
    /** SP sau khi kẹp giá trị (clamp) */
    val newSp: Int
)
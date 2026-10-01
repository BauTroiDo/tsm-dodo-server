package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.EAttribute
import tw.tsm.domain.interfaces.StatCalculator
import tw.tsm.domain.valueobjects.AstrolabeBonusData
import tw.tsm.domain.valueobjects.EquipBonusData
import tw.tsm.domain.util.DvtPercentMath
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
        astrolabeBonus: AstrolabeBonusData = AstrolabeBonusData.EMPTY,
        turn3Int: Int = 0,
        turn3Atk: Int = 0,
        turn3Def: Int = 0,
        turn3Agi: Int = 0,
        turn3Hpx: Int = 0,
        turn3Spx: Int = 0,
        killNum: Int = 0,
        moral: IntArray = intArrayOf(0, 0, 0, 0, 0),
        sex: Int = 0
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
            astrolabeHpx = astrolabeBonus.hpx,
            astrolabeDef = astrolabeBonus.def,
            astrolabeInt = astrolabeBonus.intelligence,
            astrolabeSpx = astrolabeBonus.spx,
            astrolabeAgi = astrolabeBonus.agi,
            astrolabeAtk = astrolabeBonus.atk,
            atkPercent = equipBonus.atkPercent, defPercent = equipBonus.defPercent,
            hpPercent = equipBonus.hpPercent, intPercent = equipBonus.intPercent,
            crit = equipBonus.crit, critDamage = equipBonus.critDamage,
            dodge = equipBonus.dodge, hit = equipBonus.hit,
            increaseTotalDamage = equipBonus.increaseTotalDamage,
            damageReduction = equipBonus.damageReduction,
            reduceTotalDamage = equipBonus.reduceTotalDamage,
            increaseDamageTakenSelf = equipBonus.increaseDamageTakenSelf,
            turn3Int = turn3Int, turn3Atk = turn3Atk, turn3Def = turn3Def,
            turn3Agi = turn3Agi, turn3Hpx = turn3Hpx, turn3Spx = turn3Spx,
            killNum = killNum,
            moral = moral.copyOf(5),
            sex = sex
        )
        logger.debug { "Khởi tạo trạng thái người chơi: characterId=$characterId hp=$hp/$maxHp sp=$sp/$maxSp lv=$level equipBonus=$equipBonus" }
    }

    /** Lấy trạng thái người chơi (trả về null nếu không tồn tại) */
    fun getState(characterId: Long): PlayerState? = states[characterId]

    /** Xóa trạng thái người chơi (được gọi khi đăng xuất/ngắt kết nối) */
    fun removePlayer(characterId: Long) {
        states.remove(characterId)
    }

    /** Giới tính runtime, dùng cho điều kiện tổ đội/cặp đôi Eve. */
    fun getSex(characterId: Long): Int =
        states[characterId]?.let { state -> synchronized(state) { state.sex } } ?: 0

    /** Điểm diệt địch DVT; Honor hiện được client tính 1:1 từ giá trị này. */
    fun getKillNum(characterId: Long): Int =
        states[characterId]?.let { state -> synchronized(state) { state.killNum } } ?: 0

    /** Đặt KillNum tuyệt đối sau khi persistence thành công. */
    fun setKillNum(characterId: Long, value: Int): Boolean {
        if (value < 0) return false
        val state = states[characterId] ?: return false
        synchronized(state) {
            state.killNum = value
        }
        return true
    }

    /** Tăng KillNum runtime và trả giá trị mới; -1 nếu player chưa khởi tạo. */
    fun addKillNum(characterId: Long, delta: Int): Int {
        if (delta <= 0) return getKillNum(characterId)
        val state = states[characterId] ?: return -1
        synchronized(state) {
            state.killNum = (state.killNum.toLong() + delta.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return state.killNum
        }
    }

    /** Snapshot 5 điểm phe DVT, tránh lộ mảng mutable trong PlayerState. */
    fun getMoral(characterId: Long): IntArray =
        states[characterId]?.let { state -> synchronized(state) { state.moral.copyOf(5) } }
            ?: intArrayOf(0, 0, 0, 0, 0)

    /** Đặt giá trị tuyệt đối một phe sau khi DB đã ghi thành công. */
    fun setMoral(characterId: Long, faction: Int, value: Int): Boolean {
        if (faction !in 1..5 || value < 0) return false
        val state = states[characterId] ?: return false
        synchronized(state) {
            state.moral[faction - 1] = value
        }
        return true
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
     * Collection DVT chỉ đổi MaxHP/MaxSP, không tự hồi HP/SP hiện tại.
     * Dùng riêng cho bonus Sưu Tập để khớp RoleController:SetAttribute(MaxHp/MaxSp).
     */
    fun setMaxPreserveCurrent(characterId: Long, newMaxHp: Long, newMaxSp: Int): Boolean {
        val state = states[characterId] ?: return false
        synchronized(state) {
            state.maxHp = newMaxHp.coerceAtLeast(1L)
            state.maxSp = newMaxSp.coerceAtLeast(1)
        }
        return true
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
        statCalc: StatCalculator,
        collectionHpx: Int = 0,
        collectionSpx: Int = 0
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
            state.atkPercent = bonus.atkPercent
            state.defPercent = bonus.defPercent
            state.hpPercent = bonus.hpPercent
            state.intPercent = bonus.intPercent
            state.crit = bonus.crit
            state.critDamage = bonus.critDamage
            state.dodge = bonus.dodge
            state.hit = bonus.hit
            state.increaseTotalDamage = bonus.increaseTotalDamage
            state.damageReduction = bonus.damageReduction
            state.reduceTotalDamage = bonus.reduceTotalDamage
            state.increaseDamageTakenSelf = bonus.increaseDamageTakenSelf

            val turn3Lv = if (state.turn >= 3) (state.level - 200).coerceAtLeast(0) else 0
            val calcLevel = if (state.turn >= 3) 200 else state.level

            // Lua Calc.GetMaxHp: base/equip/turn3/collection/astrolabe first, HP% last.
            val totalHpx = state.hpx + state.equipHpx +
                (if (state.turn >= 3) turn3Lv.coerceAtMost(70) + state.turn3Hpx else 0) +
                collectionHpx + state.astrolabeHpx
            val baseMaxHp = statCalc.calculateMaxHp(
                level = calcLevel, hpx = totalHpx,
                equipMaxHp = state.equipMaxHp, extraHp = 0,
                turn = state.turn, career = state.career, turn3Lv = turn3Lv
            )
            val newMaxHp = DvtPercentMath.applyFinalPercentFloor(baseMaxHp, state.hpPercent)
                .coerceAtLeast(1L)
            val oldMaxHp = state.maxHp
            state.maxHp = newMaxHp
            state.hp = (state.hp + (newMaxHp - oldMaxHp)).coerceIn(0, newMaxHp)

            // Giữ cùng nguồn cộng dồn cho MaxSP để equip refresh không làm rơi bonus hiện có.
            val totalSpx = state.spx + state.equipSpx +
                (if (state.turn >= 3) turn3Lv + state.turn3Spx else 0) +
                collectionSpx + state.astrolabeSpx
            val newMaxSp = statCalc.calculateMaxSp(
                level = calcLevel, spx = totalSpx,
                equipMaxSp = state.equipMaxSp, extraSp = 0,
                turn = state.turn, career = state.career, turn3Lv = turn3Lv
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

    /** DVT transform/disguise is session runtime state; login packet has no npcId field. */
    fun setTransformNpcId(characterId: Long, npcId: Int): Boolean {
        val state = states[characterId] ?: return false
        state.transformNpcId = npcId.coerceAtLeast(0)
        return true
    }

    fun getTransformNpcId(characterId: Long): Int =
        states[characterId]?.transformNpcId ?: 0

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
    /** Điểm diệt địch; DVT Calc.GetHonor hiện trả nguyên giá trị KillNum. */
    var killNum: Int = 0,
    /** Giới tính nhân vật: 1 nam, 2 nữ. */
    var sex: Int = 0,
    /** 5 điểm phe: Ngụy, Thục, Ngô, Khăn Vàng, Du Hiệp. */
    var moral: IntArray = intArrayOf(0, 0, 0, 0, 0),
    /** NPC ID biến hình hiện tại; DVT S:005-005, runtime-only và reset khi relog. */
    var transformNpcId: Int = 0,

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
    var equipSpx: Int = 0,
    /** DVT Astrolabe otherAdd — không tham gia vào %base. */
    var astrolabeHpx: Int = 0,
    var astrolabeDef: Int = 0,
    var astrolabeInt: Int = 0,
    var astrolabeSpx: Int = 0,
    var astrolabeAgi: Int = 0,
    var astrolabeAtk: Int = 0,
    /** DVT EAttribute.AtkPercent=101, phần trăm ATK trên base ATK */
    var atkPercent: Int = 0,
    /** DVT EAttribute.DefPercent=102, phần trăm DEF trên base DEF */
    var defPercent: Int = 0,
    /** DVT EAttribute.HpPercent=107, nhân vào MaxHP cuối cùng. */
    var hpPercent: Int = 0,
    /** DVT EAttribute.IntPercent=109, phần trăm INT trên base INT. */
    var intPercent: Int = 0,
    /** DVT EAttribute.Crit=103, phần trăm chí mạng */
    var crit: Int = 0,
    /** DVT EAttribute.CritDamage=104, phần trăm cộng thêm trên base crit 50% */
    var critDamage: Int = 0,
    /** DVT EAttribute.Dodge=105, phần trăm né tránh */
    var dodge: Int = 0,
    /** DVT EAttribute.Hit=106, phần trăm mệnh trung */
    var hit: Int = 0,
    /** DVT EAttribute.IncreaseTotalDamage=113 */
    var increaseTotalDamage: Int = 0,
    /** DVT EAttribute.DamageReduction=114 */
    var damageReduction: Int = 0,
    /** DVT EAttribute.ReduceTotalDamage=117 */
    var reduceTotalDamage: Int = 0,
    /** DVT EAttribute.IncreaseDamageTakenSelf=118 */
    var increaseDamageTakenSelf: Int = 0
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
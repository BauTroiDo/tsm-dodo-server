package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.FormulaParams
import tw.tsm.domain.interfaces.StatCalculator
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

private val logger = KotlinLogging.logger {}

/**
 * Công cụ tính toán thuộc tính — Dịch 1:1 từ `Calculator.lua`
 *
 * Dựa vào `FormulaParams` (từ `Formula.dat`) để tính toán MaxHP/MaxSP/Kinh nghiệm thăng cấp, v.v. của nhân vật.
 * Tất cả các công thức đều đối chiếu nghiêm ngặt với Calculator.lua, không tự sáng tạo.
 *
 * @param params Các tham số công thức được tải từ Formula.dat
 */
class StatSystem(private val params: FormulaParams) : StatCalculator {

    /**
     * Tính toán Max HP — Đối chiếu với `Calc.GetMaxHp`
     *
     * Calculator.lua dòng 103-226.
     * Khi Server gọi thì role=nil, truyền trực tiếp giá trị (không lấy thuộc tính từ role).
     *
     * @param level Cấp độ
     * @param hpx Mức tăng HP (bao gồm cả giá trị cộng thêm từ trang bị equipHpx)
     * @param equipMaxHp Giá trị Max HP cộng thêm từ trang bị
     * @param extraHp HP cộng thêm (từ đan dược vĩnh viễn, v.v.)
     * @param turn Số lần chuyển sinh (0/1/2/3)
     * @param career Nghề nghiệp (Sau chuyển sinh 2: 1=Hiệp khách, 2=Bá vương, 3=Hiền sĩ, 4=Đích tiên)
     * @param turn3Lv Chênh lệch cấp độ chuyển sinh 3 (khi turn>=3 thì = level - maxLv)
     */
    override fun calculateMaxHp(
        level: Int,
        hpx: Int,
        equipMaxHp: Int,
        extraHp: Int,
        turn: Int,
        career: Int,
        turn3Lv: Int
    ): Long {
        val safeHpx = max(0, hpx)
        var result: Long

        when (turn) {
            0 -> {
                // Chuyển sinh 0: round(w35*level + w36*hpx*level^w37) + baseHp + 2*hpx + equipMaxHp + extraHp
                result = (params.w35 * level + params.w36 * safeHpx * level.toDouble().pow(params.w37))
                    .roundToLong() + params.baseHp + 2L * safeHpx + equipMaxHp + extraHp
            }
            1 -> {
                // Chuyển sinh 1: round(w35*level + w36*hpx*level^w37) + 180 + 4*hpx + equipMaxHp + extraHp
                result = (params.w35 * level + params.w36 * safeHpx * level.toDouble().pow(params.w37))
                    .roundToLong() + 180 + 4L * safeHpx + equipMaxHp + extraHp
            }
            2, 3 -> {
                // Chuyển sinh 2/3: Công thức dành riêng cho nghề nghiệp
                result = when (career) {
                    CAREER_XIAKE -> {
                        // Hiệp khách: round(w35*level + 2.5*hpx*level^0.35) + 280 + 25*hpx
                        (params.w35 * level + 2.5 * safeHpx * level.toDouble().pow(0.35))
                            .roundToLong() + 280 + 25L * safeHpx + equipMaxHp + extraHp
                    }
                    CAREER_BAWANG -> {
                        // Bá vương: round(w35*level + 3*hpx*level^0.35) + 380 + 30*hpx
                        (params.w35 * level + 3.0 * safeHpx * level.toDouble().pow(0.35))
                            .roundToLong() + 380 + 30L * safeHpx + equipMaxHp + extraHp
                    }
                    CAREER_XIANSHI -> {
                        // Hiền sĩ: round(w35*level + 2*hpx*level^0.35) + 180 + 23*hpx
                        (params.w35 * level + 2.0 * safeHpx * level.toDouble().pow(0.35))
                            .roundToLong() + 180 + 23L * safeHpx + equipMaxHp + extraHp
                    }
                    CAREER_DIXIAN -> {
                        // Đích tiên: round(w35*level + 2*hpx*level^0.35) + 180 + 21*hpx
                        (params.w35 * level + 2.0 * safeHpx * level.toDouble().pow(0.35))
                            .roundToLong() + 180 + 21L * safeHpx + equipMaxHp + extraHp
                    }
                    else -> {
                        // Nghề nghiệp không xác định, dùng công thức chuyển sinh 0
                        (params.w35 * level + params.w36 * safeHpx * level.toDouble().pow(params.w37))
                            .roundToLong() + params.baseHp + 2L * safeHpx + equipMaxHp + extraHp
                    }
                }

                // Thưởng cộng thêm của chuyển sinh 3
                if (turn == 3) {
                    if (turn3Lv > 140) result += (minOf(turn3Lv - 140, 60) * 5).toLong()
                    if (turn3Lv > 90) result += (minOf(turn3Lv - 90, 50) * 10).toLong()
                    if (turn3Lv > 70) result += (minOf(turn3Lv - 70, 20) * 25).toLong()
                }
            }
            else -> {
                result = (params.w35 * level + params.w36 * safeHpx * level.toDouble().pow(params.w37))
                    .roundToLong() + params.baseHp + 2L * safeHpx + equipMaxHp + extraHp
            }
        }

        return max(1L, result)
    }

    /**
     * Tính toán Max SP — Đối chiếu với `Calc.GetMaxSp`
     *
     * Calculator.lua dòng 229-339.
     */
    override fun calculateMaxSp(
        level: Int,
        spx: Int,
        equipMaxSp: Int,
        extraSp: Int,
        turn: Int,
        career: Int,
        turn3Lv: Int
    ): Int {
        val safeSpx = max(0, spx)
        var result: Long

        when (turn) {
            0 -> {
                // Chuyển sinh 0: round(w38*level + w39*spx*level^w40) + baseSp + equipMaxSp + extraSp
                result = (params.w38 * level + params.w39 * safeSpx * level.toDouble().pow(params.w40))
                    .roundToLong() + params.baseSp + equipMaxSp + extraSp
            }
            1 -> {
                // Chuyển sinh 1: round(w38*level + w39*spx*level^w40) + 110 + equipMaxSp + extraSp
                result = (params.w38 * level + params.w39 * safeSpx * level.toDouble().pow(params.w40))
                    .roundToLong() + 110 + equipMaxSp + extraSp
            }
            2, 3 -> {
                result = when (career) {
                    CAREER_XIAKE, CAREER_BAWANG -> {
                        // Hiệp khách/Bá vương: round(w38*level + 2*spx*level^0.25) + 160
                        (params.w38 * level + 2.0 * safeSpx * level.toDouble().pow(0.25))
                            .roundToLong() + 160 + equipMaxSp + extraSp
                    }
                    CAREER_XIANSHI -> {
                        // Hiền sĩ: round(w38*level + 3*spx*level^0.25) + 310
                        (params.w38 * level + 3.0 * safeSpx * level.toDouble().pow(0.25))
                            .roundToLong() + 310 + equipMaxSp + extraSp
                    }
                    CAREER_DIXIAN -> {
                        // Đích tiên: round(w38*level + 3.5*spx*level^0.25) + 410
                        (params.w38 * level + 3.5 * safeSpx * level.toDouble().pow(0.25))
                            .roundToLong() + 410 + equipMaxSp + extraSp
                    }
                    else -> {
                        (params.w38 * level + params.w39 * safeSpx * level.toDouble().pow(params.w40))
                            .roundToLong() + params.baseSp + equipMaxSp + extraSp
                    }
                }
            }
            else -> {
                result = (params.w38 * level + params.w39 * safeSpx * level.toDouble().pow(params.w40))
                    .roundToLong() + params.baseSp + equipMaxSp + extraSp
            }
        }

        return max(1L, result).toInt()
    }

    /**
     * Tính toán kinh nghiệm cần thiết để thăng cấp — Đối chiếu với `Calc.GetUpExp`
     *
     * Calculator.lua dòng 351-368.
     */
    fun calculateUpExp(level: Int, turn: Int = 0): Long {
        return when (turn) {
            0 -> level.toDouble().pow(params.w28).roundToLong() + params.extraExp
            1 -> level.toDouble().pow(3.1).roundToLong() + params.extraExp
            2 -> level.toDouble().pow(3.24).roundToLong() + params.extraExp
            3 -> {
                if (level <= 0) return 1L
                val mod60 = level % 60
                val div60 = level / 60
                // Lua: round(round((level % 60)^3.85) * (trunc(level/60)+1.2)^1.6
                //          + 1400000 * (trunc(level/60)+0.5)^2.6)
                // Only the first power term is rounded before multiplication; the final sum is
                // rounded once. Rounding each multiplier separately changes the EXP curve.
                val part1 = mod60.toDouble().pow(3.85).roundToLong().toDouble() *
                        (div60 + 1.2).pow(1.6)
                val part2 = 1400000.0 * (div60 + 0.5).pow(2.6)
                (part1 + part2).roundToLong()
            }
            else -> level.toDouble().pow(params.w28).roundToLong() + params.extraExp
        }
    }

    fun calculateNeedTurn3Exp(level: Int): Long {
        if (level <= 0) return 0L
        var total = 0L
        for (i in 1..level) {
            val next = calculateUpExp(i, 3)
            if (Long.MAX_VALUE - total < next) return Long.MAX_VALUE
            total += next
        }
        return total
    }

    /**
     * Tính toán cấp độ tu hành — Đối chiếu với `Calc.GetPracLv`
     *
     * Calculator.lua dòng 89-97.
     */
    fun calculatePracLevel(exp: Long): Int {
        var accumulated = 0L
        for (i in 1..PRAC_MAX_LV) {
            accumulated += i.toDouble().pow(3.0).roundToLong() + PRAC_EXTRA_VALUE
            if (exp < accumulated) return i
        }
        return PRAC_MAX_LV
    }

    companion object {
        /** Hằng số nghề nghiệp (ECareer) */
        const val CAREER_XIAKE = 1      // Hiệp khách
        const val CAREER_BAWANG = 2     // Bá vương
        const val CAREER_XIANSHI = 3    // Hiền sĩ
        const val CAREER_DIXIAN = 4     // Đích tiên

        /** Hằng số tu hành */
        private const val PRAC_MAX_LV = 200
        private const val PRAC_EXTRA_VALUE = 20L
    }
}
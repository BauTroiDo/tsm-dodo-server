package tw.tsm.gamelogic.battle

import tw.tsm.domain.constants.BattleConstants
import tw.tsm.domain.constants.EHuman
import tw.tsm.domain.enums.BattleStatus

/**
 * Nhân vật tham chiến — tương ứng `FightField.lua` fightHum[]
 *
 * Đại diện một đơn vị chiến đấu trên bàn cờ (người chơi, tướng, hoặc quái).
 * Dùng class thường thay vì data class, vì:
 * - IntArray phá vỡ ngữ nghĩa equals/hashCode trong data class
 * - @Volatile mất hiệu lực sau data class.copy()
 * - Lớp này dùng roleId làm định danh duy nhất
 */
class BattleFighter(
    /** ID nhân vật (người chơi=characterId, quái=ID số âm tự sinh)*/
    val roleId: Long,
    /** Loại nhân vật (EHuman)*/
    val kind: Int,
    /** ID chủ nhân (người chơi tự thân=0, tướng=ID người chơi)*/
    val masterId: Long = 0,
    /** Chỉ số bản đồ NPC (dùng cho quái)*/
    val mapNpcIdx: Int = 0,

    // ── Tên/ngoại hình ──
    val name: String,
    val npcId: Int = 0,
    /** Carried follow slot for player-owned battle pets; 0 for non-follow fighters. */
    val followSlot: Int = 0,

    // ── Vị trí trên bàn cờ ──
    var col: Int,
    var row: Int,

    // ── Thuộc tính chiến đấu ──
    var maxHp: Long,
    var maxSp: Long,
    var hp: Long,
    var sp: Long,
    var level: Int,
    var upgradeLv: Int = 0,
    var element: Int,
    var atk: Int,
    var def: Int,
    var agi: Int,
    var intelligence: Int,

    // ── Kỹ năng (tối đa 3) ──
    val skills: IntArray = intArrayOf(),
    /** Skill level snapshot; needed by SkillData:GetFightArea(skillLv). */
    val skillLevels: Map<Int, Int> = emptyMap(),

    // ── Dữ liệu ngoại hình riêng của người chơi (dùng khi kind=Player) ──
    var sex: Int = 0,
    var face: Int = 0,
    var hair: Int = 0,
    var color1: Int = 0,
    var color2: Int = 0,
    var turn: Int = 0,
    var career: Int = 0,
    var turn3Element: Int = 0,
    var specialEquipItemId: Int = 0,
    var specialEquipDamage: Int = 0,
    var weaponItemId: Int = 0,
    var weaponItemKind: Int = 0,
    var equipItemIds: IntArray = intArrayOf(),
    var outfitItemIds: IntArray = intArrayOf(),

    // ── Trạng thái chiến đấu ──
    var isDead: Boolean = false,
    /** Có bị đánh bay không (chết do siêu sát, không thể hồi sinh trên chiến trường)*/
    var isFlyOut: Boolean = false,
    /** Hiệp này có ở trạng thái phòng thủ không (KERO: dest.def → sát thương giảm một nửa)*/
    var isDefending: Boolean = false,
    var statusEffect: BattleStatus = BattleStatus.NONE,
    var statusRounds: Int = 0,
    /** Skill that applied the current status; echoed when the status is restored. */
    var statusSkillId: Int = 0,
    /** Battle round in which the current status was inflicted. */
    var statusAppliedRound: Int = -1,
    /** Independent client status groups (Seal/Vary/Faint/Spec/Strong). */
    val groupedStatuses: MutableMap<Int, GroupedBattleStatus> = mutableMapOf(),

    /** Hiệp này đã hành động chưa (đa luồng: Netty ghi / coroutine đọc)*/
    @Volatile var hasActed: Boolean = false,

    /** Hành động đã chọn trong hiệp này (đa luồng: Netty ghi / coroutine đọc)*/
    @Volatile var pendingAction: BattleAction? = null
) {
    /** Có phải phe người chơi không (cột 2-3, bên phải)*/
    val isPlayerSide: Boolean get() = col >= 2

    /** Có phải phe địch không (cột 0-1, bên trái)*/
    val isEnemySide: Boolean get() = col < 2

    /** Có phải người chơi thật không (PLAYER hoặc PLAYERS, dùng để phân biệt người và NPC trong PVP)*/
    val isHumanPlayer: Boolean get() = kind == EHuman.PLAYER || kind == EHuman.PLAYERS

    /** Có do người chơi điều khiển không (bao gồm tướng và các nhân vật ràng buộc masterID, dùng cho client FindMasterID phán đoán)*/
    val isPlayerControlled: Boolean get() = isHumanPlayer || kind == EHuman.FOLLOW_NPC

    /** Có còn sống không*/
    val isAlive: Boolean get() = !isDead && hp > 0

    /** Seal prevents acting; client IsCanControl also blocks command input while Chaos is active. */
    val isStunned: Boolean get() = groupedStatuses.values.any { BattleStatus.isStunStatus(it.status) }
    val isCommandBlocked: Boolean get() = isStunned || groupedStatuses.values.any { it.status == BattleStatus.CHAOS || it.status == BattleStatus.TAUNT }
    /** A queued command can become invalid before its AGI turn if a control status lands mid-round. */
    val isActionBlocked: Boolean get() = isCommandBlocked

    fun setGroupedStatus(group: Int, status: BattleStatus, rounds: Int, skillId: Int, appliedRound: Int) {
        if (group <= 0 || status == BattleStatus.NONE) return
        groupedStatuses[group] = GroupedBattleStatus(status, rounds, skillId, appliedRound)
        syncLegacyStatusView()
    }

    fun clearGroupedStatus(group: Int) {
        groupedStatuses.remove(group)
        syncLegacyStatusView()
    }

    fun clearAllGroupedStatuses() {
        groupedStatuses.clear()
        syncLegacyStatusView()
    }

    fun syncLegacyStatusView() {
        val primary = groupedStatuses.toSortedMap().values.firstOrNull()
        statusEffect = primary?.status ?: BattleStatus.NONE
        statusRounds = primary?.rounds ?: 0
        statusSkillId = primary?.skillId ?: 0
        statusAppliedRound = primary?.appliedRound ?: -1
    }

    /** Chỉ số bàn cờ (col * MAX_ROW + row)*/
    val gridIndex: Int get() = col * BattleConstants.MAX_ROW + row

    /**
     * Hệ số sát thương Combo (mỗi lần liên kích nhân dồn ×1.2, kết thúc mỗi hiệp reset về 1.0)
     *
     * Được BattleInstance.resolveRound() tính và tiêm sau khi sắp xếp actingFighters.
     */
    var comboMultiplier: Double = 1.0

    companion object {
        /**
         * Xác định [prev] → [curr] có tạo thành Combo không
         *
         * Điều kiện Combo (tương ứng logic gốc C# bản PC):
         * 1. Hành động của cả hai đều là kỹ năng (không phải vật phẩm/bỏ chạy v.v.)
         * 2. Cả hai kỹ năng đều là kỹ năng vật lý (SkillDef.fightWay == 1)
         * 3. Ô mục tiêu của hai kỹ năng giống nhau (targetCol + targetRow đều bằng nhau)
         *
         * @param prev người hành động trước đó
         * @param curr người hành động hiện tại
         * @param getSkillDef lambda truy vấn định nghĩa kỹ năng (có thể truyền BattleSkillResolver::getSkillDef)
         */
        fun checkCombo(
            prev: BattleFighter,
            curr: BattleFighter,
            getSkillDef: (Int) -> tw.tsm.domain.data.SkillDef?
        ): Boolean {
            val prevAction = prev.pendingAction ?: return false
            val currAction = curr.pendingAction ?: return false

            // Điều kiện 1: cả hai đều là hành động kỹ năng
            if (prevAction.actionKind != BattleAction.KIND_SKILL) return false
            if (currAction.actionKind != BattleAction.KIND_SKILL) return false

            // Điều kiện 2: cả hai kỹ năng đều là kỹ năng vật lý (fightWay == 1 hoặc đánh thường 10000)
            val prevIsPhysical = prevAction.id == BattleConstants.HAND_FIGHT_SKILL_ID ||
                (getSkillDef(prevAction.id)?.fightWay == 1)
            val currIsPhysical = currAction.id == BattleConstants.HAND_FIGHT_SKILL_ID ||
                (getSkillDef(currAction.id)?.fightWay == 1)
            if (!prevIsPhysical || !currIsPhysical) return false

            // Điều kiện 3: ô mục tiêu giống nhau
            if (prevAction.targetCol != currAction.targetCol) return false
            if (prevAction.targetRow != currAction.targetRow) return false

            return true
        }
    }
}

/**
 * Hành động chiến đấu (người chơi chọn hoặc AI quyết định)
 */
data class BattleAction(
    /** Loại hành động: 1=kỹ năng, 2=vật phẩm, 3=bắt, 4=bỏ chạy*/
    val actionKind: Int,
    /** ID kỹ năng/vật phẩm*/
    val id: Int,
    /** Cột mục tiêu*/
    val targetCol: Int,
    /** Hàng mục tiêu*/
    val targetRow: Int
) {
    companion object {
        const val KIND_SKILL = 1
        const val KIND_ITEM = 2
        const val KIND_CATCH = 3
        const val KIND_ESCAPE = 4
        const val KIND_DEFEND = 5
    }
}


data class GroupedBattleStatus(
    val status: BattleStatus,
    var rounds: Int,
    val skillId: Int,
    val appliedRound: Int
)

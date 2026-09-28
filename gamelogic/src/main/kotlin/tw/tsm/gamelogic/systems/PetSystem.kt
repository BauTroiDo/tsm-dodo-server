package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.data.ItemDef
import tw.tsm.domain.data.NpcDef
import tw.tsm.domain.interfaces.repositories.PetRepository
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Hệ thống võ tướng — Quản lý võ tướng mang theo, xe ngựa, khách sạn, kho võ tướng
 *
 * Vòng đời: Tải khi đăng nhập → Thao tác trên bộ nhớ → Lưu khi ngắt kết nối
 * Nguồn: Role.lua FollowNpcAppear(), Cart.lua, Inn.lua
 */
class PetSystem(
    private val petRepository: PetRepository,
    private val statCalculator: StatSystem,
    private val npcDefs: Map<Int, NpcDef> = emptyMap(),
    private val itemDefs: Map<Int, ItemDef> = emptyMap()
) {

    companion object {
        /** Cấp tối đa của võ tướng trong UIStatus/Role. */
        const val PET_MAX_LEVEL = 200
        /** Số lượng võ tướng mang theo tối đa */
        const val MAX_FOLLOW = 4
        /** Sức chứa xe ngựa tối đa */
        const val MAX_CART = 4
        /** Sức chứa khách sạn tối đa (Inn.lua minSpace=6, maxSpace=30) */
        const val MAX_INN = 30
        /** Sức chứa kho võ tướng tối đa (Inn.lua warriorRepositoryMax=150) */
        const val MAX_WAREHOUSE = 150
        /** Loại lưu trữ: Mang theo */
        const val STORAGE_FOLLOW = 1
        /** Loại lưu trữ: Xe ngựa */
        const val STORAGE_CART = 2
        /** Loại lưu trữ: Khách sạn */
        const val STORAGE_INN = 3
        /** Loại lưu trữ: Kho võ tướng */
        const val STORAGE_WAREHOUSE = 4
    }

    /**
     * Bộ nhớ tạm (cache) của võ tướng nhân vật
     *
     * @property follow Võ tướng mang theo (slotIndex → data)
     * @property cart Võ tướng xe ngựa (slotIndex → data)
     * @property inn Võ tướng khách sạn (slotIndex → data)
     * @property warehouse Kho võ tướng (list, không có slot cố định)
     * @property fightNpcId ID NPC võ tướng xuất chiến (0 = Không có xuất chiến)
     * @property dirty Đánh dấu có cần lưu hay không
     */
    data class PetCache(
        val follow: MutableMap<Int, FollowNpcData> = mutableMapOf(),
        val cart: MutableMap<Int, FollowNpcData> = mutableMapOf(),
        val inn: MutableMap<Int, FollowNpcData> = mutableMapOf(),
        val warehouse: MutableList<FollowNpcData> = mutableListOf(),
        var fightNpcId: Int = 0,
        var dirty: Boolean = false
    )

    private val cache = ConcurrentHashMap<Long, PetCache>()

    /**
     * Tải võ tướng của nhân vật khi đăng nhập
     */
    suspend fun load(characterId: Long): PetCache {
        val pets = petRepository.findByCharacterId(characterId)
        val pc = PetCache()
        for (loaded in pets) {
            // DVT UIStatus computes pet EXP as cumulative total:
            // exp - Calc.GetNeedTExp(level, NpcData:GetTurn()).
            // Legacy rows from the old server stored 0/remainder EXP, which pins the bar at 0%.
            val turn = petExpTurn(loaded)
            val floorExp = statCalculator.calculateNeedTotalExp(loaded.level.coerceAtLeast(1), turn)
            var pet = if (loaded.exp < floorExp) {
                pc.dirty = true
                logger.info {
                    "Chuẩn hóa EXP võ tướng: charId=$characterId npcId=${loaded.npcId} " +
                        "lv=${loaded.level} exp=${loaded.exp} -> $floorExp"
                }
                loaded.copy(exp = floorExp)
            } else {
                loaded
            }

            // DVT client calculates the pet gauges from Calc.GetMaxHp/GetMaxSp.
            // Clamp legacy/broken rows here so an old hpBase/max mismatch cannot
            // produce impossible values such as current HP > displayed MaxHP.
            val (maxHp, maxSp) = petMaxHpSp(pet)
            val normalizedHp = if (maxHp > 0L) pet.hp.coerceIn(0L, maxHp) else pet.hp
            val normalizedSp = if (maxSp > 0) pet.sp.coerceIn(0, maxSp) else pet.sp
            if (normalizedHp != pet.hp || normalizedSp != pet.sp) {
                pc.dirty = true
                logger.info {
                    "Chuẩn hóa HP/SP võ tướng: charId=$characterId npcId=${pet.npcId} " +
                        "hp=${pet.hp}/$maxHp -> $normalizedHp, sp=${pet.sp}/$maxSp -> $normalizedSp"
                }
                pet = pet.copy(hp = normalizedHp, sp = normalizedSp)
            }

            when (pet.storageType) {
                STORAGE_FOLLOW -> pc.follow[pet.slotIndex] = pet
                STORAGE_CART -> pc.cart[pet.slotIndex] = pet
                STORAGE_INN -> pc.inn[pet.slotIndex] = pet
                STORAGE_WAREHOUSE -> pc.warehouse.add(pet)
            }
        }
        cache[characterId] = pc
        logger.info { "Tải võ tướng: charId=$characterId Mang theo=${pc.follow.size} Xe ngựa=${pc.cart.size} Khách sạn=${pc.inn.size} Kho=${pc.warehouse.size}" }
        return pc
    }

    /**
     * Lấy bộ nhớ tạm (cache) võ tướng của nhân vật
     */
    fun getCache(characterId: Long): PetCache? = cache[characterId]

    /**
     * Lấy danh sách võ tướng mang theo
     */
    fun getFollowPets(characterId: Long): Map<Int, FollowNpcData> =
        cache[characterId]?.follow ?: emptyMap()

    /** DVT NpcData:GetTurn(): static NPC turn==2 uses pet turn=1, everything else uses turn=0. */
    private fun petExpTurn(pet: FollowNpcData): Int {
        val rawTurn = npcDefs[pet.npcId]?.turn ?: pet.turnKind
        return if (rawTurn == 2) 1 else 0
    }

    /**
     * DVT Calc.GetMaxHp/GetMaxSp for FollowNpc.
     *
     * Role.FollowNpcAppear initializes the six base attributes from NpcData, then
     * recalculates MaxHp/MaxSp from level + HPX/SPX (+ equipment + permanent pills).
     * The static NpcData hpBase/spBase are spawn defaults, not a captured pet's max.
     */
    private fun petMaxHpSp(pet: FollowNpcData): Pair<Long, Int> {
        val bonus = EquipBonusCalculator.calculateTotalBonus(pet.equips, itemDefs)
        val turn = petExpTurn(pet)
        val maxHp = statCalculator.calculateMaxHp(
            level = pet.level.coerceAtLeast(1),
            hpx = pet.hpx + bonus.equipHpx,
            equipMaxHp = bonus.equipMaxHp,
            extraHp = pet.hpPillCount * 20,
            turn = turn
        )
        val maxSp = statCalculator.calculateMaxSp(
            level = pet.level.coerceAtLeast(1),
            spx = pet.spx + bonus.equipSpx,
            equipMaxSp = bonus.equipMaxSp,
            extraSp = pet.spPillCount * 5,
            turn = turn
        )
        return maxHp to maxSp
    }

    data class PetExpGain(
        val slotIndex: Int,
        val before: FollowNpcData,
        val after: FollowNpcData,
    ) {
        val levelGained: Int get() = after.level - before.level
    }

    /**
     * Add battle EXP to the active growable follow NPC using DVT cumulative EXP semantics.
     *
     * UIStatus.lua:
     *   slider = Exp - Calc.GetNeedTExp(level, turn)
     *   max    = Calc.GetUpExp(level + 1, turn)
     */
    fun gainBattleExp(characterId: Long, followSlot: Int, amount: Long): PetExpGain? {
        if (amount <= 0) return null
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val before = pc.follow[followSlot] ?: return null
            if (before.canGrow != 1 || before.level >= PET_MAX_LEVEL) return null

            val turn = petExpTurn(before)
            var level = before.level.coerceAtLeast(1)
            val floorExp = statCalculator.calculateNeedTotalExp(level, turn)
            var exp = maxOf(before.exp, floorExp)
            exp = (exp + amount).coerceAtMost(Int.MAX_VALUE.toLong())

            var gainedLevels = 0
            while (level < PET_MAX_LEVEL) {
                val nextFloor = statCalculator.calculateNeedTotalExp(level + 1, turn)
                if (exp < nextFloor) break
                level++
                gainedLevels++
            }

            var after = before.copy(
                level = level,
                exp = exp,
                skillPoint = before.skillPoint + gainedLevels,
            )
            // PC reference levelUp() and DVT status formulas both refresh the
            // pet's HP/SP capacity at a level boundary. Restore to the new max.
            if (gainedLevels > 0) {
                val (maxHp, maxSp) = petMaxHpSp(after)
                after = after.copy(hp = maxHp, sp = maxSp)
            }
            pc.follow[followSlot] = after
            pc.dirty = true
            logger.info {
                "EXP võ tướng: charId=$characterId slot=$followSlot npcId=${before.npcId} " +
                    "exp=${before.exp}+$amount->$exp lv=${before.level}->$level"
            }
            return PetExpGain(followSlot, before, after)
        }
    }

    /**
     * Lấy danh sách võ tướng xe ngựa
     */
    fun getCartPets(characterId: Long): Map<Int, FollowNpcData> =
        cache[characterId]?.cart ?: emptyMap()

    /**
     * Thêm võ tướng từ sự kiện
     *
     * Khởi tạo FollowNpcData từ NpcDef và thêm vào đội ngũ mang theo.
     *
     * Ý nghĩa canGrow (RoleController.lua L1240-1248):
     * - 0 = Không thể trưởng thành (Tên màu cam)
     * - 1 = Có thể trưởng thành (Tên màu xanh da trời)
     * - 2 = NPC nhiệm vụ (Màu đặc biệt, rời đi sau khi kết thúc nhiệm vụ)
     *
     * @param characterId ID nhân vật
     * @param npcId       ID loại NPC (Npc_C.dat)
     * @param npcDef      Định nghĩa NPC (Nguồn thuộc tính ban đầu)
     * @param canGrow     Loại trưởng thành (0=Không thể trưởng thành, 1=Có thể trưởng thành)
     * @return (slotIndex, FollowNpcData), null = Đã đầy hoặc trùng lặp
     */
    fun addFollowNpc(characterId: Long, npcId: Int, npcDef: NpcDef, canGrow: Int = 1): Pair<Int, FollowNpcData>? {
        val pc = cache[characterId] ?: return null

        synchronized(pc) {
            // Kiểm tra xem đã có trong đội hay chưa (Tìm trong cả mang theo + xe ngựa + khách sạn)
            if (pc.follow.values.any { it.npcId == npcId } || pc.cart.values.any { it.npcId == npcId }
                || pc.inn.values.any { it.npcId == npcId }) {
                logger.debug { "Võ tướng đã có trong đội: charId=$characterId npcId=$npcId" }
                return null
            }

            // Tìm vị trí trống trong danh sách mang theo
            val slot = (1..MAX_FOLLOW).firstOrNull { !pc.follow.containsKey(it) } ?: return null

            // DVT Role.FollowNpcAppear and PC TSPet.initPet both create a newly
            // captured/awarded pet at LV1. NpcDef.level is the encounter/spawn level,
            // not the pet's starting level.
            val turn = if (npcDef.turn == 2) 1 else 0
            val seed = FollowNpcData(
                slotIndex = slot,
                npcId = npcId,
                storageType = STORAGE_FOLLOW,
                name = npcDef.name,
                level = 1,
                exp = statCalculator.calculateUpExp(1, turn),
                hp = 0,
                sp = 0,
                atk = npcDef.atk,
                def = npcDef.def,
                agi = npcDef.agi,
                intelligence = npcDef.intelligence,
                hpx = npcDef.hpx,
                spx = npcDef.spx,
                loyalty = 60,
                skillPoint = 0,
                // Role.FollowNpcAppear: existing skill -> level 1; absent skill -> 0.
                skill1Lv = if (npcDef.skills.getOrElse(0) { 0 } != 0) 1 else 0,
                skill2Lv = if (npcDef.skills.getOrElse(1) { 0 } != 0) 1 else 0,
                skill3Lv = if (npcDef.skills.getOrElse(2) { 0 } != 0) 1 else 0,
                canGrow = canGrow
            )
            val (maxHp, maxSp) = petMaxHpSp(seed)
            val pet = seed.copy(hp = maxHp, sp = maxSp)
            pc.follow[slot] = pet
            pc.dirty = true
            logger.info { "Thêm võ tướng từ sự kiện: charId=$characterId npcId=$npcId (${npcDef.name}) canGrow=$canGrow → slot=$slot" }
            return Pair(slot, pet)
        }
    }

    /**
     * Xóa võ tướng mang theo dựa trên ID NPC (Dùng khi sự kiện yêu cầu xóa, resultClass=8 pStyle=2)
     *
     * @param characterId ID nhân vật
     * @param npcId       ID loại NPC cần xóa
     * @return Index của ô (slot) bị xóa, -1 = Không tìm thấy
     */
    fun removeFollowNpcByNpcId(characterId: Long, npcId: Int): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val entry = pc.follow.entries.find { it.value.npcId == npcId } ?: return -1
            val slotIndex = entry.key
            pc.follow.remove(slotIndex)
            // Nếu võ tướng xuất chiến bị xóa, xóa trạng thái xuất chiến
            if (pc.fightNpcId == npcId) {
                pc.fightNpcId = 0
            }
            pc.dirty = true
            logger.info { "Sự kiện xóa võ tướng: charId=$characterId npcId=$npcId slot=$slotIndex" }
            return slotIndex
        }
    }

    /**
     * Đưa dữ liệu võ tướng đã có sẵn vào vị trí trống của danh sách mang theo (Dùng khi nhận võ tướng từ giao dịch)
     *
     * Khác với [addFollowNpc]: Giữ lại cấp độ/thuộc tính/kỹ năng/trang bị của võ tướng, chỉ ghi đè lại vị trí (slot) và loại lưu trữ (storageType).
     * Việc kiểm tra trùng lặp sẽ do phía gọi hàm đảm nhận (Quy trình giao dịch có mã lỗi riêng).
     *
     * @return (slotIndex, Võ tướng sau khi đặt vào), null = Danh sách mang theo đã đầy
     */
    fun addExistingFollowNpc(characterId: Long, pet: FollowNpcData): Pair<Int, FollowNpcData>? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val slot = (1..MAX_FOLLOW).firstOrNull { !pc.follow.containsKey(it) } ?: return null
            val placed = pet.copy(slotIndex = slot, storageType = STORAGE_FOLLOW)
            pc.follow[slot] = placed
            pc.dirty = true
            logger.info { "Võ tướng gia nhập đội (Dữ liệu đã có sẵn): charId=$characterId npcId=${pet.npcId} → slot=$slot" }
            return Pair(slot, placed)
        }
    }

    /**
     * Trục xuất võ tướng (C:015-002)
     *
     * @return Võ tướng bị trục xuất, null nếu thất bại
     */
    fun kickFollowNpc(characterId: Long, slotIndex: Int): FollowNpcData? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val pet = pc.follow.remove(slotIndex) ?: return null
            // Nếu võ tướng xuất chiến bị trục xuất, xóa trạng thái xuất chiến
            if (pc.fightNpcId == pet.npcId) {
                pc.fightNpcId = 0
            }
            pc.dirty = true
            logger.info { "Trục xuất võ tướng: charId=$characterId slot=$slotIndex npc=${pet.npcId}" }
            return pet
        }
    }

    /**
     * Võ tướng xuất chiến (C:019-001)
     *
     * @return ID NPC xuất chiến, 0 nếu thất bại
     */
    fun deployFollowNpc(characterId: Long, npcId: Int): Int {
        val pc = cache[characterId] ?: return 0
        // Xác nhận NPC có trong danh sách mang theo
        val pet = pc.follow.values.find { it.npcId == npcId } ?: return 0
        // NPC nhiệm vụ (canGrow=0) không thể xuất chiến
        if (pet.canGrow <= 0) return 0
        pc.fightNpcId = npcId
        logger.info { "Võ tướng xuất chiến: charId=$characterId npc=$npcId" }
        return npcId
    }

    /**
     * Thu hồi võ tướng xuất chiến (C:019-002)
     *
     * @return true Thành công
     */
    fun recallFightNpc(characterId: Long): Boolean {
        val pc = cache[characterId] ?: return false
        if (pc.fightNpcId == 0) return false
        logger.info { "Thu hồi võ tướng: charId=$characterId npc=${pc.fightNpcId}" }
        pc.fightNpcId = 0
        return true
    }

    /**
     * Cập nhật HP/SP của võ tướng xuất chiến (Dùng khi kết thúc trận chiến)
     *
     * Ghi lại HP/SP cuối cùng của BattleFighter vào bộ nhớ tạm (cache) khi trận chiến kết thúc.
     */
    fun updateFightNpcHpSp(characterId: Long, hp: Long, sp: Int) {
        val pc = cache[characterId] ?: return
        val npcId = pc.fightNpcId
        if (npcId == 0) return
        synchronized(pc) {
            val slot = pc.follow.entries.firstOrNull { it.value.npcId == npcId }?.key ?: return
            val pet = pc.follow[slot] ?: return
            pc.follow[slot] = pet.copy(hp = hp, sp = sp)
            pc.dirty = true
            logger.debug { "Tổng kết trận chiến - Cập nhật HP/SP võ tướng: charId=$characterId npc=$npcId hp=$hp sp=$sp" }
        }
    }

    /**
     * Cất vào xe ngựa (C:015-010)
     *
     * @return Vị trí (slot) trong xe ngựa, -1 nếu thất bại
     */
    fun storeToCart(characterId: Long, followSlot: Int, capacity: Int = MAX_CART): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return -1

            // Đang xuất chiến thì không thể cất
            if (pc.fightNpcId == pet.npcId) return -1

            // Tìm vị trí trống trong xe ngựa
            val cartSlot = (1..capacity.coerceIn(0, MAX_CART)).firstOrNull { !pc.cart.containsKey(it) } ?: return -1

            // Di chuyển
            pc.follow.remove(followSlot)
            pc.cart[cartSlot] = pet.copy(storageType = STORAGE_CART, slotIndex = cartSlot)
            pc.dirty = true
            logger.info { "Cất vào xe ngựa: charId=$characterId follow=$followSlot → cart=$cartSlot" }
            return cartSlot
        }
    }

    /**
     * Lấy ra từ xe ngựa (C:015-011)
     *
     * @return Vị trí (slot) mang theo, -1 nếu thất bại
     */
    fun retrieveFromCart(characterId: Long, cartSlot: Int): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val pet = pc.cart[cartSlot] ?: return -1

            // Tìm vị trí trống trong danh sách mang theo
            val followSlot = (1..MAX_FOLLOW).firstOrNull { !pc.follow.containsKey(it) } ?: return -1

            // Di chuyển
            pc.cart.remove(cartSlot)
            pc.follow[followSlot] = pet.copy(storageType = STORAGE_FOLLOW, slotIndex = followSlot)
            pc.dirty = true
            logger.info { "Lấy ra từ xe ngựa: charId=$characterId cart=$cartSlot → follow=$followSlot" }
            return followSlot
        }
    }

    /**
     * Tráo đổi võ tướng (C:015-012)
     *
     * @return true Thành công
     */
    fun swapFollowAndCart(characterId: Long, followSlot: Int, cartSlot: Int): Boolean {
        val pc = cache[characterId] ?: return false
        synchronized(pc) {
            val followPet = pc.follow[followSlot] ?: return false
            val cartPet = pc.cart[cartSlot] ?: return false

            // Đang xuất chiến thì không thể tráo đổi
            if (pc.fightNpcId == followPet.npcId) return false

            pc.follow[followSlot] = cartPet.copy(storageType = STORAGE_FOLLOW, slotIndex = followSlot)
            pc.cart[cartSlot] = followPet.copy(storageType = STORAGE_CART, slotIndex = cartSlot)
            pc.dirty = true
            logger.info { "Tráo đổi võ tướng: charId=$characterId follow=$followSlot ↔ cart=$cartSlot" }
            return true
        }
    }

    /**
     * Thay đổi HP của võ tướng mang theo (Khi dùng vật phẩm)
     *
     * Tham khảo tskero `useItemPet()` prop1=25.
     *
     * @param characterId ID nhân vật
     * @param followSlot Vị trí mang theo của võ tướng (hệ cơ số 1 / 1-based)
     * @param delta Lượng HP thay đổi
     * @return Lượng thay đổi thực tế, 0 nghĩa là thất bại
     */
    fun modifyFollowNpcHp(characterId: Long, followSlot: Int, delta: Long): Long {
        val pc = cache[characterId] ?: return 0
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return 0
            val oldHp = pet.hp
            val (maxHp, _) = petMaxHpSp(pet)
            val newHp = (oldHp + delta).coerceIn(0L, maxHp)
            pc.follow[followSlot] = pet.copy(hp = newHp)
            pc.dirty = true
            return newHp - oldHp
        }
    }

    /**
     * Thay đổi SP của võ tướng mang theo (Khi dùng vật phẩm)
     *
     * Tham khảo tskero `useItemPet()` prop1=26.
     *
     * @param characterId ID nhân vật
     * @param followSlot Vị trí mang theo của võ tướng (hệ cơ số 1 / 1-based)
     * @param delta Lượng SP thay đổi
     * @return Lượng thay đổi thực tế, 0 nghĩa là thất bại
     */
    fun modifyFollowNpcSp(characterId: Long, followSlot: Int, delta: Int): Int {
        val pc = cache[characterId] ?: return 0
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return 0
            val oldSp = pet.sp
            val (_, maxSp) = petMaxHpSp(pet)
            val newSp = (oldSp + delta).coerceIn(0, maxSp)
            pc.follow[followSlot] = pet.copy(sp = newSp)
            pc.dirty = true
            return newSp - oldSp
        }
    }

    /**
     * Lấy võ tướng mang theo
     */
    fun getFollowNpc(characterId: Long, followSlot: Int): FollowNpcData? =
        cache[characterId]?.follow?.get(followSlot)

    /**
     * Áp dụng kết quả cường hóa võ tướng (Cộng điểm vào 6 chỉ số (lục vi) + Thăng sao (star) +1)
     *
     * @param characterId ID nhân vật
     * @param followSlot Vị trí mang theo của võ tướng (hệ cơ số 1 / 1-based)
     * @param gains Giá trị cộng thêm cho 6 chỉ số, theo thứ tự [int, atk, def, agi, hpx, spx]
     * @return Dữ liệu võ tướng sau cường hóa, null = Võ tướng không tồn tại
     */
    fun applyUpgrade(characterId: Long, followSlot: Int, gains: IntArray): FollowNpcData? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return null
            val upgraded = pet.copy(
                intelligence = pet.intelligence + gains[0],
                atk = pet.atk + gains[1],
                def = pet.def + gains[2],
                agi = pet.agi + gains[3],
                hpx = pet.hpx + gains[4],
                spx = pet.spx + gains[5],
                upgradeLv = pet.upgradeLv + 1
            )
            pc.follow[followSlot] = upgraded
            pc.dirty = true
            return upgraded
        }
    }

    /**
     * Cập nhật một ô trang bị của võ tướng mang theo (Dùng cho hệ thống trang bị võ tướng)
     *
     * @param characterId ID nhân vật
     * @param followSlot Vị trí mang theo của võ tướng (hệ cơ số 1 / 1-based)
     * @param fitType Vị trí trang bị (1-6)
     * @param thing Vật phẩm trang bị, null = làm trống ô đó
     * @return Dữ liệu võ tướng sau cập nhật, null = Võ tướng không tồn tại
     */
    fun updateFollowNpcEquip(characterId: Long, followSlot: Int, fitType: Int, thing: ThingData?): FollowNpcData? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return null
            val newEquips = pet.equips.toMutableMap()
            if (thing == null || thing.isEmpty) {
                newEquips.remove(fitType)
            } else {
                newEquips[fitType] = thing
            }
            val updated = pet.copy(equips = newEquips)
            pc.follow[followSlot] = updated
            pc.dirty = true
            return updated
        }
    }

    /**
     * Kiểm tra xem NPC có đang nằm trên người không (Mang theo + Xe ngựa) — Dùng để kiểm tra trùng lặp khi chuyển sinh
     */
    fun hasNpcOnBody(characterId: Long, npcId: Int): Boolean {
        val pc = cache[characterId] ?: return false
        return pc.follow.values.any { it.npcId == npcId } || pc.cart.values.any { it.npcId == npcId }
    }

    /**
     * Kiểm tra xem NPC có đang trong khách sạn không (Bao gồm cả kho võ tướng) — Dùng để kiểm tra trùng lặp khi chuyển sinh
     */
    fun hasNpcInInn(characterId: Long, npcId: Int): Boolean {
        val pc = cache[characterId] ?: return false
        return pc.inn.values.any { it.npcId == npcId } || pc.warehouse.any { it.npcId == npcId }
    }

    /**
     * Chuyển sinh võ tướng — Lấy dữ liệu võ tướng mới thay thế vào vị trí cũ
     *
     * Võ tướng cũ sẽ biến mất (dữ liệu bị thiết lập lại hoàn toàn), nếu võ tướng cũ đang xuất chiến thì xóa bỏ trạng thái xuất chiến.
     *
     * @param characterId ID nhân vật
     * @param followSlot Vị trí mang theo (hệ cơ số 1 / 1-based)
     * @param newPet Dữ liệu võ tướng mới sau khi chuyển sinh (slotIndex bắt buộc phải bằng followSlot)
     * @return Dữ liệu võ tướng mới, null = Vị trí cũ không có võ tướng
     */
    fun rebornFollowNpc(characterId: Long, followSlot: Int, newPet: FollowNpcData): FollowNpcData? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val old = pc.follow[followSlot] ?: return null
            pc.follow[followSlot] = newPet
            if (pc.fightNpcId == old.npcId) {
                pc.fightNpcId = 0
            }
            pc.dirty = true
            logger.info { "Võ tướng chuyển sinh: charId=$characterId slot=$followSlot ${old.npcId} →${newPet.npcId}" }
            return newPet
        }
    }

    /**
     * Học kỹ năng đặc biệt của võ tướng (C:044-002)
     *
     * @return Dữ liệu võ tướng sau khi cập nhật, null = Võ tướng không tồn tại hoặc đã học rồi
     */
    fun learnSpecialSkill(characterId: Long, followSlot: Int): FollowNpcData? {
        val pc = cache[characterId] ?: return null
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return null
            if (pet.canUseSpecialSkill) return null
            val updated = pet.copy(canUseSpecialSkill = true)
            pc.follow[followSlot] = updated
            pc.dirty = true
            logger.info { "Học kỹ năng đặc biệt: charId=$characterId slot=$followSlot npcId=${pet.npcId}" }
            return updated
        }
    }

    /**
     * Lưu võ tướng của nhân vật vào DB (Không dỡ khỏi bộ nhớ, dùng để tự động lưu định kỳ)
     */
    suspend fun save(characterId: Long) {
        val pc = cache[characterId] ?: return
        if (pc.dirty) {
            val allPets = pc.follow.values.toList() + pc.cart.values.toList() +
                    pc.inn.values.toList() + pc.warehouse.toList()
            petRepository.saveAll(characterId, allPets)
            pc.dirty = false
        }
    }

    /**
     * Lưu võ tướng của nhân vật vào DB và dỡ khỏi bộ nhớ đệm (cache)
     */
    suspend fun saveAndUnload(characterId: Long) {
        val pc = cache.remove(characterId) ?: return
        if (pc.dirty) {
            val allPets = pc.follow.values.toList() + pc.cart.values.toList() +
                    pc.inn.values.toList() + pc.warehouse.toList()
            petRepository.saveAll(characterId, allPets)
            logger.info { "Lưu võ tướng: charId=$characterId Tổng cộng ${allPets.size} người" }
        }
    }

    // ── Thao tác khách sạn ──

    /**
     * Lấy danh sách võ tướng trong khách sạn
     */
    fun getInnPets(characterId: Long): Map<Int, FollowNpcData> =
        cache[characterId]?.inn ?: emptyMap()

    /**
     * Cất vào khách sạn (C:031-003)
     *
     * @return Vị trí (slot) trong khách sạn, -1 nếu thất bại
     */
    fun storeToInn(characterId: Long, followSlot: Int): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return -1

            // Đang xuất chiến thì không thể cất
            if (pc.fightNpcId == pet.npcId) return -1

            // Võ tướng không thể trưởng thành thì không được cất vào khách sạn (canGrow=0 hoặc canGrow=2 Võ tướng nhiệm vụ)
            if (pet.canGrow != 1) return -1

            // Tìm vị trí trống trong khách sạn (hệ cơ số 1 / 1-based)
            val innSlot = (1..MAX_INN).firstOrNull { !pc.inn.containsKey(it) } ?: return -1

            // Di chuyển
            pc.follow.remove(followSlot)
            pc.inn[innSlot] = pet.copy(storageType = STORAGE_INN, slotIndex = innSlot)
            pc.dirty = true
            logger.info { "Cất vào khách sạn: charId=$characterId follow=$followSlot → inn=$innSlot" }
            return innSlot
        }
    }

    /**
     * Lấy ra từ khách sạn (C:031-002)
     *
     * @return Vị trí (slot) mang theo, -1 nếu thất bại
     */
    fun retrieveFromInn(characterId: Long, innSlot: Int): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val pet = pc.inn[innSlot] ?: return -1

            // Tìm vị trí trống mang theo
            val followSlot = (1..MAX_FOLLOW).firstOrNull { !pc.follow.containsKey(it) } ?: return -1

            // Di chuyển
            pc.inn.remove(innSlot)
            pc.follow[followSlot] = pet.copy(storageType = STORAGE_FOLLOW, slotIndex = followSlot)
            pc.dirty = true
            logger.info { "Lấy ra từ khách sạn: charId=$characterId inn=$innSlot → follow=$followSlot" }
            return followSlot
        }
    }

    /**
     * Tráo đổi võ tướng giữa khách sạn và mang theo (C:031-004)
     *
     * @return true Thành công
     */
    fun swapInnAndFollow(characterId: Long, innSlot: Int, followSlot: Int): Boolean {
        val pc = cache[characterId] ?: return false
        synchronized(pc) {
            val innPet = pc.inn[innSlot] ?: return false
            val followPet = pc.follow[followSlot] ?: return false

            // Đang xuất chiến thì không thể tráo đổi
            if (pc.fightNpcId == followPet.npcId) return false

            // Võ tướng không thể trưởng thành thì không được cất vào khách sạn
            if (followPet.canGrow != 1) return false

            pc.follow[followSlot] = innPet.copy(storageType = STORAGE_FOLLOW, slotIndex = followSlot)
            pc.inn[innSlot] = followPet.copy(storageType = STORAGE_INN, slotIndex = innSlot)
            pc.dirty = true
            logger.info { "Tráo đổi Khách sạn ↔ Mang theo: charId=$characterId inn=$innSlot ↔ follow=$followSlot" }
            return true
        }
    }

    /**
     * Lấy võ tướng trong khách sạn (Theo vị trí (slot) chỉ định)
     */
    fun getInnNpc(characterId: Long, innSlot: Int): FollowNpcData? =
        cache[characterId]?.inn?.get(innSlot)

    // ── Thao tác kho võ tướng ──

    /**
     * Lấy danh sách kho võ tướng
     */
    fun getWarehousePets(characterId: Long): List<FollowNpcData> =
        cache[characterId]?.warehouse?.toList() ?: emptyList()

    /**
     * Cất vào kho võ tướng (C:122-002)
     *
     * @return true Thành công
     */
    fun storeToWarehouse(characterId: Long, followSlot: Int): Boolean {
        val pc = cache[characterId] ?: return false
        synchronized(pc) {
            val pet = pc.follow[followSlot] ?: return false

            // Đang xuất chiến thì không thể cất
            if (pc.fightNpcId == pet.npcId) return false

            // Kiểm tra sức chứa của kho
            if (pc.warehouse.size >= MAX_WAREHOUSE) return false

            // Di chuyển (Kho dùng list, slotIndex dùng list index)
            pc.follow.remove(followSlot)
            pc.warehouse.add(pet.copy(storageType = STORAGE_WAREHOUSE, slotIndex = pc.warehouse.size))
            pc.dirty = true
            logger.info { "Cất vào kho võ tướng: charId=$characterId follow=$followSlot" }
            return true
        }
    }

    /**
     * Lấy ra từ kho võ tướng (C:122-003)
     *
     * @return Vị trí (slot) mang theo, -1 nếu thất bại
     */
    fun retrieveFromWarehouse(characterId: Long, npcId: Int): Int {
        val pc = cache[characterId] ?: return -1
        synchronized(pc) {
            val pet = pc.warehouse.find { it.npcId == npcId } ?: return -1

            // Tìm vị trí trống mang theo
            val followSlot = (1..MAX_FOLLOW).firstOrNull { !pc.follow.containsKey(it) } ?: return -1

            // Di chuyển
            pc.warehouse.remove(pet)
            pc.follow[followSlot] = pet.copy(storageType = STORAGE_FOLLOW, slotIndex = followSlot)
            pc.dirty = true
            logger.info { "Lấy ra từ kho võ tướng: charId=$characterId npcId=$npcId → follow=$followSlot" }
            return followSlot
        }
    }

    /**
     * Nghỉ trọ hồi phục toàn bộ HP + SP cho cả đội (Dùng cho tính năng nghỉ trọ tại Inn)
     *
     * Hồi phục HP và SP của tất cả võ tướng mang theo đến giá trị tối đa thực tế.
     * Quy tắc tính toán (Calculator.lua L183-194):
     * - turnKind >= 2 → dùng công thức của turn=1
     * - turnKind < 2 → dùng công thức của turn=0
     * - extraHp = hpPillCount * 20 (Role.hpPillAdd=20)
     * - extraSp = spPillCount * 5 (Role.spPillAdd=5)
     */
    fun restoreAllFollowHpSp(characterId: Long) {
        val pc = cache[characterId] ?: return
        synchronized(pc) {
            for ((slot, pet) in pc.follow) {
                val (maxHp, maxSp) = petMaxHpSp(pet)
                pc.follow[slot] = pet.copy(hp = maxHp, sp = maxSp)
            }
            pc.dirty = true
        }
    }
}

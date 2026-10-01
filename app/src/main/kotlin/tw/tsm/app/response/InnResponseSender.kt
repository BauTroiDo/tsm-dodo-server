package tw.tsm.app.response

import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.protocol.PacketWriter

/**
 * tuần tự hoá gói tin lữ quán/kho tướng
 *
 * mainKind = 31 (lữ quán), 122 (kho tướng)
 */
class InnResponseSender {

    /**
     * S:031-001 kết quả nghỉ trọ
     *
     * Định dạng: result(1B) 0=thành công, 1=không đủ tiền vàng, 2=HP đã đầy
     */
    fun sendInnRestResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(1).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-002 xác nhận nghỉ trọ (thông báo chi phí)
     *
     * Định dạng: cost(4B LE)
     */
    fun sendInnRestConfirm(session: SessionSender, cost: Int) {
        val writer = PacketWriter(7)
            .writeByte(31).writeByte(2).writeByte(0)
            .writeIntLE(cost)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-003 xác nhận lưu tướng vào lữ quán
     *
     * Định dạng: innSlot(1B) + followSlot(1B)
     */
    fun sendInnStoreConfirm(session: SessionSender, innSlot: Int, followSlot: Int) {
        val writer = PacketWriter(5)
            .writeByte(31).writeByte(3).writeByte(0)
            .writeByte(innSlot).writeByte(followSlot)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-004 xoá tướng ở lữ quán
     *
     * Định dạng: innSlot(1B)
     */
    fun sendInnDeleteNpc(session: SessionSender, innSlot: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(4).writeByte(0)
            .writeByte(innSlot)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-006 dữ liệu tướng lữ quán
     *
     * Định dạng: <<innSlot(1B) + npcId(2B LE) + level(1B) + exp(4B LE) + hp(4B LE) + nameLen(1B) + name(Big5) + status(1B)>>
     * Nguồn: Inn.lua:29-35 Inn.SaveNpc()
     */
    fun sendInnNpcData(session: SessionSender, innPets: Map<Int, FollowNpcData>, dispatchSlots: Set<Int> = emptySet()) {
        val writer = PacketWriter(3 + innPets.size * 20)
            .writeByte(31).writeByte(6).writeByte(0)
        for ((slot, pet) in innPets) {
            writer.writeByte(slot)
            writer.writeShortLE(pet.npcId)
            writer.writeByte(pet.level)
            writer.writeIntLE(pet.exp.toInt())
            writer.writeIntLE(pet.hp.toInt())
            writer.writeBig5WithByteLen(pet.name)
            // DVT ENpcInnStatus: 0=Common, 1=Dispatch
            writer.writeByte(if (slot in dispatchSlots) 1 else 0)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:031-007 mở UI kho tướng
     *
     * gói tin rỗng (chỉ header)
     */
    fun sendOpenWarriorRepository(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(7).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-008 dừng hoạt ảnh nhận tướng
     */
    fun sendInnRetrieveAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(8).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-009 dừng hoạt ảnh lưu tướng
     */
    fun sendInnStoreAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(9).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-010 hiệu ứng nghỉ ngơi
     *
     * Định dạng: kind(1B) 1=lữ quán, 2=trướng hành quân
     */
    fun sendInnRestAnimation(session: SessionSender, kind: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(10).writeByte(0)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-012 dừng hoạt ảnh trao đổi
     */
    fun sendInnSwapAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(12).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-015 cấp độ lữ quán
     *
     * Định dạng: level(1B)
     */
    fun sendInnLevel(session: SessionSender, level: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(15).writeByte(0)
            .writeByte(level)
        session.send(writer.toByteArray())
    }

    // ── Kho tướng (mainKind=122) ──

    /**
     * S:122-001 thông tin kho tướng
     *
     * Định dạng: count(4B LE) + <<định dạng tướng đầy đủ × count>>
     * Định dạng mỗi tướng (Inn.lua:155-186 AddWarriorRepositoryWithInfo):
     * npcId(2) + hp(4) + sp(2) + int(2) + atk(2) + def(2) + agi(2) + hpx(2) + spx(2)
     * + level(1) + exp(4) + skillPoint(2) + dieCount(1) + skillLv[4](4×1B)
     * + conjureGrow(2) + sublimeCount(1) + isSpecSkill(1) + soulId(2)
     * + graspPillCount(1) + SPPillCount(1) + HPPillCount(1)
     * + upGradeLv(1) + turnKind(1) + surplus(1) + lvUpTime(8 double)
     */
    fun sendWarehouseData(session: SessionSender, pets: List<FollowNpcData>) {
        val writer = PacketWriter(7 + pets.size * 52)
            .writeByte(122).writeByte(1).writeByte(0)
            .writeIntLE(pets.size)
        for (pet in pets) {
            writer.writeShortLE(pet.npcId)
            writer.writeIntLE(pet.hp.toInt())
            writer.writeShortLE(pet.sp)
            writer.writeShortLE(pet.intelligence)
            writer.writeShortLE(pet.atk)
            writer.writeShortLE(pet.def)
            writer.writeShortLE(pet.agi)
            writer.writeShortLE(pet.hpx)
            writer.writeShortLE(pet.spx)
            writer.writeByte(pet.level)
            writer.writeIntLE(pet.exp.toInt())
            writer.writeShortLE(pet.skillPoint)
            writer.writeByte(pet.deathCount)
            writer.writeByte(pet.skill1Lv)
            writer.writeByte(pet.skill2Lv)
            writer.writeByte(pet.skill3Lv)
            writer.writeByte(pet.skill4Lv)
            writer.writeShortLE(pet.conjureGrow)
            writer.writeByte(pet.ascendPillCount)
            writer.writeByte(if (pet.canUseSpecialSkill) 1 else 0)
            writer.writeShortLE(pet.soulId)
            // graspPillCount (hoàn bắt thú) — tạm dùng 0
            writer.writeByte(0)
            writer.writeByte(pet.spPillCount)
            writer.writeByte(pet.hpPillCount)
            writer.writeByte(pet.upgradeLv)
            writer.writeByte(pet.turnKind)
            writer.writeByte(pet.surplus)
            writer.writeDoubleLE(pet.lvUpTime)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:122-002 kết quả lưu vào kho tướng
     *
     * Định dạng: result(1B) 1=thành công, 2=không gian đã đầy, 255=thất bại
     */
    fun sendWarehouseStoreResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(122).writeByte(2).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }

    /**
     * S:122-003 kết quả lấy ra khỏi kho tướng
     *
     * Định dạng: result(1B) 1=thành công, 255=thất bại
     */
    fun sendWarehouseRetrieveResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(122).writeByte(3).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }
}

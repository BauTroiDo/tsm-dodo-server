package tw.tsm.app.response

import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.protocol.PacketWriter

/**
 * 客棧/武將倉庫封包序列化
 *
 * mainKind = 31（客棧）, 122（武將倉庫）
 */
class InnResponseSender {

    /**
     * S:031-001 住宿結果
     *
     * 格式：result(1B) 0=成功, 1=金錢不足, 2=HP已滿
     */
    fun sendInnRestResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(1).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-002 住宿確認（費用通知）
     *
     * 格式：cost(4B LE)
     */
    fun sendInnRestConfirm(session: SessionSender, cost: Int) {
        val writer = PacketWriter(7)
            .writeByte(31).writeByte(2).writeByte(0)
            .writeIntLE(cost)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-003 客棧存武將確認
     *
     * 格式：innSlot(1B) + followSlot(1B)
     */
    fun sendInnStoreConfirm(session: SessionSender, innSlot: Int, followSlot: Int) {
        val writer = PacketWriter(5)
            .writeByte(31).writeByte(3).writeByte(0)
            .writeByte(innSlot).writeByte(followSlot)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-004 客棧刪武將
     *
     * 格式：innSlot(1B)
     */
    fun sendInnDeleteNpc(session: SessionSender, innSlot: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(4).writeByte(0)
            .writeByte(innSlot)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-006 客棧武將資料
     *
     * 格式：<<innSlot(1B) + npcId(2B LE) + level(1B) + exp(4B LE) + hp(4B LE) + nameLen(1B) + name(Big5) + status(1B)>>
     * 來源：Inn.lua:29-35 Inn.SaveNpc()
     */
    fun sendInnNpcData(session: SessionSender, innPets: Map<Int, FollowNpcData>) {
        val writer = PacketWriter(3 + innPets.size * 20)
            .writeByte(31).writeByte(6).writeByte(0)
        for ((slot, pet) in innPets) {
            writer.writeByte(slot)
            writer.writeShortLE(pet.npcId)
            writer.writeByte(pet.level)
            writer.writeIntLE(pet.exp.toInt())
            writer.writeIntLE(pet.hp.toInt())
            writer.writeBig5WithByteLen(pet.name)
            // status: 0=正常, 1=陣亡
            writer.writeByte(pet.surplus)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:031-007 開啟武將倉庫 UI
     *
     * 空包（僅 header）
     */
    fun sendOpenWarriorRepository(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(7).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-008 領武將動畫停止
     */
    fun sendInnRetrieveAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(8).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-009 存武將動畫停止
     */
    fun sendInnStoreAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(9).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-010 休息演出
     *
     * 格式：kind(1B) 1=客棧, 2=行軍帳
     */
    fun sendInnRestAnimation(session: SessionSender, kind: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(10).writeByte(0)
            .writeByte(kind)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-012 交換動畫停止
     */
    fun sendInnSwapAnimStop(session: SessionSender) {
        val writer = PacketWriter(3)
            .writeByte(31).writeByte(12).writeByte(0)
        session.send(writer.toByteArray())
    }

    /**
     * S:031-015 客棧等級
     *
     * 格式：level(1B)
     */
    fun sendInnLevel(session: SessionSender, level: Int) {
        val writer = PacketWriter(4)
            .writeByte(31).writeByte(15).writeByte(0)
            .writeByte(level)
        session.send(writer.toByteArray())
    }

    // ── 武將倉庫 (mainKind=122) ──

    /**
     * S:122-001 武將倉庫資訊
     *
     * 格式：count(4B LE) + <<完整武將格式 × count>>
     * 每筆武將格式（Inn.lua:155-186 AddWarriorRepositoryWithInfo）：
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
            // graspPillCount（捉寵丸）— 暫用 0
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
     * S:122-002 存入武將倉庫結果
     *
     * 格式：result(1B) 1=成功, 2=空間已滿, 255=失敗
     */
    fun sendWarehouseStoreResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(122).writeByte(2).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }

    /**
     * S:122-003 取出武將倉庫結果
     *
     * 格式：result(1B) 1=成功, 255=失敗
     */
    fun sendWarehouseRetrieveResult(session: SessionSender, result: Int) {
        val writer = PacketWriter(4)
            .writeByte(122).writeByte(3).writeByte(0)
            .writeByte(result)
        session.send(writer.toByteArray())
    }
}

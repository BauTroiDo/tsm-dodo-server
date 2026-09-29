package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.ThingDataWire
import tw.tsm.protocol.writeThingData

/**
 * tuần tự hoá gói tin liên quan vật phẩm/trang bị/tiền vàng/giao dịch/Tiền Trang
 *
 * mainKind = 23 (túi đồ/trang bị/vật phẩm cảnh), 25 (giao dịch), 26 (tiền vàng), 27 (NPC mua bán), 30 (Tiền Trang)
 */
class InventoryResponseSender {

    // ── Túi đồ (mainKind=23) ──

    /** S:023-005 toàn bộ vật phẩm túi đồ*/
    fun sendAllBagItems(session: SessionSender, items: Map<Int, ThingData>, refresh: Boolean) {
        val writer = PacketWriter(6 + items.size * (2 + ThingDataWire.SIZE))
            .writeByte(Opcodes.SHOP).writeByte(5).writeByte(0)
            .writeByte(if (refresh) 1 else 0)
            .writeShortLE(items.size)
        for ((slot, thing) in items) {
            writer.writeShortLE(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-008 đặt vật phẩm túi đồ*/
    fun sendSetBagItem(session: SessionSender, slot: Int, thing: ThingData, showMsg: Boolean) {
        session.send(
            PacketWriter(40)
                .writeByte(Opcodes.SHOP).writeByte(8).writeByte(0)
                .writeShortLE(slot)
                .writeThingData(thing)
                .writeBoolean(showMsg)
                .toByteArray()
        )
    }

    /** S:023-009 giảm vật phẩm túi đồ*/
    fun sendReduceBagItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.SHOP).writeByte(9).writeByte(0)
                .writeShortLE(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:023-010 di chuyển vật phẩm túi đồ*/
    fun sendMoveBagItem(session: SessionSender, fromSlot: Int, amount: Int, toSlot: Int) {
        session.send(
            PacketWriter(9)
                .writeByte(Opcodes.SHOP).writeByte(10).writeByte(0)
                .writeShortLE(fromSlot)
                .writeIntLE(amount)
                .writeShortLE(toSlot)
                .toByteArray()
        )
    }

    // ── Trang bị (mainKind=23) ──

    /** S:023-011 toàn bộ trang bị*/
    fun sendAllEquipment(session: SessionSender, equips: Map<Int, ThingData>) {
        val writer = PacketWriter(4 + equips.size * ThingDataWire.SIZE)
            .writeByte(Opcodes.SHOP).writeByte(11).writeByte(0)
            .writeByte(equips.size)
        for ((_, thing) in equips) {
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-012 xoá trang bị*/
    fun sendDeleteEquip(session: SessionSender, fitType: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.SHOP).writeByte(12).writeByte(0)
                .writeByte(fitType)
                .toByteArray()
        )
    }

    /** S:023-015 dùng vật phẩm thành công*/
    fun sendUseItemSuccess(session: SessionSender, itemId: Int, count: Int, showMsg: Boolean) {
        session.send(
            PacketWriter(10)
                .writeByte(Opcodes.SHOP).writeByte(15).writeByte(0)
                .writeShortLE(itemId)
                .writeIntLE(count)
                .writeBoolean(showMsg)
                .toByteArray()
        )
    }

    /** S:023-016 cởi trang bị*/
    fun sendUnequip(session: SessionSender, fitType: Int, bagSlot: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(16).writeByte(0)
                .writeByte(fitType)
                .writeShortLE(bagSlot)
                .toByteArray()
        )
    }

    /** S:023-017 mặc trang bị*/
    fun sendEquip(session: SessionSender, bagSlot: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.SHOP).writeByte(17).writeByte(0)
                .writeShortLE(bagSlot)
                .toByteArray()
        )
    }

    // ── Trang bị tướng (mainKind=23) ──

    /** S:023-021 tướng cởi trang bị (xoá trực tiếp, dùng khi ném đi)*/
    fun sendPetEquipDelete(session: SessionSender, followSlot: Int, fitType: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(21).writeByte(0)
                .writeByte(followSlot)
                .writeByte(fitType)
                .toByteArray()
        )
    }

    /** S:023-022 tướng cởi trang bị vào túi đồ*/
    fun sendPetUnequipToBag(session: SessionSender, followSlot: Int, fitType: Int, bagSlot: Int) {
        session.send(
            PacketWriter(6)
                .writeByte(Opcodes.SHOP).writeByte(22).writeByte(0)
                .writeByte(followSlot)
                .writeByte(fitType)
                .writeShortLE(bagSlot)
                .toByteArray()
        )
    }

    /**
     * S:023-023 tướng mặc trang bị
     *
     * client nhận xong tự đổi ô túi đồ ↔ ô trang bị (protocal.lua:3992-4012).
     */
    fun sendPetEquip(session: SessionSender, followSlot: Int, bagSlot: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(23).writeByte(0)
                .writeByte(followSlot)
                .writeShortLE(bagSlot)
                .toByteArray()
        )
    }

    /** S:023-025 Kết quả nhặt*/
    fun sendPickupResult(session: SessionSender, result: Int, kind: Int, value: Int, x: Int, y: Int) {
        val writer = PacketWriter(12)
            .writeByte(Opcodes.SHOP).writeByte(25).writeByte(0)
            .writeByte(result)
        if (result == 0) {
            writer.writeShortLE(kind)
            writer.writeShortLE(value)
            writer.writeShortLE(x)
            writer.writeShortLE(y)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-003 thêm vật phẩm cảnh*/
    fun sendAddGroundItem(session: SessionSender, itemId: Int, x: Int, y: Int, isMe: Int, canPickPlayerId: Long) {
        session.send(
            PacketWriter(18)
                .writeByte(Opcodes.SHOP).writeByte(3).writeByte(0)
                .writeShortLE(itemId)
                .writeShortLE(x)
                .writeShortLE(y)
                .writeByte(isMe)
                .writeLongLE(canPickPlayerId)
                .toByteArray()
        )
    }

    /** S:023-002 xoá vật phẩm cảnh*/
    fun sendRemoveGroundItem(session: SessionSender, index: Int, isMe: Boolean) {
        session.send(
            PacketWriter(6)
                .writeByte(Opcodes.SHOP).writeByte(2).writeByte(0)
                .writeShortLE(index)
                .writeBoolean(isMe)
                .toByteArray()
        )
    }

    /** S:023-018 ném tiền vàng ra cảnh*/
    fun sendDropMoneyToGround(session: SessionSender, amount: Int, x: Int, y: Int, isMe: Boolean) {
        session.send(
            PacketWriter(12)
                .writeByte(Opcodes.SHOP).writeByte(18).writeByte(0)
                .writeIntLE(amount)
                .writeShortLE(x)
                .writeShortLE(y)
                .writeBoolean(isMe)
                .toByteArray()
        )
    }

    // ── Tiền (mainKind=26) ──

    /** S:026-004 khởi tạo tiền vàng*/
    fun sendInitMoney(session: SessionSender, gold: Int, auctionGold: Int) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.MONEY).writeByte(4).writeByte(0)
                .writeIntLE(gold)
                .writeIntLE(auctionGold)
                .toByteArray()
        )
    }

    /** S:026-001 tăng tiền vàng*/
    fun sendAddMoney(session: SessionSender, amount: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.MONEY).writeByte(1).writeByte(0)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:026-002 giảm tiền vàng*/
    fun sendSubtractMoney(session: SessionSender, amount: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.MONEY).writeByte(2).writeByte(0)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:026-003 sức chứa tiền vàng đã đầy*/
    fun sendMoneyFull(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.MONEY).writeByte(3).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    // ── NPC mua bán (mainKind=27) ──

    /** S:027-001 kết quả mua đồ*/
    fun sendBuyResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_TRADE).writeByte(1).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:027-002 kết quả bán đồ*/
    fun sendSellResult(session: SessionSender, result: Int, bagIndices: List<Int>) {
        val writer = PacketWriter(5 + bagIndices.size)
            .writeByte(Opcodes.NPC_TRADE).writeByte(2).writeByte(0)
            .writeByte(result)
            .writeByte(bagIndices.size)
        for (idx in bagIndices) {
            writer.writeByte(idx)
        }
        session.send(writer.toByteArray())
    }

    /** S:027-003 mở giao diện NPC bán đồ*/
    fun sendOpenNpcStore(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_TRADE).writeByte(3).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    // ── Tiền Trang (mainKind=30) ──

    /** S:030-001 toàn bộ vật phẩm Tiền Trang*/
    fun sendBankAllItems(session: SessionSender, items: Map<Int, ThingData>) {
        val writer = PacketWriter(3 + items.size * (2 + ThingDataWire.SIZE))
            .writeByte(Opcodes.BANK).writeByte(1).writeByte(0)
        for ((slot, thing) in items) {
            writer.writeShortLE(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:030-004 đặt vật phẩm Tiền Trang*/
    fun sendBankSetItem(session: SessionSender, slot: Int, thing: ThingData) {
        session.send(
            PacketWriter(5 + ThingDataWire.SIZE)
                .writeByte(Opcodes.BANK).writeByte(4).writeByte(0)
                .writeShortLE(slot)
                .writeThingData(thing)
                .toByteArray()
        )
    }

    /** S:030-005 giảm vật phẩm Tiền Trang*/
    fun sendBankReduceItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.BANK).writeByte(5).writeByte(0)
                .writeShortLE(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:030-007 thao tác Tiền Trang thất bại*/
    fun sendBankFail(session: SessionSender, failCode: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.BANK).writeByte(7).writeByte(0)
                .writeByte(failCode)
                .toByteArray()
        )
    }

    /** S:030-008 xoá sạch vật phẩm Tiền Trang*/
    fun sendBankClear(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.BANK).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    // ── Điều khiển Tiền Trang (mainKind=29) ──

    /** S:029-006 mở UI Tiền Trang*/
    fun sendOpenBank(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.BANK_CONTROL).writeByte(6).writeByte(0)
                .toByteArray()
        )
    }

    /** S:029-009 cấp độ Tiền Trang*/
    fun sendBankLevel(session: SessionSender, level: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.BANK_CONTROL).writeByte(9).writeByte(0)
                .writeByte(level)
                .toByteArray()
        )
    }

    // ── Bảo khố (mainKind=102) ──

    /** S:102-001 toàn bộ vật phẩm kho báu*/
    fun sendStorageAllItems(session: SessionSender, items: Map<Int, ThingData>) {
        val writer = PacketWriter(3 + items.size * (1 + ThingDataWire.SIZE))
            .writeByte(Opcodes.STORAGE).writeByte(1).writeByte(0)
        for ((slot, thing) in items) {
            writer.writeByte(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:102-004 đặt vật phẩm kho báu*/
    fun sendStorageSetItem(session: SessionSender, slot: Int, thing: ThingData) {
        session.send(
            PacketWriter(4 + ThingDataWire.SIZE)
                .writeByte(Opcodes.STORAGE).writeByte(4).writeByte(0)
                .writeByte(slot)
                .writeThingData(thing)
                .toByteArray()
        )
    }

    /** S:102-005 giảm vật phẩm kho báu*/
    fun sendStorageReduceItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.STORAGE).writeByte(5).writeByte(0)
                .writeByte(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:102-007 Thao tác kho báu thất bại*/
    fun sendStorageFail(session: SessionSender, failCode: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.STORAGE).writeByte(7).writeByte(0)
                .writeByte(failCode)
                .toByteArray()
        )
    }

    /** S:102-008 Xoá sạch vật phẩm trong kho báu*/
    fun sendStorageClear(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.STORAGE).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    // ── Dịch chuyển đã ghi nhớ (mainKind=23) ──

    /** S:023-118 dữ liệu phù dịch chuyển ký ức*/
    fun sendFlyPointData(session: SessionSender, flyPointCount: Int, points: Map<Int, FlyPointRecord>) {
        val writer = PacketWriter(5 + points.size * 7)
            .writeByte(Opcodes.SHOP).writeByte(118).writeByte(0)
            .writeByte(flyPointCount)          // Số lượng mở thêm đã mở
            .writeByte(points.size)            // Số điểm dịch chuyển đã ghi nhớ
        for ((index, record) in points) {
            writer.writeByte(index)            // Chỉ số (1-based)
            writer.writeShortLE(record.sceneId) // ID cảnh
            writer.writeShortLE(record.x)      // Toạ độ X
            writer.writeShortLE(record.y)      // Toạ độ Y
        }
        session.send(writer.toByteArray())
    }

    // ── Giao dịch người chơi (mainKind=25) ──

    /** S:025-001 bắt đầu giao dịch vật phẩm*/
    fun sendTradeStart(session: SessionSender, partnerId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.TRADE).writeByte(1).writeByte(0)
                .writeLongLE(partnerId)
                .toByteArray()
        )
    }

    /** S:025-002 giao dịch hủy/hoàn thành*/
    fun sendTradeCancel(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.TRADE).writeByte(2).writeByte(0)
                .writeByte(reason)
                .toByteArray()
        )
    }

    /**
     * S:025-003 xác nhận giao dịch vật phẩm — hiển thị giá bên kia đưa ra
     *
     * cách client đọc: ReadInt32() tiền vàng, rồi for 1~25 dùng data.length > 0 để kiểm tra
     */
    fun sendTradeConfirm(session: SessionSender, money: Int, items: List<ThingData>) {
        val writer = PacketWriter(7 + items.size * 35)
            .writeByte(Opcodes.TRADE).writeByte(3).writeByte(0)
            .writeIntLE(money)
        for (item in items) {
            writer.writeThingData(item)
        }
        session.send(writer.toByteArray())
    }

    /** S:025-010 bắt đầu giao dịch tướng*/
    fun sendPetTradeStart(session: SessionSender, partnerId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.TRADE).writeByte(10).writeByte(0)
                .writeLongLE(partnerId)
                .toByteArray()
        )
    }

    /** S:025-011 giao dịch tướng hủy/hoàn thành*/
    fun sendPetTradeCancel(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.TRADE).writeByte(11).writeByte(0)
                .writeByte(reason)
                .toByteArray()
        )
    }

    /**
     * S:025-012 xác nhận giao dịch tướng — hiển thị giá bên kia đưa ra
     *
     * định dạng theo cách client phân tích làm chuẩn (UITrade.lua:227-255, chú thích protocal.lua đã cũ):
     * +tiền(4) +NPCID(2) [+cấp(1) +MaxHp(2) +Hp(2) +MaxSp(2) +Sp(2) +L(1)+tên(L)
     * +lòng trung thành(1) +đan thăng hoa(1) +đan SP(1) +đan HP(1) +số lượng trang bị(1) <<+ThingData(35)>>]
     * khi bên kia không đưa tướng ra thì NPCID=0, client không tìm thấy npcData sẽ xoá sạch vùng hiển thị.
     */
    fun sendPetTradeConfirm(session: SessionSender, money: Int, pet: FollowNpcData?, maxHp: Int, maxSp: Int) {
        if (pet == null) {
            session.send(
                PacketWriter(9)
                    .writeByte(Opcodes.TRADE).writeByte(12).writeByte(0)
                    .writeIntLE(money)
                    .writeShortLE(0)
                    .toByteArray()
            )
            return
        }

        val equips = pet.equips.values.filter { !it.isEmpty }
        val writer = PacketWriter(32 + pet.name.length * 2 + equips.size * 35)
            .writeByte(Opcodes.TRADE).writeByte(12).writeByte(0)
            .writeIntLE(money)
            .writeShortLE(pet.npcId)
            .writeByte(pet.level)
            .writeShortLE(maxHp)
            .writeShortLE(pet.hp.toInt().coerceAtMost(65535))
            .writeShortLE(maxSp)
            .writeShortLE(pet.sp)
            .writeBig5WithByteLen(pet.name)
            .writeByte(pet.loyalty)
            .writeByte(pet.ascendPillCount)
            .writeByte(pet.spPillCount)
            .writeByte(pet.hpPillCount)
            .writeByte(equips.size)
        for (equip in equips) {
            writer.writeThingData(equip)
        }
        session.send(writer.toByteArray())
    }
}

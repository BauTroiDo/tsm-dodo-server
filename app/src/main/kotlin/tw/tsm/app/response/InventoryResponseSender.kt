package tw.tsm.app.response

import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.valueobjects.FollowNpcData
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * 物品/裝備/金錢/交易/錢莊相關封包序列化
 *
 * mainKind = 23（背包/裝備/場景物品）, 25（交易）, 26（金錢）, 27（NPC 買賣）, 30（錢莊）
 */
class InventoryResponseSender {

    // ── 背包 (mainKind=23) ──

    /** S:023-005 背包所有物品 */
    fun sendAllBagItems(session: SessionSender, items: Map<Int, ThingData>, refresh: Boolean) {
        val writer = PacketWriter(4 + items.size * 36)
            .writeByte(Opcodes.SHOP).writeByte(5).writeByte(0)
            .writeByte(if (refresh) 1 else 0)
            .writeShortLE(items.size)
        for ((slot, thing) in items) {
            writer.writeByte(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-008 背包設定物品 */
    fun sendSetBagItem(session: SessionSender, slot: Int, thing: ThingData, showMsg: Boolean) {
        session.send(
            PacketWriter(40)
                .writeByte(Opcodes.SHOP).writeByte(8).writeByte(0)
                .writeByte(slot)
                .writeThingData(thing)
                .writeBoolean(showMsg)
                .toByteArray()
        )
    }

    /** S:023-009 背包減少物品 */
    fun sendReduceBagItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.SHOP).writeByte(9).writeByte(0)
                .writeByte(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:023-010 背包移動物品 */
    fun sendMoveBagItem(session: SessionSender, fromSlot: Int, amount: Int, toSlot: Int) {
        session.send(
            PacketWriter(9)
                .writeByte(Opcodes.SHOP).writeByte(10).writeByte(0)
                .writeByte(fromSlot)
                .writeIntLE(amount)
                .writeByte(toSlot)
                .toByteArray()
        )
    }

    // ── 裝備 (mainKind=23) ──

    /** S:023-011 所有裝備 */
    fun sendAllEquipment(session: SessionSender, equips: Map<Int, ThingData>) {
        val writer = PacketWriter(4 + equips.size * 35)
            .writeByte(Opcodes.SHOP).writeByte(11).writeByte(0)
            .writeByte(equips.size)
        for ((_, thing) in equips) {
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:023-012 刪除裝備 */
    fun sendDeleteEquip(session: SessionSender, fitType: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.SHOP).writeByte(12).writeByte(0)
                .writeByte(fitType)
                .toByteArray()
        )
    }

    /** S:023-015 使用物品成功 */
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

    /** S:023-016 卸下裝備 */
    fun sendUnequip(session: SessionSender, fitType: Int, bagSlot: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(16).writeByte(0)
                .writeByte(fitType)
                .writeByte(bagSlot)
                .toByteArray()
        )
    }

    /** S:023-017 穿上裝備 */
    fun sendEquip(session: SessionSender, bagSlot: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.SHOP).writeByte(17).writeByte(0)
                .writeByte(bagSlot)
                .toByteArray()
        )
    }

    // ── 武將裝備 (mainKind=23) ──

    /** S:023-021 武將卸下裝備（直接刪除，丟棄用） */
    fun sendPetEquipDelete(session: SessionSender, followSlot: Int, fitType: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(21).writeByte(0)
                .writeByte(followSlot)
                .writeByte(fitType)
                .toByteArray()
        )
    }

    /** S:023-022 武將卸下裝備到背包 */
    fun sendPetUnequipToBag(session: SessionSender, followSlot: Int, fitType: Int, bagSlot: Int) {
        session.send(
            PacketWriter(6)
                .writeByte(Opcodes.SHOP).writeByte(22).writeByte(0)
                .writeByte(followSlot)
                .writeByte(fitType)
                .writeByte(bagSlot)
                .toByteArray()
        )
    }

    /**
     * S:023-023 武將穿上裝備
     *
     * 客戶端收到後自行做背包格 ↔ 裝備格互換（protocal.lua:3992-4012）。
     */
    fun sendPetEquip(session: SessionSender, followSlot: Int, bagSlot: Int) {
        session.send(
            PacketWriter(5)
                .writeByte(Opcodes.SHOP).writeByte(23).writeByte(0)
                .writeByte(followSlot)
                .writeByte(bagSlot)
                .toByteArray()
        )
    }

    /** S:023-025 拾取結果 */
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

    /** S:023-003 新增場景物品 */
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

    /** S:023-002 刪除場景物品 */
    fun sendRemoveGroundItem(session: SessionSender, index: Int, isMe: Boolean) {
        session.send(
            PacketWriter(6)
                .writeByte(Opcodes.SHOP).writeByte(2).writeByte(0)
                .writeShortLE(index)
                .writeBoolean(isMe)
                .toByteArray()
        )
    }

    /** S:023-018 丟棄金錢到場景 */
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

    // ── 金錢 (mainKind=26) ──

    /** S:026-004 初始化金錢 */
    fun sendInitMoney(session: SessionSender, gold: Int, auctionGold: Int) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.MONEY).writeByte(4).writeByte(0)
                .writeIntLE(gold)
                .writeIntLE(auctionGold)
                .toByteArray()
        )
    }

    /** S:026-001 增加金錢 */
    fun sendAddMoney(session: SessionSender, amount: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.MONEY).writeByte(1).writeByte(0)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:026-002 減少金錢 */
    fun sendSubtractMoney(session: SessionSender, amount: Int) {
        session.send(
            PacketWriter(7)
                .writeByte(Opcodes.MONEY).writeByte(2).writeByte(0)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:026-003 金錢容量已滿 */
    fun sendMoneyFull(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.MONEY).writeByte(3).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    // ── NPC 買賣 (mainKind=27) ──

    /** S:027-001 買東西結果 */
    fun sendBuyResult(session: SessionSender, result: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_TRADE).writeByte(1).writeByte(0)
                .writeByte(result)
                .toByteArray()
        )
    }

    /** S:027-002 賣東西結果 */
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

    /** S:027-003 開啟 NPC 賣東西介面 */
    fun sendOpenNpcStore(session: SessionSender, kind: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.NPC_TRADE).writeByte(3).writeByte(0)
                .writeByte(kind)
                .toByteArray()
        )
    }

    // ── 錢莊 (mainKind=30) ──

    /** S:030-001 錢莊所有物品 */
    fun sendBankAllItems(session: SessionSender, items: Map<Int, ThingData>) {
        val writer = PacketWriter(3 + items.size * 36)
            .writeByte(Opcodes.BANK).writeByte(1).writeByte(0)
        for ((slot, thing) in items) {
            writer.writeByte(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:030-004 錢莊設定物品 */
    fun sendBankSetItem(session: SessionSender, slot: Int, thing: ThingData) {
        session.send(
            PacketWriter(39)
                .writeByte(Opcodes.BANK).writeByte(4).writeByte(0)
                .writeByte(slot)
                .writeThingData(thing)
                .toByteArray()
        )
    }

    /** S:030-005 錢莊減少物品 */
    fun sendBankReduceItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.BANK).writeByte(5).writeByte(0)
                .writeByte(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:030-007 錢莊操作失敗 */
    fun sendBankFail(session: SessionSender, failCode: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.BANK).writeByte(7).writeByte(0)
                .writeByte(failCode)
                .toByteArray()
        )
    }

    /** S:030-008 清空錢莊物品 */
    fun sendBankClear(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.BANK).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    // ── 錢莊控制 (mainKind=29) ──

    /** S:029-006 開啟錢莊 UI */
    fun sendOpenBank(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.BANK_CONTROL).writeByte(6).writeByte(0)
                .toByteArray()
        )
    }

    /** S:029-009 錢莊等級 */
    fun sendBankLevel(session: SessionSender, level: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.BANK_CONTROL).writeByte(9).writeByte(0)
                .writeByte(level)
                .toByteArray()
        )
    }

    // ── 寶庫 (mainKind=102) ──

    /** S:102-001 寶庫所有物品 */
    fun sendStorageAllItems(session: SessionSender, items: Map<Int, ThingData>) {
        val writer = PacketWriter(3 + items.size * 36)
            .writeByte(Opcodes.STORAGE).writeByte(1).writeByte(0)
        for ((slot, thing) in items) {
            writer.writeByte(slot)
            writer.writeThingData(thing)
        }
        session.send(writer.toByteArray())
    }

    /** S:102-004 寶庫設定物品 */
    fun sendStorageSetItem(session: SessionSender, slot: Int, thing: ThingData) {
        session.send(
            PacketWriter(39)
                .writeByte(Opcodes.STORAGE).writeByte(4).writeByte(0)
                .writeByte(slot)
                .writeThingData(thing)
                .toByteArray()
        )
    }

    /** S:102-005 寶庫減少物品 */
    fun sendStorageReduceItem(session: SessionSender, slot: Int, amount: Int) {
        session.send(
            PacketWriter(8)
                .writeByte(Opcodes.STORAGE).writeByte(5).writeByte(0)
                .writeByte(slot)
                .writeIntLE(amount)
                .toByteArray()
        )
    }

    /** S:102-007 寶庫操作失敗 */
    fun sendStorageFail(session: SessionSender, failCode: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.STORAGE).writeByte(7).writeByte(0)
                .writeByte(failCode)
                .toByteArray()
        )
    }

    /** S:102-008 清空寶庫物品 */
    fun sendStorageClear(session: SessionSender) {
        session.send(
            PacketWriter(3)
                .writeByte(Opcodes.STORAGE).writeByte(8).writeByte(0)
                .toByteArray()
        )
    }

    // ── 記憶傳送 (mainKind=23) ──

    /** S:023-118 記憶傳送符資料 */
    fun sendFlyPointData(session: SessionSender, flyPointCount: Int, points: Map<Int, FlyPointRecord>) {
        val writer = PacketWriter(5 + points.size * 7)
            .writeByte(Opcodes.SHOP).writeByte(118).writeByte(0)
            .writeByte(flyPointCount)          // 已開啟的額外數量
            .writeByte(points.size)            // 已記憶的傳送點數量
        for ((index, record) in points) {
            writer.writeByte(index)            // 索引（1-based）
            writer.writeShortLE(record.sceneId) // 場景 ID
            writer.writeShortLE(record.x)      // X 座標
            writer.writeShortLE(record.y)      // Y 座標
        }
        session.send(writer.toByteArray())
    }

    // ── 玩家交易 (mainKind=25) ──

    /** S:025-001 交易物品開始 */
    fun sendTradeStart(session: SessionSender, partnerId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.TRADE).writeByte(1).writeByte(0)
                .writeLongLE(partnerId)
                .toByteArray()
        )
    }

    /** S:025-002 交易取消/完成 */
    fun sendTradeCancel(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.TRADE).writeByte(2).writeByte(0)
                .writeByte(reason)
                .toByteArray()
        )
    }

    /**
     * S:025-003 交易物品確認 — 展示對方出價
     *
     * 客戶端讀法：ReadInt32() 金錢，然後 for 1~25 用 data.length > 0 判斷
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

    /** S:025-010 交易武將開始 */
    fun sendPetTradeStart(session: SessionSender, partnerId: Long) {
        session.send(
            PacketWriter(11)
                .writeByte(Opcodes.TRADE).writeByte(10).writeByte(0)
                .writeLongLE(partnerId)
                .toByteArray()
        )
    }

    /** S:025-011 交易武將取消/完成 */
    fun sendPetTradeCancel(session: SessionSender, reason: Int) {
        session.send(
            PacketWriter(4)
                .writeByte(Opcodes.TRADE).writeByte(11).writeByte(0)
                .writeByte(reason)
                .toByteArray()
        )
    }

    /**
     * S:025-012 交易武將確認 — 展示對方出價
     *
     * 格式以客戶端解析為準（UITrade.lua:227-255，protocal.lua 註解已過時）：
     * +金錢(4) +NPCID(2) [+等級(1) +MaxHp(2) +Hp(2) +MaxSp(2) +Sp(2) +L(1)+名字(L)
     * +忠誠(1) +昇華丹(1) +SP丹(1) +HP丹(1) +裝備數量(1) <<+ThingData(35)>>]
     * 對方不出武將時 NPCID=0，客戶端查無 npcData 即清空展示區。
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

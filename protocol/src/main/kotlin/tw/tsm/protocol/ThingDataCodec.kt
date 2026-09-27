package tw.tsm.protocol

import tw.tsm.domain.valueobjects.ThingData

/**
 * Tuần tự hóa / Giải tuần tự hóa mạng ThingData — 35 bytes
 *
 * Đối chiếu chặt chẽ với `Item.lua:81-100` (Đọc) và `Item.lua:138-158` (Ghi).
 * Thứ tự các trường:
 * Id(2) quant(4) damage(1) element(1) elementValue(1) proofKind(1)
 * growLv(1) growExp(4) specialKind(1) stoneAttr(1) stoneLv(1) enhanceLv(1)
 * delTime(8) damagedItemId(2) isLock(1) Reinforced(1)
 * affix1(1) affix2(1) affix3(1) styleLv(1) = 35 bytes
 */

/**
 * Ghi ThingData vào gói tin (35 bytes)
 */
fun PacketWriter.writeThingData(thing: ThingData): PacketWriter {
    writeShortLE(thing.itemId)         // Id(2)
    writeIntLE(thing.quantity)         // quant(4)
    writeByte(thing.damage)            // damage(1)
    writeByte(thing.element)           // element(1)
    writeByte(thing.elementValue)      // elementValue(1)
    writeByte(thing.proofKind)         // proofKind(1)
    writeByte(thing.growLevel)         // growLv(1)
    writeIntLE(thing.growExp)          // growExp(4)
    writeByte(thing.specialKind)       // specialKind(1)
    writeByte(thing.stoneAttr)         // stoneAttr(1)
    writeByte(thing.stoneLevel)        // stoneLv(1)
    writeByte(thing.enhanceLevel)      // enhanceLv(1)
    writeDoubleLE(thing.deleteTime)    // delTime(8)
    writeShortLE(thing.damagedItemId)  // damagedItemId(2)
    writeBoolean(thing.isLocked)       // isLock(1)
    writeByte(thing.reinforced)        // Reinforced(1)
    writeByte(thing.affix1)            // affix1(1)
    writeByte(thing.affix2)            // affix2(1)
    writeByte(thing.affix3)            // affix3(1)
    writeByte(thing.styleLevel)        // styleLv(1)
    return this
}

/**
 * Đọc ThingData từ gói tin (35 bytes)
 */
fun PacketReader.readThingData(): ThingData = ThingData(
    itemId = readUShortLE(),           // Id(2)
    quantity = readIntLE(),            // quant(4)
    damage = readUByte(),              // damage(1)
    element = readUByte(),             // element(1)
    elementValue = readUByte(),        // elementValue(1)
    proofKind = readUByte(),           // proofKind(1)
    growLevel = readUByte(),           // growLv(1)
    growExp = readIntLE(),             // growExp(4)
    specialKind = readUByte(),         // specialKind(1)
    stoneAttr = readUByte(),           // stoneAttr(1)
    stoneLevel = readUByte(),          // stoneLv(1)
    enhanceLevel = readUByte(),        // enhanceLv(1)
    deleteTime = readDoubleLE(),       // delTime(8)
    damagedItemId = readUShortLE(),    // damagedItemId(2)
    isLocked = readBoolean(),          // isLock(1)
    reinforced = readUByte(),          // Reinforced(1)
    affix1 = readUByte(),              // affix1(1)
    affix2 = readUByte(),              // affix2(1)
    affix3 = readUByte(),              // affix3(1)
    styleLevel = readUByte()           // styleLv(1)
)
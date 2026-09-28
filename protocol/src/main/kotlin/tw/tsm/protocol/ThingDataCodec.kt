package tw.tsm.protocol

import tw.tsm.domain.valueobjects.ThingData

/**
 * Tuần tự hóa / Giải tuần tự hóa mạng ThingData của client DVT.
 *
 * Lua DVT Item.lua ThingData.New/WriteBuffer luôn đọc/ghi phần cơ sở 35 bytes và,
 * khi build có define NFT, đọc/ghi thêm 21 bytes:
 * mythLv(1) + 4 x [kind(1) + value(2) + extra(2)].
 *
 * Client DVT hiện tại chạy với NFT bật, vì vậy wire size thực tế là 56 bytes.
 * Server chưa mô hình hóa thuộc tính NFT/Myth; phần mở rộng được gửi bằng 0 để giữ
 * đúng ranh giới record và không tạo thuộc tính giả.
 */
object ThingDataWire {
    const val BASE_SIZE = 35
    const val NFT_EXTENSION_SIZE = 21
    const val SIZE = BASE_SIZE + NFT_EXTENSION_SIZE
}

/** Ghi ThingData theo wire DVT NFT (56 bytes). */
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

    // DVT NFT extension. Server chưa quản lý Myth/NFT item attributes nên gửi 0.
    writeByte(0)                       // mythLv
    repeat(4) {
        writeByte(0)                   // myth[i].kind
        writeShortLE(0)                // myth[i].value
        writeShortLE(0)                // myth[i].extra
    }
    return this
}

/**
 * Đọc ThingData theo wire DVT NFT (56 bytes).
 *
 * Hiện model [ThingData] chưa có trường Myth/NFT nên 21 bytes mở rộng được tiêu thụ
 * nhưng không lưu lại. Điều này vẫn giữ đúng alignment cho record kế tiếp.
 */
fun PacketReader.readThingData(): ThingData {
    val thing = ThingData(
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
        stoneLevel = readUByte(),           // stoneLv(1)
        enhanceLevel = readUByte(),         // enhanceLv(1)
        deleteTime = readDoubleLE(),        // delTime(8)
        damagedItemId = readUShortLE(),     // damagedItemId(2)
        isLocked = readBoolean(),           // isLock(1)
        reinforced = readUByte(),           // Reinforced(1)
        affix1 = readUByte(),               // affix1(1)
        affix2 = readUByte(),               // affix2(1)
        affix3 = readUByte(),               // affix3(1)
        styleLevel = readUByte()             // styleLv(1)
    )

    readUByte() // mythLv
    repeat(4) {
        readUByte()    // kind
        readUShortLE() // value
        readUShortLE() // extra
    }
    return thing
}

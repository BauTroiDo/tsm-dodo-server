package tw.tsm.protocol

import tw.tsm.domain.valueobjects.MythAttribute
import tw.tsm.domain.valueobjects.ThingData

/**
 * Tuần tự hóa / Giải tuần tự hóa mạng ThingData của client DVT.
 *
 * Lua DVT Item.lua ThingData.New/WriteBuffer luôn đọc/ghi phần cơ sở 35 bytes và,
 * khi build có define NFT, đọc/ghi thêm 21 bytes:
 * mythLv(1) + 4 x [kind(1) + value(2) + extra(2)].
 *
 * Client DVT hiện tại chạy với NFT bật, vì vậy wire size thực tế là 56 bytes.
 * D7 round-trip đầy đủ phần Myth/NFT thay vì zero/drop metadata.
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

    // DVT NFT extension.
    writeByte(thing.mythLevel)          // mythLv
    repeat(4) { index ->
        val attr = thing.myth[index]
        writeByte(attr.kind)            // myth[i].kind
        writeShortLE(attr.value)        // myth[i].value
        writeShortLE(attr.extra)        // myth[i].extra
    }
    return this
}

/** Read DVT NFT ThingData (56 bytes) and preserve all Myth metadata. */
fun PacketReader.readThingData(): ThingData {
    val base = ThingData(
        itemId = readUShortLE(),
        quantity = readIntLE(),
        damage = readUByte(),
        element = readUByte(),
        elementValue = readUByte(),
        proofKind = readUByte(),
        growLevel = readUByte(),
        growExp = readIntLE(),
        specialKind = readUByte(),
        stoneAttr = readUByte(),
        stoneLevel = readUByte(),
        enhanceLevel = readUByte(),
        deleteTime = readDoubleLE(),
        damagedItemId = readUShortLE(),
        isLocked = readBoolean(),
        reinforced = readUByte(),
        affix1 = readUByte(),
        affix2 = readUByte(),
        affix3 = readUByte(),
        styleLevel = readUByte()
    )

    val mythLevel = readUByte()
    val myth = List(4) {
        MythAttribute(
            kind = readUByte(),
            value = readUShortLE(),
            extra = readUShortLE()
        )
    }
    return base.copy(mythLevel = mythLevel, myth = myth)
}

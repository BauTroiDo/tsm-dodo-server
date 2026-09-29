package tw.tsm.app.response

import tw.tsm.domain.constants.MailConstants
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.interfaces.SessionSender
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.protocol.PacketWriter
import tw.tsm.protocol.writeThingData

/**
 * Tuần tự hoá gói tin hệ thống thư
 *
 * mainKind = 83
 * Nguồn: protocal.lua protocolTable[83], Social.lua AddMail
 */
class MailResponseSender {

    /**
     * S:083-001 thêm thư mới
     *
     * Định dạng: +count(4B LE) <<+mailId(4B) +time(8B Double) +state(1B)
     *       +contentLen(2B LE) +content(Big5) +attachCount(1B)
     *       <<+kind(1B) +data(ThingData/NpcData)>>>>
     */
    fun sendMails(session: SessionSender, mails: List<MailRecord>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MAIL).writeByte(1).writeByte(0)
            .writeIntLE(mails.size)

        for (mail in mails) {
            writer
                .writeIntLE(mail.id)
                .writeDoubleLE(mail.expireTime)
                .writeByte(mail.state)
                .writeBig5WithShortLen(mail.content)
                .writeByte(mail.attachments.size)

            for (attach in mail.attachments) {
                writer.writeByte(attach.kind)
                when (attach.kind) {
                    MailConstants.ATTACH_KIND_ITEM -> {
                        val thing = attach.thingData
                        if (thing != null) {
                            writer.writeThingData(thing)
                        }
                    }
                    MailConstants.ATTACH_KIND_NPC -> {
                        val npcBytes = attach.npcData
                        if (npcBytes != null) {
                            writer.writeBytes(npcBytes)
                        }
                    }
                }
            }
        }

        session.send(writer.toByteArray())
    }

    /**
     * S:083-002 xác nhận nhận thư
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailTakeConfirm(session: SessionSender, mailIds: List<Int>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MAIL).writeByte(2).writeByte(0)
            .writeIntLE(mailIds.size)
        for (mailId in mailIds) {
            writer.writeIntLE(mailId)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:083-003 xác nhận xoá thư
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailDeleteConfirm(session: SessionSender, mailIds: List<Int>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MAIL).writeByte(3).writeByte(0)
            .writeIntLE(mailIds.size)
        for (mailId in mailIds) {
            writer.writeIntLE(mailId)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:083-004 xác nhận thư đã đọc
     *
     * Định dạng: +count(4B LE) <<+mailId(4B LE)>>
     */
    fun sendMailReadConfirm(session: SessionSender, mailIds: List<Int>) {
        val writer = PacketWriter()
            .writeByte(Opcodes.MAIL).writeByte(4).writeByte(0)
            .writeIntLE(mailIds.size)
        for (mailId in mailIds) {
            writer.writeIntLE(mailId)
        }
        session.send(writer.toByteArray())
    }

    /**
     * S:083-005 mã lỗi của thư
     *
     * Định dạng: +kind(1B)
     */
    fun sendMailError(session: SessionSender, errorKind: Int) {
        session.send(
            PacketWriter()
                .writeByte(Opcodes.MAIL).writeByte(5).writeByte(0)
                .writeByte(errorKind)
                .toByteArray()
        )
    }
}

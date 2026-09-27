package tw.tsm.persistence.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.MailAttachmentData
import tw.tsm.domain.interfaces.repositories.MailRecord
import tw.tsm.domain.interfaces.repositories.MailRepository
import tw.tsm.domain.valueobjects.ThingData
import tw.tsm.persistence.tables.MailAttachments
import tw.tsm.persistence.tables.Mails

private val logger = KotlinLogging.logger {}

/**
 * 信件資料庫操作實作
 */
class MailRepositoryImpl : MailRepository {

    override suspend fun findByCharId(charId: Long): List<MailRecord> = suspendTransaction {
        val mails = Mails.selectAll().where { Mails.charId eq charId }
            .map { row ->
                MailRecord(
                    id = row[Mails.id],
                    charId = row[Mails.charId],
                    expireTime = row[Mails.expireTime],
                    state = row[Mails.state],
                    content = row[Mails.content]
                )
            }

        if (mails.isEmpty()) return@suspendTransaction emptyList()

        val mailIds = mails.map { it.id }
        val attachMap = MailAttachments.selectAll()
            .where { MailAttachments.mailId inList mailIds }
            .map { row -> row[MailAttachments.mailId] to rowToAttachment(row) }
            .groupBy({ it.first }, { it.second })

        mails.map { mail ->
            mail.copy(attachments = attachMap[mail.id] ?: emptyList())
        }
    }

    override suspend fun create(
        charId: Long,
        content: String,
        expireTime: Double,
        attachments: List<MailAttachmentData>
    ): Int = suspendTransaction {
        val mailId = Mails.insert { row ->
            row[Mails.charId] = charId
            row[Mails.expireTime] = expireTime
            row[Mails.state] = 0
            row[Mails.content] = content
            row[Mails.createdAt] = System.currentTimeMillis()
        } get Mails.id

        if (attachments.isNotEmpty()) {
            MailAttachments.batchInsert(attachments) { att ->
                this[MailAttachments.mailId] = mailId
                this[MailAttachments.slot] = att.slot
                this[MailAttachments.kind] = att.kind
                writeThingData(att)
            }
        }

        logger.info { "建立信件：mailId=$mailId, charId=$charId, 附件=${attachments.size}" }
        mailId
    }

    override suspend fun batchUpdateState(charId: Long, mailIds: List<Int>, state: Int): Unit =
        suspendTransaction {
            Mails.update({ (Mails.id inList mailIds) and (Mails.charId eq charId) }) {
                it[Mails.state] = state
            }
        }

    override suspend fun batchDelete(charId: Long, mailIds: List<Int>): Unit = suspendTransaction {
        MailAttachments.deleteWhere { MailAttachments.mailId inList mailIds }
        Mails.deleteWhere { (Mails.id inList mailIds) and (Mails.charId eq charId) }
    }

    override suspend fun findById(charId: Long, mailId: Int): MailRecord? = suspendTransaction {
        val mail = Mails.selectAll()
            .where { (Mails.id eq mailId) and (Mails.charId eq charId) }
            .firstOrNull() ?: return@suspendTransaction null

        val attachments = MailAttachments.selectAll()
            .where { MailAttachments.mailId eq mailId }
            .map { row -> rowToAttachment(row) }

        MailRecord(
            id = mail[Mails.id],
            charId = mail[Mails.charId],
            expireTime = mail[Mails.expireTime],
            state = mail[Mails.state],
            content = mail[Mails.content],
            attachments = attachments
        )
    }

    /** 將 DB row 轉換為 MailAttachmentData */
    private fun rowToAttachment(row: org.jetbrains.exposed.v1.core.ResultRow): MailAttachmentData {
        val kind = row[MailAttachments.kind]
        return MailAttachmentData(
            slot = row[MailAttachments.slot],
            kind = kind,
            thingData = if (kind == 1) ThingData(
                itemId = row[MailAttachments.itemId],
                quantity = row[MailAttachments.quantity],
                damage = row[MailAttachments.damage],
                element = row[MailAttachments.element],
                elementValue = row[MailAttachments.elementValue],
                proofKind = row[MailAttachments.proofKind],
                growLevel = row[MailAttachments.growLevel],
                growExp = row[MailAttachments.growExp],
                specialKind = row[MailAttachments.specialKind],
                stoneAttr = row[MailAttachments.stoneAttr],
                stoneLevel = row[MailAttachments.stoneLevel],
                enhanceLevel = row[MailAttachments.enhanceLevel],
                deleteTime = row[MailAttachments.deleteTime],
                damagedItemId = row[MailAttachments.damagedItemId],
                isLocked = row[MailAttachments.isLocked],
                reinforced = row[MailAttachments.reinforced],
                affix1 = row[MailAttachments.affix1],
                affix2 = row[MailAttachments.affix2],
                affix3 = row[MailAttachments.affix3],
                styleLevel = row[MailAttachments.styleLevel]
            ) else null,
            npcData = if (kind == 2) row[MailAttachments.npcData]?.bytes else null
        )
    }

    /** 將 ThingData 寫入 MailAttachments batch insert */
    private fun org.jetbrains.exposed.v1.core.statements.BatchInsertStatement.writeThingData(att: MailAttachmentData) {
        val td = att.thingData
        if (td != null) {
            this[MailAttachments.itemId] = td.itemId
            this[MailAttachments.quantity] = td.quantity
            this[MailAttachments.damage] = td.damage
            this[MailAttachments.element] = td.element
            this[MailAttachments.elementValue] = td.elementValue
            this[MailAttachments.proofKind] = td.proofKind
            this[MailAttachments.growLevel] = td.growLevel
            this[MailAttachments.growExp] = td.growExp
            this[MailAttachments.specialKind] = td.specialKind
            this[MailAttachments.stoneAttr] = td.stoneAttr
            this[MailAttachments.stoneLevel] = td.stoneLevel
            this[MailAttachments.enhanceLevel] = td.enhanceLevel
            this[MailAttachments.deleteTime] = td.deleteTime
            this[MailAttachments.damagedItemId] = td.damagedItemId
            this[MailAttachments.isLocked] = td.isLocked
            this[MailAttachments.reinforced] = td.reinforced
            this[MailAttachments.affix1] = td.affix1
            this[MailAttachments.affix2] = td.affix2
            this[MailAttachments.affix3] = td.affix3
            this[MailAttachments.styleLevel] = td.styleLevel
        }
        val npc = att.npcData
        if (npc != null) {
            this[MailAttachments.npcData] = org.jetbrains.exposed.v1.core.statements.api.ExposedBlob(npc)
        }
    }
}

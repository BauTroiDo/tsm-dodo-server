package tw.tsm.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tw.tsm.persistence.tables.Accounts
import tw.tsm.persistence.tables.CharacterBitFlags
import tw.tsm.persistence.tables.CharacterMissionFlags
import tw.tsm.persistence.tables.CharacterMissions
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.Characters
import tw.tsm.persistence.tables.CharacterMountEquips
import tw.tsm.persistence.tables.CharacterMounts
import tw.tsm.persistence.tables.CharacterDispatches
import tw.tsm.persistence.tables.CharacterPetEquips
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.persistence.tables.CharacterHotkeys
import tw.tsm.persistence.tables.CharacterFlyPoints
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterUiSellCounts
import tw.tsm.persistence.tables.CharacterSkills
import tw.tsm.persistence.tables.CharacterTurn2Skills
import tw.tsm.persistence.tables.CharacterTurn3States
import tw.tsm.persistence.tables.CharacterTurn3Skills
import tw.tsm.persistence.tables.FriendInvites
import tw.tsm.persistence.tables.Friends
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.MailAttachments
import tw.tsm.persistence.tables.Mails

private val logger = KotlinLogging.logger {}

/**
 * 資料庫初始化工廠
 *
 * 使用 HikariCP 連線池 + Exposed ORM
 */
object DatabaseFactory {

    /**
     * 初始化資料庫連線並建立表結構
     */
    fun init(url: String, user: String, password: String, maxPoolSize: Int = 10) {
        val config = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        // 自動建表（含補齊新增欄位）
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts, Characters, Inventories, CharacterMoney,
                CharacterMissions, CharacterMissionFlags, CharacterCompletedEvents, CharacterBitFlags,
                CharacterSkills, CharacterTurn2Skills, CharacterTurn3States, CharacterTurn3Skills,
                CharacterPets,
                CharacterPetEquips,
                CharacterMounts,
                CharacterMountEquips,
                CharacterHotkeys,
                CharacterRoleCounts,
                CharacterFlyPoints,
                CharacterUiSellCounts,
                CharacterDispatches,
                Friends, FriendInvites,
                Mails, MailAttachments
            )
        }

        logger.info { "資料庫初始化完成：$url（連線池 $maxPoolSize）" }
    }
}

package tw.tsm.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tw.tsm.persistence.tables.Accounts
import tw.tsm.persistence.tables.CharacterAstrolabes
import tw.tsm.persistence.tables.CharacterBitFlags
import tw.tsm.persistence.tables.CharacterMissionFlags
import tw.tsm.persistence.tables.CharacterMissions
import tw.tsm.persistence.tables.CharacterCompletedEvents
import tw.tsm.persistence.tables.CharacterCollectionCards
import tw.tsm.persistence.tables.CharacterCollectionStyles
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterMining
import tw.tsm.persistence.tables.CharacterCurrencies
import tw.tsm.persistence.tables.Characters
import tw.tsm.persistence.tables.CharacterMountEquips
import tw.tsm.persistence.tables.CharacterMounts
import tw.tsm.persistence.tables.CharacterDispatchDaily
import tw.tsm.persistence.tables.CharacterDispatches
import tw.tsm.persistence.tables.CharacterPetEquips
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.persistence.tables.CharacterHotkeys
import tw.tsm.persistence.tables.CharacterFlyPoints
import tw.tsm.persistence.tables.CharacterRoleCounts
import tw.tsm.persistence.tables.CharacterUiSellCounts
import tw.tsm.persistence.tables.CharacterSlotMachines
import tw.tsm.persistence.tables.CharacterSlotMachineAwards
import tw.tsm.persistence.tables.CharacterSkills
import tw.tsm.persistence.tables.CharacterTurn2Skills
import tw.tsm.persistence.tables.CharacterTurn3States
import tw.tsm.persistence.tables.CharacterTurn3Skills
import tw.tsm.persistence.tables.FriendInvites
import tw.tsm.persistence.tables.Friends
import tw.tsm.persistence.tables.Inventories
import tw.tsm.persistence.tables.MailAttachments
import tw.tsm.persistence.tables.Mails
import tw.tsm.persistence.tables.MessageBoardMessages

private val logger = KotlinLogging.logger {}

/**
 * Factory khởi tạo cơ sở dữ liệu
 *
 * Dùng pool kết nối HikariCP + Exposed ORM
 */
object DatabaseFactory {

    /**
     * Khởi tạo kết nối cơ sở dữ liệu và tạo cấu trúc bảng
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

        // Tự tạo bảng (kèm bổ sung các trường mới)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Accounts, Characters, Inventories, CharacterMoney, CharacterCurrencies,
                CharacterAstrolabes,
                CharacterMissions, CharacterMissionFlags, CharacterCompletedEvents, CharacterBitFlags,
                CharacterSkills, CharacterTurn2Skills, CharacterTurn3States, CharacterTurn3Skills,
                CharacterPets,
                CharacterPetEquips,
                CharacterMounts,
                CharacterMountEquips,
                CharacterHotkeys,
                CharacterRoleCounts,
                CharacterFlyPoints,
                CharacterMining,
                CharacterUiSellCounts,
                CharacterSlotMachines, CharacterSlotMachineAwards,
                CharacterCollectionStyles, CharacterCollectionCards,
                CharacterDispatches, CharacterDispatchDaily,
                Friends, FriendInvites,
                Mails, MailAttachments,
                MessageBoardMessages
            )
        }

        logger.info { "Khởi tạo cơ sở dữ liệu xong: $url (pool kết nối $maxPoolSize)" }
    }
}

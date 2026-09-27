package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import tw.tsm.domain.interfaces.repositories.HotkeyEntry
import tw.tsm.domain.interfaces.repositories.HotkeyRepository
import tw.tsm.persistence.tables.CharacterHotkeys

/**
 * 快捷鍵持久層實作
 */
class HotkeyRepositoryImpl : HotkeyRepository {

    override suspend fun findByCharId(charId: Long): List<HotkeyEntry> = suspendTransaction {
        CharacterHotkeys.selectAll()
            .where { CharacterHotkeys.characterId eq charId }
            .map { row ->
                HotkeyEntry(
                    dataIndex = row[CharacterHotkeys.dataIndex],
                    keyIndex = row[CharacterHotkeys.keyIndex],
                    useId = row[CharacterHotkeys.useId]
                )
            }
    }

    override suspend fun save(charId: Long, dataIndex: Int, keyIndex: Int, useId: Int) = suspendTransaction {
        // 先刪除舊的
        CharacterHotkeys.deleteWhere {
            (CharacterHotkeys.characterId eq charId) and
                (CharacterHotkeys.dataIndex eq dataIndex) and
                (CharacterHotkeys.keyIndex eq keyIndex)
        }
        // 只在 useId != 0 時插入（0 = 清除快捷鍵）
        if (useId != 0) {
            CharacterHotkeys.insert {
                it[CharacterHotkeys.characterId] = charId
                it[CharacterHotkeys.dataIndex] = dataIndex
                it[CharacterHotkeys.keyIndex] = keyIndex
                it[CharacterHotkeys.useId] = useId
            }
        }
        Unit
    }

    override suspend fun deleteByCharId(charId: Long) = suspendTransaction {
        CharacterHotkeys.deleteWhere { CharacterHotkeys.characterId eq charId }
        Unit
    }
}

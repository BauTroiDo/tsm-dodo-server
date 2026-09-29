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
 * Triển khai tầng persistence của phím tắt
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
        // Xoá bản cũ trước
        CharacterHotkeys.deleteWhere {
            (CharacterHotkeys.characterId eq charId) and
                (CharacterHotkeys.dataIndex eq dataIndex) and
                (CharacterHotkeys.keyIndex eq keyIndex)
        }
        // Chỉ chèn khi useId != 0 (0 = xoá phím tắt)
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

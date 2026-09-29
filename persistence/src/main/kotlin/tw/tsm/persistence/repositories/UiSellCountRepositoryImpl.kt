package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.UiSellCountRepository
import tw.tsm.persistence.tables.CharacterUiSellCounts

/**
 * Triển khai UiSellCountRepository bằng Exposed ORM
 *
 * Dùng bảng character_ui_sell_counts lưu số lần mua 介消 (mua bán qua bảng).
 */
class UiSellCountRepositoryImpl : UiSellCountRepository {

    override suspend fun getCount(charId: Long, sellId: Int): Int =
        suspendTransaction {
            CharacterUiSellCounts.selectAll()
                .where { (CharacterUiSellCounts.charId eq charId) and (CharacterUiSellCounts.sellId eq sellId) }
                .singleOrNull()
                ?.get(CharacterUiSellCounts.count)
                ?: 0
        }

    override suspend fun increment(charId: Long, sellId: Int): Int =
        suspendTransaction {
            val current = CharacterUiSellCounts.selectAll()
                .where { (CharacterUiSellCounts.charId eq charId) and (CharacterUiSellCounts.sellId eq sellId) }
                .singleOrNull()
                ?.get(CharacterUiSellCounts.count)
                ?: 0
            val newCount = current + 1
            CharacterUiSellCounts.upsert {
                it[CharacterUiSellCounts.charId] = charId
                it[CharacterUiSellCounts.sellId] = sellId
                it[count] = newCount
            }
            newCount
        }
}

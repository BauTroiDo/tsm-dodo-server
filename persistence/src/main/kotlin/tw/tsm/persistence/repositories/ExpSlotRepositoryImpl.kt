package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.ExpSlotRepository
import tw.tsm.persistence.tables.ExpSlotStates

class ExpSlotRepositoryImpl : ExpSlotRepository {
    override suspend fun loadJackpotExp(): Long = suspendTransaction {
        ExpSlotStates.selectAll()
            .where { ExpSlotStates.id eq STATE_ID }
            .singleOrNull()
            ?.get(ExpSlotStates.jackpotExp)
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    override suspend fun saveJackpotExp(value: Long) {
        suspendTransaction {
            ExpSlotStates.upsert {
                it[id] = STATE_ID
                it[jackpotExp] = value.coerceAtLeast(0L)
            }
        }
    }

    companion object {
        private const val STATE_ID = 1
    }
}

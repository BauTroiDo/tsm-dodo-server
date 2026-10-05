package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.GiftRecord
import tw.tsm.domain.interfaces.repositories.HouseBoardRepository
import tw.tsm.domain.interfaces.repositories.HouseProfile
import tw.tsm.persistence.tables.CharacterGiftRecords
import tw.tsm.persistence.tables.CharacterHouseProfiles

class HouseBoardRepositoryImpl : HouseBoardRepository {
    override suspend fun getProfile(characterId: Long): HouseProfile = suspendTransaction {
        CharacterHouseProfiles.selectAll()
            .where { CharacterHouseProfiles.characterId eq characterId }
            .firstOrNull()
            ?.let { row ->
                HouseProfile(
                    characterId = characterId,
                    sign = row[CharacterHouseProfiles.sign],
                    birthMonth = row[CharacterHouseProfiles.birthMonth],
                    birthDay = row[CharacterHouseProfiles.birthDay],
                    charm = row[CharacterHouseProfiles.charm],
                    signature = row[CharacterHouseProfiles.signature],
                    impression = row[CharacterHouseProfiles.impression],
                    favorite = row[CharacterHouseProfiles.favorite],
                    area = row[CharacterHouseProfiles.area],
                    hobby = row[CharacterHouseProfiles.hobby],
                )
            } ?: HouseProfile(characterId)
    }

    override suspend fun saveProfile(profile: HouseProfile) {
        suspendTransaction {
            val exists = CharacterHouseProfiles.selectAll()
                .where { CharacterHouseProfiles.characterId eq profile.characterId }
                .firstOrNull() != null
            if (exists) {
                CharacterHouseProfiles.update({ CharacterHouseProfiles.characterId eq profile.characterId }) {
                    writeProfile(it, profile)
                }
            } else {
                CharacterHouseProfiles.insert {
                    it[characterId] = profile.characterId
                    writeProfile(it, profile)
                }
            }
        }
    }

    override suspend fun addCharm(characterId: Long, delta: Int): HouseProfile {
        require(delta >= 0)
        val current = getProfile(characterId)
        val next = current.copy(charm = (current.charm.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        saveProfile(next)
        return next
    }

    override suspend fun applyGift(record: GiftRecord, charmDelta: Int): HouseProfile {
        require(charmDelta >= 0)
        return suspendTransaction {
            val existing = CharacterHouseProfiles.selectAll()
                .where { CharacterHouseProfiles.characterId eq record.targetId }
                .firstOrNull()

            val currentCharm = existing?.get(CharacterHouseProfiles.charm) ?: 0
            val nextCharm = (currentCharm.toLong() + charmDelta)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()

            if (existing == null) {
                CharacterHouseProfiles.insert {
                    it[characterId] = record.targetId
                    it[charm] = nextCharm
                }
            } else {
                CharacterHouseProfiles.update({ CharacterHouseProfiles.characterId eq record.targetId }) {
                    it[charm] = nextCharm
                }
            }

            CharacterGiftRecords.insert {
                it[senderId] = record.senderId
                it[targetId] = record.targetId
                it[itemId] = record.itemId
                it[quantity] = record.quantity
                it[time] = record.time
            }

            val row = CharacterHouseProfiles.selectAll()
                .where { CharacterHouseProfiles.characterId eq record.targetId }
                .first()
            HouseProfile(
                characterId = record.targetId,
                sign = row[CharacterHouseProfiles.sign],
                birthMonth = row[CharacterHouseProfiles.birthMonth],
                birthDay = row[CharacterHouseProfiles.birthDay],
                charm = row[CharacterHouseProfiles.charm],
                signature = row[CharacterHouseProfiles.signature],
                impression = row[CharacterHouseProfiles.impression],
                favorite = row[CharacterHouseProfiles.favorite],
                area = row[CharacterHouseProfiles.area],
                hobby = row[CharacterHouseProfiles.hobby],
            )
        }
    }

    override suspend fun addGift(record: GiftRecord): GiftRecord {
        suspendTransaction {
            CharacterGiftRecords.insert {
                it[senderId] = record.senderId
                it[targetId] = record.targetId
                it[itemId] = record.itemId
                it[quantity] = record.quantity
                it[time] = record.time
            }
        }
        return record
    }

    override suspend fun recentGifts(targetId: Long, limit: Int): List<GiftRecord> = suspendTransaction {
        CharacterGiftRecords.selectAll()
            .where { CharacterGiftRecords.targetId eq targetId }
            .orderBy(CharacterGiftRecords.time to SortOrder.DESC)
            .limit(limit.coerceIn(1, 100))
            .map(::toGiftRecord)
    }

    override suspend fun allGifts(targetId: Long): List<GiftRecord> = suspendTransaction {
        CharacterGiftRecords.selectAll()
            .where { CharacterGiftRecords.targetId eq targetId }
            .orderBy(CharacterGiftRecords.time to SortOrder.DESC)
            .map(::toGiftRecord)
    }

    private fun toGiftRecord(row: org.jetbrains.exposed.v1.core.ResultRow): GiftRecord =
        GiftRecord(
            id = row[CharacterGiftRecords.id],
            senderId = row[CharacterGiftRecords.senderId],
            targetId = row[CharacterGiftRecords.targetId],
            itemId = row[CharacterGiftRecords.itemId],
            quantity = row[CharacterGiftRecords.quantity],
            time = row[CharacterGiftRecords.time],
        )

    private fun <T> writeProfile(
        statement: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<T>,
        profile: HouseProfile,
    ) {
        statement[CharacterHouseProfiles.sign] = profile.sign
        statement[CharacterHouseProfiles.birthMonth] = profile.birthMonth
        statement[CharacterHouseProfiles.birthDay] = profile.birthDay
        statement[CharacterHouseProfiles.charm] = profile.charm
        statement[CharacterHouseProfiles.signature] = profile.signature
        statement[CharacterHouseProfiles.impression] = profile.impression
        statement[CharacterHouseProfiles.favorite] = profile.favorite
        statement[CharacterHouseProfiles.area] = profile.area
        statement[CharacterHouseProfiles.hobby] = profile.hobby
    }
}

package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import tw.tsm.domain.interfaces.repositories.FlyPointRecord
import tw.tsm.domain.interfaces.repositories.FlyPointRepository
import tw.tsm.persistence.tables.CharacterFlyPoints

/**
 * FlyPointRepository 的 Exposed ORM 實作
 *
 * 使用 character_fly_points 表存儲記憶傳送符位置。
 */
class FlyPointRepositoryImpl : FlyPointRepository {

    override suspend fun getAll(charId: Long): Map<Int, FlyPointRecord> =
        suspendTransaction {
            CharacterFlyPoints.selectAll()
                .where { CharacterFlyPoints.charId eq charId }
                .associate {
                    it[CharacterFlyPoints.pointIndex] to FlyPointRecord(
                        index = it[CharacterFlyPoints.pointIndex],
                        sceneId = it[CharacterFlyPoints.sceneId],
                        x = it[CharacterFlyPoints.x],
                        y = it[CharacterFlyPoints.y]
                    )
                }
        }

    override suspend fun save(charId: Long, index: Int, sceneId: Int, x: Int, y: Int) =
        suspendTransaction {
            CharacterFlyPoints.upsert {
                it[CharacterFlyPoints.charId] = charId
                it[CharacterFlyPoints.pointIndex] = index
                it[CharacterFlyPoints.sceneId] = sceneId
                it[CharacterFlyPoints.x] = x
                it[CharacterFlyPoints.y] = y
            }
            Unit
        }
}

package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.CharacterCreateData
import tw.tsm.domain.interfaces.repositories.CharacterRecord
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.domain.interfaces.repositories.LeaderboardCharRecord
import tw.tsm.persistence.tables.CharacterMoney
import tw.tsm.persistence.tables.CharacterPets
import tw.tsm.persistence.tables.Characters

/**
 * CharacterRepository 的 Exposed ORM 實作
 */
class CharacterRepositoryImpl : CharacterRepository {

    override suspend fun findByAccountId(accountId: Long): List<CharacterRecord> =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.accountId eq accountId }
                .map { it.toRecord() }
        }

    override suspend fun findById(characterId: Long): CharacterRecord? =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.id eq characterId }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun isNameTaken(name: String): Boolean =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.name eq name }
                .count() > 0
        }

    override suspend fun create(accountId: Long, data: CharacterCreateData): Long =
        suspendTransaction {
            Characters.insert {
                it[Characters.accountId] = accountId
                it[name] = data.name
                it[sex] = data.sex
                it[hair] = data.hair
                it[color1] = data.color1
                it[color2] = data.color2
                it[element] = data.element
                it[str] = data.str
                it[dex] = data.dex
                it[vit] = data.vit
                it[int_] = data.int_
                it[men] = data.men
                it[spr] = data.spr
                it[level] = 1
                it[skillPoint] = 2  // 1 等初始 2 點技能點
                it[mapId] = tw.tsm.domain.constants.DEFAULT_START_MAP
                it[x] = tw.tsm.domain.constants.DEFAULT_START_X
                it[y] = tw.tsm.domain.constants.DEFAULT_START_Y
                it[createdAt] = System.currentTimeMillis()
            }[Characters.id]
        }

    /** 更新角色轉生與職業 */
    override suspend fun updateTurnCareer(characterId: Long, turn: Int, career: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.turn] = turn
                it[Characters.career] = career
            }
        }
    }

    /** 更新轉生後外觀 */
    override suspend fun updateTurnAppearance(characterId: Long, hair: Int, color1: Int, color2: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.hair] = hair
                it[Characters.color1] = color1
                it[Characters.color2] = color2
            }
        }
    }

    /** 更新角色等級 */
    override suspend fun updateLevel(characterId: Long, level: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.level] = level
            }
        }
    }

    /** 更新角色位置（斷線/登出時儲存） */
    override suspend fun updatePosition(characterId: Long, mapId: Int, x: Int, y: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.mapId] = mapId
                it[Characters.x] = x
                it[Characters.y] = y
            }
        }
    }

    /** 更新角色技能點數 */
    override suspend fun updateSkillPoint(characterId: Long, skillPoint: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.skillPoint] = skillPoint
            }
        }
    }

    /** 屬性配點 — 增加指定六圍欄位並設定新的 attrPoint */
    override suspend fun addStatPoint(characterId: Long, column: String, addValue: Int, newAttrPoint: Int) {
        suspendTransaction {
            val col = STAT_COLUMNS[column]
                ?: error("無效的配點欄位：$column")
            // 先讀取目前值，再設定新值（避免使用棄用的 SqlExpressionBuilder.plus）
            val current = Characters.selectAll()
                .where { Characters.id eq characterId }
                .single()[col]
            Characters.update({ Characters.id eq characterId }) {
                it[col] = current + addValue
                it[Characters.attrPoint] = newAttrPoint
            }
        }
    }

    /** 更新角色經驗和等級 */
    override suspend fun updateExpAndLevel(characterId: Long, exp: Long, level: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.exp] = exp
                it[Characters.level] = level
            }
        }
    }

    /** 更新記錄點（住宿後設定，飛回紀錄點/戰敗時傳送至此） */
    override suspend fun updateRecordPoint(characterId: Long, mapId: Int, x: Int, y: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.recordMapId] = mapId
                it[Characters.recordX] = x
                it[Characters.recordY] = y
            }
        }
    }

    /** 查詢記錄點 */
    override suspend fun getRecordPoint(characterId: Long): Triple<Int, Int, Int>? =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.id eq characterId }
                .singleOrNull()
                ?.let { Triple(it[Characters.recordMapId], it[Characters.recordX], it[Characters.recordY]) }
        }

    /** 更新角色完整運行時狀態（斷線/登出儲存） */
    override suspend fun updatePlayerState(
        characterId: Long, exp: Long, level: Int,
        attrPoint: Int, skillPoint: Int,
        intelligence: Int, atk: Int, def: Int, agi: Int, hpx: Int, spx: Int
    ) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.exp] = exp
                it[Characters.level] = level
                it[Characters.attrPoint] = attrPoint
                it[Characters.skillPoint] = skillPoint
                it[Characters.int_] = intelligence
                it[Characters.str] = atk
                it[Characters.vit] = def
                it[Characters.dex] = agi
                it[Characters.spr] = hpx
                it[Characters.men] = spx
            }
        }
    }

    /** 更新出戰武將 NPC ID */
    override suspend fun updateFightNpcId(characterId: Long, fightNpcId: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.fightNpcId] = fightNpcId
            }
        }
    }

    /**
     * 查詢排行榜所需的完整資料（角色 + 金錢 + 武將最高等級）
     *
     * 三步查詢在同一 transaction 中完成，記憶體內合併。
     */
    override suspend fun findAllForLeaderboard(): List<LeaderboardCharRecord> =
        suspendTransaction {
            // 1. 角色基礎資料
            val chars = Characters.selectAll().map { row ->
                LeaderboardCharRecord(
                    id = row[Characters.id],
                    name = row[Characters.name],
                    level = row[Characters.level],
                    exp = row[Characters.exp],
                    element = row[Characters.element],
                    turn = row[Characters.turn],
                    sex = row[Characters.sex],
                    hair = row[Characters.hair],
                    color1 = row[Characters.color1],
                    color2 = row[Characters.color2]
                )
            }

            // 2. 金錢資料（characterId → gold, auctionGold）
            val moneyMap = CharacterMoney.selectAll().associate { row ->
                row[CharacterMoney.characterId] to Pair(
                    row[CharacterMoney.gold],
                    row[CharacterMoney.auctionGold]
                )
            }

            // 3. 武將最高等級（characterId → MAX(level)）
            val petMaxLevelMap = CharacterPets.selectAll()
                .groupBy { it[CharacterPets.characterId] }
                .mapValues { (_, rows) -> rows.maxOf { it[CharacterPets.level] } }

            // 合併
            chars.map { char ->
                val money = moneyMap[char.id]
                val petMax = petMaxLevelMap[char.id] ?: 0
                char.copy(
                    gold = money?.first ?: 0,
                    auctionGold = money?.second ?: 0,
                    petMaxLevel = petMax
                )
            }
        }

    /** 更新當前穿戴稱號 ID */
    override suspend fun updateTitleId(characterId: Long, titleId: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.titleId] = titleId
            }
        }
    }

    /** 查詢元寶點數（實點, 虛點） */
    override suspend fun loadPoints(characterId: Long): Pair<Int, Int> =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.id eq characterId }
                .singleOrNull()
                ?.let { Pair(it[Characters.pointReal], it[Characters.pointVirtual]) }
                ?: Pair(0, 0)
        }

    /** 儲存元寶點數 */
    override suspend fun savePoints(characterId: Long, pointReal: Int, pointVirtual: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.pointReal] = pointReal
                it[Characters.pointVirtual] = pointVirtual
            }
        }
    }

    /**
     * ResultRow → CharacterRecord 映射
     */
    private fun ResultRow.toRecord() = CharacterRecord(
        id = this[Characters.id],
        accountId = this[Characters.accountId],
        name = this[Characters.name],
        sex = this[Characters.sex],
        hair = this[Characters.hair],
        color1 = this[Characters.color1],
        color2 = this[Characters.color2],
        element = this[Characters.element],
        level = this[Characters.level],
        mapId = this[Characters.mapId],
        x = this[Characters.x],
        y = this[Characters.y],
        str = this[Characters.str],
        dex = this[Characters.dex],
        vit = this[Characters.vit],
        int_ = this[Characters.int_],
        men = this[Characters.men],
        spr = this[Characters.spr],
        skillPoint = this[Characters.skillPoint],
        attrPoint = this[Characters.attrPoint],
        exp = this[Characters.exp],
        turn = this[Characters.turn],
        career = this[Characters.career],
        fightNpcId = this[Characters.fightNpcId],
        titleId = this[Characters.titleId]
    )

    companion object {
        /** 允許配點的 DB 欄位白名單 */
        private val STAT_COLUMNS = mapOf(
            "str" to Characters.str,
            "dex" to Characters.dex,
            "vit" to Characters.vit,
            "int_" to Characters.int_,
            "men" to Characters.men,
            "spr" to Characters.spr
        )
    }
}
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
 * Triển khai Exposed ORM cho CharacterRepository
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
                it[skillPoint] = 2  // Cấp 1 khởi đầu có 2 điểm kỹ năng
                it[mapId] = tw.tsm.domain.constants.DEFAULT_START_MAP
                it[x] = tw.tsm.domain.constants.DEFAULT_START_X
                it[y] = tw.tsm.domain.constants.DEFAULT_START_Y
                it[createdAt] = System.currentTimeMillis()
            }[Characters.id]
        }

    /** Cập nhật chuyển sinh và nghề của nhân vật*/
    override suspend fun updateTurnCareer(characterId: Long, turn: Int, career: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.turn] = turn
                it[Characters.career] = career
            }
        }
    }

    /** Cập nhật tạo hình sau chuyển sinh*/
    override suspend fun updateTurnAppearance(characterId: Long, hair: Int, color1: Int, color2: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.hair] = hair
                it[Characters.color1] = color1
                it[Characters.color2] = color2
            }
        }
    }

    /** Cập nhật cấp độ nhân vật*/
    override suspend fun updateLevel(characterId: Long, level: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.level] = level
            }
        }
    }

    /** Cập nhật vị trí nhân vật (lưu khi mất kết nối/đăng xuất)*/
    override suspend fun updatePosition(characterId: Long, mapId: Int, x: Int, y: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.mapId] = mapId
                it[Characters.x] = x
                it[Characters.y] = y
            }
        }
    }

    /** Cập nhật điểm kỹ năng của nhân vật*/
    override suspend fun updateSkillPoint(characterId: Long, skillPoint: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.skillPoint] = skillPoint
            }
        }
    }

    /** Phân điểm thuộc tính — tăng các trường sáu chỉ số được chỉ định và đặt attrPoint mới*/
    override suspend fun addStatPoint(characterId: Long, column: String, addValue: Int, newAttrPoint: Int) {
        suspendTransaction {
            val col = STAT_COLUMNS[column]
                ?: error("Trường phân điểm không hợp lệ: $column")
            // Đọc giá trị hiện tại trước rồi set giá trị mới (tránh dùng SqlExpressionBuilder.plus đã deprecated)
            val current = Characters.selectAll()
                .where { Characters.id eq characterId }
                .single()[col]
            Characters.update({ Characters.id eq characterId }) {
                it[col] = current + addValue
                it[Characters.attrPoint] = newAttrPoint
            }
        }
    }

    /** Cập nhật EXP và cấp độ nhân vật*/
    override suspend fun updateExpAndLevel(characterId: Long, exp: Long, level: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.exp] = exp
                it[Characters.level] = level
            }
        }
    }

    /** Cập nhật điểm ghi (đặt sau khi nghỉ ở lữ quán, bay về điểm ghi/thua trận sẽ dịch chuyển tới đây)*/
    override suspend fun updateRecordPoint(characterId: Long, mapId: Int, x: Int, y: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.recordMapId] = mapId
                it[Characters.recordX] = x
                it[Characters.recordY] = y
            }
        }
    }

    /** Truy vấn điểm ghi*/
    override suspend fun getRecordPoint(characterId: Long): Triple<Int, Int, Int>? =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.id eq characterId }
                .singleOrNull()
                ?.let { Triple(it[Characters.recordMapId], it[Characters.recordX], it[Characters.recordY]) }
        }

    /** Cập nhật toàn bộ trạng thái runtime của nhân vật (lưu khi mất kết nối/đăng xuất)*/
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

    /** Cập nhật NPC ID của tướng xuất chiến*/
    override suspend fun updateFightNpcId(characterId: Long, fightNpcId: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.fightNpcId] = fightNpcId
            }
        }
    }

    /**
     * Tra dữ liệu đầy đủ cần cho bảng xếp hạng (nhân vật + tiền vàng + cấp cao nhất của tướng)
     *
     * Ba truy vấn hoàn tất trong cùng một transaction, ghép lại trong bộ nhớ.
     */
    override suspend fun findAllForLeaderboard(): List<LeaderboardCharRecord> =
        suspendTransaction {
            // 1. Dữ liệu cơ bản của nhân vật
            val chars = Characters.selectAll().map { row ->
                LeaderboardCharRecord(
                    id = row[Characters.id],
                    name = row[Characters.name],
                    level = row[Characters.level],
                    exp = row[Characters.exp],
                    element = row[Characters.element],
                    turn = row[Characters.turn],
                    career = row[Characters.career],
                    sex = row[Characters.sex],
                    hair = row[Characters.hair],
                    color1 = row[Characters.color1],
                    color2 = row[Characters.color2]
                )
            }

            // 2. Dữ liệu tiền (characterId → gold, auctionGold)
            val moneyMap = CharacterMoney.selectAll().associate { row ->
                row[CharacterMoney.characterId] to Pair(
                    row[CharacterMoney.gold],
                    row[CharacterMoney.auctionGold]
                )
            }

            // 3. Võ tướng cao nhất cho leaderboard ID6.
            // DVT score format cần element + turn + level, nên không được chỉ MAX(level).
            val bestPetMap = CharacterPets.selectAll()
                .groupBy { it[CharacterPets.characterId] }
                .mapValues { (_, rows) ->
                    rows.maxWithOrNull(
                        compareBy<ResultRow> { it[CharacterPets.turnKind] }
                            .thenBy { it[CharacterPets.level] }
                            .thenBy { it[CharacterPets.exp] }
                            .thenBy { it[CharacterPets.id] }
                    )
                }

            // Gộp
            chars.map { char ->
                val money = moneyMap[char.id]
                val pet = bestPetMap[char.id]
                char.copy(
                    gold = money?.first ?: 0,
                    auctionGold = money?.second ?: 0,
                    petNpcId = pet?.get(CharacterPets.npcId) ?: 0,
                    petLevel = pet?.get(CharacterPets.level) ?: 0,
                    petTurn = pet?.get(CharacterPets.turnKind) ?: 0,
                    petExp = pet?.get(CharacterPets.exp) ?: 0L,
                )
            }
        }

    /** Cập nhật ID danh hiệu đang đeo*/
    override suspend fun updateTitleId(characterId: Long, titleId: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.titleId] = titleId
            }
        }
    }

    /** Lưu điểm diệt địch DVT. */
    override suspend fun updateKillNum(characterId: Long, value: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.killNum] = value
            }
        }
    }

    /** Lưu giá trị tuyệt đối độ thân thiện một phe DVT. */
    override suspend fun updateMoral(characterId: Long, faction: Int, value: Int) {
        val column = when (faction) {
            1 -> Characters.moralWei
            2 -> Characters.moralShu
            3 -> Characters.moralWu
            4 -> Characters.moralYellow
            5 -> Characters.moralRanger
            else -> error("Phe Moral không hợp lệ: $faction")
        }
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[column] = value
            }
        }
    }

    /** Truy vấn điểm nguyên bảo (điểm thực, điểm ảo)*/
    override suspend fun loadPoints(characterId: Long): Pair<Int, Int> =
        suspendTransaction {
            Characters.selectAll()
                .where { Characters.id eq characterId }
                .singleOrNull()
                ?.let { Pair(it[Characters.pointReal], it[Characters.pointVirtual]) }
                ?: Pair(0, 0)
        }

    /** Lưu điểm nguyên bảo*/
    override suspend fun savePoints(characterId: Long, pointReal: Int, pointVirtual: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.pointReal] = pointReal
                it[Characters.pointVirtual] = pointVirtual
            }
        }
    }

    /**
     * Ánh xạ ResultRow → CharacterRecord
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
        titleId = this[Characters.titleId],
        killNum = this[Characters.killNum],
        moral = intArrayOf(
            this[Characters.moralWei],
            this[Characters.moralShu],
            this[Characters.moralWu],
            this[Characters.moralYellow],
            this[Characters.moralRanger]
        )
    )

    companion object {
        /** Danh sách trắng các trường DB được phép phân điểm*/
        private val STAT_COLUMNS = mapOf(
            "str" to Characters.str,
            "dex" to Characters.dex,
            "vit" to Characters.vit,
            "int_" to Characters.int_,
            "men" to Characters.men,
            "spr" to Characters.spr
        )
    }

    override suspend fun updateAttrPoint(characterId: Long, newAttrPoint: Int) {
        suspendTransaction {
            Characters.update({ Characters.id eq characterId }) {
                it[Characters.attrPoint] = newAttrPoint
            }
        }
    }

}
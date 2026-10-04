package tw.tsm.persistence.repositories

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import tw.tsm.domain.interfaces.repositories.SoulRepository
import tw.tsm.domain.valueobjects.SoulSkillData
import tw.tsm.domain.valueobjects.SoulThingData
import tw.tsm.persistence.tables.CharacterSouls

class SoulRepositoryImpl : SoulRepository {
    override suspend fun findByCharacterId(characterId: Long): List<SoulThingData> =
        suspendTransaction {
            CharacterSouls.selectAll()
                .where { CharacterSouls.characterId eq characterId }
                .orderBy(CharacterSouls.id)
                .map { row ->
                    SoulThingData(
                        sn = row[CharacterSouls.sn],
                        itemId = row[CharacterSouls.itemId],
                        element = row[CharacterSouls.element],
                        equipId = row[CharacterSouls.equipId],
                        level = row[CharacterSouls.level],
                        starLevel = row[CharacterSouls.starLevel],
                        startAtt = listOf(
                            row[CharacterSouls.startAtk],
                            row[CharacterSouls.startDef],
                            row[CharacterSouls.startInt],
                            row[CharacterSouls.startAgi],
                            row[CharacterSouls.startHpx],
                            row[CharacterSouls.startSpx],
                        ),
                        nowAtt = listOf(
                            row[CharacterSouls.nowAtk],
                            row[CharacterSouls.nowDef],
                            row[CharacterSouls.nowInt],
                            row[CharacterSouls.nowAgi],
                            row[CharacterSouls.nowHpx],
                            row[CharacterSouls.nowSpx],
                        ),
                        skills = listOf(
                            SoulSkillData(row[CharacterSouls.skill1Id], row[CharacterSouls.skill1Lv]),
                            SoulSkillData(row[CharacterSouls.skill2Id], row[CharacterSouls.skill2Lv]),
                            SoulSkillData(row[CharacterSouls.skill3Id], row[CharacterSouls.skill3Lv]),
                            SoulSkillData(row[CharacterSouls.skill4Id], row[CharacterSouls.skill4Lv]),
                        ),
                        lockFlags = row[CharacterSouls.lockFlags],
                    )
                }
        }

    override suspend fun save(characterId: Long, soul: SoulThingData) {
        suspendTransaction {
            val exists = CharacterSouls.selectAll()
                .where {
                    (CharacterSouls.characterId eq characterId) and
                        (CharacterSouls.sn eq soul.sn)
                }
                .firstOrNull() != null

            if (exists) {
                CharacterSouls.update({
                    (CharacterSouls.characterId eq characterId) and
                        (CharacterSouls.sn eq soul.sn)
                }) {
                    writeSoul(it, soul)
                }
            } else {
                CharacterSouls.insert {
                    it[CharacterSouls.characterId] = characterId
                    it[sn] = soul.sn
                    writeSoul(it, soul)
                }
            }
        }
    }

    override suspend fun delete(characterId: Long, sn: Long) {
        suspendTransaction {
            CharacterSouls.deleteWhere {
                (CharacterSouls.characterId eq characterId) and
                    (CharacterSouls.sn eq sn)
            }
        }
    }

    private fun <T> writeSoul(
        statement: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<T>,
        soul: SoulThingData,
    ) {
        statement[CharacterSouls.itemId] = soul.itemId
        statement[CharacterSouls.element] = soul.element
        statement[CharacterSouls.equipId] = soul.equipId
        statement[CharacterSouls.level] = soul.level
        statement[CharacterSouls.starLevel] = soul.starLevel

        statement[CharacterSouls.startAtk] = soul.startAtt[0]
        statement[CharacterSouls.startDef] = soul.startAtt[1]
        statement[CharacterSouls.startInt] = soul.startAtt[2]
        statement[CharacterSouls.startAgi] = soul.startAtt[3]
        statement[CharacterSouls.startHpx] = soul.startAtt[4]
        statement[CharacterSouls.startSpx] = soul.startAtt[5]

        statement[CharacterSouls.nowAtk] = soul.nowAtt[0]
        statement[CharacterSouls.nowDef] = soul.nowAtt[1]
        statement[CharacterSouls.nowInt] = soul.nowAtt[2]
        statement[CharacterSouls.nowAgi] = soul.nowAtt[3]
        statement[CharacterSouls.nowHpx] = soul.nowAtt[4]
        statement[CharacterSouls.nowSpx] = soul.nowAtt[5]

        statement[CharacterSouls.skill1Id] = soul.skills[0].id
        statement[CharacterSouls.skill1Lv] = soul.skills[0].level
        statement[CharacterSouls.skill2Id] = soul.skills[1].id
        statement[CharacterSouls.skill2Lv] = soul.skills[1].level
        statement[CharacterSouls.skill3Id] = soul.skills[2].id
        statement[CharacterSouls.skill3Lv] = soul.skills[2].level
        statement[CharacterSouls.skill4Id] = soul.skills[3].id
        statement[CharacterSouls.skill4Lv] = soul.skills[3].level
        statement[CharacterSouls.lockFlags] = soul.lockFlags
    }
}

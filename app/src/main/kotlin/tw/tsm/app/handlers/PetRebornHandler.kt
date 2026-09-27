package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetRebornSystem
import tw.tsm.gamelogic.systems.PetSystem
import tw.tsm.gamelogic.systems.RebornResult
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 武將轉生 Handler（mainKind=44）
 *
 * 來源：protocal.lua protocolTable[44]、UI/UINpcTurn.lua
 *
 * C:044-001 武將轉生（一轉） — +武將索引(1)
 * C:044-002 學習特有技 — +武將索引(1)
 * C:044-003 武將二轉 — +武將索引(1)
 *
 * 成功時依序下發：S:023-009 信物扣除 → S:044-001 種類0 → S:015-008 武將完整資料。
 * 失敗時下發 S:044-001 對應錯誤種類。
 */
class PetRebornHandler(
    private val responseSender: ResponseSender,
    private val petRebornSystem: PetRebornSystem,
    private val petSystem: PetSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.NPC_REINCARNATION

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (data.isEmpty()) return

        val followIndex = PacketReader(data).readUByte()

        when (subKind) {
            1 -> handleReborn(session, followIndex, PetRebornSystem.REBORN_FIRST)
            2 -> handleLearnSpecialSkill(session, followIndex)
            3 -> handleReborn(session, followIndex, PetRebornSystem.REBORN_SECOND)
            else -> logger.warn { "未處理的武將轉生子協定：subKind=$subKind" }
        }
    }

    /**
     * C:044-001 一轉 / C:044-003 二轉
     */
    private suspend fun handleReborn(session: GameSession, followIndex: Int, rebornKind: Int) {
        val charId = session.roleId.toLong()

        when (val result = petRebornSystem.reborn(charId, followIndex, rebornKind)) {
            is RebornResult.Ok -> {
                responseSender.sendReduceBagItem(session, result.consumedSlot, 1)
                responseSender.sendPetRebornMessage(session, PetRebornSystem.MSG_SUCCESS)
                responseSender.sendFollowNpcFullData(session, listOf(result.pet))
                logger.info {
                    "${session.characterName} 武將${if (rebornKind == PetRebornSystem.REBORN_FIRST) "一" else "二"}轉成功：" +
                        "slot=$followIndex → npcId=${result.pet.npcId}"
                }
            }
            is RebornResult.Fail -> {
                responseSender.sendPetRebornMessage(session, result.msgKind)
                logger.debug { "${session.characterName} 武將轉生失敗：slot=$followIndex msgKind=${result.msgKind}" }
            }
        }
    }

    /**
     * C:044-002 學習特有技
     *
     * 學習資格由 S:036-012 開介面時的可學習清單控制（Eve 事件觸發），
     * 伺服器端此處重新驗證武將存在且尚未學會。
     */
    private fun handleLearnSpecialSkill(session: GameSession, followIndex: Int) {
        val charId = session.roleId.toLong()

        val updated = petSystem.learnSpecialSkill(charId, followIndex) ?: run {
            logger.warn { "${session.characterName} 學習特有技失敗：slot=$followIndex 無武將或已學會" }
            return
        }

        responseSender.sendFollowNpcFullData(session, listOf(updated))
        logger.info { "${session.characterName} 學習特有技：slot=$followIndex npcId=${updated.npcId}" }
    }
}

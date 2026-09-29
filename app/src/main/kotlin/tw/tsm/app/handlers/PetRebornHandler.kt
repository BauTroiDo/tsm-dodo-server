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
 * Handler chuyển sinh tướng (mainKind=44)
 *
 * Nguồn: protocal.lua protocolTable[44], UI/UINpcTurn.lua
 *
 * C:044-001 Chuyển sinh tướng (nhất chuyển) — +chỉ số tướng(1)
 * C:044-002 Học kỹ năng riêng — +chỉ số tướng(1)
 * C:044-003 Nhị chuyển tướng — +chỉ số tướng(1)
 *
 * Khi thành công lần lượt gửi xuống: S:023-009 khấu trừ tín vật → S:044-001 loại 0 → S:015-008 dữ liệu đầy đủ của tướng.
 * Khi thất bại gửi xuống S:044-001 với loại lỗi tương ứng.
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
            else -> logger.warn { "Sub-protocol chuyển sinh tướng chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:044-001 nhất chuyển / C:044-003 nhị chuyển
     */
    private suspend fun handleReborn(session: GameSession, followIndex: Int, rebornKind: Int) {
        val charId = session.roleId.toLong()

        when (val result = petRebornSystem.reborn(charId, followIndex, rebornKind)) {
            is RebornResult.Ok -> {
                responseSender.sendReduceBagItem(session, result.consumedSlot, 1)
                responseSender.sendPetRebornMessage(session, PetRebornSystem.MSG_SUCCESS)
                responseSender.sendFollowNpcFullData(session, listOf(result.pet))
                logger.info {
                    "${session.characterName} tướng ${if (rebornKind == PetRebornSystem.REBORN_FIRST) "nhất chuyển" else "nhị chuyển"} thành công: " +
                        "slot=$followIndex → npcId=${result.pet.npcId}"
                }
            }
            is RebornResult.Fail -> {
                responseSender.sendPetRebornMessage(session, result.msgKind)
                logger.debug { "${session.characterName} chuyển sinh tướng thất bại: slot=$followIndex msgKind=${result.msgKind}" }
            }
        }
    }

    /**
     * C:044-002 Học kỹ năng riêng
     *
     * Điều kiện học do danh sách kỹ năng học được khi mở giao diện ở S:036-012 kiểm soát (do sự kiện Eve kích),
     * Server kiểm tra lại ở đây rằng tướng tồn tại và vẫn chưa học kỹ năng đó.
     */
    private fun handleLearnSpecialSkill(session: GameSession, followIndex: Int) {
        val charId = session.roleId.toLong()

        val updated = petSystem.learnSpecialSkill(charId, followIndex) ?: run {
            logger.warn { "${session.characterName} học kỹ năng đặc hữu thất bại: slot=$followIndex không có tướng hoặc đã học" }
            return
        }

        responseSender.sendFollowNpcFullData(session, listOf(updated))
        logger.info { "${session.characterName} học kỹ năng đặc hữu: slot=$followIndex npcId=${updated.npcId}" }
    }
}

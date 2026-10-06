package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.data.TitleDef
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.domain.interfaces.repositories.CharacterRepository
import tw.tsm.gamelogic.systems.BitFlagSystem
import tw.tsm.gamelogic.systems.DvtTitlePolicy
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý trạng thái người chơi (mainKind = 5)
 *
 * Hiện hỗ trợ:
 * - C:005-008 xem xong hướng dẫn người mới
 * - C:005-012 đổi danh hiệu
 */
class PlayerStatusHandler(
    private val bitFlagSystem: BitFlagSystem,
    private val responseSender: ResponseSender,
    private val characterRepository: CharacterRepository,
    private val worldManager: WorldManager,
    private val titleDefs: Map<Int, TitleDef>,
) : PacketHandler {
    override val mainKind: Int = Opcodes.PLAYER_STATUS

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        when (subKind) {
            8 -> handleTeachComplete(session, data)
            12 -> handleChangeTitle(session, data)
            else -> logger.trace { "Chưa xử lý C:005-$subKind" }
        }
    }

    /** C:005-008 Xem xong hướng dẫn người mới: +guideId(1B). */
    private suspend fun handleTeachComplete(session: GameSession, data: ByteArray) {
        val reader = PacketReader(data)
        val guideId = reader.readUByte()

        val charId = session.roleId.toLong()
        if (charId <= 0) return

        val bitFlagId = bitFlagSystem.completeTeach(charId, guideId)
        if (bitFlagId != null) {
            responseSender.sendBitFlagChanges(session, listOf(bitFlagId to true))
        }
    }

    /**
     * C:005-012 Đổi danh hiệu: +titleId(UInt16 LE).
     *
     * DVT UITitle only enables a title when TitleData.flagId == 0 or the
     * player's BitFlag is set. Mirror that rule server-side so a forged client
     * cannot equip a locked/non-existent title. titleId=0 always means remove.
     */
    private suspend fun handleChangeTitle(session: GameSession, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        if (data.size != 2) return
        if (session.battleId != 0L) return

        val titleId = PacketReader(data).readUShortLE()
        val charId = session.roleId.toLong()
        if (charId <= 0) return

        val ownedFlags = if (titleId == 0) emptySet() else bitFlagSystem.getSetFlagIds(charId)
        if (!DvtTitlePolicy.canEquip(titleId, titleDefs, ownedFlags)) {
            val flag = titleDefs[titleId]?.flagId
            logger.warn {
                "${session.characterName} thử mang danh hiệu không hợp lệ/chưa mở: title=$titleId flag=$flag"
            }
            return
        }

        if (session.titleId == titleId) return

        session.titleId = titleId
        characterRepository.updateTitleId(charId, titleId)

        val players = worldManager.getPlayersInMap(session.mapId, session.instanceId)
        for (player in players) {
            responseSender.sendTitleChange(player, session.playerId, titleId)
        }

        logger.info { "${session.characterName} đổi danh hiệu: $titleId" }
    }
}

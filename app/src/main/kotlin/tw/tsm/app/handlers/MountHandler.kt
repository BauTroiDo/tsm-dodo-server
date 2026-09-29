package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.MountConstants
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.MountSystem
import tw.tsm.gamelogic.systems.WorldManager
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * Bộ xử lý vật cưỡi (mainKind = 79)
 *
 * Nguồn: protocal.lua protocolTable[79]
 * Xử lý lên/xuống ngựa, nâng cấp, cộng điểm, mặc/tháo trang bị, đổi vật cưỡi.
 */
class MountHandler(
    private val mountSystem: MountSystem,
    private val responseSender: ResponseSender,
    private val worldManager: WorldManager
) : PacketHandler {
    override val mainKind: Int = Opcodes.MOUNT

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        when (subKind) {
            1 -> handleMount(session, characterId)
            2 -> handleDismount(session, characterId)
            3 -> handleLevelUp(session, characterId, data)
            4 -> handleAttributeUp(session, characterId, data)
            5 -> handleEquip(session, characterId, data)
            6 -> handleUnequip(session, characterId, data)
            7 -> handleSwitch(session, characterId, data)
            else -> logger.warn { "Vật cưỡi chưa xử lý subKind=$subKind" }
        }
    }

    /**
     * C:079-001 lên vật cưỡi
     *
     * sau khi thành công phát rộng S:079-007 cho mọi người chơi cùng bản đồ
     */
    private fun handleMount(session: GameSession, characterId: Long) {
        if (session.battleId != 0L) return

        val npcId = mountSystem.mount(characterId)
        if (npcId == 0) return

        session.mountNpcId = npcId
        val mountData = mountSystem.getCache(characterId)
        if (mountData != null) {
            session.mountLevel = mountData.level
        }

        // phát rộng S:079-007 cho mọi người chơi cùng bản đồ (gồm cả bản thân)
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendPlayerMount(player, session.playerId, npcId)
        }

        // gửi S:008-001 cập nhật thuộc tính MountsLv (kích hoạt client UpdateSpeed)
        responseSender.sendSetAttribute(
            session, MountConstants.ATTR_MOUNTS_LV,
            session.mountLevel, true, 0
        )

        logger.info { "${session.characterName} lên ngựa: npcId=$npcId" }
    }

    /**
     * C:079-002 xuống vật cưỡi
     *
     * sau khi thành công phát rộng S:079-007 (npcId=0) cho mọi người chơi cùng bản đồ
     */
    private fun handleDismount(session: GameSession, characterId: Long) {
        if (!mountSystem.dismount(characterId)) return

        session.mountNpcId = 0

        // phát rộng S:079-007 (npcId=0 biểu thị xuống ngựa)
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendPlayerMount(player, session.playerId, 0)
        }

        logger.info { "${session.characterName} xuống ngựa" }
    }

    /**
     * C:079-003 nâng cấp vật cưỡi
     *
     * Định dạng: +chỉ số túi đồ(2LE)
     */
    private suspend fun handleLevelUp(session: GameSession, characterId: Long, data: ByteArray) {
        // DVT C:079-003 = bagIndex(UInt16LE)
        if (data.size != 2) {
            logger.warn { "C79-3 sai size=" + data.size + ", expected=2" }
            return
        }
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()

        val result = mountSystem.levelUp(characterId, bagIndex) ?: return

        session.mountLevel = result.newLevel

        // S:023-009 khấu trừ vật phẩm
        if (result.itemConsumed > 0) {
            responseSender.sendReduceBagItem(session, bagIndex, result.itemConsumed)
        }
        // S:026-002 khấu trừ tiền
        if (result.moneySpent > 0) {
            responseSender.sendSubtractMoney(session, result.moneySpent)
        }
        // S:079-002 cấp độ mới
        responseSender.sendMountLevel(session, result.newLevel)
        // S:008-001 cập nhật MountsLv (kích hoạt tính lại tốc độ)
        responseSender.sendSetAttribute(
            session, MountConstants.ATTR_MOUNTS_LV,
            result.newLevel, true, 0
        )

        logger.info { "${session.characterName} vật cưỡi lên cấp → level=${result.newLevel}" }
    }

    /**
     * C:079-004 cộng điểm vật cưỡi
     *
     * Định dạng: +loại(1) +chỉ số túi đồ(2LE)
     */
    private suspend fun handleAttributeUp(session: GameSession, characterId: Long, data: ByteArray) {
        // DVT C:079-004 = kind(Byte) + bagIndex(UInt16LE)
        if (data.size != 3) {
            logger.warn { "C79-4 sai size=" + data.size + ", expected=3" }
            return
        }
        val reader = PacketReader(data)
        val kind = reader.readUByte()
        val bagIndex = reader.readUShortLE()

        val result = mountSystem.addAttributePoint(characterId, kind, bagIndex) ?: return

        // S:023-009 khấu trừ vật phẩm
        if (result.itemConsumed > 0) {
            responseSender.sendReduceBagItem(session, bagIndex, result.itemConsumed)
        }
        // S:079-003 đặt điểm
        responseSender.sendMountAttributePoint(session, kind, result.newPoints)
    }

    /**
     * C:079-005 mặc trang bị cho vật cưỡi
     *
     * Định dạng: +chỉ số túi đồ(2LE)
     */
    private suspend fun handleEquip(session: GameSession, characterId: Long, data: ByteArray) {
        // DVT C:079-005 = bagIndex(UInt16LE)
        if (data.size != 2) {
            logger.warn { "C79-5 sai size=" + data.size + ", expected=2" }
            return
        }
        val reader = PacketReader(data)
        val bagIndex = reader.readUShortLE()

        val fitType = mountSystem.equipItem(characterId, bagIndex)
        if (fitType < 0) return

        // S:079-004 phản hồi mặc trang bị
        responseSender.sendMountEquip(session, bagIndex)
    }

    /**
     * C:079-006 tháo trang bị vật cưỡi
     *
     * Định dạng: +vị trí trang bị(1) +chỉ số túi đồ(2LE)
     */
    private suspend fun handleUnequip(session: GameSession, characterId: Long, data: ByteArray) {
        // DVT C:079-006 = fitType(Byte) + bagIndex(UInt16LE)
        if (data.size != 3) {
            logger.warn { "C79-6 sai size=" + data.size + ", expected=3" }
            return
        }
        val reader = PacketReader(data)
        val fitType = reader.readUByte()
        val bagIndex = reader.readUShortLE()

        val result = mountSystem.unequipItem(characterId, fitType, bagIndex) ?: return

        // S:079-005 phản hồi tháo trang bị
        responseSender.sendMountUnequip(session, result.first, result.second)
    }

    /**
     * C:079-007 đổi vật cưỡi
     *
     * Định dạng: +NPCID(2LE)
     */
    private suspend fun handleSwitch(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val npcId = reader.readUShortLE()

        if (!mountSystem.switchMount(characterId, npcId)) return

        // S:079-006 phản hồi đổi vật cưỡi
        responseSender.sendMountSwitch(session, npcId)
    }
}

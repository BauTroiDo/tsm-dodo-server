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
 * 坐騎處理器（mainKind = 79）
 *
 * 來源：protocal.lua protocolTable[79]
 * 處理上下馬、升級、投點、穿脫裝備、切換坐騎。
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
            else -> logger.warn { "未處理的坐騎 subKind=$subKind" }
        }
    }

    /**
     * C:079-001 上座騎
     *
     * 成功後廣播 S:079-007 給同地圖所有玩家
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

        // 廣播 S:079-007 給同地圖所有玩家（含自己）
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendPlayerMount(player, session.playerId, npcId)
        }

        // 發送 S:008-001 更新 MountsLv 屬性（觸發客戶端 UpdateSpeed）
        responseSender.sendSetAttribute(
            session, MountConstants.ATTR_MOUNTS_LV,
            session.mountLevel, true, 0
        )

        logger.info { "${session.characterName} 上馬：npcId=$npcId" }
    }

    /**
     * C:079-002 下座騎
     *
     * 成功後廣播 S:079-007（npcId=0）給同地圖所有玩家
     */
    private fun handleDismount(session: GameSession, characterId: Long) {
        if (!mountSystem.dismount(characterId)) return

        session.mountNpcId = 0

        // 廣播 S:079-007（npcId=0 代表下馬）
        val players = worldManager.getPlayersInMap(session.mapId)
        for (player in players) {
            responseSender.sendPlayerMount(player, session.playerId, 0)
        }

        logger.info { "${session.characterName} 下馬" }
    }

    /**
     * C:079-003 座騎升級
     *
     * 格式：+背包索引(1 or 2)
     */
    private suspend fun handleLevelUp(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val bagIndex = if (reader.remaining >= 2) reader.readUShortLE() else reader.readUByte()

        val result = mountSystem.levelUp(characterId, bagIndex) ?: return

        session.mountLevel = result.newLevel

        // S:023-009 扣物品
        if (result.itemConsumed > 0) {
            responseSender.sendReduceBagItem(session, bagIndex, result.itemConsumed)
        }
        // S:026-002 扣金錢
        if (result.moneySpent > 0) {
            responseSender.sendSubtractMoney(session, result.moneySpent)
        }
        // S:079-002 新等級
        responseSender.sendMountLevel(session, result.newLevel)
        // S:008-001 更新 MountsLv（觸發速度重算）
        responseSender.sendSetAttribute(
            session, MountConstants.ATTR_MOUNTS_LV,
            result.newLevel, true, 0
        )

        logger.info { "${session.characterName} 坐騎升級 → level=${result.newLevel}" }
    }

    /**
     * C:079-004 座騎投點
     *
     * 格式：+種類(1) +背包索引(1 or 2)
     */
    private suspend fun handleAttributeUp(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val kind = reader.readUByte()
        val bagIndex = if (reader.remaining >= 2) reader.readUShortLE() else reader.readUByte()

        val result = mountSystem.addAttributePoint(characterId, kind, bagIndex) ?: return

        // S:023-009 扣物品
        if (result.itemConsumed > 0) {
            responseSender.sendReduceBagItem(session, bagIndex, result.itemConsumed)
        }
        // S:079-003 設定點數
        responseSender.sendMountAttributePoint(session, kind, result.newPoints)
    }

    /**
     * C:079-005 座騎穿裝
     *
     * 格式：+背包索引(1 or 2)
     */
    private suspend fun handleEquip(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 1) return
        val reader = PacketReader(data)
        val bagIndex = if (reader.remaining >= 2) reader.readUShortLE() else reader.readUByte()

        val fitType = mountSystem.equipItem(characterId, bagIndex)
        if (fitType < 0) return

        // S:079-004 穿裝回應
        responseSender.sendMountEquip(session, bagIndex)
    }

    /**
     * C:079-006 座騎脫裝
     *
     * 格式：+裝備部位(1) +背包索引(1 or 2)
     */
    private suspend fun handleUnequip(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val fitType = reader.readUByte()
        val bagIndex = if (reader.remaining >= 2) reader.readUShortLE() else reader.readUByte()

        val result = mountSystem.unequipItem(characterId, fitType, bagIndex) ?: return

        // S:079-005 脫裝回應
        responseSender.sendMountUnequip(session, result.first, result.second)
    }

    /**
     * C:079-007 座騎切換
     *
     * 格式：+NPCID(2LE)
     */
    private suspend fun handleSwitch(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size < 2) return
        val reader = PacketReader(data)
        val npcId = reader.readUShortLE()

        if (!mountSystem.switchMount(characterId, npcId)) return

        // S:079-006 切換回應
        responseSender.sendMountSwitch(session, npcId)
    }
}

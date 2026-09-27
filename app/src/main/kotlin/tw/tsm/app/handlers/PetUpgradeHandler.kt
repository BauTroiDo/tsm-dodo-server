package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.PetUpgradeSystem
import tw.tsm.gamelogic.systems.RoleCountSystem
import tw.tsm.gamelogic.systems.UpgradeStartResult
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/**
 * 武將強化 Handler（mainKind=74）
 *
 * 來源：protocal.lua protocolTable[74]、UIStatus.lua 強化流程
 *
 * C:074-001 武將強化 — +武將索引(1) +NPCID(2)
 * C:074-002 武將確定強化 — 空 payload
 * C:074-003 武將取消強化 — 空 payload
 *
 * 三段流程：
 * 1. 發起 → 驗證資源 → 預擲六圍 → S:008-011 屬性預覽
 * 2. 確認 → 扣資源（遊戲幣/碎片/畫冊）→ S:074-002 + S:015-020 + 武將完整資料刷新
 * 3. 取消 → 清除暫存 → S:074-003
 */
class PetUpgradeHandler(
    private val responseSender: ResponseSender,
    private val petUpgradeSystem: PetUpgradeSystem
) : PacketHandler {

    override val mainKind: Int = Opcodes.NPC_UPGRADE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return

        when (subKind) {
            1 -> handleStart(session, data)
            2 -> handleConfirm(session)
            3 -> handleCancel(session)
            else -> logger.warn { "未處理的武將強化子協定：subKind=$subKind" }
        }
    }

    /**
     * C:074-001 發起強化
     *
     * 格式：+武將索引(1) +NPCID(2)
     */
    private fun handleStart(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val followIndex = reader.readUByte()
        val npcId = reader.readUShortLE()

        val charId = session.roleId.toLong()
        when (val result = petUpgradeSystem.startUpgrade(charId, followIndex, npcId)) {
            is UpgradeStartResult.Ok -> {
                // S:008-011 屬性預覽（kind=1 強化）
                responseSender.sendPetTempUpgradeData(session, 1, npcId, result.pending.gains)
                logger.debug { "${session.characterName} 強化發起：slot=$followIndex npcId=$npcId" }
            }
            is UpgradeStartResult.Fail -> {
                // 驗證失敗 → S:074-003 讓客戶端清除強化狀態
                responseSender.sendPetUpgradeResult(session, false)
            }
        }
    }

    /**
     * C:074-002 確認強化
     *
     * 扣款後依序下發：金錢/碎片/畫冊扣除 → 強化成功 → 星等更新 → 武將完整資料。
     */
    private suspend fun handleConfirm(session: GameSession) {
        val charId = session.roleId.toLong()
        val result = petUpgradeSystem.confirmUpgrade(charId) ?: run {
            responseSender.sendPetUpgradeResult(session, false)
            return
        }

        // 扣款明細
        if (result.moneySpent > 0) {
            responseSender.sendSubtractMoney(session, result.moneySpent.toInt())
        }
        if (result.debrisAfter >= 0) {
            responseSender.sendRoleCountUpdate(
                session,
                mapOf(RoleCountSystem.NPC_DEBRIS to (result.debrisAfter to result.debrisMax))
            )
        }
        for ((slot, count) in result.consumedSlots) {
            responseSender.sendReduceBagItem(session, slot, count)
        }

        // 強化結果與資料刷新
        responseSender.sendPetUpgradeResult(session, true)
        responseSender.sendPetUpgradeLv(session, result.followSlot, result.upgraded.upgradeLv)
        responseSender.sendFollowNpcFullData(session, listOf(result.upgraded))

        logger.info {
            "${session.characterName} 強化成功：npcId=${result.upgraded.npcId} → ${result.upgraded.upgradeLv}星"
        }
    }

    /**
     * C:074-003 取消強化
     */
    private fun handleCancel(session: GameSession) {
        val charId = session.roleId.toLong()
        petUpgradeSystem.cancelUpgrade(charId)
        responseSender.sendPetUpgradeResult(session, false)
    }
}

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
 * Handler cường hoá tướng (mainKind=74)
 *
 * Nguồn: protocal.lua protocolTable[74], quy trình cường hoá trong UIStatus.lua
 *
 * C:074-001 Cường hoá tướng — +chỉ số tướng(1) +NPCID(2)
 * C:074-002 Xác nhận cường hoá tướng — payload trống
 * C:074-003 Huỷ cường hoá tướng — payload trống
 *
 * Quy trình ba bước:
 * 1. Bắt đầu → kiểm tra tài nguyên → roll thử sáu chỉ số → S:008-011 xem trước thuộc tính
 * 2. Xác nhận → khấu trừ tài nguyên (tiền game/mảnh/sổ tranh) → S:074-002 + S:015-020 + làm mới dữ liệu đầy đủ của tướng
 * 3. Huỷ → xoá dữ liệu tạm → S:074-003
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
            else -> logger.warn { "Sub-protocol cường hóa tướng chưa xử lý: subKind=$subKind" }
        }
    }

    /**
     * C:074-001 Bắt đầu cường hoá
     *
     * Định dạng: +chỉ số tướng(1) +NPCID(2)
     */
    private fun handleStart(session: GameSession, data: ByteArray) {
        if (data.size < 3) return
        val reader = PacketReader(data)
        val followIndex = reader.readUByte()
        val npcId = reader.readUShortLE()

        val charId = session.roleId.toLong()
        when (val result = petUpgradeSystem.startUpgrade(charId, followIndex, npcId)) {
            is UpgradeStartResult.Ok -> {
                // S:008-011 xem trước thuộc tính (kind=1 cường hoá)
                responseSender.sendPetTempUpgradeData(session, 1, npcId, result.pending.gains)
                logger.debug { "${session.characterName} bắt đầu cường hóa: slot=$followIndex npcId=$npcId" }
            }
            is UpgradeStartResult.Fail -> {
                // Kiểm tra hợp lệ thất bại → S:074-003 để client xoá trạng thái cường hoá
                responseSender.sendPetUpgradeResult(session, false)
            }
        }
    }

    /**
     * C:074-002 Xác nhận cường hoá
     *
     * Sau khi khấu tiền lần lượt gửi xuống: khấu trừ tiền/mảnh/sổ tranh → cường hoá thành công → cập nhật cấp sao → dữ liệu đầy đủ của tướng.
     */
    private suspend fun handleConfirm(session: GameSession) {
        val charId = session.roleId.toLong()
        val result = petUpgradeSystem.confirmUpgrade(charId) ?: run {
            responseSender.sendPetUpgradeResult(session, false)
            return
        }

        // Chi tiết khấu tiền
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

        // Kết quả cường hoá và làm mới dữ liệu
        responseSender.sendPetUpgradeResult(session, true)
        responseSender.sendPetUpgradeLv(session, result.followSlot, result.upgraded.upgradeLv)
        responseSender.sendFollowNpcFullData(session, listOf(result.upgraded))

        logger.info {
            "${session.characterName} cường hóa thành công: npcId=${result.upgraded.npcId} → ${result.upgraded.upgradeLv} sao"
        }
    }

    /**
     * C:074-003 Huỷ cường hoá
     */
    private fun handleCancel(session: GameSession) {
        val charId = session.roleId.toLong()
        petUpgradeSystem.cancelUpgrade(charId)
        responseSender.sendPetUpgradeResult(session, false)
    }
}

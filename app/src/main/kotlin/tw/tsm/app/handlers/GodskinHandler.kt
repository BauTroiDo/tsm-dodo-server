package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.GodskinResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.MythEnchantResult
import tw.tsm.gamelogic.systems.MythEnchantSystem
import tw.tsm.gamelogic.systems.MythUpgradeResult
import tw.tsm.gamelogic.systems.MythUpgradeSystem
import tw.tsm.protocol.PacketReader

private val godskinLogger = KotlinLogging.logger {}

/** DVT UIGodskin.lua mainKind=108. */
class GodskinHandler(
    private val mythEnchantSystem: MythEnchantSystem,
    private val mythUpgradeSystem: MythUpgradeSystem,
    private val godskinResponse: GodskinResponseSender,
    private val responseSender: ResponseSender
) : PacketHandler {
    override val mainKind: Int = Opcodes.GODSKIN

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        val characterId = session.roleId.toLong()
        if (characterId <= 0) return

        when (subKind) {
            1 -> handleUpgrade(session, characterId, data)
            2 -> handleEnchant(session, characterId, data)
            3 -> {
                // Remake stays separate until its DVT contract is audited.
                godskinResponse.sendSimpleResult(session, subKind, MythEnchantResult.RESULT_INVALID)
            }
            else -> godskinLogger.warn { "Godskin unsupported subKind=$subKind" }
        }
    }

    private suspend fun handleUpgrade(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size != 4) {
            godskinResponse.sendUpgradeResult(
                session,
                MythUpgradeResult.RESULT_INVALID,
                0,
                null
            )
            return
        }

        val reader = PacketReader(data)
        val mythSlot = reader.readUShortLE()
        val supportSlot = reader.readUShortLE()

        // D8-B1.3a: supportSlot=0 (Money/TS probability path) intentionally stays non-mutating
        // until fail-consumption semantics are authoritative.
        if (supportSlot == 0) {
            godskinResponse.sendUpgradeResult(
                session,
                MythUpgradeResult.RESULT_INVALID,
                mythSlot,
                null
            )
            return
        }

        val result = try {
            mythUpgradeSystem.upgradeWithStone(characterId, mythSlot, supportSlot)
        } catch (t: Throwable) {
            godskinLogger.error(t) {
                "Myth stone upgrade failed unexpectedly: char=$characterId mythSlot=$mythSlot supportSlot=$supportSlot"
            }
            MythUpgradeResult(MythUpgradeResult.RESULT_GENERIC_ERROR, mythSlot, supportSlot)
        }

        godskinResponse.sendUpgradeResult(
            session,
            result.code,
            result.mythSlot,
            result.updatedMyth
        )

        if (result.success) {
            val updated = result.updatedMyth ?: return
            responseSender.sendSetBagItem(session, result.mythSlot, updated, false)
            responseSender.sendReduceBagItem(session, result.supportSlot, 1)
        }
    }

    private suspend fun handleEnchant(session: GameSession, characterId: Long, data: ByteArray) {
        if (data.size != 4) {
            godskinResponse.sendEnchantResult(
                session,
                MythEnchantResult.RESULT_INVALID,
                0,
                0,
                null
            )
            return
        }

        val reader = PacketReader(data)
        val mythSlot = reader.readUShortLE()
        val runeSlot = reader.readUShortLE()
        val result = try {
            mythEnchantSystem.enchant(characterId, mythSlot, runeSlot)
        } catch (t: Throwable) {
            godskinLogger.error(t) {
                "Myth enchant failed unexpectedly: char=$characterId mythSlot=$mythSlot runeSlot=$runeSlot"
            }
            MythEnchantResult(MythEnchantResult.RESULT_INVALID, mythSlot, runeSlot)
        }

        godskinResponse.sendEnchantResult(
            session,
            result.code,
            result.mythSlot,
            result.runeSlot,
            result.updatedMyth
        )

        if (result.success) {
            val updatedMyth = result.updatedMyth ?: return
            responseSender.sendPlayerCurrency(
                session,
                MythEnchantSystem.TS_CURRENCY_KIND,
                result.tsBalance
            )
            responseSender.sendSetBagItem(
                session,
                result.mythSlot,
                updatedMyth,
                false
            )
            responseSender.sendReduceBagItem(session, result.runeSlot, 1)
        }
    }
}

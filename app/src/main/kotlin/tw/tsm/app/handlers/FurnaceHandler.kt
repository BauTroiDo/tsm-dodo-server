package tw.tsm.app.handlers

import io.github.oshai.kotlinlogging.KotlinLogging
import tw.tsm.app.response.FurnaceResponseSender
import tw.tsm.domain.constants.Opcodes
import tw.tsm.domain.enums.SessionState
import tw.tsm.domain.interfaces.GameSession
import tw.tsm.domain.interfaces.PacketHandler
import tw.tsm.domain.interfaces.ResponseSender
import tw.tsm.gamelogic.systems.FurnaceBuyResult
import tw.tsm.gamelogic.systems.FurnaceDismantleResult
import tw.tsm.gamelogic.systems.FurnaceSystem
import tw.tsm.gamelogic.systems.InventorySystem
import tw.tsm.protocol.PacketReader

private val logger = KotlinLogging.logger {}

/** DVT UIFurnace — mainKind 89. */
class FurnaceHandler(
    private val furnaceSystem: FurnaceSystem,
    private val furnaceResponse: FurnaceResponseSender,
    private val responseSender: ResponseSender,
    private val inventorySystem: InventorySystem,
) : PacketHandler {
    override val mainKind: Int = Opcodes.FURNACE

    override suspend fun handle(session: GameSession, subKind: Int, data: ByteArray) {
        if (session.state != SessionState.IN_GAME) return
        when (subKind) {
            1 -> handleQuery(session, data)
            2 -> handleBuy(session, data)
            3 -> handleDismantle(session, data)
            else -> logger.warn { "Furnace subKind chưa hỗ trợ: $subKind" }
        }
    }

    private suspend fun handleQuery(session: GameSession, data: ByteArray) {
        if (data.isNotEmpty()) {
            furnaceResponse.sendShopFail(session)
            return
        }
        try {
            furnaceResponse.sendShopData(
                session,
                furnaceSystem.snapshot(session.roleId.toLong()),
                openUi = true,
            )
        } catch (e: Exception) {
            logger.error(e) { "Furnace query lỗi: charId=${session.roleId}" }
            furnaceResponse.sendShopFail(session)
        }
    }

    private suspend fun handleBuy(session: GameSession, data: ByteArray) {
        if (data.size != 4) {
            furnaceResponse.sendBuyResult(session, FurnaceSystem.BUY_ERR_ITEM)
            return
        }
        val reader = PacketReader(data)
        val category = reader.readUByte()
        val slot = reader.readUByte()
        val itemId = reader.readUShortLE()

        when (val result = furnaceSystem.buy(session.roleId.toLong(), category, slot, itemId)) {
            is FurnaceBuyResult.Fail -> furnaceResponse.sendBuyResult(session, result.code)
            is FurnaceBuyResult.Success -> {
                responseSender.sendRoleCountUpdate(
                    session,
                    mapOf(result.roleCountIndex to (result.roleCountValue to result.roleCountMax)),
                )
                responseSender.sendBitFlagChanges(session, listOf(result.flagId to true))
                responseSender.sendAllBagItems(
                    session,
                    inventorySystem.getBag(session.roleId.toLong()),
                    refresh = true,
                )
                furnaceResponse.sendBuyResult(session, 1, result.itemId)
            }
        }
    }

    private suspend fun handleDismantle(session: GameSession, data: ByteArray) {
        if (data.isEmpty()) {
            furnaceResponse.sendDismantleFail(session, 0, 0)
            return
        }
        val reader = PacketReader(data)
        val count = reader.readUByte()
        if (count <= 0 || count > FurnaceSystem.SHOP_SIZE || reader.remaining != count * 6) {
            furnaceResponse.sendDismantleFail(session, 0, 0)
            return
        }

        val requests = ArrayList<Pair<Int, Int>>(count)
        repeat(count) {
            val bagIndex = reader.readUShortLE()
            val qty = reader.readIntLE()
            requests += bagIndex to qty
        }

        when (val result = furnaceSystem.dismantle(session.roleId.toLong(), requests)) {
            is FurnaceDismantleResult.Fail ->
                furnaceResponse.sendDismantleFail(session, result.furnaceKind, result.itemId)

            is FurnaceDismantleResult.Success -> {
                responseSender.sendAllBagItems(
                    session,
                    inventorySystem.getBag(session.roleId.toLong()),
                    refresh = true,
                )
                val roleIndex = result.roleCountIndex
                val roleValue = result.roleCountValue
                val roleMax = result.roleCountMax
                if (roleIndex != null && roleValue != null && roleMax != null) {
                    responseSender.sendRoleCountUpdate(
                        session,
                        mapOf(roleIndex to (roleValue to roleMax)),
                    )
                }
                furnaceResponse.sendDismantleSuccess(session, result.entries)
            }
        }
    }
}

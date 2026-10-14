package tw.tsm.gamelogic.systems

/** DVT S106-008 serializes total furniture ownership as a u8 (max 255). */
object HouseFurnitureRedeemPolicy {
    fun valid(expectedItemId: Int, actualItemId: Int, furnitureId: Int,
              requested: Int, available: Int, currentlyOwned: Int): Boolean =
        expectedItemId > 0 && expectedItemId == actualItemId &&
            furnitureId in 1..65535 && requested in 1..available &&
            currentlyOwned in 0..255 && requested <= 255 - currentlyOwned
}

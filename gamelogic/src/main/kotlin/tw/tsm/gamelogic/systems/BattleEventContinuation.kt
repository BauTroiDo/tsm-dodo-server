package tw.tsm.gamelogic.systems

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException

/** Non-fatal failures must release the client event lock, including missing runtime classes. */
object BattleEventContinuation {
    private val logger = KotlinLogging.logger {}
    suspend fun run(resume: suspend () -> Unit, abort: () -> Unit) {
        try {
            resume()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Battle event continuation failed; releasing event" }
            abort()
        } catch (e: LinkageError) {
            logger.error(e) { "Battle runtime class failure; releasing event" }
            abort()
        }
    }
}

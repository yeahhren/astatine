package yt.ren.astatine

import org.apache.logging.log4j.LogManager

// NB: touched by native
@Suppress("unused")
object NativeLog {
    private val logger = LogManager.getLogger("Astatine/Native")

    @JvmStatic
    fun log(level: Int, message: String) {
        when (level) {
            0 -> logger.debug(message)
            1 -> logger.info(message)
            2 -> logger.warn(message)
            else -> logger.error(message)
        }
    }
}

package Server.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Central console logger. Message payloads, especially passwords, must never be logged. */
public final class ServerLog {
    private static final Logger LOGGER = Logger.getLogger("FruitBattleServer");

    private ServerLog() {
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warning(String message) {
        LOGGER.warning(message);
    }

    public static void error(String message, Throwable error) {
        LOGGER.log(Level.SEVERE, message, error);
    }
}

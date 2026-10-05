package Client.controller;

import Client.session.GameClientSession;

/**
 * Compatibility facade for the TCP login flow.
 */
public final class LoginController {
    private String lastError = "";

    public boolean authenticate(String username, String password) {
        try {
            GameClientSession.instance().login(username, password);
            lastError = "";
            return true;
        } catch (Exception exception) {
            lastError = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            return false;
        }
    }

    public String lastError() {
        return lastError;
    }

    public static void logout(String ignoredPlayerId) {
        try {
            GameClientSession.instance().logout();
        } catch (Exception ignored) {
        }
    }
}

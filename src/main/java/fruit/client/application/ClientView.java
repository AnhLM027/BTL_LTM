package fruit.client.application;

/** UI boundary used by client logic; contains no JavaFX types. */
public interface ClientView {
    void showScreen(String name);

    void toast(String message, boolean error);

    void updateConnection();

    void clearHeld();

    void focusArena();

    void queueDisconnected();

    void queueChanged(boolean queued);

    void resultSaved(boolean saved);

    void onlinePlayersUpdated();

    void inviteReceived(java.util.Map<String, Object> invite);

    void closeInviteDialog();
}

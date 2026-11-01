package fruit.client.features.gameplay;

/** Only connection failures and rejected actions appear in the gameplay footer. */
public final class GameplayNotice {
    public enum Tone {
        INFO,
        DANGER
    }

    public record Notice(Tone tone, String icon, String text) {}

    private Notice error;
    private long expiresAt;

    public void clear() {
        error = null;
        expiresAt = 0;
    }

    public void error(String text, long now) {
        error = new Notice(Tone.DANGER, "!", text);
        expiresAt = now + 3_000_000_000L;
    }

    public Notice current(boolean connected, long now) {
        if (!connected)
            return new Notice(Tone.DANGER, "!", "Mất kết nối · Bấm Kết nối lại để trở về trận.");
        return now < expiresAt ? error : null;
    }
}

package Client.ui;

import java.util.Locale;

/** Canonical classpath paths for client artwork.
 *
 * Server rows may still contain paths written as {@code Client/assets/...}
 * (and older builds used the waiting-room design root).  Normalizing at the
 * loader boundary keeps the database protocol backward compatible while the
 * source code uses one stable asset layout.
 */
public final class AssetPaths {
    public static final String WAITING_ROOM = "ui/waiting-room/";
    public static final String PIXEL_FONT = "fonts/PixelifySans[wght].ttf";
    public static final String UI_FONT = "fonts/FVF.ttf";

    private AssetPaths() {
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        String lower = normalized.toLowerCase(Locale.ROOT);
        int assetsMarker = lower.indexOf("client/assets/");
        if (assetsMarker >= 0) {
            normalized = normalized.substring(assetsMarker + "client/assets/".length());
        }
        String oldWaitingRoot = "Fruit_Battle_MainClientFrame_v4/default_1080x760/";
        if (normalized.startsWith(oldWaitingRoot)) {
            normalized = WAITING_ROOM + normalized.substring(oldWaitingRoot.length());
        }
        if (normalized.startsWith("background/")) {
            normalized = "backgrounds/" + normalized.substring("background/".length());
        }
        if (normalized.startsWith("bomb/")) {
            normalized = "bombs/" + normalized.substring("bomb/".length());
        }
        if (normalized.equals("FVF.ttf")) normalized = UI_FONT;
        if (normalized.equals("modes/mode-fruit-group.png")) normalized = "modes/mode-classic.png";
        if (normalized.equals("modes/mode-nutrition.png")) normalized = "modes/mode-order.png";
        return normalized;
    }
}

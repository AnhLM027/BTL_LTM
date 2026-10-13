package Client.fx;

import Client.ui.AssetPaths;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared classpath asset loading for the JavaFX client. */
public final class FxAssets {
    public static final String FONT_FAMILY = "Pixelify Sans";
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    static {
        URL font = FxAssets.class.getResource("/Client/assets/" + AssetPaths.PIXEL_FONT);
        if (font != null) Font.loadFont(font.toExternalForm(), 14);
    }

    private FxAssets() {
    }

    public static ImageView image(String path) {
        path = AssetPaths.normalize(path);
        URL url = FxAssets.class.getResource("/Client/assets/" + path);
        if (url == null) return new ImageView();
        Image image = CACHE.computeIfAbsent(path,
                ignored -> new Image(url.toExternalForm(), 0, 0, false, true));
        return new ImageView(image);
    }

    public static Image load(String path) {
        return image(path).getImage();
    }

    /** Applies the shared theme to dialogs, which are hosted in a separate scene. */
    public static void styleDialog(DialogPane dialogPane) {
        if (dialogPane == null) return;
        URL css = FxAssets.class.getResource("/Client/fx/fx-client.css");
        if (css != null && !dialogPane.getStylesheets().contains(css.toExternalForm())) {
            dialogPane.getStylesheets().add(css.toExternalForm());
        }
        if (!dialogPane.getStyleClass().contains("fx-dialog")) {
            dialogPane.getStyleClass().add("fx-dialog");
        }
    }
}

package Client.ui;

import javax.swing.ImageIcon;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Image;

/**
 * Loads bundled assets first and supports the IDE source-tree fallback.
 */
public final class AssetLoader {
    private AssetLoader() {
    }

    public static ImageIcon icon(String relativePath) {
        URL resource = AssetLoader.class.getResource("/Client/assets/" + relativePath);
        if (resource == null) {
            resource = AssetLoader.class.getResource("/client/assets/" + relativePath);
        }
        if (resource != null) {
            return new ImageIcon(resource);
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            for (String root : new String[]{
                    "src/src/Client/assets", "src/Client/assets", "Client/assets", "assets"
            }) {
                Path candidate = current.resolve(root).resolve(relativePath).normalize();
                if (Files.isRegularFile(candidate)) {
                    return new ImageIcon(candidate.toString());
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /** Scales an image into a box without changing its aspect ratio. */
    public static ImageIcon scaleToFit(ImageIcon source, int maxWidth, int maxHeight) {
        if (source == null || source.getIconWidth() <= 0 || source.getIconHeight() <= 0) return source;
        double scale = Math.min(maxWidth / (double) source.getIconWidth(), maxHeight / (double) source.getIconHeight());
        int width = Math.max(1, (int) Math.round(source.getIconWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getIconHeight() * scale));
        return new ImageIcon(source.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }
}

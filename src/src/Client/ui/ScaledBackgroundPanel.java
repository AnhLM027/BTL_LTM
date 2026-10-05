package Client.ui;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Panel that always paints a background image to the current component size. */
public final class ScaledBackgroundPanel extends JPanel {
    private final ImageIcon background;

    public ScaledBackgroundPanel(ImageIcon background) {
        this.background = background;
        setOpaque(true);
        setBackground(new Color(255, 250, 232));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (background == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int imageWidth = background.getIconWidth();
        int imageHeight = background.getIconHeight();
        double scale = Math.max(getWidth() / (double) imageWidth, getHeight() / (double) imageHeight);
        int drawWidth = (int) Math.ceil(imageWidth * scale);
        int drawHeight = (int) Math.ceil(imageHeight * scale);
        int x = (getWidth() - drawWidth) / 2;
        int y = (getHeight() - drawHeight) / 2;
        g.drawImage(background.getImage(), x, y, drawWidth, drawHeight, this);
        g.dispose();
    }
}

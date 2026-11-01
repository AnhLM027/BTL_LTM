package fruit.client.shared.ui;

import static fruit.client.shared.ui.UiTheme.C_CORAL;
import static fruit.client.shared.ui.UiTheme.C_CYAN;
import static fruit.client.shared.ui.UiTheme.C_LIME;
import static fruit.client.shared.ui.UiTheme.LINE;
import static fruit.client.shared.ui.UiTheme.PANEL;
import static fruit.client.shared.ui.UiTheme.bodyFont;
import static fruit.client.shared.ui.UiTheme.pixelFont;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderImage;
import javafx.scene.layout.BorderRepeat;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads classpath assets and draws reusable sprites and frame skins. */
public final class GameArt {
    private GameArt() {}

    private static Image orchardImage;
    private static Image spriteAtlas;
    private static Image uiAtlas;
    private static Image referenceFrame;
    private static Image referencePortraits;
    private static Image referenceItems;
    private static Image frozenBasket;
    private static double[] frozenBasketBounds;
    private static final double[][] portraitBounds = new double[2][];
    private static final double[][] itemBounds = new double[8][];
    private static final Map<Long, Image> frameCache = new LinkedHashMap<>();
    private static final String[] APPLE = {
        ".....bb.....",
        ".....b.gg...",
        "....bggg....",
        "..rrrrrrrr..",
        ".rrhrrrrrrr.",
        "rrhhrrrrrrrr",
        "rrhrrrrrrrrr",
        "rrrrrrrrrrrr",
        "rrrrrrrrrrrr",
        ".rrrrrrrrrr.",
        "..rrrrrrrr..",
        "...rr..rr..."
    };
    private static final String[] BANANA = {
        ".........bb.",
        ".........yb.",
        "........yyy.",
        ".......yyyy.",
        "......yyyyy.",
        ".....yyyyy..",
        "y...yyyyy...",
        "yyyyyyyy....",
        "yyyyyyy.....",
        ".yyyyy......",
        "..yyy.......",
        "...bb......."
    };
    private static final String[] GRAPE = {
        ".....bb.....",
        "....bggg....",
        "...gggg.....",
        "..pp..pp....",
        ".phpppppp...",
        ".pppppppp...",
        "..pppppp....",
        "...ppppp....",
        "...pppp.....",
        "....ppp.....",
        ".....pp.....",
        "............"
    };
    private static final String[] BOMB = {
        "........y...",
        "......yy....",
        ".....bb.....",
        "....bbbb....",
        "..dddddddd..",
        ".ddhhdddddd.",
        "ddhhdddddddd",
        "ddhddddddddd",
        "dddddddddddd",
        ".dddddddddd.",
        "..dddddddd..",
        "....dddd...."
    };

    public static void orchard(GraphicsContext g, double w, double h, boolean own, boolean hero) {
        g.setImageSmoothing(false);
        if (orchardImage != null) drawCover(g, orchardImage, 0, 0, w, h, .5);
        else {
            g.setFill(Color.web("#b5d4be"));
            g.fillRect(0, 0, w, h);
            g.setFill(Color.web("#738855"));
            g.fillRect(0, h * .75, w, h * .25);
            g.setFill(Color.web("#d7ab6d"));
            g.fillRect(0, h * .91, w, h * .09);
        }
    }

    /** Uniform scaling with a crop, so scenery never stretches to match its container. */
    public static void drawCover(
            GraphicsContext g,
            Image image,
            double x,
            double y,
            double width,
            double height,
            double verticalAnchor) {
        if (image == null || width <= 0 || height <= 0) return;
        double scale = Math.max(width / image.getWidth(), height / image.getHeight());
        double sourceWidth = width / scale, sourceHeight = height / scale;
        double sourceX = (image.getWidth() - sourceWidth) / 2;
        double sourceY =
                (image.getHeight() - sourceHeight) * Math.max(0, Math.min(1, verticalAnchor));
        g.setImageSmoothing(false);
        g.drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, x, y, width, height);
    }

    public static void drawAvatar(
            GraphicsContext g, double x, double y, double size, int avatarId) {
        drawAvatar(g, x, y, size, avatarId != 1);
    }

    public static void basket(GraphicsContext g, double x, double y, boolean own, boolean frozen) {
        if (frozen && frozenBasket != null && frozenBasketBounds != null) {
            g.setFill(Color.web("#183b52", .22));
            g.fillOval(x - 49, y + 26, 98, 12);
            drawArt(g, frozenBasket, frozenBasketBounds, x - 54, y - 31, 108, 74);
            return;
        }
        if (referenceItems != null && itemBounds[4] != null) {
            g.setFill(Color.web("#533015", .26));
            g.fillOval(x - 49, y + 26, 98, 12);
            drawArt(g, referenceItems, itemBounds[4], x - 54, y - 31, 108, 74);
            if (frozen) {
                g.setStroke(Color.web("#b6edff", .85));
                g.setLineWidth(3);
                g.strokeRect(x - 52, y - 3, 104, 37);
            }
            return;
        }
        double xx = Math.rint(x - 50);
        g.setFill(Color.web("#0b1c27", .55));
        g.fillRect(xx + 5, y + 27, 92, 8);
        g.setFill(Color.web("#74543b"));
        g.fillRect(xx + 7, y + 3, 86, 27);
        g.setFill(Color.web("#b7834e"));
        g.fillRect(xx + 11, y + 5, 78, 22);
        g.setFill(Color.web("#dda761"));
        g.fillRect(xx, y, 100, 7);
        g.fillRect(xx + 10, y + 10, 80, 4);
        g.fillRect(xx + 14, y + 20, 72, 3);
        g.setFill(Color.web("#805631"));
        for (int i = 22; i < 90; i += 17) g.fillRect(xx + i, y + 7, 4, 22);
        g.setFill(frozen ? C_CYAN : own ? C_LIME : C_CORAL);
        g.fillRect(xx + 36, y - 5, 28, 5);
        g.fillRect(xx + 43, y - 11, 14, 6);
    }

    public static void sprite(GraphicsContext g, String kind, double cx, double cy, double px) {
        int referenceIndex =
                switch (kind) {
                    case "BANANA" -> 1;
                    case "GRAPE" -> 2;
                    case "BOMB" -> 3;
                    default -> 0;
                };
        if (referenceItems != null && itemBounds[referenceIndex] != null) {
            drawArt(
                    g,
                    referenceItems,
                    itemBounds[referenceIndex],
                    cx - 8 * px,
                    cy - 8 * px,
                    16 * px,
                    16 * px);
            return;
        }
        if (spriteAtlas != null) {
            int index =
                    switch (kind) {
                        case "BANANA" -> 1;
                        case "GRAPE" -> 2;
                        case "BOMB" -> 3;
                        default -> 0;
                    };
            g.setImageSmoothing(false);
            g.drawImage(
                    spriteAtlas,
                    index * 32,
                    0,
                    32,
                    32,
                    Math.rint(cx - 8 * px),
                    Math.rint(cy - 8 * px),
                    16 * px,
                    16 * px);
            return;
        }
        String[] cells =
                switch (kind) {
                    case "BANANA" -> BANANA;
                    case "GRAPE" -> GRAPE;
                    case "BOMB" -> BOMB;
                    default -> APPLE;
                };
        double x = Math.rint(cx - 6 * px), y = Math.rint(cy - 6 * px);
        for (int row = 0; row < cells.length; row++)
            for (int col = 0; col < cells[row].length(); col++) {
                char c = cells[row].charAt(col);
                if (c == '.') continue;
                g.setFill(
                        switch (c) {
                            case 'r' -> Color.web("#f47e72");
                            case 'g' -> Color.web("#b2d77b");
                            case 'b' -> Color.web("#8d6e48");
                            case 'h' ->
                                    Color.web(
                                            kind.equals("BOMB")
                                                    ? "#71899c"
                                                    : kind.equals("GRAPE") ? "#d1b4ef" : "#ffd9b0");
                            case 'y' -> Color.web("#f3d77c");
                            case 'p' -> Color.web("#aa8fdb");
                            default -> Color.web("#263443");
                        });
                g.fillRect(x + col * px, y + row * px, px, px);
            }
    }

    public static void drawAvatar(GraphicsContext g, double x, double y, double size, boolean own) {
        if (referencePortraits != null && portraitBounds[own ? 0 : 1] != null) {
            g.setFill(Color.web("#f5dda2"));
            g.fillRect(x, y, size, size);
            drawArt(g, referencePortraits, portraitBounds[own ? 0 : 1], x, y, size, size);
            return;
        }
        g.save();
        g.translate(x, y);
        g.scale(size / 64, size / 64);
        g.setFill(Color.web("#dfd6ab"));
        g.fillRect(0, 0, 64, 64);
        g.setFill(Color.web("#5b7650"));
        g.fillRect(8, 46, 48, 18);
        g.setFill(Color.web("#ecc48a"));
        g.fillRect(17, 22, 30, 29);
        g.setFill(Color.web("#d6a86b"));
        g.fillRect(17, 22, 30, 5);
        g.setFill(Color.web("#47533a"));
        g.fillRect(13, 16, 38, 7);
        g.fillRect(22, 6, 24, 12);
        g.setFill(Color.web("#f0d27c"));
        g.fillRect(12, 19, 42, 5);
        g.fillRect(23, 12, 22, 4);
        g.setFill(Color.web("#33412f"));
        g.fillRect(24, 31, 4, 5);
        g.fillRect(38, 31, 4, 5);
        g.fillRect(29, 42, 9, 3);
        g.setFill(Color.web("#cd8263"));
        g.fillRect(20, 38, 6, 3);
        g.fillRect(41, 38, 5, 3);
        g.setFill(own ? C_LIME : C_CORAL);
        g.fillRect(29, 51, 5, 13);
        g.restore();
    }

    public static void drawTrophy(GraphicsContext g, double x, double y, double p) {
        if (referenceItems != null && itemBounds[7] != null) {
            drawArt(g, referenceItems, itemBounds[7], x, y, 13 * p, 13 * p);
            return;
        }
        g.setFill(Color.web("#f2d581"));
        g.fillRect(x + 3 * p, y, 7 * p, 2 * p);
        g.fillRect(x + 4 * p, y + 2 * p, 5 * p, 5 * p);
        g.fillRect(x + 5 * p, y + 7 * p, 3 * p, p);
        g.fillRect(x + 6 * p, y + 8 * p, p, 3 * p);
        g.fillRect(x + 4 * p, y + 11 * p, 5 * p, 2 * p);
        g.fillRect(x + p, y + p, 2 * p, 4 * p);
        g.fillRect(x + 10 * p, y + p, 2 * p, 4 * p);
        g.fillRect(x + 2 * p, y + 5 * p, 3 * p, p);
        g.fillRect(x + 8 * p, y + 5 * p, 3 * p, p);
        g.setFill(Color.web("#b58c54"));
        g.fillRect(x + 5 * p, y + 12 * p, 4 * p, p);
    }

    public static void canvasText(
            GraphicsContext g,
            String value,
            double x,
            double y,
            double size,
            Color color,
            boolean center) {
        g.setFont(
                Font.font(
                        value.matches("[A-Za-z0-9 .+:/!×−–-]+") ? pixelFont : bodyFont,
                        FontWeight.BOLD,
                        size));
        g.setFill(color);
        g.setTextAlign(
                center
                        ? javafx.scene.text.TextAlignment.CENTER
                        : javafx.scene.text.TextAlignment.LEFT);
        g.fillText(value, x, y);
    }

    public static void loadAssets() {
        orchardImage = imageResource("orchard-reference.png");
        if (orchardImage == null) orchardImage = imageResource("orchard.png");
        spriteAtlas = imageResource("sprites.png");
        uiAtlas = imageResource("ui.png");
        referenceFrame = imageResource("skin-frame.png");
        referencePortraits = imageResource("portraits-reference.png");
        referenceItems = imageResource("items-reference-v2.png");
        frozenBasket = imageResource("basket-frozen.png");
        frozenBasketBounds = frozenBasket == null ? null : alphaBounds(frozenBasket, 0, 0, 1, 1);
        if (referencePortraits != null)
            for (int i = 0; i < 2; i++)
                portraitBounds[i] = alphaBounds(referencePortraits, i, 0, 2, 1);
        if (referenceItems != null)
            for (int i = 0; i < 8; i++)
                itemBounds[i] = alphaBounds(referenceItems, i % 4, i / 4, 4, 2);
        for (String weight : new String[] {"Bold", "Black"}) {
            try (InputStream stream =
                    GameArt.class.getResourceAsStream("/fruit/fonts/NotoSans-" + weight + ".ttf")) {
                if (stream != null) Font.loadFont(stream, 14);
            } catch (IOException ignored) {
            }
        }
        try (InputStream stream =
                GameArt.class.getResourceAsStream("/fruit/fonts/NotoSans-Regular.ttf")) {
            Font loaded = stream == null ? null : Font.loadFont(stream, 14);
            if (loaded != null) bodyFont = loaded.getFamily();
        } catch (IOException ignored) {
        }
        try (InputStream stream =
                GameArt.class.getResourceAsStream("/fruit/fonts/PixelifySans.ttf")) {
            Font loaded = stream == null ? null : Font.loadFont(stream, 24);
            if (loaded != null) pixelFont = loaded.getFamily();
        } catch (IOException ignored) {
        }
    }

    public static Image imageResource(String name) {
        try (InputStream stream = GameArt.class.getResourceAsStream("/fruit/" + name)) {
            return stream == null ? null : new Image(stream);
        } catch (Exception unavailable) {
            return null;
        }
    }

    public static void drawIcon(GraphicsContext g, String name, double x, double y, double size) {
        if (name.equals("MAGNET")) {
            g.save();
            g.translate(x - size / 2, y - size / 2);
            g.scale(size / 32, size / 32);
            g.setFill(Color.web("#512c20"));
            g.fillRect(3, 3, 10, 21);
            g.fillRect(19, 3, 10, 21);
            g.fillRect(7, 22, 18, 7);
            g.setFill(Color.web("#c74334"));
            g.fillRect(5, 5, 6, 18);
            g.fillRect(9, 23, 7, 4);
            g.setFill(Color.web("#37899c"));
            g.fillRect(21, 5, 6, 18);
            g.fillRect(16, 23, 7, 4);
            g.setFill(Color.web("#f5ead3"));
            g.fillRect(5, 5, 6, 6);
            g.fillRect(21, 5, 6, 6);
            g.restore();
            return;
        }
        int artIndex =
                switch (name) {
                    case "BOMB" -> 3;
                    case "FREEZE" -> 5;
                    case "TROPHY" -> 7;
                    default -> -1;
                };
        if (artIndex >= 0 && referenceItems != null && itemBounds[artIndex] != null) {
            drawArt(
                    g,
                    referenceItems,
                    itemBounds[artIndex],
                    x - size / 2,
                    y - size / 2,
                    size,
                    size);
            return;
        }
        int index =
                switch (name) {
                    case "BOMB" -> 3;
                    case "FREEZE" -> 4;
                    case "MUTE" -> 6;
                    case "SOUND" -> 7;
                    default -> 8;
                };
        if (spriteAtlas != null) {
            g.setImageSmoothing(false);
            g.drawImage(spriteAtlas, index * 32, 0, 32, 32, x - size / 2, y - size / 2, size, size);
        } else {
            g.setFill(name.equals("FREEZE") ? C_CYAN : C_CORAL);
            g.fillRect(x - size * .25, y - size * .25, size * .5, size * .5);
        }
    }

    public static void woodFrame(GraphicsContext g, double w, double h) {
        if (referenceFrame != null) {
            int pw = Math.max(1, (int) Math.ceil(w)), ph = Math.max(1, (int) Math.ceil(h));
            long key = ((long) pw << 32) | ph;
            Image cached = frameCache.get(key);
            if (cached == null) {
                Canvas buffer = new Canvas(pw, ph);
                paintReferenceFrame(buffer.getGraphicsContext2D(), pw, ph);
                javafx.scene.SnapshotParameters parameters = new javafx.scene.SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                cached = buffer.snapshot(parameters, null);
                if (frameCache.size() >= 24) frameCache.clear();
                frameCache.put(key, cached);
            }
            g.setImageSmoothing(false);
            g.drawImage(cached, 0, 0, w, h);
            return;
        }
        if (uiAtlas == null) return;
        double c = 12;
        g.drawImage(uiAtlas, 0, 0, 6, 6, 0, 0, c, c);
        g.drawImage(uiAtlas, 26, 0, 6, 6, w - c, 0, c, c);
        g.drawImage(uiAtlas, 0, 26, 6, 6, 0, h - c, c, c);
        g.drawImage(uiAtlas, 26, 26, 6, 6, w - c, h - c, c, c);
        g.drawImage(uiAtlas, 6, 0, 20, 6, c, 0, w - c * 2, c);
        g.drawImage(uiAtlas, 6, 26, 20, 6, c, h - c, w - c * 2, c);
        g.drawImage(uiAtlas, 0, 6, 6, 20, 0, c, c, h - c * 2);
        g.drawImage(uiAtlas, 26, 6, 6, 20, w - c, c, c, h - c * 2);
    }

    public static void paintReferenceFrame(GraphicsContext g, double w, double h) {
        paintReferenceFrame(g, w, h, 46);
    }

    public static void paintReferenceFrame(
            GraphicsContext g, double w, double h, double maxCorner) {
        double sw = referenceFrame.getWidth(), sh = referenceFrame.getHeight();
        double sx = sw * .24, sy = sh * .24, c = Math.min(maxCorner, Math.min(w, h) * .34);
        g.setImageSmoothing(false);
        g.drawImage(referenceFrame, 0, 0, sx, sy, 0, 0, c, c);
        g.drawImage(referenceFrame, sw - sx, 0, sx, sy, w - c, 0, c, c);
        g.drawImage(referenceFrame, 0, sh - sy, sx, sy, 0, h - c, c, c);
        g.drawImage(referenceFrame, sw - sx, sh - sy, sx, sy, w - c, h - c, c, c);
        g.drawImage(referenceFrame, sx, 0, sw - sx * 2, sy, c, 0, w - c * 2, c);
        g.drawImage(referenceFrame, sx, sh - sy, sw - sx * 2, sy, c, h - c, w - c * 2, c);
        g.drawImage(referenceFrame, 0, sy, sx, sh - sy * 2, 0, c, c, h - c * 2);
        g.drawImage(referenceFrame, sw - sx, sy, sx, sh - sy * 2, w - c, c, c, h - c * 2);
    }

    public static void parchment(
            GraphicsContext g, double x, double y, double w, double h, double inset) {
        g.setFill(Color.web(PANEL));
        g.fillRect(x + 5, y + 5, w - 10, h - 10);
        g.setFill(Color.web("#fff8df", .7));
        g.fillRect(x + inset, y + inset, w - inset * 2, 2);
        g.save();
        g.translate(x, y);
        woodFrame(g, w, h);
        g.restore();
    }

    public static void decorate(Region region, double width) {
        region.getStyleClass().addAll("wood-skin", "wood-skin-" + (int) width);
        if (referenceFrame == null) {
            region.setBorder(
                    new Border(
                            new BorderStroke(
                                    Color.web(LINE),
                                    BorderStrokeStyle.SOLID,
                                    CornerRadii.EMPTY,
                                    new BorderWidths(2, 2, 4, 2))));
            return;
        }
        region.setBorder(
                new Border(
                        new BorderImage(
                                referenceFrame,
                                new BorderWidths(width),
                                Insets.EMPTY,
                                new BorderWidths(
                                        referenceFrame.getHeight() * .24,
                                        referenceFrame.getWidth() * .24,
                                        referenceFrame.getHeight() * .24,
                                        referenceFrame.getWidth() * .24),
                                false,
                                BorderRepeat.STRETCH,
                                BorderRepeat.STRETCH)));
    }

    public static String frameCss() {
        URL texture = GameArt.class.getResource("/fruit/skin-frame.png");
        if (texture == null) return "";
        StringBuilder styles =
                new StringBuilder(".wood-skin {-fx-border-image-source:url('")
                        .append(texture.toExternalForm())
                        .append("');-fx-border-image-slice:24%;")
                        .append("-fx-border-image-repeat:stretch;-fx-border-image-insets:0;}\n");
        for (int width = 5; width <= 30; width++)
            styles.append(".wood-skin-")
                    .append(width)
                    .append(" {-fx-border-image-width:")
                    .append(width)
                    .append("px;}\n");
        return styles.toString();
    }

    public static double[] alphaBounds(Image art, int col, int row, int cols, int rows) {
        int originX = (int) (col * art.getWidth() / cols),
                originY = (int) (row * art.getHeight() / rows);
        int cw = (int) ((col + 1) * art.getWidth() / cols) - originX;
        int ch = (int) ((row + 1) * art.getHeight() / rows) - originY;
        int left = cw, top = ch, right = -1, bottom = -1;
        PixelReader reader = art.getPixelReader();
        if (reader == null) return null;
        for (int yy = 0; yy < ch; yy++)
            for (int xx = 0; xx < cw; xx++) {
                if ((reader.getArgb(originX + xx, originY + yy) >>> 24) > 18) {
                    left = Math.min(left, xx);
                    top = Math.min(top, yy);
                    right = Math.max(right, xx);
                    bottom = Math.max(bottom, yy);
                }
            }
        return right < left
                ? null
                : new double[] {originX + left, originY + top, right - left + 1, bottom - top + 1};
    }

    public static void drawArt(
            GraphicsContext g, Image art, double[] source, double x, double y, double w, double h) {
        double fit = Math.min(w / source[2], h / source[3]);
        double dw = source[2] * fit, dh = source[3] * fit;
        g.setImageSmoothing(false);
        g.drawImage(
                art,
                source[0],
                source[1],
                source[2],
                source[3],
                Math.rint(x + (w - dw) / 2),
                Math.rint(y + (h - dh) / 2),
                Math.rint(dw),
                Math.rint(dh));
    }

    public static Image frameImage() {
        return referenceFrame;
    }

    public static Image orchardBackground() {
        return orchardImage;
    }
}

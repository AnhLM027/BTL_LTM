package fruit.client.features.leaderboard;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Three original pixel trophies, drawn at integer coordinates for crisp scaling. */
public final class TrophyArt {
    private TrophyArt() {}

    public static Canvas image(int rank, double size) {
        Canvas canvas = new Canvas(size, size);
        canvas.setAccessibleText("Cúp " + new String[] {"vàng", "bạc", "đồng"}[rank - 1]);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(size / 48, size / 48);
        String[] base = {"#eab52e", "#b7c8d5", "#c67c45"};
        String[] light = {"#fff1a0", "#f2f8ff", "#f6c493"};
        String[] shade = {"#ac681d", "#71879e", "#874822"};
        g.setFill(Color.web("#522b13"));
        g.fillRect(3, 8, 42, 4);
        g.fillRect(3, 12, 4, 13);
        g.fillRect(41, 12, 4, 13);
        g.fillRect(7, 25, 8, 4);
        g.fillRect(33, 25, 8, 4);
        g.fillRect(11, 5, 26, 19);
        g.fillRect(15, 24, 18, 6);
        g.fillRect(19, 30, 10, 6);
        g.fillRect(11, 36, 26, 8);
        g.setFill(Color.web(base[rank - 1]));
        g.fillRect(7, 12, 4, 11);
        g.fillRect(37, 12, 4, 11);
        g.fillRect(15, 9, 18, 15);
        g.fillRect(19, 24, 10, 4);
        g.fillRect(23, 28, 2, 9);
        g.fillRect(15, 39, 18, 2);
        g.setFill(Color.web(light[rank - 1]));
        g.fillRect(15, 9, 18, 3);
        g.fillRect(15, 12, 4, 9);
        g.fillRect(15, 39, 10, 2);
        g.setFill(Color.web(shade[rank - 1]));
        g.fillRect(29, 12, 4, 12);
        g.fillRect(19, 24, 10, 3);
        g.setFill(Color.web("#8d5129"));
        g.fillRect(15, 41, 18, 1);
        return canvas;
    }
}

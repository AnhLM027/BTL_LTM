package fruit.client.features.gameplay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fruit.client.application.ClientState;
import fruit.client.shared.ui.GameArt;
import static fruit.client.shared.ui.GameArt.basket;
import static fruit.client.shared.ui.GameArt.canvasText;
import static fruit.client.shared.ui.GameArt.drawAvatar;
import static fruit.client.shared.ui.GameArt.drawIcon;
import static fruit.client.shared.ui.GameArt.orchard;
import static fruit.client.shared.ui.GameArt.parchment;
import static fruit.client.shared.ui.GameArt.sprite;
import static fruit.client.shared.ui.GameArt.woodFrame;
import static fruit.client.shared.ui.UiTheme.C_CORAL;
import static fruit.client.shared.ui.UiTheme.C_CYAN;
import static fruit.client.shared.ui.UiTheme.C_INK;
import static fruit.client.shared.ui.UiTheme.C_LIME;
import static fruit.client.shared.ui.UiTheme.bodyFont;
import static fruit.common.MessageData.displayName;
import static fruit.common.MessageData.maps;
import static fruit.common.MessageData.orderedPlayers;
import static fruit.common.MessageData.player;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.number;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Canvas rendering only; reads authoritative snapshots without changing game rules. */
public final class GameRenderer {
    private double backdropPaintWidth = -1;
    private double backdropPaintHeight = -1;
    private long startNanos;
    private int prevMyScore;
    private int prevOtherScore;
    private long myScoreFlashUntil;
    private long otherScoreFlashUntil;
    private String prevTarget = "";
    private final ClientState model;
    private final ClientFeedback feedback;

    public GameRenderer(ClientState model, ClientFeedback feedback) {
        this.model = model;
        this.feedback = feedback;
        startNanos = System.nanoTime();
    }

    public void drawGame(Canvas arena, Button[] skillButtons, Label skillStatus, long now) {
        if (arena.getWidth() < 10 || arena.getHeight() < 10) return;
        GraphicsContext g = arena.getGraphicsContext2D();
        double w = arena.getWidth(), h = arena.getHeight();
        // The two playfields share this canvas; a new frame always starts opaque.
        g.setGlobalAlpha(1);
        g.clearRect(0, 0, w, h);
        List<Map<String, Object>> players = orderedPlayers(model.state, integer(model.user, "id"));
        Map<String, Object> me = players.isEmpty() ? obj() : players.get(0),
                other = players.size() > 1 ? players.get(1) : obj();
        double gap = 10,
                boardW = (w - gap) / 2,
                boardH = Math.max(180, h - 138),
                left = 0,
                topY = 136,
                clockW = w < 1100 ? 126 : 150,
                headerW = (w - clockW - 8) / 2;
        drawPlayerHeader(g, me, 0, 0, headerW, true);
        drawPlayerHeader(g, other, w - headerW, 0, headerW, false);
        double clockX = w / 2;
        drawCenterCard(g, clockX - clockW / 2, 0, clockW, 58, Color.web("#683112"));
        int seconds = Math.max(0, (int) Math.ceil(number(model.state, "remaining")));
        String clock = String.format("%02d:%02d", seconds / 60, seconds % 60);
        g.setFont(Font.font(bodyFont, FontWeight.BOLD, 27));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.setLineWidth(4);
        g.setStroke(Color.web("#3a1b0b"));
        g.strokeText(clock, clockX, 32);
        g.setFill(Color.web(number(model.state, "remaining") <= 10 ? "#ffba59" : "#ffda57"));
        g.fillText(clock, clockX, 32);
        canvasText(g, "CÒN LẠI", clockX, 46, 9, Color.web("#ffecc5"), true);
        String target = str(model.state, "target", "APPLE");
        // Detect target change → pulse
        if (!target.equals(prevTarget)) {
            feedback.targetPulseStart = now;
            prevTarget = target;
        }
        double pulseSec = (now - feedback.targetPulseStart) / 1e9;
        double pulseScale =
                !feedback.audio.reducedMotion && pulseSec < 0.4
                        ? (1.0 + 0.15 * Math.sin(pulseSec * Math.PI / 0.4))
                        : 1.0;
        drawCenterCard(g, clockX - clockW / 2, 64, clockW, 62, Color.web("#fff0c9"));
        canvasText(g, "MỤC TIÊU", clockX, 81, 10, C_INK, true);
        sprite(g, target, clockX, 104, 1.6 * pulseScale);
        drawBoard(g, me, left, topY, boardW, boardH, true, now);
        drawBoard(g, other, left + boardW + gap, topY, boardW, boardH, false, now);
        for (CatchEffect fx : feedback.effects) {
            double age = (now - fx.born) / 1e9;
            if (!feedback.audio.reducedMotion
                    && fx.kind.startsWith("CAST")
                    && !fx.kind.equals("CAST3")
                    && age < .35) {
                boolean incoming = fx.playerId == integer(me, "id");
                double source = incoming ? left + boardW + gap + boardW / 2 : left + boardW / 2;
                double destination =
                        (incoming ? left : left + boardW + gap) + 16 + fx.x / 800 * (boardW - 32);
                double progress = age / .35;
                drawIcon(
                        g,
                        fx.kind.equals("CAST1")
                                ? "BOMB"
                                : fx.kind.equals("CAST2") ? "FREEZE" : "MAGNET",
                        source + (destination - source) * progress,
                        topY + 25 - 14 * Math.sin(progress * Math.PI),
                        22);
            }
        }
        if (str(model.state, "phase").equals("COUNTDOWN")) {
            g.setFill(Color.web("#fff3d4", .7));
            g.fillRect(0, topY, w, boardH);
            double count = number(model.state, "countdown");
            canvasText(g, "SẴN SÀNG CHƯA?", w / 2, h / 2 - 27, 18, C_LIME, true);
            canvasText(
                    g,
                    String.valueOf(Math.max(1, (int) Math.ceil(count))),
                    w / 2,
                    h / 2 + 45,
                    67,
                    C_INK,
                    true);
        }
        if (!model.connected && !model.snapshotMode) {
            g.setFill(Color.web("#fff3d4", .8));
            g.fillRect(0, topY, w, boardH);
            canvasText(g, "KẾT NỐI BỊ GIÁN ĐOẠN", w / 2, h / 2 - 5, 24, C_CORAL, true);
            canvasText(
                    g,
                    "Bấm Kết nối lại ở dưới để trở lại trận đấu.",
                    w / 2,
                    h / 2 + 27,
                    14,
                    C_INK,
                    true);
        }
        int[] costs = {30, 50, 70};
        double cooldown = number(me, "cooldown");
        boolean playing = str(model.state, "phase").equals("PLAYING");
        int myEnergy = integer(me, "energy");
        for (int i = 0; i < 3; i++) {
            if (skillButtons[i] == null) continue;
            boolean disabled =
                    !playing
                            || myEnergy < costs[i]
                            || cooldown > 0
                            || (!model.connected && !model.snapshotMode);
            skillButtons[i].setDisable(disabled);
            // Update button label to show cooldown overlay
            if (cooldown > 0 && playing) {
                skillButtons[i].setStyle("-fx-opacity:0.55;");
            } else if (myEnergy < costs[i]) {
                skillButtons[i].setStyle("-fx-opacity:0.42;");
            } else {
                skillButtons[i].setStyle("");
            }
        }
        if (skillStatus != null) {
            if (cooldown > 0) skillStatus.setText(String.format("HỒI CHIÊU  %.1fs", cooldown));
            else if (myEnergy >= 70) skillStatus.setText("SẴN SÀNG TUNG CHIÊU  •  PHÍM 1 / 2 / 3");
            else if (myEnergy >= 30) skillStatus.setText("SẴN SÀNG TUNG CHIÊU  •  PHÍM 1 / 2 / 3");
            else skillStatus.setText("HỨNG ĐÚNG ĐỂ TÍCH NĂNG LƯỢNG");
        }
    }

    private static void drawCenterCard(
            GraphicsContext g, double x, double y, double w, double h, Color fill) {
        g.save();
        g.translate(x, y);
        g.setFill(fill);
        g.fillRect(5, 5, w - 10, h - 10);
        if (GameArt.frameImage() != null) GameArt.paintReferenceFrame(g, w, h, 10);
        else woodFrame(g, w, h);
        g.restore();
    }

    private void drawPlayerHeader(
            GraphicsContext g, Map<String, Object> p, double x, double y, double w, boolean own) {
        long now = System.nanoTime();
        int score = integer(p, "score");
        int combo = integer(p, "combo");
        int energy = integer(p, "energy");
        if (own) {
            if (score != prevMyScore) {
                myScoreFlashUntil = now + 350_000_000L;
                prevMyScore = score;
            }
        } else {
            if (score != prevOtherScore) {
                otherScoreFlashUntil = now + 350_000_000L;
                prevOtherScore = score;
            }
        }
        boolean flashing =
                !feedback.audio.reducedMotion
                        && (own ? (now < myScoreFlashUntil) : (now < otherScoreFlashUntil));

        parchment(g, x, y + 4, w, 116, 19);
        double portraitSize = w < 450 ? 70 : 82;
        double portraitX = x + 12, portraitY = y + 23;
        g.setFill(Color.web("#fff9d9"));
        g.fillRect(portraitX - 2, portraitY - 2, portraitSize + 4, portraitSize + 4);
        drawAvatar(g, portraitX, portraitY, portraitSize, integer(p, "avatarId", own ? 0 : 1));
        g.setStroke(Color.web("#aa7636"));
        g.setLineWidth(2);
        g.strokeRect(portraitX - 2, portraitY - 2, portraitSize + 4, portraitSize + 4);
        double tx = portraitX + portraitSize + 14;
        g.setFill(own ? C_LIME : C_CORAL);
        g.fillRect(tx - 5, y + 3, Math.min(146, w - portraitSize - 38), 24);
        g.setStroke(Color.web("#e7c778"));
        g.setLineWidth(2);
        g.strokeRect(tx - 5, y + 3, Math.min(146, w - portraitSize - 38), 24);
        canvasText(g, own ? "BẠN" : "ĐỐI THỦ", tx + 49, y + 20, 12, Color.web("#fff7d3"), true);
        g.setFont(Font.font(bodyFont, FontWeight.BOLD, w < 450 ? 18 : 22));
        g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        g.setFill(C_INK);
        g.fillText(displayName(p), tx, y + 58, Math.max(60, w - portraitSize - 119));
        Color scoreColor = flashing ? Color.web("#93552d") : (own ? C_LIME : C_CORAL);
        g.setFont(Font.font(bodyFont, FontWeight.BOLD, 36));
        g.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
        g.setFill(scoreColor);
        g.fillText(String.valueOf(score), x + w - 36, y + 61, Math.max(64, w * .25));
        String comboSuffix = combo >= 10 ? " !" : combo >= 5 ? " +" : "";
        Color comboColor = combo >= 10 ? C_CORAL : own ? C_LIME : C_CORAL;
        g.setFont(Font.font(bodyFont, FontWeight.BOLD, 12));
        g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        g.setFill(comboColor);
        g.fillText("COMBO ×" + combo + comboSuffix, tx, y + 87);

        double barX = tx + 103, barW = Math.max(36, x + w - 78 - barX);
        double energyRatio = Math.max(0, Math.min(100, energy)) / 100.0;
        g.setFill(Color.web("#d6a971"));
        g.fillRect(barX, y + 75, barW, 16);
        g.setStroke(Color.web("#9c713e"));
        g.setLineWidth(2);
        g.strokeRect(barX, y + 75, barW, 16);
        // Gradient-like: draw 4 segments of increasing brightness
        if (energyRatio > 0) {
            String[] gradColors =
                    own
                            ? new String[] {"#54772d", "#72a03e", "#9bc556", "#bad86d"}
                            : new String[] {"#8f2925", "#b43830", "#cc5646", "#e0755e"};
            double fillW = barW * energyRatio;
            for (int seg = 0; seg < 4; seg++) {
                double segStart = barW * (seg * 0.25);
                double segEnd = barW * ((seg + 1) * 0.25);
                double drawn = Math.min(fillW, segEnd) - Math.min(fillW, segStart);
                if (drawn > 0) {
                    g.setFill(Color.web(gradColors[seg]));
                    g.fillRect(barX + segStart, y + 76, drawn, 14);
                }
            }
            // Pulse shimmer when energy >= 70
            if (energy >= 70 && !feedback.audio.reducedMotion) {
                double shimmerAlpha = 0.18 + 0.12 * Math.sin(now / 200_000_000.0);
                g.setFill(Color.web("#ffffff", shimmerAlpha));
                g.fillRect(barX, y + 76, barW * energyRatio, 14);
            }
        }
        g.setFont(Font.font(bodyFont, FontWeight.NORMAL, 11));
        g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        g.setFill(C_INK);
        g.fillText(energy + " / 100", barX + barW + 7, y + 87);
    }

    private void drawBoard(
            GraphicsContext g,
            Map<String, Object> p,
            double x,
            double y,
            double w,
            double h,
            boolean own,
            long now) {
        g.save();
        double contentW = Math.max(1, w - 32), contentH = Math.max(1, h - 32);
        g.translate(x + 16, y + 16);
        g.scale(contentW / 800, contentH / 600);
        g.beginPath();
        g.rect(0, 0, 800, 600);
        g.clip();
        int id = integer(p, "id");
        CatchEffect hit =
                feedback.effects.stream()
                        .filter(e -> e.playerId == id && !e.kind.startsWith("CAST"))
                        .reduce((a, b) -> b)
                        .orElse(null);
        double age = hit == null ? 10 : (now - hit.born) / 1e9;
        if (!feedback.audio.reducedMotion && hit != null && hit.delta < 0 && age < .18)
            g.translate(Math.sin(age * 110) * (hit.delta == -10 ? 4 : 2), 0);
        orchard(g, 800, 600, own, false);
        Map<String, Object> old = player(feedback.previousState, id);
        double blend = Math.min(1, Math.max(0, (now - feedback.stateReceived) / 50_000_000.0));
        Map<Integer, Map<String, Object>> oldDrops = new HashMap<>();
        for (Map<String, Object> d : maps(old.get("objects"))) oldDrops.put(integer(d, "id"), d);
        for (Map<String, Object> object : maps(p.get("objects"))) {
            double ox = number(object, "x"), oy = number(object, "y");
            Map<String, Object> prev = oldDrops.get(integer(object, "id"));
            if (prev != null) oy = number(prev, "y") + (oy - number(prev, "y")) * blend;
            // Server positions keep their full 800x600 mapping; art stays square.
            g.save();
            g.translate(ox, oy);
            g.scale((contentH / 600) / (contentW / 800), 1);
            sprite(g, str(object, "kind", "APPLE"), 0, 0, 3.2);
            g.restore();
        }
        double bx = p.containsKey("x") ? number(p, "x") : 400;
        if (old.containsKey("x")) bx = number(old, "x") + (bx - number(old, "x")) * blend;
        double bounce =
                !feedback.audio.reducedMotion && hit != null && hit.delta > 0 && age < .22
                        ? -Math.sin(age / .22 * Math.PI) * 7
                        : 0;
        double basketWidth =
                p.containsKey("basketWidth")
                        ? number(p, "basketWidth")
                        : number(p, "magnet") > 0 ? 140 : 100;
        g.save();
        g.translate(bx, 535 + bounce);
        g.scale(basketWidth / 100, (contentW / 800) / (contentH / 600));
        if (number(p, "magnet") > 0) {
            g.setStroke(Color.web("#80e4d2", .85));
            g.setLineWidth(2);
            g.strokeOval(-58, -33, 116, 80);
        }
        basket(g, 0, 0, own, number(p, "freeze") > 0);
        g.restore();
        if (number(p, "freeze") > 0) {
            g.setFill(Color.web("#fff4d9", .95));
            g.fillRect(268, 13, 264, 38);
            drawIcon(g, "FREEZE", bx - basketWidth / 2 - 14, 530, 24);
            canvasText(
                    g,
                    "GIỎ CHẬM " + String.format("%.1fs", number(p, "freeze")),
                    400,
                    42,
                    22,
                    C_CYAN,
                    true);
        }
        if (number(p, "magnet") > 0) {
            g.setFill(Color.web("#fff4d9", .95));
            g.fillRect(235, 43, 330, 38);
            canvasText(
                    g,
                    "GIỎ NAM CHÂM " + String.format("%.1fs", number(p, "magnet")),
                    400,
                    69,
                    22,
                    C_LIME,
                    true);
        }
        if (p.containsKey("connected") && !bool(p, "connected"))
            canvasText(g, "ĐANG KẾT NỐI LẠI…", 400, 100, 15, C_CORAL, true);
        if (number(p, "bombWarning") > 0) {
            double warning = number(p, "bombX");
            g.setFill(Color.web("#b44735", .22));
            g.fillRect(warning - 26, 0, 52, 540);
            for (int yy = 0; yy < 540; yy += 32) {
                g.setFill(Color.web("#fff0bb", .6));
                g.fillRect(warning - 26, yy, 5, 16);
                g.fillRect(warning + 21, yy + 16, 5, 16);
            }
            canvasText(g, "!", warning, 63, 36, C_CORAL, true);
        }
        for (CatchEffect fx : feedback.effects) if (fx.playerId == id) drawEffect(g, fx, now);
        g.restore();
        // Draw the frame after undoing board scaling, keeping wood and leaves crisp.
        g.save();
        g.translate(x, y);
        woodFrame(g, w, h);
        g.restore();
    }

    public void drawBackdrop(Canvas backdrop, long now) {
        GraphicsContext g = backdrop.getGraphicsContext2D();
        double w = backdrop.getWidth(), h = backdrop.getHeight();
        if (w == backdropPaintWidth && h == backdropPaintHeight) return;
        backdropPaintWidth = w;
        backdropPaintHeight = h;
        g.setImageSmoothing(false);
        g.setFill(Color.web("#8f4c28"));
        g.fillRect(0, 0, w, h);
        if (GameArt.frameImage() != null) {
            double sw = GameArt.frameImage().getWidth(), sh = GameArt.frameImage().getHeight();
            double tileW = 900, rowH = 46;
            for (int row = 0; row <= h / rowH; row++) {
                for (double xx = -(row % 2) * tileW / 2; xx < w; xx += tileW)
                    g.drawImage(
                            GameArt.frameImage(),
                            sw * .25,
                            sh * .065,
                            sw * .5,
                            sh * .025,
                            xx,
                            row * rowH,
                            tileW,
                            rowH);
                g.setFill(Color.web("#4f2818", row % 2 == 0 ? .16 : .25));
                g.fillRect(0, row * rowH, w, rowH);
                g.setFill(Color.web("#5c2d17", .65));
                g.fillRect(0, row * rowH, w, 2);
            }
            if (GameArt.orchardBackground() != null) {
                GameArt.drawCover(g, GameArt.orchardBackground(), 0, 0, w, 100, .04);
                g.drawImage(
                        GameArt.frameImage(),
                        sw * .25,
                        sh * .052,
                        sw * .5,
                        sh * .065,
                        0,
                        94,
                        w,
                        14);
            }
            return;
        }
        // The static plank backdrop is repainted only when the window changes size.
        for (int row = 0; row < h / 46 + 1; row++) {
            double yy = row * 46;
            g.setFill(Color.web(row % 2 == 0 ? "#a65d33" : "#98512c"));
            g.fillRect(0, yy + 2, w, 42);
            g.setFill(Color.web("#bd7543", .62));
            g.fillRect(0, yy + 4, w, 3);
            g.setFill(Color.web("#653116", .65));
            g.fillRect(0, yy + 42, w, 4);
            for (int col = 0; col < w / 140 + 1; col++) {
                double xx = col * 140 + (row % 2) * 67;
                g.setFill(Color.web("#773b1f", .55));
                g.fillRect(xx, yy + 15 + col % 3 * 4, 75, 2);
                g.fillRect(xx + 18, yy + 31, 55, 2);
                if (col % 3 == row % 3) g.fillRect(xx + 106, yy + 2, 3, 40);
            }
        }
    }

    public void drawIllustration(Canvas illustration, long now) {
        GraphicsContext g = illustration.getGraphicsContext2D();
        double w = illustration.getWidth(), h = illustration.getHeight();
        if (w < 1 || h < 1) return;
        g.clearRect(0, 0, w, h);
        // The frame artwork has transparent outer margins; keep scenery inside its wood rails.
        double inset = Math.min(16, Math.min(w, h) / 4);
        g.save();
        g.beginPath();
        g.rect(inset, inset, w - inset * 2, h - inset * 2);
        g.clip();
        if (GameArt.orchardBackground() != null)
            GameArt.drawCover(
                    g, GameArt.orchardBackground(), inset, inset, w - inset * 2, h - inset * 2, 1);
        else {
            g.save();
            g.translate(inset, inset);
            orchard(g, w - inset * 2, h - inset * 2, true, true);
            g.restore();
        }
        double t = feedback.audio.reducedMotion ? 0 : (now - startNanos) / 1e9;
        double scale = Math.min(1.3, Math.min((w - 56) / 360, (h - 58) / 115));
        if (model.screen.equals("home")) scale *= 0.75;
        g.save();
        g.translate(w / 2, h - 30 - 43 * scale);
        g.scale(scale, scale);
        basket(g, 0, 0, true, false);
        g.restore();
        for (int i = 0; i < 3; i++) {
            double xx = w * (.32 + i * .18), yy = h * (.32 + .07 * Math.sin(t * 1.1 + i * 2));
            sprite(
                    g,
                    new String[] {"APPLE", "BANANA", "GRAPE"}[i],
                    xx,
                    yy,
                    model.screen.equals("home") ? Math.min(3, h / 70) : 3);
        }
        g.restore();
        woodFrame(g, w, h);
        // if (model.queued) {
        //     canvasText(
        //             g,
        //             "ĐANG TÌM ĐỐI THỦ" + ".".repeat(1 + (int) (t * 2) % 3),
        //             w / 2,
        //             h * .64,
        //             15,
        //             C_INK,
        //             true);
        // }
    }

    private void drawEffect(GraphicsContext g, CatchEffect fx, long now) {
        double t = Math.max(0, (now - fx.born) / 1e9);
        if (t > .75) return;
        double opacity = Math.max(0, 1 - t / .75);
        g.save();
        // Fade individual paints, never the shared canvas or the next player's board.
        if (fx.kind.startsWith("CAST")) {
            drawIcon(
                    g,
                    fx.kind.equals("CAST1")
                            ? "BOMB"
                            : fx.kind.equals("CAST2") ? "FREEZE" : "MAGNET",
                    fx.x,
                    fx.kind.equals("CAST3") ? 490 : 60,
                    36);
        } else {
            Color color =
                    fx.delta == -10
                            ? C_CORAL
                            : fx.delta < 0
                                    ? Color.web("#9b623d")
                                    : fx.kind.equals("GRAPE")
                                            ? Color.web("#8062a3")
                                            : Color.web("#f5d078");
            if (fx.delta == -10 && t < .28) {
                int frame = Math.min(4, (int) (t / .055));
                double radius = feedback.audio.reducedMotion ? 13 : 8 + frame * 6;
                g.setFill(Color.web(frame < 2 ? "#fff1ae" : "#e88d3e", opacity));
                g.fillRect(fx.x - radius, fx.y - 15 - radius / 2, radius * 2, radius);
                g.fillRect(fx.x - radius / 2, fx.y - 15 - radius, radius, radius * 2);
            }
            if (!feedback.audio.reducedMotion) {
                int count = fx.delta == -10 ? 16 : 9;
                for (int i = 0; i < count; i++) {
                    double angle = (i * Math.PI * 2 / count) + (fx.x * .013),
                            speed = fx.delta == -10 ? 130 : 90;
                    double xx = fx.x + Math.cos(angle) * speed * t,
                            yy = fx.y - 10 + Math.sin(angle) * speed * t + 110 * t * t;
                    Color particle = fx.delta == -10 && i % 3 == 0 ? Color.web("#53604b") : color;
                    g.setFill(particle.deriveColor(0, 1, 1, opacity));
                    double size = fx.delta == -10 ? 9 * (1 - t) : 4;
                    g.fillRect(Math.rint(xx), Math.rint(yy), size, size);
                }
            }
            canvasText(
                    g,
                    (fx.delta > 0 ? "+" : "") + fx.delta,
                    fx.x,
                    fx.y - 37 - (feedback.audio.reducedMotion ? 0 : t * 52),
                    23,
                    (fx.delta > 0 ? C_LIME : C_CORAL).deriveColor(0, 1, 1, opacity),
                    true);
            if (fx.delta > 10)
                canvasText(
                        g,
                        "COMBO +20",
                        fx.x,
                        fx.y - 65 - (feedback.audio.reducedMotion ? 0 : t * 45),
                        14,
                        C_LIME.deriveColor(0, 1, 1, opacity),
                        true);
        }
        g.restore();
    }
}

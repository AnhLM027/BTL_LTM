package Client.fx;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Client.ui.AssetPaths;
import Server.network.ProtocolMessage;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** JavaFX fruit gameplay. Coordinates remain in the server's 800x600 space. */
public final class FxRunGameView extends BorderPane implements TcpGameClient.MessageListener {
    private static final double W = 800, H = 600, BASKET_Y = 474, BASKET_W = 110, BASKET_H = 58;
    private static final long CLASSIC_PHASE_1_END = 22_000, CLASSIC_PHASE_2_START = 20_000;
    private static final long CLASSIC_PHASE_2_END = 41_400, CLASSIC_PHASE_3_START = 40_000;
    private final FxScreenNavigator navigator;
    private final TcpGameClient transport;
    private final long playerId;
    private final Canvas canvas = new Canvas(W, H);
    private final Label status = new Label("Đang nhận cấu hình trận...");
    private final Map<Integer, FruitDef> fruits = new HashMap<>();
    private final List<Spawn> scheduled = new ArrayList<>();
    private final List<Spawn> active = new ArrayList<>();
    private final Set<Long> submitted = new HashSet<>();
    private final List<Integer> targets = new ArrayList<>();
    private final AnimationTimer loop;
    private Image basketImage;
    private final Image bombImage = FxAssets.load("bombs/bomb.png");
    private final Image[] phaseBackgrounds = {
            FxAssets.load("ui/game-play/backgrounds/room1.png"),
            FxAssets.load("ui/game-play/backgrounds/room2.png"),
            FxAssets.load("ui/game-play/backgrounds/room3.png")
    };
    private final Image playerPanel = FxAssets.load("ui/game-play/panels/player_panel_left.png");
    private final Image opponentPanel = FxAssets.load("ui/game-play/panels/player_panel_right.png");
    private final Image timerPanel = FxAssets.load("ui/game-play/panels/timer_panel.png");
    private final Image playerAvatar = FxAssets.load("ui/game-play/avatars/avatar_player.png");
    private final Image opponentAvatar = FxAssets.load("ui/game-play/avatars/avatar_opponent_sample.png");
    private final Image keyLeft = FxAssets.load("ui/game-play/buttons/key_left.png");
    private final Image keyRight = FxAssets.load("ui/game-play/buttons/key_right.png");
    private Image background;
    private long matchId = -1, startedAt = -1;
    private int duration = 60, basketId = -1, basketX = 345, combo;
    private int phase = 1, playerScore, opponentScoreValue;
    private String playerName = "PLAYER", opponentName = "OPPONENT";
    /** Prevents the local timer from flooding the server with timeout requests. */
    private boolean order, ended, resultRequested;

    public FxRunGameView(FxScreenNavigator navigator, ProtocolMessage prepare) {
        this(navigator, GameClientSession.instance().client(), GameClientSession.instance().identity().playerId(), prepare);
    }

    private FxRunGameView(FxScreenNavigator navigator, TcpGameClient transport, long playerId, ProtocolMessage prepare) {
        this.navigator = navigator;
        this.transport = transport;
        this.playerId = playerId;
        getStyleClass().add("fx-game-view");
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
        StackPane arena = new StackPane(canvas);
        arena.setAlignment(Pos.CENTER);
        arena.setMinSize(0, 0);
        arena.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        canvas.widthProperty().bind(arena.widthProperty());
        canvas.heightProperty().bind(arena.heightProperty());
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(event -> move(event.getCode()));
        setCenter(arena);
        status.getStyleClass().add("game-status");
        transport.addListener(this);
        handle(prepare);
        loop = new AnimationTimer() { @Override public void handle(long now) { tick(); } };
        loop.start();
        Platform.runLater(canvas::requestFocus);
    }

    static FxRunGameView preview(FxScreenNavigator navigator) {
        TcpGameClient previewClient = new TcpGameClient();
        ProtocolMessage prepare = new ProtocolMessage("MATCH_PREPARE", Map.of(
                "matchId", "999", "modeCode", "CLASSIC", "durationSeconds", "60"));
        FxRunGameView view = new FxRunGameView(navigator, previewClient, 1, prepare);
        view.handle(new ProtocolMessage("FRUIT_CONFIG", Map.of("fruitId", "1", "fruitName", "Cam", "assetPath", "fruits/orange.png")));
        view.handle(new ProtocolMessage("BASKET_CONFIG", Map.of("basketId", "1", "basketName", "Giỏ chung", "assetPath", "baskets/basket.png")));
        view.handle(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", "999", "fruitInstanceId", "1", "fruitId", "1", "isBomb", "false", "spawnOffsetMs", "0", "xPosition", "260", "fallDurationMs", "4000")));
        view.handle(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", "999", "fruitInstanceId", "2", "isBomb", "true", "spawnOffsetMs", "0", "xPosition", "480", "fallDurationMs", "4000")));
        view.handle(new ProtocolMessage("MATCH_START", Map.of("matchId", "999", "startedAt", LocalDateTime.now().toString(), "durationSeconds", "60")));
        return view;
    }

    @Override public void onMessage(ProtocolMessage message) { Platform.runLater(() -> handle(message)); }

    private void handle(ProtocolMessage m) {
        try {
            switch (m.type()) {
                case "MATCH_PREPARE" -> prepare(m);
                case "FRUIT_CONFIG" -> receiveFruitConfig(m);
                case "BASKET_CONFIG" -> receiveBasketConfig(m);
                case "FRUIT_SPAWN" -> receiveSpawn(m);
                case "MATCH_PREPARE_END" -> ready(m);
                case "MATCH_START" -> start(m);
                case "SCORE_UPDATE" -> updateScore(m);
                case "CATCH_ACK" -> { /* Server acknowledgement; score is rendered from SCORE_UPDATE. */ }
                case "MATCH_RESULT" -> { ended = true; navigator.showResult(m); }
                case "MATCH_CANCELLED" -> { ended = true; navigator.showLobby(); }
                case "OPPONENT_DISCONNECTED" -> status.setText(
                        "Đối thủ mất kết nối · còn " + m.fields().getOrDefault("graceSeconds", "30") + " giây");
                case "OPPONENT_RECONNECTED" -> status.setText("Đối thủ đã kết nối lại");
                case "ERROR" -> status.setText(m.fields().getOrDefault("message", "Catch bị từ chối"));
                default -> { }
            }
        } catch (Exception e) { status.setText("Lỗi dữ liệu trận đấu: " + e.getMessage()); }
    }

    private void receiveFruitConfig(ProtocolMessage message) {
        int fruitId = intValue(message, "fruitId");
        fruits.put(fruitId, new FruitDef(
                imagePath(message.fields().getOrDefault("assetPath", ""))));
    }

    private void receiveBasketConfig(ProtocolMessage message) {
        basketId = intValue(message, "basketId");
        basketImage = FxAssets.load(imagePath(message.fields().getOrDefault("assetPath", "")));
    }

    private void receiveSpawn(ProtocolMessage message) {
        if (longValue(message, "matchId") != matchId) return;
        Integer fruitId = message.fields().containsKey("fruitId")
                ? intValue(message, "fruitId") : null;
        scheduled.add(new Spawn(
                longValue(message, "fruitInstanceId"), fruitId,
                Boolean.parseBoolean(message.fields().getOrDefault("isBomb", "false")),
                longValue(message, "spawnOffsetMs"), intValue(message, "xPosition"),
                intValue(message, "fallDurationMs")));
    }

    private void prepare(ProtocolMessage m) {
        matchId = longValue(m, "matchId"); duration = intValue(m, "durationSeconds");
        order = "ORDER".equals(m.fields().getOrDefault("modeCode", "CLASSIC"));
        long hostPlayerId = longValue(m, "hostPlayerId");
        long guestPlayerId = longValue(m, "guestPlayerId");
        String hostName = m.fields().getOrDefault("hostPlayerName", "PLAYER");
        String guestName = m.fields().getOrDefault("guestPlayerName", "OPPONENT");
        if (playerId == hostPlayerId) {
            playerName = hostName;
            opponentName = guestName;
        } else if (playerId == guestPlayerId) {
            playerName = guestName;
            opponentName = hostName;
        }
        targets.clear(); fruits.clear(); scheduled.clear(); active.clear(); submitted.clear(); combo = 0; startedAt = -1; ended = false; resultRequested = false;
        for (int i = 1; i <= 3; i++) if (m.fields().containsKey("targetFruitId_" + i)) targets.add(intValue(m, "targetFruitId_" + i));
        phase = 1;
        background = phaseBackgrounds[0] != null
                ? phaseBackgrounds[0]
                : FxAssets.load("backgrounds/game-orchard-background.png");
        playerScore = 0;
        opponentScoreValue = 0;
        status.setText(order ? "Đơn hàng: hứng đúng 3 loại quả" : "Classic: hứng quả, né bom");
    }

    private void ready(ProtocolMessage m) throws IOException {
        if (longValue(m, "matchId") != matchId) return;
        transport.send(new ProtocolMessage("MATCH_READY", Map.of("matchId", Long.toString(matchId))));
        status.setText("Đã sẵn sàng, đang chờ đối thủ...");
    }

    private void start(ProtocolMessage m) {
        if (longValue(m, "matchId") != matchId) return;
        String value = m.fields().get("startedAt");
        try { startedAt = LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
        catch (Exception ignored) { startedAt = System.currentTimeMillis(); }
        status.setText("Trận đấu đang diễn ra");
    }

    private void updateScore(ProtocolMessage m) {
        if (longValue(m, "matchId") != matchId) return;
        long id = longValue(m, "playerId");
        try {
            int value = Integer.parseInt(m.fields().getOrDefault("score", "0"));
            if (id == playerId) playerScore = value;
            else opponentScoreValue = value;
        } catch (NumberFormatException ignored) { }
    }

    private void move(KeyCode key) {
        if (startedAt < 0 || ended) return;
        if (key == KeyCode.LEFT) basketX = Math.max(0, basketX - 24);
        if (key == KeyCode.RIGHT) basketX = Math.min((int) W - (int) BASKET_W, basketX + 24);
    }

    private void tick() {
        long elapsed = startedAt < 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt);
        if (startedAt >= 0) {
            long remaining = Math.max(0, duration * 1000L - elapsed);
            if (remaining == 0) {
                status.setText("Hết giờ · đang chốt kết quả từ server...");
                if (!resultRequested) {
                    resultRequested = true;
                    try {
                        transport.send(new ProtocolMessage("MATCH_RESULT_REQUEST",
                                Map.of("matchId", Long.toString(matchId))));
                    } catch (IOException ignored) {
                        status.setText("Mất kết nối · chưa nhận được kết quả trận đấu");
                    }
                }
                draw();
                return;
            }
            scheduled.removeIf(s -> {
                if (effectiveSpawnOffset(s) <= elapsed) {
                    active.add(s);
                    return true;
                }
                return false;
            });
            active.removeIf(s -> elapsed - s.offset > s.fallMs);
        }
        updatePhaseBackground(elapsed);
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double scale = Math.min(canvas.getWidth() / W, canvas.getHeight() / H);
        double offsetX = (canvas.getWidth() - W * scale) / 2.0;
        double offsetY = (canvas.getHeight() - H * scale) / 2.0;
        g.save();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.translate(offsetX, offsetY);
        g.scale(scale, scale);
        g.clearRect(0, 0, W, H);
        long elapsed = startedAt < 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt);
        drawBackground(g);
        drawAssetHud(g, elapsed);
        if (order) drawOrderTargets(g);
        drawComboBar(g);
        List<Spawn> collisions = new ArrayList<>();
        for (Spawn s : active) {
            int y = fallingY(s, elapsed);
            if (s.bomb) drawBomb(g, s.x, y);
            else drawFruit(g, s, y);
            if (y + 40 >= BASKET_Y && s.x + 40 >= basketX && s.x <= basketX + BASKET_W) collisions.add(s);
        }
        drawBasket(g);
        if (!order) drawClassicPhaseOverlay(g, elapsed);
        g.restore();
        collisions.forEach(this::catchFruit);
    }

    private int fallingY(Spawn spawn, long elapsed) {
        return 80 + (int) Math.min(425,
                (elapsed - effectiveSpawnOffset(spawn)) * 425L / Math.max(1, spawn.fallMs));
    }

    /**
     * Keep the stage transition notice separate from the next stage's first
     * falling object. The server's spawn schedule remains authoritative, but
     * the client delays each later stage by the notice duration so no fruit is
     * visible underneath the announcement.
     */
    private long effectiveSpawnOffset(Spawn spawn) {
        if (order) return spawn.offset;
        if (spawn.offset >= CLASSIC_PHASE_3_START) return spawn.offset + 1_800;
        if (spawn.offset >= CLASSIC_PHASE_2_START) return spawn.offset + 1_800;
        return spawn.offset;
    }

    private void drawFruit(GraphicsContext g, Spawn spawn, int y) {
        FruitDef fruit = fruits.get(spawn.fruitId);
        Image image = fruit == null ? null : FxAssets.load(fruit.asset);
        if (image != null && image.getProgress() >= 1.0) {
            g.drawImage(image, spawn.x, y, 40, 40);
        } else {
            g.setFill(Color.ORANGE);
            g.fillOval(spawn.x, y, 40, 40);
        }
    }

    private void drawBasket(GraphicsContext g) {
        if (basketImage != null && basketImage.getProgress() >= 1.0) {
            g.drawImage(basketImage, basketX, BASKET_Y, BASKET_W, BASKET_H);
        } else {
            g.setFill(Color.SANDYBROWN);
            g.fillRoundRect(basketX, BASKET_Y, BASKET_W, BASKET_H, 12, 12);
        }
    }

    private void drawAssetHud(GraphicsContext g, long elapsed) {
        drawImage(g, playerPanel, 10, 8, 296, 98);
        drawImage(g, timerPanel, 320, 8, 160, 107);
        drawImage(g, opponentPanel, 494, 8, 296, 98);
        drawImage(g, playerAvatar, 20, 20, 65, 74);
        drawImage(g, opponentAvatar, 710, 20, 65, 74);

        g.setFill(Color.web("#17452f"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 15));
        g.fillText(playerName, 92, 38);
        g.setFill(Color.web("#a64f1d"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 22));
        g.fillText("SCORE " + playerScore, 92, 76);
        g.setFill(Color.web("#17452f"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 15));
        g.fillText(opponentName, 505, 38);
        g.setFill(Color.web("#a64f1d"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 22));
        g.fillText("SCORE " + opponentScoreValue, 505, 76);

        long remaining = startedAt < 0 ? duration * 1000L : Math.max(0, duration * 1000L - elapsed);
        g.setFill(Color.web("#ffe36a"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 29));
        g.fillText(String.format("%02d:%02d", remaining / 60_000, (remaining / 1_000) % 60), 360, 61);
        g.setFill(Color.web("#4f2d18"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 12));
        g.fillText(order ? "ORDER" : "CLASSIC", 375, 95);

        g.setFill(Color.rgb(255, 248, 222, .94));
        g.fillRoundRect(288, 548, 224, 42, 10, 10);
        g.setStroke(Color.web("#7d3f1d"));
        g.strokeRoundRect(288, 548, 224, 42, 10, 10);
        drawImage(g, keyLeft, 310, 553, 30, 30);
        drawImage(g, keyRight, 348, 553, 30, 30);
        g.setFill(Color.web("#4f2d18"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 13));
        g.fillText("MOVE", 394, 575);
        g.setFont(Font.font(FxAssets.FONT_FAMILY, 11));
        g.fillText(status.getText(), 18, 530);
    }

    private void drawOrderTargets(GraphicsContext g) {
        g.setFill(Color.rgb(255, 252, 220, .92));
        g.fillRoundRect(560, 112, 225, 60, 12, 12);
        g.setStroke(Color.web("#165237"));
        g.strokeRoundRect(560, 112, 225, 60, 12, 12);
        g.setFill(Color.web("#165237"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 13));
        g.fillText("ORDER", 570, 132);
        for (int index = 0; index < targets.size(); index++) {
            FruitDef fruit = fruits.get(targets.get(index));
            Image image = fruit == null ? null : FxAssets.load(fruit.asset);
            int x = 640 + index * 45;
            if (image != null && image.getProgress() >= 1.0) g.drawImage(image, x, 122, 34, 34);
            else { g.setFill(Color.DARKGREEN); g.fillText("?", x + 12, 145); }
        }
    }

    private void drawImage(GraphicsContext g, Image image, double x, double y, double width, double height) {
        if (image != null && image.getProgress() >= 1.0) g.drawImage(image, x, y, width, height);
    }

    /** Draws the 1080x760 phase artwork without distorting its aspect ratio. */
    private void drawBackground(GraphicsContext g) {
        if (background == null || background.getProgress() < 1.0) return;
        double scale = Math.max(W / background.getWidth(), H / background.getHeight());
        double width = background.getWidth() * scale;
        double height = background.getHeight() * scale;
        double x = (W - width) / 2.0;
        double y = (H - height) / 2.0;
        g.drawImage(background, x, y, width, height);
    }

    private void updatePhaseBackground(long elapsed) {
        // ORDER has one continuous sequence; the three visual phases belong
        // to the staged CLASSIC progression only.
        if (order) {
            phase = 1;
            Image first = phaseBackgrounds[0];
            if (first != null) background = first;
            return;
        }
        if (phase == 1 && elapsed >= CLASSIC_PHASE_2_START) phase = 2;
        if (phase == 2 && elapsed >= CLASSIC_PHASE_3_START) phase = 3;
        Image next = phaseBackgrounds[Math.max(0, Math.min(phase - 1, phaseBackgrounds.length - 1))];
        if (next != null) background = next;
    }

    private void drawComboBar(GraphicsContext g) {
        g.setFill(Color.web("#165237"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 12));
        g.fillText("Combo", 670, 560);
        for (int index = 0; index < 5; index++) {
            g.setFill(index < combo ? Color.GOLD : Color.rgb(170, 170, 170, .65));
            g.fillOval(720 + index * 14, 550, 10, 10);
        }
    }

    private void drawClassicPhaseOverlay(GraphicsContext g, long elapsed) {
        String title = null;
        String subtitle = null;
        if (phaseAnnouncementReady(elapsed, CLASSIC_PHASE_1_END, CLASSIC_PHASE_2_START)) {
            title = "GIAI ĐOẠN 2"; subtitle = "TỐC ĐỘ TĂNG!";
        } else if (phaseAnnouncementReady(elapsed, CLASSIC_PHASE_2_END, CLASSIC_PHASE_3_START)) {
            title = "GIAI ĐOẠN 3"; subtitle = "TỐC ĐỘ TỐI ĐA!";
        }
        if (elapsed < 1_100) { title = "GIAI ĐOẠN 1"; subtitle = "BẮT ĐẦU!"; }
        if (title == null) return;
        g.setFill(Color.rgb(0, 45, 28, .48));
        g.fillRect(0, 0, W, H);
        g.setFill(Color.rgb(255, 253, 229, .93));
        g.fillRoundRect(190, 205, 420, 150, 22, 22);
        g.setStroke(Color.web("#165237"));
        g.setLineWidth(3);
        g.strokeRoundRect(190, 205, 420, 150, 22, 22);
        g.setFill(Color.web("#165237"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 28));
        g.fillText(title, 300, 265);
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 20));
        g.fillText(subtitle, 327, 300);
    }

    /** Shows the speed-up notice before the next phase's objects are released. */
    private boolean phaseAnnouncementReady(long elapsed, long previousEnd, long nextStart) {
        return elapsed >= nextStart && elapsed < nextStart + 1_800;
    }

    private void drawBomb(GraphicsContext g, int x, int y) {
        if (bombImage != null && bombImage.getProgress() >= 1.0) {
            g.drawImage(bombImage, x, y, 40, 40);
            return;
        }
        g.setFill(Color.rgb(45, 45, 50));
        g.fillOval(x, y, 40, 40);
        g.setStroke(Color.BLACK);
        g.strokeOval(x, y, 40, 40);
        g.setStroke(Color.ORANGE);
        g.setLineWidth(3);
        g.strokeLine(x + 28, y + 8, x + 39, y - 4);
        g.setFill(Color.WHITE);
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 18));
        g.fillText("!", x + 17, y + 27);
    }
    private void catchFruit(Spawn s) {
        if (startedAt < 0 || System.currentTimeMillis() - startedAt >= duration * 1000L) return;
        if (!submitted.add(s.id) || matchId < 0 || basketId <= 0) return;
        active.remove(s);
        try { transport.send(new ProtocolMessage("CATCH_EVENT", Map.of("matchId", Long.toString(matchId), "fruitInstanceId", Long.toString(s.id), "basketId", Integer.toString(basketId)))); }
        catch (IOException e) { status.setText("Không gửi được CatchEvent"); }
    }
    private String imagePath(String path) { return AssetPaths.normalize(path); }
    private long longValue(ProtocolMessage m,String k){return Long.parseLong(m.fields().getOrDefault(k,"-1"));}
    private int intValue(ProtocolMessage m,String k){return Integer.parseInt(m.fields().getOrDefault(k,"0"));}
    @Override public void onConnectionError(Exception e) { Platform.runLater(() -> status.setText("Mất kết nối máy chủ")); }
    public void dispose(){ loop.stop(); transport.removeListener(this); }
    private record FruitDef(String asset){}
    private record Spawn(long id,Integer fruitId,boolean bomb,long offset,int x,int fallMs){}
}

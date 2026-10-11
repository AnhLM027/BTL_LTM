package Client.fx;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** JavaFX fruit gameplay. Coordinates remain in the server's 800x600 space. */
public final class FxRunGameView extends BorderPane implements TcpGameClient.MessageListener {
    private static final double W = 800, H = 600, BASKET_Y = 505, BASKET_W = 120;
    private static final long CLASSIC_PHASE_1_END = 8_800, CLASSIC_PHASE_2_START = 11_000;
    private static final long CLASSIC_PHASE_2_END = 20_000, CLASSIC_PHASE_3_START = 22_200;
    private final FxScreenNavigator navigator;
    private final TcpGameClient transport;
    private final long playerId;
    private final Canvas canvas = new Canvas(W, H);
    private final Label opponentScore = new Label("Đối thủ: 0");
    private final Label objective = new Label("Đang nhận mục tiêu trận...");
    private final Label score = new Label("Bạn: 0    Đối thủ: 0");
    private final Label timer = new Label("Đang chuẩn bị...");
    private final Label status = new Label("Đang nhận cấu hình trận...");
    private final Map<Integer, FruitDef> fruits = new HashMap<>();
    private final List<Spawn> scheduled = new ArrayList<>();
    private final List<Spawn> active = new ArrayList<>();
    private final Set<Long> submitted = new HashSet<>();
    private final List<Integer> targets = new ArrayList<>();
    private final AnimationTimer loop;
    private Image basketImage;
    private final Image bombImage = FxAssets.load("bombs/bomb.png");
    private Image background;
    private long matchId = -1, startedAt = -1;
    private int duration = 30, basketId = -1, basketX = 340, combo;
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
        score.setText("Điểm của bạn: 0");
        setTop(topBar());
        StackPane arena = new StackPane(canvas);
        arena.setAlignment(Pos.CENTER);
        arena.setMinSize(0, 0);
        arena.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        canvas.widthProperty().bind(arena.widthProperty());
        canvas.heightProperty().bind(arena.heightProperty());
        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(event -> move(event.getCode()));
        setCenter(arena);
        objective.getStyleClass().add("game-objective");
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
                "matchId", "999", "modeCode", "CLASSIC", "durationSeconds", "30"));
        FxRunGameView view = new FxRunGameView(navigator, previewClient, 1, prepare);
        view.handle(new ProtocolMessage("FRUIT_CONFIG", Map.of("fruitId", "1", "fruitName", "Cam", "assetPath", "fruits/orange.png")));
        view.handle(new ProtocolMessage("BASKET_CONFIG", Map.of("basketId", "1", "basketName", "Giỏ chung", "assetPath", "baskets/standard-basket.png")));
        view.handle(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", "999", "fruitInstanceId", "1", "fruitId", "1", "isBomb", "false", "spawnOffsetMs", "0", "xPosition", "260", "fallDurationMs", "4000")));
        view.handle(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", "999", "fruitInstanceId", "2", "isBomb", "true", "spawnOffsetMs", "0", "xPosition", "480", "fallDurationMs", "4000")));
        view.handle(new ProtocolMessage("MATCH_START", Map.of("matchId", "999", "startedAt", LocalDateTime.now().toString(), "durationSeconds", "30")));
        return view;
    }

    private VBox topBar() {
        BorderPane bar = new BorderPane();
        score.getStyleClass().add("game-score");
        opponentScore.getStyleClass().add("game-opponent-score");
        timer.getStyleClass().add("game-timer");
        bar.setLeft(score);
        bar.setCenter(timer);
        bar.setRight(opponentScore);
        BorderPane.setAlignment(score, Pos.CENTER_LEFT);
        BorderPane.setAlignment(timer, Pos.CENTER);
        BorderPane.setAlignment(opponentScore, Pos.CENTER_RIGHT);
        HBox info = new HBox(18, objective, status);
        info.setAlignment(Pos.CENTER);
        VBox wrapper = new VBox(bar, info);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.getStyleClass().add("game-header");
        return wrapper;
    }

    @Override public void onMessage(ProtocolMessage message) { Platform.runLater(() -> handle(message)); }

    private void handle(ProtocolMessage m) {
        try {
            switch (m.type()) {
                case "MATCH_PREPARE" -> prepare(m);
                case "FRUIT_CONFIG" -> fruits.put(intValue(m, "fruitId"), new FruitDef(
                        intValue(m, "fruitId"), m.fields().getOrDefault("fruitName", "Quả"),
                        imagePath(m.fields().getOrDefault("assetPath", ""))));
                case "BASKET_CONFIG" -> { basketId = intValue(m, "basketId"); basketImage = FxAssets.load(imagePath(m.fields().getOrDefault("assetPath", ""))); }
                case "FRUIT_SPAWN" -> {
                    if (longValue(m, "matchId") == matchId) scheduled.add(new Spawn(longValue(m, "fruitInstanceId"),
                            m.fields().containsKey("fruitId") ? intValue(m, "fruitId") : null,
                            Boolean.parseBoolean(m.fields().getOrDefault("isBomb", "false")),
                            longValue(m, "spawnOffsetMs"), intValue(m, "xPosition"), intValue(m, "fallDurationMs")));
                }
                case "MATCH_PREPARE_END" -> ready(m);
                case "MATCH_START" -> start(m);
                case "SCORE_UPDATE" -> updateScore(m);
                  case "CATCH_ACK" -> status.setText(Boolean.parseBoolean(m.fields().getOrDefault("accepted", "true"))
                          ? "Đã hứng · điểm đã đồng bộ"
                          : "Hứng bị từ chối: " + m.fields().getOrDefault("reason", "đối tượng không còn hợp lệ"));
                case "MATCH_RESULT" -> { ended = true; navigator.showResult(m); }
                  case "MATCH_CANCELLED" -> { ended = true; navigator.showLobby(); }
                case "OPPONENT_DISCONNECTED" -> status.setText("Đối thủ mất kết nối · còn " + m.fields().getOrDefault("graceSeconds", "30") + " giây");
                case "OPPONENT_RECONNECTED" -> status.setText("Đối thủ đã kết nối lại");
                case "ERROR" -> status.setText(m.fields().getOrDefault("message", "Catch bị từ chối"));
                default -> { }
            }
        } catch (Exception e) { status.setText("Lỗi dữ liệu trận đấu: " + e.getMessage()); }
    }

    private void prepare(ProtocolMessage m) {
        matchId = longValue(m, "matchId"); duration = intValue(m, "durationSeconds");
        order = "ORDER".equals(m.fields().getOrDefault("modeCode", "CLASSIC"));
        objective.setText(order ? "Đơn hàng: hứng đúng 3 loại quả mục tiêu" : "CLASSIC: hứng quả, né bom");
        targets.clear(); fruits.clear(); scheduled.clear(); active.clear(); submitted.clear(); combo = 0; startedAt = -1; ended = false; resultRequested = false;
        for (int i = 1; i <= 3; i++) if (m.fields().containsKey("targetFruitId_" + i)) targets.add(intValue(m, "targetFruitId_" + i));
        background = FxAssets.load("backgrounds/game-orchard-background.png");
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
        long id = longValue(m, "playerId"); String value = m.fields().getOrDefault("score", "0");
        if (id == playerId) Platform.runLater(() -> score.setText("Điểm của bạn: " + value));
        else opponentScore.setText("Đối thủ: " + value);
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
            timer.setText("Thời gian: " + ((remaining + 999) / 1000));
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
            scheduled.removeIf(s -> { if (s.offset <= elapsed) { active.add(s); return true; } return false; });
            active.removeIf(s -> elapsed - s.offset > s.fallMs);
        }
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D(); double sx = canvas.getWidth() / W, sy = canvas.getHeight() / H;
        g.save(); g.scale(sx, sy); g.clearRect(0, 0, W, H);
        if (background != null) g.drawImage(background, 0, 0, W, H);
        drawControlsOverlay(g);
        if (order) drawOrderOverlay(g);
        drawComboBar(g);
        long elapsed = startedAt < 0 ? 0 : Math.max(0, System.currentTimeMillis() - startedAt);
        List<Spawn> collisions = new ArrayList<>();
        for (Spawn s : active) {
            int y = 80 + (int) Math.min(425, (elapsed - s.offset) * 425L / Math.max(1, s.fallMs));
            if (s.bomb) drawBomb(g, s.x, y); else { FruitDef f = fruits.get(s.fruitId); Image img = f == null ? null : FxAssets.load(f.asset); if (img != null && img.getProgress() >= 1.0) g.drawImage(img, s.x, y, 40, 40); else { g.setFill(Color.ORANGE); g.fillOval(s.x, y, 40, 40); } if (f != null) { g.setFill(Color.DARKSLATEGRAY); g.setFont(Font.font(FxAssets.FONT_FAMILY, 11)); g.fillText(f.name, s.x - 6, y - 5); } }
            if (y + 40 >= BASKET_Y && s.x + 40 >= basketX && s.x <= basketX + BASKET_W) collisions.add(s);
        }
        if (basketImage != null && basketImage.getProgress() >= 1.0) g.drawImage(basketImage, basketX, BASKET_Y, BASKET_W, 87);
        else { g.setFill(Color.SANDYBROWN); g.fillRoundRect(basketX, BASKET_Y, BASKET_W, 70, 12, 12); }
        g.setFill(Color.DARKGREEN); g.setFont(Font.font(FxAssets.FONT_FAMILY, 13)); g.fillText("\u0042\u1ea1n", basketX + 12, BASKET_Y - 10);
        if (order) { g.setFill(Color.BEIGE); g.fillRoundRect(540, 15, 240, 58, 12, 12); g.setFill(Color.DARKGREEN); g.fillText("Đơn hàng", 555, 35); for (int i=0;i<targets.size();i++){ FruitDef f=fruits.get(targets.get(i)); if(f!=null){Image img=FxAssets.load(f.asset); if(img!=null)g.drawImage(img,600+i*52,40,34,34);}} }
        if (!order) drawClassicPhaseOverlay(g, elapsed);
        g.restore();
        collisions.forEach(this::catchFruit);
    }

    private void drawControlsOverlay(GraphicsContext g) {
        g.setFill(Color.rgb(255, 255, 255, .82));
        g.fillRoundRect(14, 14, 190, 58, 14, 14);
        g.setStroke(Color.web("#165237"));
        g.strokeRoundRect(14, 14, 190, 58, 14, 14);
        g.setFill(Color.web("#165237"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 14));
        g.fillText("Điều khiển", 26, 35);
        g.setFont(Font.font(FxAssets.FONT_FAMILY, 13));
        g.fillText("← / →  Di chuyển giỏ", 26, 56);
    }

    private void drawOrderOverlay(GraphicsContext g) {
        g.setFill(Color.rgb(255, 252, 220, .88));
        g.fillRoundRect(560, 14, 225, 60, 12, 12);
        g.setStroke(Color.web("#165237"));
        g.strokeRoundRect(560, 14, 225, 60, 12, 12);
        g.setFill(Color.web("#165237"));
        g.setFont(Font.font(FxAssets.FONT_FAMILY, javafx.scene.text.FontWeight.BOLD, 13));
        g.fillText("Đơn hàng", 570, 34);
        for (int index = 0; index < targets.size(); index++) {
            FruitDef fruit = fruits.get(targets.get(index));
            Image image = fruit == null ? null : FxAssets.load(fruit.asset);
            int x = 640 + index * 45;
            if (image != null && image.getProgress() >= 1.0) g.drawImage(image, x, 24, 34, 34);
            else { g.setFill(Color.DARKGREEN); g.fillText("?", x + 12, 47); }
        }
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
        long countdown = 0;
        if (phaseBreakReady(elapsed, CLASSIC_PHASE_1_END, CLASSIC_PHASE_2_START)) {
            title = "GIAI ĐOẠN 2"; subtitle = "TỐC ĐỘ TĂNG!";
            countdown = CLASSIC_PHASE_2_START - elapsed;
        } else if (phaseBreakReady(elapsed, CLASSIC_PHASE_2_END, CLASSIC_PHASE_3_START)) {
            title = "GIAI ĐOẠN 3"; subtitle = "TỐC ĐỘ TỐI ĐA!";
            countdown = CLASSIC_PHASE_3_START - elapsed;
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
        if (countdown > 0) {
            g.setFont(Font.font(FxAssets.FONT_FAMILY, 16));
            g.fillText("Bắt đầu sau " + ((countdown + 999) / 1_000) + "...", 335, 330);
        }
    }

    private boolean phaseBreakReady(long elapsed, long previousEnd, long nextStart) {
        if (elapsed < previousEnd || elapsed >= nextStart) return false;
        return active.stream().noneMatch(spawn -> spawn.offset < nextStart && elapsed - spawn.offset <= spawn.fallMs);
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
    private record FruitDef(int id,String name,String asset){}
    private record Spawn(long id,Integer fruitId,boolean bomb,long offset,int x,int fallMs){}
}

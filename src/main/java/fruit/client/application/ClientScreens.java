package fruit.client.application;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import fruit.client.features.accounts.LoginScreen;
import fruit.client.features.accounts.ProfileScreen;
import fruit.client.features.admin.AdminScreen;
import fruit.client.features.gameplay.ClientFeedback;
import fruit.client.features.gameplay.GameRenderer;
import fruit.client.features.gameplay.GameScreen;
import fruit.client.features.gameplay.GameplayNotice;
import fruit.client.features.help.HelpDialog;
import fruit.client.features.history.HistoryScreen;
import fruit.client.features.leaderboard.LeaderboardScreen;
import fruit.client.features.lobby.LobbyScreen;
import fruit.client.features.results.ResultScreen;
import fruit.client.features.settings.AudioSettingsDialog;
import fruit.client.shared.preview.DemoData;
import static fruit.client.shared.ui.GameArt.decorate;
import static fruit.client.shared.ui.GameArt.frameCss;
import static fruit.client.shared.ui.GameArt.loadAssets;
import static fruit.client.shared.ui.GameArt.sprite;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.CSS;
import static fruit.client.shared.ui.UiTheme.C_CORAL;
import static fruit.client.shared.ui.UiTheme.C_MUTED;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.NAVY;
import static fruit.client.shared.ui.UiTheme.PANEL;
import static fruit.client.shared.ui.UiTheme.bodyFont;
import static fruit.client.shared.ui.UiTheme.pixelFont;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Application window, screen routing and keyboard bindings. No socket or database code. */
public final class ClientScreens implements ClientView {
    public final ClientController controller;
    public final ClientState model;
    public final ClientFeedback feedback;
    public final GameRenderer renderer;
    private Map<String, String> parameters;
    private AnimationTimer timer;

    public ClientScreens(ClientController controller) {
        this.controller = controller;
        this.model = controller.model;
        this.feedback = controller.feedback;
        this.renderer = new GameRenderer(model, feedback);
        controller.setView(this);
    }

    public long lastFrame;
    public long frameCount;
    public long metricsStart;
    public Button muteButton;
    public final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    public Stage stage;
    public BorderPane shell;
    public StackPane root;
    public VBox body;
    public HBox top;
    public Label message;
    private HBox noticeBox;
    private Canvas noticeIcon;
    private GameplayNotice.Notice displayedNotice;
    private long lastNoticePaint;
    public Label connection;
    public Button reconnect;
    public Canvas backdrop;
    public Canvas arena;
    public Canvas illustration;
    public final Button[] skillButtons = new Button[3];
    public Label queueLabel;
    public Label skillStatus;
    public Label savedStatus;
    public Button findButton;
    public TextField hostInput;
    public TextField portInput;
    public TextField userInput;
    public PasswordField passInput;
    public int adminTab;

    public void start(Stage window, Map<String, String> parameters) {
        this.parameters = parameters;
        stage = window;
        loadAssets();
        feedback.audio.preload();
        model.host = parameters.getOrDefault("host", "localhost");
        try {
            model.port = Integer.parseInt(parameters.getOrDefault("port", "5555"));
        } catch (NumberFormatException ignored) {
        }
        model.snapshotMode = parameters.containsKey("snapshot");
        backdrop = new Canvas();
        root = new StackPane(backdrop);
        shell = new BorderPane();
        root.getChildren().add(shell);
        backdrop.widthProperty().bind(root.widthProperty());
        backdrop.heightProperty().bind(root.heightProperty());
        root.setStyle("-fx-font-family: '" + bodyFont + "'; -fx-background-color:" + NAVY + ";");
        root.widthProperty()
                .addListener(
                        (o, before, width) ->
                                root.pseudoClassStateChanged(
                                        javafx.css.PseudoClass.getPseudoClass("compact"),
                                        width.doubleValue() < 1180));
        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets()
                .add(
                        "data:text/css;base64,"
                                + Base64.getEncoder()
                                        .encodeToString(
                                                (CSS + frameCss())
                                                        .getBytes(StandardCharsets.UTF_8)));
        scene.addEventFilter(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                e -> {
                    if (e.getCode() == KeyCode.F8) {
                        toggleMute();
                        e.consume();
                        return;
                    }
                    if (!model.screen.equals("game")) return;
                    boolean first = held.add(e.getCode());
                    if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
                        move();
                        e.consume();
                    } else if (first) {
                        int skill =
                                switch (e.getCode()) {
                                    case DIGIT1, NUMPAD1 -> 1;
                                    case DIGIT2, NUMPAD2 -> 2;
                                    case DIGIT3, NUMPAD3 -> 3;
                                    default -> 0;
                                };
                        if (skill > 0) {
                            controller.useSkill(skill);
                            e.consume();
                        }
                    }
                });
        scene.addEventFilter(
                javafx.scene.input.KeyEvent.KEY_RELEASED,
                e -> {
                    held.remove(e.getCode());
                    if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT) {
                        move();
                        if (model.screen.equals("game")) e.consume();
                    }
                });
        stage.focusedProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!b) controller.stopMoving();
                        });
        stage.setTitle("Fruit Battle Online • Pixel Orchard");
        stage.setMinWidth(1024);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> controller.close());
        if (model.snapshotMode) {
            DemoData.populate(model);
            showScreen(parameters.getOrDefault("screen", "home"));
        } else showScreen("login");
        root.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                e -> {
                    if (e.getTarget() instanceof Button) feedback.audio.play("click", .55);
                });
        stage.show();
        var available = javafx.stage.Screen.getPrimary().getVisualBounds();
        if (stage.getWidth() > available.getWidth()) stage.setWidth(available.getWidth());
        if (stage.getHeight() > available.getHeight()) stage.setHeight(available.getHeight());
        stage.centerOnScreen();

        timer =
                new AnimationTimer() {
                    public void handle(long now) {
                        if (lastFrame == 0) {
                            lastFrame = now;
                            metricsStart = now;
                        }
                        lastFrame = now;
                        frameCount++;
                        if (Boolean.getBoolean("fruit.metrics")
                                && now - metricsStart >= 5_000_000_000L) {
                            System.out.printf(
                                    "RENDER %.1f FPS effects=%d clips=%d%n",
                                    frameCount / ((now - metricsStart) / 1e9),
                                    feedback.effects.size(),
                                    feedback.audio.openClips());
                            metricsStart = now;
                            frameCount = 0;
                        }
                        feedback.effects.removeIf(fx -> now - fx.born > 900_000_000L);
                        renderer.drawBackdrop(backdrop, now);
                        if (arena != null && model.screen.equals("game"))
                            renderer.drawGame(arena, skillButtons, skillStatus, now);
                        if (model.screen.equals("game") && now - lastNoticePaint >= 100_000_000L) {
                            lastNoticePaint = now;
                            showNotice(
                                    feedback.notices.current(
                                            model.connected || model.snapshotMode, now));
                        }
                        if (illustration != null) renderer.drawIllustration(illustration, now);
                        if (model.queued && queueLabel != null)
                            queueLabel.setText(
                                    "ĐANG TÌM ĐỐI THỦ  ·  "
                                            + (int) ((now - model.queueNanos) / 1_000_000_000L)
                                            + "s");
                    }
                };
        timer.start();
        if (model.snapshotMode) {
            PauseTransition pause = new PauseTransition(Duration.millis(700));
            pause.setOnFinished(e -> saveSnapshot());
            pause.play();
        }
    }

    public void showScreen(String name) {
        if (!name.equals("game")) controller.stopMoving();
        if (!name.equals(model.screen)) feedback.effects.clear();
        model.screen = name;
        feedback.audio.theme(name.equals("game") ? "game" : name.equals("result") ? "" : "menu");
        arena = null;
        illustration = null;
        queueLabel = null;
        skillStatus = null;
        findButton = null;
        Arrays.fill(skillButtons, null);
        buildChrome();
        body = new VBox(name.equals("game") ? 6 : 16);
        body.setPadding(
                new Insets(
                        name.equals("game") ? 5 : 24,
                        name.equals("game") ? 8 : 28,
                        name.equals("game") ? 6 : 22,
                        name.equals("game") ? 8 : 28));
        body.setFillWidth(true);
        body.setMinWidth(0);
        if (!name.equals("game")) {
            body.setBackground(
                    new Background(
                            new BackgroundFill(
                                    Color.web(PANEL, .97), CornerRadii.EMPTY, new Insets(7))));
            decorate(body, 18);
        }
        shell.setCenter(body);
        switch (name) {
            case "home" -> LobbyScreen.build(this);
            case "game" -> GameScreen.build(this);
            case "result" -> ResultScreen.build(this);
            case "profile" -> ProfileScreen.build(this);
            case "leaderboard" -> LeaderboardScreen.build(this);
            case "history" -> HistoryScreen.build(this);
            case "admin" -> AdminScreen.build(this);
            default -> LoginScreen.build(this);
        }
        // Ở cửa sổ nhỏ, biểu mẫu có thể cuộn thay vì cắt mất nút hoặc nội dung.
        if (Set.of("login", "home", "profile", "result").contains(name)) {
            body.setMinHeight(Region.USE_PREF_SIZE);
            ScrollPane scroll = new ScrollPane(body);
            scroll.getStyleClass().add("screen-scroll");
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            shell.setCenter(scroll);
        }
    }

    public void buildChrome() {
        top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8, 14, 8, 14));
        top.getStyleClass().add("top-bar");
        Canvas logo = new Canvas(46, 46);
        sprite(logo.getGraphicsContext2D(), "APPLE", 23, 23, 2.7);
        VBox brand =
                new VBox(
                        0,
                        label("FRUIT BATTLE", 25, INK, true),
                        label("VƯỜN ĐẤU TRÁI CÂY", 10, INK, true));
        ((Label) brand.getChildren().get(0)).setFont(Font.font(pixelFont, FontWeight.BOLD, 29));
        brand.getChildren().get(0).getStyleClass().add("brand-name");
        brand.getChildren().get(1).getStyleClass().add("brand-subtitle");
        HBox sign = new HBox(9, logo, brand);
        sign.setAlignment(Pos.CENTER_LEFT);
        sign.setPadding(new Insets(5, 17, 5, 12));
        sign.setBackground(
                new Background(
                        new BackgroundFill(Color.web(PANEL), CornerRadii.EMPTY, new Insets(7))));
        decorate(sign, 12);
        top.getChildren().addAll(sign, spacer());
        if (!model.user.isEmpty() && !model.screen.equals("login")) {
            if (!model.screen.equals("game")) {
                top.getChildren()
                        .addAll(
                                nav("Trang chủ", "home"),
                                nav("Xếp hạng", "leaderboard"),
                                nav("Lịch sử", "history"),
                                nav("Hồ sơ", "profile"));
                if (str(model.user, "role").equals("ADMIN"))
                    top.getChildren().add(nav("Quản trị", "admin"));
            } else {
                Label mode = label("ĐỐI KHÁNG 1 vs 1", 13, INK, true);
                mode.setPadding(new Insets(12, 18, 12, 18));
                mode.setBackground(
                        new Background(
                                new BackgroundFill(
                                        Color.web(PANEL), CornerRadii.EMPTY, new Insets(4))));
                decorate(mode, 8);
                top.getChildren().add(mode);
            }
            top.getChildren().add(button("?", false, this::help));
            if (!model.screen.equals("game"))
                top.getChildren().add(button("Đăng xuất", false, controller::logout));
        } else top.getChildren().add(button("Cách chơi  ?", false, this::help));
        top.getChildren().add(button("Âm thanh", false, this::settings));
        shell.setTop(top);
        message = label("Chào mừng đến với vườn đấu!", 12, "#fff0ce", false);
        message.setMinWidth(0);
        message.setMaxWidth(Double.MAX_VALUE);
        message.setFont(Font.font(bodyFont, FontWeight.BOLD, 12));
        message.setId("game-notice-text");
        HBox.setHgrow(message, javafx.scene.layout.Priority.ALWAYS);
        noticeIcon = new Canvas(28, 28);
        noticeBox = new HBox(10, noticeIcon, message);
        noticeBox.setId("game-notice");
        noticeBox.setAlignment(Pos.CENTER_LEFT);
        noticeBox.setMinWidth(0);
        noticeBox.setPrefWidth(400);
        noticeBox.setMaxWidth(400);
        noticeBox.setMinHeight(42);
        noticeBox.setMaxHeight(42);
        noticeBox.setPadding(new Insets(5, 10, 5, 10));
        HBox.setHgrow(noticeBox, javafx.scene.layout.Priority.NEVER);
        displayedNotice = null;
        showNotice(
                model.screen.equals("game")
                        ? null
                        : new GameplayNotice.Notice(
                                GameplayNotice.Tone.INFO, "i", "Chào mừng đến với vườn đấu!"));
        connection = label(connectionText(), 11, model.connected ? "#d4f183" : "#ffe9bf", true);
        reconnect = button("Kết nối lại", false, controller::reconnect);
        reconnect.setVisible(!model.connected && !model.user.isEmpty() && !model.snapshotMode);
        reconnect.setManaged(reconnect.isVisible());
        muteButton =
                button(feedback.audio.muted ? "Bật tiếng" : "Tắt tiếng", false, this::toggleMute);
        HBox footer = new HBox(12, noticeBox, spacer(), reconnect, muteButton, connection);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(7, 22, 8, 22));
        footer.getStyleClass().add("wood-footer");
        shell.setBottom(footer);
    }

    public Button nav(String title, String destination) {
        Button b = button(title, false, () -> navigate(destination));
        if (model.screen.equals(destination)) b.getStyleClass().add("active");
        return b;
    }

    public void navigate(String destination) {
        showScreen(destination);
        if (destination.equals("leaderboard")) controller.send(obj("type", "LEADERBOARD"));
        if (destination.equals("history")) controller.send(obj("type", "HISTORY"));
        if (destination.equals("profile")) controller.send(obj("type", "PROFILE_GET"));
        if (destination.equals("admin")) controller.loadAdmin();
    }

    public void toggleMute() {
        feedback.audio.muted = !feedback.audio.muted;
        feedback.audio.changed();
        if (muteButton != null)
            muteButton.setText(feedback.audio.muted ? "Bật tiếng" : "Tắt tiếng");
        toast(feedback.audio.muted ? "Đã tắt âm thanh · F8 để bật lại" : "Đã bật âm thanh", false);
    }

    public void saveSnapshot() {
        try {
            root.applyCss();
            root.layout();
            WritableImage image = root.snapshot(null, null);
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pixels = image.getPixelReader();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) png.setRGB(x, y, pixels.getArgb(x, y));
            Path target = Path.of(parameters.get("snapshot")).toAbsolutePath();
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            ImageIO.write(png, "png", target.toFile());
            System.out.println("SNAPSHOT " + target);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            Platform.exit();
        }
    }

    public void toast(String value, boolean error) {
        GameplayNotice.Tone tone = error ? GameplayNotice.Tone.DANGER : GameplayNotice.Tone.INFO;
        if (model.screen.equals("game") && error) {
            if (model.connected) feedback.notices.error(value, System.nanoTime());
            else feedback.notices.clear();
        }
        if (!model.screen.equals("game"))
            showNotice(new GameplayNotice.Notice(tone, error ? "!" : "i", value));
    }

    private void showNotice(GameplayNotice.Notice notice) {
        if (message == null) return;
        noticeBox.setVisible(notice != null);
        if (notice == null) {
            displayedNotice = null;
            message.setText("");
            return;
        }
        if (notice.equals(displayedNotice)) return;
        displayedNotice = notice;
        String[] colors =
                switch (notice.tone()) {
                    case DANGER -> new String[] {"#fce0d4", "#9b3025"};
                    default -> new String[] {"#e3edf0", "#295664"};
                };
        noticeBox.setStyle(
                "-fx-background-color:"
                        + colors[0]
                        + ";-fx-border-color:"
                        + colors[1]
                        + ";-fx-border-width:1 1 1 4;");
        message.setText(notice.text());
        message.setTextFill(Color.web(colors[1]));
        message.setTooltip(new javafx.scene.control.Tooltip(notice.text()));
        var g = noticeIcon.getGraphicsContext2D();
        g.clearRect(0, 0, 28, 28);
        String icon = notice.icon();
        g.setFill(Color.web(colors[1]));
        g.fillOval(2, 2, 24, 24);
        g.setFill(Color.WHITE);
        g.setFont(Font.font(bodyFont, FontWeight.BOLD, 18));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.fillText(icon, 14, 21);
    }

    public void pageTitle(String eyebrow, String title, String subtitle) {
        body.getChildren()
                .add(
                        new VBox(
                                8,
                                label(eyebrow, 11, LIME, true),
                                label(title, 31, INK, true),
                                text(subtitle, 14, MUTED)));
    }

    public void updateConnection() {
        if (connection != null) {
            connection.setText(connectionText());
            connection.setTextFill(Color.web(model.connected ? "#d4f183" : "#ffe9bf"));
        }
        if (reconnect != null) {
            reconnect.setVisible(!model.connected && !model.user.isEmpty() && !model.snapshotMode);
            reconnect.setManaged(reconnect.isVisible());
            reconnect.setDisable(model.connecting);
        }
    }

    public String connectionText() {
        String status =
                model.snapshotMode
                        ? "● BẢN XEM TRƯỚC"
                        : model.connecting
                                ? "◌ ĐANG KẾT NỐI"
                                : model.connected ? "● ĐÃ KẾT NỐI" : "○ CHƯA KẾT NỐI";
        return status + "  /  " + model.host + ":" + model.port;
    }

    public void help() {
        HelpDialog.show(this);
    }

    public void settings() {
        AudioSettingsDialog.show(this);
    }

    public void adminMatchDetails(Map<String, Object> match) {
        AdminScreen.showMatchDetails(this, match);
    }

    public void move() {
        controller.move(
                (held.contains(KeyCode.RIGHT) ? 1 : 0) - (held.contains(KeyCode.LEFT) ? 1 : 0));
    }

    public void clearHeld() {
        held.clear();
    }

    public void focusArena() {
        if (arena != null) arena.requestFocus();
    }

    public void queueDisconnected() {
        if (findButton != null) findButton.setText("👉  Tìm trận ngay");
        if (queueLabel != null) queueLabel.setText("ĐÃ NGẮT KẾT NỐI");
    }

    public void queueChanged(boolean queued) {
        if (findButton != null) findButton.setText(queued ? "Hủy tìm trận" : "👉  Tìm trận ngay");
        if (queueLabel != null)
            queueLabel.setText(queued ? "ĐANG TÌM ĐỐI THỦ" : "60 GIÂY  •  1 vs 1");
    }

    public void resultSaved(boolean saved) {
        if (!model.screen.equals("result") || savedStatus == null) return;
        savedStatus.setText(
                saved
                        ? "Kết quả đã lưu vào lịch sử thi đấu."
                        : "Kết quả đang chờ lưu. Máy chủ sẽ tự thử lại.");
        savedStatus.setTextFill(saved ? C_MUTED : C_CORAL);
    }

    public void stop() {
        if (timer != null) timer.stop();
        controller.close();
    }
}

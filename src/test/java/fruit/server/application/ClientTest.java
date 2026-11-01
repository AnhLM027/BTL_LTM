package fruit.server.application;

import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.json;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.number;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.parse;
import static fruit.common.Protocol.str;

import fruit.client.Client;
import fruit.common.Protocol;
import fruit.server.features.matches.Drop;
import fruit.server.features.matches.Match;
import fruit.server.features.matches.Player;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

/**
 * Conventional JavaFX application smoke test using public scene/control APIs. The real client talks
 * to an isolated TCP server; no desktop automation or production test hooks are required. Run
 * separately from the headless tests.
 */
public final class ClientTest {
    private static final String PASSWORD = "SmokeOrchard!2026";
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final AtomicInteger checks = new AtomicInteger();
    private static GameServer server;
    private static Stage window;
    private static int playerId;

    public static void main(String[] args) throws Exception {
        long began = System.nanoTime();
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, error) -> {
                    failure.compareAndSet(null, error);
                    System.err.println("Uncaught exception on " + thread.getName());
                    error.printStackTrace();
                });
        System.setProperty("fruit.admin.password", PASSWORD);
        System.setProperty(
                "fruit.settings",
                Files.createTempDirectory("fruit-ui-preferences-")
                        .resolve("settings.properties")
                        .toString());
        System.setProperty("fruit.metrics", "true");
        server = new GameServer(0, Files.createTempDirectory("fruit-ui-smoke-"), true);
        server.start();
        try (Peer account = new Peer(server.port())) {
            account.send(
                    "REGISTER",
                    "username",
                    "smoke_player",
                    "password",
                    PASSWORD,
                    "displayName",
                    "Smoke Orchard");
            Map<String, Object> authentication = account.await("AUTH");
            check(bool(authentication, "ok"), "Smoke player account is created over TCP");
            playerId = integer(map(authentication.get("user")), "id");
            account.send("LOGOUT");
            account.await("LOGGED_OUT");
        }

        Thread driver =
                new Thread(
                        () -> {
                            try (Peer opponent = new Peer(server.port())) {
                                opponent.send(
                                        "REGISTER",
                                        "username",
                                        "smoke_opponent",
                                        "password",
                                        PASSWORD,
                                        "displayName",
                                        "Socket Peach");
                                check(
                                        bool(opponent.await("AUTH"), "ok"),
                                        "Second TCP player is authenticated");
                                waitForWindow();
                                loginAndNavigate();
                                matchAndResult(opponent);
                                registrationAndAdmin();
                                lockedReconnectRecovery();
                                check(
                                        failure.get() == null,
                                        "No uncaught JavaFX exception occurred");
                            } catch (Throwable error) {
                                failure.compareAndSet(null, error);
                                error.printStackTrace();
                                try {
                                    screenshot("ui-failure.png");
                                    System.err.println(fx(ClientTest::describeScene));
                                } catch (Exception ignored) {
                                }
                            } finally {
                                try {
                                    fx(
                                            () -> {
                                                Platform.exit();
                                                return null;
                                            });
                                } catch (Exception ignored) {
                                }
                            }
                        },
                        "fruit-ui-test-driver");
        driver.setDaemon(true);
        driver.start();

        // Application.launch owns the main thread until the driver exits JavaFX.
        try {
            Application.launch(Client.class, "--host=127.0.0.1", "--port=" + server.port());
        } finally {
            driver.join(10_000);
            server.close();
        }
        if (failure.get() != null)
            throw new AssertionError("Client smoke test failed", failure.get());
        check(!driver.isAlive(), "UI test driver completed");
        System.out.printf(
                "PASS UI: %d checks in %.1f seconds; screenshots: target/ui-*.png%n",
                checks.get(), (System.nanoTime() - began) / 1_000_000_000.0);
    }

    private static void waitForWindow() throws Exception {
        await(
                "JavaFX window",
                () -> {
                    try {
                        return fx(
                                () -> {
                                    for (Window candidate : Window.getWindows()) {
                                        if (candidate instanceof Stage stage
                                                && stage.isShowing()
                                                && stage.getTitle().contains("Fruit Battle")) {
                                            window = stage;
                                            return true;
                                        }
                                    }
                                    return false;
                                });
                    } catch (IllegalStateException notStarted) {
                        return false;
                    }
                });
        check(fx(() -> window.getScene() != null), "Client opens a real JavaFX scene");
    }

    private static void loginAndNavigate() throws Exception {
        awaitLabel("ĐĂNG NHẬP");
        check(fx(() -> buttons("Vào vườn đấu").size() == 1), "Login action is present");
        screenshot("ui-login.png");
        fx(
                () -> {
                    textField("Tên đăng nhập").setText("smoke_player");
                    passwordField("Mật khẩu").setText(PASSWORD);
                    button("Vào vườn đấu").fire();
                    return null;
                });
        awaitLabel("Xin chào, Smoke Orchard!");
        check(
                engine(() -> server.online.containsKey(playerId)),
                "UI login authenticates on the TCP server");
        Thread.sleep(150); // Allow the first illustration animation pulse to paint.
        screenshot("ui-home.png");
        audioSettings();
        helpGuide();

        click("Hồ sơ");
        awaitLabel("HỒ SƠ NGƯỜI CHƠI");
        await("Profile name field", () -> fx(() -> hasTextField("Tên hiển thị")));
        fx(
                () -> {
                    textField("Tên hiển thị").setText("Smoke Pixel");
                    ((javafx.scene.control.ToggleButton)
                                    window.getScene().lookup("#avatar-choice-1"))
                            .fire();
                    button("Lưu thay đổi").fire();
                    return null;
                });
        awaitLabel("Smoke Pixel");
        check(
                engine(() -> server.store.users.get(playerId).displayName.equals("Smoke Pixel")),
                "Profile rename reaches the server through the UI");
        check(
                engine(() -> server.store.users.get(playerId).avatarId == 1),
                "Profile avatar selection reaches the server");
        screenshot("ui-profile.png");

        click("Xếp hạng");
        awaitLabel("BẢNG XẾP HẠNG");
        await("Leaderboard rows", () -> fx(() -> firstTable().getItems().size() == 2));
        check(
                fx(
                        () ->
                                firstTable().getItems().stream()
                                        .anyMatch(
                                                row ->
                                                        str(map(row), "displayName")
                                                                .equals("Smoke Pixel"))),
                "Leaderboard displays the updated player name");
        fx(
                () -> {
                    ((TextField) window.getScene().lookup("#leaderboard-search"))
                            .setText("smoke pixel");
                    button("Tìm kiếm").fire();
                    return null;
                });
        check(
                fx(
                        () ->
                                firstTable().getItems().size() == 1
                                        && integer(map(firstTable().getItems().get(0)), "id")
                                                == playerId),
                "Leaderboard search filters by display name");
        fx(
                () -> {
                    ((TextField) window.getScene().lookup("#leaderboard-search"))
                            .setText("unknown-user");
                    button("Tìm kiếm").fire();
                    return null;
                });
        check(
                fx(() -> firstTable().getItems().isEmpty()),
                "Unmatched search shows an empty result");
        click("Xóa lọc");
        check(fx(() -> firstTable().getItems().size() == 2), "Clearing search restores ranking");
        screenshot("ui-leaderboard.png");
        click("Lịch sử");
        awaitLabel("LỊCH SỬ THI ĐẤU");
        check(fx(() -> firstTable().getItems().isEmpty()), "New player history is empty");
        click("Trang chủ");
        awaitLabel("Xin chào, Smoke Pixel!");

        click("Tìm trận ngay");
        awaitButton("Hủy tìm trận");
        check(
                engine(() -> server.queue.contains(playerId)),
                "Find match enters the authoritative queue");
        click("Hủy tìm trận");
        awaitButton("Tìm trận ngay");
        check(
                engine(() -> !server.queue.contains(playerId)),
                "Cancel match leaves the authoritative queue");
    }

    private static void matchAndResult(Peer opponent) throws Exception {
        opponent.send("MATCH_FIND");
        opponent.await("QUEUE");
        click("Tìm trận ngay");
        opponent.await("MATCH_FOUND");
        awaitLabel("ĐẤU TRƯỜNG");
        await(
                "Playing state after countdown",
                () -> opponent.state != null && str(opponent.state, "phase").equals("PLAYING"));
        check(
                fx(
                        () ->
                                nodes().stream()
                                                .filter(
                                                        n ->
                                                                n instanceof Button b
                                                                        && b.getGraphic() != null)
                                                .count()
                                        == 3),
                "Gameplay presents all three skill buttons");
        double before = number(opponent.player(playerId), "x");
        key(KeyEvent.KEY_PRESSED, KeyCode.RIGHT);
        await(
                "Right arrow reaches server",
                () -> number(opponent.player(playerId), "x") > before + 70);
        key(KeyEvent.KEY_RELEASED, KeyCode.RIGHT);
        check(
                number(opponent.player(playerId), "x") > before + 70,
                "JavaFX keyboard event moves the authoritative basket");
        await(
                "Released key stops movement",
                () ->
                        engine(
                                () ->
                                        server.activeByUser.get(playerId).player(playerId).direction
                                                == 0));

        // Supply energy through the existing package-visible model, then exercise
        // the real keyboard -> TCP -> validated skill -> opponent event path.
        engine(
                () -> {
                    server.activeByUser.get(playerId).player(playerId).energy = 100;
                    return null;
                });
        await(
                "Energy enables skill controls",
                () ->
                        fx(
                                () ->
                                        nodes().stream()
                                                .anyMatch(
                                                        n ->
                                                                n instanceof Button b
                                                                        && b.getGraphic() != null
                                                                        && !b.isDisabled())));
        key(KeyEvent.KEY_PRESSED, KeyCode.DIGIT1);
        key(KeyEvent.KEY_RELEASED, KeyCode.DIGIT1);
        Map<String, Object> attack = opponent.await("SKILL_EFFECT");
        check(
                integer(attack, "attackerId") == playerId && integer(attack, "skill") == 1,
                "Digit1 activates the server-validated bomb skill");
        screenshot("ui-game.png");
        gameplayAtMinimumSize();
        presentationFeedback(opponent);

        // The independent headless suite already runs a complete 63-second match.
        // Here only the clock is advanced to keep screen-flow verification short.
        engine(
                () -> {
                    Match match = server.activeByUser.get(playerId);
                    match.elapsed = 59.6;
                    match.nextSpawn = 60;
                    match.nextTarget = 60;
                    return null;
                });
        Map<String, Object> result = opponent.await("MATCH_RESULT");
        check(bool(result, "saved"), "Match result is persisted by the isolated server");
        awaitButton("Chơi tiếp");
        awaitButton("Về trang chủ");
        check(
                fx(() -> hasLabel("Kết quả đã lưu vào lịch sử thi đấu.")),
                "Result screen confirms saved statistics");
        screenshot("ui-result.png");

        click("Về trang chủ");
        awaitLabel("Xin chào, Smoke Pixel!");
        check(
                fx(() -> buttons("Tìm trận ngay").size() == 1),
                "Result home action returns to the lobby");
        click("Lịch sử");
        awaitLabel("LỊCH SỬ THI ĐẤU");
        await(
                "Saved match appears in UI history",
                () -> fx(() -> firstTable().getItems().size() == 1));
        check(
                fx(
                        () ->
                                str(map(firstTable().getItems().get(0)), "opponent")
                                        .equals("Socket Peach")),
                "UI history displays the actual TCP opponent");
        check(
                fx(
                        () ->
                                String.valueOf(firstTable().getColumns().get(0).getCellData(0))
                                        .matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}")),
                "History table renders readable local timestamps");
        screenshot("ui-history.png");

        // A second short real match exercises Play again independently of Home.
        click("Trang chủ");
        awaitLabel("Xin chào, Smoke Pixel!");
        opponent.send("MATCH_FIND");
        opponent.await("QUEUE");
        click("Tìm trận ngay");
        Map<String, Object> found = opponent.await("MATCH_FOUND");
        await(
                "Second match after countdown",
                () ->
                        opponent.state != null
                                && str(opponent.state, "matchId").equals(str(found, "matchId"))
                                && str(opponent.state, "phase").equals("PLAYING"));
        engine(
                () -> {
                    Match match = server.activeByUser.get(playerId);
                    match.elapsed = 59.8;
                    match.nextSpawn = 60;
                    match.nextTarget = 60;
                    return null;
                });
        opponent.await("MATCH_RESULT");
        awaitButton("Chơi tiếp");
        click("Chơi tiếp");
        awaitButton("Hủy tìm trận");
        check(
                engine(() -> server.queue.contains(playerId)),
                "Play again starts matchmaking from the result screen");
        click("Hủy tìm trận");
        awaitButton("Tìm trận ngay");
        click("Đăng xuất");
        awaitLabel("ĐĂNG NHẬP");
        await(
                "GameServer removes logged out UI player",
                () -> engine(() -> !server.online.containsKey(playerId)));
        check(
                engine(() -> !server.online.containsKey(playerId)),
                "Logout closes the authenticated client session");
    }

    private static void gameplayAtMinimumSize() throws Exception {
        fx(
                () -> {
                    window.setWidth(1024);
                    window.setHeight(720);
                    return null;
                });
        await(
                "Gameplay resizes to the minimum supported window",
                () ->
                        fx(
                                () ->
                                        Math.abs(window.getWidth() - 1024) < 2
                                                && Math.abs(window.getHeight() - 720) < 2
                                                && window.getScene().getWidth() <= 1024));
        check(
                fx(
                        () -> {
                            Scene scene = window.getScene();
                            return nodes().stream()
                                    .filter(
                                            n ->
                                                    n instanceof javafx.scene.canvas.Canvas c
                                                            && c.getWidth() > 200)
                                    .allMatch(n -> insideScene(n, scene));
                        }),
                "Gameplay canvases fit the 1024x720 viewport in both dimensions");
        check(
                fx(
                        () -> {
                            Scene scene = window.getScene();
                            List<Node> controls =
                                    nodes().stream()
                                            .filter(n -> n instanceof Button b && b.isVisible())
                                            .toList();
                            return !controls.isEmpty()
                                    && controls.stream().allMatch(n -> insideScene(n, scene));
                        }),
                "Navigation, skill and sound buttons stay fully visible at 1024x720");
        // Canvas geometry changes during layout; its live painting follows on
        // animation pulses. Let the resized arena repaint before snapshotting.
        Thread.sleep(250);
        screenshot("ui-game-1024.png");
        fx(
                () -> {
                    window.setWidth(1280);
                    window.setHeight(820);
                    return null;
                });
        await(
                "Gameplay restores the regular window",
                () ->
                        fx(
                                () ->
                                        Math.abs(window.getWidth() - 1280) < 2
                                                && window.getScene().getWidth() > 1200));
        Thread.sleep(250);
    }

    private static boolean insideScene(Node node, Scene scene) {
        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        return bounds.getMinX() >= -.5
                && bounds.getMinY() >= -.5
                && bounds.getMaxX() <= scene.getWidth() + .5
                && bounds.getMaxY() <= scene.getHeight() + .5;
    }

    private static Stage audioWindow() {
        return Window.getWindows().stream()
                .filter(
                        w ->
                                w instanceof Stage s
                                        && s.isShowing()
                                        && s.getTitle().equals("Âm thanh & chuyển động"))
                .map(w -> (Stage) w)
                .findFirst()
                .orElse(null);
    }

    private static void helpGuide() throws Exception {
        Platform.runLater(() -> button("?").fire());
        await("Illustrated guide opens", () -> fx(() -> helpWindow() != null));
        check(
                fx(
                        () ->
                                nodes(helpWindow().getScene().getRoot()).stream()
                                        .anyMatch(
                                                n ->
                                                        n instanceof TabPane t
                                                                && t.getTabs().size() == 3)),
                "Guide separates basics, scoring and skills");
        screenshotWindow("ui-help.png", fx(ClientTest::helpWindow));
        fx(
                () -> {
                    TabPane tabs =
                            (TabPane)
                                    nodes(helpWindow().getScene().getRoot()).stream()
                                            .filter(n -> n instanceof TabPane)
                                            .findFirst()
                                            .orElseThrow();
                    tabs.getSelectionModel().select(2);
                    return null;
                });
        screenshotWindow("ui-help-skills.png", fx(ClientTest::helpWindow));
        fx(
                () -> {
                    DialogPane pane =
                            (DialogPane)
                                    nodes(helpWindow().getScene().getRoot()).stream()
                                            .filter(n -> n instanceof DialogPane)
                                            .findFirst()
                                            .orElseThrow();
                    ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                    return null;
                });
    }

    private static Window helpWindow() {
        return Window.getWindows().stream()
                .filter(
                        w ->
                                w instanceof Stage s
                                        && s.getTitle().startsWith("Cách chơi")
                                        && s.isShowing())
                .findFirst()
                .orElse(null);
    }

    private static void audioSettings() throws Exception {
        Platform.runLater(() -> button("Âm thanh").fire());
        await("Audio settings opens", () -> fx(() -> audioWindow() != null));
        fx(
                () -> {
                    Parent root = audioWindow().getScene().getRoot();
                    root.applyCss();
                    root.layout();
                    check(
                            nodes(root).stream()
                                            .filter(n -> n instanceof javafx.scene.control.Slider)
                                            .count()
                                    == 3,
                            "Three independent volume sliders are visible");
                    ((javafx.scene.control.Slider) root.lookup("#volume-master")).setValue(72);
                    ((javafx.scene.control.Slider) root.lookup("#volume-music")).setValue(18);
                    ((javafx.scene.control.Slider) root.lookup("#volume-sfx")).setValue(60);
                    ((javafx.scene.control.CheckBox) root.lookup("#audio-mute")).setSelected(true);
                    ((javafx.scene.control.CheckBox) root.lookup("#reduced-motion"))
                            .setSelected(true);
                    return null;
                });
        screenshotWindow("ui-audio.png", fx(ClientTest::audioWindow));
        fx(
                () -> {
                    Parent root = audioWindow().getScene().getRoot();
                    ((javafx.scene.control.CheckBox) root.lookup("#reduced-motion"))
                            .setSelected(false);
                    DialogPane pane =
                            nodes(root).stream()
                                    .filter(n -> n instanceof DialogPane)
                                    .map(n -> (DialogPane) n)
                                    .findFirst()
                                    .orElseThrow();
                    ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                    return null;
                });
        await("Audio settings closes", () -> fx(() -> audioWindow() == null));
        awaitButton("Bật tiếng");
        key(KeyEvent.KEY_PRESSED, KeyCode.F8);
        key(KeyEvent.KEY_RELEASED, KeyCode.F8);
        awaitButton("Tắt tiếng");
        check(
                fx(() -> buttons("Tắt tiếng").size() == 1),
                "F8 unmutes without changing gameplay keys");
        await("Settings saved", () -> Files.exists(Path.of(System.getProperty("fruit.settings"))));
        java.util.Properties saved = new java.util.Properties();
        await(
                "Slider values persisted",
                () -> {
                    try (var in =
                            Files.newInputStream(Path.of(System.getProperty("fruit.settings")))) {
                        saved.load(in);
                        return saved.getProperty("master", "").equals("0.72")
                                && saved.getProperty("music", "").equals("0.18");
                    } catch (Exception e) {
                        return false;
                    }
                });
        check(saved.getProperty("sfx").equals("0.6"), "UI slider changes persist independently");
    }

    private static void presentationFeedback(Peer opponent) throws Exception {
        engine(
                () -> {
                    Match match = server.activeByUser.get(playerId);
                    Player p = match.player(playerId);
                    for (int i = 0; i < 5; i++)
                        match.catchDrop(p, new Drop(900 + i, match.target, p.x, 550, 180));
                    return null;
                });
        await("Combo is authoritative", () -> integer(opponent.player(playerId), "combo") >= 5);
        Thread.sleep(90);
        screenshot("ui-catch.png");
        engine(
                () -> {
                    Match match = server.activeByUser.get(playerId);
                    Player p = match.player(playerId);
                    match.catchDrop(
                            p,
                            new Drop(
                                    910,
                                    match.target.equals("APPLE") ? "BANANA" : "APPLE",
                                    p.x,
                                    550,
                                    180));
                    match.catchDrop(p, new Drop(911, "BOMB", p.x, 550, 180));
                    return null;
                });
        await("Bomb statistics update", () -> integer(opponent.player(playerId), "bombs") >= 1);
        Thread.sleep(85);
        screenshot("ui-bomb.png");
        for (int skill = 2; skill <= 3; skill++) {
            engine(
                    () -> {
                        Match match = server.activeByUser.get(playerId);
                        match.step(6.1);
                        match.player(playerId).energy = 100;
                        return null;
                    });
            await(
                    "Skill enabled after recovery",
                    () ->
                            fx(
                                    () ->
                                            nodes().stream()
                                                    .anyMatch(
                                                            n ->
                                                                    n instanceof Button b
                                                                            && b.getGraphic()
                                                                                    != null
                                                                            && !b.isDisabled())));
            key(KeyEvent.KEY_PRESSED, skill == 2 ? KeyCode.DIGIT2 : KeyCode.DIGIT3);
            key(KeyEvent.KEY_RELEASED, skill == 2 ? KeyCode.DIGIT2 : KeyCode.DIGIT3);
            Map<String, Object> effect = opponent.await("SKILL_EFFECT");
            check(
                    integer(effect, "skill") == skill,
                    "UI dispatches skill " + skill + " through TCP");
            final String field = skill == 2 ? "freeze" : "magnet";
            int affected =
                    skill == 3
                            ? playerId
                            : engine(() -> server.activeByUser.get(playerId).other(playerId).id);
            check(integer(effect, "targetId") == affected, "Skill targets the correct player");
            await(
                    "Effect in authoritative snapshot",
                    () -> number(opponent.player(affected), field) > 0);
            if (skill == 3) {
                int otherId = engine(() -> server.activeByUser.get(playerId).other(playerId).id);
                check(
                        number(opponent.player(playerId), "basketWidth") == 140
                                && number(opponent.player(otherId), "basketWidth") == 100,
                        "Magnet widens only the caster basket in TCP snapshots");
            }
            Thread.sleep(160); // Let the client receive and paint the matching snapshot.
            screenshot(skill == 2 ? "ui-freeze.png" : "ui-magnet.png");
        }
        engine(
                () -> {
                    Player own = server.activeByUser.get(playerId).player(playerId);
                    own.freeze = 3;
                    own.magnet = 5;
                    own.bombWarning = 2;
                    return null;
                });
        Thread.sleep(150);
        check(
                fx(() -> !window.getScene().lookup("#game-notice").isVisible()),
                "Bomb and skill effects do not show footer notices");
        key(KeyEvent.KEY_PRESSED, KeyCode.DIGIT1);
        key(KeyEvent.KEY_RELEASED, KeyCode.DIGIT1);
        await(
                "Rejected action notice",
                () ->
                        fx(
                                () ->
                                        window.getScene().lookup("#game-notice").isVisible()
                                                && ((Label)
                                                                window.getScene()
                                                                        .lookup(
                                                                                "#game-notice-text"))
                                                        .getText()
                                                        .contains("hồi chiêu")));
        screenshot("ui-notice-error.png");
    }

    private static void registrationAndAdmin() throws Exception {
        click("Chưa có tài khoản?");
        awaitLabel("TẠO TÀI KHOẢN");
        await("Registration form fields", () -> fx(() -> hasTextField("Tên hiển thị")));
        screenshot("ui-register.png");
        fx(
                () -> {
                    textField("Tên đăng nhập").setText("smoke_registered");
                    passwordField("Mật khẩu").setText(PASSWORD);
                    textField("Tên hiển thị").setText("UI Blossom");
                    button("Tạo tài khoản").fire();
                    return null;
                });
        awaitLabel("Xin chào, UI Blossom!");
        check(
                engine(
                        () ->
                                server.store.findUsername("smoke_registered") != null
                                        && server.store
                                                .findUsername("smoke_registered")
                                                .role
                                                .equals("PLAYER")),
                "Registration form creates a real PLAYER account over TCP");

        fx(
                () -> {
                    window.setWidth(1024);
                    window.setHeight(720);
                    return null;
                });
        await(
                "Minimum supported stage size",
                () ->
                        fx(
                                () ->
                                        Math.abs(window.getWidth() - 1024) < 2
                                                && Math.abs(window.getHeight() - 720) < 2
                                                && window.getScene().getWidth() <= 1024
                                                && window.getScene().getWidth() > 950
                                                && window.getScene().getHeight() <= 720));
        check(
                fx(() -> scrollToButton("Tìm trận ngay")),
                "Find match remains reachable at 1024x720");
        check(
                fx(
                        () ->
                                nodes().stream()
                                        .filter(
                                                n ->
                                                        n instanceof javafx.scene.canvas.Canvas c
                                                                && c.getWidth() > 200)
                                        .allMatch(
                                                n -> {
                                                    Bounds b = n.localToScene(n.getBoundsInLocal());
                                                    return b.getMinX() >= 0
                                                            && b.getMaxX()
                                                                    <= window.getScene().getWidth();
                                                })),
                "Orchard canvas resizes within the 1024px viewport instead of retaining old width");
        screenshot("ui-home-1024.png");
        click("Hồ sơ");
        await("Compact profile form", () -> fx(() -> hasTextField("Tên hiển thị")));
        check(
                fx(() -> scrollToButton("Lưu thay đổi")),
                "Profile save remains reachable by scrolling at 1024x720");
        screenshot("ui-profile-1024.png");
        fx(
                () -> {
                    window.setWidth(1280);
                    window.setHeight(820);
                    return null;
                });
        click("Đăng xuất");
        awaitButton("Đã có tài khoản?");
        click("Đã có tài khoản?");
        awaitLabel("ĐĂNG NHẬP");

        fx(
                () -> {
                    textField("Tên đăng nhập").setText("admin");
                    passwordField("Mật khẩu").setText(PASSWORD);
                    button("Vào vườn đấu").fire();
                    return null;
                });
        awaitButton("Quản trị");
        click("Quản trị");
        awaitLabel("QUẢN TRỊ HỆ THỐNG");
        await("Admin accounts loaded", () -> fx(() -> selectedAdminTable().getItems().size() == 4));
        check(
                fx(
                        () ->
                                selectedAdminTable().getItems().stream()
                                        .anyMatch(
                                                row ->
                                                        str(map(row), "username")
                                                                .equals("smoke_registered"))),
                "Administrator UI receives the registered account list");
        fx(() -> {
            ((TextField)window.getScene().lookup("#admin-search")).setText("UI Blossom");
            button("Tìm kiếm").fire(); return null;
        });
        await("Admin account search", () -> fx(() -> selectedAdminTable().getItems().size() == 1
                && str(map(selectedAdminTable().getItems().get(0)), "username").equals("smoke_registered")));
        click("Làm mới dữ liệu");
        await("Admin refresh preserves search", () -> fx(() -> selectedAdminTable().getItems().size() == 1));
        click("Xóa lọc");
        await("Admin clear restores accounts", () -> fx(() -> selectedAdminTable().getItems().size() == 4));
        check(fx(() -> {
            Bounds refresh = window.getScene().lookup("#admin-refresh").localToScene(
                    window.getScene().lookup("#admin-refresh").getBoundsInLocal());
            Bounds tabs = adminTabs().localToScene(adminTabs().getBoundsInLocal());
            return Math.abs(refresh.getMinY() - tabs.getMinY()) < 10
                    && selectedAdminTable().getHeight() >= 260;
        }), "Admin toolbar shares the tab row and list has a larger viewport");
        accountAction("smoke_registered", "Khóa");
        await(
                "Account locked by admin UI",
                () -> engine(() -> server.store.findUsername("smoke_registered").locked));
        await(
                "Admin table reflects locked state",
                () -> fx(() -> adminRowLocked("smoke_registered")));
        try (Peer locked = new Peer(server.port())) {
            locked.send("LOGIN", "username", "smoke_registered", "password", PASSWORD);
            check(!bool(locked.await("AUTH"), "ok"), "UI lock prevents a real TCP login");
        }
        accountAction("smoke_registered", "Mở khóa");
        await(
                "Account unlocked by admin UI",
                () -> engine(() -> !server.store.findUsername("smoke_registered").locked));
        await(
                "Admin table reflects unlocked state",
                () -> fx(() -> !adminRowLocked("smoke_registered")));
        check(
                engine(() -> !server.store.findUsername("smoke_registered").locked),
                "UI unlock is stored on the server");
        screenshot("ui-admin.png");
        fx(() -> { window.setWidth(1024); window.setHeight(720); return null; });
        Thread.sleep(160);
        check(fx(() -> {
            Node refresh = window.getScene().lookup("#admin-refresh");
            return refresh.localToScene(refresh.getBoundsInLocal()).getMaxX() <= window.getScene().getWidth()
                    && selectedAdminTable().getHeight() >= 180;
        }), "Admin toolbar and list fit the minimum window");
        screenshot("ui-admin-1024.png");
        fx(() -> { window.setWidth(1280); window.setHeight(820); return null; });

        fx(
                () -> {
                    TabPane tabs = adminTabs();
                    tabs.getTabs().stream()
                            .filter(tab -> tab.getText().equals("Trận đấu gần đây"))
                            .findFirst()
                            .ifPresent(tab -> tabs.getSelectionModel().select(tab));
                    return null;
                });
        await("Admin match list", () -> fx(() -> selectedAdminTable().getItems().size() == 2));
        fx(() -> {
            String id = str(map(selectedAdminTable().getItems().get(0)), "matchId");
            ((TextField)window.getScene().lookup("#admin-search")).setText(id);
            button("Tìm kiếm").fire(); return null;
        });
        await("Admin match ID search", () -> fx(() -> selectedAdminTable().getItems().size() == 1));
        click("Xóa lọc");
        await("Admin clear restores matches", () -> fx(() -> selectedAdminTable().getItems().size() == 2));
        fx(
                () -> {
                    selectedAdminTable().getSelectionModel().select(0);
                    return null;
                });
        await(
                "Match detail action enabled",
                () -> fx(() -> !button("Xem chi tiết trận").isDisabled()));
        // showAndWait runs a nested FX event loop, so the opening action is queued
        // without waiting for its handler to return before inspecting the dialog.
        fx(
                () -> {
                    Platform.runLater(() -> button("Xem chi tiết trận").fire());
                    return null;
                });
        await("Match details dialog", () -> fx(() -> detailWindow() != null));
        check(
                fx(
                        () -> {
                            List<Node> contents = nodes(detailWindow().getScene().getRoot());
                            return contents.stream()
                                            .anyMatch(
                                                    n ->
                                                            n instanceof Label l
                                                                    && l.getText()
                                                                            .equals("Smoke Pixel"))
                                    && contents.stream()
                                            .anyMatch(
                                                    n ->
                                                            n instanceof Label l
                                                                    && l.getText()
                                                                            .equals("Socket Peach"))
                                    && contents.stream()
                                                    .filter(
                                                            n ->
                                                                    n instanceof Label l
                                                                            && l.getText()
                                                                                    .equals(
                                                                                            "Quả hứng"
                                                                                                + " đúng"))
                                                    .count()
                                            == 2;
                        }),
                "Admin match dialog shows both players and detailed catch statistics");
        screenshotWindow("ui-admin-detail.png", fx(ClientTest::detailWindow));
        fx(
                () -> {
                    DialogPane pane =
                            nodes(detailWindow().getScene().getRoot()).stream()
                                    .filter(n -> n instanceof DialogPane)
                                    .map(n -> (DialogPane) n)
                                    .findFirst()
                                    .orElseThrow();
                    ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                    return null;
                });
        await("Match details dialog closes", () -> fx(() -> detailWindow() == null));
        check(fx(() -> detailWindow() == null), "Admin match detail modal closes normally");
        click("Đăng xuất");
        awaitLabel("ĐĂNG NHẬP");
    }

    private static void lockedReconnectRecovery() throws Exception {
        await(
                "Previous admin session logs out",
                () ->
                        engine(
                                () ->
                                        !server.online.containsKey(
                                                server.store.findUsername("admin").id)));
        fx(
                () -> {
                    textField("Tên đăng nhập").setText("smoke_player");
                    passwordField("Mật khẩu").setText(PASSWORD);
                    button("Vào vườn đấu").fire();
                    return null;
                });
        awaitLabel("Xin chào, Smoke Pixel!");
        try (Peer administrator = new Peer(server.port())) {
            administrator.send("LOGIN", "username", "admin", "password", PASSWORD);
            check(
                    bool(administrator.await("AUTH"), "ok"),
                    "Separate administrator authenticates for reconnect regression");
            administrator.send("ADMIN_LOCK", "id", playerId, "locked", true);
            check(
                    bool(administrator.await("ACTION"), "ok"),
                    "Administrator locks the active UI account");
            awaitButton("Kết nối lại");
            click("Kết nối lại");
            awaitLabel("ĐĂNG NHẬP");
            awaitLabel("Tài khoản đã bị khóa.");
            check(
                    fx(
                            () ->
                                    !button("Vào vườn đấu").isDisabled()
                                            && buttons("Kết nối lại").isEmpty()),
                    "Rejected reauthentication returns to a usable login form with the"
                            + " locked-account message");
            check(
                    engine(() -> !server.online.containsKey(playerId)),
                    "Rejected reconnect leaves no authenticated player session");

            administrator.send("ADMIN_LOCK", "id", playerId, "locked", false);
            check(
                    bool(administrator.await("ACTION"), "ok"),
                    "Administrator unlocks the account after rejected reconnect");
            fx(
                    () -> {
                        textField("Tên đăng nhập").setText("smoke_player");
                        passwordField("Mật khẩu").setText(PASSWORD);
                        button("Vào vườn đấu").fire();
                        return null;
                    });
            awaitLabel("Xin chào, Smoke Pixel!");
            check(
                    engine(() -> server.online.containsKey(playerId)),
                    "Login recovers successfully after the account is unlocked");
            click("Đăng xuất");
            awaitLabel("ĐĂNG NHẬP");
        }
    }

    private static TabPane adminTabs() {
        return nodes().stream()
                .filter(n -> n instanceof TabPane)
                .map(n -> (TabPane) n)
                .findFirst()
                .orElseThrow();
    }

    private static TableView<?> selectedAdminTable() {
        Node content = adminTabs().getSelectionModel().getSelectedItem().getContent();
        return nodes(content).stream()
                .filter(n -> n instanceof TableView<?>)
                .map(n -> (TableView<?>) n)
                .findFirst()
                .orElseThrow();
    }

    private static boolean adminRowLocked(String username) {
        return selectedAdminTable().getItems().stream()
                .map(Protocol::map)
                .filter(row -> str(row, "username").equals(username))
                .findFirst()
                .map(row -> bool(row, "locked"))
                .orElseThrow();
    }

    private static void accountAction(String username, String action) throws Exception {
        await(
                "Admin action " + action + " for " + username,
                () -> fx(() -> accountButton(username, action) != null));
        fx(
                () -> {
                    Button button = accountButton(username, action);
                    check(!button.isDisabled(), "Admin action enabled: " + action);
                    button.fire();
                    return null;
                });
    }

    private static Button accountButton(String username, String action) {
        TableView<?> table = selectedAdminTable();
        for (int index = 0; index < table.getItems().size(); index++) {
            if (str(map(table.getItems().get(index)), "username").equals(username)) {
                table.scrollTo(index);
                table.applyCss();
                table.layout();
                break;
            }
        }
        for (Node node : nodes(table)) {
            if (node instanceof TableRow<?> row
                    && str(map(row.getItem()), "username").equals(username)) {
                return nodes(row).stream()
                        .filter(child -> child instanceof Button b && b.getText().equals(action))
                        .map(child -> (Button) child)
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private static Window detailWindow() {
        return Window.getWindows().stream()
                .filter(
                        candidate ->
                                candidate instanceof Stage stage
                                        && stage.isShowing()
                                        && stage.getTitle().equals("Chi tiết trận đấu"))
                .findFirst()
                .orElse(null);
    }

    private static boolean scrollToButton(String text) {
        Button action = button(text);
        ScrollPane scroll =
                nodes().stream()
                        .filter(
                                n ->
                                        n instanceof ScrollPane pane
                                                && pane.getStyleClass().contains("screen-scroll"))
                        .map(n -> (ScrollPane) n)
                        .findFirst()
                        .orElseThrow();
        Bounds actionBounds = action.localToScene(action.getBoundsInLocal());
        Bounds contentBounds =
                scroll.getContent().localToScene(scroll.getContent().getBoundsInLocal());
        double viewportHeight = scroll.getViewportBounds().getHeight();
        double travel =
                Math.max(1, scroll.getContent().getBoundsInLocal().getHeight() - viewportHeight);
        scroll.setVvalue(
                Math.max(
                        0,
                        Math.min(
                                1,
                                (actionBounds.getMinY()
                                                - contentBounds.getMinY()
                                                - viewportHeight / 2)
                                        / travel)));
        window.getScene().getRoot().layout();
        Node viewport = scroll.lookup(".viewport");
        Bounds visible = viewport.localToScene(viewport.getBoundsInLocal());
        Bounds moved = action.localToScene(action.getBoundsInLocal());
        return visible.contains(moved.getCenterX(), moved.getCenterY()) && !action.isDisabled();
    }

    private static void key(javafx.event.EventType<KeyEvent> type, KeyCode code) throws Exception {
        fx(
                () -> {
                    Event.fireEvent(
                            window.getScene(),
                            new KeyEvent(type, "", "", code, false, false, false, false));
                    return null;
                });
    }

    private static void click(String text) throws Exception {
        awaitButton(text);
        fx(
                () -> {
                    Button button = button(text);
                    check(!button.isDisabled(), "Button enabled: " + text);
                    button.fire();
                    return null;
                });
    }

    private static void awaitButton(String text) throws Exception {
        await("Button " + text, () -> fx(() -> !buttons(text).isEmpty()));
    }

    private static void awaitLabel(String text) throws Exception {
        await("Label " + text, () -> fx(() -> hasLabel(text)));
    }

    private static boolean hasLabel(String text) {
        return nodes().stream().anyMatch(n -> n instanceof Label l && l.getText().equals(text));
    }

    private static Button button(String prefix) {
        List<Button> matches = buttons(prefix);
        if (matches.size() != 1)
            throw new AssertionError(
                    "Expected one button starting with '"
                            + prefix
                            + "', found "
                            + matches.size()
                            + "; "
                            + describeScene());
        return matches.get(0);
    }

    private static List<Button> buttons(String prefix) {
        return nodes().stream()
                .filter(
                        n ->
                                n instanceof Button b
                                        && b.isVisible()
                                        && buttonCaption(b.getText()).startsWith(buttonCaption(prefix)))
                .map(n -> (Button) n)
                .toList();
    }

    private static String buttonCaption(String value) {
        return value.replaceFirst("^[^\\p{L}\\p{N}?]+", "").stripLeading();
    }

    private static TextField textField(String prompt) {
        return nodes().stream()
                .filter(
                        n ->
                                n instanceof TextField field
                                        && !(field instanceof PasswordField)
                                        && field.getPromptText().equals(prompt))
                .map(n -> (TextField) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing field: " + prompt));
    }

    private static boolean hasTextField(String prompt) {
        return nodes().stream()
                .anyMatch(
                        n ->
                                n instanceof TextField field
                                        && !(field instanceof PasswordField)
                                        && field.getPromptText().equals(prompt));
    }

    private static PasswordField passwordField(String prompt) {
        return nodes().stream()
                .filter(
                        n ->
                                n instanceof PasswordField field
                                        && field.getPromptText().equals(prompt))
                .map(n -> (PasswordField) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing password field: " + prompt));
    }

    private static TableView<?> firstTable() {
        return nodes().stream()
                .filter(n -> n instanceof TableView<?>)
                .map(n -> (TableView<?>) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Screen has no table; " + describeScene()));
    }

    private static List<Node> nodes() {
        Parent root = window.getScene().getRoot();
        root.applyCss();
        root.layout();
        return nodes(root);
    }

    private static List<Node> nodes(Node root) {
        List<Node> result = new ArrayList<>();
        collect(root, result, Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }

    private static void collect(Node node, List<Node> nodes, Set<Node> visited) {
        if (!visited.add(node)) return;
        nodes.add(node);
        // A freshly replaced ScrollPane content exists before its skin has
        // attached it. Traverse the public content API as well as visual children.
        if (node instanceof ScrollPane scroll && scroll.getContent() != null)
            collect(scroll.getContent(), nodes, visited);
        if (node instanceof TabPane tabs && tabs.getSelectionModel().getSelectedItem() != null)
            collect(tabs.getSelectionModel().getSelectedItem().getContent(), nodes, visited);
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) collect(child, nodes, visited);
    }

    private static String describeScene() {
        return "Buttons="
                + nodes().stream()
                        .filter(n -> n instanceof Button)
                        .map(n -> ((Button) n).getText())
                        .toList()
                + "; labels="
                + nodes().stream()
                        .filter(n -> n instanceof Label)
                        .map(n -> ((Label) n).getText())
                        .toList();
    }

    private static void screenshot(String filename) throws Exception {
        screenshotWindow(filename, window);
    }

    private static void screenshotWindow(String filename, Window target) throws Exception {
        fx(
                () -> {
                    Scene scene = target.getScene();
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    WritableImage image = scene.snapshot(null);
                    BufferedImage png =
                            new BufferedImage(
                                    (int) image.getWidth(),
                                    (int) image.getHeight(),
                                    BufferedImage.TYPE_INT_ARGB);
                    PixelReader pixels = image.getPixelReader();
                    for (int y = 0; y < png.getHeight(); y++)
                        for (int x = 0; x < png.getWidth(); x++)
                            png.setRGB(x, y, pixels.getArgb(x, y));
                    Files.createDirectories(Path.of("target"));
                    check(
                            ImageIO.write(png, "png", Path.of("target", filename).toFile()),
                            "Screenshot written: " + filename);
                    return null;
                });
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    private static <T> T engine(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        server.submit(task);
        return task.get(5, TimeUnit.SECONDS);
    }

    private static void await(String description, CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            if (failure.get() != null)
                throw new AssertionError("Uncaught application error", failure.get());
            if (condition.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError(
                "Timed out waiting for " + description + "; " + fx(ClientTest::describeScene));
    }

    private static void check(boolean condition, String description) {
        checks.incrementAndGet();
        if (!condition) throw new AssertionError(description);
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean get() throws Exception;
    }

    /** Reads a genuine second client independently of the JavaFX application. */
    private static final class Peer implements Closeable {
        final Socket socket;
        final BufferedWriter output;
        final LinkedBlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        volatile Map<String, Object> state;

        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setTcpNoDelay(true);
            output =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8));
            Thread reader =
                    new Thread(
                            () -> {
                                try (BufferedReader input =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        socket.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    for (String line; (line = Protocol.readLine(input)) != null; ) {
                                        Map<String, Object> event = map(parse(line));
                                        if (str(event, "type").equals("GAME_STATE")) state = event;
                                        inbox.add(event);
                                    }
                                } catch (Exception error) {
                                    if (!socket.isClosed()) failure.compareAndSet(null, error);
                                }
                            },
                            "ui-test-tcp-peer");
            reader.setDaemon(true);
            reader.start();
        }

        void send(String type, Object... pairs) throws Exception {
            Map<String, Object> request = obj(pairs);
            request.put("type", type);
            output.write(json(request));
            output.newLine();
            output.flush();
        }

        Map<String, Object> await(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                Map<String, Object> message = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && str(message, "type").equals(type)) return message;
            }
            throw new AssertionError("Second TCP client timed out waiting for " + type);
        }

        Map<String, Object> player(int id) {
            Map<String, Object> latest = state;
            if (latest != null)
                for (Object value : list(latest.get("players")))
                    if (integer(map(value), "id") == id) return map(value);
            return Map.of();
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}

package Client.fx;

import Client.session.GameClientSession;
import Client.Constants;
import Client.fx.util.SoundManager;
import Client.network.TcpGameClient;
import Server.network.ProtocolMessage;
import javafx.application.Application;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/** JavaFX entry point used while the Swing client is being migrated. */
public final class FxClientMain extends Application {
    private static volatile boolean active;
    private static String snapshotPath;
    private static String snapshotScreen = "main";
    private static String snapshotLogin;
    private static String snapshotOpponent;
    @Override
    public void start(Stage stage) {
        active = true;
        FxScreenNavigator navigator = new FxScreenNavigator(stage);
        switch (snapshotScreen) {
            case "login" -> navigator.showLogin();
            case "register" -> navigator.showRegister();
            case "lobby", "profile", "history", "ranking", "admin" -> {
                if (snapshotLogin == null || !snapshotLogin.contains(":")) { navigator.showMain(); break; }
                String[] credentials = snapshotLogin.split(":", 2);
                Thread.ofVirtual().start(() -> {
                    try {
                        GameClientSession.instance().login(credentials[0], credentials[1]);
                        javafx.application.Platform.runLater(() -> {
                            // A reconnect snapshot is routed by
                            // GameClientSession after MATCH_PREPARE.  Do not
                            // replace that game with the lobby when the login
                            // callback completes.
                            if (GameClientSession.instance().isRejoiningMatch()) {
                                scheduleSnapshot(stage);
                                return;
                            }
                            switch (snapshotScreen) {
                                case "profile" -> navigator.showProfile();
                                case "history" -> navigator.showHistory();
                                case "ranking" -> navigator.showRanking();
                                case "admin" -> navigator.showAdmin();
                                default -> navigator.showLobby();
                            }
                            scheduleSnapshot(stage);
                        });
                    } catch (Exception exception) { exception.printStackTrace(); javafx.application.Platform.exit(); }
                });
                return;
            }
            case "game" -> {
                if (snapshotLogin == null || snapshotOpponent == null) { navigator.showMain(); break; }
                autoMatchSnapshot(navigator, stage);
                return;
            }
            case "preview-game" -> { navigator.showGamePreview(); scheduleSnapshot(stage); return; }
            default -> navigator.showMain();
        }
        if (snapshotPath != null) scheduleSnapshot(stage);
    }

    private static void autoMatchSnapshot(FxScreenNavigator navigator, Stage stage) {
        String[] opponent = snapshotOpponent.split(":", 3);
        if (opponent.length < 3) { navigator.showMain(); return; }
        long opponentId = Long.parseLong(opponent[2]);
        TcpGameClient opponentClient = new TcpGameClient();
        opponentClient.addListener(new TcpGameClient.MessageListener() {
            @Override public void onMessage(ProtocolMessage message) {
                try {
                    if (message.type().equals("INVITE_NOTIFICATION"))
                        opponentClient.send(new ProtocolMessage("INVITE_ACCEPT", Map.of("inviteId", message.fields().get("inviteId"))));
                    else if (message.type().equals("MATCH_PREPARE_END"))
                        opponentClient.send(new ProtocolMessage("MATCH_READY", Map.of("matchId", message.fields().get("matchId"))));
                } catch (IOException ignored) { }
            }
        });
        Thread.ofVirtual().start(() -> {
        try {
            String[] local = snapshotLogin.split(":", 2);
            GameClientSession.instance().login(local[0], local[1]);
            GameClientSession.instance().client().addListener(new TcpGameClient.MessageListener() {
                @Override public void onMessage(ProtocolMessage message) {
                    try {
                        if (message.type().equals("ROOM_CREATED"))
                            GameClientSession.instance().client().send(new ProtocolMessage("INVITE_PLAYER", Map.of("roomId", message.fields().get("roomId"), "playerId", Long.toString(opponentId))));
                        else if (message.type().equals("ROOM_UPDATED") && message.fields().getOrDefault("guestPlayerId", "").equals(Long.toString(opponentId)))
                            GameClientSession.instance().client().send(new ProtocolMessage("START_MATCH", Map.of("roomId", message.fields().get("roomId"))));
                        else if (message.type().equals("MATCH_PREPARE"))
                            javafx.application.Platform.runLater(() -> { navigator.showGame(message); scheduleSnapshot(stage); });
                    } catch (IOException ignored) { }
                }
            });
            opponentClient.connect(Constants.IP_SERVER, Constants.PORT);
            opponentClient.send(new ProtocolMessage("LOGIN", Map.of("username", opponent[0], "password", opponent[1])));
            javafx.application.Platform.runLater(() -> navigator.showLobby());
            GameClientSession.instance().client().send(new ProtocolMessage("CREATE_ROOM", Map.of("modeCode", "CLASSIC")));
        } catch (Exception exception) { exception.printStackTrace(); javafx.application.Platform.exit(); }
        });
    }

    private static void scheduleSnapshot(Stage stage) {
        if (snapshotPath != null) {
            stage.setWidth(snapshotPath.contains("810") ? 810 : 1080);
            stage.setHeight(snapshotPath.contains("540") ? 540 : 760);
            PauseTransition pause = new PauseTransition(Duration.millis(700));
            pause.setOnFinished(event -> {
                try {
                    int sceneWidth = (int) stage.getScene().getWidth();
                    int sceneHeight = (int) stage.getScene().getHeight();
                    WritableImage image = new WritableImage(sceneWidth, sceneHeight);
                    stage.getScene().snapshot(result -> {
                        try {
                            BufferedImage output = new BufferedImage(sceneWidth, sceneHeight, BufferedImage.TYPE_INT_ARGB);
                            javafx.scene.image.PixelReader reader = image.getPixelReader();
                            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, reader.getArgb(x, y));
                            ImageIO.write(output, "png", new File(snapshotPath));
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        } finally {
                            javafx.application.Platform.exit();
                        }
                        return null;
                    }, image);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            });
            pause.play();
        }
    }

    public static boolean isActive() { return active; }

    @Override
    public void stop() {
        SoundManager.getInstance().stopAll();
    }

    public static void main(String[] args) {
        for (String arg : args) if (arg.startsWith("--snapshot=")) snapshotPath = arg.substring("--snapshot=".length());
        for (String arg : args) if (arg.startsWith("--snapshot-screen=")) snapshotScreen = arg.substring("--snapshot-screen=".length());
        for (String arg : args) if (arg.startsWith("--snapshot-login=")) snapshotLogin = arg.substring("--snapshot-login=".length());
        for (String arg : args) if (arg.startsWith("--snapshot-opponent=")) snapshotOpponent = arg.substring("--snapshot-opponent=".length());
        launch(args);
    }
}

package Client.session;

import Client.Constants;
import Client.network.TcpGameClient;
import Server.network.ProtocolMessage;
import Client.fx.FxClientMain;
import Client.fx.FxScreenNavigator;
import javafx.application.Platform;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Process-wide authenticated TCP session owned by the desktop client.
 */
public final class GameClientSession implements TcpGameClient.MessageListener, AutoCloseable {
    private static final GameClientSession INSTANCE = new GameClientSession();
    private final TcpGameClient client = new TcpGameClient();
    private volatile CompletableFuture<Identity> pendingLogin;
    private volatile Identity identity;
    /** True from MATCH_REJOIN until the restored game/result has been shown. */
    private volatile boolean rejoiningMatch;
    private final Object rejoinLock = new Object();
    private final List<ProtocolMessage> rejoinSnapshot = new ArrayList<>();
    private boolean rejoinOpenScheduled;

    private GameClientSession() {
        client.addListener(this);
    }

    public static GameClientSession instance() {
        return INSTANCE;
    }

    public TcpGameClient client() {
        return client;
    }

    public Identity identity() {
        if (identity == null) throw new IllegalStateException("Not logged in");
        return identity;
    }

    public boolean isAuthenticated() {
        return identity != null && client.isConnected();
    }

    public boolean isRejoiningMatch() {
        return rejoiningMatch;
    }

    public synchronized Identity login(String username, String password) throws Exception {
        if (client.isConnected()) close();
        CompletableFuture<Identity> future = new CompletableFuture<>();
        pendingLogin = future;
        try {
            IOException lastConnectionError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    client.connect(Constants.IP_SERVER, Constants.PORT);
                    lastConnectionError = null;
                    break;
                } catch (IOException error) {
                    lastConnectionError = error;
                    client.close();
                    if (attempt < 2) Thread.sleep(250);
                }
            }
            if (lastConnectionError != null) throw lastConnectionError;
            client.send(new ProtocolMessage("LOGIN", Map.of("username", username, "password", password)));
            return future.get(10, TimeUnit.SECONDS);
        } finally {
            pendingLogin = null;
        }
    }

    public void logout() throws IOException {
        if (client.isConnected()) client.send(ProtocolMessage.of("LOGOUT"));
        identity = null;
    }

    @Override
    public void onMessage(ProtocolMessage message) {
        if (message.type().equals("MATCH_REJOIN") && identity != null) {
            // MATCH_REJOIN is only a notification.  The actual game snapshot
            // (MATCH_PREPARE, FRUIT_CONFIG, BASKET_CONFIG and FRUIT_SPAWN)
            // follows immediately afterwards.  Do not construct RunGame from
            // this message: doing so races the snapshot and leaves matchId or
            // basketId at their default values.
            synchronized (rejoinLock) {
                rejoinSnapshot.clear();
                rejoinSnapshot.add(message);
                rejoiningMatch = true;
                rejoinOpenScheduled = false;
            }
            return;
        }
        if (message.type().equals("MATCH_PREPARE") && rejoiningMatch && identity != null) {
            synchronized (rejoinLock) {
                rejoinSnapshot.add(message);
            }
            return;
        }
        if (rejoiningMatch && isRejoinSnapshotMessage(message)) {
            boolean openNow = false;
            synchronized (rejoinLock) {
                rejoinSnapshot.add(message);
                if (message.type().equals("MATCH_PREPARE_END") && !rejoinOpenScheduled) {
                    rejoinOpenScheduled = true;
                    openNow = true;
                }
            }
            if (!openNow) return;
            Runnable open = () -> {
                List<ProtocolMessage> snapshot;
                synchronized (rejoinLock) {
                    snapshot = List.copyOf(rejoinSnapshot);
                    rejoinOpenScheduled = false;
                }
                if (!FxClientMain.isActive()) return;
                FxScreenNavigator navigator = FxScreenNavigator.current();
                if (navigator == null || snapshot.size() < 2) return;
                navigator.showGame(snapshot.get(1));
                if (navigator.currentGame() != null) {
                    for (ProtocolMessage item : snapshot.subList(2, snapshot.size()))
                        navigator.currentGame().onMessage(item);
                    synchronized (rejoinLock) {
                        List<ProtocolMessage> late = new ArrayList<>(rejoinSnapshot.subList(snapshot.size(), rejoinSnapshot.size()));
                        rejoiningMatch = false;
                        for (ProtocolMessage item : late) navigator.currentGame().onMessage(item);
                    }
                }
            };
            Platform.runLater(open);
            return;
        }
        if (message.type().equals("MATCH_RESULT") && rejoiningMatch && identity != null) {
            rejoiningMatch = false;
            if (FxClientMain.isActive()) Platform.runLater(() -> {
                FxScreenNavigator navigator = FxScreenNavigator.current();
                if (navigator != null) navigator.showResult(message);
            });
            return;
        }
        CompletableFuture<Identity> future = pendingLogin;
        if (future == null) return;
        if (message.type().equals("LOGIN_SUCCESS")) {
            Identity signedIn = new Identity(Long.parseLong(message.fields().get("playerId")), message.fields().get("displayName"), message.fields().get("role"));
            identity = signedIn;
            future.complete(signedIn);
        } else if (message.type().equals("LOGIN_FAILED") || message.type().equals("ERROR")) {
            future.completeExceptionally(new IOException(message.fields().getOrDefault("reason", message.fields().getOrDefault("message", "Login failed"))));
        }
    }

    private boolean isRejoinSnapshotMessage(ProtocolMessage message) {
        return switch (message.type()) {
            case "FRUIT_CONFIG", "BASKET_CONFIG", "FRUIT_SPAWN", "MATCH_PREPARE_END", "MATCH_START", "SCORE_UPDATE" -> true;
            default -> false;
        };
    }

    @Override
    public void onConnectionError(Exception exception) {
        CompletableFuture<Identity> future = pendingLogin;
        if (future != null) future.completeExceptionally(exception);
    }

    @Override
    public synchronized void close() {
        identity = null;
        client.close();
    }

    public record Identity(long playerId, String displayName, String role) {
    }
}

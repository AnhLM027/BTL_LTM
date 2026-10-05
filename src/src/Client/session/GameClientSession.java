package Client.session;

import Client.Constants;
import Client.network.TcpGameClient;
import Server.network.ProtocolMessage;
import Client.view.RunGame;

import javax.swing.SwingUtilities;

import java.io.IOException;
import java.util.Map;
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
            Identity currentIdentity = identity;
            runOnEventThreadAndWait(() -> {
                RunGame game = new RunGame(client, currentIdentity.playerId());
                game.onMessage(message);
                game.setVisible(true);
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

    private void runOnEventThreadAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (Exception exception) {
            onConnectionError(new IOException("Cannot restore the match UI", exception));
        }
    }

    public record Identity(long playerId, String displayName, String role) {
    }
}

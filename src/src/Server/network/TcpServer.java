package Server.network;

import Server.util.ServerLog;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TcpServer implements AutoCloseable {
    private final int port;
    private final ServerEventHandler eventHandler;
    private final SessionRegistry sessionRegistry;
    private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;

    public TcpServer(int port, ServerEventHandler eventHandler) {
        this(port, eventHandler, new SessionRegistry());
    }

    public TcpServer(int port, ServerEventHandler eventHandler, SessionRegistry sessionRegistry) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port;
        this.eventHandler = Objects.requireNonNull(eventHandler, "eventHandler");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry");
    }

    public SessionRegistry sessions() {
        return sessionRegistry;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("TCP server is already running");
        }

        try (ServerSocket listeningSocket = new ServerSocket(port)) {
            serverSocket = listeningSocket;
            ServerLog.info("TCP server is listening on port " + port);
            while (running.get()) {
                Socket socket = listeningSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                ServerLog.info("Client connected: " + socket.getRemoteSocketAddress());
                clientExecutor.submit(() -> handleConnection(socket));
            }
        } finally {
            running.set(false);
            serverSocket = null;
            ServerLog.info("TCP server stopped");
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try {
            session = new ClientSession(socket);
            sessionRegistry.add(session);
            eventHandler.onConnected(session);

            String line;
            while ((line = session.readLine()) != null) {
                try {
                    eventHandler.onMessage(session, MessageCodec.decode(line));
                } catch (ProtocolException exception) {
                    ServerLog.warning("Invalid protocol message from session " + session.sessionId() + ": " + exception.getMessage());
                    session.send(ProtocolMessage.error("INVALID_MESSAGE", exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            // A client disconnect commonly reaches this branch; the session is still cleaned up below.
            if (session != null && session.isOpen()) {
                ServerLog.warning("Connection error for session " + session.sessionId() + ": " + exception.getMessage());
            }
        } finally {
            if (session != null) {
                ServerLog.info("Client disconnected: session=" + session.sessionId() + ", player=" + session.playerId());
                sessionRegistry.remove(session);
                session.close();
                eventHandler.onDisconnected(session);
            } else {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Best effort when a session cannot be constructed.
                }
            }
        }
    }

    @Override
    public void close() {
        ServerLog.info("Stopping TCP server");
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Best effort during shutdown.
            }
        }
        for (ClientSession session : sessionRegistry.sessions()) {
            session.close();
        }
        clientExecutor.close();
    }
}

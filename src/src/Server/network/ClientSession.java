package Server.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import Server.model.enums.AccountRole;

public final class ClientSession implements AutoCloseable {
    private final String sessionId = UUID.randomUUID().toString();
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Long playerId;
    private volatile AccountRole role;

    ClientSession(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String sessionId() {
        return sessionId;
    }

    public Long playerId() {
        return playerId;
    }

    void bindPlayer(long playerId) {
        this.playerId = playerId;
    }

    void bindRole(AccountRole role) {
        this.role = role;
    }

    public AccountRole role() {
        return role;
    }

    void clearPlayer() {
        this.playerId = null;
        this.role = null;
    }

    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    public boolean isOpen() {
        return !closed.get() && !socket.isClosed();
    }

    String readLine() throws IOException {
        return reader.readLine();
    }

    public synchronized void send(ProtocolMessage message) throws IOException {
        if (!isOpen()) {
            throw new IOException("Session is closed");
        }
        writer.write(MessageCodec.encode(message));
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Connection teardown is best effort.
            }
        }
    }
}

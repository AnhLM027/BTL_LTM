package Client.network;

import Server.network.MessageCodec;
import Server.network.ProtocolException;
import Server.network.ProtocolMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One persistent TCP connection for a logged-in Swing client.
 * Network callbacks run off the EDT; views must marshal UI changes to the EDT.
 */
public final class TcpGameClient implements AutoCloseable {
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(true);
    /** Identifies the active socket so an old reader cannot close a new connection. */
    private final AtomicLong connectionId = new AtomicLong();
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    public synchronized void connect(String host, int port) throws IOException {
        if (!closed.get()) throw new IllegalStateException("TCP client is already connected");
        Socket nextSocket = new Socket(Objects.requireNonNull(host, "host"), port);
        nextSocket.setTcpNoDelay(true);
        nextSocket.setKeepAlive(true);
        BufferedReader nextReader = new BufferedReader(
                new InputStreamReader(nextSocket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter nextWriter = new BufferedWriter(
                new OutputStreamWriter(nextSocket.getOutputStream(), StandardCharsets.UTF_8));
        long currentConnection = connectionId.incrementAndGet();
        socket = nextSocket;
        reader = nextReader;
        writer = nextWriter;
        closed.set(false);
        readerThread = Thread.ofVirtual().name("fruit-battle-tcp-reader")
                .start(() -> readLoop(currentConnection, nextSocket, nextReader));
    }

    public void addListener(MessageListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    public synchronized void send(ProtocolMessage message) throws IOException {
        if (closed.get()) throw new IOException("TCP client is not connected");
        writer.write(MessageCodec.encode(message));
        writer.newLine();
        writer.flush();
    }

    public boolean isConnected() {
        return !closed.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void readLoop(long currentConnection, Socket sourceSocket, BufferedReader sourceReader) {
        try {
            String line;
            while (!closed.get() && connectionId.get() == currentConnection
                    && (line = sourceReader.readLine()) != null) {
                if (closed.get() || connectionId.get() != currentConnection) break;
                try {
                    publish(MessageCodec.decode(line));
                } catch (ProtocolException exception) {
                    publishError(exception);
                }
            }
        } catch (IOException exception) {
            if (!closed.get() && connectionId.get() == currentConnection) publishError(exception);
        } finally {
            finishConnection(currentConnection, sourceSocket);
        }
    }

    private synchronized void finishConnection(long currentConnection, Socket sourceSocket) {
        if (connectionId.get() != currentConnection) return;
        closed.set(true);
        if (socket == sourceSocket) {
            socket = null;
            reader = null;
            writer = null;
        }
        if (readerThread == Thread.currentThread()) readerThread = null;
        try {
            sourceSocket.close();
        } catch (IOException ignored) {
            // The remote side may have already closed the socket.
        }
    }

    private void publish(ProtocolMessage message) {
        for (MessageListener listener : listeners) listener.onMessage(message);
    }

    private void publishError(Exception exception) {
        for (MessageListener listener : listeners) listener.onConnectionError(exception);
    }

    @Override
    public void close() {
        Socket currentSocket;
        synchronized (this) {
            connectionId.incrementAndGet();
            closed.set(true);
            currentSocket = socket;
            socket = null;
            reader = null;
            writer = null;
            readerThread = null;
        }
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException ignored) {
                // Teardown is best effort.
            }
        }
    }

    public interface MessageListener {
        void onMessage(ProtocolMessage message);

        default void onConnectionError(Exception exception) {
        }
    }
}

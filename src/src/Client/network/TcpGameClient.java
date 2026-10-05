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

/**
 * One persistent TCP connection for a logged-in Swing client.
 * Network callbacks run off the EDT; views must marshal UI changes to the EDT.
 */
public final class TcpGameClient implements AutoCloseable {
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(true);
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread readerThread;

    public synchronized void connect(String host, int port) throws IOException {
        if (!closed.get()) throw new IllegalStateException("TCP client is already connected");
        socket = new Socket(Objects.requireNonNull(host, "host"), port);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        closed.set(false);
        readerThread = Thread.ofVirtual().name("fruit-battle-tcp-reader").start(this::readLoop);
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

    private void readLoop() {
        try {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                try {
                    publish(MessageCodec.decode(line));
                } catch (ProtocolException exception) {
                    publishError(exception);
                }
            }
        } catch (IOException exception) {
            if (!closed.get()) publishError(exception);
        } finally {
            close();
        }
    }

    private void publish(ProtocolMessage message) {
        for (MessageListener listener : listeners) listener.onMessage(message);
    }

    private void publishError(Exception exception) {
        for (MessageListener listener : listeners) listener.onConnectionError(exception);
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true) && socket != null) {
            try {
                socket.close();
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

package fruit.client.infrastructure.network;

import static fruit.common.MessageData.asMap;
import static fruit.common.Protocol.json;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.parse;
import static fruit.common.Protocol.readLine;

import fruit.client.application.ClientState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Ordered TCP transport, connection generations and background reader/writer executors. */
public final class ClientConnection implements AutoCloseable {
    private final ExecutorService io =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "fruit-tcp");
                        t.setDaemon(true);
                        return t;
                    });
    private final ExecutorService writes =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "fruit-send");
                        t.setDaemon(true);
                        return t;
                    });
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private final ClientState model;
    private final Consumer<Runnable> dispatch;
    private final Runnable updateConnection;
    private final Consumer<Map<String, Object>> receive;
    private final Consumer<Exception> failed;

    public ClientConnection(
            ClientState model,
            Consumer<Runnable> dispatch,
            Runnable updateConnection,
            Consumer<Map<String, Object>> receive,
            Consumer<Exception> failed) {
        this.model = model;
        this.dispatch = dispatch;
        this.updateConnection = updateConnection;
        this.receive = receive;
        this.failed = failed;
    }

    public void connect(Map<String, Object> authentication) {
        // Mỗi kết nối có một số thứ tự để bỏ qua sự kiện từ socket cũ.
        int ticket = generation.incrementAndGet();
        closeSocket();
        model.connecting = true;
        model.connected = false;
        updateConnection.run();
        String address = model.host;
        int serverPort = model.port;
        io.execute(
                () -> {
                    Socket candidate = new Socket();
                    try {
                        candidate.connect(new InetSocketAddress(address, serverPort), 4000);
                        candidate.setTcpNoDelay(true);
                        candidate.setKeepAlive(true);
                        BufferedWriter output =
                                new BufferedWriter(
                                        new OutputStreamWriter(
                                                candidate.getOutputStream(),
                                                StandardCharsets.UTF_8));
                        BufferedReader input =
                                new BufferedReader(
                                        new InputStreamReader(
                                                candidate.getInputStream(),
                                                StandardCharsets.UTF_8));
                        synchronized (this) {
                            if (generation.get() != ticket) {
                                candidate.close();
                                return;
                            }
                            socket = candidate;
                            writer = output;
                        }
                        output.write(json(authentication));
                        output.newLine();
                        output.flush();
                        dispatch.accept(
                                () -> {
                                    if (generation.get() == ticket) {
                                        model.connecting = false;
                                        model.connected = true;
                                        updateConnection.run();
                                    }
                                });
                        String line;
                        while ((line = readLine(input)) != null) {
                            if (generation.get() != ticket) return;
                            Object parsed = parse(line);
                            if (!(parsed instanceof Map<?, ?>))
                                throw new IOException("Message không hợp lệ");
                            Map<String, Object> event = asMap(parsed);
                            dispatch.accept(
                                    () -> {
                                        if (generation.get() == ticket) receive.accept(event);
                                    });
                        }
                        throw new EOFException("Máy chủ đã đóng kết nối");
                    } catch (Exception ex) {
                        try {
                            candidate.close();
                        } catch (IOException ignored) {
                        }
                        dispatch.accept(
                                () -> {
                                    if (generation.get() == ticket) failed.accept(ex);
                                });
                    }
                });
    }

    public void send(Map<String, Object> request) {
        int ticket = generation.get();
        BufferedWriter output = writer;
        Socket requestSocket = socket;
        // Một luồng ghi duy nhất giữ nguyên thứ tự các thao tác bàn phím.
        writes.execute(
                () -> {
                    if (ticket != generation.get() || output == null) return;
                    try {
                        output.write(json(request));
                        output.newLine();
                        output.flush();
                    } catch (IOException ex) {
                        try {
                            if (requestSocket != null) requestSocket.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
    }

    public synchronized void closeSocket() {
        Socket old = socket;
        socket = null;
        writer = null;
        if (old != null) {
            try {
                old.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void logout() {
        Socket previousSocket = socket;
        BufferedWriter previousWriter = writer;
        generation.incrementAndGet();
        socket = null;
        writer = null;
        // Gửi LOGOUT trên luồng ghi rồi đóng đúng socket cũ.
        if (previousSocket != null)
            writes.execute(
                    () -> {
                        try {
                            if (previousWriter != null) {
                                previousWriter.write(json(obj("type", "LOGOUT")));
                                previousWriter.newLine();
                                previousWriter.flush();
                            }
                        } catch (IOException ignored) {
                        } finally {
                            try {
                                previousSocket.close();
                            } catch (IOException ignored) {
                            }
                        }
                    });
    }

    public void disconnect() {
        generation.incrementAndGet();
        closeSocket();
    }

    public void close() {
        disconnect();
        io.shutdownNow();
        writes.shutdownNow();
    }
}

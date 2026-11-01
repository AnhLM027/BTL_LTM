package fruit.server.infrastructure.network;

import static fruit.common.Protocol.json;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.parse;
import static fruit.common.Protocol.readLine;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Session {
    private final GameServer server;

    public final Socket socket;
    public final BufferedReader input;
    public final BufferedWriter output;
    public final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(128);
    public final AtomicBoolean closed = new AtomicBoolean();
    public User user;
    public int authAttempts;
    public long rateStart = System.nanoTime();
    public int rateCount;

    public Session(GameServer server, Socket socket) throws IOException {
        this.server = server;

        this.socket = socket;
        input =
                new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        output =
                new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public void send(Map<String, Object> message) {
        if (!closed.get() && !outgoing.offer(json(message))) close();
    }

    public void error(String message) {
        send(obj("type", "ERROR", "message", message));
    }

    public void authFailure(String message) {
        send(obj("type", "AUTH", "ok", false, "message", message));
    }

    public void readLoop() {
        try {
            for (String line; !closed.get() && (line = readLine(input)) != null; ) {
                long now = System.nanoTime();
                if (now - rateStart > 1_000_000_000L) {
                    rateStart = now;
                    rateCount = 0;
                }
                if (++rateCount > 150) {
                    error("Gửi yêu cầu quá nhanh.");
                    break;
                }
                Object parsed;
                try {
                    parsed = parse(line);
                } catch (IllegalArgumentException e) {
                    error("JSON không hợp lệ.");
                    continue;
                }
                if (!(parsed instanceof Map<?, ?>)) {
                    error("Yêu cầu phải là một JSON object.");
                    continue;
                }
                Map<String, Object> message = map(parsed);
                server.submit(() -> server.request(this, message));
            }
        } catch (IOException ignored) {
            // EOF and socket failure both follow the same disconnect rules.
        } finally {
            close();
        }
    }

    public void writeLoop() {
        try {
            while (!closed.get()) {
                String message = outgoing.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    output.write(message);
                    output.newLine();
                    output.flush();
                }
            }
        } catch (IOException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        server.connections.remove(this);
        server.submit(() -> server.detach(this));
    }
}

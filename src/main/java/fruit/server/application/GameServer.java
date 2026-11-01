package fruit.server.application;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.features.accounts.AccountService;
import fruit.server.features.accounts.User;
import fruit.server.features.admin.AdminService;
import fruit.server.features.matches.Match;
import fruit.server.features.matchmaking.MatchmakingService;
import fruit.server.features.statistics.StatisticsService;
import fruit.server.infrastructure.network.Session;
import fruit.server.infrastructure.persistence.Store;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authoritative account, matchmaking and engine coordinator. Display output is injected. */
public final class GameServer implements AutoCloseable {
    public static final String[] FRUITS = {"APPLE", "BANANA", "GRAPE"};
    public static final double BASKET_SPEED = 520;
    public static final double BASKET_Y = 550;
    public static final double BASKET_WIDTH = 100;
    public static final double OBJECT_RADIUS = 18;
    private final AccountService accounts = new AccountService(this);
    private final StatisticsService statistics = new StatisticsService(this);
    private final AdminService admin = new AdminService(this);
    private final MatchmakingService matchmaking = new MatchmakingService(this);
    public final Store store;
    public final Map<Integer, Session> online = new HashMap<>();
    public final LinkedHashSet<Integer> queue = new LinkedHashSet<>();
    public final Map<Integer, Match> activeByUser = new HashMap<>();
    public final LinkedHashSet<Match> matches = new LinkedHashSet<>();
    public final Map<Integer, Map<String, Object>> pendingResults = new HashMap<>();
    public final Map<String, Map<String, Object>> unsavedMatches = new LinkedHashMap<>();
    public final Set<Session> connections = ConcurrentHashMap.newKeySet();
    public final ScheduledExecutorService engine =
            Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "fruit-engine"));
    public final ExecutorService sockets =
            Executors.newCachedThreadPool(r -> daemon(r, "fruit-socket"));
    public final ServerSocket listener;
    public final SecureRandom secure = new SecureRandom();
    public final AtomicBoolean closed = new AtomicBoolean();
    public volatile boolean running;
    public long lastTick;
    public long retrySaveAt;
    public final ServerDisplay display;

    public GameServer(int port, Path dataDir, boolean memoryOnly) throws Exception {
        this(port, dataDir, memoryOnly, false, ServerDisplay.quiet());
    }

    public GameServer(
            int port, Path dataDir, boolean memoryOnly, boolean localOnly, ServerDisplay display)
            throws Exception {
        this.display = Objects.requireNonNull(display);
        listener = new ServerSocket();
        Store openedStore = null;
        try {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(port));
            // Reserve the port before opening storage or creating initial accounts.
            store = new Store(dataDir, memoryOnly, localOnly);
            openedStore = store;
            bootstrapAdmin();
        } catch (Exception e) {
            if (openedStore != null) openedStore.close();
            try {
                listener.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            engine.shutdownNow();
            sockets.shutdownNow();
            throw e;
        }
    }

    public static Thread daemon(Runnable work, String name) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        return thread;
    }

    public int port() {
        return listener.getLocalPort();
    }

    public synchronized void start() {
        if (running || closed.get()) return;
        running = true;
        lastTick = System.nanoTime();
        engine.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
        sockets.execute(
                () -> {
                    while (running) {
                        try {
                            Socket socket = listener.accept();
                            socket.setTcpNoDelay(true);
                            socket.setKeepAlive(true);
                            if (connections.size() >= 500) {
                                socket.close();
                                continue;
                            }
                            Session session = new Session(this, socket);
                            connections.add(session);
                            sockets.execute(session::writeLoop);
                            sockets.execute(session::readLoop);
                        } catch (IOException e) {
                            if (running) display.warning("TCP accept: " + e.getMessage());
                        }
                    }
                });
        display.info("Fruit Battle TCP listening on port " + port() + " | storage: " + store.mode);
    }

    public void submit(Runnable action) {
        if (!closed.get()) {
            try {
                engine.execute(action);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }

    public void tick() {
        try {
            long now = System.nanoTime();
            double dt = (now - lastTick) / 1_000_000_000.0;
            lastTick = now;
            for (Match match : new ArrayList<>(matches)) {
                match.step(dt);
                if (!match.finished) match.broadcast(match.snapshot());
            }
            if (!unsavedMatches.isEmpty() && now >= retrySaveAt) retrySaves();
        } catch (Exception e) {
            // A malformed request or a failed persistence operation must not kill the game loop.
            display.warning("Engine error: " + e.getMessage());
            display.error(e);
        }
    }

    public void bootstrapAdmin() throws Exception {
        accounts.bootstrapAdmin();
    }

    public static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value;
    }

    public byte[] randomBytes(int count) {
        byte[] value = new byte[count];
        secure.nextBytes(value);
        return value;
    }

    public static boolean validUsername(String value) {
        return AccountService.validUsername(value);
    }

    public static boolean validPassword(String value) {
        return AccountService.validPassword(value);
    }

    public static boolean validDisplayName(String value) {
        return AccountService.validDisplayName(value);
    }

    public void request(Session session, Map<String, Object> request) {
        if (session.closed.get()) return;
        String type = str(request, "type");
        try {
            if (type.equals("PING")) {
                session.send(obj("type", "PONG"));
                return;
            }
            if (type.equals("LOGIN") || type.equals("REGISTER")) {
                authenticate(session, request, type);
                return;
            }
            User user = session.user;
            if (user == null) {
                session.error("Vui lòng đăng nhập trước.");
                return;
            }
            if (user.locked) {
                session.error("Tài khoản đã bị khóa.");
                session.close();
                return;
            }
            switch (type) {
                case "PROFILE_GET" ->
                        session.send(obj("type", "PROFILE", "ok", true, "user", user.view()));
                case "PROFILE_UPDATE" -> updateProfile(session, request);
                case "LEADERBOARD" ->
                        session.send(obj("type", "LEADERBOARD", "rows", leaderboard()));
                case "HISTORY" -> session.send(obj("type", "HISTORY", "rows", history(user.id)));
                case "ADMIN_GET" -> adminData(session, request);
                case "ADMIN_LOCK" -> adminLock(session, request);
                case "MATCH_FIND" -> findMatch(session);
                case "MATCH_CANCEL" -> cancelQueue(session);
                case "MATCH_REJOIN" -> rejoin(session, true);
                case "PLAYER_MOVE" -> {
                    Match match = activeByUser.get(user.id);
                    Object direction = request.get("direction");
                    if (!(direction instanceof Number n)
                            || n.doubleValue() != n.intValue()
                            || Math.abs(n.intValue()) > 1) {
                        session.error("Hướng di chuyển phải là -1, 0 hoặc 1.");
                    } else if (match != null) {
                        match.player(user.id).direction = n.intValue();
                    }
                }
                case "USE_SKILL" -> {
                    Match match = activeByUser.get(user.id);
                    if (match == null) session.error("Bạn chưa ở trong trận đấu.");
                    else match.skill(match.player(user.id), integer(request, "skill"));
                }
                case "LOGOUT" -> {
                    detach(session);
                    session.send(obj("type", "LOGGED_OUT"));
                }
                default -> session.error("Yêu cầu không được hỗ trợ: " + type);
            }
        } catch (Exception e) {
            display.warning("Request " + type + ": " + e.getMessage());
            session.error("Không thể lưu hoặc xử lý yêu cầu. Vui lòng thử lại.");
        }
    }

    public void authenticate(Session session, Map<String, Object> request, String type)
            throws Exception {
        accounts.authenticate(session, request, type);
    }

    public void updateProfile(Session session, Map<String, Object> request) throws Exception {
        accounts.updateProfile(session, request);
    }

    public List<Map<String, Object>> leaderboard() {
        return statistics.leaderboard();
    }

    public List<Map<String, Object>> history(int id) {
        return statistics.history(id);
    }

    public void adminData(Session session, Map<String, Object> request) {
        admin.adminData(session, request);
    }

    public boolean isAdmin(Session session) {
        return admin.isAdmin(session);
    }

    public void adminLock(Session session, Map<String, Object> request) throws Exception {
        admin.adminLock(session, request);
    }

    public void findMatch(Session session) {
        matchmaking.findMatch(session);
    }

    public void cancelQueue(Session session) {
        matchmaking.cancelQueue(session);
    }

    public void pairPlayers() {
        matchmaking.pairPlayers();
    }

    public void rejoin(Session session, boolean explicit) {
        matchmaking.rejoin(session, explicit);
    }

    public void detach(Session session) {
        matchmaking.detach(session);
    }

    public void retrySaves() {
        retrySaveAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Iterator<Map.Entry<String, Map<String, Object>>> iterator =
                unsavedMatches.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Map<String, Object> record = entry.getValue();
            List<Object> players = list(record.get("players"));
            int a = integer(map(players.get(0)), "id"), b = integer(map(players.get(1)), "id");
            try {
                store.saveMatch(record, store.users.get(a), store.users.get(b));
                iterator.remove();
                for (int id : new int[] {a, b}) {
                    Map<String, Object> pending = pendingResults.get(id);
                    if (pending != null && entry.getKey().equals(str(pending, "matchId")))
                        pending.put("saved", true);
                    Session session = online.get(id);
                    if (session != null)
                        session.send(
                                obj(
                                        "type",
                                        "SAVE_STATUS",
                                        "matchId",
                                        entry.getKey(),
                                        "saved",
                                        true));
                }
            } catch (Exception e) {
                display.warning("Match save retry pending: " + entry.getKey());
                break;
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        running = false;
        try {
            listener.close();
        } catch (IOException ignored) {
        }
        for (Session session : connections) session.close();
        engine.shutdown();
        try {
            if (!engine.awaitTermination(3, TimeUnit.SECONDS)) engine.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            engine.shutdownNow();
        }
        sockets.shutdownNow();
        store.close();
    }

    public GameServer(int port, Path dataDir, boolean memoryOnly, boolean localOnly)
            throws Exception {
        this(port, dataDir, memoryOnly, localOnly, ServerDisplay.quiet());
    }
}

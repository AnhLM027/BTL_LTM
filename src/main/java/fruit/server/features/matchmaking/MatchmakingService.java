package fruit.server.features.matchmaking;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.Match;
import fruit.server.features.matches.Player;
import fruit.server.infrastructure.network.Session;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Matchmaking feature handlers; executed only on the server engine thread. */
public final class MatchmakingService {
    private final GameServer server;

    public MatchmakingService(GameServer server) {
        this.server = server;
    }

    public void findMatch(Session session) {
        int id = session.user.id;
        if (server.activeByUser.containsKey(id)) {
            rejoin(session, false);
            return;
        }
        server.pendingResults.remove(id);
        server.queue.add(id);
        session.send(obj("type", "QUEUE", "queued", true));
        pairPlayers();
    }

    public void cancelQueue(Session session) {
        if (server.activeByUser.containsKey(session.user.id)) {
            session.error("Trận đấu đã được ghép.");
            return;
        }
        server.queue.remove(session.user.id);
        session.send(obj("type", "QUEUE", "queued", false));
    }

    public void pairPlayers() {
        server.queue.removeIf(
                id ->
                        !server.online.containsKey(id)
                                || server.online.get(id).closed.get()
                                || server.store.users.get(id).locked
                                || server.activeByUser.containsKey(id));
        while (server.queue.size() >= 2) {
            Iterator<Integer> iterator = server.queue.iterator();
            int a = iterator.next();
            iterator.remove();
            int b = iterator.next();
            iterator.remove();
            Match match = new Match(server, server.store.users.get(a), server.store.users.get(b));
            server.matches.add(match);
            server.activeByUser.put(a, match);
            server.activeByUser.put(b, match);
            for (Player player : match.players) {
                Session session = server.online.get(player.id);
                session.send(
                        obj(
                                "type",
                                "MATCH_FOUND",
                                "matchId",
                                match.id,
                                "opponent",
                                server.store.users.get(match.other(player.id).id).view(),
                                "countdown",
                                3));
            }
            match.broadcast(match.snapshot());
        }
    }

    public void rejoin(Session session, boolean explicit) {
        int id = session.user.id;
        Match match = server.activeByUser.get(id);
        if (match != null && !match.finished) {
            session.send(
                    obj(
                            "type",
                            "MATCH_FOUND",
                            "matchId",
                            match.id,
                            "opponent",
                            server.store.users.get(match.other(id).id).view(),
                            "countdown",
                            match.countdown,
                            "rejoin",
                            true));
            if (match.countdown <= 0)
                session.send(obj("type", "MATCH_START", "matchId", match.id, "rejoin", true));
            session.send(match.snapshot());
            return;
        }
        Map<String, Object> result = server.pendingResults.remove(id);
        if (result != null) session.send(result);
        else if (explicit) {
            List<Map<String, Object>> records = server.store.records;
            for (int i = records.size() - 1; i >= 0; i--) {
                Map<String, Object> record = records.get(i);
                if (list(record.get("players")).stream()
                        .anyMatch(p -> integer(map(p), "id") == id)) {
                    Map<String, Object> last = new LinkedHashMap<>(record);
                    last.put("type", "MATCH_RESULT");
                    last.put("saved", !server.unsavedMatches.containsKey(str(record, "matchId")));
                    session.send(last);
                    return;
                }
            }
            session.error("Không có trận đấu để khôi phục.");
        }
    }

    public void detach(Session session) {
        User user = session.user;
        if (user == null) return;
        if (server.online.get(user.id) == session) server.online.remove(user.id);
        session.user = null;
        server.queue.remove(user.id);
        Match match = server.activeByUser.get(user.id);
        if (match == null || match.finished) return;
        match.player(user.id).direction = 0;
        if (match.countdown > 0) {
            match.finished = true;
            server.matches.remove(match);
            for (Player player : match.players) server.activeByUser.remove(player.id);
            Player opponent = match.other(user.id);
            Session other = server.online.get(opponent.id);
            if (other != null && !other.closed.get()) {
                server.queue.add(opponent.id);
                other.send(
                        obj(
                                "type",
                                "QUEUE",
                                "queued",
                                true,
                                "message",
                                "Đối thủ mất kết nối. Đang tìm trận mới."));
            }
            pairPlayers();
        }
    }
}

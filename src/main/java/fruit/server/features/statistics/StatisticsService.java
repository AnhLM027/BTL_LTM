package fruit.server.features.statistics;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Statistics feature handlers; executed only on the server engine thread. */
public final class StatisticsService {
    private final GameServer server;

    public StatisticsService(GameServer server) {
        this.server = server;
    }

    public List<Map<String, Object>> leaderboard() {
        return server.store.users.values().stream()
                .filter(u -> !u.locked && !u.role.equals("ADMIN"))
                .sorted(
                        Comparator.comparingInt((User u) -> u.wins)
                                .reversed()
                                .thenComparing(
                                        Comparator.comparingInt((User u) -> u.totalScore)
                                                .reversed())
                                .thenComparingInt(u -> u.id))
                .limit(100)
                .map(User::view)
                .toList();
    }

    public List<Map<String, Object>> history(int id) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = server.store.records.size() - 1; i >= 0 && rows.size() < 100; i--) {
            Map<String, Object> record = server.store.records.get(i);
            List<Object> players = list(record.get("players"));
            if (players.size() != 2) continue;
            Map<String, Object> own = map(players.get(0)), opponent = map(players.get(1));
            if (integer(opponent, "id") == id) {
                Map<String, Object> temp = own;
                own = opponent;
                opponent = temp;
            }
            if (integer(own, "id") != id) continue;
            int winner = integer(record, "winnerId");
            rows.add(
                    obj(
                            "matchId",
                            record.get("matchId"),
                            "createdAt",
                            record.get("createdAt"),
                            "opponent",
                            str(opponent, "displayName"),
                            "ownScore",
                            integer(own, "score"),
                            "opponentScore",
                            integer(opponent, "score"),
                            "outcome",
                            winner == 0 ? "DRAW" : winner == id ? "WIN" : "LOSS",
                            "correct",
                            integer(own, "correct"),
                            "wrong",
                            integer(own, "wrong"),
                            "bombs",
                            integer(own, "bombs"),
                            "maxCombo",
                            integer(own, "maxCombo"),
                            "skills",
                            integer(own, "skills")));
        }
        return rows;
    }
}

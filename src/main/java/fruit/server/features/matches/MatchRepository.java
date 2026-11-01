package fruit.server.features.matches;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Completed matches and the two participant results, without a JSON result column. */
public final class MatchRepository {
    private MatchRepository() {}

    public static void load(Connection database, List<Map<String, Object>> records)
            throws SQLException {
        String matchSql =
                """
                SELECT m.*, m.id AS match_id, m.finish_reason AS reason, p.account_id AS user_id,
                       p.slot, a.username AS username_snapshot, p.display_name_snapshot, p.outcome,
                       p.score, p.correct, p.wrong, p.bombs, p.max_combo, p.skills_used AS skills
                FROM matches m LEFT JOIN match_players p ON p.match_id = m.id
                LEFT JOIN accounts a ON a.id = p.account_id
                ORDER BY m.created_at, m.id, p.slot
                """;
        Map<String, Map<String, Object>> matches = new LinkedHashMap<>();
        try (PreparedStatement statement = database.prepareStatement(matchSql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("match_id");
                Map<String, Object> record = matches.get(id);
                if (record == null) {
                    record =
                            obj(
                                    "matchId",
                                    id,
                                    "createdAt",
                                    instant(rows, "created_at"),
                                    "winnerId",
                                    0,
                                    "reason",
                                    rows.getString("reason"),
                                    "players",
                                    new ArrayList<>());
                    if (rows.getObject("finished_at") != null)
                        record.put("finishedAt", instant(rows, "finished_at"));
                    if (rows.getObject("duration_ms") != null)
                        record.put("durationMs", rows.getInt("duration_ms"));
                    matches.put(id, record);
                }
                if (rows.getObject("user_id") != null) {
                    String outcome = rows.getString("outcome");
                    if ("WIN".equals(outcome)) record.put("winnerId", rows.getInt("user_id"));
                    list(record.get("players"))
                            .add(
                                    obj(
                                            "id",
                                            rows.getInt("user_id"),
                                            "username",
                                            rows.getString("username_snapshot"),
                                            "displayName",
                                            rows.getString("display_name_snapshot"),
                                            "score",
                                            rows.getInt("score"),
                                            "correct",
                                            rows.getInt("correct"),
                                            "wrong",
                                            rows.getInt("wrong"),
                                            "bombs",
                                            rows.getInt("bombs"),
                                            "maxCombo",
                                            rows.getInt("max_combo"),
                                            "skills",
                                            rows.getInt("skills"),
                                            "outcome",
                                            outcome));
                }
            }
        }
        for (Map<String, Object> record : matches.values()) {
            validateMatch(record);
            int winner = integer(record, "winnerId");
            for (Object value : list(record.get("players"))) {
                Map<String, Object> player = map(value);
                String expected =
                        winner == 0 ? "DRAW" : integer(player, "id") == winner ? "WIN" : "LOSS";
                if (!expected.equals(player.remove("outcome")))
                    throw new SQLException(
                            "Inconsistent participant outcomes for match "
                                    + str(record, "matchId"));
            }
            records.add(record);
        }
    }

    public static void validateMatch(Map<String, Object> record) throws SQLException {
        try {
            String id = str(record, "matchId");
            if (!UUID.fromString(id).toString().equalsIgnoreCase(id))
                throw new IllegalArgumentException("Invalid match UUID");
            dateTime(str(record, "createdAt"));
            if (record.get("finishedAt") != null) dateTime(str(record, "finishedAt"));
            if (record.get("durationMs") != null) {
                int duration = requiredInt(record, "durationMs");
                if (duration < 0 || duration > 60_000)
                    throw new IllegalArgumentException("Invalid match duration");
            }
            if (!List.of("TIME_UP", "ACCOUNT_LOCKED").contains(str(record, "reason")))
                throw new IllegalArgumentException("Invalid match finish reason");
            List<Object> players = list(record.get("players"));
            if (players.size() != 2)
                throw new IllegalArgumentException("A match requires exactly two players");
            int first = requiredInt(map(players.get(0)), "id");
            int second = requiredInt(map(players.get(1)), "id");
            int winner = requiredInt(record, "winnerId");
            if (first <= 0
                    || second <= 0
                    || first == second
                    || (winner != 0 && winner != first && winner != second))
                throw new IllegalArgumentException("Invalid match participants or winner");
            for (Object value : players) {
                Map<String, Object> player = map(value);
                if (str(player, "username").isBlank() || str(player, "displayName").isBlank())
                    throw new IllegalArgumentException("Missing player name snapshot");
                requiredInt(player, "score");
                for (String counter : List.of("correct", "wrong", "bombs", "maxCombo", "skills"))
                    if (requiredInt(player, counter) < 0)
                        throw new IllegalArgumentException("Negative player counter " + counter);
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException(
                    "Invalid result for match " + str(record, "matchId") + ": " + e.getMessage(),
                    e);
        }
    }

    public static void save(Connection database, Map<String, Object> record) throws SQLException {
        validateMatch(record);
        String id = str(record, "matchId");
        try (PreparedStatement statement =
                database.prepareStatement(
                        """
                        INSERT INTO matches (id,created_at,finished_at,duration_ms,finish_reason)
                        VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE
                        created_at=VALUES(created_at),finished_at=VALUES(finished_at),
                        duration_ms=VALUES(duration_ms),finish_reason=VALUES(finish_reason)
                        """)) {
            statement.setString(1, id);
            statement.setObject(2, dateTime(str(record, "createdAt")));
            if (record.get("finishedAt") == null) statement.setNull(3, Types.TIMESTAMP);
            else statement.setObject(3, dateTime(str(record, "finishedAt")));
            if (record.get("durationMs") == null) statement.setNull(4, Types.INTEGER);
            else statement.setInt(4, integer(record, "durationMs"));
            statement.setString(5, str(record, "reason"));
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                database.prepareStatement("DELETE FROM match_players WHERE match_id=?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                database.prepareStatement(
                        """
INSERT INTO match_players (match_id,account_id,slot,display_name_snapshot,outcome,
    score,correct,wrong,bombs,max_combo,skills_used) VALUES (?,?,?,?,?,?,?,?,?,?,?)
""")) {
            List<Object> players = list(record.get("players"));
            int winner = integer(record, "winnerId");
            for (int slot = 0; slot < players.size(); slot++) {
                Map<String, Object> player = map(players.get(slot));
                int accountId = integer(player, "id");
                statement.setString(1, id);
                statement.setInt(2, accountId);
                statement.setInt(3, slot + 1);
                statement.setString(4, str(player, "displayName"));
                statement.setString(5, winner == 0 ? "DRAW" : winner == accountId ? "WIN" : "LOSS");
                statement.setInt(6, integer(player, "score"));
                statement.setInt(7, integer(player, "correct"));
                statement.setInt(8, integer(player, "wrong"));
                statement.setInt(9, integer(player, "bombs"));
                statement.setInt(10, integer(player, "maxCombo"));
                statement.setInt(11, integer(player, "skills"));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static int requiredInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException("Invalid integer " + key);
        return number.intValue();
    }

    private static LocalDateTime dateTime(String value) throws SQLException {
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        } catch (DateTimeParseException e) {
            throw new SQLException("Invalid UTC timestamp: " + value, e);
        }
    }

    private static String instant(ResultSet rows, String column) throws SQLException {
        return rows.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC).toString();
    }
}

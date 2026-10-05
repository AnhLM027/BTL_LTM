package Server.repository;

import Server.service.RoomException;
import Server.service.ScoreUpdate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Applies one catch event. The database unique constraint rejects a duplicate fruit event.
 */
public final class CatchEventRepository {
    public ScoreUpdate process(Connection connection, long matchId, long playerId, long fruitInstanceId, int basketId)
            throws SQLException, RoomException {
        String event = """
                SELECT m.match_state, gm.mode_code, m.mission_label_id, fs.fruit_id, f.group_id,
                       b.group_id AS basket_group
                FROM game_match m
                JOIN game_mode gm ON gm.mode_id = m.mode_id
                JOIN fruit_spawn fs ON fs.match_id = m.match_id
                JOIN fruit f ON f.fruit_id = fs.fruit_id
                LEFT JOIN basket b ON b.basket_id = ? AND b.is_active = TRUE
                WHERE m.match_id = ? AND fs.fruit_instance_id = ?
                  AND EXISTS (SELECT 1 FROM match_player mp WHERE mp.match_id = m.match_id AND mp.player_id = ?)
                  AND m.started_at IS NOT NULL
                  AND CURRENT_TIMESTAMP < DATE_ADD(m.started_at, INTERVAL m.duration_seconds SECOND)
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(event)) {
            statement.setInt(1, basketId);
            statement.setLong(2, matchId);
            statement.setLong(3, fruitInstanceId);
            statement.setLong(4, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new RoomException("Invalid match, player, fruit instance, or basket");
                if (!"PLAYING".equals(result.getString("match_state"))) throw new RoomException("Match is not playing");
                boolean correct = "FRUIT_GROUP".equals(result.getString("mode_code"))
                        ? result.getObject("basket_group") != null && result.getInt("group_id") == result.getInt("basket_group")
                        : hasNutrition(connection, result.getInt("fruit_id"), result.getObject("mission_label_id", Integer.class));
                int delta = correct ? 10 : -5;
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO catch_event(match_id,player_id,fruit_instance_id,catch_result,score_delta) VALUES (?,?,?,?,?)")) {
                    insert.setLong(1, matchId);
                    insert.setLong(2, playerId);
                    insert.setLong(3, fruitInstanceId);
                    insert.setString(4, correct ? "CORRECT" : "WRONG");
                    insert.setInt(5, delta);
                    insert.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE match_player SET correct_count=correct_count+?, wrong_count=wrong_count+?, final_score=final_score+? WHERE match_id=? AND player_id=?")) {
                    update.setInt(1, correct ? 1 : 0);
                    update.setInt(2, correct ? 0 : 1);
                    update.setInt(3, delta);
                    update.setLong(4, matchId);
                    update.setLong(5, playerId);
                    update.executeUpdate();
                }
                try (PreparedStatement score = connection.prepareStatement("SELECT final_score, correct_count, wrong_count FROM match_player WHERE match_id=? AND player_id=?")) {
                    score.setLong(1, matchId);
                    score.setLong(2, playerId);
                    try (ResultSet value = score.executeQuery()) {
                        value.next();
                        return new ScoreUpdate(matchId, playerId, value.getInt(1), value.getInt(2), value.getInt(3));
                    }
                }
            }
        }
    }

    private boolean hasNutrition(Connection connection, int fruitId, Integer labelId) throws SQLException {
        if (labelId == null) return false;
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM fruit_nutrition WHERE fruit_id=? AND label_id=?")) {
            statement.setInt(1, fruitId);
            statement.setInt(2, labelId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}

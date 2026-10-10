package Server.repository;

import Server.service.RoomException;
import Server.service.ScoreUpdate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Applies one server-authoritative catch event for CLASSIC or ORDER. */
public final class CatchEventRepository {
    public ScoreUpdate process(Connection connection, long matchId, long playerId, long fruitInstanceId, int basketId)
            throws SQLException, RoomException {
        String event = """
                SELECT m.match_state, gm.mode_code, fs.fruit_id, fs.is_bomb
                FROM game_match m
                JOIN game_mode gm ON gm.mode_id = m.mode_id
                JOIN fruit_spawn fs ON fs.match_id = m.match_id
                JOIN basket b ON b.basket_id = ? AND b.is_active = TRUE
                WHERE m.match_id = ? AND fs.fruit_instance_id = ?
                  AND EXISTS (SELECT 1 FROM match_player mp WHERE mp.match_id=m.match_id AND mp.player_id=?)
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
                if (!result.next()) throw new RoomException("Invalid match, player, object instance, or basket");
                if (!"PLAYING".equals(result.getString("match_state"))) throw new RoomException("Match is not playing");
                boolean bomb = result.getBoolean("is_bomb");
                String mode = result.getString("mode_code");
                boolean correct;
                int delta;
                if ("CLASSIC".equals(mode)) {
                    correct = !bomb;
                    delta = bomb ? -10 : 10;
                } else if ("ORDER".equals(mode)) {
                    correct = !bomb && isOrderTarget(connection, matchId, result.getObject("fruit_id", Integer.class));
                    delta = correct ? 10 : -5;
                } else throw new RoomException("Unsupported game mode");

                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO catch_event(match_id,player_id,fruit_instance_id,catch_result,score_delta) VALUES (?,?,?,?,?)")) {
                    insert.setLong(1, matchId); insert.setLong(2, playerId); insert.setLong(3, fruitInstanceId);
                    insert.setString(4, correct ? "CORRECT" : "WRONG"); insert.setInt(5, delta); insert.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE match_player SET correct_count=correct_count+?, wrong_count=wrong_count+?, final_score=final_score+? WHERE match_id=? AND player_id=?")) {
                    update.setInt(1, correct ? 1 : 0); update.setInt(2, correct ? 0 : 1); update.setInt(3, delta);
                    update.setLong(4, matchId); update.setLong(5, playerId); update.executeUpdate();
                }
                return readScore(connection, matchId, playerId, correct, 0, 0);
            }
        }
    }

    public ScoreUpdate applyComboBonus(Connection connection, ScoreUpdate score, int bonus, int comboCount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE match_player SET final_score=final_score+? WHERE match_id=? AND player_id=?")) {
            update.setInt(1, bonus); update.setLong(2, score.matchId()); update.setLong(3, score.playerId()); update.executeUpdate();
        }
        return readScore(connection, score.matchId(), score.playerId(), score.wasCorrect(), comboCount, bonus);
    }

    private ScoreUpdate readScore(Connection connection, long matchId, long playerId, boolean correct, int comboCount, int comboBonus) throws SQLException {
        try (PreparedStatement score = connection.prepareStatement("SELECT final_score,correct_count,wrong_count FROM match_player WHERE match_id=? AND player_id=?")) {
            score.setLong(1, matchId); score.setLong(2, playerId);
            try (ResultSet value = score.executeQuery()) {
                if (!value.next()) throw new SQLException("Match player was not found");
                return new ScoreUpdate(matchId, playerId, value.getInt(1), value.getInt(2), value.getInt(3), correct, comboCount, comboBonus);
            }
        }
    }

    private boolean isOrderTarget(Connection connection, long matchId, Integer fruitId) throws SQLException {
        if (fruitId == null) return false;
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM match_target_fruit WHERE match_id=? AND fruit_id=?")) {
            statement.setLong(1, matchId); statement.setInt(2, fruitId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }
}

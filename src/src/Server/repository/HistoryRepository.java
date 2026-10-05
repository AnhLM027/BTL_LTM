package Server.repository;

import Server.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class HistoryRepository {
    public List<HistoryItem> findForPlayer(long playerId) throws SQLException {
        String sql = """
                SELECT m.match_id, gm.mode_code, m.started_at, m.ended_at, mp.final_score, mp.correct_count, mp.wrong_count, mp.result,
                       opponent.player_id AS opponent_id, opponent.display_name AS opponent_name
                FROM match_player mp JOIN game_match m ON m.match_id=mp.match_id JOIN game_mode gm ON gm.mode_id=m.mode_id
                JOIN match_player other ON other.match_id=m.match_id AND other.player_id<>mp.player_id
                JOIN player opponent ON opponent.player_id=other.player_id
                WHERE mp.player_id=? AND m.match_state='FINISHED' ORDER BY m.ended_at DESC, m.match_id DESC
                """;
        try (Connection connection = DatabaseConfig.openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                List<HistoryItem> items = new ArrayList<>();
                while (result.next())
                    items.add(new HistoryItem(result.getLong("match_id"), result.getString("mode_code"), time(result, "started_at"), time(result, "ended_at"), result.getLong("opponent_id"), result.getString("opponent_name"), result.getInt("final_score"), result.getInt("correct_count"), result.getInt("wrong_count"), result.getString("result")));
                return items;
            }
        }
    }

    private LocalDateTime time(ResultSet result, String name) throws SQLException {
        var value = result.getTimestamp(name);
        return value == null ? null : value.toLocalDateTime();
    }

    public record HistoryItem(long matchId, String modeCode, LocalDateTime startedAt, LocalDateTime endedAt,
                              long opponentPlayerId, String opponentName, int score, int correctCount, int wrongCount,
                              String result) {
    }
}

package Server.repository;

import Server.config.DatabaseConfig;
import Server.model.domain.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class PlayerRepository {
    public Optional<Player> findById(long playerId) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_id, account_id, display_name, total_score, total_games, total_wins, created_at, updated_at FROM player WHERE player_id=?")) {
            statement.setLong(1, playerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public List<Player> leaderboard(int limit) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player_id, account_id, display_name, total_score, total_games, total_wins, created_at, updated_at FROM player ORDER BY total_score DESC, total_wins DESC, total_games DESC, player_id ASC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<Player> players = new ArrayList<>();
                while (result.next()) players.add(map(result));
                return players;
            }
        }
    }

    public List<Player> findByIds(Collection<Long> playerIds) throws SQLException {
        if (playerIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(playerIds.size(), "?"));
        String query = "SELECT player_id, account_id, display_name, total_score, total_games, total_wins, created_at, updated_at "
                + "FROM player WHERE player_id IN (" + placeholders + ") ORDER BY total_score DESC, player_id ASC";

        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (Long playerId : playerIds) {
                statement.setLong(index++, playerId);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Player> players = new ArrayList<>();
                while (result.next()) {
                    players.add(map(result));
                }
                return players;
            }
        }
    }

    private Player map(ResultSet result) throws SQLException {
        return new Player(result.getLong("player_id"), result.getLong("account_id"), result.getString("display_name"), result.getInt("total_score"), result.getInt("total_games"), result.getInt("total_wins"), result.getTimestamp("created_at").toLocalDateTime(), result.getTimestamp("updated_at").toLocalDateTime());
    }
}

package Server.repository;

import Server.config.DatabaseConfig;
import Server.model.domain.GameMode;
import Server.model.enums.GameModeCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public final class GameModeRepository {
    public List<GameMode> findActive() throws SQLException {
        String query = "SELECT mode_id, mode_code, mode_name, description, is_active FROM game_mode WHERE is_active=TRUE ORDER BY mode_id";
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet result = statement.executeQuery()) {
            List<GameMode> modes = new ArrayList<>();
            while (result.next()) modes.add(map(result));
            return modes;
        }
    }

    public Optional<GameMode> findById(int modeId) throws SQLException {
        String query = "SELECT mode_id, mode_code, mode_name, description, is_active FROM game_mode WHERE mode_id = ?";
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, modeId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(map(result));
            }
        }
    }

    public Optional<GameMode> findActiveByCode(GameModeCode modeCode) throws SQLException {
        String query = "SELECT mode_id, mode_code, mode_name, description, is_active FROM game_mode "
                + "WHERE mode_code = ? AND is_active = TRUE";
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, modeCode.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(result));
            }
        }
    }

    private GameMode map(ResultSet result) throws SQLException {
        return new GameMode(result.getInt("mode_id"), GameModeCode.valueOf(result.getString("mode_code")),
                result.getString("mode_name"), result.getString("description"), result.getBoolean("is_active"));
    }
}

package Server.repository;

import Server.model.domain.NutritionLabel;
import Server.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads the authoritative nutrition objective selected for a match.
 */
public final class NutritionLabelRepository {
    public NutritionLabel findById(int id) throws SQLException {
        try (var connection = DatabaseConfig.openConnection(); var statement = connection.prepareStatement("SELECT label_id,label_code,display_name,description,is_active FROM nutrition_label WHERE label_id=?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Nutrition label was not found");
                return new NutritionLabel(result.getInt(1), result.getString(2), result.getString(3), result.getString(4), result.getBoolean(5));
            }
        }
    }

    public int findRandomActiveId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT label_id FROM nutrition_label WHERE is_active = TRUE ORDER BY RAND() LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("No active nutrition labels are configured");
            }
            return result.getInt(1);
        }
    }
}

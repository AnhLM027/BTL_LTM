package Server.repository;

import Server.config.DatabaseConfig;
import Server.model.domain.Fruit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class FruitRepository {
    public record CatalogEntry(int fruitId, int groupId, String groupCode, String groupName,
                               String fruitCode, String fruitName, String description, String assetPath, String nutritionLabels) {}

    public List<Fruit> findActive() throws SQLException {
        String query = "SELECT fruit_id, group_id, fruit_code, fruit_name, default_asset_path, is_active "
                + "FROM fruit WHERE is_active = TRUE ORDER BY fruit_id";
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet result = statement.executeQuery()) {
            List<Fruit> fruits = new ArrayList<>();
            while (result.next()) {
                fruits.add(new Fruit(
                        result.getInt("fruit_id"), result.getInt("group_id"),
                        result.getString("fruit_code"), result.getString("fruit_name"),
                        result.getString("default_asset_path"), result.getBoolean("is_active")
                ));
            }
            return fruits;
        }
    }

    public List<CatalogEntry> findCatalog() throws SQLException {
        String query = "SELECT f.fruit_id, f.group_id, g.group_code, g.group_name, "
                + "f.fruit_code, f.fruit_name, f.description, f.default_asset_path, "
                + "COALESCE(GROUP_CONCAT(n.display_name ORDER BY n.label_id SEPARATOR ', '), '') AS nutrition_labels "
                + "FROM fruit f JOIN fruit_group g ON g.group_id=f.group_id "
                + "LEFT JOIN fruit_nutrition fn ON fn.fruit_id=f.fruit_id "
                + "LEFT JOIN nutrition_label n ON n.label_id=fn.label_id AND n.is_active=TRUE "
                + "WHERE f.is_active=TRUE AND g.is_active=TRUE "
                + "GROUP BY f.fruit_id, f.group_id, g.group_code, g.group_name, f.fruit_code, f.fruit_name, f.description, f.default_asset_path "
                + "ORDER BY f.fruit_id";
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet result = statement.executeQuery()) {
            List<CatalogEntry> entries = new ArrayList<>();
            while (result.next()) {
                entries.add(new CatalogEntry(result.getInt("fruit_id"), result.getInt("group_id"),
                        result.getString("group_code"), result.getString("group_name"),
                        result.getString("fruit_code"), result.getString("fruit_name"),
                        result.getString("description"), result.getString("default_asset_path"),
                        result.getString("nutrition_labels")));
            }
            return entries;
        }
    }

    public int create(int groupId, String code, String name, String description, String assetPath) throws SQLException {
        String sql = "INSERT INTO fruit(group_id,fruit_code,fruit_name,description,default_asset_path,is_active) VALUES (?,?,?,?,?,TRUE)";
        try (Connection c = DatabaseConfig.openConnection(); PreparedStatement s = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            s.setInt(1, groupId); s.setString(2, code); s.setString(3, name);
            s.setString(4, description); s.setString(5, assetPath);
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing fruit id"); return keys.getInt(1); }
        }
    }

    public void update(int fruitId, int groupId, String code, String name, String description, String assetPath) throws SQLException {
        String sql = "UPDATE fruit SET group_id=?,fruit_code=?,fruit_name=?,description=?,default_asset_path=? WHERE fruit_id=? AND is_active=TRUE";
        try (Connection c = DatabaseConfig.openConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setInt(1, groupId); s.setString(2, code); s.setString(3, name); s.setString(4, description); s.setString(5, assetPath); s.setInt(6, fruitId);
            if (s.executeUpdate() != 1) throw new SQLException("Fruit was not found");
        }
    }

    public void delete(int fruitId) throws SQLException {
        try (Connection c = DatabaseConfig.openConnection(); PreparedStatement s = c.prepareStatement("UPDATE fruit SET is_active=FALSE WHERE fruit_id=?")) {
            s.setInt(1, fruitId); if (s.executeUpdate() != 1) throw new SQLException("Fruit was not found");
        }
    }
}

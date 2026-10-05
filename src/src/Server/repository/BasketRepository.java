package Server.repository;

import Server.config.DatabaseConfig;
import Server.model.domain.Basket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class BasketRepository {
    public List<Basket> findActive() throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT basket_id, group_id, basket_name, asset_path, is_active FROM basket WHERE is_active=TRUE ORDER BY basket_id");
             ResultSet result = statement.executeQuery()) {
            List<Basket> baskets = new ArrayList<>();
            while (result.next()) baskets.add(new Basket(result.getInt("basket_id"), result.getInt("group_id"),
                    result.getString("basket_name"), result.getString("asset_path"), result.getBoolean("is_active")));
            return baskets;
        }
    }
}

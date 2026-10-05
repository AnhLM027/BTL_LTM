package Server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConfig {
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/fruit_battle_online";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "123456";

    private DatabaseConfig() {
    }

    public static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                value("fruitbattle.db.url", "FRUIT_BATTLE_DB_URL", DEFAULT_URL),
                value("fruitbattle.db.user", "FRUIT_BATTLE_DB_USER", DEFAULT_USER),
                value("fruitbattle.db.password", "FRUIT_BATTLE_DB_PASSWORD", DEFAULT_PASSWORD)
        );
    }

    private static String value(String propertyName, String environmentName, String fallback) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv(environmentName);
        return environment == null || environment.isBlank() ? fallback : environment;
    }
}

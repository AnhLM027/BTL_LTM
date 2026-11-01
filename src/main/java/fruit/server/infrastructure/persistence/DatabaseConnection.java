package fruit.server.infrastructure.persistence;

import fruit.server.application.GameServer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Owns the MySQL configuration and connection used by the server engine thread. */
public final class DatabaseConnection implements AutoCloseable {
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public DatabaseConnection() {
        url =
                GameServer.setting(
                        "fruit.db.url",
                        "FRUIT_DB_URL",
                        "jdbc:mysql://localhost:3306/fruit_battle?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Bangkok&connectTimeout=3000&socketTimeout=5000");
        user = GameServer.setting("fruit.db.user", "FRUIT_DB_USER", "root");
        password = GameServer.setting("fruit.db.password", "FRUIT_DB_PASSWORD", "123456");
    }

    /** Reuses a healthy connection, or opens a replacement before the next operation. */
    public Connection get() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            close();
            connection = DriverManager.getConnection(url, user, password);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET time_zone = '+00:00'");
            } catch (SQLException e) {
                close();
                throw e;
            }
        }
        return connection;
    }

    @Override
    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}

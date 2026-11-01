package fruit.server.infrastructure.persistence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

/** Four business tables; schema metadata lives in the accounts table comment, not a fifth table. */
public final class DatabaseSchema {
    public static final String MARKER = "fruit-battle:v3";

    private DatabaseSchema() {}

    public static void initialize(Connection database) throws SQLException {
        if (!database.getAutoCommit())
            throw new SQLException("Schema setup requires an idle connection");
        if (database.getCatalog() == null || database.getCatalog().isBlank())
            throw new SQLException("Select the game database in the JDBC URL");
        String lock =
                "fruit_schema:"
                        + UUID.nameUUIDFromBytes(
                                database.getCatalog().getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement statement = database.prepareStatement("SELECT GET_LOCK(?,3)")) {
            statement.setString(1, lock);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt(1) != 1)
                    throw new SQLException("Another server is changing this database");
            }
        }
        try {
            Set<String> supported = Set.of("accounts", "player_stats", "matches", "match_players");
            try (Statement statement = database.createStatement();
                    ResultSet rows = statement.executeQuery("SHOW TABLES")) {
                while (rows.next()) {
                    if (!supported.contains(rows.getString(1)))
                        throw new SQLException(
                                "Unsupported table "
                                        + rows.getString(1)
                                        + ". Use the current four-table database or a new empty"
                                        + " database.");
                }
            }
            String marker = tableExists(database, "accounts") ? marker(database) : "";
            if (!marker.isEmpty()
                    && !marker.equals(MARKER)
                    && !marker.startsWith(MARKER + ":migrated:"))
                throw new SQLException("Unsupported accounts schema: " + marker);
            if (tableExists(database, "accounts") && marker.isEmpty())
                throw new SQLException("An unrecognized accounts table already exists");
            createTables(database);
            ensureAvatarColumn(database);
        } finally {
            try (PreparedStatement statement =
                    database.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.executeQuery().close();
            }
        }
    }

    static void createTables(Connection database) throws SQLException {
        try (InputStream stream =
                DatabaseSchema.class.getResourceAsStream("/fruit/db/schema.sql")) {
            if (stream == null) throw new SQLException("Missing /fruit/db/schema.sql");
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = database.createStatement()) {
                for (String sql : schema.split(";"))
                    if (!sql.isBlank()) statement.executeUpdate(sql.trim());
            }
        } catch (java.io.IOException e) {
            throw new SQLException("Cannot read schema", e);
        }
    }

    private static void ensureAvatarColumn(Connection database) throws SQLException {
        try (Statement statement = database.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT 1 FROM information_schema.columns WHERE"
                                        + " table_schema=DATABASE() AND table_name='accounts' AND"
                                        + " column_name='avatar_id'")) {
            if (rows.next()) return;
        }
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate(
                    "ALTER TABLE accounts ADD COLUMN avatar_id TINYINT UNSIGNED NOT NULL DEFAULT 0,"
                            + " ADD CONSTRAINT ck_accounts_avatar CHECK (avatar_id IN (0,1))");
        }
    }

    public static boolean tableExists(Connection database, String table) throws SQLException {
        try (PreparedStatement statement =
                database.prepareStatement(
                        "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                                + " table_name=?")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    static String marker(Connection database) throws SQLException {
        try (Statement statement = database.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT table_comment FROM information_schema.tables WHERE"
                                        + " table_schema=DATABASE() AND table_name='accounts'")) {
            return rows.next() ? rows.getString(1) : "";
        }
    }
}

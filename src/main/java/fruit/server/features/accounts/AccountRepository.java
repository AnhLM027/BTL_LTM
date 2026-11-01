package fruit.server.features.accounts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** Account identity, password and lifetime-statistics persistence. */
public final class AccountRepository {
    private AccountRepository() {}

    public static void load(Connection database, Map<Integer, User> users) throws SQLException {
        String userSql =
                """
                SELECT u.*, u.password_salt AS salt,
                       s.wins, s.losses, s.draws, s.total_score
                FROM accounts u
                LEFT JOIN player_stats s ON s.account_id = u.id
                ORDER BY u.id
                """;
        try (PreparedStatement statement = database.prepareStatement(userSql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                if (rows.getObject("wins") == null) {
                    throw new SQLException(
                            "Incomplete or unsupported account data for user " + rows.getInt("id"));
                }
                User user =
                        new User(
                                rows.getInt("id"),
                                rows.getString("username"),
                                rows.getString("display_name"));
                user.role = rows.getString("role");
                user.avatarId = rows.getInt("avatar_id");
                user.locked = rows.getBoolean("locked");
                user.salt = rows.getString("salt");
                user.hash = rows.getString("password_hash");
                user.wins = rows.getInt("wins");
                user.losses = rows.getInt("losses");
                user.draws = rows.getInt("draws");
                user.totalScore = rows.getInt("total_score");
                users.put(user.id, user);
            }
        }
    }

    public static void save(Connection database, User user) throws SQLException {
        try (PreparedStatement statement =
                database.prepareStatement(
                        """
INSERT INTO accounts (id,username,display_name,password_hash,password_salt,role,locked,avatar_id)
VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
display_name=VALUES(display_name),password_hash=VALUES(password_hash),
password_salt=VALUES(password_salt),role=VALUES(role),locked=VALUES(locked),avatar_id=VALUES(avatar_id)
""")) {
            statement.setInt(1, user.id);
            statement.setString(2, user.username);
            statement.setString(3, user.displayName);
            statement.setString(4, user.hash);
            statement.setString(5, user.salt);
            statement.setString(6, user.role);
            statement.setBoolean(7, user.locked);
            statement.setInt(8, user.avatarId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                database.prepareStatement(
                        """
INSERT INTO player_stats (account_id,wins,losses,draws,total_score)
VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE
wins=VALUES(wins),losses=VALUES(losses),draws=VALUES(draws),total_score=VALUES(total_score)
""")) {
            statement.setInt(1, user.id);
            statement.setInt(2, user.wins);
            statement.setInt(3, user.losses);
            statement.setInt(4, user.draws);
            statement.setInt(5, user.totalScore);
            statement.executeUpdate();
        }
    }
}

package Server.repository;

import Server.config.DatabaseConfig;
import Server.model.domain.Account;
import Server.model.domain.Player;
import Server.model.enums.AccountRole;
import Server.model.enums.AccountStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.sql.Statement;

public final class AccountRepository {
    public AuthenticatedPlayer createPlayerAccount(String username, String passwordHash) throws SQLException {
        return createAccount(username, passwordHash, AccountRole.PLAYER);
    }

    public AuthenticatedPlayer createAccount(String username, String passwordHash, AccountRole role) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement account = connection.prepareStatement("INSERT INTO account(username,password_hash,role,status) VALUES (?,?,?,'ACTIVE')", Statement.RETURN_GENERATED_KEYS)) {
                account.setString(1, username);
                account.setString(2, passwordHash);
                account.setString(3, role.name());
                account.executeUpdate();
                try (ResultSet keys = account.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Missing account id");
                    long accountId = keys.getLong(1);
                    if (role == AccountRole.PLAYER) {
                        try (PreparedStatement player = connection.prepareStatement("INSERT INTO player(account_id,display_name) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
                            player.setLong(1, accountId);
                            player.setString(2, username);
                            player.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
        return findAuthenticatedPlayerByUsername(username).orElseThrow(() -> new SQLException("Created account cannot be read"));
    }

    public void deleteAccount(long accountId) throws SQLException {
        try (Connection c = DatabaseConfig.openConnection(); PreparedStatement s = c.prepareStatement("DELETE FROM account WHERE account_id=?")) {
            s.setLong(1, accountId);
            if (s.executeUpdate() != 1) throw new SQLException("Account was not found");
        }
    }

    public void updateAccount(long accountId, String username, AccountRole role, AccountStatus status, String passwordHash) throws SQLException {
        try (Connection c = DatabaseConfig.openConnection()) {
            c.setAutoCommit(false);
            try {
                AccountRole oldRole;
                try (PreparedStatement current = c.prepareStatement("SELECT role FROM account WHERE account_id=?")) {
                    current.setLong(1, accountId);
                    try (ResultSet result = current.executeQuery()) {
                        if (!result.next()) throw new SQLException("Account was not found");
                        oldRole = AccountRole.valueOf(result.getString(1));
                    }
                }

                String sql = passwordHash == null ? "UPDATE account SET username=?,role=?,status=? WHERE account_id=?" : "UPDATE account SET username=?,role=?,status=?,password_hash=? WHERE account_id=?";
                try (PreparedStatement statement = c.prepareStatement(sql)) {
                    statement.setString(1, username);
                    statement.setString(2, role.name());
                    statement.setString(3, status.name());
                    if (passwordHash == null) statement.setLong(4, accountId);
                    else {
                        statement.setString(4, passwordHash);
                        statement.setLong(5, accountId);
                    }
                    if (statement.executeUpdate() != 1) throw new SQLException("Account was not found");
                }

                if (oldRole == AccountRole.PLAYER && role == AccountRole.ADMIN) {
                    try (PreparedStatement statement = c.prepareStatement("DELETE FROM player WHERE account_id=?")) {
                        statement.setLong(1, accountId);
                        statement.executeUpdate();
                    }
                } else if (oldRole == AccountRole.ADMIN && role == AccountRole.PLAYER) {
                    try (PreparedStatement statement = c.prepareStatement("INSERT INTO player(account_id,display_name) VALUES (?,?)")) {
                        statement.setLong(1, accountId);
                        statement.setString(2, username);
                        statement.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException exception) {
                c.rollback();
                throw exception;
            }
        }
    }

    public List<PublicAccount> findPublicAccounts() throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT account_id, username, role, status FROM account ORDER BY account_id");
             ResultSet result = statement.executeQuery()) {
            List<PublicAccount> accounts = new ArrayList<>();
            while (result.next())
                accounts.add(new PublicAccount(result.getLong(1), result.getString(2), AccountRole.valueOf(result.getString(3)), AccountStatus.valueOf(result.getString(4))));
            return accounts;
        }
    }

    private static final String FIND_AUTHENTICATED_PLAYER = """
            SELECT a.account_id, a.username, a.password_hash, a.role, a.status,
                   a.created_at AS account_created_at, a.updated_at AS account_updated_at,
                   p.player_id, p.account_id AS player_account_id, p.display_name,
                   p.total_score, p.total_games, p.total_wins,
                   p.created_at AS player_created_at, p.updated_at AS player_updated_at
            FROM account a
            LEFT JOIN player p ON p.account_id = a.account_id
            WHERE a.username = ?
            """;

    public Optional<AuthenticatedPlayer> findAuthenticatedPlayerByUsername(String username) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_AUTHENTICATED_PLAYER)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                Account account = new Account(
                        result.getLong("account_id"),
                        result.getString("username"),
                        result.getString("password_hash"),
                        AccountRole.valueOf(result.getString("role")),
                        AccountStatus.valueOf(result.getString("status")),
                        result.getTimestamp("account_created_at").toLocalDateTime(),
                        result.getTimestamp("account_updated_at").toLocalDateTime()
                );
                Player player = null;
                Long playerId = result.getObject("player_id", Long.class);
                if (playerId != null) {
                    player = new Player(
                            playerId,
                            result.getLong("player_account_id"),
                            result.getString("display_name"),
                            result.getInt("total_score"),
                            result.getInt("total_games"),
                            result.getInt("total_wins"),
                            result.getTimestamp("player_created_at").toLocalDateTime(),
                            result.getTimestamp("player_updated_at").toLocalDateTime()
                    );
                }
                return Optional.of(new AuthenticatedPlayer(account, player));
            }
        }
    }

    public record PublicAccount(long accountId, String username, AccountRole role, AccountStatus status) {
    }
}

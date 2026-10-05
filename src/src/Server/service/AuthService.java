package Server.service;

import Server.model.enums.AccountStatus;
import Server.model.enums.AccountRole;
import Server.repository.AccountRepository;
import Server.repository.AuthenticatedPlayer;
import Server.security.PasswordHasher;

import java.sql.SQLException;
import java.util.List;

public final class AuthService {
    private final AccountRepository accountRepository;

    public AuthService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AuthenticatedPlayer authenticate(String username, String password)
            throws AuthenticationException, SQLException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Username and password are required");
        }
        AuthenticatedPlayer authenticatedPlayer = accountRepository.findAuthenticatedPlayerByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (authenticatedPlayer.account().status() != AccountStatus.ACTIVE) {
            throw new AuthenticationException("Account is not active");
        }
        if (!PasswordHasher.verify(password, authenticatedPlayer.account().passwordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }
        return authenticatedPlayer;
    }

    public List<AccountRepository.PublicAccount> publicAccounts() throws SQLException {
        return accountRepository.findPublicAccounts();
    }

    public AuthenticatedPlayer register(String username, String password) throws AuthenticationException, SQLException {
        if (username == null || !username.matches("[A-Za-z0-9_]{2,50}") || password == null || password.length() < 6)
            throw new AuthenticationException("Username must be 2-50 letters/numbers and password at least 6 characters");
        try {
            return accountRepository.createPlayerAccount(username, PasswordHasher.hash(password));
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23"))
                throw new AuthenticationException("Username already exists");
            throw e;
        }
    }

    public void deleteAccount(long accountId) throws SQLException {
        accountRepository.deleteAccount(accountId);
    }

    public void updateAccount(long accountId, String username, String role, String status, String password) throws AuthenticationException, SQLException {
        if (username == null || !username.matches("[A-Za-z0-9_]{2,50}"))
            throw new AuthenticationException("Invalid username");
        AccountRole accountRole;
        AccountStatus accountStatus;
        try {
            accountRole = AccountRole.valueOf(role);
            accountStatus = AccountStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid role or status");
        }
        accountRepository.updateAccount(accountId, username, accountRole, accountStatus, password == null || password.isBlank() ? null : PasswordHasher.hash(password));
    }

    public void createAdminAccount(String username, String password, String role) throws AuthenticationException, SQLException {
        if (username == null || !username.matches("[A-Za-z0-9_]{2,50}") || password == null || password.length() < 6)
            throw new AuthenticationException("Invalid username or password");
        try {
            accountRepository.createAccount(username, PasswordHasher.hash(password), AccountRole.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid role");
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23"))
                throw new AuthenticationException("Username already exists");
            throw e;
        }
    }
}

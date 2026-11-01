package fruit.server.infrastructure.persistence;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.json;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.parse;
import static fruit.common.Protocol.readLine;
import static fruit.common.Protocol.str;

import fruit.server.features.accounts.AccountRepository;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.MatchRepository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Store implements AutoCloseable {
    public final Map<Integer, User> users = new LinkedHashMap<>();
    public final List<Map<String, Object>> records = new ArrayList<>();
    public final Path file;
    public final String mode;
    public final long startedAt = System.currentTimeMillis();
    public final DatabaseConnection connection;
    public int nextId = 1;

    public Store(Path dataDir, boolean memoryOnly, boolean localOnly) throws Exception {
        file = dataDir.resolve("fruit-data.jsonl");
        connection = memoryOnly || localOnly ? null : new DatabaseConnection();
        if (memoryOnly) mode = "MEMORY (test only)";
        else if (localOnly) {
            mode = "LOCAL JSON (explicit --local)";
            loadLocal();
        } else {
            mode = "MySQL";
            try {
                Connection database = connection.get();
                DatabaseSchema.initialize(database);
                AccountRepository.load(database, users);
                MatchRepository.load(database, records);
            } catch (SQLException e) {
                close();
                throw new SQLException(
                        "Cannot initialize MySQL storage. Check the connection/schema or explicitly"
                                + " use --local. "
                                + e.getMessage(),
                        e);
            }
        }
        nextId = users.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    public User findUsername(String username) {
        return users.values().stream()
                .filter(u -> u.username.equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    public void loadLocal() throws IOException {
        if (!Files.exists(file)) return;
        // Each stored row is bounded independently; a long history may exceed
        // the 64 KiB TCP message limit without breaking the local database.
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (String line; (line = readLine(reader)) != null; ) {
                Map<String, Object> row = map(parse(line));
                Map<String, Object> value = map(row.get("value"));
                if (str(row, "type").equals("USER")) {
                    User user = User.load(value);
                    users.put(user.id, user);
                } else if (str(row, "type").equals("MATCH")) records.add(value);
            }
        }
    }

    public void saveLocal() throws IOException {
        if (mode.startsWith("MEMORY")) return;
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            for (User user : users.values()) {
                writer.write(json(obj("type", "USER", "value", user.saved())));
                writer.newLine();
            }
            for (Map<String, Object> record : records) {
                writer.write(json(obj("type", "MATCH", "value", record)));
                writer.newLine();
            }
        }
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveUser(User user) throws Exception {
        if (!mode.equals("MySQL")) {
            saveLocal();
            return;
        }
        inTransaction(database -> AccountRepository.save(database, user));
    }

    public void saveMatch(Map<String, Object> record, User first, User second) throws Exception {
        if (!mode.equals("MySQL")) {
            saveLocal();
            return;
        }
        MatchRepository.validateMatch(record);
        List<Object> players = list(record.get("players"));
        if (first == null
                || second == null
                || first.id != integer(map(players.get(0)), "id")
                || second.id != integer(map(players.get(1)), "id"))
            throw new SQLException("Match participants do not match the supplied users");
        inTransaction(
                database -> {
                    AccountRepository.save(database, first);
                    AccountRepository.save(database, second);
                    MatchRepository.save(database, record);
                });
    }

    private void inTransaction(SqlAction action) throws SQLException {
        Connection database = connection.get();
        boolean autoCommit = database.getAutoCommit();
        database.setAutoCommit(false);
        Throwable failure = null;
        try {
            action.run(database);
            database.commit();
        } catch (SQLException | RuntimeException e) {
            failure = e;
            try {
                database.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            try {
                database.setAutoCommit(autoCommit);
            } catch (SQLException restoreFailure) {
                if (failure != null) failure.addSuppressed(restoreFailure);
                else throw restoreFailure;
            }
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection database) throws SQLException;
    }

    @Override
    public void close() {
        if (connection != null) connection.close();
    }
}

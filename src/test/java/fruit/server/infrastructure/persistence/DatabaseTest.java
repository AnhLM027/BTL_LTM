package fruit.server.infrastructure.persistence;

import static fruit.common.Protocol.obj;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.MatchRepository;

import java.net.BindException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Integration checks use new, retained audit databases and never modify the configured database.
 */
public final class DatabaseTest {
    private static int checks;
    private static final String PASSWORD = "DatabaseFruit!2026";
    private static final String CREATED_AT = "2025-02-03T04:05:06Z";
    private static final String DRAW_ID = "00000000-0000-0000-0000-000000000001";
    private static final String WIN_ID = "00000000-0000-0000-0000-000000000002";

    public static void main(String[] args) throws Exception {
        System.out.println("PASS: " + run() + " database checks");
    }

    public static int run() throws Exception {
        checks = 0;
        String previousUrl = System.getProperty("fruit.db.url");
        String configuredUrl =
                GameServer.setting(
                        "fruit.db.url", "FRUIT_DB_URL", "jdbc:mysql://localhost:3306/fruit_battle");
        try {
            fresh(configuredUrl);
            occupiedPortGuard(configuredUrl);
            unsupportedSchema(configuredUrl);
        } finally {
            if (previousUrl == null) System.clearProperty("fruit.db.url");
            else System.setProperty("fruit.db.url", previousUrl);
        }
        return checks;
    }

    private static void fourTables(Connection db) throws SQLException {
        Set<String> actual = new HashSet<>();
        try (Statement s = db.createStatement();
                ResultSet r = s.executeQuery("SHOW TABLES")) {
            while (r.next()) actual.add(r.getString(1));
        }
        check(
                actual.equals(Set.of("accounts", "player_stats", "matches", "match_players")),
                "Exactly the four business tables remain");
    }

    private static void fresh(String url) throws Exception {
        isolate(url, "v3_fresh");
        Path data = Files.createTempDirectory("fruit-v3-fresh-");
        User first = account(21, "fresh_alpha", "Táo mới"),
                second = account(22, "fresh_beta", "Nho mới");
        try (Store store = new Store(data, false, false)) {
            Connection db = store.connection.get();
            fourTables(db);
            check(
                    scalar(
                                    db,
                                    "SELECT COUNT(*) FROM information_schema.columns WHERE"
                                        + " table_schema=DATABASE() AND table_name='matches' AND"
                                        + " column_name IN ('result_json','winner_user_id')")
                            == 0,
                    "No JSON result or duplicated winner column");
            store.saveUser(first);
            store.saveUser(second);
            first.avatarId = 1;
            store.saveUser(first);
            expectSqlFailure(
                    db,
                    "UPDATE accounts SET avatar_id=2 WHERE id=21",
                    "Avatar must be one of the two available portraits");
            first.draws = second.draws = 1;
            first.totalScore = second.totalScore = -5;
            Map<String, Object> draw = record(DRAW_ID, first, second, -5, -5, 0);
            draw.put("finishedAt", "2025-02-03T04:06:06.123456Z");
            draw.put("durationMs", 60000);
            store.saveMatch(draw, first, second);
            store.saveMatch(draw, first, second);
            check(
                    scalar(db, "SELECT COUNT(*) FROM matches") == 1
                            && scalar(
                                            db,
                                            "SELECT COUNT(*) FROM match_players WHERE"
                                                    + " outcome='DRAW' AND score=-5")
                                    == 2,
                    "Retry preserves one match, two DRAW rows and negative scores");
            check(
                    scalar(db, "SELECT SUM(draws) FROM player_stats") == 2
                            && scalar(db, "SELECT SUM(total_score) FROM player_stats") == -10,
                    "Retry does not double-count lifetime totals");
            check(
                    scalar(
                                    db,
                                    "SELECT COUNT(*) FROM matches WHERE duration_ms=60000 AND"
                                            + " finished_at='2025-02-03 04:06:06.123456'")
                            == 1,
                    "Microsecond UTC time and duration are stored without timezone shifts");
            first.displayName = "Tên sau trận";
            store.saveUser(first);
            List<Map<String, Object>> restored = new java.util.ArrayList<>();
            MatchRepository.load(db, restored);
            check(
                    players(restored.get(0)).get(0).get("displayName").equals("Táo mới"),
                    "Profile changes do not rewrite match snapshots");
            expectSqlFailure(
                    db,
                    "UPDATE match_players SET slot=3 WHERE slot=1",
                    "Only positions 1 and 2 are allowed");
            expectSqlFailure(
                    db,
                    "UPDATE match_players SET slot=2 WHERE slot=1",
                    "Two participants cannot occupy one position");
            expectSqlFailure(
                    db,
                    "UPDATE match_players SET account_id=999999 WHERE slot=1",
                    "Participant account FK is enforced");
            expectSqlFailure(
                    db,
                    "UPDATE match_players SET match_id='missing-match' WHERE slot=1",
                    "Participant match FK is enforced");
            expectSqlFailure(
                    db,
                    "UPDATE match_players SET correct=-1 WHERE slot=1",
                    "Counters cannot be negative");
            expectSqlFailure(
                    db,
                    "UPDATE matches SET duration_ms=60001",
                    "Duration cannot exceed the playing phase");
            expectSqlFailure(
                    db,
                    "INSERT INTO match_players SELECT * FROM match_players LIMIT 1",
                    "Duplicate participant results are rejected");
            expectSqlFailure(
                    db,
                    "UPDATE accounts SET username='FRESH_BETA' WHERE id=21",
                    "Username uniqueness is case-insensitive");
            expectSaveFailure(store, record(WIN_ID, first, first, 10, 20, first.id), first, first);
            Map<String, Object> missing = record(WIN_ID, first, second, 10, 20, first.id);
            missing.put("players", List.of(players(missing).get(0)));
            expectSaveFailure(store, missing, first, second);
            expectSaveFailure(store, record(WIN_ID, first, second, 10, 20, 999999), first, second);
            Map<String, Object> invalid = record(WIN_ID, first, second, 10, 20, first.id);
            players(invalid).get(0).put("correct", -1);
            expectSaveFailure(store, invalid, first, second);
            db.setAutoCommit(false);
            try (Statement s = db.createStatement()) {
                s.executeUpdate("UPDATE match_players SET outcome='WIN'");
            }
            boolean rejected = false;
            try {
                MatchRepository.load(db, new java.util.ArrayList<>());
            } catch (SQLException expected) {
                rejected = true;
            } finally {
                db.rollback();
                db.setAutoCommit(true);
            }
            check(rejected, "Loading rejects two WIN outcomes in the same match");
            rollback(store, first, second);
        }
        try (Store restarted = new Store(data, false, false)) {
            check(
                    restarted.users.get(first.id).avatarId == 1,
                    "Selected avatar survives SQL reload");
            fourTables(restarted.connection.get());
            check(
                    restarted.users.get(first.id).checkPassword(PASSWORD),
                    "Salt/hash stored in accounts still authenticates after restart");
            check(
                    restarted.records.size() == 1 && restarted.users.get(first.id).draws == 1,
                    "Restart preserves matches and totals");
            try (Statement s = restarted.connection.get().createStatement()) {
                s.executeUpdate(
                        "ALTER TABLE accounts DROP CHECK ck_accounts_avatar, DROP COLUMN"
                                + " avatar_id");
            }
        }
        try (Store upgraded = new Store(data, false, false)) {
            check(
                    upgraded.users.get(first.id).avatarId == 0
                            && upgraded.users.get(first.id).checkPassword(PASSWORD)
                            && upgraded.records.size() == 1,
                    "Adding avatars to an existing schema preserves accounts and matches");
        }
    }

    private static void rollback(Store store, User first, User second) throws Exception {
        Connection db = store.connection.get();
        try (Statement s = db.createStatement()) {
            s.executeUpdate(
                    "CREATE TRIGGER test_reject_result BEFORE INSERT ON match_players FOR EACH ROW"
                            + " BEGIN IF NEW.score=123456789 THEN SIGNAL SQLSTATE '45000' SET"
                            + " MESSAGE_TEXT='intentional rollback'; END IF; END");
        }
        try {
            first.wins = 7;
            first.totalScore = 123456789;
            second.losses = 7;
            second.totalScore = 50;
            expectSaveFailure(
                    store, record(WIN_ID, first, second, 123456789, 50, first.id), first, second);
            check(
                    scalar(db, "SELECT SUM(wins+losses) FROM player_stats") == 0
                            && scalar(db, "SELECT SUM(total_score) FROM player_stats") == -10,
                    "Partial SQL failure rolls back both account statistics");
            check(
                    scalar(db, "SELECT COUNT(*) FROM matches") == 1
                            && scalar(db, "SELECT COUNT(*) FROM match_players") == 2,
                    "Partial SQL failure rolls back header and participant rows");
            check(db.getAutoCommit(), "Transaction failure restores connection mode");
        } finally {
            try (Statement s = db.createStatement()) {
                s.executeUpdate("DROP TRIGGER test_reject_result");
            }
        }
    }

    private static void occupiedPortGuard(String url) throws Exception {
        isolate(url, "port_guard");
        try (DatabaseConnection provider = new DatabaseConnection()) {
            provider.get();
        }
        boolean rejected = false;
        try (ServerSocket socket = new ServerSocket(0)) {
            try (GameServer ignored =
                    new GameServer(
                            socket.getLocalPort(),
                            Files.createTempDirectory("fruit-port-"),
                            false)) {
            } catch (BindException expected) {
                rejected = true;
            }
        }
        check(rejected, "An occupied port is rejected before opening the database");
        try (DatabaseConnection provider = new DatabaseConnection()) {
            check(
                    scalar(
                                    provider.get(),
                                    "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                            + " table_schema=DATABASE()")
                            == 0,
                    "A port conflict does not initialize the database");
        }
    }

    private static void unsupportedSchema(String url) throws Exception {
        isolate(url, "unsupported_schema");
        try (DatabaseConnection provider = new DatabaseConnection();
                Statement statement = provider.get().createStatement()) {
            statement.executeUpdate("CREATE TABLE unsupported_table(id INT PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO unsupported_table VALUES(1)");
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        boolean rejected = false;
        try (GameServer ignored =
                new GameServer(port, Files.createTempDirectory("fruit-schema-"), false)) {
        } catch (SQLException expected) {
            rejected = true;
        }
        check(rejected, "An unsupported schema is rejected without converting or removing it");
        try (ServerSocket socket = new ServerSocket(port)) {
            check(socket.getLocalPort() == port, "Schema failure releases the game port");
        }
        try (DatabaseConnection provider = new DatabaseConnection()) {
            check(
                    scalar(provider.get(), "SELECT COUNT(*) FROM unsupported_table") == 1,
                    "Unsupported database data is left intact");
            check(
                    scalar(
                                    provider.get(),
                                    "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                            + " table_schema=DATABASE()")
                            == 1,
                    "No new tables are added to an unsupported database");
        }
    }

    private static User account(int id, String username, String name) throws Exception {
        User user = new User(id, username, name);
        user.changePassword(PASSWORD);
        return user;
    }

    private static Map<String, Object> record(
            String id, User first, User second, int firstScore, int secondScore, int winner) {
        return obj(
                "matchId",
                id,
                "createdAt",
                CREATED_AT,
                "winnerId",
                winner,
                "reason",
                "TIME_UP",
                "players",
                List.of(participant(first, firstScore), participant(second, secondScore)));
    }

    private static Map<String, Object> participant(User user, int score) {
        return obj(
                "id",
                user.id,
                "username",
                user.username,
                "displayName",
                user.displayName,
                "score",
                score,
                "correct",
                3,
                "wrong",
                2,
                "bombs",
                1,
                "maxCombo",
                3,
                "skills",
                2);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> record) {
        return (List<Map<String, Object>>) record.get("players");
    }

    private static void isolate(String configuredUrl, String suffix) {
        String prefix = "jdbc:mysql://";
        if (!configuredUrl.startsWith(prefix))
            throw new IllegalArgumentException("MySQL JDBC URL required");
        int slash = configuredUrl.indexOf('/', prefix.length());
        if (slash < 0)
            throw new IllegalArgumentException("Database path required in MySQL JDBC URL");
        int query = configuredUrl.indexOf('?', slash);
        String options =
                query < 0 ? "?createDatabaseIfNotExist=true" : configuredUrl.substring(query);
        if (!options.contains("createDatabaseIfNotExist="))
            options += "&createDatabaseIfNotExist=true";
        else
            options =
                    options.replaceAll(
                            "createDatabaseIfNotExist=[^&]*", "createDatabaseIfNotExist=true");
        String database =
                "fruit_battle_test_" + suffix + "_" + Long.toUnsignedString(System.nanoTime());
        System.setProperty(
                "fruit.db.url", configuredUrl.substring(0, slash + 1) + database + options);
        System.out.println("Database schema audit retained: " + database);
    }

    private static long scalar(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new AssertionError("Missing scalar result: " + sql);
            return rows.getLong(1);
        }
    }

    private static void expectSqlFailure(Connection db, String sql, String description)
            throws Exception {
        boolean rejected = false;
        try (Statement statement = db.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException expected) {
            rejected = true;
        }
        check(rejected, description);
    }

    private static void expectSaveFailure(
            Store store, Map<String, Object> record, User first, User second) throws Exception {
        boolean rejected = false;
        try {
            store.saveMatch(record, first, second);
        } catch (Exception expected) {
            rejected = true;
        }
        check(rejected, "Invalid result or forced SQL failure is rejected");
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
}

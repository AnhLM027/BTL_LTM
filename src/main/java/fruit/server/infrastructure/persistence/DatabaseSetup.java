package fruit.server.infrastructure.persistence;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

/** Creates/checks the current four-table database without starting a game server. */
public final class DatabaseSetup {
    private DatabaseSetup() {}

    public static void main(String[] args) throws Exception {
        try (Store store = new Store(Path.of("data"), false, false);
                Statement statement = store.connection.get().createStatement();
                ResultSet rows = statement.executeQuery("SHOW TABLES")) {
            while (rows.next()) System.out.println("Table: " + rows.getString(1));
            System.out.println(
                    "Database ready: "
                            + store.users.size()
                            + " accounts, "
                            + store.records.size()
                            + " completed matches");
        }
    }
}

package fruit.server.application;

/** Output port: the engine can run with a console or silently in integration tests. */
public interface ServerDisplay {
    default void info(String message) {}

    default void warning(String message) {}

    default void error(Exception error) {}

    static ServerDisplay quiet() {
        return new ServerDisplay() {};
    }
}

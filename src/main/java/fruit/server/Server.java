package fruit.server;

import fruit.common.Protocol;
import fruit.server.application.GameServer;
import fruit.server.application.ServerDisplay;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Server entry point; no JavaFX dependency. */
public final class Server implements ServerDisplay {
    private Server() {}

    public static void main(String[] args) throws Exception {
        int port = Protocol.PORT;
        boolean local = false;
        Path data = Path.of("data");
        for (String arg : args) {
            if (arg.equals("--local")) local = true;
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else if (arg.startsWith("--data=")) data = Path.of(arg.substring(7));
            else if (arg.matches("\\d+")) port = Integer.parseInt(arg);
            else
                throw new IllegalArgumentException(
                        "Usage: Server [--port=5555] [--local] [--data=data]");
        }
        GameServer server = new GameServer(port, data, false, local, new Server());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "fruit-shutdown"));
        server.start();
        new CountDownLatch(1).await();
    }

    @Override
    public void info(String message) {
        System.out.println(message);
    }

    @Override
    public void warning(String message) {
        System.err.println(message);
    }

    @Override
    public void error(Exception error) {
        error.printStackTrace(System.err);
    }
}

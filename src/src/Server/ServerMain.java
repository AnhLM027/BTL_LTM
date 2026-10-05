package Server;

import Server.network.GameServerEventHandler;
import Server.network.SessionRegistry;
import Server.network.TcpServer;
import Server.repository.AccountRepository;
import Server.repository.GameModeRepository;
import Server.repository.InviteRepository;
import Server.repository.PlayerRepository;
import Server.repository.RoomRepository;
import Server.service.AuthService;
import Server.service.RoomService;
import Server.service.MatchService;
import Server.repository.MatchRepository;
import Server.repository.FruitRepository;
import Server.repository.CatchEventRepository;

import java.io.IOException;

/**
 * Entry point for the new TCP server. Business handlers are added in the next phases.
 */
public final class ServerMain {
    private static final int DEFAULT_PORT = 12345;

    private ServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        SessionRegistry sessions = new SessionRegistry();
        GameServerEventHandler eventHandler = new GameServerEventHandler(
                sessions,
                new AuthService(new AccountRepository()),
                new PlayerRepository(),
                new RoomService(new GameModeRepository(), new RoomRepository(), new InviteRepository()),
                new MatchService(new RoomRepository(), new MatchRepository(), new FruitRepository(), new GameModeRepository(), new CatchEventRepository())
        );
        TcpServer server = new TcpServer(port, eventHandler, sessions);
        eventHandler.recoverMatches();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            eventHandler.close();
            server.close();
        }));
        System.out.println("Fruit Battle TCP server listening on port " + port);
        server.start();
    }
}

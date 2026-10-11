package Client.view;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only top-level window used by the player flow.
 * Screens are lightweight panels swapped through CardLayout.
 */
public final class MainClientFrame extends JFrame {
    private static MainClientFrame instance;

    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final Map<String, JPanel> installedCards = new HashMap<>();
    private JPanel current;
    private ProtocolMessage previousRoom;

    private MainClientFrame() {
        super("Hứng Hoa Quả — Thi đấu đối kháng online");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(810, 540));
        setSize(1_080, 760);
        setLocationRelativeTo(null);
        setContentPane(deck);
    }

    public static synchronized MainClientFrame instance() {
        if (instance == null) instance = new MainClientFrame();
        return instance;
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            MainClientFrame frame = instance();
            frame.showMain();
            frame.setVisible(true);
        });
    }

    public void showMain() {
        showScreen("MAIN", new MainScreen());
    }

    public void showLogin() {
        showScreen("LOGIN", new LoginScreen());
    }

    public void showRegister() {
        showScreen("REGISTER", new RegisterScreen());
    }

    public void showLobby() {
        GameClientSession.Identity identity = GameClientSession.instance().identity();
        showScreen("LOBBY", new LobbyScreen(Long.toString(identity.playerId()), identity.displayName()));
    }

    public void showProfile() {
        GameClientSession.Identity identity = GameClientSession.instance().identity();
        showScreen("PROFILE", new ProfileScreen(Long.toString(identity.playerId())));
    }

    public void showHistory() {
        GameClientSession.Identity identity = GameClientSession.instance().identity();
        showScreen("HISTORY", new HistoryScreen(Long.toString(identity.playerId())));
    }

    public void showRanking() {
        showScreen("RANKING", new RankScreen());
    }

    public void showWaitingRoom(long playerId, ProtocolMessage room) {
        rememberRoom(room);
        showScreen("WAITING_ROOM", new WaitingRoomScreen(playerId, room));
    }

    /** Keeps the latest server room snapshot while the room view remains open. */
    void rememberRoom(ProtocolMessage room) {
        if (room != null && "ROOM_UPDATED".equals(room.type())) previousRoom = room;
    }

    public void showGame(TcpGameClient client, long playerId, ProtocolMessage prepareOrRejoin) {
        RunGame game = new RunGame(client, playerId);
        showScreen("GAME", game);
        game.onMessage(prepareOrRejoin);
    }

    public RunGame currentGame() {
        return current instanceof RunGame game ? game : null;
    }

    public void showEndGame(String result, String myScore, String opponentScore) {
        showScreen("END_GAME", new EndGameScreen(result, myScore, opponentScore, this::showPreviousRoom));
    }

    /** Restores the room that hosted the match after the authoritative result. */
    public void showPreviousRoom() {
        if (previousRoom == null) {
            showLobby();
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>(previousRoom.fields());
        // The server resets the room to FULL after persisting the match
        // result, allowing both players to start another match immediately.
        fields.put("roomState", "FULL");
        fields.put("hostReady", "false");
        fields.put("guestReady", "false");
        fields.put("matchId", "");
        GameClientSession.Identity identity = GameClientSession.instance().identity();
        showWaitingRoom(identity.playerId(), new ProtocolMessage("ROOM_UPDATED", fields));
    }

    private void showScreen(String name, JPanel next) {
        if (current instanceof ClientScreen screen) screen.onHide();
        JPanel previousAtName = installedCards.put(name, next);
        if (previousAtName != null) deck.remove(previousAtName);
        deck.add(next, name);
        current = next;
        cards.show(deck, name);
        if (next instanceof ClientScreen screen) screen.onShow();
        deck.revalidate();
        deck.repaint();
        requestFocus();
    }
}

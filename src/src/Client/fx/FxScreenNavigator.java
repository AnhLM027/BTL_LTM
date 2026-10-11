package Client.fx;

import Server.network.ProtocolMessage;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.LinkedHashMap;
import java.util.List;

/** Single JavaFX window for the new client flow. */
public final class FxScreenNavigator {
    private static volatile FxScreenNavigator current;
    public static final double DESIGN_WIDTH = 1080;
    public static final double DESIGN_HEIGHT = 760;
    private final Stage stage;
    private ProtocolMessage previousRoom;

    public FxScreenNavigator(Stage stage) {
        current = this;
        this.stage = stage;
        stage.setTitle("Fruit Battle Online");
        stage.setMinWidth(810);
        stage.setMinHeight(540);
    }

    public static FxScreenNavigator current() { return current; }

    public FxRunGameView currentGame() {
        return stage.getScene() != null && stage.getScene().getRoot() instanceof FxRunGameView game
                ? game : null;
    }

    public void showMain() {
        show(new FxMainView(this));
    }

    public void showLogin() {
        show(new FxLoginView(this));
    }

    public void showRegister() {
        show(new FxRegisterView(this));
    }

    public void showLobby() {
        show(new FxLobbyView(this));
    }

    public void showWaitingRoom(ProtocolMessage room) {
        rememberRoom(room);
        show(new FxWaitingRoomView(this, room));
    }

    /** Keeps the latest server room snapshot while the room view remains open. */
    void rememberRoom(ProtocolMessage room) {
        if (room != null && "ROOM_UPDATED".equals(room.type())) previousRoom = room;
    }

    public void showGame(ProtocolMessage prepare) {
        show(new FxRunGameView(this, prepare));
    }

    public void showGameSnapshot(List<ProtocolMessage> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        showGame(snapshot.get(0));
        FxRunGameView game = currentGame();
        if (game != null) for (ProtocolMessage message : snapshot.subList(1, snapshot.size())) game.onMessage(message);
    }

    public void showGamePreview() { show(FxRunGameView.preview(this)); }

    public void showResult(ProtocolMessage result) {
        show(new FxEndGameView(this, result));
    }

    /** Returns to the room that hosted the completed match, marked as closed. */
    public void showPreviousRoom() {
        if (previousRoom == null) {
            showLobby();
            return;
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(previousRoom.fields());
        // Match history is already stored server-side; the retained room is
        // reusable and therefore returns as FULL with readiness cleared.
        fields.put("roomState", "FULL");
        fields.put("hostReady", "false");
        fields.put("guestReady", "false");
        fields.put("matchId", "");
        showWaitingRoom(new ProtocolMessage("ROOM_UPDATED", fields));
    }

    public void showProfile() { show(new FxDataView(this, FxDataView.Kind.PROFILE)); }
    public void showHistory() { show(new FxDataView(this, FxDataView.Kind.HISTORY)); }
    public void showRanking() { show(new FxDataView(this, FxDataView.Kind.RANKING)); }
    public void showAdmin() { show(new FxAdminView(this)); }

    public void show(Parent view) {
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof FxLobbyView oldLobby) {
            oldLobby.dispose();
        }
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof FxWaitingRoomView oldRoom) {
            oldRoom.dispose();
        }
        if (stage.getScene() != null && stage.getScene().getRoot() instanceof FxRunGameView oldGame) {
            oldGame.dispose();
        }
        if (stage.getScene() == null) stage.setScene(new Scene(view, DESIGN_WIDTH, DESIGN_HEIGHT));
        else stage.getScene().setRoot(view);
        stage.show();
        stage.centerOnScreen();
    }
}

package Client.fx;

import Client.ui.AssetPaths;
import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pixel-art waiting room using the MainClientFrame v4 component set. */
public final class FxWaitingRoomView extends StackPane implements TcpGameClient.MessageListener {
    private static final double DESIGN_W = 1080;
    private static final double DESIGN_H = 760;
    private static final String ASSET_ROOT = AssetPaths.WAITING_ROOM;

    private final FxScreenNavigator navigator;
    private final GameClientSession session = GameClientSession.instance();
    private final long playerId;
    private final Pane design = new Pane();
    private final Label roomLabel = textLabel("Phòng đang tải...", 18, Color.web("#4f2d18"));
    private final Label hostName = textLabel("Đang tải...", 22, Color.web("#4f2d18"));
    private final Label hostSubtitle = textLabel("", 14, Color.web("#4f2d18"));
    private final Label hostReady = textLabel("", 16, Color.WHITE);
    private final Label guestName = textLabel("Đang chờ đối thủ", 22, Color.web("#4f2d18"));
    private final Label guestSubtitle = textLabel("Mời bạn vào chơi cùng", 14, Color.web("#4f2d18"));
    private final Label guestReady = textLabel("", 16, Color.WHITE);
    private final Label roomStatus = textLabel("Đang tải trạng thái phòng...", 15, Color.web("#4f2d18"));
    private final Label fruitRule = textLabel("Hứng quả +10", 15, Color.web("#4f2d18"));
    private final Label bombRule = textLabel("Trúng bom −10", 15, Color.web("#4f2d18"));
    private final Label timeRule = textLabel("30 giây", 15, Color.web("#4f2d18"));
    private final Label moveRule = textLabel("Di chuyển giỏ", 15, Color.web("#4f2d18"));
    private final ComboBox<String> mode = new ComboBox<>();
    private final Button inviteButton = imageButton("buttons/green.png", "Mời người chơi", 198, 47);
    private final Button startButton = imageButton("buttons/disabled.png", "Bắt đầu trận", 216, 50);
    private final Button leaveButton = imageButton("buttons/orange.png", "Rời phòng", 176, 50);
    private final ImageView hostAvatar = image("avatars/player.png", 166, 166);
    private final ImageView guestAvatar = image("avatars/waiting.png", 166, 166);
    private final List<String> onlineEntries = new ArrayList<>();
    private final List<ProtocolMessage> pendingMatchSnapshot = new ArrayList<>();
    private final Pane inviteOverlay = new Pane();
    private final ListView<String> inviteList = new ListView<>();
    private FxInviteOverlay incomingInviteOverlay;

    private long roomId = -1;
    private long matchId = -1;
    private long hostPlayerId = -1;
    private boolean hasGuest;
    private String roomState = "WAITING";
    private boolean applyingRoomUpdate;
    private boolean bufferingMatchSnapshot;

    public FxWaitingRoomView(FxScreenNavigator navigator, ProtocolMessage initial) {
        this.navigator = navigator;
        this.playerId = session.identity().playerId();
        getStyleClass().add("fx-waiting-room");
        setMinWidth(810);
        setMinHeight(540);
        setBackground(new Background(new BackgroundImage(
                FxAssets.load("backgrounds/room1.png"),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(1, 1, true, true, false, true))));

        design.setPrefSize(DESIGN_W, DESIGN_H);
        design.setMinSize(DESIGN_W, DESIGN_H);
        design.setMaxSize(DESIGN_W, DESIGN_H);
        getChildren().add(design);
        StackPane.setAlignment(design, Pos.CENTER);
        bindDesignScale();
        buildDesign();
        loadStylesheet();

        mode.getItems().setAll("CLASSIC", "ORDER");
        mode.setValue("CLASSIC");
        mode.getStyleClass().add("room-mode-selector");
        mode.setOnAction(event -> changeMode());
        inviteButton.setOnAction(event -> openInviteOverlay());
        startButton.setOnAction(event -> sendStart());
        leaveButton.setOnAction(event -> sendLeave());

        session.client().addListener(this);
        if (initial != null) handle(initial);
        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
        // Refresh the server-owned room state; this is especially important
        // when the view was opened from the post-match result screen.
        send(ProtocolMessage.of("GET_ROOM_SNAPSHOT"));
    }

    private void bindDesignScale() {
        var scale = Bindings.createDoubleBinding(
                () -> Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H),
                widthProperty(), heightProperty());
        design.scaleXProperty().bind(scale);
        design.scaleYProperty().bind(scale);
        design.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> (getWidth() - DESIGN_W * scale.get()) / 2,
                widthProperty(), heightProperty()));
        design.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> (getHeight() - DESIGN_H * scale.get()) / 2,
                widthProperty(), heightProperty()));
    }

    private void buildDesign() {
        add(image("icons/logo.png", 151, 90), 50, 28);
        add(image("panels/header.png", 504, 108), 288, 30);
        add(textLabel("PHÒNG CHỜ", 34, Color.web("#4f2d18")), 288, 70, 504, 49);
        add(roomLabel, 390, 153, 300, 30);
        roomLabel.setAlignment(Pos.CENTER);

        add(image("panels/player_blue.png", 288, 306), 154, 200);
        add(image("panels/player_orange.png", 288, 306), 638, 200);
        add(hostAvatar, 228, 225, 135, 135);
        add(guestAvatar, 712, 225, 135, 135);
        add(image("icons/vs.png", 122, 85), 479, 320);

        add(hostName, 160, 370, 276, 32);
        add(hostSubtitle, 160, 400, 276, 23);
        add(image("buttons/blue.png", 198, 47), 199, 430);
        add(hostReady, 199, 430, 198, 47);
        add(guestName, 644, 370, 276, 32);
        add(guestSubtitle, 644, 400, 276, 23);
        add(image("buttons/blue.png", 198, 47), 683, 430);
        add(guestReady, 683, 430, 198, 47);
        add(inviteButton, 683, 430);
        hostReady.setAlignment(Pos.CENTER);
        guestReady.setAlignment(Pos.CENTER);
        guestReady.setVisible(false);

        add(image("panels/mode2.png", 886, 83), 97, 565);
        add(textLabel("CHẾ ĐỘ", 15, Color.web("#4f2d18")), 114, 594, 81, 27);
        add(mode, 205, 587, 135, 40);
        add(image("icons/apple.png", 25, 25), 361, 595);
        add(fruitRule, 392, 594, 108, 27);
        add(image("icons/bomb.png", 25, 25), 516, 595);
        add(bombRule, 547, 594, 112, 27);
        add(image("icons/clock.png", 25, 25), 676, 595);
        add(timeRule, 707, 594, 81, 27);
        add(image("icons/arrow_left.png", 25, 20), 800, 598);
        add(image("icons/arrow_right.png", 25, 20), 827, 598);
        add(moveRule, 857, 594, 112, 27);

        add(leaveButton, 58, 690, 176, 50);
        add(image("panels/waiting_status.png", 176, 40), 600, 697);
        add(roomStatus, 600, 697, 176, 40);
        roomStatus.setAlignment(Pos.CENTER);
        add(startButton, 816, 690, 216, 50);
        startButton.setDisable(true);
        startButton.disableProperty().addListener((observable, oldValue, disabled) ->
                setButtonImage(startButton, disabled ? "buttons/disabled.png" : "buttons/green.png", 216, 50));

        buildInviteOverlay();
    }

    private void buildInviteOverlay() {
        ImageView panel = image("panels/invite2.png", 640, 430);
        Label title = textLabel("Chọn người chơi để mời", 22, Color.web("#4f2d18"));
        title.setAlignment(Pos.CENTER);
        title.setLayoutX(65);
        title.setLayoutY(45);
        title.setPrefSize(510, 40);

        inviteList.setLayoutX(78);
        inviteList.setLayoutY(105);
        inviteList.setPrefSize(540, 280);
        inviteList.setMinSize(540, 280);
        inviteList.setMaxSize(540, 280);
        inviteList.setFixedCellSize(48);
        inviteList.getStyleClass().add("invite-list");
        inviteList.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                String[] parts = value.split("\\|", 3);
                String id = parts[0];
                String name = parts.length > 1 ? parts[1].trim() : "Người chơi";
                String state = parts.length > 2 ? parts[2] : "ONLINE";
                Label label = new Label(name + " · " + onlineState(state));
                label.setFont(Font.font(FxAssets.FONT_FAMILY, 14));
                label.setTextFill(Color.web("#4f2d18"));
                Button invite = new Button("Mời");
                invite.getStyleClass().add("secondary-button");
                invite.setMinSize(88, 38);
                invite.setPrefSize(88, 38);
                invite.setMaxSize(88, 38);
                invite.setDisable(!("ONLINE".equals(state) || "WAITING".equals(state)));
                invite.setOnAction(event -> {
                    send(new ProtocolMessage("INVITE_PLAYER", Map.of(
                            "roomId", Long.toString(roomId), "playerId", id)));
                    roomStatus.setText("Đã gửi lời mời · chờ phản hồi");
                    inviteOverlay.setVisible(false);
                });
                HBox row = new HBox(16, label, invite);
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(label, Priority.ALWAYS);
                setGraphic(row);
            }
        });

        Button close = new Button();
        close.setLayoutX(574);
        close.setLayoutY(14);
        close.setPrefSize(48, 48);
        close.setMinSize(48, 48);
        close.setMaxSize(48, 48);
        close.setGraphic(image("panels/close_48x48.png", 48, 48));
        close.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-cursor: hand;");
        close.setOnAction(event -> inviteOverlay.setVisible(false));

        inviteOverlay.getChildren().setAll(panel, title, inviteList, close);
        add(inviteOverlay, 220, 145, 640, 430);
        inviteOverlay.setVisible(false);
    }

    private void updateModeRules() {
        boolean order = "ORDER".equals(mode.getValue());
        fruitRule.setText(order ? "Hứng theo đơn" : "Quả +10");
        bombRule.setText(order ? "Ngoài đơn -5" : "Bom -10");
    }

    private void add(Node node, double x, double y) {
        node.setLayoutX(x);
        node.setLayoutY(y);
        design.getChildren().add(node);
    }

    private void add(Node node, double x, double y, double width, double height) {
        node.setLayoutX(x);
        node.setLayoutY(y);
        if (node instanceof Region region) {
            region.setMinSize(width, height);
            region.setPrefSize(width, height);
            region.setMaxSize(width, height);
        } else if (node instanceof ImageView view) {
            view.setFitWidth(width);
            view.setFitHeight(height);
        }
        design.getChildren().add(node);
    }

    private ImageView image(String path, double width, double height) {
        ImageView view = FxAssets.image(ASSET_ROOT + path);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);
        view.setSmooth(false);
        return view;
    }

    private static Label textLabel(String text, double size, Color color) {
        Label label = new Label(text);
        label.setFont(Font.font(FxAssets.FONT_FAMILY, size));
        label.setTextFill(color);
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        label.getStyleClass().add("room-design-label");
        return label;
    }

    private Button imageButton(String path, String text, double width, double height) {
        Button button = new Button(text);
        button.setMinSize(width, height);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);
        button.setFont(Font.font(FxAssets.FONT_FAMILY, 18));
        button.setTextFill(Color.WHITE);
        button.setStyle("-fx-background-insets: 0; -fx-padding: 0;");
        setButtonImage(button, path, width, height);
        return button;
    }

    private void setButtonImage(Button button, String path, double width, double height) {
        Image image = FxAssets.load(ASSET_ROOT + path);
        if (image == null) return;
        var resource = getClass().getResource("/Client/assets/" + ASSET_ROOT + path);
        if (resource == null) return;
        button.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
                + " -fx-background-image: url('" + resource.toExternalForm() + "');"
                + " -fx-background-repeat: no-repeat; -fx-background-position: center;"
                + " -fx-background-size: " + width + "px " + height + "px;");
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }

    private void changeMode() {
        if (applyingRoomUpdate || roomId <= 0 || hostPlayerId != playerId
                || !("WAITING".equals(roomState) || "FULL".equals(roomState))) return;
        updateModeRules();
        send(new ProtocolMessage("CHANGE_GAME_MODE", Map.of(
                "roomId", Long.toString(roomId), "modeCode", mode.getValue())));
    }

    private void sendStart() {
        if (roomId > 0) send(new ProtocolMessage("START_MATCH", Map.of("roomId", Long.toString(roomId))));
    }

    private void sendLeave() {
        if (roomId > 0) send(new ProtocolMessage("LEAVE_ROOM", Map.of("roomId", Long.toString(roomId))));
    }

    private void openInviteOverlay() {
        if (hostPlayerId != playerId || hasGuest) {
            roomStatus.setText("Phòng đã có đối thủ hoặc bạn không phải chủ phòng.");
            return;
        }
        inviteList.getItems().setAll(onlineEntries);
        inviteOverlay.setVisible(true);
        inviteOverlay.toFront();
        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
    }

    private void inviteNotification(ProtocolMessage message) {
        if (incomingInviteOverlay != null) getChildren().remove(incomingInviteOverlay);
        incomingInviteOverlay = new FxInviteOverlay(message,
                accepted -> send(new ProtocolMessage(
                        accepted ? "INVITE_ACCEPT" : "INVITE_REJECT",
                        Map.of("inviteId", message.fields().getOrDefault("inviteId", "")))));
        getChildren().add(incomingInviteOverlay);
        StackPane.setAlignment(incomingInviteOverlay, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(incomingInviteOverlay, new javafx.geometry.Insets(0, 18, 18, 0));
        incomingInviteOverlay.toFront();
    }

    private void showInviteSent(ProtocolMessage message) {
        roomStatus.setText("Đã gửi lời mời");
        // Alert alert = new Alert(Alert.AlertType.INFORMATION,
        //         "Lời mời đã được gửi đến người chơi.");
        // FxAssets.styleDialog(alert.getDialogPane());
        // alert.setTitle("Mời người chơi");
        // alert.setHeaderText("Gửi lời mời thành công");
        // alert.show();
    }

    private void send(ProtocolMessage message) {
        try { session.client().send(message); }
        catch (IOException error) { roomStatus.setText("Mất kết nối máy chủ."); }
    }

    @Override public void onMessage(ProtocolMessage message) {
        Platform.runLater(() -> handle(message));
    }

    private void handle(ProtocolMessage message) {
        if (bufferingMatchSnapshot && isMatchSnapshotMessage(message)) {
            pendingMatchSnapshot.add(message);
            if ("MATCH_PREPARE_END".equals(message.type())) {
                bufferingMatchSnapshot = false;
                List<ProtocolMessage> snapshot = List.copyOf(pendingMatchSnapshot);
                pendingMatchSnapshot.clear();
                navigator.showGameSnapshot(snapshot);
            }
            return;
        }
        switch (message.type()) {
            case "ROOM_UPDATED" -> applyRoomUpdate(message);
            case "ONLINE_PLAYERS_BEGIN" -> { onlineEntries.clear(); inviteList.getItems().clear(); }
            case "ONLINE_PLAYER" -> receiveOnlinePlayer(message);
            case "INVITE_NOTIFICATION" -> inviteNotification(message);
            case "INVITE_SENT" -> showInviteSent(message);
            case "INVITE_RESULT" -> roomStatus.setText("Đã cập nhật trạng thái lời mời.");
            case "MATCH_READY_ACK" -> roomStatus.setText("Đã sẵn sàng, đang chờ đối thủ...");
            case "MATCH_PREPARE" -> {
                bufferingMatchSnapshot = true;
                pendingMatchSnapshot.clear();
                pendingMatchSnapshot.add(message);
            }
            case "MATCH_CANCELLED" -> navigator.showLobby();
            case "ROOM_LEFT" -> navigator.showLobby();
            case "ERROR", "ROOM_ACTION_REJECTED" -> roomStatus.setText(
                    message.fields().getOrDefault("message", "Yêu cầu bị từ chối."));
            default -> { }
        }
    }

    private boolean isMatchSnapshotMessage(ProtocolMessage message) {
        return switch (message.type()) {
            case "FRUIT_CONFIG", "BASKET_CONFIG", "FRUIT_SPAWN", "MATCH_PREPARE_END" -> true;
            default -> false;
        };
    }

    private void receiveOnlinePlayer(ProtocolMessage message) {
        String id = message.fields().getOrDefault("playerId", "");
        if (!id.equals(Long.toString(playerId))) {
            String entry = id + "| " + message.fields().getOrDefault("displayName", "Người chơi")
                    + "|" + message.fields().getOrDefault("status", "ONLINE");
            if (!onlineEntries.contains(entry)) {
                onlineEntries.add(entry);
                // The response is asynchronous; update the already-open overlay too.
                if (inviteOverlay.isVisible()) inviteList.getItems().add(entry);
            }
        }
    }

    private void applyRoomUpdate(ProtocolMessage message) {
        navigator.rememberRoom(message);
        roomId = longValue(message, "roomId", -1);
        matchId = longValue(message, "matchId", -1);
        hostPlayerId = longValue(message, "hostPlayerId", -1);
        hasGuest = !message.fields().getOrDefault("guestPlayerId", "").isBlank();
        if (hasGuest) inviteOverlay.setVisible(false);
        roomState = message.fields().getOrDefault("roomState", "WAITING");
        applyingRoomUpdate = true;
        try {
            mode.setValue(message.fields().getOrDefault("modeCode", modeCode(message.fields().get("modeId"))));
        } finally {
            applyingRoomUpdate = false;
        }
        updateModeRules();

        String guestId = message.fields().getOrDefault("guestPlayerId", "");
        boolean localHost = hostPlayerId == playerId;
        roomLabel.setText("Phòng #" + roomId + " · " + (hasGuest ? "2/2 người chơi" : "1/2 người chơi"));
        hostName.setText(localHost ? session.identity().displayName() : "Người chơi #" + hostPlayerId);
        hostSubtitle.setText(localHost ? "Chủ phòng · Bạn" : "Chủ phòng");
        hostReady.setText(readyText(message.fields().get("hostReady")));

        if (hasGuest) {
            boolean localGuest = guestId.equals(Long.toString(playerId));
            guestName.setText(localGuest ? session.identity().displayName() : "Người chơi #" + guestId);
            guestSubtitle.setText(localGuest ? "Khách · Bạn" : "Đối thủ");
            guestReady.setText(readyText(message.fields().get("guestReady")));
            guestAvatar.setImage(FxAssets.load(ASSET_ROOT + "avatars/player.png"));
            guestReady.setVisible(true);
            inviteButton.setVisible(false);
        } else {
            guestName.setText("Đang chờ đối thủ");
            guestSubtitle.setText("Mời bạn vào chơi cùng");
            guestReady.setText("");
            guestAvatar.setImage(FxAssets.load(ASSET_ROOT + "avatars/waiting.png"));
            guestReady.setVisible(false);
            inviteButton.setVisible(true);
        }
        boolean canStart = localHost && hasGuest && "FULL".equals(roomState);
        boolean canInvite = localHost && !hasGuest && "WAITING".equals(roomState);
        mode.setDisable(!localHost || !("WAITING".equals(roomState) || "FULL".equals(roomState)));
        inviteButton.setDisable(!canInvite);
        startButton.setDisable(!canStart);
        roomStatus.setText(roomStateText(roomState));
    }

    private String readyText(String value) {
        return Boolean.parseBoolean(value) ? "Đã sẵn sàng" : "Đã vào phòng";
    }

    private String onlineState(String value) {
        return switch (value == null ? "ONLINE" : value) {
            case "IN_ROOM", "WAITING" -> "Đang ở phòng";
            case "READY" -> "Đã sẵn sàng";
            case "PLAYING" -> "Đang thi đấu";
            default -> "Sẵn sàng";
        };
    }

    private String roomStateText(String value) {
        return switch (value) {
            case "FULL" -> "Đang chờ sẵn sàng";
            case "PREPARING" -> "Đang chuẩn bị trận";
            case "READY" -> "Cả hai đã sẵn sàng";
            case "PLAYING" -> "Đang thi đấu";
            case "FINISHED" -> "Trận đã kết thúc · phòng đang được đặt lại";
            default -> "Đang chờ đối thủ";
        };
    }

    private String modeCode(String value) {
        return "2".equals(value) ? "ORDER" : "CLASSIC";
    }

    private long longValue(ProtocolMessage message, String field, long fallback) {
        try { return Long.parseLong(message.fields().getOrDefault(field, Long.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    public void dispose() {
        session.client().removeListener(this);
    }
}

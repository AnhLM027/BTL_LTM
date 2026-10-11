package Client.fx;

import Client.network.TcpGameClient;
import Client.session.GameClientSession;
import Client.ui.AssetPaths;
import Server.network.ProtocolMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JavaFX lobby matching the Swing dashboard layout and TCP flow. */
public final class FxLobbyView extends StackPane implements TcpGameClient.MessageListener {
    private final FxScreenNavigator navigator;
    private final GameClientSession session = GameClientSession.instance();
    private final Label status = new Label("Đang tải dữ liệu...");
    private final Label online = new Label("Người chơi online: —");
    private final Label score = new Label("Tổng điểm: —");
    private final Label guideText = new Label();
    private final Button create = new Button("＋ TẠO PHÒNG");
    private final ComboBox<String> mode = new ComboBox<>();
    private final FlowPane fruitGrid = new FlowPane();
    private final List<FruitItem> fruitCatalog = new ArrayList<>();
    private final Map<String, String> modeDescriptions = new HashMap<>();
    private final VBox discoveryCard = new VBox();
    private FxInviteOverlay incomingInviteOverlay;

    public FxLobbyView(FxScreenNavigator navigator) {
        this.navigator = navigator;
        getStyleClass().add("fx-lobby-view");

        ImageView background = FxAssets.image("backgrounds/bg1.gif");
        background.setPreserveRatio(false);
        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());

        VBox surface = buildSurface();
        ScrollPane scroll = new ScrollPane(surface);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("lobby-scroll");
        StackPane.setAlignment(scroll, Pos.CENTER);
        StackPane.setMargin(scroll, new Insets(12));

        getChildren().addAll(background, scroll);
        loadStylesheet();
        session.client().addListener(this);
        send(ProtocolMessage.of("GET_PROFILE"));
        send(ProtocolMessage.of("GET_ONLINE_PLAYERS"));
        send(ProtocolMessage.of("GET_FRUIT_CATALOG"));
        send(ProtocolMessage.of("GET_GAME_MODES"));
        send(ProtocolMessage.of("GET_ROOM_SNAPSHOT"));
    }

    private VBox buildSurface() {
        Label title = new Label("FRUIT BATTLE ONLINE");
        title.getStyleClass().add("lobby-brand");
        ImageView logo = FxAssets.image("logo/logo-fruit-basket.png");
        logo.setFitWidth(54);
        logo.setFitHeight(54);
        logo.setPreserveRatio(true);
        ImageView connection = FxAssets.image("icons/connection-online.png");
        connection.setFitWidth(24);
        connection.setFitHeight(24);
        connection.setPreserveRatio(true);
        Label greeting = new Label("Xin chào, " + session.identity().displayName());
        greeting.getStyleClass().add("lobby-greeting");
        score.getStyleClass().add("lobby-stat");
        online.getStyleClass().add("lobby-stat");
        status.getStyleClass().add("lobby-status");

        HBox header = new HBox(14, logo, title, spacer(), score, connection);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("lobby-header");
        HBox meta = new HBox(20, greeting, spacer(), online);
        meta.setAlignment(Pos.CENTER_LEFT);
        meta.getStyleClass().add("lobby-meta");

        HBox cards = new HBox(16, buildStartCard(), buildGuideCard());
        cards.setFillHeight(true);
        HBox.setHgrow(cards.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(cards.getChildren().get(1), Priority.ALWAYS);

        buildDiscoveryCard();

        Button profile = navButton("Hồ sơ", event -> navigator.showProfile());
        Button history = navButton("Lịch sử đấu", event -> navigator.showHistory());
        Button ranking = navButton("Bảng xếp hạng", event -> navigator.showRanking());
        Button logout = navButton("Đăng xuất", event -> logout());
        HBox navigation = new HBox(10, profile, history, ranking, logout);
        navigation.setAlignment(Pos.CENTER);
        navigation.getStyleClass().add("lobby-navigation");

        VBox body = new VBox(8, header, new Separator(), meta, cards, discoveryCard, navigation);
        body.setPadding(new Insets(12, 24, 10, 24));
        body.setMaxWidth(1040);
        body.getStyleClass().add("lobby-surface");
        return body;
    }

    private VBox buildStartCard() {
        Label heading = sectionTitle("BẮT ĐẦU CHƠI");
        Label subtitle = new Label("Thi đấu 1 vs 1 trong 30 giây");
        subtitle.getStyleClass().add("card-description");
        Label rules = new Label("Hứng đúng: +10    Hứng ngoài đơn: -5");
        rules.getStyleClass().add("card-description");
        mode.getItems().setAll("CLASSIC", "ORDER");
        mode.setValue("CLASSIC");
        mode.getStyleClass().add("mode-selector");
        mode.setMaxWidth(Double.MAX_VALUE);
        create.getStyleClass().addAll("primary-button", "green-button", "wide-button");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(event -> createRoom(mode.getValue()));
        status.setWrapText(true);
        VBox card = card(heading, subtitle, rules, mode, create, status);
        card.getStyleClass().add("lobby-start-card");
        return card;
    }

    private VBox buildGuideCard() {
        Label heading = sectionTitle("HƯỚNG DẪN CÁCH CHƠI");
        guideText.getStyleClass().add("card-description");
        guideText.setWrapText(true);
        guideText.setText("1. Tạo phòng và mời đối thủ\n2. Chọn chế độ chơi\n3. Dùng phím ← / → để di chuyển giỏ\n4. Hứng đúng quả để ghi điểm");
        Button details = new Button("Xem chi tiết");
        details.getStyleClass().add("secondary-button");
        details.setOnAction(event -> showGuideDetails());
        HBox footer = new HBox(details);
        footer.setAlignment(Pos.CENTER_RIGHT);
        VBox card = card(heading, guideText, footer);
        card.getStyleClass().add("lobby-guide-card");
        return card;
    }

    private void buildDiscoveryCard() {
        Label heading = sectionTitle("KHÁM PHÁ HOA QUẢ");
        fruitGrid.setHgap(10);
        fruitGrid.setVgap(10);
        fruitGrid.setAlignment(Pos.CENTER_LEFT);
        fruitGrid.getStyleClass().add("fruit-grid");
        Label loading = new Label("Đang tải danh sách hoa quả...");
        loading.getStyleClass().add("card-description");
        fruitGrid.getChildren().add(loading);
        Button all = new Button("Xem tất cả hoa quả");
        all.getStyleClass().add("secondary-button");
        all.setOnAction(event -> showAllFruits());
        HBox footer = new HBox(all);
        footer.setAlignment(Pos.CENTER_RIGHT);
        discoveryCard.getChildren().setAll(heading, fruitGrid, footer);
        discoveryCard.setSpacing(6);
        discoveryCard.setPadding(new Insets(12));
        discoveryCard.getStyleClass().addAll("lobby-card", "lobby-discovery-card");
    }

    private VBox card(Node... children) {
        VBox card = new VBox(6, children);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.TOP_LEFT);
        card.setMinHeight(150);
        card.getStyleClass().add("lobby-card");
        return card;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-title");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Button navButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(handler);
        return button;
    }

    private void createRoom(String modeCode) {
        create.setDisable(true);
        status.setText("Đang tạo phòng...");
        send(new ProtocolMessage("CREATE_ROOM", Map.of("modeCode", modeCode == null ? "CLASSIC" : modeCode)));
    }

    private void logout() {
        try { session.logout(); } catch (Exception ignored) { }
        navigator.showMain();
    }

    private void refreshFruitGrid() {
        fruitGrid.getChildren().clear();
        if (fruitCatalog.isEmpty()) {
            Label empty = new Label("Chưa có dữ liệu hoa quả");
            empty.getStyleClass().add("card-description");
            fruitGrid.getChildren().add(empty);
            return;
        }
        fruitCatalog.stream().limit(6).forEach(fruit -> fruitGrid.getChildren().add(fruitCard(fruit)));
    }

    private VBox fruitCard(FruitItem fruit) {
        VBox card = new VBox(3);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(126, 126);
        card.setMinSize(126, 126);
        card.getStyleClass().add("fruit-card");
        ImageView image = FxAssets.image(normalizeAsset(fruit.assetPath()));
        image.setFitWidth(56);
        image.setFitHeight(56);
        image.setPreserveRatio(true);
        Label name = new Label(fruit.name());
        name.getStyleClass().add("fruit-name");
        Label group = new Label(fruit.groupName());
        group.getStyleClass().add("fruit-group");
        card.getChildren().addAll(image, name, group);
        card.setOnMouseClicked(event -> showFruitDetails(fruit));
        card.setOnMouseEntered(event -> card.getStyleClass().add("fruit-card-hover"));
        card.setOnMouseExited(event -> card.getStyleClass().remove("fruit-card-hover"));
        return card;
    }

    private String normalizeAsset(String path) {
        String normalized = AssetPaths.normalize(path);
        return normalized.isBlank() ? "fruits/orange.png" : normalized;
    }

    private void showFruitDetails(FruitItem fruit) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        FxAssets.styleDialog(alert.getDialogPane());
        alert.setTitle("Thông tin hoa quả");
        alert.setHeaderText(fruit.name());
        alert.setContentText("Nhóm: " + fruit.groupName() + "\n\nĐặc tính dinh dưỡng: "
                + (fruit.nutritionLabels().isBlank() ? "Chưa có dữ liệu" : fruit.nutritionLabels())
                + "\n\n" + fruit.description());
        alert.showAndWait();
    }

    private void showGuideDetails() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        FxAssets.styleDialog(alert.getDialogPane());
        alert.setTitle("Hướng dẫn cách chơi");
        alert.setHeaderText("CÁCH CHƠI");
        String classic = modeDescriptions.getOrDefault("CLASSIC", "Hứng hoa quả để nhận điểm và né bom.");
        String order = modeDescriptions.getOrDefault("ORDER", "Hứng đúng ba loại quả trong đơn hàng.");
        alert.setContentText("Thi đấu 1 vs 1 trong 30 giây. Dùng phím ← / → để di chuyển giỏ.\n\n"
                + "CHẾ ĐỘ CLASSIC\n" + classic + "\n\nCHẾ ĐỘ ORDER\n" + order);
        alert.showAndWait();
    }

    private void showAllFruits() {
        ScrollPane scroll = new ScrollPane();
        FlowPane all = new FlowPane(10, 10);
        all.setPadding(new Insets(12));
        all.getChildren().addAll(fruitCatalog.stream().map(this::fruitCard).toList());
        scroll.setContent(all);
        scroll.setPrefViewportWidth(680);
        scroll.setPrefViewportHeight(460);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        FxAssets.styleDialog(alert.getDialogPane());
        alert.setTitle("Tất cả hoa quả");
        alert.setHeaderText("Danh mục hoa quả");
        alert.getDialogPane().setContent(scroll);
        alert.showAndWait();
    }

    private void send(ProtocolMessage message) {
        try { session.client().send(message); }
        catch (IOException error) { status.setText("Mất kết nối máy chủ."); create.setDisable(false); }
    }

    @Override public void onMessage(ProtocolMessage message) {
        Platform.runLater(() -> handle(message));
    }

    private void handle(ProtocolMessage message) {
        switch (message.type()) {
            case "PROFILE_DATA" -> {
                score.setText("Tổng điểm: " + message.fields().getOrDefault("totalScore", "0"));
                status.setText("Sẵn sàng tạo phòng mới");
            }
            case "ONLINE_PLAYERS_BEGIN" -> {
                online.setText("Người chơi online: " + message.fields().getOrDefault("count", "0"));
                status.setText("Sẵn sàng tạo phòng mới");
            }
            case "FRUIT_CATALOG_BEGIN" -> fruitCatalog.clear();
            case "FRUIT_CATALOG_ITEM" -> fruitCatalog.add(new FruitItem(
                    message.fields().getOrDefault("fruitName", "Hoa quả"),
                    message.fields().getOrDefault("groupName", ""),
                    message.fields().getOrDefault("description", ""),
                    message.fields().getOrDefault("assetPath", ""),
                    message.fields().getOrDefault("nutritionLabels", "")));
            case "FRUIT_CATALOG_END" -> refreshFruitGrid();
            case "GAME_MODES_BEGIN" -> modeDescriptions.clear();
            case "GAME_MODE_ITEM" -> modeDescriptions.put(
                    message.fields().getOrDefault("modeCode", ""),
                    message.fields().getOrDefault("description", ""));
            case "ROOM_CREATED" -> status.setText("Đã tạo phòng #"
                    + message.fields().getOrDefault("roomId", "") + ". Đang chờ đối thủ...");
            case "ROOM_UPDATED" -> navigator.showWaitingRoom(message);
            case "ROOM_LEFT" -> { create.setDisable(false); status.setText("Sẵn sàng tạo phòng mới"); }
            case "INVITE_NOTIFICATION" -> showInviteNotification(message);
            case "ERROR" -> { status.setText(message.fields().getOrDefault("message", "Yêu cầu bị từ chối.")); create.setDisable(false); }
            default -> { }
        }
    }

    private void showInviteNotification(ProtocolMessage message) {
        if (incomingInviteOverlay != null) getChildren().remove(incomingInviteOverlay);
        incomingInviteOverlay = new FxInviteOverlay(message,
                accepted -> send(new ProtocolMessage(
                        accepted ? "INVITE_ACCEPT" : "INVITE_REJECT",
                        Map.of("inviteId", message.fields().getOrDefault("inviteId", "")))));
        getChildren().add(incomingInviteOverlay);
        StackPane.setAlignment(incomingInviteOverlay, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(incomingInviteOverlay, new Insets(0, 18, 18, 0));
        incomingInviteOverlay.toFront();
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }

    public void dispose() {
        session.client().removeListener(this);
    }

    private record FruitItem(String name, String groupName, String description,
                             String assetPath, String nutritionLabels) { }
}

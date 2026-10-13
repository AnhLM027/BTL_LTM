package Client.fx;

import Client.fx.component.PixelButton;
import Client.fx.util.SoundManager;
import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Pixel-art shop counter for managing fruit catalog and game items.
 * Hosted inside the 1080x760 design space of FxAdminView.
 */
public class AdminFruitsPane extends Pane {
    private static final double DESIGN_W = 1080;
    private static final double DESIGN_H = 760;

    private final Runnable onSwitchToAccounts;
    private final Runnable onLogout;

    private final TableView<ObservableList<String>> fruitsTable = createFruitsTable();
    private final Label statusLabel = new Label("Đang tải dữ liệu...");
    private final ImageView npcView = new ImageView();
    private final Label speechBubble = new Label("Hàng tươi mỗi ngày! 🍊");

    private final Map<String, String> fruitAssets = new HashMap<>();
    private final Map<String, String> fruitGroups = new HashMap<>();

    private PixelButton editBtn;
    private PixelButton deleteBtn;

    public AdminFruitsPane(Runnable onSwitchToAccounts, Runnable onLogout) {
        this.onSwitchToAccounts = onSwitchToAccounts;
        this.onLogout = onLogout;

        setMinSize(DESIGN_W, DESIGN_H);
        setPrefSize(DESIGN_W, DESIGN_H);
        setMaxSize(DESIGN_W, DESIGN_H);

        buildView();
        loadFruits();
    }

    private void buildView() {
        // 1. Background scene
        ImageView bg = FxAssets.image("backgrounds/admin-shop-fruits.png");
        bg.setFitWidth(DESIGN_W);
        bg.setFitHeight(DESIGN_H);
        bg.setPreserveRatio(false);
        bg.setSmooth(false);
        getChildren().add(bg);

        // 2. Navigation Button to Accounts (Top-Left)
        PixelButton navAccountsBtn = new PixelButton(
                PixelButton.BTN_BLUE,
                "⬅ Quầy Tài Khoản",
                210, 48, 15
        );
        navAccountsBtn.setLayoutX(40);
        navAccountsBtn.setLayoutY(36);
        navAccountsBtn.setOnAction(() -> {
            if (onSwitchToAccounts != null) {
                onSwitchToAccounts.run();
            }
        });
        getChildren().add(navAccountsBtn);

        // 3. Header Banner (Top-Right)
        Pane headerGroup = new Pane();
        headerGroup.setLayoutX(600);
        headerGroup.setLayoutY(24);

        ImageView headerBg = FxAssets.image("ui/waiting-room/panels/header.png");
        headerBg.setFitWidth(440);
        headerBg.setFitHeight(80);
        headerBg.setPreserveRatio(false);

        Label headerTitle = new Label("QUẢN TRỊ HOA QUẢ");
        headerTitle.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 24));
        headerTitle.setTextFill(Color.web("#4f2d18"));
        headerTitle.setPrefSize(440, 80);
        headerTitle.setAlignment(Pos.CENTER);
        headerTitle.setLayoutY(10);

        DropShadow titleShadow = new DropShadow(2, 1, 1, Color.rgb(255, 255, 255, 0.4));
        headerTitle.setEffect(titleShadow);

        headerGroup.getChildren().addAll(headerBg, headerTitle);
        getChildren().add(headerGroup);

        // 4. Data Panel Container (Left Side)
        StackPane dataPanel = new StackPane();
        dataPanel.setLayoutX(40);
        dataPanel.setLayoutY(116);
        dataPanel.setPrefSize(770, 520);

        ImageView panelBg = FxAssets.image("ui/admin-shop/data-panel.png");
        panelBg.setFitWidth(770);
        panelBg.setFitHeight(520);
        panelBg.setPreserveRatio(false);
        panelBg.setSmooth(false);

        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(34, 48, 20, 48));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // Top info bar inside panel
        HBox panelHeader = new HBox(12);
        panelHeader.setAlignment(Pos.CENTER_LEFT);

        Label panelTitle = new Label("KHO DỮ LIỆU HOA QUẢ");
        panelTitle.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 17));
        panelTitle.setTextFill(Color.web("#3D2B1F"));
        panelTitle.setTranslateX(50);

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        statusLabel.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 13));
        statusLabel.setTextFill(Color.web("#6B4E31"));
        statusLabel.setTranslateX(-50);

        panelHeader.getChildren().addAll(panelTitle, hSpacer, statusLabel);

        // Fruits Table
        fruitsTable.setPrefHeight(390);
        VBox.setMargin(fruitsTable, new Insets(0, 10, 0, 10));

        // CRUD Buttons Bar
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(buttonBar, new Insets(0, 10, 0, 10));

        PixelButton addBtn = PixelButton.green("➕ Thêm quả", 140, 44, this::createFruit);
        editBtn = PixelButton.blue("✏ Sửa", 130, 44, this::editFruit);
        deleteBtn = PixelButton.orange("🗑 Xóa", 130, 44, this::deleteFruit);
        PixelButton refreshBtn = PixelButton.blue("🔄 Làm mới", 140, 44, this::loadFruits);

        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        fruitsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = (newVal != null);
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        buttonBar.getChildren().addAll(addBtn, editBtn, deleteBtn, refreshBtn);

        contentBox.getChildren().addAll(panelHeader, fruitsTable, buttonBar);
        dataPanel.getChildren().addAll(panelBg, contentBox);

        // 5. NPC Sprite & Speech Bubble (Right Side Behind Counter)
        npcView.setFitWidth(440);
        npcView.setFitHeight(320);
        npcView.setLayoutX(685);
        npcView.setLayoutY(300);
        npcView.setPreserveRatio(true);
        npcView.setSmooth(false);
        setNpcSprite("npc/shopkeeper-wave.gif");

        speechBubble.setLayoutX(815);
        speechBubble.setLayoutY(305);
        speechBubble.setFont(Font.font(FxAssets.FONT_FAMILY, 13));
        speechBubble.setTextFill(Color.web("#3D2B1F"));
        speechBubble.setStyle(
                "-fx-background-color: #FFF8E7; " +
                "-fx-border-color: #8B6914; " +
                "-fx-border-width: 2px; " +
                "-fx-padding: 5px 10px; " +
                "-fx-background-radius: 4px; " +
                "-fx-border-radius: 4px;"
        );
        speechBubble.setEffect(new DropShadow(4, 2, 2, Color.rgb(0, 0, 0, 0.4)));

        getChildren().addAll(npcView, speechBubble);

        // 6. Wooden Counter (Across the bottom)
        ImageView counter = FxAssets.image("ui/admin-shop/counter-wood.png");
        counter.setFitWidth(DESIGN_W);
        counter.setFitHeight(180);
        counter.setLayoutX(0);
        counter.setLayoutY(530);
        counter.setPreserveRatio(false);
        counter.setSmooth(false);
        getChildren().add(counter);

        // 7. Decorative Fruit Sprites on the Counter (Right side beside NPC)
        addFruitDecoration("fruits/apple.png", 830, 560, 36);
        addFruitDecoration("fruits/orange.png", 875, 558, 38);
        addFruitDecoration("fruits/banana.png", 920, 555, 40);
        addFruitDecoration("fruits/strawberry.png", 965, 562, 34);
        addFruitDecoration("fruits/peach.png", 1010, 558, 38);

        // 8. Data Panel Container (Added AFTER counter so it sits on top)
        getChildren().add(dataPanel);

        // 8. Bottom Status Bar (Fixed at 710px)
        HBox bottomBar = new HBox(16);
        bottomBar.setLayoutX(0);
        bottomBar.setLayoutY(710);
        bottomBar.setPrefSize(DESIGN_W, 50);
        bottomBar.setPadding(new Insets(0, 30, 0, 30));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle(
                "-fx-background-color: #21130d; " +
                "-fx-border-color: #8B6914 transparent transparent transparent; " +
                "-fx-border-width: 2px;"
        );

        ImageView userIcon = FxAssets.image("icons/icon-user.png");
        userIcon.setFitWidth(24);
        userIcon.setFitHeight(24);

        var identity = GameClientSession.instance().identity();
        String adminName = (identity != null) ? identity.displayName() : "Quản trị viên";
        Label userLabel = new Label("Admin đang đăng nhập: " + adminName);
        userLabel.setFont(Font.font(FxAssets.FONT_FAMILY, 14));
        userLabel.setTextFill(Color.web("#FFF8E7"));

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        PixelButton logoutBtn = PixelButton.orange("🚪 Đăng xuất", 140, 36, () -> {
            SoundManager.getInstance().playLogout();
            if (onLogout != null) {
                onLogout.run();
            }
        });

        bottomBar.getChildren().addAll(userIcon, userLabel, bottomSpacer, logoutBtn);
        getChildren().add(bottomBar);
    }

    private void addFruitDecoration(String assetPath, double x, double y, double size) {
        ImageView f = FxAssets.image(assetPath);
        f.setFitWidth(size);
        f.setFitHeight(size);
        f.setLayoutX(x);
        f.setLayoutY(y);
        f.setSmooth(false);
        f.setEffect(new DropShadow(3, 1, 1, Color.rgb(0, 0, 0, 0.5)));
        getChildren().add(f);
    }

    private TableView<ObservableList<String>> createFruitsTable() {
        TableView<ObservableList<String>> table = new TableView<>();
        table.getStyleClass().add("pixel-admin-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "Mã", "Tên quả", "Nhóm", "Mô tả"};
        double[] widths = {50, 100, 130, 140, 250};

        for (int i = 0; i < headers.length; i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                    data.getValue() != null && colIndex < data.getValue().size()
                            ? data.getValue().get(colIndex) : ""
            ));
            col.setPrefWidth(widths[i]);
            table.getColumns().add(col);
        }
        return table;
    }

    private void setNpcSprite(String spritePath) {
        var img = FxAssets.load(spritePath);
        if (img != null) {
            npcView.setImage(img);
        }
    }

    /** Called when this pane becomes active/visible. */
    public void onShow() {
        setNpcSprite("npc/shopkeeper-wave.gif");
        speechBubble.setText("Hàng tươi mỗi ngày! 🍊");
        PauseTransition wavePause = new PauseTransition(Duration.seconds(2.5));
        wavePause.setOnFinished(e -> setNpcSprite("npc/shopkeeper-idle.gif"));
        wavePause.play();

        loadFruits();
    }

    public void loadFruits() {
        statusLabel.setText("Đang tải...");
        try {
            TcpQuery.collect(GameClientSession.instance().client(),
                    ProtocolMessage.of("GET_FRUIT_CATALOG"),
                    "FRUIT_CATALOG_ITEM",
                    "FRUIT_CATALOG_END")
                    .thenAccept(list -> Platform.runLater(() -> {
                        fruitsTable.getItems().clear();
                        fruitAssets.clear();
                        fruitGroups.clear();
                        for (var m : list) {
                            String id = m.fields().getOrDefault("fruitId", "");
                            fruitAssets.put(id, m.fields().getOrDefault("assetPath", ""));
                            fruitGroups.put(id, m.fields().getOrDefault("groupId", ""));
                            fruitsTable.getItems().add(FXCollections.observableArrayList(
                                    id,
                                    m.fields().getOrDefault("fruitCode", ""),
                                    m.fields().getOrDefault("fruitName", ""),
                                    m.fields().getOrDefault("groupName", ""),
                                    m.fields().getOrDefault("description", "")
                            ));
                        }
                        statusLabel.setText("Đã tải " + list.size() + " loại hoa quả");
                    }))
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            statusLabel.setText("Lỗi: " + e.getMessage());
                            SoundManager.getInstance().playActionError();
                        });
                        return null;
                    });
        } catch (IOException e) {
            statusLabel.setText("Lỗi kết nối: " + e.getMessage());
            SoundManager.getInstance().playActionError();
        }
    }

    private void createFruit() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Thêm hoa quả");
        dialog.setHeaderText("Thêm loại quả mới vào catalog trò chơi");
        FxAssets.styleDialog(dialog.getDialogPane());

        TextField codeField = new TextField();
        codeField.setPromptText("VD: DRAGON_FRUIT");
        TextField nameField = new TextField();
        nameField.setPromptText("VD: Thanh long");

        ComboBox<String> groupCombo = new ComboBox<>(FXCollections.observableArrayList(
                "1 - Nhóm quả có múi (CITRUS)",
                "2 - Nhóm quả mọng (BERRY)",
                "3 - Nhóm quả hạch (STONE_FRUIT)",
                "4 - Nhóm quả nhiệt đới (TROPICAL)",
                "5 - Nhóm quả có lõi (POME)",
                "6 - Nhóm dưa (MELON)",
                "7 - Nhóm chuối (BANANA)"
        ));
        groupCombo.setValue("4 - Nhóm quả nhiệt đới (TROPICAL)");

        TextField assetField = new TextField("Client/assets/fruits/apple.png");
        TextField descField = new TextField();
        descField.setPromptText("Mô tả hương vị và dinh dưỡng");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        form.addRow(0, new Label("Mã code:"), codeField);
        form.addRow(1, new Label("Tên quả:"), nameField);
        form.addRow(2, new Label("Nhóm quả:"), groupCombo);
        form.addRow(3, new Label("Asset Path:"), assetField);
        form.addRow(4, new Label("Mô tả:"), descField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            String code = codeField.getText().trim();
            String name = nameField.getText().trim();
            if (code.isBlank() || name.isBlank()) {
                SoundManager.getInstance().playActionError();
                statusLabel.setText("Mã và tên quả không được để trống");
                return;
            }

            String groupId = groupCombo.getValue().split(" - ")[0];
            String asset = assetField.getText().trim();
            String desc = descField.getText().trim();

            send(new ProtocolMessage("ADMIN_CREATE_FRUIT", Map.of(
                    "groupId", groupId,
                    "fruitCode", code,
                    "fruitName", name,
                    "description", desc,
                    "assetPath", asset
            )), "ADMIN_FRUIT_CREATED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Đã nhập hoa quả mới! 🍎");
                loadFruits();
            });
        });
    }

    private void editFruit() {
        int r = fruitsTable.getSelectionModel().getSelectedIndex();
        if (r < 0) {
            statusLabel.setText("Vui lòng chọn một hoa quả");
            return;
        }
        var row = fruitsTable.getItems().get(r);
        String fruitId = row.get(0);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Sửa hoa quả");
        dialog.setHeaderText("Chỉnh sửa thông tin quả: " + row.get(2) + " (" + row.get(1) + ")");
        FxAssets.styleDialog(dialog.getDialogPane());

        TextField nameField = new TextField(row.get(2));
        TextField descField = new TextField(row.get(4));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        form.addRow(0, new Label("Tên hoa quả:"), nameField);
        form.addRow(1, new Label("Mô tả:"), descField);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            String newName = nameField.getText().trim();
            String newDesc = descField.getText().trim();
            if (newName.isBlank()) {
                SoundManager.getInstance().playActionError();
                statusLabel.setText("Tên hoa quả không được để trống");
                return;
            }

            send(new ProtocolMessage("ADMIN_UPDATE_FRUIT", Map.of(
                    "fruitId", fruitId,
                    "fruitName", newName,
                    "description", newDesc,
                    "groupId", fruitGroups.getOrDefault(fruitId, "1"),
                    "fruitCode", row.get(1),
                    "assetPath", fruitAssets.getOrDefault(fruitId, "")
            )), "ADMIN_FRUIT_UPDATED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Cập nhật hoa quả xong! ✔");
                loadFruits();
            });
        });
    }

    private void deleteFruit() {
        int r = fruitsTable.getSelectionModel().getSelectedIndex();
        if (r < 0) {
            statusLabel.setText("Vui lòng chọn một hoa quả để xóa");
            return;
        }
        var row = fruitsTable.getItems().get(r);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa hoa quả");
        alert.setHeaderText("Bạn có chắc chắn muốn xóa quả '" + row.get(2) + "' (Mã: " + row.get(1) + ")?");
        alert.setContentText("Hành động này không thể hoàn tác!");
        FxAssets.styleDialog(alert.getDialogPane());

        alert.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            send(new ProtocolMessage("ADMIN_DELETE_FRUIT", Map.of("fruitId", row.get(0))),
                    "ADMIN_FRUIT_DELETED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Đã gỡ bỏ quả này! 🗑");
                loadFruits();
            });
        });
    }

    private void send(ProtocolMessage req, String expectedResponse, Runnable onSuccess) {
        try {
            TcpQuery.single(GameClientSession.instance().client(), req, expectedResponse)
                    .thenRun(() -> Platform.runLater(onSuccess))
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            SoundManager.getInstance().playActionError();
                            statusLabel.setText("Thao tác thất bại: " + e.getMessage());
                        });
                        return null;
                    });
        } catch (IOException e) {
            SoundManager.getInstance().playActionError();
            statusLabel.setText("Lỗi kết nối: " + e.getMessage());
        }
    }
}

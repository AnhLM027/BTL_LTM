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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pixel-art shop counter for managing user accounts.
 * Hosted inside the 1080x760 design space of FxAdminView.
 */
public class AdminAccountsPane extends Pane {
    private static final double DESIGN_W = 1080;
    private static final double DESIGN_H = 760;

    private final Runnable onSwitchToFruits;
    private final Runnable onLogout;

    private final TableView<ObservableList<String>> accountsTable = createAccountsTable();
    private final Label statusLabel = new Label("Đang tải dữ liệu...");
    private final ImageView npcView = new ImageView();
    private final Label speechBubble = new Label("Cần tìm ai không? 👀");

    private PixelButton editBtn;
    private PixelButton deleteBtn;

    public AdminAccountsPane(Runnable onSwitchToFruits, Runnable onLogout) {
        this.onSwitchToFruits = onSwitchToFruits;
        this.onLogout = onLogout;

        setMinSize(DESIGN_W, DESIGN_H);
        setPrefSize(DESIGN_W, DESIGN_H);
        setMaxSize(DESIGN_W, DESIGN_H);

        buildView();
        loadAccounts();
    }

    private void buildView() {
        // 1. Background scene
        ImageView bg = FxAssets.image("backgrounds/admin-shop-accounts.png");
        bg.setFitWidth(DESIGN_W);
        bg.setFitHeight(DESIGN_H);
        bg.setPreserveRatio(false);
        bg.setSmooth(false);
        getChildren().add(bg);

        // 2. Header Banner (Left)
        Pane headerGroup = new Pane();
        headerGroup.setLayoutX(40);
        headerGroup.setLayoutY(24);

        ImageView headerBg = FxAssets.image("ui/waiting-room/panels/header.png");
        headerBg.setFitWidth(440);
        headerBg.setFitHeight(80);
        headerBg.setPreserveRatio(false);

        Label headerTitle = new Label("QUẢN TRỊ TÀI KHOẢN");
        headerTitle.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 24));
        headerTitle.setTextFill(Color.web("#4f2d18"));
        headerTitle.setPrefSize(440, 80);
        headerTitle.setAlignment(Pos.CENTER);
        headerTitle.setLayoutY(10);

        DropShadow titleShadow = new DropShadow(2, 1, 1, Color.rgb(255, 255, 255, 0.4));
        headerTitle.setEffect(titleShadow);

        headerGroup.getChildren().addAll(headerBg, headerTitle);
        getChildren().add(headerGroup);

        // 3. Navigation Button to Fruits (Right)
        PixelButton navFruitsBtn = new PixelButton(
                PixelButton.BTN_BLUE,
                "Quầy Hoa Quả ➔",
                210, 48, 15
        );
        navFruitsBtn.setLayoutX(830);
        navFruitsBtn.setLayoutY(36);
        navFruitsBtn.setOnAction(() -> {
            if (onSwitchToFruits != null) {
                onSwitchToFruits.run();
            }
        });
        getChildren().add(navFruitsBtn);

        // 4. NPC Sprite & Speech Bubble (Left, Behind Counter)
        npcView.setFitWidth(180);
        npcView.setFitHeight(230);
        npcView.setLayoutX(60);
        npcView.setLayoutY(350);
        npcView.setPreserveRatio(true);
        npcView.setSmooth(false);
        setNpcSprite("npc/shopkeeper-wave.gif");

        speechBubble.setLayoutX(45);
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

        // 5. Wooden Counter (Across the bottom above the footer)
        ImageView counter = FxAssets.image("ui/admin-shop/counter-wood.png");
        counter.setFitWidth(DESIGN_W);
        counter.setFitHeight(180);
        counter.setLayoutX(0);
        counter.setLayoutY(530);
        counter.setPreserveRatio(false);
        counter.setSmooth(false);
        getChildren().add(counter);

        // 6. Data Panel Container (Right Side)
        StackPane dataPanel = new StackPane();
        dataPanel.setLayoutX(270);
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

        Label panelTitle = new Label("SỔ QUẢN LÝ TÀI KHOẢN");
        panelTitle.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 17));
        panelTitle.setTextFill(Color.web("#3D2B1F"));
        panelTitle.setTranslateX(50);

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        statusLabel.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 13));
        statusLabel.setTextFill(Color.web("#6B4E31"));
        statusLabel.setTranslateX(-50);

        panelHeader.getChildren().addAll(panelTitle, hSpacer, statusLabel);

        // Accounts Table
        accountsTable.setPrefHeight(390);
        VBox.setMargin(accountsTable, new Insets(0, 10, 0, 10));

        // CRUD Buttons Bar
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(buttonBar, new Insets(0, 10, 0, 10));


        PixelButton addBtn = PixelButton.green("➕ Thêm", 140, 44, this::createAccount);
        editBtn = PixelButton.blue("✏️ Sửa", 130, 44, this::editAccount);
        deleteBtn = PixelButton.orange("🗑️ Xóa", 130, 44, this::deleteAccount);
        PixelButton refreshBtn = PixelButton.blue("🔄 Làm mới", 140, 44, this::loadAccounts);

        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = (newVal != null);
            editBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        buttonBar.getChildren().addAll(addBtn, editBtn, deleteBtn, refreshBtn);

        contentBox.getChildren().addAll(panelHeader, accountsTable, buttonBar);
        dataPanel.getChildren().addAll(panelBg, contentBox);
        getChildren().add(dataPanel);

        // 7. Bottom Status Bar (Fixed at 710px)
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

    private TableView<ObservableList<String>> createAccountsTable() {
        TableView<ObservableList<String>> table = new TableView<>();
        table.getStyleClass().add("pixel-admin-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        String[] headers = {"ID", "Tên tài khoản", "Vai trò", "Trạng thái"};
        double[] widths = {70, 260, 170, 170};

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
        speechBubble.setText("Cần tìm ai không? 👀");
        PauseTransition wavePause = new PauseTransition(Duration.seconds(2.5));
        wavePause.setOnFinished(e -> setNpcSprite("npc/shopkeeper-idle.gif"));
        wavePause.play();

        loadAccounts();
    }

    public void loadAccounts() {
        statusLabel.setText("Đang tải...");
        try {
            TcpQuery.collect(GameClientSession.instance().client(),
                    ProtocolMessage.of("ADMIN_LIST_ACCOUNTS"),
                    "ADMIN_ACCOUNT",
                    "ADMIN_ACCOUNTS_END")
                    .thenAccept(list -> Platform.runLater(() -> {
                        accountsTable.getItems().clear();
                        for (var m : list) {
                            accountsTable.getItems().add(FXCollections.observableArrayList(
                                    m.fields().getOrDefault("accountId", ""),
                                    m.fields().getOrDefault("username", ""),
                                    m.fields().getOrDefault("role", ""),
                                    m.fields().getOrDefault("status", "")
                            ));
                        }
                        statusLabel.setText("Đã tải " + list.size() + " tài khoản");
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

    private void createAccount() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tạo tài khoản");
        dialog.setHeaderText("Tạo tài khoản mới trong hệ thống");
        FxAssets.styleDialog(dialog.getDialogPane());

        TextField username = new TextField();
        username.setPromptText("Nhập username");
        PasswordField password = new PasswordField();
        password.setPromptText("Nhập mật khẩu");
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("PLAYER", "ADMIN"));
        role.setValue("PLAYER");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        form.addRow(0, new Label("Tên tài khoản:"), username);
        form.addRow(1, new Label("Mật khẩu:"), password);
        form.addRow(2, new Label("Vai trò:"), role);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            String u = username.getText().trim();
            String p = password.getText();
            if (u.isBlank() || p.isBlank()) {
                SoundManager.getInstance().playActionError();
                statusLabel.setText("Tên và mật khẩu không được để trống");
                return;
            }
            send(new ProtocolMessage("ADMIN_CREATE_ACCOUNT", Map.of(
                    "username", u,
                    "password", p,
                    "role", role.getValue()
            )), "ADMIN_ACCOUNT_CREATED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Đã thêm tài khoản mới! ✨");
                loadAccounts();
            });
        });
    }

    private void editAccount() {
        int r = accountsTable.getSelectionModel().getSelectedIndex();
        if (r < 0) {
            statusLabel.setText("Vui lòng chọn một tài khoản");
            return;
        }
        var row = accountsTable.getItems().get(r);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Sửa tài khoản");
        dialog.setHeaderText("Chỉnh sửa tài khoản: " + row.get(1));
        FxAssets.styleDialog(dialog.getDialogPane());

        TextField username = new TextField(row.get(1));
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("PLAYER", "ADMIN"));
        role.setValue(row.get(2));
        ComboBox<String> state = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "DISABLED", "LOCKED"));
        state.setValue(row.get(3));
        PasswordField password = new PasswordField();
        password.setPromptText("Để trống nếu không đổi mật khẩu");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        form.addRow(0, new Label("Tên tài khoản:"), username);
        form.addRow(1, new Label("Vai trò:"), role);
        form.addRow(2, new Label("Trạng thái:"), state);
        form.addRow(3, new Label("Mật khẩu mới:"), password);

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("accountId", row.get(0));
            fields.put("username", username.getText().trim());
            fields.put("role", role.getValue());
            fields.put("status", state.getValue());
            if (!password.getText().isBlank()) {
                fields.put("password", password.getText());
            }

            send(new ProtocolMessage("ADMIN_UPDATE_ACCOUNT", fields),
                    "ADMIN_ACCOUNT_UPDATED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Cập nhật thành công! ✔");
                loadAccounts();
            });
        });
    }

    private void deleteAccount() {
        int r = accountsTable.getSelectionModel().getSelectedIndex();
        if (r < 0) {
            statusLabel.setText("Vui lòng chọn một tài khoản để xóa");
            return;
        }
        var row = accountsTable.getItems().get(r);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận xóa tài khoản");
        alert.setHeaderText("Bạn có chắc chắn muốn xóa tài khoản '" + row.get(1) + "' (ID: " + row.get(0) + ")?");
        alert.setContentText("Hành động này không thể hoàn tác!");
        FxAssets.styleDialog(alert.getDialogPane());

        alert.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            send(new ProtocolMessage("ADMIN_DELETE_ACCOUNT", Map.of("accountId", row.get(0))),
                    "ADMIN_ACCOUNT_DELETED", () -> {
                SoundManager.getInstance().playActionSuccess();
                speechBubble.setText("Đã xóa tài khoản! 🗑");
                loadAccounts();
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

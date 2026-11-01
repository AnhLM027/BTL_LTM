package fruit.client.features.admin;

import static fruit.client.shared.ui.GameArt.decorate;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.column;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiComponents.table;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.CYAN;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.PANEL;
import static fruit.common.MessageData.displayName;
import static fruit.common.MessageData.recordPlayer;
import static fruit.common.MessageData.value;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;
import fruit.client.features.results.ResultScreen;
import fruit.common.MessageData;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;

/** Admin screen layout and control bindings. */
public final class AdminScreen {
    private AdminScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.body.setPadding(new Insets(12, 18, 12, 18));
        ui.body.setSpacing(10);
        HBox heading = new HBox(12,
                new VBox(4, label("QUẢN TRỊ HỆ THỐNG", 10, LIME, true),
                        label("Quản lý tài khoản và trận đấu", 24, INK, true)),
                spacer(),
                label("Hàng đợi: " + value(model.adminStats, "queued")
                        + " · Đã khóa: " + value(model.adminStats, "locked")
                        + " · Hoạt động: " + value(model.adminStats, "uptime") + "s", 11, MUTED, false));
        heading.setAlignment(Pos.CENTER_LEFT);
        ui.body.getChildren().add(heading);
        HBox metrics =
                new HBox(
                        12,
                        summary(
                                value(model.adminStats, "users"),
                                "TÀI KHOẢN",
                                LIME),
                        summary(
                                value(model.adminStats, "online"),
                                "TRỰC TUYẾN",
                                CYAN),
                        summary(
                                value(model.adminStats, "activeMatches"),
                                "TRẬN ĐANG CHƠI",
                                CORAL),
                        summary(
                                value(model.adminStats, "totalMatches"),
                                "TRẬN ĐÃ LƯU",
                                LIME));
        ui.body.getChildren().add(metrics);
        TableView<Map<String, Object>> accounts = table(model.adminUsers);
        column(accounts, "ID", 55, r -> value(r, "id"));
        column(accounts, "TÀI KHOẢN", 160, r -> str(r, "username"));
        column(accounts, "TÊN HIỂN THỊ", 180, MessageData::displayName);
        column(accounts, "VAI TRÒ", 100, r -> str(r, "role"));
        column(accounts, "THẮNG", 80, r -> value(r, "wins"));
        column(accounts, "TRẠNG THÁI", 125, r -> bool(r, "locked") ? "Đã khóa" : "Hoạt động");
        TableColumn<Map<String, Object>, String> action = new TableColumn<>("THAO TÁC");
        action.setPrefWidth(140);
        action.setCellValueFactory(v -> new ReadOnlyStringWrapper(""));
        action.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String value, boolean empty) {
                                super.updateItem(value, empty);
                                if (empty) {
                                    setGraphic(null);
                                    return;
                                }
                                Map<String, Object> row = getTableView().getItems().get(getIndex());
                                Button lock =
                                        button(
                                                bool(row, "locked") ? "Mở khóa" : "Khóa",
                                                false,
                                                () ->
                                                        controller.send(
                                                                obj(
                                                                        "type",
                                                                        "ADMIN_LOCK",
                                                                        "id",
                                                                        integer(row, "id"),
                                                                        "locked",
                                                                        !bool(row, "locked"))));
                                lock.setDisable(integer(row, "id") == integer(model.user, "id"));
                                lock.setStyle("-fx-padding:0 10;");
                                setGraphic(lock);
                            }
                        });
        accounts.getColumns().add(action);
        TableView<Map<String, Object>> matches = table(model.adminMatches);
        column(
                matches,
                "TRẬN",
                80,
                r -> r.containsKey("id") ? value(r, "id") : value(r, "matchId"));
        column(
                matches,
                "THỜI GIAN",
                210,
                r -> fruit.client.shared.ui.DisplayFormats.matchTime(str(r, "createdAt")));
        column(matches, "NGƯỜI CHƠI 1", 180, r -> displayName(recordPlayer(r, 0)));
        column(matches, "NGƯỜI CHƠI 2", 180, r -> displayName(recordPlayer(r, 1)));
        column(
                matches,
                "ĐIỂM",
                130,
                r ->
                        value(recordPlayer(r, 0), "score")
                                + " : "
                                + value(recordPlayer(r, 1), "score"));
        Button prevUsers =
                button(
                        "← Trước",
                        false,
                        () -> {
                            model.adminUserOffset = Math.max(0, model.adminUserOffset - 50);
                            controller.loadAdmin();
                        });
        prevUsers.setDisable(model.adminUserOffset == 0);
        Button nextUsers =
                button(
                        "Tiếp →",
                        false,
                        () -> {
                            model.adminUserOffset += 50;
                            controller.loadAdmin();
                        });
        nextUsers.setDisable(!model.moreAdminUsers);
        HBox userPages =
                new HBox(
                        12,
                        prevUsers,
                        label(
                                "Tài khoản "
                                        + (model.adminUsers.isEmpty()
                                                ? 0
                                                : model.adminUserOffset + 1)
                                        + " – "
                                        + (model.adminUserOffset + model.adminUsers.size())
                                        + " / " + model.adminUserCount,
                                12,
                                MUTED,
                                false),
                        nextUsers);
        userPages.setAlignment(Pos.CENTER_LEFT);
        VBox usersPane = new VBox(12, accounts, userPages);
        VBox.setVgrow(accounts, Priority.ALWAYS);
        Button prevMatches =
                button(
                        "← Trước",
                        false,
                        () -> {
                            model.adminMatchOffset = Math.max(0, model.adminMatchOffset - 25);
                            controller.loadAdmin();
                        });
        prevMatches.setDisable(model.adminMatchOffset == 0);
        Button nextMatches =
                button(
                        "Tiếp →",
                        false,
                        () -> {
                            model.adminMatchOffset += 25;
                            controller.loadAdmin();
                        });
        nextMatches.setDisable(!model.moreAdminMatches);
        HBox matchPages =
                new HBox(
                        12,
                        prevMatches,
                        label(
                                "Trận "
                                        + (model.adminMatches.isEmpty()
                                                ? 0
                                                : model.adminMatchOffset + 1)
                                        + " – "
                                        + (model.adminMatchOffset + model.adminMatches.size())
                                        + " / " + model.adminMatchCount,
                                12,
                                MUTED,
                                false),
                        nextMatches);
        matchPages.setAlignment(Pos.CENTER_LEFT);
        Button details =
                button(
                        "Xem chi tiết trận  ↗",
                        false,
                        () -> ui.adminMatchDetails(matches.getSelectionModel().getSelectedItem()));
        details.disableProperty().bind(matches.getSelectionModel().selectedItemProperty().isNull());
        matchPages.getChildren().addAll(spacer(), details);
        VBox matchesPane = new VBox(12, matches, matchPages);
        VBox.setVgrow(matches, Priority.ALWAYS);
        TabPane tabs =
                new TabPane(
                        new Tab("Tài khoản", usersPane), new Tab("Trận đấu gần đây", matchesPane));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(ui.adminTab);
        tabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener((o, a, b) -> ui.adminTab = b.intValue());
        tabs.setId("admin-tabs");
        TextField search = new TextField();
        search.setId("admin-search");
        search.setMinWidth(100);
        search.setPrefWidth(220);
        search.setStyle("-fx-font-size:12px;-fx-padding:6 10;");
        HBox.setHgrow(search, Priority.ALWAYS);
        Runnable restoreQuery = () -> {
            boolean usersSelected = tabs.getSelectionModel().getSelectedIndex() == 0;
            search.setPromptText(usersSelected ? "Tên / tài khoản / ID" : "Mã trận / tên người chơi");
            search.setText(usersSelected ? model.adminUserQuery : model.adminMatchQuery);
        };
        restoreQuery.run();
        tabs.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> restoreQuery.run());
        Runnable applySearch = () -> {
            String query = search.getText().strip();
            if (tabs.getSelectionModel().getSelectedIndex() == 0) {
                if (!query.equals(model.adminUserQuery)) model.adminUserOffset = 0;
                model.adminUserQuery = query;
            } else {
                if (!query.equals(model.adminMatchQuery)) model.adminMatchOffset = 0;
                model.adminMatchQuery = query;
            }
            controller.loadAdmin();
        };
        search.setOnAction(e -> applySearch.run());
        Button find = button("Tìm kiếm", true, applySearch);
        Button clear = button("Xóa lọc", false, () -> { search.clear(); applySearch.run(); });
        Button refresh = button("Làm mới dữ liệu  ↻", false, applySearch);
        refresh.setId("admin-refresh");
        for (Button b : new Button[]{find, clear, refresh}) {
            b.setStyle("-fx-padding:1 10;");
            b.setMinHeight(36);
            b.setMaxHeight(36);
        }
        for (Button b : new Button[]{prevUsers, nextUsers, prevMatches, nextMatches, details})
            b.setStyle("-fx-padding:6 10;");
        HBox tools = new HBox(8, search, find, clear, refresh);
        tools.setAlignment(Pos.CENTER_RIGHT);
        tools.setMaxHeight(38);
        tools.setMaxWidth(560);
        StackPane deck = new StackPane(tabs, tools);
        StackPane.setAlignment(tools, Pos.TOP_RIGHT);
        tools.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(560,
                javafx.beans.binding.Bindings.max(100, deck.widthProperty().subtract(280))));
        tools.setMinWidth(0);
        tools.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setVgrow(deck, Priority.ALWAYS);
        ui.body.getChildren().add(deck);
    }

    private static HBox summary(String value, String caption, String color) {
        HBox box = new HBox(12, label(value, 24, color, true), label(caption, 10, INK, true));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 16, 10, 16));
        box.setMinWidth(0);
        box.setPrefWidth(220);
        box.setMinHeight(54);
        box.setMaxHeight(54);
        box.setStyle("-fx-background-color:#fff0c9;");
        decorate(box, 8);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    public static void showMatchDetails(ClientScreens ui, Map<String, Object> match) {
        ClientState model = ui.model;

        if (match == null) return;
        Map<String, Object> first = recordPlayer(match, 0), second = recordPlayer(match, 1);
        int winner = integer(match, "winnerId");
        String verdict =
                winner == 0
                        ? "Kết quả hòa"
                        : "Chiến thắng: "
                                + displayName(integer(first, "id") == winner ? first : second);
        VBox content =
                new VBox(
                        18,
                        label(verdict, 24, LIME, true),
                        label(
                                "Trận #"
                                        + value(match, "matchId")
                                        + "  •  "
                                        + fruit.client.shared.ui.DisplayFormats.matchTime(
                                                str(match, "createdAt")),
                                12,
                                MUTED,
                                false),
                        new HBox(
                                18,
                                ResultScreen.resultCard(first, true, "NGƯỜI CHƠI 1"),
                                ResultScreen.resultCard(second, false, "NGƯỜI CHƠI 2")));
        content.setPadding(new Insets(22));
        content.setStyle("-fx-background-color:" + PANEL + ";");
        decorate(content, 13);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(ui.stage);
        dialog.setTitle("Chi tiết trận đấu");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(900);
        dialog.showAndWait();
    }
}

package fruit.client.features.leaderboard;

import static fruit.client.shared.ui.UiComponents.*;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.column;
import static fruit.client.shared.ui.UiComponents.table;
import static fruit.client.shared.ui.UiTheme.*;
import static fruit.common.MessageData.displayName;
import static fruit.common.MessageData.value;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;

/** Leaderboard screen layout and control bindings. */
public final class LeaderboardScreen {
    private LeaderboardScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.pageTitle(
                "BẢNG XẾP HẠNG",
                "Những tay hứng cừ khôi.",
                "Xếp hạng theo số trận thắng, sau đó theo tổng điểm.");
        HBox podium = new HBox(14);
        for (int i = 0; i < 3; i++) {
            VBox card = panel(14);
            card.setMinWidth(0);
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setPrefWidth(300);
            Map<String, Object> player = i < model.ranking.size() ? model.ranking.get(i) : null;
            VBox caption =
                    new VBox(
                            5,
                            label("TOP " + (i + 1), 10, MUTED, true),
                            label(
                                    player == null ? "Đang chờ chủ nhân" : displayName(player),
                                    18,
                                    INK,
                                    true),
                            label(
                                    player == null
                                            ? "—"
                                            : value(player, "wins")
                                                    + " thắng · "
                                                    + value(player, "totalScore")
                                                    + " điểm",
                                    11,
                                    MUTED,
                                    false));
            caption.setMinWidth(0);
            HBox row = new HBox(12, TrophyArt.image(i + 1, 64), caption);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
            podium.getChildren().add(card);
        }
        ui.body.getChildren().add(podium);
        TableView<Map<String, Object>> table = table(model.ranking);
        TextField query = field("Tên người chơi hoặc tài khoản", model.rankingQuery);
        query.setId("leaderboard-search");
        query.setPrefWidth(320);
        HBox.setHgrow(query, Priority.ALWAYS);
        Label count = label("", 11, MUTED, false);
        Runnable search =
                () -> {
                    model.rankingQuery = query.getText().strip();
                    String needle = normalize(model.rankingQuery);
                    table.getItems()
                            .setAll(
                                    model.ranking.stream()
                                            .filter(
                                                    r ->
                                                            normalize(
                                                                            displayName(r)
                                                                                    + " "
                                                                                    + str(
                                                                                            r,
                                                                                            "username"))
                                                                    .contains(needle))
                                            .toList());
                    table.setPlaceholder(
                            label("Không tìm thấy người chơi phù hợp.", 14, MUTED, false));
                    count.setText(
                            table.getItems().size() + " / " + model.ranking.size() + " người chơi");
                };
        query.setOnAction(e -> search.run());
        HBox controls =
                new HBox(
                        10,
                        query,
                        button("Tìm kiếm", true, search),
                        button(
                                "Xóa lọc",
                                false,
                                () -> {
                                    query.clear();
                                    search.run();
                                }),
                        button(
                                "Làm mới  ↻",
                                false,
                                () -> controller.send(obj("type", "LEADERBOARD"))));
        controls.setAlignment(Pos.CENTER_LEFT);
        ui.body
                .getChildren()
                .addAll(
                        controls,
                        new HBox(
                                12,
                                label(
                                        "Tìm trong top 100 · Thứ hạng giữ nguyên khi lọc",
                                        11,
                                        MUTED,
                                        false),
                                count));
        search.run();
        column(table, "HẠNG", 75, r -> String.valueOf(model.ranking.indexOf(r) + 1));
        column(
                table,
                "NGƯỜI CHƠI",
                250,
                r -> {
                    int rid = integer(r, "id"), uid = integer(model.user, "id");
                    return (rid == uid ? "▶ " : "") + displayName(r);
                });
        column(table, "THẮNG", 110, r -> value(r, "wins"));
        column(table, "THUA", 100, r -> value(r, "losses"));
        column(table, "HÒA", 100, r -> value(r, "draws"));
        column(table, "TỔNG ĐIỂM", 150, r -> value(r, "totalScore"));
        // Highlight own row
        int myId = integer(model.user, "id");
        table.setRowFactory(
                tv ->
                        new TableRow<>() {
                            @Override
                            protected void updateItem(Map<String, Object> item, boolean empty) {
                                super.updateItem(item, empty);
                                if (!empty && item != null && integer(item, "id") == myId) {
                                    setStyle(
                                            "-fx-background-color:#dbe3ad;-fx-border-color:transparent"
                                                + " transparent #819c44"
                                                + " transparent;-fx-border-width:0 0 2 0;");
                                } else {
                                    setStyle("");
                                }
                            }
                        });
        ui.body.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(java.util.Locale.ROOT);
    }
}

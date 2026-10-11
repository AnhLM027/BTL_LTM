package Client.fx;

import Client.network.TcpQuery;
import Client.session.GameClientSession;
import Server.network.ProtocolMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** JavaFX profile/history/ranking screens using the existing TCP query protocol. */
public final class FxDataView extends BorderPane {
    public enum Kind { PROFILE, HISTORY, RANKING }
    private final FxScreenNavigator navigator;
    private final VBox content = new VBox(14);
    private final Label status = new Label("Đang tải dữ liệu...");

    public FxDataView(FxScreenNavigator navigator, Kind kind) {
        this.navigator = navigator;
        getStyleClass().add("fx-data-view");
        Label title = new Label(switch (kind) { case PROFILE -> "HỒ SƠ NGƯỜI CHƠI"; case HISTORY -> "LỊCH SỬ TRẬN ĐẤU"; case RANKING -> "BẢNG XẾP HẠNG"; });
        title.getStyleClass().add("screen-title");
        setTop(title); BorderPane.setAlignment(title, Pos.CENTER); BorderPane.setMargin(title, new Insets(24));
        content.setPadding(new Insets(20, 48, 20, 48)); content.getStyleClass().add("data-surface");
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.getStyleClass().add("data-scroll"); setCenter(scroll);
        Button back = new Button("Trở về Lobby"); back.getStyleClass().add("secondary-button"); back.setOnAction(e -> navigator.showLobby());
        setBottom(back); BorderPane.setAlignment(back, Pos.CENTER); BorderPane.setMargin(back, new Insets(14));
        var css = getClass().getResource("/Client/fx/fx-client.css"); if (css != null) getStylesheets().add(css.toExternalForm());
        load(kind);
    }

    private void load(Kind kind) {
        try {
            if (kind == Kind.PROFILE) {
                TcpQuery.single(GameClientSession.instance().client(), ProtocolMessage.of("GET_PROFILE"), "PROFILE_DATA")
                        .thenAccept(m -> Platform.runLater(() -> profile(m))).exceptionally(this::error);
            } else {
                String request = kind == Kind.HISTORY ? "GET_MATCH_HISTORY" : "GET_LEADERBOARD";
                String item = kind == Kind.HISTORY ? "MATCH_HISTORY_ITEM" : "LEADERBOARD_PLAYER";
                String end = kind == Kind.HISTORY ? "MATCH_HISTORY_END" : "LEADERBOARD_END";
                TcpQuery.collect(GameClientSession.instance().client(), ProtocolMessage.of(request), item, end)
                        .thenAccept(list -> Platform.runLater(() -> table(kind, list))).exceptionally(this::error);
            }
        } catch (IOException e) { error(e); }
    }

    private void profile(ProtocolMessage m) {
        content.getChildren().clear();
        add("Tên hiển thị", m.fields().get("displayName")); add("Tổng điểm", m.fields().get("totalScore"));
        add("Số trận", m.fields().get("totalGames")); add("Số trận thắng", m.fields().get("totalWins"));
        status.setText("Đã tải hồ sơ"); content.getChildren().add(status);
    }

    private void add(String label, String value) {
        Label row = new Label(label + ":  " + (value == null ? "—" : value)); row.getStyleClass().add("data-row"); content.getChildren().add(row);
    }

    private void table(Kind kind, List<ProtocolMessage> messages) {
        content.getChildren().clear();
        TableView<ObservableList<String>> table = new TableView<>();
        String[] headers = kind == Kind.HISTORY ? new String[]{"Mã trận", "Thời gian", "Đối thủ", "Mode", "Điểm", "Kết quả"} : new String[]{"#", "Tên", "Điểm", "Thắng", "Trận"};
        for (int col = 0; col < headers.length; col++) { final int index = col; TableColumn<ObservableList<String>, String> c = new TableColumn<>(headers[col]); c.setCellValueFactory(v -> new javafx.beans.property.SimpleStringProperty(v.getValue().get(index))); c.setPrefWidth(145); table.getColumns().add(c); }
        int rank = 1;
        for (ProtocolMessage m : messages) {
            ObservableList<String> row = FXCollections.observableArrayList();
            if (kind == Kind.HISTORY) row.addAll(values(m, "matchId", "endedAt", "opponentName", "modeCode", "score", "result"));
            else row.addAll(values(m, "displayName", "totalScore", "totalWins", "totalGames"));
            if (kind == Kind.RANKING) row.add(0, Integer.toString(rank++));
            table.getItems().add(row);
        }
        content.getChildren().add(table); status.setText("Đã tải " + messages.size() + " bản ghi"); content.getChildren().add(status);
    }

    private List<String> values(ProtocolMessage m, String... fields) { return java.util.Arrays.stream(fields).map(f -> m.fields().getOrDefault(f, "—")).toList(); }
    private Void error(Throwable t) { Platform.runLater(() -> status.setText("Không tải được dữ liệu: " + (t.getMessage() == null ? "lỗi kết nối" : t.getMessage()))); return null; }
}

package fruit.client.features.history;

import java.util.Map;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.column;
import static fruit.client.shared.ui.UiComponents.table;
import static fruit.common.MessageData.outcome;
import static fruit.common.MessageData.value;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** History screen layout and control bindings. */
public final class HistoryScreen {
    private HistoryScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.pageTitle(
                "LỊCH SỬ THI ĐẤU",
                "Mỗi trận, một câu chuyện.",
                "Kết quả được lưu khi trận kết thúc.");
        TableView<Map<String, Object>> table = table(model.history);
        column(
                table,
                "THỜI GIAN",
                190,
                r -> fruit.client.shared.ui.DisplayFormats.matchTime(str(r, "createdAt")));
        column(table, "ĐỐI THỦ", 165, r -> str(r, "opponent"));
        column(table, "KẾT QUẢ", 100, r -> outcome(str(r, "outcome")));
        column(table, "ĐIỂM", 100, r -> value(r, "ownScore") + " : " + value(r, "opponentScore"));
        column(table, "ĐÚNG / SAI", 110, r -> value(r, "correct") + " / " + value(r, "wrong"));
        column(table, "BOM", 65, r -> value(r, "bombs"));
        column(table, "COMBO", 75, r -> value(r, "maxCombo"));
        column(table, "KỸ NĂNG", 85, r -> value(r, "skills"));
        ui.body.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        ui.body
                .getChildren()
                .add(button("Làm mới  ↻", false, () -> controller.send(obj("type", "HISTORY"))));
    }
}

package fruit.client.features.results;

import static fruit.client.shared.ui.GameArt.drawTrophy;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.divider;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.metric;
import static fruit.client.shared.ui.UiComponents.panel;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.CYAN;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.common.MessageData.displayName;
import static fruit.common.MessageData.orderedPlayers;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/** Result screen layout and control bindings. */
public final class ResultScreen {
    private ResultScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.body.setSpacing(10);
        ui.body.setPadding(new Insets(12, 32, 20, 32));
        List<Map<String, Object>> players = orderedPlayers(model.result, integer(model.user, "id"));
        Map<String, Object> me = players.isEmpty() ? model.user : players.get(0),
                other = players.size() > 1 ? players.get(1) : obj();
        int winner = integer(model.result, "winnerId");
        boolean draw = winner == 0, won = winner == integer(model.user, "id");

        // Trophy — larger when win
        VBox headline = new VBox(8);
        headline.setAlignment(Pos.CENTER);
        double trophyScale = won ? 6 : draw ? 5 : 4;
        Canvas trophy = new Canvas((int) (13 * trophyScale), (int) (13 * trophyScale));
        drawTrophy(trophy.getGraphicsContext2D(), 0, 0, trophyScale);
        headline.getChildren()
                .addAll(
                        trophy,
                        label(
                                draw
                                        ? "BẤT PHÂN THẮNG BẠI"
                                        : won ? "CHIẾN THẮNG NGỌT NGÀO!" : "HẸN Ở MÙA QUẢ TỚI!",
                                won ? 28 : 25,
                                won ? LIME : draw ? CYAN : CORAL,
                                true),
                        text(
                                draw
                                        ? "Hai người chơi có cùng điểm số."
                                        : won
                                                ? "Phản xạ tốt. Chiến thuật hay. Vườn quả thuộc về"
                                                        + " bạn!"
                                                : "Một trận đấu đáng nhớ. Lần tới, hãy thử một"
                                                        + " chiến thuật mới.",
                                13,
                                MUTED));
        ui.body.getChildren().add(headline);

        // Score comparison bar
        int myScore = integer(me, "score"), otherScore = integer(other, "score");
        int totalScore = myScore + otherScore;
        HBox scoreBar = new HBox(0);
        scoreBar.setMaxWidth(Double.MAX_VALUE);
        scoreBar.setMinHeight(12);
        if (totalScore > 0) {
            Region myBar = new Region();
            Region otherBar = new Region();
            double myRatio =
                    (double) Math.max(0, myScore) / Math.max(1, Math.max(myScore, otherScore));
            double otherRatio =
                    (double) Math.max(0, otherScore) / Math.max(1, Math.max(myScore, otherScore));
            myBar.setStyle("-fx-background-color:" + LIME + ";");
            otherBar.setStyle("-fx-background-color:" + CORAL + ";");
            HBox.setHgrow(myBar, Priority.ALWAYS);
            HBox.setHgrow(otherBar, Priority.ALWAYS);
            myBar.setMaxWidth(Double.MAX_VALUE);
            otherBar.setMaxWidth(Double.MAX_VALUE);
            myBar.prefWidthProperty().bind(scoreBar.widthProperty().multiply(myRatio * 0.48));
            otherBar.prefWidthProperty().bind(scoreBar.widthProperty().multiply(otherRatio * 0.48));
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            scoreBar.getChildren().addAll(myBar, gap, otherBar);
        }
        ui.body.getChildren().add(scoreBar);

        HBox scorecards =
                new HBox(
                        24,
                        ResultScreen.resultCard(me, true),
                        ResultScreen.resultCard(other, false));
        scorecards.setAlignment(Pos.CENTER);
        VBox.setVgrow(scorecards, Priority.ALWAYS);
        ui.body.getChildren().add(scorecards);
        HBox actions =
                new HBox(
                        14,
                        button("Chơi tiếp  →", true, controller::findMatch),
                        button(
                                "Về trang chủ",
                                false,
                                () -> {
                                    ui.showScreen("home");
                                    controller.send(obj("type", "PROFILE_GET"));
                                }));
        actions.setAlignment(Pos.CENTER);
        ui.body.getChildren().add(actions);
        ui.savedStatus =
                text(
                        bool(model.result, "saved")
                                ? "Kết quả đã lưu vào lịch sử thi đấu."
                                : "Kết quả đang chờ lưu. Máy chủ sẽ tự thử lại.",
                        12,
                        bool(model.result, "saved") ? MUTED : CORAL);
        ui.savedStatus.setMaxWidth(Double.MAX_VALUE);
        ui.savedStatus.setAlignment(Pos.CENTER);
        ui.body.getChildren().add(ui.savedStatus);
        String reason = str(model.result, "reason");
        if (!reason.isBlank() && !reason.equals("TIME_UP") && !reason.equals("Hết giờ"))
            ui.toast(reason, false);
    }

    public static VBox resultCard(Map<String, Object> p, boolean own) {
        return ResultScreen.resultCard(p, own, own ? "BẠN" : "ĐỐI THỦ");
    }

    public static VBox resultCard(Map<String, Object> p, boolean own, String caption) {
        VBox card = panel(18);
        card.setSpacing(9);
        card.setPrefWidth(420);
        card.setMaxWidth(490);
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox name =
                new HBox(
                        10,
                        label(caption, 11, own ? LIME : CORAL, true),
                        spacer(),
                        label(displayName(p), 19, INK, true));
        card.getChildren()
                .addAll(
                        name,
                        label(String.valueOf(integer(p, "score")), 46, own ? LIME : CORAL, true),
                        divider(),
                        metric("Quả hứng đúng", integer(p, "correct")),
                        metric("Quả hứng sai", integer(p, "wrong")),
                        metric("Bom đã hứng", integer(p, "bombs")),
                        metric("Combo cao nhất", integer(p, "maxCombo")),
                        metric("Kỹ năng đã dùng", integer(p, "skills")));
        return card;
    }
}

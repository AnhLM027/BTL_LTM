package fruit.client.features.lobby;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;
import static fruit.client.shared.ui.GameArt.drawAvatar;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.divider;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.panel;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiComponents.stat;
import static fruit.client.shared.ui.UiComponents.tag;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.common.MessageData.displayName;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Lobby screen layout and control bindings. */
public final class LobbyScreen {
    private LobbyScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.body.setSpacing(12);
        ui.body.setPadding(new Insets(10, 24, 10, 24));
        ui.body.getStyleClass().removeIf(style -> style.startsWith("wood-skin"));
        ui.body.setBorder(Border.EMPTY);
        HBox heading =
                new HBox(
                        18,
                        new VBox(
                                5,
                                label("CHÀO MỪNG TRỞ LẠI", 10, LIME, true),
                                label("Xin chào, " + displayName(model.user) + "!", 26, INK, true)),
                        spacer(),
                        tag("MÙA QUẢ  /  01"));
        heading.setAlignment(Pos.CENTER_LEFT);

        HBox columns = new HBox(18);
        columns.setMinWidth(0);
        VBox.setVgrow(columns, Priority.ALWAYS);
        VBox hero = panel(12);
        hero.setMinWidth(0);
        hero.setSpacing(9);
        HBox.setHgrow(hero, Priority.ALWAYS);
        hero.getChildren()
                .addAll(
                        tag("ĐẤU TRƯỜNG TRÁI CÂY"),
                        label("Hứng quả ngon.\nGiành điểm lớn.", 30, INK, true),
                        text(
                                "Đối kháng thời gian thực 1v1. Cùng một vườn quả,\n"
                                        + "cùng một mục tiêu — ai sẽ là người chiến thắng?",
                                13,
                                MUTED));
        // Keep the first lobby's short illustration without imposing a fixed canvas width.
        StackPane clearing = new StackPane();
        clearing.setMinSize(0, 160);
        clearing.setMaxSize(720, 230);
        clearing.prefHeightProperty()
                .bind(
                        javafx.beans.binding.Bindings.min(
                                230,
                                javafx.beans.binding.Bindings.max(
                                        160, ui.root.heightProperty().subtract(560))));
        ui.illustration = new Canvas();
        ui.illustration.widthProperty().bind(clearing.widthProperty());
        ui.illustration.heightProperty().bind(clearing.heightProperty());
        clearing.getChildren().add(ui.illustration);
        ui.findButton =
                button(
                        model.queued ? "Hủy tìm trận" : "👉  Tìm trận ngay",
                        true,
                        () -> {
                            if (model.queued) controller.send(obj("type", "MATCH_CANCEL"));
                            else controller.findMatch();
                        });
        ui.findButton.setPrefWidth(220);
        ui.findButton.setMinHeight(48);
        ui.findButton.setStyle("-fx-padding:9 20;");
        ui.queueLabel =
                label(
                        model.queued ? "ĐANG TÌM ĐỐI THỦ" : "60 GIÂY  •  1 vs 1",
                        12,
                        model.queued ? LIME : MUTED,
                        true);
        HBox action = new HBox(14, ui.findButton, ui.queueLabel);
        action.setAlignment(Pos.CENTER_LEFT);
        hero.getChildren().addAll(clearing, action);

        VBox side = new VBox(12);
        side.setPrefWidth(294);
        side.setMinWidth(270);
        side.setMaxWidth(294);
        VBox player = panel(8);
        player.setSpacing(8);
        Canvas avatar = new Canvas(52, 52);
        drawAvatar(avatar.getGraphicsContext2D(), 0, 0, 52, integer(model.user, "avatarId"));
        Label playerName = label(displayName(model.user), 20, INK, true);
        playerName.setMinWidth(0);
        playerName.setMaxWidth(Double.MAX_VALUE);
        VBox identityText = new VBox(5, playerName, tag(str(model.user, "role", "PLAYER")));
        identityText.setMinWidth(0);
        HBox.setHgrow(identityText, Priority.ALWAYS);
        HBox identity = new HBox(12, avatar, identityText);
        identity.setAlignment(Pos.CENTER_LEFT);
        int wins = integer(model.user, "wins"),
                total = wins + integer(model.user, "losses") + integer(model.user, "draws");
        HBox stats =
                new HBox(
                        20,
                        stat(String.valueOf(wins), "THẮNG", LIME),
                        stat(String.valueOf(total), "TRẬN", INK),
                        stat(
                                total == 0 ? "—" : Math.round(100.0 * wins / total) + "%",
                                "TỈ LỆ",
                                CORAL));
        Button profileLink = button("Xem hồ sơ  ↗", false, () -> ui.navigate("profile"));
        profileLink.setStyle("-fx-padding:7 14;");
        player.getChildren().addAll(identity, divider(), stats, profileLink);
        VBox tip = panel(8);
        tip.setSpacing(8);
        Button rulesLink = button("Xem luật chơi  →", false, ui::help);
        rulesLink.setStyle("-fx-padding:7 14;");
        tip.getChildren()
                .addAll(
                        label("MẸO NHỎ, ĐIỂM LỚN", 10, LIME, true),
                        label("Combo ngọt ngào", 19, INK, true),
                        text(
                                "Mỗi 5 quả đúng liên tiếp, nhận thêm 20 điểm. Bỏ lỡ quả không làm"
                                        + " mất combo!",
                                12,
                                MUTED),
                        rulesLink);
        side.getChildren().addAll(player, tip);
        columns.getChildren().addAll(hero, side);

        ui.body.getChildren().addAll(heading, columns);
    }
}

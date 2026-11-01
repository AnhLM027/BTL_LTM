package fruit.client.features.gameplay;

import static fruit.client.shared.ui.GameArt.decorate;
import static fruit.client.shared.ui.GameArt.drawIcon;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.spacer;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.CYAN;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.PANEL;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Game screen layout and control bindings. */
public final class GameScreen {
    private GameScreen() {}

    public static void build(ClientScreens ui) {
        ClientController controller = ui.controller;

        ui.body.setSpacing(6);
        HBox header =
                new HBox(
                        15,
                        label("ĐẤU TRƯỜNG", 11, LIME, true),
                        spacer(),
                        label("←  →  DI CHUYỂN  ·  1  2  3  KỸ NĂNG", 10, INK, true));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 12, 4, 12));
        header.setBackground(
                new Background(
                        new BackgroundFill(Color.web(PANEL), CornerRadii.EMPTY, Insets.EMPTY)));
        decorate(header, 5);
        StackPane arenaBox = new StackPane();
        arenaBox.setMinWidth(0);
        arenaBox.setMinHeight(350);
        VBox.setVgrow(arenaBox, Priority.ALWAYS);
        ui.arena = new Canvas();
        ui.arena.widthProperty().bind(arenaBox.widthProperty());
        ui.arena.heightProperty().bind(arenaBox.heightProperty());
        arenaBox.getChildren().add(ui.arena);
        ui.body.getChildren().add(arenaBox);
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER);
        String[] titles = {"GỬI BOM", "LÀM CHẬM", "GIỎ NAM CHÂM"};
        String[] notes = {
            "30 năng lượng · Bom cảnh báo",
            "50 năng lượng · Giỏ chậm 3s",
            "70 năng lượng · +40% · 5s"
        };
        for (int i = 0; i < 3; i++) {
            final int skill = i + 1;
            Button b = button("", false, () -> controller.useSkill(skill));
            b.getStyleClass().add("skill-button");
            ui.skillButtons[i] = b;
            HBox content = new HBox(12);
            content.setAlignment(Pos.CENTER_LEFT);
            Canvas icon = new Canvas(44, 44);
            drawIcon(
                    icon.getGraphicsContext2D(),
                    i == 0 ? "BOMB" : i == 1 ? "FREEZE" : "MAGNET",
                    22,
                    22,
                    40);
            Label key =
                    label(String.valueOf(skill), 29, i == 0 ? CORAL : i == 1 ? CYAN : LIME, true);
            key.setMinWidth(28);
            content.getChildren()
                    .addAll(
                            icon,
                            key,
                            new VBox(
                                    4,
                                    label(titles[i], 14, INK, true),
                                    label(notes[i], 11, MUTED, false)));
            b.setGraphic(content);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefWidth(0);
            b.setMinWidth(0);
            decorate(b, 18);
            HBox.setHgrow(b, Priority.ALWAYS);
            controls.getChildren().add(b);
        }
        ui.skillStatus = label("Tích năng lượng bằng cách hứng đúng quả mục tiêu.", 11, LIME, true);
        header.getChildren()
                .addAll(
                        spacer(),
                        ui.skillStatus,
                        spacer(),
                        label("Đúng +10  /  Sai −5  /  Bom −10  /  5 combo +20", 10, INK, false));
        ui.body.getChildren().addAll(controls, header);
        Platform.runLater(
                () -> {
                    if (ui.arena != null) ui.arena.requestFocus();
                });
    }
}

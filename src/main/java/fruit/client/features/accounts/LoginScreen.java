package fruit.client.features.accounts;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.field;
import static fruit.client.shared.ui.UiComponents.formRow;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.panel;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.pixelFont;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/** Login screen layout and control bindings. */
public final class LoginScreen {
    private LoginScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        HBox columns = new HBox(28);
        columns.setAlignment(Pos.CENTER);
        VBox.setVgrow(columns, Priority.ALWAYS);
        ui.body.getChildren().add(columns);
        VBox hero = new VBox(15);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setMaxWidth(615);
        hero.setMinWidth(0);
        HBox.setHgrow(hero, Priority.ALWAYS);
        Label titleArt = label("FRUIT BATTLE", 54, LIME, true);
        titleArt.setFont(Font.font(pixelFont, 54));
        ui.illustration = new Canvas(540, 340);
        javafx.scene.layout.StackPane picture = new javafx.scene.layout.StackPane(ui.illustration);
        picture.setMinSize(0, 0);
        picture.setPrefSize(540, 340);
        picture.setMaxWidth(540);
        ui.illustration.widthProperty().bind(picture.widthProperty());
        ui.illustration.heightProperty().bind(picture.widthProperty().multiply(340.0 / 540));
        picture.prefHeightProperty().bind(picture.widthProperty().multiply(340.0 / 540));
        hero.getChildren()
                .addAll(
                        titleArt,
                        label("VƯỜN ĐẤU TRÁI CÂY", 16, INK, true),
                        picture,
                        text("Hai người chơi - Một mùa thu hoạch - 60 giây", 14, INK));
        VBox form = panel(24);
        form.setPrefWidth(365);
        form.setMinWidth(320);
        form.setMaxWidth(390);
        Label title =
                label(model.registering ? "Tham gia vườn đấu" : "Chào người chơi!", 26, INK, true);
        form.getChildren()
                .addAll(
                        label(model.registering ? "TẠO TÀI KHOẢN" : "ĐĂNG NHẬP", 11, LIME, true),
                        title,
                        text("Chọn giỏ của bạn và vào vườn.", 13, MUTED));
        ui.userInput = field("Tên đăng nhập", model.username);
        ui.passInput = new PasswordField();
        ui.passInput.setPromptText("Mật khẩu");
        ui.passInput.setText(model.password);
        TextField display = field("Tên hiển thị", "");
        form.getChildren()
                .addAll(formRow("TÀI KHOẢN", ui.userInput), formRow("MẬT KHẨU", ui.passInput));
        if (model.registering) form.getChildren().add(formRow("TÊN HIỂN THỊ", display));
        Button submit =
                button(
                        model.registering ? "Tạo tài khoản  →" : "Vào vườn đấu  →",
                        true,
                        () ->
                                controller.authenticate(
                                        ui.userInput.getText(),
                                        ui.passInput.getText(),
                                        model.host,
                                        String.valueOf(model.port),
                                        display.getText()));
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDisable(model.authPending || model.connecting);
        ui.passInput.setOnAction(e -> submit.fire());
        ui.userInput.setOnAction(e -> ui.passInput.requestFocus());
        form.getChildren()
                .addAll(
                        submit,
                        button(
                                model.registering
                                        ? "Đã có tài khoản?  Đăng nhập"
                                        : "Chưa có tài khoản?  Đăng ký",
                                false,
                                () -> {
                                    model.username = ui.userInput.getText();
                                    model.password = "";
                                    model.registering = !model.registering;
                                    ui.showScreen("login");
                                }));
        columns.getChildren().addAll(hero, form);
    }
}

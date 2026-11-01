package fruit.client.features.accounts;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;
import fruit.client.application.ClientState;
import static fruit.client.shared.ui.GameArt.drawAvatar;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.divider;
import static fruit.client.shared.ui.UiComponents.field;
import static fruit.client.shared.ui.UiComponents.formRow;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.metric;
import static fruit.client.shared.ui.UiComponents.panel;
import static fruit.client.shared.ui.UiComponents.tag;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.common.MessageData.displayName;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Profile screen layout and control bindings. */
public final class ProfileScreen {
    private ProfileScreen() {}

    public static void build(ClientScreens ui) {
        ClientState model = ui.model;
        ClientController controller = ui.controller;

        ui.pageTitle(
                "HỒ SƠ NGƯỜI CHƠI",
                "Một chút về bạn.",
                "Quản lý thông tin và theo dõi hành trình trong vườn đấu.");
        HBox columns = new HBox(22);
        VBox.setVgrow(columns, Priority.ALWAYS);
        ui.body.getChildren().add(columns);
        VBox summary = panel(26);
        summary.setPrefWidth(355);
        Canvas avatar = new Canvas(100, 100);
        drawAvatar(avatar.getGraphicsContext2D(), 0, 0, 100, integer(model.user, "avatarId"));
        javafx.scene.control.ToggleGroup avatars = new javafx.scene.control.ToggleGroup();
        HBox choices = new HBox(12);
        for (int i = 0; i < 2; i++) {
            final int id = i;
            Canvas portrait = new Canvas(64, 64);
            drawAvatar(portrait.getGraphicsContext2D(), 0, 0, 64, id);
            javafx.scene.control.ToggleButton choice =
                    new javafx.scene.control.ToggleButton(
                            i == 0 ? "Anh làm vườn" : "Chị làm vườn", portrait);
            choice.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
            choice.setToggleGroup(avatars);
            choice.setUserData(id);
            choice.setId("avatar-choice-" + id);
            Runnable style =
                    () ->
                            choice.setStyle(
                                    "-fx-background-color:"
                                            + (choice.isSelected() ? "#dbe3ad" : "#fff5d6")
                                            + ";-fx-border-color:"
                                            + (choice.isSelected() ? "#2d6029" : "#b59864")
                                            + ";-fx-border-width:3;-fx-padding:10;-fx-text-fill:#4b260b;");
            choice.selectedProperty().addListener((o, a, b) -> style.run());
            choice.setSelected(id == integer(model.user, "avatarId"));
            style.run();
            choices.getChildren().add(choice);
        }
        avatars.selectedToggleProperty()
                .addListener(
                        (o, previous, selected) -> {
                            if (selected == null) {
                                if (previous != null) previous.setSelected(true);
                                return;
                            }
                            avatar.getGraphicsContext2D().clearRect(0, 0, 100, 100);
                            drawAvatar(
                                    avatar.getGraphicsContext2D(),
                                    0,
                                    0,
                                    100,
                                    (int) selected.getUserData());
                        });
        summary.getChildren()
                .addAll(
                        avatar,
                        label(displayName(model.user), 28, INK, true),
                        label("@" + str(model.user, "username"), 13, MUTED, false),
                        tag(str(model.user, "role", "PLAYER")),
                        divider(),
                        metric("Chiến thắng", integer(model.user, "wins")),
                        metric("Thua", integer(model.user, "losses")),
                        metric("Hòa", integer(model.user, "draws")),
                        metric("Tổng điểm", integer(model.user, "totalScore")));
        VBox edit = panel(26);
        HBox.setHgrow(edit, Priority.ALWAYS);
        TextField name = field("Tên hiển thị", str(model.user, "displayName"));
        PasswordField old = new PasswordField(),
                fresh = new PasswordField(),
                repeat = new PasswordField();
        old.setPromptText("Mật khẩu hiện tại");
        fresh.setPromptText("Mật khẩu mới (tối thiểu 6 ký tự)");
        repeat.setPromptText("Nhập lại mật khẩu mới");
        edit.getChildren()
                .addAll(
                        label("Thông tin cá nhân", 23, INK, true),
                        label("CHỌN AVATAR", 10, MUTED, true),
                        choices,
                        formRow("TÊN HIỂN THỊ", name),
                        divider(),
                        label("Đổi mật khẩu", 19, INK, true),
                        text("Bỏ trống phần này nếu chỉ đổi tên hiển thị hoặc avatar.", 12, MUTED),
                        formRow("MẬT KHẨU HIỆN TẠI", old),
                        formRow("MẬT KHẨU MỚI", fresh),
                        formRow("XÁC NHẬN MẬT KHẨU", repeat),
                        button(
                                "Lưu thay đổi  →",
                                true,
                                () -> {
                                    if (name.getText().strip().isEmpty()) {
                                        ui.toast("Hãy nhập tên hiển thị.", true);
                                        return;
                                    }
                                    if (!fresh.getText().equals(repeat.getText())) {
                                        ui.toast("Hai mật khẩu mới chưa trùng nhau.", true);
                                        return;
                                    }
                                    if (!fresh.getText().isEmpty()
                                            && fresh.getText().length() < 6) {
                                        ui.toast("Mật khẩu mới cần ít nhất 6 ký tự.", true);
                                        return;
                                    }
                                    controller.send(
                                            obj(
                                                    "type",
                                                    "PROFILE_UPDATE",
                                                    "displayName",
                                                    name.getText().strip(),
                                                    "avatarId",
                                                    avatars.getSelectedToggle().getUserData(),
                                                    "oldPassword",
                                                    old.getText(),
                                                    "newPassword",
                                                    fresh.getText()));
                                    if (!fresh.getText().isEmpty())
                                        model.pendingPassword = fresh.getText();
                                }));
        columns.getChildren().addAll(summary, edit);
    }
}

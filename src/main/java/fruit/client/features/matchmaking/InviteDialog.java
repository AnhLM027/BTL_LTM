package fruit.client.features.matchmaking;

import static fruit.client.shared.ui.GameArt.drawAvatar;
import static fruit.client.shared.ui.GameArt.decorate;
import static fruit.client.shared.ui.UiComponents.button;
import static fruit.client.shared.ui.UiComponents.label;
import static fruit.client.shared.ui.UiComponents.text;
import static fruit.client.shared.ui.UiTheme.CORAL;
import static fruit.client.shared.ui.UiTheme.INK;
import static fruit.client.shared.ui.UiTheme.LIME;
import static fruit.client.shared.ui.UiTheme.MUTED;
import static fruit.client.shared.ui.UiTheme.PANEL;
import static fruit.common.MessageData.asMap;
import static fruit.common.MessageData.displayName;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientScreens;

import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Non-blocking lobby dialog that renders the current server-side online roster. */
public final class InviteDialog {
    private static Dialog<Void> browser;
    private static ClientScreens owner;
    private static VBox rows;
    private static TextField query;

    private InviteDialog() {}

    public static void show(ClientScreens ui) {
        if (browser != null && browser.isShowing()) {
            return;
        }
        owner = ui;
        browser = new Dialog<>();
        browser.initOwner(ui.stage);
        browser.setTitle("Mời người chơi • Fruit Battle Online");
        browser.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) browser.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Đóng");
        browser.setOnHidden(event -> {
            browser = null;
            owner = null;
            rows = null;
            query = null;
        });

        query = new TextField(ui.model.onlineQuery);
        query.setPromptText("Tìm theo tên hiển thị hoặc tài khoản");
        query.setOnAction(event -> ui.controller.loadOnlinePlayers(query.getText()));
        Button search = button("Tìm", true, () -> ui.controller.loadOnlinePlayers(query.getText()));
        Button refresh = button("Làm mới ↻", false, () -> ui.controller.loadOnlinePlayers(query.getText()));
        HBox tools = new HBox(8, query, search, refresh);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(query, Priority.ALWAYS);

        rows = new VBox(7);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(310);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox content =
                new VBox(
                        13,
                        label("NGƯỜI CHƠI TRỰC TUYẾN", 12, LIME, true),
                        text("Chỉ người đang sẵn sàng mới có thể nhận lời mời.", 13, MUTED),
                        tools,
                        scroll);
        content.setPadding(new Insets(18));
        content.setPrefWidth(610);
        decorate(content, 12);
        browser.getDialogPane().setContent(content);
        browser.getDialogPane().setStyle("-fx-background-color:" + PANEL + ";");
        render(ui);
        browser.show();
        ui.controller.loadOnlinePlayers(query.getText());
    }

    public static void refresh(ClientScreens ui) {
        if (browser == null || !browser.isShowing() || owner != ui) return;
        render(ui);
    }

    /** Closes the non-modal online roster when the player enters a match. */
    public static void close(ClientScreens ui) {
        if (browser != null && browser.isShowing() && owner == ui) browser.close();
    }

    public static void showIncoming(ClientScreens ui, Map<String, Object> invite) {
        Map<String, Object> from = asMap(invite.get("from"));
        String inviteId = str(invite, "inviteId");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(ui.stage);
        dialog.setTitle("Lời mời đấu");
        ButtonType accept = new ButtonType("Nhận lời", ButtonType.OK.getButtonData());
        ButtonType decline = new ButtonType("Từ chối", ButtonType.CANCEL.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(accept, decline);
        Canvas avatar = new Canvas(58, 58);
        drawAvatar(avatar.getGraphicsContext2D(), 0, 0, 58, integer(from, "avatarId"));
        VBox words =
                new VBox(
                        6,
                        label(displayName(from) + " mời bạn vào trận 1 vs 1", 17, INK, true),
                        text("Lời mời hết hạn sau " + integer(invite, "expiresIn", 25) + " giây.", 13, MUTED));
        HBox content = new HBox(14, avatar, words);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(18));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setStyle("-fx-background-color:" + PANEL + ";");
        ButtonType response = dialog.showAndWait().orElse(decline);
        ui.controller.respondInvite(inviteId, response == accept);
    }

    private static void render(ClientScreens ui) {
        if (rows == null) return;
        rows.getChildren().clear();
        if (ui.model.onlinePlayers.isEmpty()) {
            Region empty = new Region();
            empty.setMinHeight(8);
            rows.getChildren().addAll(label("Chưa có người chơi phù hợp đang online.", 14, MUTED, false), empty);
            return;
        }
        for (Map<String, Object> player : ui.model.onlinePlayers) {
            Canvas avatar = new Canvas(42, 42);
            drawAvatar(avatar.getGraphicsContext2D(), 0, 0, 42, integer(player, "avatarId"));
            VBox identity =
                    new VBox(
                            2,
                            label(displayName(player), 15, INK, true),
                            label("@" + str(player, "username"), 11, MUTED, false));
            identity.setMinWidth(0);
            HBox.setHgrow(identity, Priority.ALWAYS);
            boolean ready = bool(player, "inviteable");
            String status = str(player, "status");
            Button invite =
                    button(
                            ready ? "Mời" : "Không sẵn sàng",
                            ready,
                            () -> ui.controller.invitePlayer(integer(player, "id")));
            invite.setDisable(!ready);
            invite.setMinWidth(132);
            HBox row = new HBox(11, avatar, identity, label(status, 10, ready ? LIME : CORAL, true), invite);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.setStyle("-fx-background-color:#fff6dc;-fx-border-color:#d5ae70;");
            rows.getChildren().add(row);
        }
    }
}

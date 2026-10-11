package Client.fx;

import Client.ui.AssetPaths;
import Server.network.ProtocolMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.util.Map;
import java.util.function.Consumer;

/** Shared pixel-art invitation dialog used by the lobby and waiting room. */
final class FxInviteDialog {
    private static final String ASSET_ROOT = AssetPaths.WAITING_ROOM;

    private FxInviteDialog() {
    }

    static void show(Window owner, ProtocolMessage message, Consumer<Boolean> decision) {
        String roomId = message.fields().getOrDefault("roomId", "");
        String inviteId = message.fields().getOrDefault("inviteId", "");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Lời mời thi đấu");
        dialog.setResizable(false);
        if (owner != null) dialog.initOwner(owner);

        DialogPane pane = dialog.getDialogPane();
        FxAssets.styleDialog(pane);
        pane.setPadding(Insets.EMPTY);

        Pane content = new Pane();
        content.setPrefSize(560, 360);
        content.setMinSize(560, 360);
        content.setMaxSize(560, 360);
        content.getChildren().add(image("panels/invite2.png", 560, 360));

        Label title = label("LỜI MỜI THI ĐẤU", 24, Color.web("#4f2d18"));
        title.setLayoutX(50);
        title.setLayoutY(38);
        title.setPrefSize(460, 38);
        content.getChildren().add(title);

        ImageView basket = image("logo/logo-fruit-basket.png", 78, 78);
        basket.setLayoutX(241);
        basket.setLayoutY(82);
        content.getChildren().add(basket);

        Label body = label("Bạn nhận được lời mời vào phòng #" + roomId, 17, Color.web("#4f2d18"));
        body.setLayoutX(45);
        body.setLayoutY(175);
        body.setPrefSize(470, 32);
        content.getChildren().add(body);

        Label hint = label("Bạn có muốn tham gia trận đấu không?", 14, Color.web("#7a5238"));
        hint.setLayoutX(45);
        hint.setLayoutY(210);
        hint.setPrefSize(470, 28);
        content.getChildren().add(hint);

        Button accept = imageButton("buttons/green.png", "Chấp nhận", 165, 46);
        accept.setLayoutX(105);
        accept.setLayoutY(270);
        Button reject = imageButton("buttons/orange.png", "Từ chối", 165, 46);
        reject.setLayoutX(290);
        reject.setLayoutY(270);
        content.getChildren().addAll(accept, reject);

        Button close = new Button();
        close.setGraphic(image("panels/close_48x48.png", 48, 48));
        close.setLayoutX(498);
        close.setLayoutY(13);
        close.setPrefSize(48, 48);
        close.setMinSize(48, 48);
        close.setMaxSize(48, 48);
        close.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-cursor: hand;");
        content.getChildren().add(close);

        pane.setContent(content);
        accept.setOnAction(event -> finish(dialog, ButtonType.YES));
        reject.setOnAction(event -> finish(dialog, ButtonType.NO));
        close.setOnAction(event -> finish(dialog, ButtonType.NO));

        dialog.showAndWait().ifPresent(result -> {
            if (decision != null) decision.accept(result == ButtonType.YES);
        });
    }

    private static void finish(Dialog<ButtonType> dialog, ButtonType result) {
        dialog.setResult(result);
        dialog.close();
    }

    private static Label label(String text, double size, Color color) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setTextFill(color);
        label.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, size));
        return label;
    }

    private static ImageView image(String path, double width, double height) {
        ImageView view = FxAssets.image(ASSET_ROOT + path);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);
        view.setSmooth(false);
        return view;
    }

    private static Button imageButton(String path, String text, double width, double height) {
        Button button = new Button(text);
        button.setMinSize(width, height);
        button.setPrefSize(width, height);
        button.setMaxSize(width, height);
        button.setFont(Font.font(FxAssets.FONT_FAMILY, FontWeight.BOLD, 16));
        button.setTextFill(Color.WHITE);
        var resource = FxInviteDialog.class.getResource("/Client/assets/" + ASSET_ROOT + path);
        if (resource != null) {
            button.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
                    + " -fx-background-image: url('" + resource.toExternalForm() + "');"
                    + " -fx-background-repeat: no-repeat; -fx-background-position: center;"
                    + " -fx-background-size: " + width + "px " + height + "px;");
        }
        return button;
    }
}

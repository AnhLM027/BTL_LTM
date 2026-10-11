package Client.fx;

import Client.ui.AssetPaths;
import Server.network.ProtocolMessage;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

/** In-window invitation overlay; it does not create a second Stage or Dialog. */
final class FxInviteOverlay extends StackPane {
    private static final String ASSET_ROOT = AssetPaths.WAITING_ROOM;

    FxInviteOverlay(ProtocolMessage message, Consumer<Boolean> decision) {
        setPrefSize(390, 265);
        setMinSize(390, 265);
        setMaxSize(390, 265);

        Pane card = new Pane();
        card.setPrefSize(380, 255);
        card.setMinSize(380, 255);
        card.setMaxSize(380, 255);
        card.getChildren().add(image("panels/invite2.png", 380, 255));

        Label title = label("LỜI MỜI THI ĐẤU", 18, Color.web("#4f2d18"));
        title.setLayoutX(40);
        title.setLayoutY(25);
        title.setPrefSize(300, 30);
        card.getChildren().add(title);

        ImageView basket = image("logo/logo-fruit-basket.png", 56, 56);
        basket.setLayoutX(162);
        basket.setLayoutY(53);
        card.getChildren().add(basket);

        String roomId = message.fields().getOrDefault("roomId", "");
        Label body = label("Mời bạn vào phòng #" + roomId, 13, Color.web("#4f2d18"));
        body.setLayoutX(30);
        body.setLayoutY(113);
        body.setPrefSize(320, 25);
        card.getChildren().add(body);

        Label hint = label("Tham gia trận đấu?", 11, Color.web("#7a5238"));
        hint.setLayoutX(30);
        hint.setLayoutY(140);
        hint.setPrefSize(320, 22);
        card.getChildren().add(hint);

        Button accept = imageButton("buttons/green.png", "Chấp nhận", 110, 34);
        accept.setLayoutX(74);
        accept.setLayoutY(196);
        Button reject = imageButton("buttons/orange.png", "Từ chối", 110, 34);
        reject.setLayoutX(196);
        reject.setLayoutY(196);
        card.getChildren().addAll(accept, reject);

        Button close = new Button();
        close.setGraphic(image("panels/close_48x48.png", 32, 32));
        close.setLayoutX(340);
        close.setLayoutY(8);
        close.setPrefSize(32, 32);
        close.setMinSize(32, 32);
        close.setMaxSize(32, 32);
        close.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0; -fx-cursor: hand;");
        card.getChildren().add(close);

        accept.setOnAction(event -> finish(ButtonType.YES, decision));
        reject.setOnAction(event -> finish(ButtonType.NO, decision));
        close.setOnAction(event -> finish(ButtonType.NO, decision));

        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    private void finish(ButtonType result, Consumer<Boolean> decision) {
        setVisible(false);
        if (decision != null) decision.accept(result == ButtonType.YES);
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
        var resource = FxInviteOverlay.class.getResource("/Client/assets/" + ASSET_ROOT + path);
        if (resource != null) {
            button.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
                    + " -fx-background-image: url('" + resource.toExternalForm() + "');"
                    + " -fx-background-repeat: no-repeat; -fx-background-position: center;"
                    + " -fx-background-size: " + width + "px " + height + "px;");
        }
        return button;
    }
}

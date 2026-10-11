package Client.fx;

import Server.network.ProtocolMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Server-authoritative match result screen. */
public final class FxEndGameView extends StackPane {
    private Timeline returnTimer;
    public FxEndGameView(FxScreenNavigator navigator, ProtocolMessage result) {
        getStyleClass().add("fx-result-view");
        ImageView background = FxAssets.image("backgrounds/lobby-orchard-background.png");
        background.setPreserveRatio(false);
        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());
        String outcome = result.fields().getOrDefault("result", "DRAW");
        String asset = switch (outcome) {
            case "WIN" -> "results/result-win.png";
            case "LOSE" -> "results/result-lose.png";
            default -> "results/result-draw.png";
        };
        Image outcomeImage = FxAssets.load(asset);
        ImageView resultImage = new ImageView(outcomeImage);
        resultImage.setFitWidth(340); resultImage.setFitHeight(136); resultImage.setPreserveRatio(true);
        Label fallback = new Label(outcome);
        fallback.getStyleClass().add("result-title");
        StackPane heading = new StackPane(resultImage.getImage() == null ? fallback : resultImage);
        Label mine = new Label("Bạn: " + result.fields().getOrDefault("score", "0"));
        Label versus = new Label("VS");
        Label opponent = new Label("Đối thủ: " + result.fields().getOrDefault("opponentScore", "0"));
        mine.getStyleClass().add("result-score-me"); versus.getStyleClass().add("result-vs"); opponent.getStyleClass().add("result-score-opponent");
        HBox scores = new HBox(34, mine, versus, opponent); scores.setAlignment(Pos.CENTER);
        Label next = new Label("Tự động quay về phòng cũ sau 5 giây...");
        next.getStyleClass().add("result-countdown");
        Button back = new Button("Trở về phòng cũ"); back.getStyleClass().addAll("primary-button", "blue-button");
        Runnable goRoom = () -> { returnTimer.stop(); navigator.showPreviousRoom(); };
        back.setOnAction(event -> goRoom.run());
        VBox card = new VBox(18, heading, scores, next, back); card.setAlignment(Pos.CENTER); card.setPadding(new Insets(42)); card.getStyleClass().add("result-card");
        BorderPane content = new BorderPane(card); BorderPane.setAlignment(card, Pos.CENTER);
        getChildren().addAll(background, content);
        var css = getClass().getResource("/Client/fx/fx-client.css"); if (css != null) getStylesheets().add(css.toExternalForm());

        returnTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            int left = Integer.parseInt(next.getText().replaceAll("\\D+", ""));
            left--;
            if (left <= 0) { next.setText("Đang mở phòng cũ..."); goRoom.run(); }
            else next.setText("Tự động quay về phòng cũ sau " + left + " giây...");
        }));
        returnTimer.setCycleCount(5);
        returnTimer.play();
    }
}

package Client.fx;

import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;


/** Initial JavaFX screen; network-backed screens will be migrated next. */
public final class FxMainView extends StackPane {
    public FxMainView(FxScreenNavigator navigator) {
        setPrefSize(FxScreenNavigator.DESIGN_WIDTH, FxScreenNavigator.DESIGN_HEIGHT);
        getStyleClass().add("fx-main-view");

        ImageView background = FxAssets.image("backgrounds/bg1.gif");
        background.setPreserveRatio(false);
        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());

        BorderPane content = new BorderPane();
        content.setPickOnBounds(false);
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER);
        ImageView logo = FxAssets.image("logo/logo-fruit-basket.png");
        logo.setFitWidth(180);
        logo.setFitHeight(180);
        logo.setPreserveRatio(true);
        Label title = new Label("Hứng Hoa Quả");
        title.setFont(Font.font(FxAssets.FONT_FAMILY, 34));
        title.setTextFill(Color.web("#115336"));
        hero.getChildren().addAll(logo, title);
        content.setCenter(hero);

        Button login = new Button("Đăng nhập");
        Button register = new Button("Đăng ký");
        login.getStyleClass().addAll("primary-button", "blue-button");
        register.getStyleClass().addAll("primary-button", "orange-button");
        HBox actions = new HBox(20, login, register);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(0, 0, 14, 0));
        content.setBottom(actions);
        BorderPane.setAlignment(actions, Pos.CENTER);
        login.setOnAction(event -> navigator.showLogin());
        register.setOnAction(event -> navigator.showRegister());
        getChildren().addAll(background, content);
        loadStylesheet();
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }
}

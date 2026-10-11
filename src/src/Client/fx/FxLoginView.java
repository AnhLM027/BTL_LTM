package Client.fx;

import Client.session.GameClientSession;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

/** JavaFX login screen backed by the existing TCP session. */
public final class FxLoginView extends StackPane {
    private final FxScreenNavigator navigator;
    private final Button submit = new Button("Đăng nhập");
    private final Label status = new Label();

    public FxLoginView(FxScreenNavigator navigator) {
        this.navigator = navigator;
        getStyleClass().add("fx-form-view");
        ImageView background = FxAssets.image("backgrounds/bg1.gif");
        background.setPreserveRatio(false);
        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());

        TextField username = new TextField();
        username.setPromptText("Tên đăng nhập");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu");
        Label usernameLabel = new Label("Tên đăng nhập:");
        Label passwordLabel = new Label("Mật khẩu:");
        usernameLabel.getStyleClass().add("form-label");
        passwordLabel.getStyleClass().add("form-label");
        Label title = new Label("ĐĂNG NHẬP");
        title.getStyleClass().add("form-title");
        status.getStyleClass().add("form-status");
        submit.getStyleClass().addAll("primary-button", "blue-button");
        Button back = new Button("Quay lại");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> navigator.showMain());
        submit.setOnAction(event -> authenticate(username.getText().trim(), password.getText()));
        password.setOnAction(event -> submit.fire());

        VBox form = new VBox(8, title, usernameLabel, username, passwordLabel, password, submit, back, status);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(390);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.setPadding(new Insets(20, 28, 20, 28));
        form.getStyleClass().add("form-card");
        BorderPane content = new BorderPane(form);
        BorderPane.setAlignment(form, Pos.CENTER);
        getChildren().addAll(background, content);
        loadStylesheet();
    }

    private void authenticate(String username, String password) {
        if (username.isBlank() || password.isBlank()) {
            status.setText("Vui lòng nhập đủ tên đăng nhập và mật khẩu.");
            return;
        }
        submit.setDisable(true);
        status.setText("Đang kết nối máy chủ...");
        Task<GameClientSession.Identity> task = new Task<>() {
            @Override protected GameClientSession.Identity call() throws Exception {
                return GameClientSession.instance().login(username, password);
            }
        };
        task.setOnSucceeded(event -> {
            GameClientSession.Identity identity = task.getValue();
            if ("ADMIN".equalsIgnoreCase(identity.role())) {
                navigator.showAdmin();
            } else {
                navigator.showLobby();
            }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            status.setText(error == null || error.getMessage() == null
                    ? "Đăng nhập thất bại." : error.getMessage());
            submit.setDisable(false);
        });
        Thread.ofVirtual().name("fx-login").start(task);
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }
}

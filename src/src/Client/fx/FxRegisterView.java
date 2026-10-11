package Client.fx;

import Client.controller.RegisterController;
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

/** JavaFX account registration screen. */
public final class FxRegisterView extends StackPane {
    private final FxScreenNavigator navigator;
    private final Button submit = new Button("Đăng ký");
    private final Label status = new Label();

    public FxRegisterView(FxScreenNavigator navigator) {
        this.navigator = navigator;
        getStyleClass().add("fx-form-view");
        ImageView background = FxAssets.image("backgrounds/bg1.gif");
        background.setPreserveRatio(false);
        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());
        Label title = new Label("TẠO TÀI KHOẢN");
        title.getStyleClass().add("form-title");
        TextField username = new TextField();
        username.setPromptText("Tên đăng nhập");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Nhập lại mật khẩu");
        Label usernameLabel = new Label("Tên đăng nhập:");
        Label passwordLabel = new Label("Mật khẩu:");
        Label confirmLabel = new Label("Xác nhận mật khẩu:");
        usernameLabel.getStyleClass().add("form-label");
        passwordLabel.getStyleClass().add("form-label");
        confirmLabel.getStyleClass().add("form-label");
        status.getStyleClass().add("form-status");
        submit.getStyleClass().addAll("primary-button", "orange-button");
        Button back = new Button("Quay lại đăng nhập");
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> navigator.showLogin());
        submit.setOnAction(event -> register(username.getText().trim(), password.getText(), confirm.getText()));
        VBox form = new VBox(7, title, usernameLabel, username, passwordLabel, password,
                confirmLabel, confirm, submit, back, status);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(390);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.setPadding(new Insets(18, 28, 18, 28));
        form.getStyleClass().add("form-card");
        BorderPane content = new BorderPane(form);
        BorderPane.setAlignment(form, Pos.CENTER);
        getChildren().addAll(background, content);
        loadStylesheet();
    }

    private void register(String username, String password, String confirm) {
        if (username.isBlank() || password.isBlank()) {
            status.setText("Vui lòng nhập đủ thông tin.");
            return;
        }
        if (!password.equals(confirm)) {
            status.setText("Mật khẩu nhập lại không khớp.");
            return;
        }
        submit.setDisable(true);
        status.setText("Đang tạo tài khoản...");
        Task<String> task = new Task<>() {
            @Override protected String call() { return new RegisterController().register(username, password); }
        };
        task.setOnSucceeded(event -> {
            String error = task.getValue();
            if (error == null) navigator.showLogin();
            else { status.setText(error); submit.setDisable(false); }
        });
        task.setOnFailed(event -> { status.setText("Đăng ký thất bại."); submit.setDisable(false); });
        Thread.ofVirtual().name("fx-register").start(task);
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
    }
}

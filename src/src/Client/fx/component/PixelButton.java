package Client.fx.component;

import Client.fx.FxAssets;
import Client.fx.util.SoundManager;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

/**
 * Reusable pixel-art styled button with hover and click animations,
 * sound effects, and enabled/disabled state toggling.
 */
public class PixelButton extends StackPane {
    public static final String BTN_GREEN = "ui/waiting-room/buttons/green.png";
    public static final String BTN_BLUE = "ui/waiting-room/buttons/blue.png";
    public static final String BTN_ORANGE = "ui/waiting-room/buttons/orange.png";
    public static final String BTN_DISABLED = "ui/waiting-room/buttons/disabled.png";

    private final ImageView backgroundView;
    private final Label textLabel;

    private String activeImagePath;
    private String disabledImagePath = BTN_DISABLED;
    private Runnable onAction;

    public PixelButton(String imagePath, String text, double width, double height) {
        this(imagePath, text, width, height, 16);
    }

    public PixelButton(String imagePath, String text, double width, double height, double fontSize) {
        this.activeImagePath = normalizeButtonPath(imagePath);

        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        setAlignment(Pos.CENTER);

        backgroundView = new ImageView();
        backgroundView.setFitWidth(width);
        backgroundView.setFitHeight(height);
        backgroundView.setPreserveRatio(false);
        backgroundView.setSmooth(false);

        textLabel = new Label(text);
        textLabel.setFont(Font.font(FxAssets.FONT_FAMILY, fontSize));
        textLabel.setTextFill(Color.WHITE);
        textLabel.setAlignment(Pos.CENTER);
        textLabel.setMouseTransparent(true);

        DropShadow textShadow = new DropShadow();
        textShadow.setColor(Color.rgb(0, 0, 0, 0.7));
        textShadow.setRadius(2);
        textShadow.setOffsetX(1.5);
        textShadow.setOffsetY(1.5);
        textLabel.setEffect(textShadow);

        getChildren().addAll(backgroundView, textLabel);

        // Listen to JavaFX disable property
        disableProperty().addListener((obs, oldVal, newVal) -> {
            setScaleX(1.0);
            setScaleY(1.0);
            updateVisualState();
        });

        updateVisualState();
        setupInteractivity();
    }

    private static String normalizeButtonPath(String path) {
        if (path == null || path.isBlank()) return BTN_BLUE;
        if (path.startsWith("buttons/")) {
            return "ui/waiting-room/" + path;
        }
        return path;
    }

    private void updateVisualState() {
        boolean isDis = isDisable();
        String targetPath = isDis ? disabledImagePath : activeImagePath;
        Image img = FxAssets.load(targetPath);
        if (img != null) {
            backgroundView.setImage(img);
        }
        if (isDis) {
            setCursor(Cursor.DEFAULT);
            textLabel.setTextFill(Color.rgb(200, 200, 200, 0.75));
        } else {
            setCursor(Cursor.HAND);
            textLabel.setTextFill(Color.WHITE);
        }
    }

    private void setupInteractivity() {
        setOnMouseEntered(e -> {
            if (!isDisable()) {
                animateScale(1.05, 80);
            }
        });

        setOnMouseExited(e -> {
            if (!isDisable()) {
                animateScale(1.0, 80);
            }
        });

        setOnMousePressed(e -> {
            if (!isDisable()) {
                animateScale(0.96, 50);
            }
        });

        setOnMouseReleased(e -> {
            if (!isDisable()) {
                animateScale(isHover() ? 1.05 : 1.0, 50);
            }
        });

        setOnMouseClicked(e -> {
            if (!isDisable()) {
                SoundManager.getInstance().playButtonClick();
                if (onAction != null) {
                    onAction.run();
                }
            }
        });
    }

    private void animateScale(double target, double millis) {
        ScaleTransition st = new ScaleTransition(Duration.millis(millis), this);
        st.setToX(target);
        st.setToY(target);
        st.play();
    }

    public void setText(String text) {
        textLabel.setText(text);
    }

    public String getText() {
        return textLabel.getText();
    }

    public void setOnAction(Runnable onAction) {
        this.onAction = onAction;
    }

    public void setActiveImagePath(String path) {
        this.activeImagePath = normalizeButtonPath(path);
        updateVisualState();
    }

    public void setDisabledImagePath(String path) {
        this.disabledImagePath = normalizeButtonPath(path);
        updateVisualState();
    }

    // Factory methods for quick construction
    public static PixelButton green(String text, double width, double height, Runnable onAction) {
        PixelButton btn = new PixelButton(BTN_GREEN, text, width, height);
        btn.setOnAction(onAction);
        return btn;
    }

    public static PixelButton blue(String text, double width, double height, Runnable onAction) {
        PixelButton btn = new PixelButton(BTN_BLUE, text, width, height);
        btn.setOnAction(onAction);
        return btn;
    }

    public static PixelButton orange(String text, double width, double height, Runnable onAction) {
        PixelButton btn = new PixelButton(BTN_ORANGE, text, width, height);
        btn.setOnAction(onAction);
        return btn;
    }
}

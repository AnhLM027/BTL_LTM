package Client.fx;

import Client.fx.util.SoundManager;
import Client.session.GameClientSession;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Pixel-art shop administration dashboard for Fruit Battle Online.
 * Coordinates smooth transitions between Accounts Counter and Fruits Counter.
 */
public final class FxAdminView extends StackPane {
    private static final double DESIGN_W = 1080;
    private static final double DESIGN_H = 760;

    private final FxScreenNavigator navigator;
    private final Pane design = new Pane();
    private final StackPane sceneContainer = new StackPane();

    private final AdminAccountsPane accountsPane;
    private final AdminFruitsPane fruitsPane;

    private boolean animating = false;

    public FxAdminView(FxScreenNavigator navigator) {
        this.navigator = navigator;

        design.setMinSize(DESIGN_W, DESIGN_H);
        design.setPrefSize(DESIGN_W, DESIGN_H);
        design.setMaxSize(DESIGN_W, DESIGN_H);

        sceneContainer.setMinSize(DESIGN_W, DESIGN_H);
        sceneContainer.setPrefSize(DESIGN_W, DESIGN_H);
        sceneContainer.setMaxSize(DESIGN_W, DESIGN_H);

        // Clip the scene container so slide transitions stay within 1080x760
        Rectangle clip = new Rectangle(DESIGN_W, DESIGN_H);
        sceneContainer.setClip(clip);

        accountsPane = new AdminAccountsPane(this::switchToFruits, this::logout);
        fruitsPane = new AdminFruitsPane(this::switchToAccounts, this::logout);

        // Accounts pane starts visible at (0,0)
        accountsPane.setTranslateX(0);
        accountsPane.setVisible(true);

        // Fruits pane starts hidden off-screen to the right (+1080)
        fruitsPane.setTranslateX(DESIGN_W);
        fruitsPane.setVisible(false);

        sceneContainer.getChildren().addAll(accountsPane, fruitsPane);
        design.getChildren().add(sceneContainer);
        getChildren().add(design);

        bindDesignScale();
        loadStylesheet();

        accountsPane.onShow();
        SoundManager.getInstance().playAdminBgm();
    }

    private void bindDesignScale() {
        var scale = Bindings.createDoubleBinding(
                () -> Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H),
                widthProperty(), heightProperty());
        design.scaleXProperty().bind(scale);
        design.scaleYProperty().bind(scale);
        design.translateXProperty().bind(Bindings.createDoubleBinding(
                () -> (getWidth() - DESIGN_W * scale.get()) / 2,
                widthProperty(), heightProperty()));
        design.translateYProperty().bind(Bindings.createDoubleBinding(
                () -> (getHeight() - DESIGN_H * scale.get()) / 2,
                widthProperty(), heightProperty()));
    }

    private void loadStylesheet() {
        var css = getClass().getResource("/Client/fx/fx-client.css");
        if (css != null && !getStylesheets().contains(css.toExternalForm())) {
            getStylesheets().add(css.toExternalForm());
        }
    }

    private void switchToFruits() {
        if (animating) return;
        animating = true;

        SoundManager.getInstance().playShopSwitch();

        fruitsPane.setVisible(true);
        fruitsPane.setTranslateX(DESIGN_W);

        TranslateTransition slideOutAccounts = new TranslateTransition(Duration.millis(380), accountsPane);
        slideOutAccounts.setFromX(0);
        slideOutAccounts.setToX(-DESIGN_W);
        slideOutAccounts.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideInFruits = new TranslateTransition(Duration.millis(380), fruitsPane);
        slideInFruits.setFromX(DESIGN_W);
        slideInFruits.setToX(0);
        slideInFruits.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition transition = new ParallelTransition(slideOutAccounts, slideInFruits);
        transition.setOnFinished(e -> {
            accountsPane.setVisible(false);
            fruitsPane.onShow();
            animating = false;
        });
        transition.play();
    }

    private void switchToAccounts() {
        if (animating) return;
        animating = true;

        SoundManager.getInstance().playShopSwitch();

        accountsPane.setVisible(true);
        accountsPane.setTranslateX(-DESIGN_W);

        TranslateTransition slideOutFruits = new TranslateTransition(Duration.millis(380), fruitsPane);
        slideOutFruits.setFromX(0);
        slideOutFruits.setToX(DESIGN_W);
        slideOutFruits.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideInAccounts = new TranslateTransition(Duration.millis(380), accountsPane);
        slideInAccounts.setFromX(-DESIGN_W);
        slideInAccounts.setToX(0);
        slideInAccounts.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition transition = new ParallelTransition(slideOutFruits, slideInAccounts);
        transition.setOnFinished(e -> {
            fruitsPane.setVisible(false);
            accountsPane.onShow();
            animating = false;
        });
        transition.play();
    }

    public void dispose() {
        SoundManager.getInstance().stopAdminBgm();
    }

    private void logout() {
        dispose();
        try {
            GameClientSession.instance().logout();
        } catch (Exception ignored) {
        }
        navigator.showLogin();
    }
}

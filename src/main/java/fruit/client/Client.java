package fruit.client;

import static javafx.application.Application.launch;

import fruit.client.application.ClientController;
import fruit.client.application.ClientScreens;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/** JavaFX entry point. */
public final class Client extends Application {
    private ClientScreens screens;

    @Override
    public void start(Stage stage) {
        screens = new ClientScreens(new ClientController(Platform::runLater));
        screens.start(stage, getParameters().getNamed());
    }

    @Override
    public void stop() {
        if (screens != null) screens.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package fruit.client.features.gameplay;

import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;

public final class GameplayActions {
    private final ClientController controller;

    public GameplayActions(ClientController controller) {
        this.controller = controller;
    }

    public void move(int next) {

        if (!controller.model.screen.equals("game")) next = 0;
        if (next != controller.model.direction) {
            controller.model.direction = next;
            if (controller.model.connected)
                controller.send(
                        obj("type", "PLAYER_MOVE", "direction", controller.model.direction));
        }
    }

    public void stopMoving() {
        controller.view().clearHeld();
        if (controller.model.direction != 0) {
            controller.model.direction = 0;
            if (controller.model.connected)
                controller.send(obj("type", "PLAYER_MOVE", "direction", 0));
        }
    }

    public void useSkill(int skill) {
        if (!controller.model.screen.equals("game")
                || !str(controller.model.state, "phase").equals("PLAYING")) return;
        controller.send(obj("type", "USE_SKILL", "skill", skill));
        controller.view().focusArena();
    }
}

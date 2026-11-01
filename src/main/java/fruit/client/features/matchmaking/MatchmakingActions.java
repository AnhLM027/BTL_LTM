package fruit.client.features.matchmaking;

import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;

import java.util.Map;

public final class MatchmakingActions {
    private final ClientController controller;

    public MatchmakingActions(ClientController controller) {
        this.controller = controller;
    }

    public void findMatch() {
        if (!controller.model.connected && !controller.model.snapshotMode) {
            controller.view().toast("Kết nối lại máy chủ trước khi tìm trận.", true);
            return;
        }
        if (controller.model.snapshotMode) return;
        controller.view().showScreen("home");
        controller.send(obj("type", "MATCH_FIND"));
    }

    public void receiveQueue(Map<String, Object> event) {
        controller.model.queued = bool(event, "queued");
        if (controller.model.queued) controller.model.queueNanos = System.nanoTime();
        if (!controller.model.screen.equals("home")) {
            controller.view().showScreen("home");
        } else {
            controller.view().queueChanged(controller.model.queued);
        }
        controller
                .view()
                .toast(
                        controller.model.queued
                                ? "Đã vào hàng đợi. Đang tìm người chơi thứ hai…"
                                : str(event, "message", "Đã hủy tìm trận."),
                        false);
    }
}

package fruit.client.features.matchmaking;

import static fruit.common.MessageData.maps;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;

import java.util.Map;

/** Client requests and incoming events for direct invitations in the lobby. */
public final class InviteActions {
    private final ClientController controller;

    public InviteActions(ClientController controller) {
        this.controller = controller;
    }

    public void loadOnlinePlayers(String query) {
        if (!controller.model.connected || controller.model.snapshotMode) return;
        controller.model.onlineQuery = query == null ? "" : query.strip();
        controller.send(obj("type", "ONLINE_PLAYERS", "query", controller.model.onlineQuery));
    }

    public void invitePlayer(int targetId) {
        if (controller.model.queued) {
            controller.view().toast("Hủy tìm trận trước khi gửi lời mời.", true);
            return;
        }
        controller.send(obj("type", "INVITE_SEND", "targetId", targetId));
    }

    public void respondInvite(String inviteId, boolean accepted) {
        controller.send(obj("type", accepted ? "INVITE_ACCEPT" : "INVITE_DECLINE", "inviteId", inviteId));
    }

    public void receiveOnlinePlayers(Map<String, Object> event) {
        controller.model.onlinePlayers = maps(event.get("rows"));
        controller.view().onlinePlayersUpdated();
    }

    public void receiveInvite(Map<String, Object> event) {
        controller.view().inviteReceived(event);
    }

    public void receiveInviteStatus(Map<String, Object> event) {
        String message = str(event, "message");
        if (!message.isBlank()) controller.view().toast(message, false);
        if (!controller.model.onlinePlayers.isEmpty()) loadOnlinePlayers(controller.model.onlineQuery);
    }
}

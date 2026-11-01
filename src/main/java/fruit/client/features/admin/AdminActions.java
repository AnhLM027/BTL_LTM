package fruit.client.features.admin;

import static fruit.common.Protocol.obj;

import fruit.client.application.ClientController;

public final class AdminActions {
    private final ClientController controller;

    public AdminActions(ClientController controller) {
        this.controller = controller;
    }

    public void loadAdmin() {
        controller.send(
                obj(
                        "type",
                        "ADMIN_GET",
                        "userOffset",
                        controller.model.adminUserOffset,
                        "matchOffset",
                        controller.model.adminMatchOffset,
                        "userQuery",
                        controller.model.adminUserQuery,
                        "matchQuery",
                        controller.model.adminMatchQuery,
                        "limit",
                        50));
    }
}

package fruit.client.features.accounts;

import static fruit.common.MessageData.asMap;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.application.ClientController;

import java.util.Map;

public final class AccountActions {
    private final ClientController controller;

    public AccountActions(ClientController controller) {
        this.controller = controller;
    }

    public void authenticate(
            String userText, String passText, String hostText, String portText, String display) {
        if (controller.model.authPending || controller.model.connecting) return;
        String requestedUser = userText.strip();
        String requestedPass = passText;
        int requestedPort;
        try {
            requestedPort = Integer.parseInt(portText.strip());
            if (requestedPort < 1 || requestedPort > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            controller.view().toast("Cổng phải là số từ 1 đến 65535.", true);
            return;
        }
        if (requestedUser.isEmpty() || requestedPass.isEmpty()) {
            controller.view().toast("Hãy nhập đầy đủ tài khoản và mật khẩu.", true);
            return;
        }
        if (controller.model.registering && requestedPass.length() < 6) {
            controller.view().toast("Mật khẩu cần ít nhất 6 ký tự.", true);
            return;
        }
        if (controller.model.registering && display.strip().isEmpty()) {
            controller.view().toast("Hãy nhập tên hiển thị.", true);
            return;
        }
        controller.model.username = requestedUser;
        controller.model.password = requestedPass;
        controller.model.host = hostText.strip();
        controller.model.port = requestedPort;
        if (controller.model.host.isEmpty()) {
            controller.view().toast("Hãy nhập địa chỉ máy chủ.", true);
            return;
        }
        controller.model.authPending = true;
        Map<String, Object> request =
                obj(
                        "type",
                        controller.model.registering ? "REGISTER" : "LOGIN",
                        "username",
                        controller.model.username,
                        "password",
                        controller.model.password,
                        "displayName",
                        display.strip());
        controller.connection().connect(request);
        controller
                .view()
                .toast(
                        "Đang kết nối đến "
                                + controller.model.host
                                + ":"
                                + controller.model.port
                                + "…",
                        false);
    }

    public void logout() {
        controller.connection().logout();
        controller.model.connected = false;
        controller.model.connecting = false;
        controller.model.authPending = false;
        controller.model.queued = false;
        // Không lưu mật khẩu ra tệp; đăng xuất xóa cả thông tin trong bộ nhớ.
        controller.model.username = "";
        controller.model.password = "";
        controller.model.pendingPassword = "";
        controller.model.user = obj();
        controller.model.state = obj();
        controller.model.result = obj();
        controller.view().showScreen("login");
    }

    public void receiveProfile(Map<String, Object> event) {
        if (event.containsKey("ok") && !bool(event, "ok")) {
            controller.model.pendingPassword = "";
            controller.view().toast(str(event, "message", "Không thể cập nhật hồ sơ."), true);
            return;
        }
        if (event.containsKey("user")) controller.model.user = asMap(event.get("user"));
        if (event.containsKey("message")
                && bool(event, "ok")
                && !controller.model.pendingPassword.isEmpty()) {
            controller.model.password = controller.model.pendingPassword;
            controller.model.pendingPassword = "";
        }
        if (controller.model.screen.equals("profile") || controller.model.screen.equals("home"))
            controller.view().showScreen(controller.model.screen);
        if (event.containsKey("message")) controller.view().toast(str(event, "message"), false);
    }
}

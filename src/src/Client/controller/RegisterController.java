package Client.controller;

import Client.Constants;
import Client.network.TcpGameClient;
import Server.network.ProtocolMessage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RegisterController {

    /**
     * Attempts to register a new account.
     *
     * @return {@code null} if registration succeeded, or a non-null user-facing
     *         error message if it failed (duplicate username, network error, etc.)
     */
    public String register(String username, String password) {
        try (TcpGameClient client = new TcpGameClient()) {
            CompletableFuture<String> result = new CompletableFuture<>();
            client.addListener(new TcpGameClient.MessageListener() {
                public void onMessage(ProtocolMessage m) {
                    switch (m.type()) {
                        case "REGISTER_SUCCESS" -> result.complete(null); // null = success
                        case "ERROR"            -> result.complete(
                                m.fields().getOrDefault("message", "Đăng ký thất bại. Vui lòng thử lại."));
                        case "LOGIN_FAILED"     -> result.complete("Tên đăng nhập đã tồn tại.");
                        default                 -> {} // ignore unrelated messages
                    }
                }
            });
            client.connect(Constants.IP_SERVER, Constants.PORT);
            client.send(new ProtocolMessage("REGISTER", Map.of("username", username, "password", password)));
            // null means success; non-null means error message
            return result.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "Không nhận được phản hồi từ máy chủ (timeout). Vui lòng thử lại.";
        } catch (Exception e) {
            return "Lỗi kết nối: " + e.getMessage();
        }
    }
}

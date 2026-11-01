package fruit.server.features.accounts;

import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.application.GameServer;
import fruit.server.infrastructure.network.Session;

import java.util.Base64;
import java.util.Map;

/** Accounts feature handlers; executed only on the server engine thread. */
public final class AccountService {
    private final GameServer server;

    public AccountService(GameServer server) {
        this.server = server;
    }

    public void bootstrapAdmin() throws Exception {
        if (server.store.users.values().stream().anyMatch(u -> "ADMIN".equals(u.role))) return;
        String name = GameServer.setting("fruit.admin.username", "FRUIT_ADMIN_USERNAME", "admin");
        if (!validUsername(name))
            throw new IllegalArgumentException("Invalid administrator username");
        String password = GameServer.setting("fruit.admin.password", "FRUIT_ADMIN_PASSWORD", "");
        boolean generated = password.isEmpty();
        if (generated)
            password =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(server.randomBytes(12));
        if (!validPassword(password))
            throw new IllegalArgumentException(
                    "Administrator password must contain 6–128 characters");
        User user = new User(server.store.nextId++, name, "Quản trị viên");
        user.role = "ADMIN";
        user.changePassword(password);
        server.store.users.put(user.id, user);
        server.store.saveUser(user);
        server.display.info("Administrator account created: " + name);
        if (generated)
            server.display.info("Initial administrator password (shown once): " + password);
    }

    public void authenticate(Session session, Map<String, Object> request, String type)
            throws Exception {
        if (session.user != null) {
            session.error("Bạn đã đăng nhập.");
            return;
        }
        if (++session.authAttempts > 12) {
            session.authFailure("Quá nhiều lần thử. Hãy kết nối lại sau.");
            return;
        }
        String username = str(request, "username").trim();
        String password = str(request, "password");
        if (!validUsername(username) || !validPassword(password)) {
            session.authFailure("Tài khoản gồm 3–24 ký tự chữ, số, _. Mật khẩu gồm 6–128 ký tự.");
            return;
        }
        User user = server.store.findUsername(username);
        if (type.equals("REGISTER")) {
            if (user != null) {
                session.authFailure("Tên tài khoản đã tồn tại.");
                return;
            }
            String displayName = str(request, "displayName", username).trim();
            if (displayName.isEmpty()) displayName = username;
            if (!validDisplayName(displayName)) {
                session.authFailure("Tên hiển thị phải có 2–24 ký tự.");
                return;
            }
            user = new User(server.store.nextId++, username, displayName);
            user.changePassword(password);
            server.store.users.put(user.id, user);
            try {
                server.store.saveUser(user);
            } catch (Exception e) {
                server.store.users.remove(user.id);
                throw e;
            }
        } else if (user == null || !user.checkPassword(password)) {
            session.authFailure("Tài khoản hoặc mật khẩu không đúng.");
            return;
        }
        if (user.locked) {
            session.authFailure("Tài khoản đã bị khóa.");
            return;
        }
        Session existing = server.online.get(user.id);
        if (existing != null && !existing.closed.get()) {
            session.authFailure("Tài khoản đang được sử dụng trên một kết nối khác.");
            return;
        }
        if (existing != null) server.detach(existing);
        session.user = user;
        session.authAttempts = 0;
        server.online.put(user.id, session);
        session.send(
                obj(
                        "type",
                        "AUTH",
                        "ok",
                        true,
                        "message",
                        "Đăng nhập thành công.",
                        "user",
                        user.view()));
        server.rejoin(session, false);
    }

    public void updateProfile(Session session, Map<String, Object> request) throws Exception {
        User user = session.user;
        String displayName = str(request, "displayName", user.displayName).trim();
        String newPassword = str(request, "newPassword");
        Object requestedAvatar = request.getOrDefault("avatarId", user.avatarId);
        if (!(requestedAvatar instanceof Number number)
                || number.doubleValue() != number.intValue()
                || number.intValue() < 0
                || number.intValue() > 1) {
            session.send(
                    obj(
                            "type",
                            "PROFILE",
                            "ok",
                            false,
                            "message",
                            "Hãy chọn một trong hai avatar.",
                            "user",
                            user.view()));
            return;
        }
        int avatarId = ((Number) requestedAvatar).intValue();
        if (!validDisplayName(displayName)) {
            session.send(
                    obj(
                            "type",
                            "PROFILE",
                            "ok",
                            false,
                            "message",
                            "Tên hiển thị phải có 2–24 ký tự.",
                            "user",
                            user.view()));
            return;
        }
        if (!newPassword.isEmpty()
                && (!validPassword(newPassword)
                        || !user.checkPassword(str(request, "oldPassword")))) {
            session.send(
                    obj(
                            "type",
                            "PROFILE",
                            "ok",
                            false,
                            "message",
                            "Mật khẩu cũ không đúng hoặc mật khẩu mới không hợp lệ.",
                            "user",
                            user.view()));
            return;
        }
        String oldName = user.displayName, oldSalt = user.salt, oldHash = user.hash;
        int oldAvatar = user.avatarId;
        user.displayName = displayName;
        user.avatarId = avatarId;
        if (!newPassword.isEmpty()) user.changePassword(newPassword);
        try {
            server.store.saveUser(user);
        } catch (Exception e) {
            user.displayName = oldName;
            user.avatarId = oldAvatar;
            user.salt = oldSalt;
            user.hash = oldHash;
            throw e;
        }
        session.send(
                obj(
                        "type",
                        "PROFILE",
                        "ok",
                        true,
                        "message",
                        "Đã cập nhật hồ sơ.",
                        "user",
                        user.view()));
    }

    public static boolean validUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,24}");
    }

    public static boolean validPassword(String value) {
        return value != null && value.length() >= 6 && value.length() <= 128;
    }

    public static boolean validDisplayName(String value) {
        return value != null
                && value.length() >= 2
                && value.length() <= 24
                && value.codePoints().noneMatch(Character::isISOControl);
    }
}

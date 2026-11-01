package fruit.server.features.admin;

import static fruit.common.MessageData.displayName;
import static fruit.common.MessageData.recordPlayer;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.Match;
import fruit.server.infrastructure.network.Session;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin feature handlers; executed only on the server engine thread. */
public final class AdminService {
    private final GameServer server;

    public AdminService(GameServer server) {
        this.server = server;
    }

    public void adminData(Session session, Map<String, Object> request) {
        if (!isAdmin(session)) return;
        long totalPlayers =
                server.store.users.values().stream().filter(u -> !u.role.equals("ADMIN")).count();
        long locked = server.store.users.values().stream().filter(u -> u.locked).count();
        int userOffset = Math.max(0, integer(request, "userOffset"));
        int matchOffset = Math.max(0, integer(request, "matchOffset"));
        int limit = Math.max(1, Math.min(50, integer(request, "limit", 50)));
        String userQuery = searchKey(str(request, "userQuery"));
        String matchQuery = searchKey(str(request, "matchQuery"));
        List<Map<String, Object>> matchingMatches = new ArrayList<>();
        for (int i = server.store.records.size() - 1; i >= 0; i--) {
            Map<String, Object> match = server.store.records.get(i);
            String searchable = str(match, "matchId") + " " + str(match, "id") + " "
                    + displayName(recordPlayer(match, 0)) + " " + str(recordPlayer(match, 0), "username")
                    + " " + displayName(recordPlayer(match, 1)) + " " + str(recordPlayer(match, 1), "username");
            if (searchKey(searchable).contains(matchQuery)) matchingMatches.add(match);
        }
        List<Map<String, Object>> recent = matchingMatches.stream().skip(matchOffset).limit(25).toList();
        List<User> matchingUsers =
                server.store.users.values().stream()
                        .filter(u -> searchKey(u.id + " " + u.username + " " + u.displayName).contains(userQuery))
                        .toList();
        List<Map<String, Object>> users = matchingUsers.stream()
                        .skip(userOffset)
                        .limit(limit)
                        .map(User::view)
                        .toList();
        session.send(
                obj(
                        "type",
                        "ADMIN_DATA",
                        "users",
                        users,
                        "matches",
                        recent,
                        "userOffset",
                        userOffset,
                        "matchOffset",
                        matchOffset,
                        "hasMoreUsers",
                        userOffset + users.size() < matchingUsers.size(),
                        "hasMoreMatches",
                        matchOffset + recent.size() < matchingMatches.size(),
                        "userCount",
                        matchingUsers.size(),
                        "matchCount",
                        matchingMatches.size(),
                        "stats",
                        obj(
                                "users",
                                server.store.users.size(),
                                "players",
                                totalPlayers,
                                "online",
                                server.online.size(),
                                "queued",
                                server.queue.size(),
                                "activeMatches",
                                server.matches.size(),
                                "totalMatches",
                                server.store.records.size(),
                                "locked",
                                locked,
                                "storage",
                                server.store.mode,
                                "uptime",
                                (System.currentTimeMillis() - server.store.startedAt) / 1000)));
    }

    private static String searchKey(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    public boolean isAdmin(Session session) {
        if (!"ADMIN".equals(session.user.role)) {
            session.error("Chỉ quản trị viên được sử dụng chức năng này.");
            return false;
        }
        return true;
    }

    public void adminLock(Session session, Map<String, Object> request) throws Exception {
        if (!isAdmin(session)) return;
        User target = server.store.users.get(integer(request, "id"));
        if (target == null || "ADMIN".equals(target.role)) {
            session.send(
                    obj(
                            "type",
                            "ACTION",
                            "ok",
                            false,
                            "message",
                            "Không thể thay đổi tài khoản này."));
            return;
        }
        if (!(request.get("locked") instanceof Boolean)) {
            session.error("Trạng thái khóa không hợp lệ.");
            return;
        }
        boolean previous = target.locked;
        target.locked = bool(request, "locked");
        try {
            server.store.saveUser(target);
        } catch (Exception e) {
            target.locked = previous;
            throw e;
        }
        if (target.locked) {
            server.queue.remove(target.id);
            Session victim = server.online.get(target.id);
            if (victim != null) {
                victim.error("Tài khoản của bạn đã bị quản trị viên khóa.");
                server.detach(victim);
                victim.close();
            }
            Match match = server.activeByUser.get(target.id);
            if (match != null && !match.finished)
                match.finish("ACCOUNT_LOCKED", match.other(target.id).id);
        }
        session.send(
                obj(
                        "type",
                        "ACTION",
                        "ok",
                        true,
                        "message",
                        target.locked ? "Đã khóa tài khoản." : "Đã mở khóa tài khoản."));
    }
}

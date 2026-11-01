package fruit.server.features.matchmaking;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.list;
import static fruit.common.Protocol.map;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.Match;
import fruit.server.features.matches.Player;
import fruit.server.infrastructure.network.Session;

import java.text.Normalizer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** Matchmaking feature handlers; executed only on the server engine thread. */
public final class MatchmakingService {
    private static final long INVITE_LIFETIME_NANOS = TimeUnit.SECONDS.toNanos(25);
    private final GameServer server;
    private final Map<String, Invite> invites = new LinkedHashMap<>();

    public MatchmakingService(GameServer server) {
        this.server = server;
    }

    public void findMatch(Session session) {
        int id = session.user.id;
        if (server.activeByUser.containsKey(id)) {
            rejoin(session, false);
            return;
        }
        server.pendingResults.remove(id);
        server.queue.add(id);
        session.send(obj("type", "QUEUE", "queued", true));
        pairPlayers();
    }

    public void cancelQueue(Session session) {
        if (server.activeByUser.containsKey(session.user.id)) {
            session.error("Trận đấu đã được ghép.");
            return;
        }
        server.queue.remove(session.user.id);
        session.send(obj("type", "QUEUE", "queued", false));
    }

    public void pairPlayers() {
        server.queue.removeIf(
                id ->
                        !server.online.containsKey(id)
                                || server.online.get(id).closed.get()
                                || server.store.users.get(id).locked
                                || server.activeByUser.containsKey(id));
        while (server.queue.size() >= 2) {
            Iterator<Integer> iterator = server.queue.iterator();
            int a = iterator.next();
            iterator.remove();
            int b = iterator.next();
            iterator.remove();
            startMatch(server.store.users.get(a), server.store.users.get(b));
        }
    }

    /** Sends only public data for players currently connected to the game server. */
    public void onlinePlayers(Session session, String query) {
        String needle = normalized(query);
        List<Map<String, Object>> rows =
                server.online.entrySet().stream()
                        .filter(entry -> entry.getKey() != session.user.id)
                        .filter(entry -> entry.getValue() != null && !entry.getValue().closed.get())
                        .map(entry -> onlineView(entry.getKey()))
                        .filter(row -> matches(row, needle))
                        .sorted(
                                java.util.Comparator.comparing(
                                                (Map<String, Object> row) ->
                                                        !"SẴN SÀNG".equals(str(row, "status")))
                                        .thenComparing(row -> normalized(str(row, "displayName"))))
                        .limit(100)
                        .toList();
        session.send(obj("type", "ONLINE_PLAYERS", "rows", rows));
    }

    public void invitePlayer(Session session, int targetId) {
        int inviterId = session.user.id;
        if (targetId == inviterId) {
            session.error("Bạn không thể mời chính mình.");
            return;
        }
        if (!available(inviterId)) {
            session.error("Bạn cần rời hàng đợi hoặc kết thúc trận trước khi mời.");
            return;
        }
        if (!available(targetId)) {
            session.error("Người chơi này hiện không sẵn sàng nhận lời mời.");
            return;
        }
        if (hasInviteFor(inviterId) || hasInviteFor(targetId)) {
            session.error("Một trong hai người chơi đang có lời mời chờ xử lý.");
            return;
        }
        Invite invite =
                new Invite(
                        UUID.randomUUID().toString(),
                        inviterId,
                        targetId,
                        System.nanoTime() + INVITE_LIFETIME_NANOS);
        invites.put(invite.id, invite);
        Session target = server.online.get(targetId);
        target.send(
                obj(
                        "type", "INVITE_RECEIVED",
                        "inviteId", invite.id,
                        "from", publicUser(session.user),
                        "expiresIn", 25));
        session.send(
                obj(
                        "type",
                        "INVITE_STATUS",
                        "inviteId",
                        invite.id,
                        "state",
                        "SENT",
                        "message",
                        "Đã gửi lời mời."));
    }

    public void respondInvite(Session session, String inviteId, boolean accepted) {
        Invite invite = invites.get(inviteId);
        if (invite == null || invite.inviteeId != session.user.id) {
            session.error("Lời mời không còn hiệu lực.");
            return;
        }
        invites.remove(invite.id);
        Session inviter = server.online.get(invite.inviterId);
        if (System.nanoTime() >= invite.expiresAt) {
            notifyInvite(invite, "EXPIRED", "Lời mời đã hết hạn.");
            return;
        }
        if (!accepted) {
            if (inviter != null)
                inviter.send(inviteStatus(invite, "DECLINED", "Lời mời đã bị từ chối."));
            session.send(inviteStatus(invite, "DECLINED", "Bạn đã từ chối lời mời."));
            return;
        }
        if (!available(invite.inviterId) || !available(invite.inviteeId)) {
            if (inviter != null)
                inviter.send(
                        inviteStatus(
                                invite,
                                "UNAVAILABLE",
                                "Không thể bắt đầu trận: người chơi không còn sẵn sàng."));
            session.send(
                    inviteStatus(
                            invite,
                            "UNAVAILABLE",
                            "Không thể bắt đầu trận: người chơi không còn sẵn sàng."));
            return;
        }
        User inviterUser = server.store.users.get(invite.inviterId);
        startMatch(inviterUser, session.user);
    }

    public void cancelInvite(Session session, String inviteId) {
        Invite invite = invites.get(inviteId);
        if (invite == null || invite.inviterId != session.user.id) {
            session.error("Không tìm thấy lời mời để hủy.");
            return;
        }
        invites.remove(invite.id);
        Session target = server.online.get(invite.inviteeId);
        if (target != null) target.send(inviteStatus(invite, "CANCELLED", "Người mời đã hủy lời mời."));
        session.send(inviteStatus(invite, "CANCELLED", "Đã hủy lời mời."));
    }

    public void expireInvites(long now) {
        Iterator<Invite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            Invite invite = iterator.next();
            if (now < invite.expiresAt) continue;
            iterator.remove();
            notifyInvite(invite, "EXPIRED", "Lời mời đã hết hạn.");
        }
    }

    private void startMatch(User first, User second) {
        server.queue.remove(first.id);
        server.queue.remove(second.id);
        Match match = new Match(server, first, second);
        server.matches.add(match);
        server.activeByUser.put(first.id, match);
        server.activeByUser.put(second.id, match);
        for (Player player : match.players) {
            Session session = server.online.get(player.id);
            if (session != null)
                session.send(
                        obj(
                                "type",
                                "MATCH_FOUND",
                                "matchId",
                                match.id,
                                "opponent",
                                server.store.users.get(match.other(player.id).id).view(),
                                "countdown",
                                3));
        }
        match.broadcast(match.snapshot());
    }

    private boolean available(int id) {
        Session session = server.online.get(id);
        User user = server.store.users.get(id);
        return session != null
                && !session.closed.get()
                && user != null
                && !user.locked
                && !server.queue.contains(id)
                && !server.activeByUser.containsKey(id)
                && !hasInviteFor(id);
    }

    private boolean hasInviteFor(int id) {
        return invites.values().stream()
                .anyMatch(invite -> invite.inviterId == id || invite.inviteeId == id);
    }

    private Map<String, Object> onlineView(int id) {
        User user = server.store.users.get(id);
        String status =
                server.activeByUser.containsKey(id)
                        ? "ĐANG THI ĐẤU"
                        : server.queue.contains(id)
                                ? "ĐANG TÌM TRẬN"
                                : hasInviteFor(id) ? "ĐANG MỜI" : "SẴN SÀNG";
        Map<String, Object> row = publicUser(user);
        row.put("status", status);
        row.put("inviteable", available(id));
        return row;
    }

    private static Map<String, Object> publicUser(User user) {
        return new LinkedHashMap<>(
                obj(
                        "id", user.id,
                        "username", user.username,
                        "displayName", user.displayName,
                        "avatarId", user.avatarId,
                        "wins", user.wins,
                        "losses", user.losses,
                        "draws", user.draws));
    }

    private static boolean matches(Map<String, Object> row, String needle) {
        return needle.isEmpty()
                || normalized(str(row, "username")).contains(needle)
                || normalized(str(row, "displayName")).contains(needle);
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .trim();
    }

    private static Map<String, Object> inviteStatus(Invite invite, String state, String message) {
        return obj("type", "INVITE_STATUS", "inviteId", invite.id, "state", state, "message", message);
    }

    private void notifyInvite(Invite invite, String state, String message) {
        for (int id : new int[] {invite.inviterId, invite.inviteeId}) {
            Session session = server.online.get(id);
            if (session != null) session.send(inviteStatus(invite, state, message));
        }
    }

    public void rejoin(Session session, boolean explicit) {
        int id = session.user.id;
        Match match = server.activeByUser.get(id);
        if (match != null && !match.finished) {
            session.send(
                    obj(
                            "type",
                            "MATCH_FOUND",
                            "matchId",
                            match.id,
                            "opponent",
                            server.store.users.get(match.other(id).id).view(),
                            "countdown",
                            match.countdown,
                            "rejoin",
                            true));
            if (match.countdown <= 0)
                session.send(obj("type", "MATCH_START", "matchId", match.id, "rejoin", true));
            session.send(match.snapshot());
            return;
        }
        Map<String, Object> result = server.pendingResults.remove(id);
        if (result != null) session.send(result);
        else if (explicit) {
            List<Map<String, Object>> records = server.store.records;
            for (int i = records.size() - 1; i >= 0; i--) {
                Map<String, Object> record = records.get(i);
                if (list(record.get("players")).stream()
                        .anyMatch(p -> integer(map(p), "id") == id)) {
                    Map<String, Object> last = new LinkedHashMap<>(record);
                    last.put("type", "MATCH_RESULT");
                    last.put("saved", !server.unsavedMatches.containsKey(str(record, "matchId")));
                    session.send(last);
                    return;
                }
            }
            session.error("Không có trận đấu để khôi phục.");
        }
    }

    public void detach(Session session) {
        User user = session.user;
        if (user == null) return;
        Iterator<Invite> invitesIterator = invites.values().iterator();
        while (invitesIterator.hasNext()) {
            Invite invite = invitesIterator.next();
            if (invite.inviterId != user.id && invite.inviteeId != user.id) continue;
            invitesIterator.remove();
            notifyInvite(invite, "CANCELLED", "Lời mời đã được hủy vì người chơi mất kết nối.");
        }
        if (server.online.get(user.id) == session) server.online.remove(user.id);
        session.user = null;
        server.queue.remove(user.id);
        Match match = server.activeByUser.get(user.id);
        if (match == null || match.finished) return;
        match.player(user.id).direction = 0;
        if (match.countdown > 0) {
            match.finished = true;
            server.matches.remove(match);
            for (Player player : match.players) server.activeByUser.remove(player.id);
            Player opponent = match.other(user.id);
            Session other = server.online.get(opponent.id);
            if (other != null && !other.closed.get()) {
                server.queue.add(opponent.id);
                other.send(
                        obj(
                                "type",
                                "QUEUE",
                                "queued",
                                true,
                                "message",
                                "Đối thủ mất kết nối. Đang tìm trận mới."));
            }
            pairPlayers();
        }
    }

    private record Invite(String id, int inviterId, int inviteeId, long expiresAt) {}
}

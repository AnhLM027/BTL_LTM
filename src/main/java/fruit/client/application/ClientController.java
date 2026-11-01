package fruit.client.application;

import static fruit.common.MessageData.asMap;
import static fruit.common.MessageData.maps;
import static fruit.common.Protocol.bool;
import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.number;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import fruit.client.features.accounts.AccountActions;
import fruit.client.features.admin.AdminActions;
import fruit.client.features.gameplay.CatchEffect;
import fruit.client.features.gameplay.ClientFeedback;
import fruit.client.features.gameplay.GameplayActions;
import fruit.client.features.matchmaking.MatchmakingActions;
import fruit.client.infrastructure.network.ClientConnection;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Authentication, message handling, navigation decisions and player actions. No JavaFX controls.
 */
public final class ClientController implements AutoCloseable {
    public final ClientState model = new ClientState();
    public final ClientFeedback feedback = new ClientFeedback(model);
    private final ClientConnection connection;
    private final AccountActions accounts = new AccountActions(this);
    private final MatchmakingActions matchmaking = new MatchmakingActions(this);
    private final GameplayActions gameplay = new GameplayActions(this);
    private final AdminActions admin = new AdminActions(this);
    private ClientView view;

    public ClientController(Consumer<Runnable> dispatcher) {
        connection =
                new ClientConnection(
                        model,
                        dispatcher,
                        () -> view.updateConnection(),
                        this::receive,
                        this::connectionLost);
    }

    public ClientView view() {
        return view;
    }

    public ClientConnection connection() {
        return connection;
    }

    public void setView(ClientView view) {
        this.view = Objects.requireNonNull(view);
    }

    public void close() {
        connection.close();
        feedback.audio.close();
    }

    public void loadAdmin() {
        admin.loadAdmin();
    }

    public void authenticate(
            String userText, String passText, String hostText, String portText, String display) {
        accounts.authenticate(userText, passText, hostText, portText, display);
    }

    public void connectionLost(Exception error) {

        model.connecting = false;
        model.connected = false;
        model.authPending = false;
        model.queued = false;
        stopMoving();
        view.updateConnection();
        String suggestion =
                model.user.isEmpty()
                        ? ". Kiểm tra địa chỉ và máy chủ."
                        : ". Bấm Kết nối lại để khôi phục.";
        view.toast("Mất kết nối: " + friendlyError(error) + suggestion, true);
        view.queueDisconnected();
    }

    public void send(Map<String, Object> request) {
        if (model.snapshotMode) return;
        if (!model.connected) {
            view.toast("Chưa kết nối máy chủ. Bấm Kết nối lại hoặc đăng nhập.", true);
            return;
        }
        connection.send(request);
    }

    public void reconnect() {
        if (model.connecting) return;
        if (model.username.isBlank() || model.password.isBlank()) {
            view.showScreen("login");
            return;
        }
        model.authPending = true;
        connection.connect(
                obj("type", "LOGIN", "username", model.username, "password", model.password));
        view.toast("Đang kết nối lại và khôi phục phiên chơi…", false);
    }

    public void logout() {
        accounts.logout();
    }

    public void receive(Map<String, Object> event) {
        String type = str(event, "type");
        switch (type) {
            case "AUTH" -> {
                model.authPending = false;
                if (!bool(event, "ok")) {
                    // Kết nối TCP có thể thành công nhưng tài khoản đã bị khóa.
                    // Đưa phiên khôi phục thất bại về form để người chơi đăng nhập lại.
                    connection.disconnect();
                    model.connected = false;
                    model.connecting = false;
                    model.queued = false;
                    stopMoving();
                    if (!model.screen.equals("login")) {
                        model.user = obj();
                        model.state = obj();
                        model.result = obj();
                        model.registering = false;
                        view.showScreen("login");
                    }
                    view.updateConnection();
                    feedback.audio.play("deny", .7);
                    view.toast(str(event, "message", "Đăng nhập thất bại."), true);
                    return;
                }
                model.user = asMap(event.get("user"));
                feedback.audio.play("confirm", .7);
                view.showScreen("home");
                view.toast(str(event, "message", "Đăng nhập thành công."), false);
            }
            case "CATCH_EVENT" -> feedback.catchFeedback(event);
            case "TARGET_CHANGE" -> {
                feedback.targetPulseStart = System.nanoTime();
                feedback.audio.play("target", .7);
            }
            case "FRUIT_SPAWN", "SCORE_UPDATE" -> {
                /* positions and totals arrive in GAME_STATE */
            }
            case "PROFILE" -> receiveProfile(event);
            case "LEADERBOARD" -> {
                model.ranking = maps(event.get("rows"));
                if (model.screen.equals("leaderboard")) view.showScreen(model.screen);
            }
            case "HISTORY" -> {
                model.history = maps(event.get("rows"));
                if (model.screen.equals("history")) view.showScreen(model.screen);
            }
            case "ADMIN_DATA" -> {
                model.adminUsers = maps(event.get("users"));
                model.adminMatches = maps(event.get("matches"));
                model.adminStats = asMap(event.get("stats"));
                model.adminUserOffset = integer(event, "userOffset");
                model.adminMatchOffset = integer(event, "matchOffset");
                model.adminUserCount = integer(event, "userCount", model.adminUsers.size());
                model.adminMatchCount = integer(event, "matchCount", model.adminMatches.size());
                model.moreAdminUsers = bool(event, "hasMoreUsers");
                model.moreAdminMatches = bool(event, "hasMoreMatches");
                if (model.screen.equals("admin")) view.showScreen(model.screen);
            }
            case "ACTION" -> {
                view.toast(str(event, "message"), !bool(event, "ok"));
                if (bool(event, "ok") && model.screen.equals("admin")) loadAdmin();
            }
            case "QUEUE" -> receiveQueue(event);
            case "MATCH_FOUND" -> {
                feedback.restoringMatch = bool(event, "rejoin");
                String found = String.valueOf(event.get("matchId"));
                if (!found.equals(feedback.presentationMatch)) {
                    feedback.notices.clear();
                    feedback.effects.clear();
                    feedback.caughtEvents.clear();
                    feedback.previousState = obj();
                    feedback.presentationMatch = found;
                    feedback.lastCountdown = -1;
                    feedback.lastSecond = 61;
                }
                if (!bool(event, "rejoin")) feedback.audio.play("match-found", .8);
                model.queued = false;
                model.state =
                        obj(
                                "matchId",
                                event.get("matchId"),
                                "phase",
                                "COUNTDOWN",
                                "countdown",
                                3,
                                "remaining",
                                60,
                                "target",
                                "APPLE",
                                "stage",
                                1,
                                "players",
                                List.of(model.user, asMap(event.get("opponent"))));
                view.showScreen("game");
                view.toast("Đã tìm thấy đối thủ. Chuẩn bị!", false);
            }
            case "MATCH_START" -> {
                if (!bool(event, "rejoin")) feedback.audio.play("go", .8);
                view.toast("Bắt đầu! Hãy hứng đúng quả mục tiêu.", false);
            }
            case "GAME_STATE" -> {
                feedback.receiveState(event);
                model.queued = false;
                if (!model.screen.equals("game")) view.showScreen("game");
            }
            case "MATCH_RESULT" -> {
                stopMoving();
                model.queued = false;
                model.result = event;
                String resultId = String.valueOf(event.get("matchId"));
                if (!resultId.equals(feedback.resultSoundMatch)) {
                    feedback.resultSoundMatch = resultId;
                    int winner = integer(event, "winnerId");
                    feedback.audio.play(
                            winner == 0
                                    ? "draw"
                                    : winner == integer(model.user, "id") ? "win" : "lose",
                            .9);
                }
                model.state = obj();
                view.showScreen("result");
                send(obj("type", "PROFILE_GET"));
            }
            case "SAVE_STATUS" -> {
                if (Objects.equals(event.get("matchId"), model.result.get("matchId"))) {
                    model.result.put("saved", bool(event, "saved"));
                    view.resultSaved(bool(event, "saved"));
                }
            }
            case "SKILL_EFFECT" -> {
                int skill = integer(event, "skill");
                int victim = integer(event, "targetId");
                String sound = skill == 1 ? "bomb-warning" : skill == 2 ? "freeze" : "magnet";
                feedback.audio.play(sound, victim == integer(model.user, "id") ? 1 : .55);
                feedback.addEffect(
                        new CatchEffect(victim, number(event, "x"), 80, "CAST" + skill, 0));
                feedback.effect = str(event, "message", "Kỹ năng đã kích hoạt!");
                feedback.effectOnMe = integer(event, "targetId") == integer(model.user, "id");
                feedback.flashUntil = System.nanoTime() + 1_600_000_000L;
            }
            case "ERROR" -> {
                feedback.audio.play("deny", .7);
                model.authPending = false;
                model.pendingPassword = "";
                view.toast(str(event, "message", "Máy chủ từ chối thao tác."), true);
            }
            case "LOGGED_OUT" -> {
                if (!model.screen.equals("login")) logout();
            }
            default -> {} // Vật thể, mục tiêu và điểm đã có trong GAME_STATE.
        }
    }

    public void receiveProfile(Map<String, Object> event) {
        accounts.receiveProfile(event);
    }

    public void receiveQueue(Map<String, Object> event) {
        matchmaking.receiveQueue(event);
    }

    public void findMatch() {
        matchmaking.findMatch();
    }

    public void move(int next) {
        gameplay.move(next);
    }

    public void stopMoving() {
        gameplay.stopMoving();
    }

    public void useSkill(int skill) {
        gameplay.useSkill(skill);
    }

    public static String friendlyError(Exception ex) {
        if (ex instanceof ConnectException) return "Không tìm thấy máy chủ";
        if (ex instanceof SocketTimeoutException) return "Hết thời gian chờ";
        if (ex instanceof UnknownHostException) return "Địa chỉ máy chủ không hợp lệ";
        return ex.getMessage() == null ? "Kết nối bị gián đoạn" : ex.getMessage();
    }
}

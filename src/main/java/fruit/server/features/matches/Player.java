package fruit.server.features.matches;

import static fruit.common.Protocol.obj;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Player {
    private final GameServer server;

    public final int id;
    public final String username;
    public double x = 400, cooldown, freeze, magnet, activeSkill, incomingSkill, bombWarning, bombX;
    public int direction, score, combo, energy, correct, wrong, bombs, maxCombo, skills;
    public final List<Drop> objects = new ArrayList<>();

    public Player(GameServer server, User user) {
        this.server = server;

        id = user.id;
        username = user.username;
    }

    public Map<String, Object> stats() {
        User user = server.store.users.get(id);
        return obj(
                "id",
                id,
                "username",
                username,
                "displayName",
                user == null ? username : user.displayName,
                "avatarId",
                user == null ? 0 : user.avatarId,
                "score",
                score,
                "correct",
                correct,
                "wrong",
                wrong,
                "bombs",
                bombs,
                "maxCombo",
                maxCombo,
                "skills",
                skills);
    }

    public Map<String, Object> view() {
        Map<String, Object> view = stats();
        view.putAll(
                obj(
                        "x",
                        x,
                        "combo",
                        combo,
                        "energy",
                        energy,
                        "cooldown",
                        cooldown,
                        "freeze",
                        freeze,
                        "magnet",
                        magnet,
                        "basketWidth",
                        basketWidth(),
                        "connected",
                        server.online.containsKey(id) && !server.online.get(id).closed.get(),
                        "objects",
                        objects.stream().map(Drop::view).toList(),
                        "bombWarning",
                        Math.max(0, bombWarning),
                        "bombX",
                        bombX));
        return view;
    }

    public double basketWidth() {
        return GameServer.BASKET_WIDTH * (magnet > 0 ? 1.4 : 1);
    }
}

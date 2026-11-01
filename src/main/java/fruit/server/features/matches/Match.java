package fruit.server.features.matches;

import static fruit.common.Protocol.obj;

import fruit.server.application.GameServer;
import fruit.server.features.accounts.User;
import fruit.server.infrastructure.network.Session;

import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Match {
    private final GameServer server;

    public final String id = UUID.randomUUID().toString();
    public final String createdAt = Instant.now().toString();
    public final Player[] players;
    public final Random random = new Random();
    public double countdown = 3, elapsed, nextSpawn, nextTarget = 15;
    public int stage = 1;
    public String target = GameServer.FRUITS[random.nextInt(GameServer.FRUITS.length)];
    public long nextObject;
    public boolean finished;

    public Match(GameServer server, User first, User second) {
        this.server = server;

        players = new Player[] {new Player(server, first), new Player(server, second)};
    }

    public Player player(int id) {
        return players[0].id == id ? players[0] : players[1];
    }

    public Player other(int id) {
        return players[0].id == id ? players[1] : players[0];
    }

    public void broadcast(Map<String, Object> message) {
        for (Player player : players) {
            Session session = server.online.get(player.id);
            if (session != null) session.send(message);
        }
    }

    public void step(double dt) {
        // Catch up in small, fixed-size pieces after a delayed tick. The full
        // elapsed time is retained, so a match still lasts 60 real seconds.
        while (dt > 0.000000001 && !finished) {
            double part = Math.min(0.05, dt);
            advance(part);
            dt -= part;
        }
    }

    public void advance(double dt) {
        if (finished || dt <= 0) return;
        if (countdown > 0) {
            countdown -= dt;
            if (countdown > 0) return;
            dt = -countdown;
            countdown = 0;
            broadcast(obj("type", "MATCH_START", "matchId", id));
        }
        double used = Math.min(dt, 60 - elapsed);
        elapsed = Math.min(60, elapsed + used);
        if (Math.abs(elapsed - Math.rint(elapsed)) < 0.000000001) elapsed = Math.rint(elapsed);
        stage = Math.min(3, 1 + (int) (elapsed / 20));
        while (nextTarget < 60 && elapsed >= nextTarget) {
            String previous = target;
            do {
                target = GameServer.FRUITS[random.nextInt(GameServer.FRUITS.length)];
            } while (target.equals(previous));
            nextTarget += 15;
            broadcast(obj("type", "TARGET_CHANGE", "matchId", id, "target", target));
        }
        for (Player player : players) {
            double frozenTime = Math.min(player.freeze, used);
            player.cooldown = Math.max(0, player.cooldown - used);
            player.activeSkill = Math.max(0, player.activeSkill - used);
            player.freeze = Math.max(0, player.freeze - used);
            player.magnet = Math.max(0, player.magnet - used);
            player.incomingSkill = Math.max(0, player.incomingSkill - used);
            double distance = GameServer.BASKET_SPEED * (used - 0.4 * frozenTime);
            double halfWidth = player.basketWidth() / 2;
            player.x =
                    Math.max(
                            halfWidth,
                            Math.min(800 - halfWidth, player.x + player.direction * distance));
            if (player.bombWarning > 0) {
                player.bombWarning -= used;
                if (player.bombWarning <= 0)
                    addDrop(player, ++nextObject, "BOMB", player.bombX, baseSpeed());
            }
        }
        // One base spawn schedule is copied to both boards, including its x and kind.
        while (nextSpawn <= elapsed && nextSpawn < 60) {
            spawnBase();
            int spawnStage = Math.min(3, 1 + (int) (nextSpawn / 20));
            nextSpawn += spawnStage == 1 ? 1.2 : spawnStage == 2 ? 0.95 : 0.7;
        }
        for (Player player : players) {
            Iterator<Drop> iterator = player.objects.iterator();
            while (iterator.hasNext()) {
                Drop drop = iterator.next();
                double previousY = drop.y;
                drop.y += drop.speed * used;
                // Swept vertical collision prevents a fast fruit skipping over the basket.
                boolean crossesBasket =
                        previousY <= GameServer.BASKET_Y + 15 + GameServer.OBJECT_RADIUS
                                && drop.y >= GameServer.BASKET_Y - 15 - GameServer.OBJECT_RADIUS;
                if (crossesBasket
                        && Math.abs(drop.x - player.x)
                                <= player.basketWidth() / 2 + GameServer.OBJECT_RADIUS) {
                    catchDrop(player, drop);
                    iterator.remove();
                } else if (drop.y > 600 + GameServer.OBJECT_RADIUS) iterator.remove();
            }
        }
        if (elapsed >= 60) finish("TIME_UP", 0);
    }

    public double baseSpeed() {
        return stage == 1 ? 180 : stage == 2 ? 225 : 275;
    }

    public void spawnBase() {
        double chance = random.nextDouble();
        String kind;
        if (chance < 0.54) kind = target;
        else if (chance < 0.88) {
            do {
                kind = GameServer.FRUITS[random.nextInt(GameServer.FRUITS.length)];
            } while (kind.equals(target));
        } else kind = "BOMB";
        double x = 48 + random.nextInt(705);
        long objectId = ++nextObject;
        for (Player player : players) addDrop(player, objectId, kind, x, baseSpeed());
    }

    public void addDrop(Player player, long objectId, String kind, double x, double speed) {
        Drop drop = new Drop(objectId, kind, x, -GameServer.OBJECT_RADIUS, speed);
        player.objects.add(drop);
        Session session = server.online.get(player.id);
        if (session != null)
            session.send(
                    obj(
                            "type",
                            "FRUIT_SPAWN",
                            "matchId",
                            id,
                            "playerId",
                            player.id,
                            "object",
                            drop.view()));
    }

    public void catchDrop(Player player, Drop drop) {
        int delta;
        if (drop.kind.equals("BOMB")) {
            player.bombs++;
            player.combo = 0;
            delta = -10;
        } else if (drop.kind.equals(target)) {
            player.correct++;
            player.combo++;
            player.maxCombo = Math.max(player.maxCombo, player.combo);
            player.energy = Math.min(100, player.energy + 10);
            delta = 10 + (player.combo % 5 == 0 ? 20 : 0);
        } else {
            player.wrong++;
            player.combo = 0;
            delta = -5;
        }
        player.score += delta;
        broadcast(
                obj(
                        "type",
                        "CATCH_EVENT",
                        "matchId",
                        id,
                        "playerId",
                        player.id,
                        "objectId",
                        drop.id,
                        "kind",
                        drop.kind,
                        "delta",
                        delta,
                        "x",
                        drop.x,
                        "y",
                        GameServer.BASKET_Y,
                        "combo",
                        player.combo));
        broadcast(
                obj(
                        "type",
                        "SCORE_UPDATE",
                        "matchId",
                        id,
                        "playerId",
                        player.id,
                        "score",
                        player.score,
                        "combo",
                        player.combo,
                        "energy",
                        player.energy));
    }

    public void skill(Player attacker, int skill) {
        if (finished || countdown > 0 || elapsed >= 60) {
            skillError(attacker, "Kỹ năng chỉ dùng được khi trận đang diễn ra.");
            return;
        }
        if (skill < 1 || skill > 3) {
            skillError(attacker, "Kỹ năng không hợp lệ.");
            return;
        }
        int cost = skill == 1 ? 30 : skill == 2 ? 50 : 70;
        Player victim = skill == 3 ? attacker : other(attacker.id);
        if (attacker.energy < cost) {
            skillError(attacker, "Không đủ năng lượng.");
            return;
        }
        if (attacker.cooldown > 0 || attacker.activeSkill > 0) {
            skillError(attacker, "Kỹ năng đang hồi chiêu.");
            return;
        }
        if (skill != 3 && victim.incomingSkill > 0) {
            skillError(attacker, "Đối thủ đang chịu một hiệu ứng khác.");
            return;
        }
        double duration = skill == 1 ? 0.65 : skill == 2 ? 3 : 5;
        attacker.energy -= cost;
        attacker.skills++;
        attacker.cooldown = 6;
        attacker.activeSkill = duration;
        if (skill != 3) victim.incomingSkill = duration;
        if (skill == 1) {
            victim.bombWarning = duration;
            victim.bombX = victim.x;
        }
        if (skill == 2) victim.freeze = duration;
        if (skill == 3) {
            attacker.magnet = duration;
            double halfWidth = attacker.basketWidth() / 2;
            attacker.x = Math.max(halfWidth, Math.min(800 - halfWidth, attacker.x));
        }
        broadcast(
                obj(
                        "type",
                        "SKILL_EFFECT",
                        "matchId",
                        id,
                        "attackerId",
                        attacker.id,
                        "targetId",
                        victim.id,
                        "skill",
                        skill,
                        "duration",
                        duration,
                        "x",
                        victim.x,
                        "message",
                        skill == 1
                                ? "CẢNH BÁO BOM!"
                                : skill == 2
                                        ? "Giỏ bị làm chậm 40%!"
                                        : "Giỏ nam châm: vùng hứng rộng hơn 40% trong 5 giây!"));
    }

    public void skillError(Player player, String message) {
        Session session = server.online.get(player.id);
        if (session != null) session.error(message);
    }

    public Map<String, Object> snapshot() {
        return obj(
                "type",
                "GAME_STATE",
                "matchId",
                id,
                "phase",
                countdown > 0 ? "COUNTDOWN" : "PLAYING",
                "remaining",
                Math.max(0, 60 - elapsed),
                "countdown",
                Math.max(0, countdown),
                "target",
                target,
                "stage",
                stage,
                "players",
                Arrays.stream(players).map(Player::view).toList());
    }

    public void finish(String reason, int forcedWinner) {
        if (finished) return;
        finished = true;
        server.matches.remove(this);
        for (Player player : players) {
            server.activeByUser.remove(player.id);
            player.direction = 0;
        }
        int winner =
                forcedWinner != 0
                        ? forcedWinner
                        : players[0].score == players[1].score
                                ? 0
                                : players[0].score > players[1].score
                                        ? players[0].id
                                        : players[1].id;
        Map<String, Object> record =
                obj(
                        "matchId",
                        id,
                        "createdAt",
                        createdAt,
                        "finishedAt",
                        Instant.now().toString(),
                        "durationMs",
                        (int) Math.round(elapsed * 1000),
                        "winnerId",
                        winner,
                        "reason",
                        reason,
                        "players",
                        Arrays.stream(players).map(Player::stats).toList());
        for (Player player : players) {
            User user = server.store.users.get(player.id);
            if (winner == 0) user.draws++;
            else if (winner == player.id) user.wins++;
            else user.losses++;
            user.totalScore += player.score;
        }
        server.store.records.add(record);
        boolean saved;
        try {
            server.store.saveMatch(
                    record,
                    server.store.users.get(players[0].id),
                    server.store.users.get(players[1].id));
            saved = true;
        } catch (Exception e) {
            saved = false;
            server.unsavedMatches.put(id, record);
            server.retrySaveAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            server.display.warning("Could not save match " + id + ": " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>(record);
        result.put("type", "MATCH_RESULT");
        result.put("saved", saved);
        for (Player player : players) {
            Session session = server.online.get(player.id);
            if (session != null && !session.closed.get()) session.send(result);
            else server.pendingResults.put(player.id, result);
        }
    }
}

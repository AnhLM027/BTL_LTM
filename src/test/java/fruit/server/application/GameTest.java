package fruit.server.application;

import fruit.common.Protocol;
import fruit.server.features.accounts.User;
import fruit.server.features.matches.Drop;
import fruit.server.features.matches.Match;
import fruit.server.features.matches.Player;
import fruit.server.infrastructure.persistence.DatabaseTest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Run with assertions or without them: every check below is always enforced. */
public final class GameTest {
    private static int checks;
    private static final String PASSWORD = "FruitTest!2026";

    public static void main(String[] args) throws Exception {
        long began = System.nanoTime();
        protocol();
        System.setProperty("fruit.admin.password", PASSWORD);
        rules();
        invitations();
        lifecycle();
        boolean mysql = Arrays.asList(args).contains("--mysql");
        persistence(mysql);
        if (mysql) checks += DatabaseTest.run();
        System.out.printf(
                "PASS: %d checks in %.1f seconds%n",
                checks, (System.nanoTime() - began) / 1_000_000_000.0);
    }

    private static void rules() throws Exception {
        Path data = Files.createTempDirectory("fruit-test-rules-");
        try (GameServer server = new GameServer(0, data, true)) {
            int aId = 2, bId = 3;
            server.store.users.put(aId, new User(aId, "rules_alpha", "Rules Alpha"));
            server.store.users.put(bId, new User(bId, "rules_beta", "Rules Beta"));
            Match match =
                    new Match(server, server.store.users.get(aId), server.store.users.get(bId));
            match.countdown = 0;
            Player player = match.players[0];
            Player opponent = match.players[1];
            check(player.energy == 0 && opponent.energy == 0, "Match begins with zero energy");
            for (int i = 0; i < 5; i++)
                match.catchDrop(player, new Drop(i, match.target, 400, 550, 200));
            check(
                    player.score == 70 && player.combo == 5,
                    "Five correct catches award 50 plus 20 combo points");
            check(
                    player.energy == 50 && player.correct == 5 && player.maxCombo == 5,
                    "Correct catches update energy and statistics");
            player.objects.add(new Drop(100, match.target, 30, 650, 200));
            match.step(.01);
            check(player.combo == 5 && player.score == 70, "Missed fruit keeps combo and score");
            String wrongKind = match.target.equals("APPLE") ? "BANANA" : "APPLE";
            match.catchDrop(player, new Drop(101, wrongKind, 400, 550, 200));
            check(
                    player.score == 65
                            && player.combo == 0
                            && player.wrong == 1
                            && player.energy == 50,
                    "Wrong fruit deducts five, clears combo, adds no energy");
            match.catchDrop(player, new Drop(102, "BOMB", 400, 550, 200));
            check(
                    player.score == 55
                            && player.combo == 0
                            && player.bombs == 1
                            && player.energy == 50,
                    "Bomb deducts ten, clears combo, adds no energy");
            for (int i = 0; i < 10; i++)
                match.catchDrop(player, new Drop(200 + i, match.target, 400, 550, 200));
            check(
                    player.score == 195 && player.combo == 10 && player.maxCombo == 10,
                    "Ten consecutive catches grant two combo bonuses");
            check(player.energy == 100, "Energy is capped at 100");
            String oldTarget = match.target;
            match.target = oldTarget.equals("APPLE") ? "BANANA" : "APPLE";
            match.catchDrop(player, new Drop(300, oldTarget, 400, 550, 200));
            check(
                    player.wrong == 2 && player.combo == 0,
                    "Catch is judged using current target, not spawn target");
            player.score = 0;
            match.catchDrop(player, new Drop(301, "BOMB", 400, 550, 200));
            check(player.score == -10, "Negative scores are permitted");
            skills(match, player, opponent);
            schedule(server, aId, bId);
        }
    }

    private static void skills(Match match, Player player, Player opponent) {
        player.objects.clear();
        opponent.objects.clear();
        match.nextSpawn = 100;
        player.energy = 29;
        int skillsBefore = player.skills;
        match.skill(player, 1);
        check(
                player.energy == 29 && player.skills == skillsBefore,
                "Insufficient energy rejects skill");
        player.energy = 100;
        player.cooldown = 0;
        player.activeSkill = 0;
        match.skill(player, 1);
        check(player.energy == 70 && player.skills == skillsBefore + 1, "Bomb costs 30 energy");
        near(opponent.bombWarning, .65, .001, "Bomb warns the opponent before it appears");
        check(
                opponent.objects.stream().noneMatch(drop -> drop.kind.equals("BOMB")),
                "Bomb is delayed until warning expires");
        near(player.cooldown, 5, .001, "All skills use five second shared cooldown");
        int afterBomb = player.skills;
        match.skill(player, 2);
        check(
                player.energy == 70 && player.skills == afterBomb,
                "Shared cooldown rejects a different skill");
        advance(match, .7);
        check(
                opponent.objects.stream().anyMatch(drop -> drop.kind.equals("BOMB")),
                "Bomb appears after warning");
        check(
                player.objects.stream().noneMatch(drop -> drop.kind.equals("BOMB")),
                "Attack bomb only appears on victim's field");
        player.cooldown = 0;
        player.activeSkill = 0;
        player.energy = 100;
        opponent.freeze = 0;
        match.skill(player, 2);
        check(player.energy == 50, "Freeze costs 50 energy");
        near(opponent.freeze, 3, .001, "Freeze lasts three seconds");
        player.x = opponent.x = 400;
        player.direction = opponent.direction = 1;
        player.objects.clear();
        opponent.objects.clear();
        match.step(.1);
        double normalMovement = player.x - 400;
        double frozenMovement = opponent.x - 400;
        check(normalMovement > 0, "Direction advances basket on server");
        near(
                frozenMovement / normalMovement,
                .6,
                .001,
                "Freeze reduces movement speed by 40 percent");
        player.cooldown = 0;
        player.energy = 100;
        match.skill(player, 1);
        check(player.energy == 100, "Cannot start a new skill while previous skill remains active");
        advance(match, 3.1);
        check(opponent.freeze <= 0, "Freeze effect expires");
        player.cooldown = player.activeSkill = 0;
        player.energy = 100;
        player.direction = opponent.direction = 0;
        player.x = opponent.x = 400;
        player.objects.clear();
        opponent.objects.clear();
        match.nextSpawn = 100;
        player.energy = 89;
        match.skill(player, 3);
        check(player.energy == 89 && player.magnet == 0, "Magnet rejects insufficient energy");
        player.energy = 100;
        opponent.incomingSkill = 2;
        player.freeze = player.incomingSkill = 2;
        match.skill(player, 3);
        check(
                player.energy == 10,
                "Magnet costs 90 energy even when opponent has an incoming effect");
        near(player.magnet, 5, .001, "Magnet lasts five seconds on the caster");
        near(player.basketWidth(), 100, .001, "Magnet keeps the basket at its normal width");
        near(opponent.basketWidth(), 100, .001, "Magnet leaves the opponent basket unchanged");
        check(
                player.view().containsKey("magnet") && player.view().containsKey("basketWidth"),
                "Snapshot exposes magnet duration and normal collision width for rendering");
        match.spawnBase();
        near(
                player.objects.get(0).speed,
                opponent.objects.get(0).speed,
                .001,
                "Magnet never changes falling speed on either field");
        player.objects.clear();
        opponent.objects.clear();
        String wrong = match.target.equals("APPLE") ? "BANANA" : "APPLE";
        Drop targetNear = new Drop(600, match.target, 490, 480, 0);
        Drop wrongNear = new Drop(601, wrong, 490, 480, 0);
        Drop bombNear = new Drop(602, "BOMB", 490, 480, 0);
        Drop targetFar = new Drop(603, match.target, 560, 480, 0);
        player.objects.add(targetNear);
        player.objects.add(wrongNear);
        player.objects.add(bombNear);
        player.objects.add(targetFar);
        match.step(.1);
        check(
                targetNear.x < 490 && targetNear.x > player.x,
                "Magnet gradually pulls a nearby target fruit toward the basket");
        near(wrongNear.x, 490, .001, "Magnet never pulls a nearby wrong fruit");
        near(bombNear.x, 490, .001, "Magnet never pulls a nearby bomb");
        near(targetFar.x, 560, .001, "Magnet does not pull a target outside its local range");
        player.objects.clear();
        player.energy = 100;
        double beforeRefresh = player.magnet;
        match.skill(player, 3);
        check(
                player.energy == 100 && player.magnet == beforeRefresh,
                "Shared cooldown prevents magnet refresh or stacking");
        player.x = 50;
        match.step(.01);
        near(player.x, 50, .001, "Normal-width magnet basket stays within the field edge");
        advance(match, 5.1);
        check(
                player.magnet == 0 && player.basketWidth() == 100,
                "Magnet expiry leaves the normal basket width intact");
        player.x = 400;
        player.objects.add(new Drop(612, match.target, 490, 480, 0));
        match.step(.01);
        check(
                player.objects.get(0).x == 490,
                "Target fruit is no longer pulled after magnet expires");
    }

    private static void schedule(GameServer server, int firstId, int secondId) {
        Match match =
                new Match(
                        server, server.store.users.get(firstId), server.store.users.get(secondId));
        match.step(1);
        check(
                match.elapsed == 0 && match.players[0].objects.isEmpty(),
                "Countdown prevents movement of game clock and spawning");
        match.countdown = 0;
        match.spawnBase();
        Drop firstDrop = match.players[0].objects.get(0);
        Drop secondDrop = match.players[1].objects.get(0);
        check(
                firstDrop.id == secondDrop.id && firstDrop.kind.equals(secondDrop.kind),
                "Both fields receive identical base object identity and kind");
        near(firstDrop.x, secondDrop.x, .001, "Both fields receive identical base spawn position");
        near(
                firstDrop.speed,
                secondDrop.speed,
                .001,
                "Both fields receive identical base falling speed");
        String target = match.target;
        advance(match, 14.9);
        check(match.target.equals(target), "Target remains fixed for the first fifteen seconds");
        advance(match, .2);
        check(!match.target.equals(target), "Target changes at fifteen seconds without repeating");
        advance(match, 4.8);
        check(match.stage == 1, "First difficulty lasts twenty seconds");
        advance(match, .2);
        check(match.stage == 2, "Second difficulty starts after twenty seconds");
        target = match.target;
        advance(match, 10);
        check(!match.target.equals(target), "Target changes at thirty seconds without repeating");
        advance(match, 9.8);
        check(match.stage == 2, "Second difficulty lasts until forty seconds");
        advance(match, .2);
        check(match.stage == 3, "Third difficulty starts after forty seconds");
        target = match.target;
        advance(match, 5);
        check(
                !match.target.equals(target),
                "Target changes at forty-five seconds without repeating");
        Match density =
                new Match(
                        server, server.store.users.get(firstId), server.store.users.get(secondId));
        density.countdown = 0;
        density.step(.01);
        double slowInterval = density.nextSpawn;
        double slowSpeed = density.baseSpeed();
        density.elapsed = density.nextSpawn = 20;
        density.step(.01);
        double mediumInterval = density.nextSpawn - 20;
        double mediumSpeed = density.baseSpeed();
        density.elapsed = density.nextSpawn = 40;
        density.step(.01);
        double fastInterval = density.nextSpawn - 40;
        check(
                slowInterval > mediumInterval && mediumInterval > fastInterval,
                "Spawn density increases at each difficulty phase");
        check(
                fastInterval >= .69 && slowInterval <= 1.21,
                "Spawn intervals stay within specified 0.7 to 1.2 seconds");
        check(
                slowSpeed < mediumSpeed && mediumSpeed < density.baseSpeed(),
                "Falling speed increases at each difficulty phase");
    }

    private static void advance(Match match, double seconds) {
        while (seconds > .000001) {
            double step = Math.min(.05, seconds);
            match.step(step);
            seconds -= step;
        }
    }

    private static void protocol() {
        Map<String, Object> original =
                map(
                        "type",
                        "TEST",
                        "name",
                        "Táo 🍎 / \\\"\n\t",
                        "array",
                        Arrays.asList(1, -2.5, true, null, map("nested", "nho")));
        Map<String, Object> decoded = decode(Protocol.json(original));
        check(
                Objects.equals(original.get("name"), decoded.get("name")),
                "JSON Unicode and escapes round trip");
        check(list(decoded.get("array")).size() == 5, "JSON nested arrays round trip");
        check(
                mapValue(list(decoded.get("array")).get(4)).get("nested").equals("nho"),
                "JSON nested objects round trip");
        for (String malformed :
                List.of(
                        "",
                        "{",
                        "{\"x\":}",
                        "{\"x\":1,}",
                        "{\"x\":NaN}",
                        "{\"x\":Infinity}",
                        "{\"x\":1} trailing",
                        "{\"x\":\"unterminated}",
                        "{\"x\":1,\"x\":2}",
                        "{\"x\":01}",
                        "{\"x\":1e999}",
                        "{\"x\":\"\\q\"}")) {
            boolean rejected = false;
            try {
                Protocol.parse(malformed);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            check(rejected, "Malformed JSON rejected: " + malformed);
        }
        boolean oversized = false;
        try {
            Protocol.parse(" ".repeat(Protocol.MAX_MESSAGE + 1));
        } catch (IllegalArgumentException expected) {
            oversized = true;
        }
        check(oversized, "Oversized JSON rejected");
        boolean deeplyNested = false;
        try {
            Protocol.parse("[".repeat(66) + "0" + "]".repeat(66));
        } catch (IllegalArgumentException expected) {
            deeplyNested = true;
        }
        check(deeplyNested, "Excessive JSON nesting rejected");
        try {
            check(
                    Protocol.readLine(new java.io.StringReader("{\"x\":1}\r\nnext"))
                            .equals("{\"x\":1}"),
                    "TCP framing accepts CRLF");
            check(
                    Protocol.readLine(new java.io.StringReader("")) == null,
                    "TCP framing detects EOF");
            boolean bounded = false;
            try {
                Protocol.readLine(new java.io.StringReader("x".repeat(Protocol.MAX_MESSAGE + 1)));
            } catch (java.io.IOException expected) {
                bounded = true;
            }
            check(bounded, "TCP framing rejects oversized lines before parsing");
        } catch (java.io.IOException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void lifecycle() throws Exception {
        Path data = Files.createTempDirectory("fruit-test-memory-");
        try (GameServer server = new GameServer(0, data, true)) {
            server.start();
            try (Peer first = new Peer(server.port());
                    Peer second = new Peer(server.port());
                    Peer admin = new Peer(server.port());
                    Peer other = new Peer(server.port())) {
                first.send("REGISTER", "username", "x", "password", "123");
                ok(first.await("AUTH"), false, "Invalid registration rejected");
                first.send(
                        "REGISTER",
                        "username",
                        "test_alpha",
                        "password",
                        PASSWORD,
                        "displayName",
                        "Táo đỏ");
                Map<String, Object> alpha = first.await("AUTH");
                ok(alpha, true, "Player registration succeeds over TCP");
                int firstId = integer(mapValue(alpha.get("user")).get("id"));
                check(
                        text(mapValue(alpha.get("user")).get("role")).equals("PLAYER"),
                        "Registration grants PLAYER role");
                check(
                        !mapValue(alpha.get("user")).containsKey("password"),
                        "Password absent from public profile");
                second.send(
                        "REGISTER",
                        "username",
                        "test_beta",
                        "password",
                        PASSWORD,
                        "displayName",
                        "Nho tím");
                Map<String, Object> beta = second.await("AUTH");
                ok(beta, true, "Second player registers");
                int secondId = integer(mapValue(beta.get("user")).get("id"));
                other.send("REGISTER", "username", "test_alpha", "password", PASSWORD);
                ok(other.await("AUTH"), false, "Duplicate username rejected");
                other.send("LOGIN", "username", "test_alpha", "password", "incorrect-password");
                ok(other.await("AUTH"), false, "Invalid password rejected");
                other.send("LOGIN", "username", "test_alpha", "password", PASSWORD);
                ok(other.await("AUTH"), false, "Duplicate live session rejected");
                other.send("MATCH_FIND");
                ok(other.await("ERROR"), false, "Anonymous matchmaking rejected");
                first.send("PROFILE_GET");
                ok(first.await("PROFILE"), true, "Own profile can be read");
                first.send(
                        "PROFILE_UPDATE",
                        "displayName",
                        "Vườn táo",
                        "oldPassword",
                        "wrong",
                        "newPassword",
                        "ChangedFruit!2026");
                ok(first.await("PROFILE"), false, "Password change requires old password");
                first.send(
                        "PROFILE_UPDATE",
                        "displayName",
                        "Vườn táo",
                        "oldPassword",
                        PASSWORD,
                        "newPassword",
                        "ChangedFruit!2026");
                Map<String, Object> changed = first.await("PROFILE");
                ok(changed, true, "Profile and password update succeeds");
                check(
                        text(mapValue(changed.get("user")).get("displayName")).equals("Vườn táo"),
                        "Unicode display name persists");
                first.send("ADMIN_GET");
                ok(first.await("ERROR"), false, "PLAYER cannot read admin data");
                admin.send("LOGIN", "username", "admin", "password", PASSWORD);
                Map<String, Object> adminAuth = admin.await("AUTH");
                ok(adminAuth, true, "Bootstrap administrator can log in");
                check(
                        text(mapValue(adminAuth.get("user")).get("role")).equals("ADMIN"),
                        "Administrator role preserved");
                admin.send("ADMIN_GET");
                Map<String, Object> adminData = admin.await("ADMIN_DATA");
                check(list(adminData.get("users")).size() >= 3, "Admin sees registered accounts");
                admin.send("ADMIN_LOCK", "id", secondId, "locked", true);
                ok(admin.await("ACTION"), true, "Admin locks account");
                second.close();
                try (Peer locked = new Peer(server.port())) {
                    locked.send("LOGIN", "username", "test_beta", "password", PASSWORD);
                    ok(locked.await("AUTH"), false, "Locked account cannot log in");
                }
                admin.send("ADMIN_LOCK", "id", secondId, "locked", false);
                ok(admin.await("ACTION"), true, "Admin unlocks account");
                try (Peer betaAgain = new Peer(server.port())) {
                    betaAgain.send("LOGIN", "username", "test_beta", "password", PASSWORD);
                    ok(betaAgain.await("AUTH"), true, "Unlocked account can log in");
                    queueAndMatch(server, first, betaAgain, firstId, secondId);
                }
            }
        }
    }

    private static void invitations() throws Exception {
        Path data = Files.createTempDirectory("fruit-test-invites-");
        try (GameServer server = new GameServer(0, data, true)) {
            server.start();
            try (Peer inviter = new Peer(server.port()); Peer invitee = new Peer(server.port())) {
                inviter.send(
                        "REGISTER",
                        "username",
                        "invite_alpha",
                        "password",
                        PASSWORD,
                        "displayName",
                        "Vườn táo");
                Map<String, Object> inviterAuth = inviter.await("AUTH");
                ok(inviterAuth, true, "Inviter registration succeeds");
                int inviterId = integer(mapValue(inviterAuth.get("user")).get("id"));
                invitee.send(
                        "REGISTER",
                        "username",
                        "invite_beta",
                        "password",
                        PASSWORD,
                        "displayName",
                        "Nho tím");
                Map<String, Object> inviteeAuth = invitee.await("AUTH");
                ok(inviteeAuth, true, "Invitee registration succeeds");
                int inviteeId = integer(mapValue(inviteeAuth.get("user")).get("id"));

                inviter.send("ONLINE_PLAYERS", "query", "nho");
                Map<String, Object> roster = inviter.await("ONLINE_PLAYERS");
                Map<String, Object> only = mapValue(list(roster.get("rows")).get(0));
                check(integer(only.get("id")) == inviteeId, "Online search finds display names without accents");
                check(Boolean.TRUE.equals(only.get("inviteable")), "Available online player can receive an invite");
                check(!only.containsKey("hash") && !only.containsKey("salt"), "Online roster exposes no credentials");

                invitee.send("MATCH_FIND");
                invitee.await("QUEUE");
                inviter.send("INVITE_SEND", "targetId", inviteeId);
                check(type(inviter.await("ERROR")).equals("ERROR"), "Cannot invite a player in automatic matchmaking");
                invitee.send("MATCH_CANCEL");
                invitee.await("QUEUE");

                inviter.send("INVITE_SEND", "targetId", inviteeId);
                Map<String, Object> sent = inviter.await("INVITE_STATUS");
                Map<String, Object> received = invitee.await("INVITE_RECEIVED");
                String declinedId = text(received.get("inviteId"));
                check(
                        text(sent.get("inviteId")).equals(declinedId)
                                && integer(mapValue(received.get("from")).get("id")) == inviterId,
                        "Invitation reaches exactly the requested online player");
                invitee.send("INVITE_DECLINE", "inviteId", declinedId);
                check(
                        text(inviter.await("INVITE_STATUS").get("state")).equals("DECLINED"),
                        "Inviter receives a declined invitation status");

                inviter.send("INVITE_SEND", "targetId", inviteeId);
                String acceptedId = text(invitee.await("INVITE_RECEIVED").get("inviteId"));
                inviter.await("INVITE_STATUS");
                invitee.send("INVITE_ACCEPT", "inviteId", acceptedId);
                Map<String, Object> foundByInviter = inviter.await("MATCH_FOUND");
                Map<String, Object> foundByInvitee = invitee.await("MATCH_FOUND");
                check(
                        text(foundByInviter.get("matchId")).equals(text(foundByInvitee.get("matchId"))),
                        "Accepting an invitation creates one shared direct match");
                check(
                        server.engine.submit(() -> server.queue.isEmpty() && server.activeByUser.size() == 2).get(),
                        "Direct invitation bypasses and leaves the automatic queue empty");
            }
        }
    }

    private static void queueAndMatch(
            GameServer server, Peer first, Peer second, int firstId, int secondId)
            throws Exception {
        first.send("MATCH_FIND");
        check(
                Boolean.TRUE.equals(first.await("QUEUE").get("queued")),
                "Player enters matchmaking queue");
        first.send("MATCH_FIND");
        check(
                Boolean.TRUE.equals(first.await("QUEUE").get("queued")),
                "Duplicate matchmaking request stays queued");
        check(
                server.engine.submit(() -> server.queue.size()).get() == 1,
                "Repeated request cannot queue the same account twice");
        first.send("MATCH_CANCEL");
        check(
                Boolean.FALSE.equals(first.await("QUEUE").get("queued")),
                "Player cancels matchmaking");
        check(
                server.engine.submit(() -> server.queue.isEmpty()).get(),
                "Cancelled account leaves queue");
        first.send("MATCH_FIND");
        first.await("QUEUE");
        first.close();
        first = loginEventually(server.port(), "test_alpha", "ChangedFruit!2026");
        check(
                server.engine.submit(() -> server.queue.isEmpty()).get(),
                "Disconnect while searching removes the account from queue");
        first.send("MATCH_FIND");
        first.await("QUEUE");
        second.send("MATCH_FIND");
        Map<String, Object> cancelled = first.await("MATCH_FOUND");
        second.await("MATCH_FOUND");
        second.close();
        Map<String, Object> requeued = first.await("QUEUE");
        check(
                Boolean.TRUE.equals(requeued.get("queued")),
                "Countdown disconnect requeues the remaining player");
        check(
                server.engine
                        .submit(() -> server.matches.isEmpty() && server.activeByUser.isEmpty())
                        .get(),
                "Countdown disconnect cancels the unfinished match");

        Peer liveFirst = first;
        Peer liveSecond = loginEventually(server.port(), "test_beta", PASSWORD);
        List<Peer> firstConnections = new ArrayList<>(List.of(first));
        List<Peer> secondConnections = new ArrayList<>(List.of(second, liveSecond));
        try {
            liveSecond.send("MATCH_FIND");
            Map<String, Object> found = first.await("MATCH_FOUND");
            liveSecond.await("MATCH_FOUND");
            String matchId = text(found.get("matchId"));
            check(
                    !matchId.equals(text(cancelled.get("matchId"))),
                    "Requeued player gets a fresh match");
            near(
                    number(found.get("countdown")),
                    3,
                    .001,
                    "Match announces a three second countdown");
            long foundAt = System.nanoTime();
            first.send("MATCH_CANCEL");
            first.await("ERROR");
            first.send("USE_SKILL", "skill", 1);
            first.await("ERROR");
            first.await("MATCH_START");
            liveSecond.await("MATCH_START");
            double countdownSeconds = (System.nanoTime() - foundAt) / 1_000_000_000.0;
            check(
                    countdownSeconds >= 2.7 && countdownSeconds < 6,
                    "Real countdown lasts three seconds");
            long startedAt = System.nanoTime();
            first.await(
                    m -> type(m).equals("GAME_STATE") && text(m.get("phase")).equals("PLAYING"),
                    Duration.ofSeconds(3),
                    "playing state");
            double originalX = number(player(first.state, firstId).get("x"));
            first.send("PLAYER_MOVE", "direction", 99);
            first.await("ERROR");
            first.send("PLAYER_MOVE", "direction", 1, "x", -999999, "score", 999999, "energy", 100);
            Map<String, Object> moved =
                    first.await(
                            m ->
                                    type(m).equals("GAME_STATE")
                                            && number(player(m, firstId).get("x")) > originalX + 25,
                            Duration.ofSeconds(3),
                            "server basket movement");
            Map<String, Object> movedPlayer = player(moved, firstId);
            double movedX = number(movedPlayer.get("x"));
            check(
                    movedX >= 50 && movedX <= 750 && movedX - originalX < 200,
                    "GameServer bounds and advances movement rather than accepting client"
                            + " position");
            check(
                    integer(movedPlayer.get("score")) == 0
                            && integer(movedPlayer.get("energy")) == 0,
                    "Client-provided score and energy are ignored");
            first.send("PLAYER_MOVE", "direction", 0);
            first.send("CATCH_EVENT", "delta", 999999, "score", 999999);
            first.await("ERROR");
            first.send("SCORE_UPDATE", "score", 999999);
            first.await("ERROR");

            boolean rejoined = false, secondOffline = false;
            int firstDirection = 0, secondDirection = 0;
            Set<Integer> stages = new HashSet<>();
            Map<String, Object> result = null;
            while ((System.nanoTime() - startedAt) < Duration.ofSeconds(72).toNanos()) {
                Map<String, Object> state = liveFirst.state;
                if (state != null && text(state.get("matchId")).equals(matchId)) {
                    double elapsed = 60 - number(state.get("remaining"));
                    stages.add(integer(state.get("stage")));
                    if (!rejoined && elapsed >= 8) {
                        liveFirst.send("PLAYER_MOVE", "direction", 0);
                        double previousRemaining = number(state.get("remaining"));
                        liveFirst.close();
                        liveFirst =
                                loginEventually(server.port(), "test_alpha", "ChangedFruit!2026");
                        firstConnections.add(liveFirst);
                        Map<String, Object> restored = liveFirst.await("MATCH_FOUND");
                        check(
                                Boolean.TRUE.equals(restored.get("rejoin"))
                                        && text(restored.get("matchId")).equals(matchId),
                                "Re-login restores the same running match");
                        Map<String, Object> resumed = liveFirst.await("GAME_STATE");
                        check(
                                number(resumed.get("remaining")) <= previousRemaining,
                                "Match clock continues through disconnection");
                        Map<String, Object> own = player(resumed, firstId);
                        for (String key :
                                List.of(
                                        "score",
                                        "combo",
                                        "energy",
                                        "x",
                                        "objects",
                                        "freeze",
                                        "magnet",
                                        "basketWidth",
                                        "cooldown"))
                            check(own.containsKey(key), "Reconnect restores " + key);
                        liveFirst.send("MATCH_REJOIN");
                        liveFirst.await("MATCH_FOUND");
                        liveFirst.await("GAME_STATE");
                        firstDirection = 0;
                        rejoined = true;
                    }
                    if (!secondOffline && elapsed >= 55) {
                        liveSecond.close();
                        secondOffline = true;
                    }
                    firstDirection = drive(liveFirst, firstId, firstDirection);
                    if (!secondOffline)
                        secondDirection = drive(liveSecond, secondId, secondDirection);
                }
                if (liveFirst.result != null) {
                    result = liveFirst.result;
                    break;
                }
                Thread.sleep(50);
            }
            check(result != null, "Real match finishes without client-provided result");
            double realDuration = (System.nanoTime() - startedAt) / 1_000_000_000.0;
            check(
                    realDuration >= 59 && realDuration < 69,
                    "Real playing phase lasts sixty seconds");
            check(rejoined && secondOffline, "Natural match exercised both reconnect scenarios");
            check(stages.equals(Set.of(1, 2, 3)), "Real match visits all three difficulty phases");
            check(
                    text(result.get("reason")).equals("TIME_UP")
                            && Boolean.TRUE.equals(result.get("saved")),
                    "Time-up result is saved");
            Map<String, Object> a = player(result, firstId), b = player(result, secondId);
            int scoreA = integer(a.get("score")), scoreB = integer(b.get("score"));
            int expectedWinner = scoreA == scoreB ? 0 : scoreA > scoreB ? firstId : secondId;
            check(
                    integer(result.get("winnerId")) == expectedWinner,
                    "GameServer chooses winner from final scores");
            check(
                    integer(a.get("correct")) > 0 && integer(b.get("correct")) > 0,
                    "Socket bots naturally catch target fruit");
            for (String field : List.of("correct", "wrong", "bombs", "maxCombo", "skills"))
                check(
                        a.containsKey(field) && b.containsKey(field),
                        "Result includes both players' " + field);
            verifySchedule(firstConnections, secondConnections);
            try (Peer afterEnd = loginEventually(server.port(), "test_beta", PASSWORD)) {
                Map<String, Object> recoveredResult = afterEnd.await("MATCH_RESULT");
                check(
                        text(recoveredResult.get("matchId")).equals(matchId),
                        "Re-login after time-up receives saved result");
                check(
                        integer(player(recoveredResult, secondId).get("score")) == scoreB,
                        "Recovered result preserves final statistics");
            }
            liveFirst.send("HISTORY");
            List<?> history = list(liveFirst.await("HISTORY").get("rows"));
            check(
                    history.size() == 1
                            && text(mapValue(history.get(0)).get("matchId")).equals(matchId),
                    "History includes the completed match and excludes cancelled countdown");
            liveFirst.send("LEADERBOARD");
            List<?> ranking = list(liveFirst.await("LEADERBOARD").get("rows"));
            check(ranking.size() == 2, "Leaderboard includes players and excludes admin");
            liveFirst.send("PROFILE_GET");
            Map<String, Object> profile = mapValue(liveFirst.await("PROFILE").get("user"));
            check(
                    integer(profile.get("wins"))
                                    + integer(profile.get("losses"))
                                    + integer(profile.get("draws"))
                            == 1,
                    "Exactly one completed match updates account statistics");
            check(
                    integer(profile.get("totalScore")) == scoreA,
                    "Account total score matches authoritative result");
        } finally {
            liveFirst.close();
            liveSecond.close();
        }
    }

    /** Inputs only: target selection cannot award a catch, a point or an energy unit. */
    private static int drive(Peer peer, int id, int previousDirection) throws Exception {
        Map<String, Object> state = peer.state;
        if (state == null) return previousDirection;
        Map<String, Object> own = player(state, id);
        String target = text(state.get("target"));
        double x = number(own.get("x"));
        double desiredX = 400, closestY = -1000;
        for (Object value : list(own.get("objects"))) {
            Map<String, Object> drop = mapValue(value);
            double y = number(drop.get("y"));
            if (text(drop.get("kind")).equals(target) && y > closestY && y < 570) {
                closestY = y;
                desiredX = number(drop.get("x"));
            }
        }
        int direction = Math.abs(desiredX - x) < 18 ? 0 : desiredX < x ? -1 : 1;
        if (direction != previousDirection) peer.send("PLAYER_MOVE", "direction", direction);
        if (integer(own.get("energy")) >= 30 && number(own.get("cooldown")) <= 0)
            peer.send("USE_SKILL", "skill", 1);
        return direction;
    }

    private static void verifySchedule(List<Peer> alpha, List<Peer> beta) {
        Map<Long, Map<String, Object>> first = spawns(alpha), second = spawns(beta);
        int common = 0;
        for (Map.Entry<Long, Map<String, Object>> entry : first.entrySet()) {
            Map<String, Object> other = second.get(entry.getKey());
            if (other == null) continue; // Skill bombs have an id on the victim's field only.
            Map<String, Object> own = entry.getValue();
            check(
                    text(own.get("kind")).equals(text(other.get("kind"))),
                    "TCP base spawns have the same fruit kind");
            near(
                    number(own.get("x")),
                    number(other.get("x")),
                    .001,
                    "TCP base spawns have the same x position");
            near(
                    number(own.get("speed")),
                    number(other.get("speed")),
                    .001,
                    "TCP base spawns have the same speed");
            common++;
        }
        check(
                common >= 35,
                "Both clients observe a shared base spawn schedule throughout the match");
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Peer peer : alpha)
            synchronized (peer.observed) {
                for (Map<String, Object> message : peer.observed)
                    if (type(message).equals("TARGET_CHANGE")) changes.add(message);
            }
        check(changes.size() == 3, "Real match broadcasts all three target rotations");
        for (int i = 1; i < changes.size(); i++)
            check(
                    !text(changes.get(i).get("target"))
                            .equals(text(changes.get(i - 1).get("target"))),
                    "Consecutive broadcast targets never repeat");
    }

    private static Map<Long, Map<String, Object>> spawns(List<Peer> peers) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Peer peer : peers)
            synchronized (peer.observed) {
                for (Map<String, Object> message : peer.observed) {
                    if (!type(message).equals("FRUIT_SPAWN")) continue;
                    Map<String, Object> drop = mapValue(message.get("object"));
                    result.put(((Number) drop.get("id")).longValue(), drop);
                }
            }
        return result;
    }

    private static Map<String, Object> player(Map<String, Object> message, int id) {
        for (Object value : list(message.get("players"))) {
            Map<String, Object> player = mapValue(value);
            if (integer(player.get("id")) == id) return player;
        }
        throw new AssertionError("Player " + id + " missing from " + message);
    }

    private static Peer loginEventually(int port, String username, String password)
            throws Exception {
        Peer peer = new Peer(port);
        try {
            for (int attempt = 0; attempt < 10; attempt++) {
                peer.send("LOGIN", "username", username, "password", password);
                Map<String, Object> auth = peer.await("AUTH");
                if (Boolean.TRUE.equals(auth.get("ok"))) return peer;
                Thread.sleep(100);
            }
            throw new AssertionError("Unable to log in after disconnection: " + username);
        } catch (Exception | AssertionError failure) {
            peer.close();
            throw failure;
        }
    }

    private static void persistence(boolean mysql) throws Exception {
        Path data = Files.createTempDirectory("fruit-test-durable-");
        String previousUrl = System.getProperty("fruit.db.url");
        String database = "fruit_battle_test_" + Long.toUnsignedString(System.nanoTime());
        if (mysql) {
            // A new database is retained as audit evidence; the production database is untouched.
            System.setProperty(
                    "fruit.db.url",
                    "jdbc:mysql://localhost:3306/"
                            + database
                            + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Bangkok&connectTimeout=3000&socketTimeout=5000");
        }
        String matchId;
        int firstId, secondId;
        try {
            try (GameServer server = new GameServer(0, data, false, !mysql)) {
                server.start();
                try (Peer first = new Peer(server.port());
                        Peer second = new Peer(server.port())) {
                    first.send(
                            "REGISTER",
                            "username",
                            "persist_alpha",
                            "password",
                            PASSWORD,
                            "displayName",
                            "Táo lưu trữ");
                    Map<String, Object> firstAuth = first.await("AUTH");
                    ok(firstAuth, true, "Durable account alpha registers");
                    firstId = integer(mapValue(firstAuth.get("user")).get("id"));
                    second.send("REGISTER", "username", "persist_beta", "password", PASSWORD);
                    Map<String, Object> secondAuth = second.await("AUTH");
                    ok(secondAuth, true, "Durable account beta registers");
                    secondId = integer(mapValue(secondAuth.get("user")).get("id"));
                    first.send(
                            "PROFILE_UPDATE",
                            "displayName",
                            "Vườn nho lưu trữ",
                            "avatarId",
                            1,
                            "oldPassword",
                            PASSWORD,
                            "newPassword",
                            "DurableFruit!2026");
                    ok(
                            first.await("PROFILE"),
                            true,
                            "Durable profile and password update succeeds");
                    int a = firstId, b = secondId;
                    matchId =
                            server.engine
                                    .submit(
                                            () -> {
                                                User alpha = server.store.users.get(a),
                                                        beta = server.store.users.get(b);
                                                check(
                                                        !alpha.hash.equals(PASSWORD)
                                                                && !alpha.hash.equals(
                                                                        "DurableFruit!2026"),
                                                        "Durable storage uses password hashes");
                                                check(
                                                        !alpha.salt.equals(beta.salt),
                                                        "Each account has a distinct random"
                                                                + " password salt");
                                                Match match = new Match(server, alpha, beta);
                                                match.countdown = 0;
                                                for (int i = 0; i < 5; i++)
                                                    match.catchDrop(
                                                            match.players[0],
                                                            new Drop(
                                                                    i,
                                                                    match.target,
                                                                    400,
                                                                    550,
                                                                    180));
                                                String wrong =
                                                        match.target.equals("APPLE")
                                                                ? "BANANA"
                                                                : "APPLE";
                                                match.catchDrop(
                                                        match.players[1],
                                                        new Drop(10, wrong, 400, 550, 180));
                                                match.finish("TIME_UP", 0);
                                                match.finish("TIME_UP", 0);
                                                check(
                                                        server.store.records.size() == 1,
                                                        "Finishing twice cannot duplicate result or"
                                                                + " statistics");
                                                return match.id;
                                            })
                                    .get(8, TimeUnit.SECONDS);
                    Map<String, Object> saved = first.await("MATCH_RESULT");
                    check(Boolean.TRUE.equals(saved.get("saved")), "Durable match commit succeeds");
                    if (mysql) {
                        boolean transaction =
                                server.engine
                                        .submit(
                                                () -> {
                                                    java.sql.Connection previous =
                                                            server.store.connection.get();
                                                    previous.close();
                                                    java.sql.Connection reconnected =
                                                            server.store.connection.get();
                                                    check(
                                                            reconnected != previous
                                                                    && reconnected.isValid(2),
                                                            "MySQL reconnects after a closed"
                                                                    + " connection");
                                                    try (java.sql.Statement statement =
                                                                    reconnected.createStatement();
                                                            java.sql.ResultSet rows =
                                                                    statement.executeQuery(
                                                                            "SELECT (SELECT"
                                                                                + " COUNT(*) FROM"
                                                                                + " matches),"
                                                                                + " (SELECT"
                                                                                + " COUNT(*) FROM"
                                                                                + " accounts u JOIN"
                                                                                + " player_stats s"
                                                                                + " ON s.account_id=u.id"
                                                                                + " WHERE username"
                                                                                + " IN ('persist_alpha','persist_beta')"
                                                                                + " AND wins+losses+draws=1)")) {
                                                        rows.next();
                                                        return rows.getInt(1) == 1
                                                                && rows.getInt(2) == 2;
                                                    }
                                                })
                                        .get(8, TimeUnit.SECONDS);
                        check(
                                transaction,
                                "MySQL stores one result and both updated account statistics");
                    }
                }
            }
            if (!mysql) {
                Path file = data.resolve("fruit-data.jsonl");
                check(Files.isRegularFile(file), "Explicit local storage creates durable file");
                String contents = Files.readString(file, StandardCharsets.UTF_8);
                check(
                        !contents.contains(PASSWORD) && !contents.contains("DurableFruit!2026"),
                        "Durable file contains no plaintext passwords");
            }
            try (GameServer restarted = new GameServer(0, data, false, !mysql)) {
                restarted.start();
                try (Peer first = new Peer(restarted.port());
                        Peer admin = new Peer(restarted.port())) {
                    first.send("LOGIN", "username", "persist_alpha", "password", PASSWORD);
                    ok(first.await("AUTH"), false, "Old password is rejected after restart");
                    first.send(
                            "LOGIN", "username", "persist_alpha", "password", "DurableFruit!2026");
                    Map<String, Object> restored = first.await("AUTH");
                    ok(restored, true, "Changed password survives restart");
                    Map<String, Object> user = mapValue(restored.get("user"));
                    check(integer(user.get("avatarId")) == 1, "Avatar survives restart and login");
                    first.send("PROFILE_UPDATE", "displayName", "Unwanted name", "avatarId", 2);
                    ok(first.await("PROFILE"), false, "Unknown avatar is rejected");
                    check(
                            text(user.get("displayName")).equals("Vườn nho lưu trữ"),
                            "Unicode profile survives restart");
                    check(
                            integer(user.get("wins")) == 1 && integer(user.get("totalScore")) == 70,
                            "Winner statistics survive restart");
                    first.send("HISTORY");
                    List<?> history = list(first.await("HISTORY").get("rows"));
                    check(
                            history.size() == 1,
                            "History survives restart without duplicate matches");
                    Map<String, Object> row = mapValue(history.get(0));
                    check(
                            text(row.get("matchId")).equals(matchId)
                                    && integer(row.get("ownScore")) == 70
                                    && integer(row.get("opponentScore")) == -5
                                    && text(row.get("outcome")).equals("WIN"),
                            "Durable history retains scores and outcome");
                    check(
                            integer(row.get("correct")) == 5 && integer(row.get("maxCombo")) == 5,
                            "Durable history retains catch and combo statistics");
                    first.send("LEADERBOARD");
                    List<?> ranking = list(first.await("LEADERBOARD").get("rows"));
                    check(
                            ranking.size() == 2
                                    && integer(mapValue(ranking.get(0)).get("id")) == firstId,
                            "Durable leaderboard ranks winner first");
                    first.send("MATCH_REJOIN");
                    Map<String, Object> lastResult = first.await("MATCH_RESULT");
                    check(
                            text(lastResult.get("matchId")).equals(matchId)
                                    && integer(player(lastResult, secondId).get("score")) == -5,
                            "Explicit rejoin returns last completed result after server restart");
                    admin.send("LOGIN", "username", "admin", "password", PASSWORD);
                    ok(admin.await("AUTH"), true, "Administrator account survives restart");
                    admin.send("ADMIN_GET", "limit", 1);
                    Map<String, Object> page = admin.await("ADMIN_DATA");
                    check(
                            list(page.get("users")).size() == 1
                                    && Boolean.TRUE.equals(page.get("hasMoreUsers")),
                            "Admin account pagination limits response size");
                    check(
                            integer(mapValue(page.get("stats")).get("totalMatches")) == 1
                                    && list(page.get("matches")).size() == 1,
                            "Admin statistics include durable match");
                    admin.send("ADMIN_GET", "userOffset", 1, "limit", 1);
                    Map<String, Object> nextPage = admin.await("ADMIN_DATA");
                    check(
                            integer(mapValue(list(nextPage.get("users")).get(0)).get("id"))
                                    != integer(mapValue(list(page.get("users")).get(0)).get("id")),
                            "Admin pagination advances to a different account");
                    admin.send("ADMIN_GET", "userQuery", "VUON NHO", "limit", 1);
                    Map<String, Object> foundUser = admin.await("ADMIN_DATA");
                    check(list(foundUser.get("users")).size() == 1
                            && integer(mapValue(list(foundUser.get("users")).get(0)).get("id")) == firstId
                            && integer(foundUser.get("userCount")) == 1
                            && !Boolean.TRUE.equals(foundUser.get("hasMoreUsers")),
                            "Admin search ignores case/accents and filters before pagination");
                    admin.send("ADMIN_GET", "matchQuery", "PERSIST_BETA");
                    Map<String, Object> foundMatch = admin.await("ADMIN_DATA");
                    check(list(foundMatch.get("matches")).size() == 1
                            && integer(foundMatch.get("matchCount")) == 1,
                            "Admin match search finds a participant username");
                    admin.send("ADMIN_GET", "matchQuery", matchId);
                    check(list(admin.await("ADMIN_DATA").get("matches")).size() == 1,
                            "Admin search finds an exact match ID");
                    admin.send("ADMIN_GET", "userQuery", "no-such-player", "matchQuery", "no-such-match");
                    Map<String, Object> absent = admin.await("ADMIN_DATA");
                    check(list(absent.get("users")).isEmpty() && list(absent.get("matches")).isEmpty()
                            && !Boolean.TRUE.equals(absent.get("hasMoreUsers"))
                            && !Boolean.TRUE.equals(absent.get("hasMoreMatches")),
                            "Empty admin search results have no next page");
                }
            }
            System.out.println(
                    mysql
                            ? "MySQL audit database retained: " + database
                            : "Local persistence restart verified.");
        } finally {
            if (previousUrl == null) System.clearProperty("fruit.db.url");
            else System.setProperty("fruit.db.url", previousUrl);
        }
    }

    private static void ok(Map<String, Object> message, boolean expected, String description) {
        check(Boolean.TRUE.equals(message.get("ok")) == expected, description + ": " + message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(
                Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decode(String json) {
        return (Map<String, Object>) (Object) Protocol.parse(json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?>))
            throw new AssertionError("Expected JSON object, got: " + value);
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?>))
            throw new AssertionError("Expected JSON array, got: " + value);
        return (List<?>) value;
    }

    private static String type(Map<String, Object> value) {
        return text(value.get("type"));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integer(Object value) {
        return ((Number) value).intValue();
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    /** Reader remains active while a test sends input or waits for a particular event. */
    private static final class Peer implements Closeable {
        final Socket socket;
        final BufferedWriter output;
        final LinkedBlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        final List<Map<String, Object>> observed =
                java.util.Collections.synchronizedList(new ArrayList<>());
        volatile Map<String, Object> state;
        volatile Map<String, Object> result;
        volatile Throwable readerFailure;

        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setTcpNoDelay(true);
            output =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.UTF_8));
            Thread reader =
                    new Thread(
                            () -> {
                                try (BufferedReader input =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        socket.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = input.readLine()) != null) {
                                        Map<String, Object> message = decode(line);
                                        if (type(message).equals("GAME_STATE")) state = message;
                                        if (type(message).equals("MATCH_RESULT")) result = message;
                                        observed.add(message);
                                        inbox.add(message);
                                    }
                                } catch (Throwable failure) {
                                    if (!socket.isClosed()) readerFailure = failure;
                                }
                            },
                            "test-tcp-reader");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(String type, Object... fields) throws Exception {
            Map<String, Object> message = map(fields);
            message.put("type", type);
            output.write(Protocol.json(message));
            output.newLine();
            output.flush();
        }

        Map<String, Object> await(String type) throws Exception {
            return await(m -> type(m).equals(type), Duration.ofSeconds(5), type);
        }

        Map<String, Object> await(
                Predicate<Map<String, Object>> predicate, Duration timeout, String description)
                throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<String> skipped = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                Map<String, Object> message =
                        inbox.poll(
                                Math.min(
                                        200,
                                        Math.max(
                                                1,
                                                TimeUnit.NANOSECONDS.toMillis(
                                                        deadline - System.nanoTime()))),
                                TimeUnit.MILLISECONDS);
                if (message != null) {
                    if (predicate.test(message)) return message;
                    skipped.add(type(message));
                }
                if (readerFailure != null)
                    throw new AssertionError("TCP reader failed", readerFailure);
            }
            throw new AssertionError(
                    "Timed out waiting for " + description + "; received " + skipped);
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}

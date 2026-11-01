package fruit.client.shared.preview;

import static fruit.common.Protocol.obj;

import fruit.client.application.ClientState;

import java.util.List;
import java.util.Map;

/** Stable preview data used by the snapshot command. */
public final class DemoData {
    private DemoData() {}

    public static void populate(ClientState model) {
        model.user =
                obj(
                        "id",
                        1,
                        "username",
                        "mint",
                        "displayName",
                        "Mint Orchard",
                        "role",
                        "ADMIN",
                        "wins",
                        24,
                        "losses",
                        7,
                        "draws",
                        3,
                        "totalScore",
                        2840);
        List<Map<String, Object>> objects =
                List.of(
                        obj("id", 1, "kind", "APPLE", "x", 148, "y", 123),
                        obj("id", 2, "kind", "BANANA", "x", 350, "y", 250),
                        obj("id", 3, "kind", "GRAPE", "x", 590, "y", 157),
                        obj("id", 4, "kind", "APPLE", "x", 408, "y", 416),
                        obj("id", 5, "kind", "BOMB", "x", 700, "y", 330));
        Map<String, Object> me =
                obj(
                        "id",
                        1,
                        "displayName",
                        "Mint Orchard",
                        "x",
                        405,
                        "score",
                        160,
                        "combo",
                        7,
                        "energy",
                        70,
                        "correct",
                        17,
                        "wrong",
                        2,
                        "bombs",
                        0,
                        "maxCombo",
                        7,
                        "skills",
                        2,
                        "cooldown",
                        0,
                        "freeze",
                        0,
                        "magnet",
                        0,
                        "connected",
                        true,
                        "objects",
                        objects);
        Map<String, Object> opponent =
                obj(
                        "id",
                        2,
                        "displayName",
                        "Peachy Player",
                        "x",
                        320,
                        "score",
                        125,
                        "combo",
                        3,
                        "energy",
                        40,
                        "correct",
                        15,
                        "wrong",
                        3,
                        "bombs",
                        1,
                        "maxCombo",
                        5,
                        "skills",
                        1,
                        "cooldown",
                        0,
                        "freeze",
                        0,
                        "magnet",
                        0,
                        "connected",
                        true,
                        "objects",
                        objects);
        model.state =
                obj(
                        "type",
                        "GAME_STATE",
                        "matchId",
                        25,
                        "phase",
                        "PLAYING",
                        "remaining",
                        32,
                        "target",
                        "APPLE",
                        "stage",
                        2,
                        "players",
                        List.of(me, opponent));
        model.result =
                obj(
                        "matchId",
                        25,
                        "winnerId",
                        1,
                        "saved",
                        true,
                        "reason",
                        "TIME_UP",
                        "players",
                        List.of(me, opponent));
        model.ranking =
                List.of(
                        model.user,
                        obj(
                                "id",
                                2,
                                "username",
                                "peachy",
                                "displayName",
                                "Peachy Player",
                                "wins",
                                18,
                                "losses",
                                10,
                                "draws",
                                2,
                                "totalScore",
                                2130));
        model.history =
                List.of(
                        obj(
                                "createdAt",
                                "26/10/2026 20:45",
                                "opponent",
                                "Peachy Player",
                                "outcome",
                                "WIN",
                                "ownScore",
                                160,
                                "opponentScore",
                                125,
                                "correct",
                                17,
                                "wrong",
                                2,
                                "bombs",
                                0,
                                "maxCombo",
                                7,
                                "skills",
                                2));
        model.adminUsers = model.ranking;
        model.adminMatches =
                List.of(
                        obj(
                                "matchId",
                                25,
                                "createdAt",
                                "26/10/2026 20:45",
                                "players",
                                List.of(me, opponent)));
        model.adminStats =
                obj(
                        "users",
                        32,
                        "online",
                        12,
                        "activeMatches",
                        4,
                        "totalMatches",
                        128,
                        "queued",
                        2,
                        "locked",
                        1,
                        "uptime",
                        3800);
    }
}

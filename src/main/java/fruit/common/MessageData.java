package fruit.common;

import static fruit.common.Protocol.integer;
import static fruit.common.Protocol.obj;
import static fruit.common.Protocol.str;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed conversions and display values shared by client components. */
public final class MessageData {
    private MessageData() {}

    public static String displayName(Map<String, Object> player) {
        return str(player, "displayName", str(player, "username", "Người chơi"));
    }

    public static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "0" : String.valueOf(value);
    }

    public static Map<String, Object> recordPlayer(Map<String, Object> record, int index) {
        List<Map<String, Object>> players = maps(record.get("players"));
        return players.size() > index ? players.get(index) : obj();
    }

    public static String fruitName(String kind) {
        return switch (kind) {
            case "BANANA" -> "CHUỐI";
            case "GRAPE" -> "NHO";
            default -> "TÁO";
        };
    }

    public static String outcome(String value) {
        return switch (value) {
            case "WIN" -> "Thắng";
            case "LOSS" -> "Thua";
            case "DRAW" -> "Hòa";
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    public static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list)
            for (Object row : list) if (row instanceof Map<?, ?>) result.add(asMap(row));
        return result;
    }

    public static List<Map<String, Object>> orderedPlayers(Map<String, Object> data, int ownId) {
        List<Map<String, Object>> players = maps(data.get("players"));
        players.sort(Comparator.comparingInt(p -> integer(p, "id") == ownId ? 0 : 1));
        return players;
    }

    public static Map<String, Object> player(Map<String, Object> snapshot, int id) {
        for (Map<String, Object> p : maps(snapshot.get("players")))
            if (integer(p, "id") == id) return p;
        return obj();
    }
}

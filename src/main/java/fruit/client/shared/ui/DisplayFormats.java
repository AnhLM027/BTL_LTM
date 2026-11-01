package fruit.client.shared.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Presentation formats; timestamps on the network remain ISO-8601 instants. */
public final class DisplayFormats {
    private static final DateTimeFormatter MATCH_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DisplayFormats() {}

    public static String matchTime(String value) {
        return matchTime(value, ZoneId.systemDefault());
    }

    public static String matchTime(String value, ZoneId zone) {
        if (value == null || value.isBlank()) return "—";
        try {
            return MATCH_TIME.withZone(zone).format(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return "—";
        }
    }
}

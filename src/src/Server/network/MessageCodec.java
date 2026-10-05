package Server.network;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Line-delimited TCP format: TYPE|key=urlEncodedValue&key2=urlEncodedValue.
 * URL encoding keeps delimiters and Unicode characters safe without another dependency.
 */
public final class MessageCodec {
    private MessageCodec() {
    }

    public static String encode(ProtocolMessage message) {
        StringBuilder line = new StringBuilder(message.type());
        if (message.fields().isEmpty()) {
            return line.toString();
        }

        line.append('|');
        boolean first = true;
        for (Map.Entry<String, String> field : message.fields().entrySet()) {
            if (!field.getKey().matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid field name: " + field.getKey());
            }
            if (!first) {
                line.append('&');
            }
            line.append(field.getKey())
                    .append('=')
                    .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return line.toString();
    }

    public static ProtocolMessage decode(String line) throws ProtocolException {
        if (line == null || line.isBlank() || line.length() > 8_192) {
            throw new ProtocolException("Message is empty or exceeds 8192 characters");
        }

        String[] sections = line.split("\\|", 2);
        String type = sections[0];
        if (!type.matches("[A-Z_]+")) {
            throw new ProtocolException("Invalid message type");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        if (sections.length == 2 && !sections[1].isEmpty()) {
            for (String pair : sections[1].split("&", -1)) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length != 2 || !keyValue[0].matches("[a-zA-Z][a-zA-Z0-9_]*")) {
                    throw new ProtocolException("Invalid message field");
                }
                if (fields.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8)) != null) {
                    throw new ProtocolException("Duplicate message field: " + keyValue[0]);
                }
            }
        }
        try {
            return new ProtocolMessage(type, fields);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException(exception.getMessage());
        }
    }
}

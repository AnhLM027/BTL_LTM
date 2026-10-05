package Server.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ProtocolMessage(String type, Map<String, String> fields) {
    public ProtocolMessage {
        if (type == null || !type.matches("[A-Z_]+")) {
            throw new IllegalArgumentException("Message type must contain uppercase letters and underscores only");
        }
        fields = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(fields, "fields")));
    }

    public static ProtocolMessage of(String type) {
        return new ProtocolMessage(type, Map.of());
    }

    public static ProtocolMessage error(String code, String message) {
        return new ProtocolMessage("ERROR", Map.of("code", code, "message", message));
    }

    public String requiredField(String name) throws ProtocolException {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new ProtocolException("Missing required field: " + name);
        }
        return value;
    }
}

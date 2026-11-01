package fruit.server.features.matches;

import static fruit.common.Protocol.obj;

import java.util.Map;

public final class Drop {
    public final long id;
    public final String kind;
    public final double x, speed;
    public double y;

    public Drop(long id, String kind, double x, double y, double speed) {
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.speed = speed;
    }

    public Map<String, Object> view() {
        return obj("id", id, "kind", kind, "x", x, "y", y, "speed", speed);
    }
}
